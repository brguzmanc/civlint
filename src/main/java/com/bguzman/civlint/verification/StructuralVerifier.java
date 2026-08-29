package com.bguzman.civlint.verification;

import com.bguzman.civlint.domain.ApprovalGate;
import com.bguzman.civlint.domain.Counterexample;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvidenceReference;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.FindingSubject;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.PolicyRule;
import com.bguzman.civlint.domain.ProcedureStep;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCategory;
import com.bguzman.civlint.domain.RuleCriterion;
import com.bguzman.civlint.domain.RuleCriterion.StructuralInvariant.Invariant;
import com.bguzman.civlint.domain.SeparationOfDuty;
import com.bguzman.civlint.domain.Severity;
import com.bguzman.civlint.procedure.VersionComparison;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Checks a proposed procedure version against the existing version, the policy pack and the Human
 * Necessity Map.
 *
 * <p>This is where a modernisation is separated from a regression. Removing a human step is not
 * inherently wrong — removing a duplicated clerical sign-off is the point of the exercise — so the
 * decisive question is never "did a human step disappear?" but "does the approved Human Necessity Map
 * still require one here?". Removals the map permits are recorded as safe; removals it forbids block
 * the release.
 *
 * <p>Where the policy pack declares no rule for an invariant, the check is <em>not</em> silently
 * skipped: a finding is raised against the pack itself, because an unenforced safety invariant looks
 * identical to a satisfied one in any report that omits it.
 *
 * <p><strong>Side effects:</strong> none; stateless and deterministic.
 */
public final class StructuralVerifier {

    /** Rule identifier used when the policy pack declares no rule for a structural invariant. */
    public static final String UNDECLARED_INVARIANT_RULE_ID = "CIVLINT.UNDECLARED-INVARIANT";

    /** Explanation code emitted when a structural invariant has no governing rule. */
    public static final String CODE_INVARIANT_UNDECLARED = "STRUCTURAL_INVARIANT_UNDECLARED";

    private StructuralVerifier() {
        throw new AssertionError("No instances.");
    }

    public static List<Finding> verify(
            PolicyPack pack,
            ProcedureVersion existing,
            ProcedureVersion proposed,
            HumanNecessityMap map) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(map, "map");

        VersionComparison comparison = VersionComparison.compare(existing, proposed);
        List<Finding> findings = new ArrayList<>();

