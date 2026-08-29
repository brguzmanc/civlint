package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete result of evaluating the fixed case set under one architecture.
 *
 * <p>The distinction between {@link #canonicalHash()} and the run's timing fields is the core of the
 * reproducibility contract. {@link #toCanonicalJson()} covers the inputs and the verdicts and
 * deliberately excludes {@code runId}, {@code startedAtEpochMilli}, {@code durationMillis} and every
 * metric measured in {@link MetricResult.Unit#MILLISECONDS}: all of those differ between two identical
 * runs by construction. Two runs of the same architecture over the same
 * inputs must produce the same canonical hash; if they do not, something non-deterministic is present
 * and {@code docs/architecture.md} records how that is diagnosed.
 *
 * @param runId stable identifier for the mode and policy inputs
 * @param mode which architecture produced the result
 * @param policyPackId identifier of the policy pack used
 * @param policyHash canonical hash of the policy pack used
 * @param humanNecessityMapHash canonical hash of the Human Necessity Map used
 * @param existingVersionId identifier of the existing procedure version
 * @param proposedVersionId identifier of the proposed procedure version
 * @param existingVersionHash canonical hash of the existing version
 * @param proposedVersionHash canonical hash of the proposed version
 * @param ruleEngineVersion version of the rule engine
 * @param verifierVersion version of the deterministic verifier
 * @param findings findings in canonical order
 * @param caseOutcomes per-case outcomes in case-identifier order
 * @param metrics metrics in identifier order
 * @param traces agent traces in identifier order
 * @param releaseDecision the derived release decision
 * @param startedAtEpochMilli when the run started; excluded from the canonical hash
 * @param durationMillis how long the run took; excluded from the canonical hash
 */
public record EvaluationRun(
        String runId,
        Mode mode,
        String policyPackId,
        String policyHash,
        String humanNecessityMapHash,
        String existingVersionId,
        String proposedVersionId,
        String existingVersionHash,
        String proposedVersionHash,
        String ruleEngineVersion,
        String verifierVersion,
        List<Finding> findings,
        List<CaseOutcome> caseOutcomes,
        List<MetricResult> metrics,
        List<AgentTrace> traces,
        ReleaseDecision releaseDecision,
        long startedAtEpochMilli,
        long durationMillis) {

    /**
     * Which architecture produced a run.
     */
    public enum Mode {
        /** One general-purpose agent, no deterministic verifier, no Human Necessity Map. */
        BASELINE,
        /** Three specialised agents, typed contracts and the deterministic verifier. */
        ADVANCED
    }

    /**
     * The outcome for one case within a run.
     *
     * @param caseId the case evaluated
     * @param decidedTier the tier the architecture concluded
     * @param requiredRole the role the architecture says must act
     * @param findingIds identifiers of findings raised for this case, in ascending order
     * @param agreesWithOracle whether the tier and role match the locked oracle
     */
    public record CaseOutcome(
            String caseId,
            DecisionTier decidedTier,
            ReviewerRole requiredRole,
            List<String> findingIds,
            boolean agreesWithOracle) {

        public CaseOutcome {
            caseId = Identifiers.requireStable("caseId", caseId);
            Objects.requireNonNull(decidedTier, "decidedTier");
            Objects.requireNonNull(requiredRole, "requiredRole");
            findingIds = List.copyOf(Objects.requireNonNull(findingIds, "findingIds"))
                    .stream()
                    .sorted()
                    .distinct()
                    .toList();
        }

        public Json toJson() {
            return Json.obj()
                    .put("caseId", caseId)
                    .put("decidedTier", decidedTier)
                    .put("requiredRole", requiredRole)
                    .put("findingIds", Json.strings(findingIds))
                    .put("agreesWithOracle", agreesWithOracle)
                    .build();
        }
    }

    public EvaluationRun {
        runId = Identifiers.requireStable("runId", runId);
        Objects.requireNonNull(mode, "mode");
        policyPackId = Identifiers.requireStable("policyPackId", policyPackId);
        policyHash = Objects.requireNonNull(policyHash, "policyHash");
        humanNecessityMapHash = Objects.requireNonNull(humanNecessityMapHash, "humanNecessityMapHash");
        existingVersionId = Identifiers.requireStable("existingVersionId", existingVersionId);
        proposedVersionId = Identifiers.requireStable("proposedVersionId", proposedVersionId);
        existingVersionHash = Objects.requireNonNull(existingVersionHash, "existingVersionHash");
        proposedVersionHash = Objects.requireNonNull(proposedVersionHash, "proposedVersionHash");
        ruleEngineVersion = Identifiers.requireText("ruleEngineVersion", ruleEngineVersion);
        verifierVersion = Identifiers.requireText("verifierVersion", verifierVersion);

        findings = Objects.requireNonNull(findings, "findings").stream().sorted(Finding.SORT_ORDER).toList();
        caseOutcomes = Objects.requireNonNull(caseOutcomes, "caseOutcomes").stream()
                .sorted(Comparator.comparing(CaseOutcome::caseId))
                .toList();
        metrics = Objects.requireNonNull(metrics, "metrics").stream()
                .sorted(Comparator.comparing(MetricResult::metricId))
                .toList();
        traces = Objects.requireNonNull(traces, "traces").stream()
                .sorted(Comparator.comparing(AgentTrace::traceId))
                .toList();
        Objects.requireNonNull(releaseDecision, "releaseDecision");

        if (startedAtEpochMilli < 0 || durationMillis < 0) {
            throw new IllegalArgumentException("Run " + runId + " has negative timing values");
        }
    }

    public Optional<CaseOutcome> outcome(String caseId) {
        return caseOutcomes.stream().filter(o -> o.caseId().equals(caseId)).findFirst();
    }

    public Optional<MetricResult> metric(String metricId) {
        return metrics.stream().filter(m -> m.metricId().equals(metricId)).findFirst();
    }

    /**
     * Returns a copy of this run publishing one additional metric. Rejecting a duplicate identifier
     * makes "appended exactly once" an enforced property rather than a convention.
     *
     * @param metric the metric to publish
     * @return a new run, identical except for the added metric
     * @throws IllegalArgumentException if this run already publishes that metric identifier
     */
    public EvaluationRun withMetric(MetricResult metric) {
        Objects.requireNonNull(metric, "metric");
        if (metric(metric.metricId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Run " + runId + " already publishes metric " + metric.metricId());
        }
        List<MetricResult> extended = new ArrayList<>(metrics);
        extended.add(metric);
        return new EvaluationRun(
                runId,
                mode,
                policyPackId,
                policyHash,
                humanNecessityMapHash,
                existingVersionId,
                proposedVersionId,
                existingVersionHash,
                proposedVersionHash,
                ruleEngineVersion,
                verifierVersion,
                findings,
                caseOutcomes,
                extended,
                traces,
                releaseDecision,
                startedAtEpochMilli,
                durationMillis);
    }

    public List<Finding> releaseBlockingFindings() {
        return findings.stream().filter(Finding::releaseBlocked).toList();
    }

    /**
     * Renders the reproducibility-relevant content as canonical JSON.
     *
     * <p>Excludes {@code runId} and both timing fields, which vary between identical runs.
     *
     * @return a canonical representation suitable for hashing and for comparing two runs
     */
    public Json toCanonicalJson() {
        return Json.obj()
                .put("mode", mode)
                .put("policyPackId", policyPackId)
                .put("policyHash", policyHash)
                .put("humanNecessityMapHash", humanNecessityMapHash)
                .put("existingVersionId", existingVersionId)
                .put("proposedVersionId", proposedVersionId)
                .put("existingVersionHash", existingVersionHash)
                .put("proposedVersionHash", proposedVersionHash)
                .put("ruleEngineVersion", ruleEngineVersion)
                .put("verifierVersion", verifierVersion)
                .put("findings", Json.array(findings.stream().map(Finding::toJson).toList()))
                .put("caseOutcomes", Json.array(caseOutcomes.stream().map(CaseOutcome::toJson).toList()))
                // Wall-clock metrics are excluded: two identical runs differ in duration by
                // construction, so including them would make every replay comparison fail for a
                // reason that has nothing to do with correctness.
                .put(
                        "metrics",
                        Json.array(metrics.stream()
                                .filter(m -> m.unit() != MetricResult.Unit.MILLISECONDS)
                                .map(MetricResult::toJson)
                                .toList()))
                .put("traces", Json.array(traces.stream().map(AgentTrace::toJson).toList()))
                .put("releaseDecision", releaseDecision.toJson())
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toCanonicalJson());
    }
}
