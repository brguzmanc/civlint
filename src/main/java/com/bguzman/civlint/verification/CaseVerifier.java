package com.bguzman.civlint.verification;

import com.bguzman.civlint.domain.CorrectionRequest;
import com.bguzman.civlint.domain.Counterexample;
import com.bguzman.civlint.domain.CriterionResult;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvidenceReference;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.FindingSubject;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.PolicyRule;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCriterion;
import com.bguzman.civlint.domain.Severity;
import com.bguzman.civlint.policy.RuleEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Decides how one case must be handled, using only the policy pack and the request.
 *
 * <p>The decision procedure is deliberately boring, and each property below exists to remove a way
 * the result could vary between runs:
 *
 * <ol>
 *   <li>Rules are evaluated in ascending rule-identifier order.
 *   <li>Each engaged rule contributes a tier; the case tier is the fold of those tiers under
 *       {@link DecisionTier#escalate}, which is commutative, so evaluation order cannot change it.
 *   <li>An {@link CriterionResult.Abstain} outcome is escalated to at least
 *       {@link DecisionTier#HUMAN_REQUIRED}. A check that could not run never authorises automation.
 *   <li>The required role is taken from the lowest-identifier finding at the strictest tier, which is
 *       a total tie-break rather than "whichever the iteration reached first".
 * </ol>
 *
 * <p><strong>Side effects:</strong> none; stateless and safe to call from virtual threads.
 */
public final class CaseVerifier {

    private CaseVerifier() {
        throw new AssertionError("No instances.");
    }

    public static CaseVerdict verify(PolicyPack pack, CorrectionRequest request) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(request, "request");

        FindingSubject subject = new FindingSubject.OfCase(request.caseId());
        List<Finding> findings = new ArrayList<>();

        for (PolicyRule rule : pack.rules()) {
            if (rule.criterion() instanceof RuleCriterion.StructuralInvariant) {
                // Checked against the procedure graph by StructuralVerifier, not against a request.
                continue;
            }
            CriterionResult result = RuleEvaluator.evaluate(rule, request);
            if (!result.engaged()) {
                continue;
            }
            findings.add(toFinding(rule, subject, result));
        }

        DecisionTier tier = findings.stream()
                .map(Finding::decisionTier)
                .reduce(DecisionTier::escalate)
                .orElse(DecisionTier.AUTOMATE);

        ReviewerRole role = findings.stream()
                .sorted(Finding.SORT_ORDER)
                .filter(f -> f.decisionTier() == tier)
                .map(Finding::requiredReviewerRole)
                .filter(ReviewerRole::human)
                .findFirst()
                .orElse(tier == DecisionTier.AUTOMATE ? ReviewerRole.NONE : ReviewerRole.REGISTRY_SUPERVISOR);

        return new CaseVerdict(request.caseId(), tier, role, findings);
    }

    private static Finding toFinding(
            PolicyRule rule, FindingSubject subject, CriterionResult result) {
        String code;
        String message;
        List<EvidenceReference> references;
        DecisionTier tier;

        switch (result) {
            case CriterionResult.Unmet(String c, String m, List<EvidenceReference> refs) -> {
                code = c;
                message = m;
                references = refs;
                tier = rule.tierWhenEngaged();
            }
            case CriterionResult.Abstain(String c, String m, List<EvidenceReference> refs) -> {
                code = c;
                message = m;
                references = refs;
                // A check that declined to conclude cannot leave the case on the mechanical path.
                tier = DecisionTier.escalate(rule.tierWhenEngaged(), DecisionTier.HUMAN_REQUIRED);
            }
            case CriterionResult.Met() ->
                    throw new IllegalStateException("A satisfied criterion must not produce a finding");
        }

        List<EvidenceReference> allReferences = new ArrayList<>(references);
        allReferences.add(rule.reference());

        ReviewerRole role = tier.humanInvolved() && !rule.requiredRole().human()
                ? ReviewerRole.REGISTRY_SUPERVISOR
                : rule.requiredRole();

        Severity severity = tier == DecisionTier.RELEASE_BLOCKED
                ? Severity.max(rule.severity(), Severity.HIGH)
                : rule.severity();

        return new Finding(
                Finding.deterministicId(rule.ruleId(), subject),
                subject,
                severity,
                rule.category(),
                tier,
                rule.ruleId(),
                message,
                code,
                allReferences,
                counterexampleFor(code, rule, subject, allReferences),
                role,
                tier == DecisionTier.RELEASE_BLOCKED);
    }

    private static Optional<Counterexample> counterexampleFor(
            String code,
            PolicyRule rule,
            FindingSubject subject,
            List<EvidenceReference> references) {
        Counterexample.Kind kind = switch (code) {
            case "AUTHORITATIVE_CONFLICT" -> Counterexample.Kind.AUTHORITATIVE_CONFLICT;
            case "EVIDENCE_ABSENT", "EVIDENCE_INCOMPLETE", "CERTIFIED_ORDER_MISSING" ->
                    Counterexample.Kind.EVIDENCE_MISSING;
            case "EVIDENCE_NOT_USABLE",
                            "EVIDENCE_QUALITY_INSUFFICIENT",
                            "CERTIFIED_ORDER_NOT_USABLE" ->
                    Counterexample.Kind.MECHANICAL_ABSTENTION;
            default -> {
                if (code.startsWith("NAME_UNDECIDABLE")) {
                    yield Counterexample.Kind.MECHANICAL_ABSTENTION;
                }
                yield null;
            }
        };
        if (kind == null) {
            return Optional.empty();
        }
        // The witness is exactly the artifacts the conclusion rests on: nothing wider is needed to
        // reproduce the problem, and nothing narrower would demonstrate it.
        List<String> witness = references.stream()
                .filter(ref -> ref.kind() != EvidenceReference.Kind.POLICY_RULE)
                .map(EvidenceReference::targetId)
                .sorted()
                .distinct()
                .toList();
        if (witness.isEmpty()) {
            return Optional.empty();
        }
        TreeMap<String, String> values = new TreeMap<>();
        values.put("ruleId", rule.ruleId());
        values.put("explanationCode", code);
        return Optional.of(new Counterexample(
                Finding.deterministicId(rule.ruleId(), subject).replace("F.", "CX."),
                kind,
                rule.title() + " is not satisfied for " + subject.key(),
                witness,
                values));
    }
}
