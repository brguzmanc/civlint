package com.bguzman.civlint.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verifies that domain types refuse to be constructed in states the product must never reach.
 *
 * <p>These are the invariants that make later reasoning safe: if an unattended machine cannot be
 * assigned a consequential step at construction time, no downstream code needs to check for it.
 */
class DomainInvariantsTest {

    @Nested
    @DisplayName("DecisionTier")
    class Tiers {

        @Test
        @DisplayName("escalation keeps the more cautious tier and is order-independent")
        void escalation() {
            assertThat(DecisionTier.escalate(DecisionTier.AUTOMATE, DecisionTier.HUMAN_REQUIRED))
                    .isEqualTo(DecisionTier.HUMAN_REQUIRED);
            assertThat(DecisionTier.escalate(DecisionTier.HUMAN_REQUIRED, DecisionTier.AUTOMATE))
                    .isEqualTo(DecisionTier.HUMAN_REQUIRED);
            assertThat(DecisionTier.escalate(DecisionTier.RELEASE_BLOCKED, DecisionTier.HUMAN_REQUIRED))
                    .isEqualTo(DecisionTier.RELEASE_BLOCKED);
        }

        @Test
        @DisplayName("escalation is commutative, associative and idempotent for every pair")
        void escalationAlgebra() {
            for (DecisionTier a : DecisionTier.values()) {
                assertThat(DecisionTier.escalate(a, a)).as("idempotent for %s", a).isEqualTo(a);
                for (DecisionTier b : DecisionTier.values()) {
                    assertThat(DecisionTier.escalate(a, b))
                            .as("commutative for %s,%s", a, b)
                            .isEqualTo(DecisionTier.escalate(b, a));
                    for (DecisionTier c : DecisionTier.values()) {
                        assertThat(DecisionTier.escalate(DecisionTier.escalate(a, b), c))
                                .as("associative for %s,%s,%s", a, b, c)
                                .isEqualTo(DecisionTier.escalate(a, DecisionTier.escalate(b, c)));
                    }
                }
            }
        }

        @ParameterizedTest
        @EnumSource(DecisionTier.class)
        @DisplayName("only RELEASE_BLOCKED blocks a release")
        void blocking(DecisionTier tier) {
            assertThat(tier.blocksRelease()).isEqualTo(tier == DecisionTier.RELEASE_BLOCKED);
            assertThat(tier.explanation()).isNotBlank();
        }

        @ParameterizedTest
        @EnumSource(DecisionTier.class)
        @DisplayName("AUTOMATE is the only tier with no human involvement")
        void humanInvolvement(DecisionTier tier) {
            assertThat(tier.humanInvolved()).isEqualTo(tier != DecisionTier.AUTOMATE);
        }

        @Test
        @DisplayName("weakening is detected in the direction that reduces control")
        void weakening() {
            assertThat(DecisionTier.HUMAN_REQUIRED.weakenedBy(DecisionTier.AUTOMATE)).isTrue();
            assertThat(DecisionTier.AUTOMATE.weakenedBy(DecisionTier.HUMAN_REQUIRED)).isFalse();
            assertThat(DecisionTier.AUTOMATE.weakenedBy(DecisionTier.AUTOMATE)).isFalse();
        }
    }

    @Nested
    @DisplayName("RuleCategory")
    class Categories {

        @Test
        @DisplayName("there are exactly the eleven prescribed categories")
        void count() {
            assertThat(RuleCategory.values()).hasSize(11);
        }

        @ParameterizedTest
        @EnumSource(RuleCategory.class)
        @DisplayName("a category that is not mechanically decidable never defaults to AUTOMATE")
        void ceiling(RuleCategory category) {
            assertThat(category.label()).isNotBlank();
            if (!category.mechanicallyDecidable()) {
                assertThat(category.defaultTier()).isNotEqualTo(DecisionTier.AUTOMATE);
                assertThat(category.defaultTier().humanInvolved()).isTrue();
            }
        }

        @Test
        @DisplayName("the categories reserved for people are the expected ones")
        void reservedCategories() {
            assertThat(EnumSet.allOf(RuleCategory.class).stream()
                            .filter(c -> !c.mechanicallyDecidable())
                            .toList())
                    .containsExactlyInAnyOrder(
                            RuleCategory.LEGAL_AUTHORITY,
                            RuleCategory.ACCESSIBILITY,
                            RuleCategory.DISCRETIONARY,
                            RuleCategory.APPEAL_RIGHTS,
                            RuleCategory.SEPARATION_OF_DUTIES);
        }
    }

