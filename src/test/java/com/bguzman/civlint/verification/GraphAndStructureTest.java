package com.bguzman.civlint.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bguzman.civlint.domain.ApprovalGate;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.ProcedureGraph;
import com.bguzman.civlint.domain.ProcedureStep;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCategory;
import com.bguzman.civlint.domain.RuleCriterion;
import com.bguzman.civlint.domain.SeparationOfDuty;
import com.bguzman.civlint.domain.StepKind;
import com.bguzman.civlint.domain.Transition;
import com.bguzman.civlint.evaluation.DemoHumanNecessity;
import com.bguzman.civlint.evaluation.DemoPolicy;
import com.bguzman.civlint.evaluation.DemoProcedures;
import com.bguzman.civlint.procedure.VersionComparison;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies graph analysis, version comparison and the structural checks built on them.
 */
class GraphAndStructureTest {

    private static ProcedureStep automated(String id) {
        return new ProcedureStep(id, "Step " + id, StepKind.MECHANICAL_CHECK, DecisionTier.AUTOMATE,
                EnumSet.of(RuleCategory.MECHANICAL), ReviewerRole.NONE, false, 0);
    }

    private static ProcedureStep terminal(String id) {
        return new ProcedureStep(id, "End " + id, StepKind.TERMINAL, DecisionTier.AUTOMATE,
                EnumSet.of(RuleCategory.MECHANICAL), ReviewerRole.NONE, false, 0);
    }

    private static ProcedureGraph graph(List<ProcedureStep> steps, List<Transition> transitions,
            String entry) {
        SequencedMap<String, ProcedureStep> map = new LinkedHashMap<>();
        steps.forEach(step -> map.put(step.stepId(), step));
        return new ProcedureGraph(map, transitions, entry, List.of(), List.of());
    }

    @Nested
    @DisplayName("reachability and paths")
    class Reachability {

