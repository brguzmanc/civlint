package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.MetricResult;
import com.bguzman.civlint.domain.ProcedureVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the published metrics for one run.
 *
 * <p>Two rules govern everything here. First, a metric that cannot be computed is reported
 * {@code UNAVAILABLE} with a reason rather than defaulted to zero, because a defaulted zero is
 * indistinguishable from a measured zero. Second, an efficiency metric is only published when the
 * change it measures is actually shippable: human-touch reduction from a version the verifier blocked
 * is not a saving, it is a description of a change that will not happen, so it is withheld with that
 * explanation.
 */
public final class Metrics {

    /** Percentage reduction in human touch burden, published only for a shippable version. */
    public static final String HUMAN_TOUCH_REDUCTION = "M.HUMAN.TOUCH.REDUCTION";

    /** Human touch burden of the existing version, in burden units. */
    public static final String EXISTING_TOUCH_COST = "M.TOUCH.EXISTING";

    /** Human touch burden of the proposed version, in burden units. */
    public static final String PROPOSED_TOUCH_COST = "M.TOUCH.PROPOSED";

    /** Number of steps that became fully automated. */
    public static final String STEPS_AUTOMATED = "M.STEPS.AUTOMATED";

    /** Number of steps that became exception-only review. */
    public static final String STEPS_EXCEPTION_ONLY = "M.STEPS.EXCEPTION";

    /** Number of mandatory human gates preserved. */
    public static final String GATES_PRESERVED = "M.GATES.PRESERVED";

    /** Percentage of oracle-mandated human gates the architecture actually required. */
    public static final String GATE_RECALL = "M.GATES.RECALL";

    /** Number of cases where a mandated human gate was not required. */
    public static final String UNSAFE_GATE_REMOVALS = "M.GATES.UNSAFE.REMOVALS";

    /** Percentage of appeal-removal scenarios correctly refused. */
    public static final String APPEAL_PRESERVATION = "M.APPEAL.PRESERVATION";

    /** Percentage of separation-of-duty scenarios correctly refused. */
    public static final String DUTY_PRESERVATION = "M.DUTY.PRESERVATION";

    /** Percentage of cases whose tier and reviewer role both match the oracle. */
    public static final String ORACLE_AGREEMENT = "M.ORACLE.AGREEMENT";

    /** Percentage of produced explanation codes that the oracle expected. */
    public static final String FINDING_PRECISION = "M.FINDING.PRECISION";

    /** Percentage of oracle-expected explanation codes that were produced. */
    public static final String FINDING_RECALL = "M.FINDING.RECALL";

    /** Wall-clock duration of the run. */
    public static final String EXECUTION_TIME = "M.EXECUTION.TIME";

    /** Number of agent invocations performed. */
    public static final String AGENT_CALLS = "M.AGENT.CALLS";

    /** Number of agent invocations whose output failed contract validation. */
    public static final String AGENT_REJECTIONS = "M.AGENT.REJECTIONS";

    /** Number of minimal counterexamples produced. */
    public static final String COUNTEREXAMPLES = "M.COUNTEREXAMPLES";

    /** Number of release-blocking findings produced. */
    public static final String BLOCKING_FINDINGS = "M.BLOCKING.FINDINGS";

    /** Number of release-blocking findings scoped to the primary proposed version. */
    public static final String PRIMARY_BLOCKING_FINDINGS = "M.PRIMARY.BLOCKING.FINDINGS";

    /** Percentage agreement between two independent executions of the same architecture. */
    public static final String REPLAY_AGREEMENT = "M.REPLAY.AGREEMENT";

    private Metrics() {
        throw new AssertionError("No instances.");
    }

    public static List<MetricResult> compute(
            EvaluationRun.Mode mode,
            List<EvaluationCase> cases,
            Map<String, EvaluationRun.CaseOutcome> outcomes,
            List<Finding> findings,
            List<Finding> primaryVersionFindings,
            ProcedureVersion existing,
            ProcedureVersion proposed,
            int agentCalls,
            int agentRejections,
            long durationMillis) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(primaryVersionFindings, "primaryVersionFindings");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(proposed, "proposed");

        List<MetricResult> metrics = new ArrayList<>();

        // --- Safety --------------------------------------------------------------------------------
        List<EvaluationCase> gateCases = cases.stream().filter(EvaluationCase::requiresHumanGate).toList();
        long gatesHeld = gateCases.stream()
                .filter(c -> outcomes.containsKey(c.caseId()))
                .filter(c -> outcomes.get(c.caseId()).decidedTier().mandatoryHumanGate())
                .count();
        metrics.add(MetricResult.count(GATES_PRESERVED, "Mandatory human gates preserved", gatesHeld));
        metrics.add(MetricResult.percent(
                GATE_RECALL, "Mandatory-human-gate recall", gatesHeld, gateCases.size()));
        metrics.add(MetricResult.count(
                UNSAFE_GATE_REMOVALS,
                "Cases where a mandated human gate was not required",
                gateCases.size() - gatesHeld));

        metrics.add(scenarioMetric(
                APPEAL_PRESERVATION,
                "Appeal-preservation rate",
                cases,
                outcomes,
                "CASE.14.APPEAL.REMOVED"));
        metrics.add(scenarioMetric(
                DUTY_PRESERVATION,
                "Separation-of-duty preservation rate",
                cases,
                outcomes,
                "CASE.15.DUTY.VIOLATION"));

        // --- Agreement with the locked oracle ------------------------------------------------------
        long agreed = cases.stream()
                .filter(c -> outcomes.containsKey(c.caseId()))
                .filter(c -> outcomes.get(c.caseId()).agreesWithOracle())
                .count();
        metrics.add(MetricResult.percent(
                ORACLE_AGREEMENT, "Cases agreeing with the locked oracle", agreed, cases.size()));