        for (Invariant invariant : Invariant.values()) {
            Optional<PolicyRule> rule = ruleFor(pack, invariant);
            if (rule.isEmpty()) {
                findings.add(undeclaredInvariantFinding(pack, invariant));
                continue;
            }
            findings.addAll(check(invariant, rule.get(), pack, existing, proposed, map, comparison));
        }
        return findings.stream().sorted(Finding.SORT_ORDER).toList();
    }

    private static Optional<PolicyRule> ruleFor(PolicyPack pack, Invariant invariant) {
        return pack.rules().stream()
                .filter(rule -> rule.criterion() instanceof RuleCriterion.StructuralInvariant si
                        && si.invariant() == invariant)
                .findFirst();
    }

    private static List<Finding> check(
            Invariant invariant,
            PolicyRule rule,
            PolicyPack pack,
            ProcedureVersion existing,
            ProcedureVersion proposed,
            HumanNecessityMap map,
            VersionComparison comparison) {
        return switch (invariant) {
            case ALL_STEPS_REACHABLE -> reachability(rule, proposed);
            case NO_CYCLES -> cycles(rule, proposed);
            case TERMINAL_REACHABLE -> terminals(rule, proposed);
            case APPROVAL_ORDER_HELD -> approvalOrder(rule, proposed);
            case SEPARATION_OF_DUTIES_HELD -> separationOfDuties(rule, proposed);
            case APPEAL_ROUTE_PRESERVED -> appealRoutes(rule, existing, proposed, comparison);
            case HUMAN_GATE_PRESERVED -> humanGates(rule, existing, proposed, map, comparison);
            case TIER_PERMITTED_FOR_CATEGORY -> tierPermitted(rule, proposed);
            case POLICY_BINDING_CONSISTENT -> policyBinding(rule, pack, proposed);
        };
    }

    private static List<Finding> reachability(PolicyRule rule, ProcedureVersion proposed) {
        List<Finding> findings = new ArrayList<>();
        for (String stepId : proposed.graph().unreachableStepIds()) {
            FindingSubject subject = new FindingSubject.OfStep(stepId);
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.AUTO_WITH_EXCEPTION,
                    "Step " + stepId + " cannot be reached from entry step "
                            + proposed.graph().entryStepId()
                            + ", so the work it describes would never be performed.",
                    "STEP_UNREACHABLE",
                    List.of(EvidenceReference.step(stepId, "Unreachable step")),
                    Optional.of(Counterexample.of(
                            "CX.UNREACHABLE." + stepId,
                            Counterexample.Kind.STEP_UNREACHABLE,
                            "No path exists from " + proposed.graph().entryStepId() + " to " + stepId,
                            List.of(proposed.graph().entryStepId(), stepId)))));
        }
        return findings;
    }

    private static List<Finding> cycles(PolicyRule rule, ProcedureVersion proposed) {
        return proposed.graph().findCycle()
                .map(cycle -> {
                    FindingSubject subject = new FindingSubject.OfVersion(proposed.versionId());
                    return List.of(build(
                            rule,
                            subject,
                            DecisionTier.AUTO_WITH_EXCEPTION,
                            "The step graph contains a cycle: " + String.join(" -> ", cycle)
                                    + ". A case entering it may never conclude.",
                            "CYCLE_PRESENT",
                            cycle.stream()
                                    .distinct()
                                    .map(id -> EvidenceReference.step(id, "Step in cycle"))
                                    .toList(),
                            Optional.of(Counterexample.of(
                                    "CX.CYCLE." + proposed.versionId(),
                                    Counterexample.Kind.CYCLE_PRESENT,
                                    "Cycle: " + String.join(" -> ", cycle),
                                    cycle))));
                })
                .orElseGet(List::of);
    }

    private static List<Finding> terminals(PolicyRule rule, ProcedureVersion proposed) {
        List<Finding> findings = new ArrayList<>();
        for (String stepId : GraphAnalysis.stepsWithNoTerminal(proposed.graph())) {
            FindingSubject subject = new FindingSubject.OfStep(stepId);
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.AUTO_WITH_EXCEPTION,
                    "No terminal step is reachable from " + stepId
                            + ", so a case that arrives there can never be concluded.",
                    "NO_TERMINAL_REACHABLE",
                    List.of(EvidenceReference.step(stepId, "Dead-end step")),
                    Optional.of(Counterexample.of(
                            "CX.DEADEND." + stepId,
                            Counterexample.Kind.NO_TERMINAL_REACHABLE,
                            "No terminal step is reachable from " + stepId,
                            List.of(stepId)))));
        }
        return findings;
    }

    private static List<Finding> approvalOrder(PolicyRule rule, ProcedureVersion proposed) {
        List<Finding> findings = new ArrayList<>();
        List<ApprovalGate> gates = proposed.graph().approvalGates();
        for (int i = 0; i < gates.size(); i++) {
            for (int j = i + 1; j < gates.size(); j++) {
                ApprovalGate earlier = gates.get(i);
                ApprovalGate later = gates.get(j);
                if (!earlier.mandatory()) {
                    continue;
                }
                Optional<List<String>> bypass = GraphAnalysis.shortestPathAvoiding(
                        proposed.graph(),
                        proposed.graph().entryStepId(),
                        later.stepId(),
                        earlier.stepId());
                if (bypass.isEmpty()) {
                    continue;
                }
                FindingSubject subject = new FindingSubject.OfGate(later.gateId());
                TreeMap<String, String> values = new TreeMap<>();
                values.put("bypassedGateId", earlier.gateId());
                values.put("bypassedSequence", String.valueOf(earlier.sequence()));
                values.put("reachedGateId", later.gateId());
                values.put("reachedSequence", String.valueOf(later.sequence()));
                findings.add(build(
                        rule,
                        subject,
                        DecisionTier.HUMAN_REQUIRED,
                        "Gate " + later.gateId() + " (sequence " + later.sequence()
                                + ") can be reached without passing mandatory gate " + earlier.gateId()
                                + " (sequence " + earlier.sequence() + ") via "
                                + String.join(" -> ", bypass.get())
                                + ", so the declared approval order is not enforced by the graph.",
                        "APPROVAL_ORDER_VIOLATED",
                        List.of(earlier.reference(), later.reference()),
                        Optional.of(new Counterexample(
                                "CX.ORDER." + earlier.gateId() + "." + later.gateId(),
                                Counterexample.Kind.APPROVAL_ORDER_VIOLATED,
                                "A path reaches " + later.gateId() + " while avoiding "
                                        + earlier.gateId(),
                                bypass.get(),
                                values))));
            }
        }
        return findings;
    }

    private static List<Finding> separationOfDuties(PolicyRule rule, ProcedureVersion proposed) {
        List<Finding> findings = new ArrayList<>();
        for (SeparationOfDuty duty : proposed.graph().separationOfDuties()) {
            Optional<ProcedureStep> preparing = proposed.graph().step(duty.preparingStepId());
            Optional<ProcedureStep> approving = proposed.graph().step(duty.approvingStepId());
            if (preparing.isEmpty() || approving.isEmpty()) {
                continue;
            }
            ReviewerRole preparer = preparing.get().requiredRole();
            ReviewerRole approver = approving.get().requiredRole();
            if (approver.canApproveFor(preparer)) {
                continue;
            }
            FindingSubject subject =
                    new FindingSubject.OfStepPair(duty.preparingStepId(), duty.approvingStepId());
            TreeMap<String, String> values = new TreeMap<>();
            values.put("dutyId", duty.dutyId());
            values.put("preparingRole", preparer.name());
            values.put("approvingRole", approver.name());
            String detail = preparer == approver
                    ? "the same role, " + preparer.label() + ", both prepares and approves"
                    : "the approving role " + approver.label() + " cannot give a valid approval";
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.RELEASE_BLOCKED,
                    "Separation of duty " + duty.dutyId() + " is violated: " + detail
                            + " (prepare at " + duty.preparingStepId() + ", approve at "
                            + duty.approvingStepId() + ").",
                    "SEPARATION_OF_DUTIES_VIOLATED",
                    List.of(
                            duty.reference(),
                            preparing.get().reference(),
                            approving.get().reference()),
                    Optional.of(new Counterexample(
                            "CX.SOD." + duty.dutyId(),
                            Counterexample.Kind.SAME_ROLE_PREPARES_AND_APPROVES,
                            detail,
                            List.of(duty.preparingStepId(), duty.approvingStepId()),
                            values))));
        }
        return findings;
    }

    private static List<Finding> appealRoutes(
            PolicyRule rule,
            ProcedureVersion existing,
            ProcedureVersion proposed,
            VersionComparison comparison) {
        List<Finding> findings = new ArrayList<>();

        for (String stepId : comparison.removedAppealStepIds()) {
            FindingSubject subject = new FindingSubject.OfStep(stepId);
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.RELEASE_BLOCKED,
                    "Appeal-route step " + stepId + " exists in version " + existing.versionId()
                            + " but not in " + proposed.versionId()
                            + ", removing a route by which an applicant can challenge a decision.",
                    "APPEAL_ROUTE_REMOVED",
                    List.of(EvidenceReference.step(
                            stepId, "Appeal-route step present only in the existing version")),
                    Optional.of(Counterexample.of(
                            "CX.APPEAL." + stepId,
                            Counterexample.Kind.APPEAL_ROUTE_REMOVED,
                            "Step " + stepId + " is on the appeal route of " + existing.versionId()
                                    + " and absent from " + proposed.versionId(),
                            List.of(stepId)))));
        }

        for (VersionComparison.GateChange change : comparison.gateChanges()) {
            if (!change.appealabilityLost()) {
                continue;
            }
            FindingSubject subject = new FindingSubject.OfGate(change.gateId());
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.RELEASE_BLOCKED,
                    "Gate " + change.gateId() + " could be appealed in " + existing.versionId()
                            + " and cannot in " + proposed.versionId()
                            + ", weakening the right to review.",
                    "APPEAL_RIGHT_WEAKENED",
                    List.of(EvidenceReference.step(
                            change.gateId(), "Gate that lost appealability")),
                    Optional.of(Counterexample.of(
                            "CX.APPEALGATE." + change.gateId(),
                            Counterexample.Kind.APPEAL_RIGHT_WEAKENED,
                            "Gate " + change.gateId() + " lost appealability",
                            List.of(change.gateId())))));
        }
        return findings;
    }

    private static List<Finding> humanGates(
            PolicyRule rule,
            ProcedureVersion existing,
            ProcedureVersion proposed,
            HumanNecessityMap map,
            VersionComparison comparison) {
        List<Finding> findings = new ArrayList<>();

        // The map is authoritative: wherever it mandates a human decision, the proposed version must
        // still have one, whether or not the existing version did.
        for (String stepId : map.mandatoryHumanGateStepIds()) {
            Optional<ProcedureStep> step = proposed.graph().step(stepId);
            boolean satisfied = step.isPresent() && step.get().mandatoryHumanGate();
            if (satisfied) {
                continue;
            }
            FindingSubject subject = new FindingSubject.OfStep(stepId);
            String detail = step.isEmpty()
                    ? "the step is absent from " + proposed.versionId()
                    : "the step declares " + step.get().declaredTier() + ", which does not mandate a person";
            TreeMap<String, String> values = new TreeMap<>();
            values.put("stepId", stepId);
            values.put("proposedTier", step.map(s -> s.declaredTier().name()).orElse("ABSENT"));
            map.requiredTierForStep(stepId)
                    .ifPresent(tier -> values.put("mapRequiredTier", tier.name()));
            findings.add(build(
                    rule,
                    subject,
                    DecisionTier.RELEASE_BLOCKED,
                    "The Human Necessity Map requires a human decision at " + stepId + " but "
                            + detail + ".",
                    "HUMAN_GATE_REMOVED",
                    List.of(EvidenceReference.step(stepId, "Step requiring a mandatory human gate")),
                    Optional.of(new Counterexample(
                            "CX.GATE." + stepId,
                            Counterexample.Kind.HUMAN_GATE_REMOVED,
                            "Mandatory human gate at " + stepId + " is not present in "
                                    + proposed.versionId(),
                            List.of(stepId),
                            values))));
        }

        // Human steps the map does not require may be removed. Recording these as findings — at
        // AUTOMATE severity INFO — is what lets the report show the automation that was actually
        // achieved, rather than only the changes that were refused.
        for (String stepId : existing.graph().mandatoryHumanGateStepIds()) {
            if (map.mandatoryHumanGateStepIds().contains(stepId)) {
                continue;
            }
            Optional<ProcedureStep> step = proposed.graph().step(stepId);
            boolean stillMandatory = step.isPresent() && step.get().mandatoryHumanGate();
            if (stillMandatory) {
                continue;
            }
            FindingSubject subject = new FindingSubject.OfStep(stepId);
            findings.add(new Finding(
                    Finding.deterministicId(rule.ruleId() + ".SAFE", subject),
                    subject,
                    Severity.INFO,
                    rule.category(),
                    DecisionTier.AUTOMATE,
                    rule.ruleId(),
                    "Human involvement at " + stepId + " was removed or reduced, and the approved "
                            + "Human Necessity Map does not require a person there, so the change is "
                            + "recorded as a safe reduction rather than a regression.",
                    "HUMAN_GATE_SAFELY_REMOVED",
                    List.of(
                            EvidenceReference.step(stepId, "Step whose human involvement was reduced"),
                            rule.reference()),
                    Optional.empty(),
                    ReviewerRole.NONE,
                    false));
        }

        for (VersionComparison.TierChange change : comparison.tierChanges()) {
            if (!change.weakens()) {
                continue;
            }
            boolean mapRequiresHuman = map.requiredTierForStep(change.stepId())
                    .map(DecisionTier::mandatoryHumanGate)
                    .orElse(false);
            if (mapRequiresHuman) {
                // Already reported by the authoritative pass above.
                continue;
            }
            if (change.proposedTier().humanInvolved()) {
                continue;
            }
            FindingSubject subject = new FindingSubject.OfStep(change.stepId());
            findings.add(new Finding(
                    Finding.deterministicId(rule.ruleId() + ".TIER", subject),
                    subject,
                    Severity.LOW,
                    rule.category(),
                    DecisionTier.AUTO_WITH_EXCEPTION,
                    rule.ruleId(),
                    "Step " + change.stepId() + " moves from " + change.existingTier() + " to "
                            + change.proposedTier()
                            + ". The Human Necessity Map permits it, but the change removes all human "
                            + "involvement and should be confirmed before release.",
                    "TIER_WEAKENED_WITHIN_POLICY",
                    List.of(EvidenceReference.step(change.stepId(), "Step whose tier was weakened"), rule.reference()),
                    Optional.empty(),
                    ReviewerRole.REGISTRY_SUPERVISOR,
                    false));
        }
        return findings;
    }

    private static List<Finding> tierPermitted(PolicyRule rule, ProcedureVersion proposed) {
        List<Finding> findings = new ArrayList<>();
        for (ProcedureStep step : proposed.graph().steps().values()) {
            for (RuleCategory category : step.categories()) {
                if (category.mechanicallyDecidable()
                        || !step.declaredTier().equals(DecisionTier.AUTOMATE)) {
                    continue;
                }
                FindingSubject subject = new FindingSubject.OfStep(step.stepId());
                TreeMap<String, String> values = new TreeMap<>();
                values.put("stepId", step.stepId());
                values.put("category", category.name());
                values.put("declaredTier", step.declaredTier().name());
                findings.add(build(
                        rule,
                        subject,
                        DecisionTier.RELEASE_BLOCKED,
                        "Step " + step.stepId() + " declares AUTOMATE while engaging category "
                                + category.label()
                                + ", which policy does not permit a machine to conclude.",
                        "TIER_NOT_PERMITTED",
                        List.of(step.reference()),
                        Optional.of(new Counterexample(
                                "CX.TIER." + step.stepId() + "." + category.name(),
                                Counterexample.Kind.TIER_NOT_PERMITTED,
                                "Category " + category.label() + " cannot be automated",
                                List.of(step.stepId()),
                                values))));
            }
        }
        return findings;
    }

    private static List<Finding> policyBinding(
            PolicyRule rule, PolicyPack pack, ProcedureVersion proposed) {
        if (proposed.policyPackId().equals(pack.packId())
                && proposed.policyVersion().equals(pack.version())) {
            return List.of();
        }
        FindingSubject subject = new FindingSubject.OfVersion(proposed.versionId());
        return List.of(build(
                rule,
                subject,
                DecisionTier.HUMAN_REQUIRED,
                "Version " + proposed.versionId() + " is written against policy "
                        + proposed.policyPackId() + " " + proposed.policyVersion()
                        + " but is being verified against " + pack.packId() + " " + pack.version()
                        + ". The verdict cannot be relied on until the binding is reconciled.",
                "POLICY_BINDING_MISMATCH",
                List.of(new EvidenceReference(
                        EvidenceReference.Kind.POLICY_RULE, pack.packId(), "Policy pack in force")),
                Optional.empty()));
    }

    private static Finding undeclaredInvariantFinding(PolicyPack pack, Invariant invariant) {
        FindingSubject subject = new FindingSubject.OfPolicy(pack.packId());
        return new Finding(
                Finding.deterministicId(UNDECLARED_INVARIANT_RULE_ID + "." + invariant.name(), subject),
                subject,
                Severity.HIGH,
                RuleCategory.RETENTION_AUDIT,
                DecisionTier.HUMAN_REQUIRED,
                UNDECLARED_INVARIANT_RULE_ID,
                "Policy pack " + pack.packId() + " declares no rule for structural invariant "
                        + invariant.name()
                        + ", so that invariant is not enforced. An unenforced invariant is "
                        + "indistinguishable from a satisfied one in a report, so it is raised here "
                        + "rather than skipped.",
                CODE_INVARIANT_UNDECLARED,
                List.of(new EvidenceReference(
                        EvidenceReference.Kind.POLICY_RULE,
                        pack.packId(),
                        "Policy pack missing invariant " + invariant.name())),
                Optional.empty(),
                ReviewerRole.LEGAL_REVIEWER,
                false);
    }

    private static Finding build(
            PolicyRule rule,
            FindingSubject subject,
            DecisionTier tier,
            String explanation,
            String code,
            List<EvidenceReference> references,
            Optional<Counterexample> counterexample) {
        DecisionTier effective = DecisionTier.escalate(tier, rule.tierWhenEngaged());
        List<EvidenceReference> allReferences = new ArrayList<>(references);
        allReferences.add(rule.reference());
        ReviewerRole role = effective.humanInvolved() && !rule.requiredRole().human()
                ? ReviewerRole.REGISTRY_SUPERVISOR
                : rule.requiredRole();
        Severity severity = effective == DecisionTier.RELEASE_BLOCKED
                ? Severity.max(rule.severity(), Severity.CRITICAL)
                : rule.severity();
        return new Finding(
                Finding.deterministicId(rule.ruleId(), subject),
                subject,
                severity,
                rule.category(),
                effective,
                rule.ruleId(),
                explanation,
                code,
                allReferences,
                counterexample,
                role,
                effective == DecisionTier.RELEASE_BLOCKED);
    }
}