        @Test
        @DisplayName("a linear graph reaches every step and reports no unreachable steps")
        void linear() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.B"), terminal("S.C")),
                    List.of(new Transition("S.A", "S.B", "always"),
                            new Transition("S.B", "S.C", "always")),
                    "S.A");
            assertThat(g.reachableStepIds()).containsExactly("S.A", "S.B", "S.C");
            assertThat(g.unreachableStepIds()).isEmpty();
            assertThat(g.terminalStepIds()).containsExactly("S.C");
            assertThat(g.findCycle()).isEmpty();
        }

        @Test
        @DisplayName("an orphaned step is reported unreachable")
        void orphan() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.ORPHAN"), terminal("S.C")),
                    List.of(new Transition("S.A", "S.C", "always")),
                    "S.A");
            assertThat(g.unreachableStepIds()).containsExactly("S.ORPHAN");
            assertThat(g.reachableStepIds()).containsExactly("S.A", "S.C");
        }

        @Test
        @DisplayName("a cycle is found and reported as a concrete path")
        void cycle() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.B"), terminal("S.C")),
                    List.of(new Transition("S.A", "S.B", "always"),
                            new Transition("S.B", "S.A", "loops back"),
                            new Transition("S.A", "S.C", "always")),
                    "S.A");
            assertThat(g.findCycle()).isPresent();
            List<String> cycle = g.findCycle().orElseThrow();
            assertThat(cycle.getFirst()).isEqualTo(cycle.getLast());
            assertThat(cycle).contains("S.A", "S.B");
        }

        @Test
        @DisplayName("cycle detection is deterministic across repeated calls")
        void cycleIsDeterministic() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.B"), automated("S.C"), terminal("S.T")),
                    List.of(new Transition("S.A", "S.B", "1"), new Transition("S.B", "S.C", "2"),
                            new Transition("S.C", "S.B", "3"), new Transition("S.A", "S.T", "4")),
                    "S.A");
            List<String> first = g.findCycle().orElseThrow();
            for (int i = 0; i < 20; i++) {
                assertThat(g.findCycle().orElseThrow()).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("a shortest path is returned inclusive of both endpoints")
        void shortestPath() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.B"), automated("S.C"), terminal("S.D")),
                    List.of(new Transition("S.A", "S.B", "1"), new Transition("S.B", "S.D", "2"),
                            new Transition("S.A", "S.C", "3"), new Transition("S.C", "S.D", "4")),
                    "S.A");
            assertThat(GraphAnalysis.shortestPath(g, "S.A", "S.D")).isPresent();
            assertThat(GraphAnalysis.shortestPath(g, "S.A", "S.D").orElseThrow())
                    .hasSize(3)
                    .startsWith("S.A")
                    .endsWith("S.D");
            assertThat(GraphAnalysis.shortestPath(g, "S.A", "S.A").orElseThrow())
                    .containsExactly("S.A");
            assertThat(GraphAnalysis.shortestPath(g, "S.D", "S.A")).isEmpty();
        }

        @Test
        @DisplayName("a path avoiding a step is found only when a genuine bypass exists")
        void bypass() {
            ProcedureGraph withBypass = graph(
                    List.of(automated("S.A"), automated("S.GATE"), automated("S.SKIP"), terminal("S.T")),
                    List.of(new Transition("S.A", "S.GATE", "1"), new Transition("S.GATE", "S.T", "2"),
                            new Transition("S.A", "S.SKIP", "3"), new Transition("S.SKIP", "S.T", "4")),
                    "S.A");
            assertThat(GraphAnalysis.shortestPathAvoiding(withBypass, "S.A", "S.T", "S.GATE"))
                    .isPresent();
            assertThat(GraphAnalysis.dominates(withBypass, "S.GATE", "S.T")).isFalse();

            ProcedureGraph noBypass = graph(
                    List.of(automated("S.A"), automated("S.GATE"), terminal("S.T")),
                    List.of(new Transition("S.A", "S.GATE", "1"), new Transition("S.GATE", "S.T", "2")),
                    "S.A");
            assertThat(GraphAnalysis.shortestPathAvoiding(noBypass, "S.A", "S.T", "S.GATE")).isEmpty();
            assertThat(GraphAnalysis.dominates(noBypass, "S.GATE", "S.T")).isTrue();
        }

        @Test
        @DisplayName("a dead-end step is reported as unable to conclude")
        void deadEnd() {
            ProcedureGraph g = graph(
                    List.of(automated("S.A"), automated("S.DEAD"), terminal("S.T")),
                    List.of(new Transition("S.A", "S.T", "1"), new Transition("S.A", "S.DEAD", "2")),
                    "S.A");
            assertThat(GraphAnalysis.stepsWithNoTerminal(g)).containsExactly("S.DEAD");
        }

        @Test
        @DisplayName("the demonstration versions are internally sound")
        void demoGraphsAreSound() {
            for (ProcedureVersion version : DemoProcedures.procedure().versions()) {
                assertThat(version.graph().unreachableStepIds())
                        .as("%s has no unreachable step", version.versionId())
                        .isEmpty();
                assertThat(version.graph().findCycle())
                        .as("%s is acyclic", version.versionId())
                        .isEmpty();
                assertThat(GraphAnalysis.stepsWithNoTerminal(version.graph()))
                        .as("%s can always conclude", version.versionId())
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("graph construction invariants")
    class Construction {

        @Test
        @DisplayName("a transition to an undeclared step is rejected")
        void danglingTransition() {
            assertThatThrownBy(() -> graph(
                            List.of(automated("S.A"), terminal("S.T")),
                            List.of(new Transition("S.A", "S.MISSING", "always")),
                            "S.A"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not a declared step");
        }

        @Test
        @DisplayName("an entry step that does not exist is rejected")
        void badEntry() {
            assertThatThrownBy(() -> graph(List.of(terminal("S.T")), List.of(), "S.NOPE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Entry step");
        }

        @Test
        @DisplayName("a graph with no terminal step is rejected")
        void noTerminal() {
            assertThatThrownBy(() -> graph(List.of(automated("S.A")), List.of(), "S.A"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TERMINAL");
        }

        @Test
        @DisplayName("a self-transition is rejected")
        void selfTransition() {
            assertThatThrownBy(() -> new Transition("S.A", "S.A", "loops"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Self-transition");
        }

        @Test
        @DisplayName("duplicate gate sequences are rejected")
        void duplicateGateSequence() {
            SequencedMap<String, ProcedureStep> steps = new LinkedHashMap<>();
            steps.put("S.A", automated("S.A"));
            steps.put("S.T", terminal("S.T"));
            assertThatThrownBy(() -> new ProcedureGraph(
                            steps,
                            List.of(new Transition("S.A", "S.T", "always")),
                            "S.A",
                            List.of(
                                    new ApprovalGate("G.1", "S.A", ReviewerRole.RECORDS_OFFICER, 1, true, true),
                                    new ApprovalGate("G.2", "S.T", ReviewerRole.LEGAL_REVIEWER, 1, true, true)),
                            List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate approval gate sequence");
        }

        @Test
        @DisplayName("a separation-of-duty naming one step for both halves is rejected")
        void degenerateDuty() {
            assertThatThrownBy(() -> new SeparationOfDuty("SOD.X", "S.A", "S.A", "Same step"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same step");
        }

        @Test
        @DisplayName("a mandatory gate must name a human role")
        void mandatoryGateNeedsHuman() {
            assertThatThrownBy(() ->
                            new ApprovalGate("G.X", "S.A", ReviewerRole.NONE, 1, true, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must name a human role");
        }
    }

    @Nested
    @DisplayName("version comparison")
    class Comparison {

        @Test
        @DisplayName("comparing a version with itself reports no difference")
        void identity() {
            VersionComparison same = VersionComparison.compare(
                    DemoProcedures.proposedNational(), DemoProcedures.proposedNational());
            assertThat(same.identical()).isTrue();
            assertThat(same.removedStepIds()).isEmpty();
            assertThat(same.tierChanges()).isEmpty();
            assertThat(same.removedAppealStepIds()).isEmpty();
        }

        @Test
        @DisplayName("the safe migration removes the duplicated read and weakens mechanical tiers")
        void safeMigration() {
            VersionComparison migration = VersionComparison.compare(
                    DemoProcedures.existingRegional(), DemoProcedures.proposedNational());
            assertThat(migration.identical()).isFalse();
            assertThat(migration.removedStepIds()).containsExactly(DemoProcedures.STEP_CLERICAL_TWO);
            assertThat(migration.addedStepIds()).isEmpty();
            assertThat(migration.removedAppealStepIds()).isEmpty();
            assertThat(migration.removedDutyIds()).isEmpty();
            assertThat(migration.weakeningTierChanges()).isNotEmpty();
            assertThat(migration.tierChange(DemoProcedures.STEP_FORMAT)).isPresent();
            assertThat(migration.tierChange(DemoProcedures.STEP_FORMAT).orElseThrow().weakens())
                    .isTrue();
        }

        @Test
        @DisplayName("removing the appeal step is reported as a removed appeal route")
        void appealRemoval() {
            VersionComparison unsafe = VersionComparison.compare(
                    DemoProcedures.existingRegional(), DemoProcedures.proposedWithAppealRemoved());
            assertThat(unsafe.removedAppealStepIds()).contains(DemoProcedures.STEP_APPEAL);
            assertThat(unsafe.removedStepIds()).contains(DemoProcedures.STEP_APPEAL);
        }

        @Test
        @DisplayName("reassigning approval is reported as a role change, not a removal")
        void dutyReassignment() {
            VersionComparison unsafe = VersionComparison.compare(
                    DemoProcedures.existingRegional(), DemoProcedures.proposedWithDutyViolation());
            assertThat(unsafe.removedDutyIds()).isEmpty();
            assertThat(unsafe.roleChanges())
                    .anySatisfy(change -> assertThat(change.stepId()).isEqualTo(DemoProcedures.STEP_APPROVE));
            assertThat(unsafe.removedAppealStepIds()).isEmpty();
        }

        private ProcedureVersion gateVersion(
                String versionId, int sequence, boolean mandatory, boolean appealable) {
            SequencedMap<String, ProcedureStep> steps = new LinkedHashMap<>();
            steps.put("S.A", automated("S.A"));
            steps.put("S.T", terminal("S.T"));
            ProcedureGraph gated = new ProcedureGraph(
                    steps,
                    List.of(new Transition("S.A", "S.T", "always")),
                    "S.A",
                    List.of(new ApprovalGate(
                            "G.1", "S.A", ReviewerRole.RECORDS_OFFICER, sequence, mandatory, appealable)),
                    List.of());
            return new ProcedureVersion(
                    "P.GATE", versionId, "Gate fixture", gated,
                    DemoPolicy.PACK_ID, DemoPolicy.VERSION, "Synthetic gate comparison fixture");
        }

        @Test
        @DisplayName("a gate that stops being mandatory is reported as weakening a safeguard")
        void gateStopsBeingMandatory() {
            VersionComparison change = VersionComparison.compare(
                    gateVersion("V1.GATE", 1, true, true), gateVersion("V2.GATE", 1, false, true));

            assertThat(change.identical()).isFalse();
            assertThat(change.removedGateIds())
                    .as("the gate still exists, so this is a change and not a removal")
                    .isEmpty();
            assertThat(change.gateChanges()).singleElement().satisfies(gate -> {
                assertThat(gate.gateId()).isEqualTo("G.1");
                assertThat(gate.mandatoryLost()).isTrue();
                assertThat(gate.appealabilityLost()).isFalse();
                assertThat(gate.roleChanged()).isFalse();
                assertThat(gate.sequenceChanged()).isFalse();
                assertThat(gate.weakensSafeguard())
                        .as("a gate that can now be skipped is a weakened safeguard")
                        .isTrue();
            });
        }

        @Test
        @DisplayName("a gate that can no longer be appealed is reported as weakening a safeguard")
        void gateLosesAppealability() {
            VersionComparison change = VersionComparison.compare(
                    gateVersion("V1.GATE", 1, true, true), gateVersion("V4.GATE", 1, true, false));

            assertThat(change.removedAppealStepIds())
                    .as("the appeal route step is untouched; only the gate's appealability changed")
                    .isEmpty();
            assertThat(change.gateChanges()).singleElement().satisfies(gate -> {
                assertThat(gate.appealabilityLost()).isTrue();
                assertThat(gate.mandatoryLost()).isFalse();
                assertThat(gate.weakensSafeguard()).isTrue();
            });
        }

        @Test
        @DisplayName("a gate that only moves in the order is reported without weakening a safeguard")
        void gateOnlyMovesInTheOrder() {
            VersionComparison change = VersionComparison.compare(
                    gateVersion("V1.GATE", 1, true, true), gateVersion("V3.GATE", 2, true, true));

            assertThat(change.identical()).isFalse();
            assertThat(change.gateChanges()).singleElement().satisfies(gate -> {
                assertThat(gate.sequenceChanged()).isTrue();
                assertThat(gate.mandatoryLost()).isFalse();
                assertThat(gate.appealabilityLost()).isFalse();
                assertThat(gate.weakensSafeguard())
                        .as("reordering is a factual change, not a lost safeguard")
                        .isFalse();
            });
        }

        @Test
        @DisplayName("versions of different procedures cannot be compared")
        void differentProcedures() {
            ProcedureVersion other = new ProcedureVersion(
                    "OTHER.PROC", "V1", "Other", DemoProcedures.proposedNational().graph(),
                    DemoPolicy.PACK_ID, DemoPolicy.VERSION, "");
            assertThatThrownBy(() ->
                            VersionComparison.compare(DemoProcedures.existingRegional(), other))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different procedures");
        }
    }

    @Nested
    @DisplayName("structural verification")
    class Structural {

        private List<Finding> verify(ProcedureVersion proposed) {
            return StructuralVerifier.verify(
                    DemoPolicy.pack(), DemoProcedures.existingRegional(), proposed,
                    DemoHumanNecessity.map());
        }

        @Test
        @DisplayName("the safe migration produces no release-blocking finding")
        void safeMigrationPasses() {
            List<Finding> findings = verify(DemoProcedures.proposedNational());
            assertThat(findings).isNotEmpty();
            assertThat(findings.stream().filter(Finding::releaseBlocked).toList()).isEmpty();
            assertThat(findings).anySatisfy(f ->
                    assertThat(f.explanationCode()).isEqualTo("HUMAN_GATE_SAFELY_REMOVED"));
        }

        @Test
        @DisplayName("the existing version verified against itself produces no finding at all")
        void selfVerificationIsClean() {
            assertThat(verify(DemoProcedures.existingRegional())).isEmpty();
        }

        @Test
        @DisplayName("removing an appeal route blocks the release and names the step")
        void appealRemovalBlocks() {
            List<Finding> findings = verify(DemoProcedures.proposedWithAppealRemoved());
            List<Finding> blocking = findings.stream().filter(Finding::releaseBlocked).toList();
            assertThat(blocking).isNotEmpty();
            assertThat(blocking).anySatisfy(f -> {
                assertThat(f.explanationCode()).isEqualTo("APPEAL_ROUTE_REMOVED");
                assertThat(f.decisionTier()).isEqualTo(DecisionTier.RELEASE_BLOCKED);
                assertThat(f.counterexample()).isPresent();
                assertThat(f.counterexample().orElseThrow().witnessPath())
                        .contains(DemoProcedures.STEP_APPEAL);
            });
            assertThat(blocking).anySatisfy(f ->
                    assertThat(f.explanationCode()).isEqualTo("HUMAN_GATE_REMOVED"));
        }

        @Test
        @DisplayName("a duty violation blocks the release and names both steps")
        void dutyViolationBlocks() {
            List<Finding> findings = verify(DemoProcedures.proposedWithDutyViolation());
            assertThat(findings).anySatisfy(f -> {
                if (!"SEPARATION_OF_DUTIES_VIOLATED".equals(f.explanationCode())) {
                    return;
                }
                assertThat(f.releaseBlocked()).isTrue();
                assertThat(f.requiredReviewerRole()).isEqualTo(ReviewerRole.LEGAL_REVIEWER);
                var witness = f.counterexample().orElseThrow();
                assertThat(witness.witnessPath())
                        .containsExactly(DemoProcedures.STEP_PREPARE, DemoProcedures.STEP_APPROVE);
                assertThat(witness.witnessValues()).containsEntry("approvingRole", "RECORDS_OFFICER");
                assertThat(witness.witnessValues()).containsEntry("preparingRole", "RECORDS_OFFICER");
            });
            assertThat(findings.stream()
                            .filter(f -> "SEPARATION_OF_DUTIES_VIOLATED".equals(f.explanationCode()))
                            .toList())
                    .hasSize(1);
        }

        @Test
        @DisplayName("an invariant with no governing rule is reported, never silently skipped")
        void undeclaredInvariantIsReported() {
            PolicyPack thin = new PolicyPack(
                    "THIN.PACK", DemoPolicy.VERSION, "Thin pack", "Synthetic",
                    List.of(DemoPolicy.pack().rule("R.CASE.CONFLICT").orElseThrow()));
            List<Finding> findings = StructuralVerifier.verify(
                    thin, DemoProcedures.existingRegional(), DemoProcedures.proposedNational(),
                    DemoHumanNecessity.map());
            List<Finding> undeclared = findings.stream()
                    .filter(f -> StructuralVerifier.CODE_INVARIANT_UNDECLARED.equals(f.explanationCode()))
                    .toList();
            // One finding per structural invariant the pack failed to declare.
            assertThat(undeclared)
                    .hasSize(RuleCriterion.StructuralInvariant.Invariant
                            .values().length);
            undeclared.forEach(f -> {
                assertThat(f.ruleId()).isEqualTo(StructuralVerifier.UNDECLARED_INVARIANT_RULE_ID);
                assertThat(f.requiredReviewerRole().human()).isTrue();
                assertThat(f.explanation()).contains("not enforced");
            });
        }

        @Test
        @DisplayName("a version bound to another policy pack is reported")
        void policyBindingMismatch() {
            ProcedureVersion misbound = new ProcedureVersion(
                    DemoProcedures.PROCEDURE_ID, DemoProcedures.VERSION_PROPOSED, "Misbound",
                    DemoProcedures.proposedNational().graph(), DemoPolicy.PACK_ID, "1999.01.1", "");
            assertThat(verify(misbound))
                    .anySatisfy(f -> assertThat(f.explanationCode()).isEqualTo("POLICY_BINDING_MISMATCH"));
        }

        @Test
        @DisplayName("structural verification is deterministic")
        void deterministic() {
            List<Finding> first = verify(DemoProcedures.proposedWithAppealRemoved());
            for (int i = 0; i < 10; i++) {
                assertThat(verify(DemoProcedures.proposedWithAppealRemoved())).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("null arguments are rejected")
        void nullArguments() {
            assertThatThrownBy(() -> StructuralVerifier.verify(null,
                            DemoProcedures.existingRegional(), DemoProcedures.proposedNational(),
                            DemoHumanNecessity.map()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> StructuralVerifier.verify(DemoPolicy.pack(), null,
                            DemoProcedures.proposedNational(), DemoHumanNecessity.map()))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
