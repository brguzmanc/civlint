package com.bguzman.civlint.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bguzman.civlint.agents.ReplayAgentAdapter;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.MetricResult;
import com.bguzman.civlint.domain.ReviewerRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Covers the operation every published canonical hash comes from: the comparison happens on raw runs,
 * the agreement metric is appended exactly once, a disagreement stops publication, and neither the
 * clock nor the generated run identifier can move the hash.
 */
class ReplayVerificationTest {

    private static EvaluationHarness harness(Clock clock) {
        return new EvaluationHarness(new ReplayAgentAdapter(), clock);
    }

    private static EvaluationHarness harness() {
        return harness(Clock.systemUTC());
    }

    @ParameterizedTest
    @EnumSource(EvaluationRun.Mode.class)
    @DisplayName("a verified run is the raw run plus exactly one agreement metric")
    void verifiedRunIsRawPlusOneMetric(EvaluationRun.Mode mode) {
        EvaluationRun raw = harness().run(mode);
        EvaluationRun verified = harness().runReplayVerified(mode);

        assertThat(raw.metric(Metrics.REPLAY_AGREEMENT)).isEmpty();
        assertThat(verified.metrics().stream()
                        .filter(m -> m.metricId().equals(Metrics.REPLAY_AGREEMENT))
                        .toList())
                .hasSize(1);
        assertThat(verified.metric(Metrics.REPLAY_AGREEMENT).orElseThrow().value().orElseThrow())
                .isEqualByComparingTo("100.00");
        assertThat(verified.metrics()).hasSize(raw.metrics().size() + 1);
        assertThat(verified.findings()).isEqualTo(raw.findings());
        assertThat(verified.caseOutcomes()).isEqualTo(raw.caseOutcomes());
        assertThat(verified.traces()).isEqualTo(raw.traces());
        assertThat(verified.releaseDecision()).isEqualTo(raw.releaseDecision());
    }

    @ParameterizedTest
    @EnumSource(EvaluationRun.Mode.class)
    @DisplayName("two verified runs of the same mode are canonically identical")
    void verifiedRunsAreReproducible(EvaluationRun.Mode mode) {
        assertThat(harness().runReplayVerified(mode).canonicalHash())
                .isEqualTo(harness().runReplayVerified(mode).canonicalHash())
                .hasSize(64);
    }

    @Test
    @DisplayName("a disagreement between two raw runs is reported, not published")
    void disagreementFailsClearly() {
        EvaluationRun raw = harness().run(EvaluationRun.Mode.ADVANCED);
        List<EvaluationRun.CaseOutcome> extra = new ArrayList<>(raw.caseOutcomes());
        extra.add(new EvaluationRun.CaseOutcome(
                "CASE.99.INJECTED", DecisionTier.AUTOMATE, ReviewerRole.NONE, List.of(), false));
        EvaluationRun divergent = rebuild(raw, raw.runId(), extra);

        assertThat(divergent.canonicalHash()).isNotEqualTo(raw.canonicalHash());
        assertThat(Metrics.replayAgreement(raw.canonicalHash(), divergent.canonicalHash())
                        .value()
                        .orElseThrow())
                .as("the metric reports disagreement as 0%, never as a default")
                .isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> ReplayVerification.verify(raw, divergent))
                .isInstanceOf(ReplayVerificationException.class)
                .hasMessageContaining("disagree")
                .hasMessageContaining(raw.canonicalHash())
                .hasMessageContaining(divergent.canonicalHash());
    }

    @Test
    @DisplayName("an already-verified run is refused rather than verified twice")
    void verifyingAVerifiedRunIsRefused() {
        EvaluationRun verified = harness().runReplayVerified(EvaluationRun.Mode.ADVANCED);
        EvaluationRun raw = harness().run(EvaluationRun.Mode.ADVANCED);

        assertThatThrownBy(() -> ReplayVerification.verify(verified, verified))
                .isInstanceOf(ReplayVerificationException.class)
                .hasMessageContaining("first")
                .hasMessageContaining(Metrics.REPLAY_AGREEMENT);
        assertThatThrownBy(() -> ReplayVerification.verify(raw, verified))
                .isInstanceOf(ReplayVerificationException.class)
                .hasMessageContaining("second")
                .hasMessageContaining(Metrics.REPLAY_AGREEMENT);
        // The domain enforces the same rule independently of this operation.
        assertThatThrownBy(() -> verified.withMetric(Metrics.replayAgreement("a", "a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Metrics.REPLAY_AGREEMENT);
    }

    @Test
    @DisplayName("comparing two different architectures is refused")
    void crossModeVerificationIsRefused() {
        EvaluationRun advanced = harness().run(EvaluationRun.Mode.ADVANCED);
        EvaluationRun baseline = harness().run(EvaluationRun.Mode.BASELINE);

        assertThatThrownBy(() -> ReplayVerification.verify(advanced, baseline))
                .isInstanceOf(ReplayVerificationException.class)
                .hasMessageContaining("ADVANCED")
                .hasMessageContaining("BASELINE");
    }

    @ParameterizedTest
    @EnumSource(EvaluationRun.Mode.class)
    @DisplayName("the clock, the duration and the run identifier leave the hash untouched")
    void timingAndRunIdentifierDoNotAffectCanonicalIdentity(EvaluationRun.Mode mode) {
        EvaluationRun early = harness(fixed("2020-01-01T00:00:00Z")).runReplayVerified(mode);
        EvaluationRun late = harness(fixed("2031-12-31T23:59:59Z")).runReplayVerified(mode);
        EvaluationRun slow = harness(ticking()).runReplayVerified(mode);

        assertThat(early.startedAtEpochMilli()).isNotEqualTo(late.startedAtEpochMilli());
        assertThat(slow.durationMillis()).isNotEqualTo(early.durationMillis());
        assertThat(early.canonicalHash())
                .isEqualTo(late.canonicalHash())
                .isEqualTo(slow.canonicalHash())
                .isEqualTo(rebuild(early, early.runId() + ".RENAMED", early.caseOutcomes())
                        .canonicalHash());
        assertThat(early.metric(Metrics.EXECUTION_TIME).orElseThrow().unit())
                .as("the differing measurement is still published, just outside the hash")
                .isEqualTo(MetricResult.Unit.MILLISECONDS);
    }

    private static Clock fixed(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    /** A clock that advances on every read, so the run records a non-zero, mode-independent duration. */
    private static Clock ticking() {
        return new Clock() {
            private Instant now = Instant.parse("2026-08-28T00:00:00Z");

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                now = now.plus(Duration.ofSeconds(41));
                return now;
            }
        };
    }

    private static EvaluationRun rebuild(
            EvaluationRun run, String runId, List<EvaluationRun.CaseOutcome> caseOutcomes) {
        return new EvaluationRun(
                runId,
                run.mode(),
                run.policyPackId(),
                run.policyHash(),
                run.humanNecessityMapHash(),
                run.existingVersionId(),
                run.proposedVersionId(),
                run.existingVersionHash(),
                run.proposedVersionHash(),
                run.ruleEngineVersion(),
                run.verifierVersion(),
                run.findings(),
                caseOutcomes,
                run.metrics(),
                run.traces(),
                run.releaseDecision(),
                run.startedAtEpochMilli(),
                run.durationMillis());
    }
}
