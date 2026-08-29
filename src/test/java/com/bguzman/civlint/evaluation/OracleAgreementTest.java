package com.bguzman.civlint.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bguzman.civlint.agents.AgentDefinition;
import com.bguzman.civlint.agents.ReplayAgentAdapter;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.MetricResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Asserts that the advanced architecture agrees with the locked oracle on every fixed case, and that
 * the safety requirements hold exactly.
 */
class OracleAgreementTest {

    private static final EvaluationRun ADVANCED =
            new EvaluationHarness(new ReplayAgentAdapter(), Clock.systemUTC()).runAdvanced();

    private static final EvaluationRun BASELINE =
            new EvaluationHarness(new ReplayAgentAdapter(), Clock.systemUTC()).runBaseline();

    static Stream<EvaluationCase> allCases() {
        return DemoCases.cases().stream();
    }

    @Test
    @DisplayName("the case set contains exactly fifteen cases with unique identifiers")
    void caseSetShape() {
        List<EvaluationCase> cases = DemoCases.cases();
        assertThat(cases).hasSize(DemoCases.CASE_COUNT).hasSize(15);
        assertThat(cases.stream().map(EvaluationCase::caseId).distinct().toList()).hasSize(15);
        assertThat(cases.stream().map(EvaluationCase::caseId).toList())
                .isSorted()
                .allSatisfy(id -> assertThat(id).startsWith("CASE."));
    }

