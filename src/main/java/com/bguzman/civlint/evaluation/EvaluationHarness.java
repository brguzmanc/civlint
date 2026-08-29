package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.agents.AgentDefinition;
import com.bguzman.civlint.agents.AgentModelPort;
import com.bguzman.civlint.agents.AgentOrchestrator;
import com.bguzman.civlint.agents.AgentOutcome;
import com.bguzman.civlint.agents.AgentRequest;
import com.bguzman.civlint.agents.RunContext;
import com.bguzman.civlint.domain.AgentObservation;
import com.bguzman.civlint.domain.AgentTrace;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.domain.MetricResult;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.Procedure;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.domain.ReleaseDecision;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.policy.RuleEvaluator;
import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Digest;
import com.bguzman.civlint.verification.CaseVerdict;
import com.bguzman.civlint.verification.CaseVerifier;
import com.bguzman.civlint.verification.StructuralVerifier;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the fixed case set under either architecture.
 *
 * <p>The two paths differ in exactly one respect, which is the whole point of the comparison:
 *
 * <ul>
 *   <li><strong>Baseline.</strong> One general-purpose agent is asked about each case and its answer
 *       is adopted as the verdict. There is no deterministic verifier, no Human Necessity Map and no
 *       structural comparison of procedure versions.
 *   <li><strong>Advanced.</strong> Three specialised agents are invoked and their observations are
 *       recorded in traces, but the verdict comes from the deterministic verifier. Agent output is
 *       <em>not load-bearing</em>: {@link #runAdvanced} produces identical findings, verdicts and
 *       release decisions when every agent is unavailable, which
 *       {@code AgentIndependenceTest} asserts directly.
 * </ul>
 *
 * <p>The clock is injected and is used only for the run's timing fields, which are excluded from the
 * canonical hash. Nothing in a verdict depends on the time of day.
 */
public final class EvaluationHarness {

    /** Version of the verifier, recorded in every run. */
    public static final String VERIFIER_VERSION = "civlint-verifier/0.1.0";

    private static final Set<String> BOUNDARY_CASE_IDS = Set.of(
            "CASE.08.CERTIFIED.NAMECHANGE",
            "CASE.10.MISSING.NONCRITICAL",
            "CASE.11.CONFLICT",
            "CASE.12.STRUCTURE",
            "CASE.13.ACCESSIBILITY",
            "CASE.14.APPEAL.REMOVED",
            "CASE.15.DUTY.VIOLATION");
    private static final Set<String> REPAIR_CASE_IDS = Set.of(
            "CASE.14.APPEAL.REMOVED",
            "CASE.15.DUTY.VIOLATION");

    private final PolicyPack pack;
    private final Procedure procedure;
    private final HumanNecessityMap map;
    private final List<EvaluationCase> cases;
    private final String existingVersionId;
    private final String proposedVersionId;
    private final AgentModelPort port;
    private final Clock clock;

    public EvaluationHarness(AgentModelPort port, Clock clock) {
        this(
                DemoPolicy.pack(),
                DemoProcedures.procedure(),
                DemoHumanNecessity.map(),
                DemoCases.cases(),
                DemoProcedures.VERSION_EXISTING,
                DemoProcedures.VERSION_PROPOSED,
                port,
                clock);
    }

    public EvaluationHarness(
            PolicyPack pack,
            Procedure procedure,
            HumanNecessityMap map,
            List<EvaluationCase> cases,
            String existingVersionId,
            String proposedVersionId,
            AgentModelPort port,
            Clock clock) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.procedure = Objects.requireNonNull(procedure, "procedure");
        this.map = Objects.requireNonNull(map, "map");
        this.cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        this.existingVersionId = Objects.requireNonNull(existingVersionId, "existingVersionId");
        this.proposedVersionId = Objects.requireNonNull(proposedVersionId, "proposedVersionId");
        this.port = Objects.requireNonNull(port, "port");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Runs one architecture raw, with no replay-agreement metric appended.
     *
     * @param mode which architecture to run
     * @return the raw run, which is the unit {@link #runReplayVerified} compares
     */
    public EvaluationRun run(EvaluationRun.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return mode == EvaluationRun.Mode.ADVANCED ? runAdvanced() : runBaseline();
    }

    /**
     * Runs one architecture twice and publishes the comparison. Every surface that publishes a
     * canonical hash uses this, so reproducibility is measured rather than asserted.
     *
     * @param mode which architecture to run
     * @return the run, carrying exactly one {@link Metrics#REPLAY_AGREEMENT} metric
     * @throws ReplayVerificationException if the two raw runs do not hash identically
     */
    public EvaluationRun runReplayVerified(EvaluationRun.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return ReplayVerification.verify(run(mode), run(mode));
    }

    public EvaluationRun runAdvanced() {
        long startedAt = clock.millis();
        ProcedureVersion existing = version(existingVersionId);
        ProcedureVersion primary = version(proposedVersionId);

        List<AgentOutcome> outcomes = invokeAgents(AgentDefinition.specialised());

        List<Finding> allFindings = new ArrayList<>();
        Map<String, EvaluationRun.CaseOutcome> caseOutcomes = new LinkedHashMap<>();

        for (EvaluationCase evaluationCase : cases) {
            DecisionTier tier;
            ReviewerRole role;
            List<Finding> findings;

            if (evaluationCase.scope() == EvaluationCase.Scope.CASE_LEVEL) {
                CaseVerdict verdict = CaseVerifier.verify(pack, evaluationCase.request());
                tier = verdict.tier();
                role = verdict.requiredRole();
                findings = verdict.findings();
            } else {
                ProcedureVersion proposed = version(evaluationCase.proposedVersionId());
                findings = StructuralVerifier.verify(pack, existing, proposed, map);
                tier = findings.stream()
                        .map(Finding::decisionTier)
                        .reduce(DecisionTier::escalate)
                        .orElse(DecisionTier.AUTOMATE);
                role = roleAt(findings, tier);
            }

            allFindings.addAll(findings);
            caseOutcomes.put(evaluationCase.caseId(), outcome(evaluationCase, tier, role, findings));
        }

        List<Finding> primaryFindings = StructuralVerifier.verify(pack, existing, primary, map);
        long duration = Math.max(0, clock.millis() - startedAt);

        return assemble(
                EvaluationRun.Mode.ADVANCED,
                existing,
                primary,
                dedupe(allFindings),
                caseOutcomes,
                primaryFindings,
                outcomes,
                startedAt,
                duration);
    }

    /**
     * Runs the baseline architecture: one general-purpose agent, whose answer is adopted directly.
     *
     * <p>No verifier is consulted, so the baseline produces no findings and no counterexamples. That
     * is a property of the architecture rather than a limitation imposed on it: the baseline's agent
     * is permitted to block a release, which the advanced architecture's agents are not.
     *
     * @return the run, with per-case outcomes, metrics and traces
     */
    public EvaluationRun runBaseline() {
        long startedAt = clock.millis();
        ProcedureVersion existing = version(existingVersionId);
        ProcedureVersion primary = version(proposedVersionId);

        List<AgentOutcome> outcomes = invokeAgents(List.of(AgentDefinition.BASELINE_GENERALIST));

        Map<String, DecisionTier> proposedTiers = new TreeMap<>();
        for (AgentOutcome outcome : outcomes) {
            for (AgentObservation observation : outcome.usableObservations()) {
                proposedTiers.put(promptKeyOf(observation), observation.proposedTier());
            }
        }

        Map<String, EvaluationRun.CaseOutcome> caseOutcomes = new LinkedHashMap<>();
        for (EvaluationCase evaluationCase : cases) {
            // With no verifier, the agent's proposal IS the verdict. Where the agent said nothing,
            // the baseline has no answer, which is recorded as the most cautious tier rather than as
            // an accidental automation.
            DecisionTier tier = proposedTiers.getOrDefault(
                    evaluationCase.caseId(), DecisionTier.HUMAN_REQUIRED);
            ReviewerRole role = tier == DecisionTier.AUTOMATE
                    ? ReviewerRole.NONE
                    : ReviewerRole.REGISTRY_SUPERVISOR;
            caseOutcomes.put(
                    evaluationCase.caseId(), outcome(evaluationCase, tier, role, List.of()));
        }

        long duration = Math.max(0, clock.millis() - startedAt);
        return assemble(
                EvaluationRun.Mode.BASELINE,
                existing,
                primary,
                List.of(),
                caseOutcomes,
                List.of(),
                outcomes,
                startedAt,
                duration);
    }

    private String promptKeyOf(AgentObservation observation) {
        // The baseline agent answers per case; its fixture subject names either the case or the
        // procedure version, so map a version subject back to the case that asked about it.
        String key = observation.subject().key();
        if (key.startsWith("CASE.")) {
            return key.substring("CASE.".length());
        }
        String versionId = key.substring(key.indexOf('.') + 1);
        return cases.stream()
                .filter(c -> c.scope() == EvaluationCase.Scope.VERSION_COMPARISON)
                .filter(c -> c.proposedVersionId().equals(versionId))
                .map(EvaluationCase::caseId)
                .findFirst()
                .orElse(key);
    }

    private List<AgentOutcome> invokeAgents(List<AgentDefinition> definitions) {
        // Hashing the pack re-serialises the whole policy graph, and the value is the same for every
        // invocation, so it is computed once instead of once per case and agent.
        String packHash = pack.canonicalHash();
        List<AgentOrchestrator.Invocation> invocations = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            String payload = CanonicalJson.write(
                    evaluationCase.request().toJson());
            String inputHash = evaluationCase.request().canonicalHash();
            for (AgentDefinition definition : definitions) {
                if (!isPlannedFor(definition, evaluationCase.caseId())) {
                    continue;
                }
                invocations.add(new AgentOrchestrator.Invocation(
                        definition,
                        new AgentRequest(
                                definition.agentId(),
                                definition.agentVersion(),
                                evaluationCase.caseId(),
                                inputHash,
                                packHash,
                                List.of(
                                        existingVersionId,
                                        evaluationCase.proposedVersionId()),
                                payload)));
            }
        }
        RunContext context = new RunContext(
                "RUN.AGENTS",
                pack.version(),
                packHash,
                "ALL",
                "CORR." + Digest.shorten(packHash));
        return new AgentOrchestrator(port).runAll(context, invocations);
    }

    private static boolean isPlannedFor(AgentDefinition definition, String caseId) {
        if (definition == AgentDefinition.BOUNDARY_CASE) {
            return BOUNDARY_CASE_IDS.contains(caseId);
        }
        if (definition == AgentDefinition.REPAIR_ADVISOR) {
            return REPAIR_CASE_IDS.contains(caseId);
        }
        return true;
    }

    private EvaluationRun.CaseOutcome outcome(
            EvaluationCase evaluationCase,
            DecisionTier tier,
            ReviewerRole role,
            List<Finding> findings) {
        boolean agrees = tier == evaluationCase.oracleTier()
                && role == evaluationCase.oracleRequiredRole();
        return new EvaluationRun.CaseOutcome(
                evaluationCase.caseId(),
                tier,
                role,
                findings.stream().map(Finding::findingId).toList(),
                agrees);
    }

    private static ReviewerRole roleAt(List<Finding> findings, DecisionTier tier) {
        return findings.stream()
                .sorted(Finding.SORT_ORDER)
                .filter(f -> f.decisionTier() == tier)
                .map(Finding::requiredReviewerRole)
                .filter(ReviewerRole::human)
                .findFirst()
                .orElse(tier == DecisionTier.AUTOMATE ? ReviewerRole.NONE : ReviewerRole.REGISTRY_SUPERVISOR);
    }

    private static List<Finding> dedupe(List<Finding> findings) {
        Map<String, Finding> byId = new TreeMap<>();
        findings.forEach(f -> byId.putIfAbsent(f.findingId(), f));
        return List.copyOf(byId.values());
    }

    private EvaluationRun assemble(
            EvaluationRun.Mode mode,
            ProcedureVersion existing,
            ProcedureVersion primary,
            List<Finding> findings,
            Map<String, EvaluationRun.CaseOutcome> caseOutcomes,
            List<Finding> primaryFindings,
            List<AgentOutcome> outcomes,
            long startedAt,
            long duration) {
        List<AgentTrace> traces = AgentOrchestrator.traces(outcomes);
        int rejections = (int) traces.stream()
                .filter(t -> t.status() == AgentTrace.Status.SCHEMA_REJECTED)
                .count();

        List<MetricResult> metrics = Metrics.compute(
                mode,
                cases,
                caseOutcomes,
                findings,
                primaryFindings,
                existing,
                primary,
                traces.size(),
                rejections,
                duration);

        // Unsafe variants remain evaluation counterexamples; the release decision covers the primary proposal.
        ReleaseDecision release = ReleaseDecision.from(primaryFindings);

        return new EvaluationRun(
                runId(mode),
                mode,
                pack.packId(),
                pack.canonicalHash(),
                map.canonicalHash(),
                existing.versionId(),
                primary.versionId(),
                existing.canonicalHash(),
                primary.canonicalHash(),
                RuleEvaluator.RULE_ENGINE_VERSION,
                VERIFIER_VERSION,
                findings,
                List.copyOf(caseOutcomes.values()),
                metrics,
                traces,
                release,
                startedAt,
                duration);
    }

    private String runId(EvaluationRun.Mode mode) {
        // Deterministic by design: derived from the mode and the policy hash, never from the clock,
        // so two executions of the same inputs are directly comparable.
        return "RUN." + mode.name() + "." + Digest.shorten(pack.canonicalHash()).toUpperCase();
    }

    private ProcedureVersion version(String versionId) {
        Optional<ProcedureVersion> found = procedure.version(versionId);
        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "Procedure " + procedure.procedureId() + " has no version " + versionId);
        }
        return found.get();
    }
}