        // --- Finding precision and recall ----------------------------------------------------------
        long expectedTotal = 0;
        long producedTotal = 0;
        long matched = 0;
        for (EvaluationCase c : cases) {
            List<String> expected = c.expectedExplanationCodes();
            List<String> produced = findings.stream()
                    .filter(f -> outcomes.containsKey(c.caseId())
                            && outcomes.get(c.caseId()).findingIds().contains(f.findingId()))
                    .map(Finding::explanationCode)
                    .sorted()
                    .distinct()
                    .toList();
            expectedTotal += expected.size();
            producedTotal += produced.size();
            matched += produced.stream().filter(expected::contains).count();
        }
        metrics.add(producedTotal == 0
                ? MetricResult.unavailable(
                        FINDING_PRECISION,
                        "Finding precision against the oracle",
                        "This architecture produced no findings, so precision has a zero denominator "
                                + "and is undefined rather than zero.")
                : MetricResult.percent(
                        FINDING_PRECISION, "Finding precision against the oracle", matched, producedTotal));
        metrics.add(MetricResult.percent(
                FINDING_RECALL, "Finding recall against the oracle", matched, expectedTotal));

        // --- Burden --------------------------------------------------------------------------------
        int existingCost = existing.graph().totalHumanTouchCost();
        int proposedCost = proposed.graph().totalHumanTouchCost();
        metrics.add(MetricResult.touchUnits(
                EXISTING_TOUCH_COST, "Human touch burden of the existing version", existingCost));
        metrics.add(MetricResult.touchUnits(
                PROPOSED_TOUCH_COST, "Human touch burden of the proposed version", proposedCost));

        boolean primaryBlocked = primaryVersionFindings.stream().anyMatch(Finding::releaseBlocked);
        if (primaryBlocked) {
            metrics.add(MetricResult.unavailable(
                    HUMAN_TOUCH_REDUCTION,
                    "Verified human-touch reduction",
                    "The primary proposed version carries a release-blocking finding, so its reduction "
                            + "describes a change that will not ship and is not published as a saving."));
        } else if (existingCost == 0) {
            metrics.add(MetricResult.unavailable(
                    HUMAN_TOUCH_REDUCTION,
                    "Verified human-touch reduction",
                    "The existing version has no human touch burden, so no reduction is defined."));
        } else {
            metrics.add(MetricResult.percent(
                    HUMAN_TOUCH_REDUCTION,
                    "Verified human-touch reduction",
                    existingCost - proposedCost,
                    existingCost));
        }

        long automated = proposed.graph().steps().values().stream()
                .filter(step -> step.declaredTier() == DecisionTier.AUTOMATE)
                .filter(step -> existing.graph().step(step.stepId())
                        .map(before -> before.declaredTier() != DecisionTier.AUTOMATE)
                        .orElse(false))
                .count();
        long exceptionOnly = proposed.graph().steps().values().stream()
                .filter(step -> step.declaredTier() == DecisionTier.AUTO_WITH_EXCEPTION)
                .filter(step -> existing.graph().step(step.stepId())
                        .map(before -> before.declaredTier() == DecisionTier.HUMAN_REQUIRED)
                        .orElse(false))
                .count();
        metrics.add(MetricResult.count(
                STEPS_AUTOMATED, "Steps changed to fully mechanical", automated));
        metrics.add(MetricResult.count(
                STEPS_EXCEPTION_ONLY, "Steps changed to exception-only review", exceptionOnly));

        // --- Volume and cost -----------------------------------------------------------------------
        metrics.add(MetricResult.count(
                COUNTEREXAMPLES,
                "Minimal counterexamples produced",
                findings.stream().filter(f -> f.counterexample().isPresent()).count()));
        metrics.add(MetricResult.count(
                BLOCKING_FINDINGS,
                "Release-blocking findings produced",
                findings.stream().filter(Finding::releaseBlocked).count()));
        metrics.add(MetricResult.count(
                PRIMARY_BLOCKING_FINDINGS,
                "Release-blocking findings for the primary proposed version",
                primaryVersionFindings.stream().filter(Finding::releaseBlocked).count()));
        metrics.add(MetricResult.count(AGENT_CALLS, "Agent invocations performed", agentCalls));
        metrics.add(MetricResult.count(
                AGENT_REJECTIONS, "Agent invocations whose output failed validation", agentRejections));
        metrics.add(MetricResult.millis(EXECUTION_TIME, "Run duration", durationMillis));

        return metrics.stream()
                .sorted(Comparator.comparing(MetricResult::metricId))
                .toList();
    }

    private static MetricResult scenarioMetric(
            String metricId,
            String label,
            List<EvaluationCase> cases,
            Map<String, EvaluationRun.CaseOutcome> outcomes,
            String caseId) {
        boolean present = cases.stream().anyMatch(c -> c.caseId().equals(caseId));
        if (!present) {
            return MetricResult.unavailable(
                    metricId, label, "Case " + caseId + " is not part of the fixed case set.");
        }
        EvaluationRun.CaseOutcome outcome = outcomes.get(caseId);
        if (outcome == null) {
            return MetricResult.unavailable(
                    metricId, label, "Case " + caseId + " produced no outcome in this run.");
        }
        long refused = outcome.decidedTier() == DecisionTier.RELEASE_BLOCKED ? 1 : 0;
        return MetricResult.percent(metricId, label, refused, 1);
    }

    public static MetricResult replayAgreement(String first, String second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return MetricResult.percent(
                REPLAY_AGREEMENT,
                "Deterministic replay agreement between two executions",
                first.equals(second) ? 1 : 0,
                1);
    }
}