    @Test
    @DisplayName("every oracle tier prescribed by the evaluation design is represented")
    void tierCoverage() {
        List<DecisionTier> tiers = DemoCases.cases().stream().map(EvaluationCase::oracleTier).toList();
        assertThat(tiers).contains(DecisionTier.AUTOMATE);
        assertThat(tiers).contains(DecisionTier.AUTO_WITH_EXCEPTION);
        assertThat(tiers).contains(DecisionTier.HUMAN_REQUIRED);
        assertThat(tiers).contains(DecisionTier.RELEASE_BLOCKED);
        assertThat(tiers.stream().filter(t -> t == DecisionTier.RELEASE_BLOCKED).count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("the advanced architecture reaches the oracle tier")
    void advancedTierMatchesOracle(EvaluationCase evaluationCase) {
        EvaluationRun.CaseOutcome outcome = ADVANCED.outcome(evaluationCase.caseId()).orElseThrow();
        assertThat(outcome.decidedTier())
                .as("tier for %s", evaluationCase.caseId())
                .isEqualTo(evaluationCase.oracleTier());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("the advanced architecture routes to the oracle reviewer role")
    void advancedRoleMatchesOracle(EvaluationCase evaluationCase) {
        EvaluationRun.CaseOutcome outcome = ADVANCED.outcome(evaluationCase.caseId()).orElseThrow();
        assertThat(outcome.requiredRole())
                .as("role for %s", evaluationCase.caseId())
                .isEqualTo(evaluationCase.oracleRequiredRole());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("the advanced architecture produces exactly the expected explanation codes")
    void advancedCodesMatchOracle(EvaluationCase evaluationCase) {
        EvaluationRun.CaseOutcome outcome = ADVANCED.outcome(evaluationCase.caseId()).orElseThrow();
        List<String> produced = ADVANCED.findings().stream()
                .filter(f -> outcome.findingIds().contains(f.findingId()))
                .map(Finding::explanationCode)
                .sorted()
                .distinct()
                .toList();
        assertThat(produced)
                .as("codes for %s", evaluationCase.caseId())
                .isEqualTo(evaluationCase.expectedExplanationCodes());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("the advanced architecture agrees with the oracle on every case")
    void advancedAgrees(EvaluationCase evaluationCase) {
        assertThat(ADVANCED.outcome(evaluationCase.caseId()).orElseThrow().agreesWithOracle())
                .as("agreement for %s", evaluationCase.caseId())
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allCases")
    @DisplayName("every case where the oracle mandates a human gate keeps one under the advanced path")
    void mandatoryGatesHeld(EvaluationCase evaluationCase) {
        if (!evaluationCase.requiresHumanGate()) {
            return;
        }
        EvaluationRun.CaseOutcome outcome = ADVANCED.outcome(evaluationCase.caseId()).orElseThrow();
        assertThat(outcome.decidedTier().mandatoryHumanGate())
                .as("mandatory human gate for %s", evaluationCase.caseId())
                .isTrue();
        assertThat(outcome.requiredRole().human())
                .as("a human role is named for %s", evaluationCase.caseId())
                .isTrue();
    }

    @Test
    @DisplayName("mandatory-human-gate recall is exactly 100% and unsafe gate removal is exactly zero")
    void safetyRequirements() {
        assertThat(percentOf(ADVANCED, Metrics.GATE_RECALL)).isEqualByComparingTo("100.00");
        assertThat(countOf(ADVANCED, Metrics.UNSAFE_GATE_REMOVALS)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("appeal removal and separation-of-duty violation each block the release")
    void blockingScenarios() {
        assertThat(ADVANCED.outcome("CASE.14.APPEAL.REMOVED").orElseThrow().decidedTier())
                .isEqualTo(DecisionTier.RELEASE_BLOCKED);
        assertThat(ADVANCED.outcome("CASE.15.DUTY.VIOLATION").orElseThrow().decidedTier())
                .isEqualTo(DecisionTier.RELEASE_BLOCKED);
        assertThat(percentOf(ADVANCED, Metrics.APPEAL_PRESERVATION)).isEqualByComparingTo("100.00");
        assertThat(percentOf(ADVANCED, Metrics.DUTY_PRESERVATION)).isEqualByComparingTo("100.00");
        assertThat(ADVANCED.releaseBlockingFindings()).isNotEmpty();
    }

    @Test
    @DisplayName("the primary proposed version carries no release-blocking finding")
    void primaryVersionIsReleasable() {
        assertThat(countOf(ADVANCED, Metrics.PRIMARY_BLOCKING_FINDINGS)).isEqualByComparingTo("0");
        assertThat(ADVANCED.releaseDecision().blocked()).isFalse();
        assertThat(ADVANCED.releaseDecision().blockingFindingIds()).isEmpty();
    }

    @Test
    @DisplayName("finding precision and recall against the oracle are both 100%")
    void findingQuality() {
        assertThat(percentOf(ADVANCED, Metrics.FINDING_PRECISION)).isEqualByComparingTo("100.00");
        assertThat(percentOf(ADVANCED, Metrics.FINDING_RECALL)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("the baseline misses at least one mandated human gate, which the advanced path holds")
    void baselineIsMeasurablyWeaker() {
        BigDecimal baselineRecall = percentOf(BASELINE, Metrics.GATE_RECALL);
        BigDecimal advancedRecall = percentOf(ADVANCED, Metrics.GATE_RECALL);
        assertThat(baselineRecall).isLessThan(advancedRecall);
        assertThat(countOf(BASELINE, Metrics.UNSAFE_GATE_REMOVALS))
                .isGreaterThan(countOf(ADVANCED, Metrics.UNSAFE_GATE_REMOVALS));
        assertThat(percentOf(BASELINE, Metrics.ORACLE_AGREEMENT))
                .isLessThan(percentOf(ADVANCED, Metrics.ORACLE_AGREEMENT));
    }

    @Test
    @DisplayName("the baseline is permitted to block, so its failures are not a contract artifact")
    void baselineMayBlock() {
        assertThat(AgentDefinition.BASELINE_GENERALIST.mayBlockRelease())
                .isTrue();
        // It exercises that permission on the appeal case, so its miss on the duty case reflects the
        // architecture rather than a restriction imposed on it.
        assertThat(BASELINE.outcome("CASE.14.APPEAL.REMOVED").orElseThrow().decidedTier())
                .isEqualTo(DecisionTier.RELEASE_BLOCKED);
        assertThat(BASELINE.outcome("CASE.15.DUTY.VIOLATION").orElseThrow().decidedTier())
                .isNotEqualTo(DecisionTier.RELEASE_BLOCKED);
    }

    @Test
    @DisplayName("the baseline produces no findings or counterexamples, having no verifier")
    void baselineHasNoEvidenceLayer() {
        assertThat(BASELINE.findings()).isEmpty();
        assertThat(countOf(BASELINE, Metrics.COUNTEREXAMPLES)).isEqualByComparingTo("0");
        assertThat(BASELINE.metric(Metrics.FINDING_PRECISION).orElseThrow().measured()).isFalse();
        assertThat(BASELINE.metric(Metrics.FINDING_PRECISION).orElseThrow().unavailableReason())
                .contains("zero denominator");
    }

    @Test
    @DisplayName("the advanced path produces minimal counterexamples")
    void counterexamplesAreMinimal() {
        List<Finding> withWitness =
                ADVANCED.findings().stream().filter(f -> f.counterexample().isPresent()).toList();
        assertThat(withWitness).isNotEmpty();
        withWitness.forEach(finding -> {
            var counterexample = finding.counterexample().orElseThrow();
            assertThat(counterexample.witnessSize())
                    .as("witness for %s is small enough to read", finding.findingId())
                    .isBetween(1, 6);
            assertThat(counterexample.description()).isNotBlank();
        });
    }

    @Test
    @DisplayName("every finding carries evidence and a machine-readable code")
    void findingsAreEvidenceBacked() {
        assertThat(ADVANCED.findings()).isNotEmpty();
        ADVANCED.findings().forEach(finding -> {
            assertThat(finding.references()).as("references for %s", finding.findingId()).isNotEmpty();
            assertThat(finding.explanationCode()).isNotBlank();
            assertThat(finding.explanation()).isNotBlank();
            assertThat(finding.releaseBlocked())
                    .isEqualTo(finding.decisionTier() == DecisionTier.RELEASE_BLOCKED);
        });
    }

    @Test
    @DisplayName("findings are emitted in canonical order")
    void findingsAreOrdered() {
        assertThat(ADVANCED.findings().stream().map(Finding::findingId).toList()).isSorted();
        assertThat(ADVANCED.traces().stream().map(t -> t.traceId()).toList()).isSorted();
        assertThat(ADVANCED.metrics().stream().map(MetricResult::metricId).toList()).isSorted();
        assertThat(ADVANCED.caseOutcomes().stream().map(EvaluationRun.CaseOutcome::caseId).toList())
                .isSorted();
    }

    private static BigDecimal percentOf(EvaluationRun run, String metricId) {
        MetricResult metric = run.metric(metricId).orElseThrow(
                () -> new AssertionError("Missing metric " + metricId));
        return metric.value().orElseThrow(
                () -> new AssertionError("Metric " + metricId + " is unavailable"));
    }

    private static BigDecimal countOf(EvaluationRun run, String metricId) {
        return percentOf(run, metricId);
    }
}