    @Nested
    @DisplayName("ReviewerRole")
    class Roles {

        @Test
        @DisplayName("a role may never approve its own preparation")
        void selfApproval() {
            for (ReviewerRole role : ReviewerRole.values()) {
                assertThat(role.canApproveFor(role))
                        .as("%s must not approve for itself", role)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("an unattended machine may approve nothing")
        void noneApprovesNothing() {
            for (ReviewerRole preparer : ReviewerRole.values()) {
                assertThat(ReviewerRole.NONE.canApproveFor(preparer)).isFalse();
            }
        }

        @Test
        @DisplayName("distinct human roles may approve for one another")
        void distinctHumansMayApprove() {
            assertThat(ReviewerRole.REGISTRY_SUPERVISOR.canApproveFor(ReviewerRole.RECORDS_OFFICER))
                    .isTrue();
            assertThat(ReviewerRole.LEGAL_REVIEWER.canApproveFor(ReviewerRole.INTAKE_CLERK)).isTrue();
        }

        @Test
        @DisplayName("a null preparer cannot be approved for")
        void nullPreparer() {
            assertThat(ReviewerRole.REGISTRY_SUPERVISOR.canApproveFor(null)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(ReviewerRole.class)
        @DisplayName("NONE is the only non-human role")
        void humanity(ReviewerRole role) {
            assertThat(role.human()).isEqualTo(role != ReviewerRole.NONE);
            assertThat(role.label()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("ProcedureStep")
    class Steps {

        private ProcedureStep step(StepKind kind, DecisionTier tier, ReviewerRole role) {
            return new ProcedureStep(
                    "S.X", "A step", kind, tier, EnumSet.of(RuleCategory.MECHANICAL), role, false, 1);
        }

        @Test
        @DisplayName("a consequential step cannot be fully automated")
        void consequentialCannotAutomate() {
            for (StepKind kind : StepKind.values()) {
                if (!kind.consequential()) {
                    continue;
                }
                assertThatThrownBy(() -> step(kind, DecisionTier.AUTOMATE, ReviewerRole.NONE))
                        .as("%s must not be automatable", kind)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("imposes an outcome");
            }
        }

        @Test
        @DisplayName("a step needing review must name someone to perform it")
        void reviewNeedsAReviewer() {
            assertThatThrownBy(() ->
                            step(StepKind.MECHANICAL_CHECK, DecisionTier.HUMAN_REQUIRED, ReviewerRole.NONE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no human role");
        }

        @Test
        @DisplayName("an automated step must not name a reviewer")
        void automatedNamesNobody() {
            assertThatThrownBy(() ->
                            step(StepKind.MECHANICAL_CHECK, DecisionTier.AUTOMATE, ReviewerRole.RECORDS_OFFICER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("declares AUTOMATE but assigns role");
        }

        @Test
        @DisplayName("a negative burden is rejected")
        void negativeTouchCost() {
            assertThatThrownBy(() -> new ProcedureStep(
                            "S.X", "A step", StepKind.MECHANICAL_CHECK, DecisionTier.AUTOMATE,
                            EnumSet.noneOf(RuleCategory.class), ReviewerRole.NONE, false, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("touchCost");
        }

        @Test
        @DisplayName("valid combinations are accepted and report their properties")
        void validCombinations() {
            ProcedureStep automated =
                    step(StepKind.MECHANICAL_CHECK, DecisionTier.AUTOMATE, ReviewerRole.NONE);
            assertThat(automated.humanTouch()).isFalse();
            assertThat(automated.mandatoryHumanGate()).isFalse();

            ProcedureStep decision =
                    step(StepKind.DECISION, DecisionTier.HUMAN_REQUIRED, ReviewerRole.REGISTRY_SUPERVISOR);
            assertThat(decision.humanTouch()).isTrue();
            assertThat(decision.mandatoryHumanGate()).isTrue();
            assertThat(decision.reference().kind()).isEqualTo(EvidenceReference.Kind.PROCEDURE_STEP);

            ProcedureStep exception = step(
                    StepKind.MECHANICAL_CHECK, DecisionTier.AUTO_WITH_EXCEPTION, ReviewerRole.RECORDS_OFFICER);
            assertThat(exception.humanTouch()).isTrue();
            assertThat(exception.mandatoryHumanGate()).isFalse();
        }

        @Test
        @DisplayName("categories are copied defensively")
        void categoriesAreImmutable() {
            EnumSet<RuleCategory> mutable = EnumSet.of(RuleCategory.MECHANICAL);
            ProcedureStep created = new ProcedureStep(
                    "S.X", "A step", StepKind.INTAKE, DecisionTier.AUTOMATE, mutable,
                    ReviewerRole.NONE, false, 0);
            mutable.add(RuleCategory.LEGAL_AUTHORITY);
            assertThat(created.categories()).containsExactly(RuleCategory.MECHANICAL);
            assertThatThrownBy(() -> created.categories().add(RuleCategory.DISCRETIONARY))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("PolicyRule")
    class Rules {

        private PolicyRule rule(RuleCategory category, DecisionTier tier, ReviewerRole role,
                Severity severity, boolean blocks) {
            return new PolicyRule(
                    "R.X", category, "A rule", "Synthetic source",
                    new RuleCriterion.EvidenceUsable(), tier, role, severity, blocks, "CODE.X");
        }

        @Test
        @DisplayName("a category reserved for people cannot be automated by a permissive rule")
        void cannotAutomateReservedCategory() {
            assertThatThrownBy(() -> rule(RuleCategory.APPEAL_RIGHTS, DecisionTier.AUTOMATE,
                            ReviewerRole.NONE, Severity.LOW, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not mechanically decidable");
        }

        @Test
        @DisplayName("a rule needing a person must name a role")
        void mustNameRole() {
            assertThatThrownBy(() -> rule(RuleCategory.MECHANICAL, DecisionTier.HUMAN_REQUIRED,
                            ReviewerRole.NONE, Severity.LOW, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("names no human role");
        }

        @Test
        @DisplayName("a release-blocking rule cannot be presented as cosmetic")
        void blockingImpliesSeverity() {
            for (Severity severity : List.of(Severity.INFO, Severity.LOW, Severity.MEDIUM)) {
                assertThatThrownBy(() -> rule(RuleCategory.APPEAL_RIGHTS, DecisionTier.RELEASE_BLOCKED,
                                ReviewerRole.APPEALS_ADJUDICATOR, severity, true))
                        .as("severity %s must not block", severity)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("HIGH or CRITICAL");
            }
            assertThatCode(() -> rule(RuleCategory.APPEAL_RIGHTS, DecisionTier.RELEASE_BLOCKED,
                            ReviewerRole.APPEALS_ADJUDICATOR, Severity.CRITICAL, true))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("HumanNecessity")
    class Necessity {

        private HumanNecessity entry(DecisionTier tier, ReviewerRole role, boolean approved,
                RuleCategory category) {
            return new HumanNecessity(
                    "HN.X", "S.X", category, tier, "Synthetic source", "Reason text",
                    "Impact text", HumanNecessity.Reversibility.FULLY_REVERSIBLE, "Trigger",
                    role, "Minimum evidence", 80, "1", approved);
        }

        @Test
        @DisplayName("an unapproved entry cannot authorise automation")
        void unapprovedCannotAutomate() {
            HumanNecessity draft = entry(DecisionTier.AUTOMATE, ReviewerRole.NONE, false,
                    RuleCategory.MECHANICAL);
            assertThat(draft.tier()).isEqualTo(DecisionTier.AUTOMATE);
            assertThat(draft.effectiveTier()).isEqualTo(DecisionTier.HUMAN_REQUIRED);
            assertThat(draft.mandatoryHumanGate()).isTrue();
        }

        @Test
        @DisplayName("an approved entry is taken at face value")
        void approvedIsHonoured() {
            HumanNecessity approved = entry(DecisionTier.AUTOMATE, ReviewerRole.NONE, true,
                    RuleCategory.MECHANICAL);
            assertThat(approved.effectiveTier()).isEqualTo(DecisionTier.AUTOMATE);
            assertThat(approved.mandatoryHumanGate()).isFalse();
        }

        @Test
        @DisplayName("an entry cannot claim automation for a category reserved for people")
        void cannotAutomateReserved() {
            assertThatThrownBy(() -> entry(DecisionTier.AUTOMATE, ReviewerRole.NONE, true,
                            RuleCategory.DISCRETIONARY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not mechanically decidable");
        }

        @Test
        @DisplayName("confidence must lie within zero and one hundred")
        void confidenceBounds() {
            for (int bad : new int[] {-1, 101, 1000}) {
                assertThatThrownBy(() -> new HumanNecessity(
                                "HN.X", "S.X", RuleCategory.MECHANICAL, DecisionTier.AUTOMATE,
                                "Source", "Reason", "Impact",
                                HumanNecessity.Reversibility.FULLY_REVERSIBLE, "Trigger",
                                ReviewerRole.NONE, "Evidence", bad, "1", true))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("confidence");
            }
        }
    }

    @Nested
    @DisplayName("Finding and ReleaseDecision")
    class Findings {

        private Finding finding(DecisionTier tier, boolean blocked, List<EvidenceReference> refs) {
            return new Finding(
                    "F.X", new FindingSubject.OfCase("CASE.X"), Severity.HIGH,
                    RuleCategory.APPEAL_RIGHTS, tier, "R.X", "Explanation text", "CODE.X",
                    refs, Optional.empty(),
                    tier.humanInvolved() ? ReviewerRole.APPEALS_ADJUDICATOR : ReviewerRole.NONE,
                    blocked);
        }

        @Test
        @DisplayName("a finding must carry evidence")
        void needsEvidence() {
            assertThatThrownBy(() -> finding(DecisionTier.HUMAN_REQUIRED, false, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one evidence reference");
        }

        @Test
        @DisplayName("the blocking flag must agree with the tier")
        void blockingAgreesWithTier() {
            List<EvidenceReference> refs = List.of(EvidenceReference.rule("R.X", "A rule"));
            assertThatThrownBy(() -> finding(DecisionTier.HUMAN_REQUIRED, true, refs))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("the two must agree");
            assertThatThrownBy(() -> finding(DecisionTier.RELEASE_BLOCKED, false, refs))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("the two must agree");
            assertThatCode(() -> finding(DecisionTier.RELEASE_BLOCKED, true, refs))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("finding identifiers are a pure function of rule and subject")
        void identifiersAreDeterministic() {
            FindingSubject subject = new FindingSubject.OfStep("S.APPEAL");
            assertThat(Finding.deterministicId("R.APPEAL", subject))
                    .isEqualTo(Finding.deterministicId("R.APPEAL", subject))
                    .isEqualTo("F.R.APPEAL.STEP.S.APPEAL");
            assertThat(Finding.deterministicId("R.OTHER", subject))
                    .isNotEqualTo(Finding.deterministicId("R.APPEAL", subject));
        }

        @Test
        @DisplayName("an over-long identifier is shortened deterministically without colliding")
        void longIdentifiersAreBounded() {
            String longRule = "R." + "X".repeat(60);
            FindingSubject subjectA = new FindingSubject.OfStep("S." + "A".repeat(60));
            FindingSubject subjectB = new FindingSubject.OfStep("S." + "B".repeat(60));

            String idA = Finding.deterministicId(longRule, subjectA);
            String idB = Finding.deterministicId(longRule, subjectB);

            assertThat(idA).hasSize(Finding.MAX_ID_LENGTH);
            assertThat(idB).hasSize(Finding.MAX_ID_LENGTH);
            assertThat(idA).isNotEqualTo(idB);
            assertThat(idA).isEqualTo(Finding.deterministicId(longRule, subjectA));
            assertThat(idA).matches("[A-Za-z0-9_.-]+");
        }

        @Test
        @DisplayName("the release decision is a pure function of the findings")
        void releaseIsDerived() {
            List<EvidenceReference> refs = List.of(EvidenceReference.rule("R.X", "A rule"));
            assertThat(ReleaseDecision.from(List.of()).outcome())
                    .isEqualTo(ReleaseDecision.Outcome.ALLOW);
            assertThat(ReleaseDecision.from(List.of(finding(DecisionTier.HUMAN_REQUIRED, false, refs)))
                            .outcome())
                    .isEqualTo(ReleaseDecision.Outcome.ALLOW);
            ReleaseDecision blocked =
                    ReleaseDecision.from(List.of(finding(DecisionTier.RELEASE_BLOCKED, true, refs)));
            assertThat(blocked.outcome()).isEqualTo(ReleaseDecision.Outcome.BLOCK);
            assertThat(blocked.blocked()).isTrue();
            assertThat(blocked.blockingFindingIds()).containsExactly("F.X");
        }

        @Test
        @DisplayName("a decision cannot claim ALLOW while naming blocking findings")
        void decisionMustBeConsistent() {
            assertThatThrownBy(() -> new ReleaseDecision(
                            ReleaseDecision.Outcome.ALLOW, List.of("F.X"), "Inconsistent"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disagrees");
            assertThatThrownBy(() -> new ReleaseDecision(
                            ReleaseDecision.Outcome.BLOCK, List.of(), "Inconsistent"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disagrees");
        }
    }

    @Nested
    @DisplayName("MetricResult")
    class MetricResults {

        @Test
        @DisplayName("a metric has either a value or a reason, never both or neither")
        void exclusivity() {
            assertThatThrownBy(() -> new MetricResult(
                            "M.X", "Label", Optional.of(BigDecimal.ONE), MetricResult.Unit.COUNT, "reason"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one");
            assertThatThrownBy(() -> new MetricResult(
                            "M.X", "Label", Optional.empty(), MetricResult.Unit.COUNT, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one");
        }

        @Test
        @DisplayName("a zero denominator yields UNAVAILABLE, not zero percent")
        void zeroDenominator() {
            MetricResult metric = MetricResult.percent("M.X", "Label", 0, 0);
            assertThat(metric.measured()).isFalse();
            assertThat(metric.display()).isEqualTo("UNAVAILABLE");
            assertThat(metric.unavailableReason()).contains("denominator is zero");
        }

        @Test
        @DisplayName("percentages are rounded half-even to two places")
        void rounding() {
            assertThat(MetricResult.percent("M.X", "L", 1, 3).value().orElseThrow())
                    .isEqualByComparingTo("33.33");
            assertThat(MetricResult.percent("M.X", "L", 2, 3).value().orElseThrow())
                    .isEqualByComparingTo("66.67");
            assertThat(MetricResult.percent("M.X", "L", 3, 5).value().orElseThrow())
                    .isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("a percentage outside the valid range is rejected")
        void percentBounds() {
            assertThatThrownBy(() -> new MetricResult(
                            "M.X", "L", Optional.of(new BigDecimal("101")),
                            MetricResult.Unit.PERCENT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside [0,100]");
        }

        @Test
        @DisplayName("display formats each unit distinctly")
        void display() {
            assertThat(MetricResult.count("M.A", "L", 7).display()).isEqualTo("7");
            assertThat(MetricResult.percent("M.B", "L", 1, 2).display()).isEqualTo("50.00%");
            assertThat(MetricResult.millis("M.C", "L", 12).display()).isEqualTo("12 ms");
            assertThat(MetricResult.touchUnits("M.D", "L", 5).display()).isEqualTo("5 touch units");
        }

        @Test
        @DisplayName("an unavailable metric requires a stated reason")
        void unavailableNeedsReason() {
            assertThatThrownBy(() -> MetricResult.unavailable("M.X", "L", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("EvidenceItem and CorrectionRequest")
    class Requests {

        @Test
        @DisplayName("an illegible item is never usable mechanically")
        void illegibleIsUnusable() {
            EvidenceItem illegible = new EvidenceItem(
                    "E.X", EvidenceType.BIRTH_RECORD_EXTRACT, "Office", "REF", true, false,
                    new TreeMap<>());
            assertThat(illegible.usableMechanically()).isFalse();
        }

        @Test
        @DisplayName("an uncertified authoritative item is not usable mechanically")
        void uncertifiedAuthoritativeIsUnusable() {
            EvidenceItem uncertified = new EvidenceItem(
                    "E.X", EvidenceType.BIRTH_RECORD_EXTRACT, "Office", "REF", false, true,
                    new TreeMap<>());
            assertThat(uncertified.usableMechanically()).isFalse();

            EvidenceItem supporting = new EvidenceItem(
                    "E.Y", EvidenceType.SWORN_DECLARATION, "Office", "REF", false, true,
                    new TreeMap<>());
            assertThat(supporting.usableMechanically()).isTrue();
        }

        @Test
        @DisplayName("duplicate evidence identifiers are rejected")
        void duplicateEvidence() {
            EvidenceItem item = new EvidenceItem(
                    "E.X", EvidenceType.IDENTITY_DOCUMENT, "Office", "REF", true, true, new TreeMap<>());
            assertThatThrownBy(() -> new CorrectionRequest(
                            "CASE.X", record(), new TreeMap<>(), List.of(item, item), EnumSet.noneOf(RequestFlag.class)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate evidence identifier");
        }

        @Test
        @DisplayName("changed fields exclude values that already match")
        void changedFields() {
            TreeMap<String, String> requested = new TreeMap<>();
            requested.put(RegistryRecord.GIVEN_NAME, "María");
            requested.put(RegistryRecord.SURNAME, "Ortiz");
            CorrectionRequest request = new CorrectionRequest(
                    "CASE.X", record(), requested, List.of(), EnumSet.noneOf(RequestFlag.class));
            assertThat(request.changedFields()).containsExactly(RegistryRecord.SURNAME);
        }

        @Test
        @DisplayName("evidence is stored sorted, so equal requests hash equally")
        void evidenceIsSorted() {
            EvidenceItem a = new EvidenceItem(
                    "E.AAA", EvidenceType.IDENTITY_DOCUMENT, "Office", "REF", true, true, new TreeMap<>());
            EvidenceItem b = new EvidenceItem(
                    "E.BBB", EvidenceType.IDENTITY_DOCUMENT, "Office", "REF", true, true, new TreeMap<>());
            CorrectionRequest forward = new CorrectionRequest(
                    "CASE.X", record(), new TreeMap<>(), List.of(a, b), EnumSet.noneOf(RequestFlag.class));
            CorrectionRequest reverse = new CorrectionRequest(
                    "CASE.X", record(), new TreeMap<>(), List.of(b, a), EnumSet.noneOf(RequestFlag.class));
            assertThat(forward).isEqualTo(reverse);
            assertThat(forward.canonicalHash()).isEqualTo(reverse.canonicalHash());
        }

        private RegistryRecord record() {
            TreeMap<String, String> fields = new TreeMap<>();
            fields.put(RegistryRecord.GIVEN_NAME, "María");
            fields.put(RegistryRecord.SURNAME, "Serrano-Vidal");
            return new RegistryRecord("R.X", "RG.X", fields, false);
        }
    }

    @Nested
    @DisplayName("AgentTrace")
    class Traces {

        @Test
        @DisplayName("a rejected invocation may not carry observations")
        void rejectedCarriesNothing() {
            AgentObservation observation = new AgentObservation(
                    "OBS.X", "AGENT.X", new FindingSubject.OfCase("CASE.X"), DecisionTier.AUTOMATE,
                    RuleCategory.MECHANICAL, "Reason", 50, List.of());
            String hash = "b".repeat(64);
            for (AgentTrace.Status status :
                    List.of(AgentTrace.Status.SCHEMA_REJECTED, AgentTrace.Status.FAILED)) {
                assertThatThrownBy(() -> new AgentTrace(
                                "T.X", "AGENT.X", "0.1.0", hash, hash, List.of("V1"),
                                List.of(observation),
                                List.of(new AgentTrace.TraceEvent(
                                        1, AgentTrace.TraceEvent.Kind.COMPLETED, "done")),
                                0, status))
                        .as("status %s", status)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("must not carry observations");
            }
        }

        @Test
        @DisplayName("hashes must be lower-case hexadecimal digests")
        void hashShape() {
            String good = "c".repeat(64);
            assertThatThrownBy(() -> new AgentTrace(
                            "T.X", "AGENT.X", "0.1.0", "not-a-hash", good, List.of(), List.of(),
                            List.of(), 0, AgentTrace.Status.COMPLETED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SHA-256 digest");
            assertThatThrownBy(() -> new AgentTrace(
                            "T.X", "AGENT.X", "0.1.0", good, "ABCDEF".repeat(10) + "abcd", List.of(),
                            List.of(), List.of(), 0, AgentTrace.Status.COMPLETED))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("only a completed invocation reports itself usable")
        void usability() {
            String hash = "d".repeat(64);
            for (AgentTrace.Status status : AgentTrace.Status.values()) {
                AgentTrace trace = new AgentTrace(
                        "T.X", "AGENT.X", "0.1.0", hash, hash, List.of(), List.of(), List.of(), 0, status);
                assertThat(trace.usable()).isEqualTo(status == AgentTrace.Status.COMPLETED);
            }
        }
    }
}
