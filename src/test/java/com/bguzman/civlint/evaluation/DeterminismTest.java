package com.bguzman.civlint.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bguzman.civlint.agents.AgentModelPort;
import com.bguzman.civlint.agents.AgentRequest;
import com.bguzman.civlint.agents.AgentUnavailableException;
import com.bguzman.civlint.agents.ReplayAgentAdapter;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.support.CanonicalJson;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Asserts that two executions over identical inputs produce identical canonical output, and that the
 * things which must not affect a verdict do not.
 */
class DeterminismTest {

    private static EvaluationHarness harness(Clock clock) {
        return new EvaluationHarness(new ReplayAgentAdapter(), clock);
    }

    @Test
    @DisplayName("two advanced executions produce the same canonical hash")
    void advancedIsReproducible() {
        String first = harness(Clock.systemUTC()).runAdvanced().canonicalHash();
        String second = harness(Clock.systemUTC()).runAdvanced().canonicalHash();
        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(Metrics.replayAgreement(first, second).value().orElseThrow())
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("two baseline executions produce the same canonical hash")
    void baselineIsReproducible() {
        assertThat(harness(Clock.systemUTC()).runBaseline().canonicalHash())
                .isEqualTo(harness(Clock.systemUTC()).runBaseline().canonicalHash());
    }

    @RepeatedTest(5)
    @DisplayName("repeated executions never diverge")
    void repeatedExecutionsAgree() {
        assertThat(harness(Clock.systemUTC()).runAdvanced().canonicalHash())
                .isEqualTo(harness(Clock.systemUTC()).runAdvanced().canonicalHash());
    }

    @Test
    @DisplayName("the wall clock cannot change a verdict or a canonical hash")
    void clockIsIrrelevantToVerdicts() {
        Clock early = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        Clock late = Clock.fixed(Instant.parse("2031-12-31T23:59:59Z"), ZoneOffset.UTC);

        EvaluationRun a = harness(early).runAdvanced();
        EvaluationRun b = harness(late).runAdvanced();

        assertThat(a.canonicalHash()).isEqualTo(b.canonicalHash());
        assertThat(a.releaseDecision()).isEqualTo(b.releaseDecision());
        assertThat(a.findings()).isEqualTo(b.findings());
        assertThat(a.caseOutcomes()).isEqualTo(b.caseOutcomes());
        // The excluded timing fields do differ, which is why they are excluded.
        assertThat(a.startedAtEpochMilli()).isNotEqualTo(b.startedAtEpochMilli());
    }

    @Test
    @DisplayName("a slow run and a fast run hash identically")
    void durationIsExcludedFromTheHash() {
        Clock ticking = new Clock() {
            private Instant now = Instant.parse("2026-08-28T00:00:00Z");

            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                now = now.plus(Duration.ofSeconds(37));
                return now;
            }
        };
        EvaluationRun slow = harness(ticking).runAdvanced();
        EvaluationRun fast = harness(Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC))
                .runAdvanced();

        assertThat(slow.durationMillis()).isNotEqualTo(fast.durationMillis());
        assertThat(slow.canonicalHash()).isEqualTo(fast.canonicalHash());
    }

    @Test
    @DisplayName("the run identifier is derived from inputs, not from the clock")
    void runIdIsDeterministic() {
        assertThat(harness(Clock.systemUTC()).runAdvanced().runId())
                .isEqualTo(harness(Clock.systemUTC()).runAdvanced().runId())
                .startsWith("RUN.ADVANCED.");
    }

    @Test
    @DisplayName("concurrent agent execution cannot reorder results")
    void concurrencyDoesNotReorder() {
        Set<String> observedOrderings = new LinkedHashSet<>();
        for (int i = 0; i < 12; i++) {
            EvaluationRun run = harness(Clock.systemUTC()).runAdvanced();
            observedOrderings.add(String.join(
                    ",", run.traces().stream().map(t -> t.traceId()).toList()));
        }
        assertThat(observedOrderings)
                .as("every execution ordered its traces identically")
                .hasSize(1);
    }

    @Test
    @DisplayName("the fixture inputs are themselves stably hashable")
    void inputHashesAreStable() {
        assertThat(DemoPolicy.pack().canonicalHash()).isEqualTo(DemoPolicy.pack().canonicalHash());
        assertThat(DemoHumanNecessity.map().canonicalHash())
                .isEqualTo(DemoHumanNecessity.map().canonicalHash());
        assertThat(DemoProcedures.proposedNational().canonicalHash())
                .isEqualTo(DemoProcedures.proposedNational().canonicalHash());
        assertThat(CanonicalJson.write(DemoCases.cases().getFirst().toJson()))
                .isEqualTo(CanonicalJson.write(DemoCases.cases().getFirst().toJson()));
    }

    @Test
    @DisplayName("different procedure versions hash differently")
    void distinctVersionsHashDistinctly() {
        Set<String> hashes = new LinkedHashSet<>();
        hashes.add(DemoProcedures.existingRegional().canonicalHash());
        hashes.add(DemoProcedures.proposedNational().canonicalHash());
        hashes.add(DemoProcedures.proposedWithAppealRemoved().canonicalHash());
        hashes.add(DemoProcedures.proposedWithDutyViolation().canonicalHash());
        assertThat(hashes).hasSize(4);
    }

    @Test
    @DisplayName("renaming a version does not change its canonical hash, but moving a step does")
    void hashCoversStructureNotLabels() {
        var original = DemoProcedures.proposedNational();
        var relabelled = new ProcedureVersion(
                original.procedureId(),
                original.versionId(),
                "A completely different label",
                original.graph(),
                original.policyPackId(),
                original.policyVersion(),
                "Different notes entirely");
        assertThat(relabelled.canonicalHash()).isEqualTo(original.canonicalHash());
        assertThat(DemoProcedures.proposedWithDutyViolation().canonicalHash())
                .isNotEqualTo(original.canonicalHash());
    }

    @Test
    @DisplayName("an unavailable model does not make a run non-deterministic")
    void unavailableModelIsDeterministic() {
        AgentModelPort dead = new AgentModelPort() {
            @Override
            public String invoke(AgentRequest request) {
                throw new AgentUnavailableException("No model configured in this environment");
            }

            @Override
            public String adapterId() {
                return "unavailable";
            }
        };
        EvaluationRun a = new EvaluationHarness(dead, Clock.systemUTC()).runAdvanced();
        EvaluationRun b = new EvaluationHarness(dead, Clock.systemUTC()).runAdvanced();
        assertThat(a.canonicalHash()).isEqualTo(b.canonicalHash());
    }
}
