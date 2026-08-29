package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * One entry of the {@link HumanNecessityMap}: the reasoned position on whether a person is required
 * at a step, and why.
 *
 * <p>The entry records the reasoning, not just the conclusion. {@code reason},
 * {@code citizenImpact}, {@code reversibility} and {@code minimumEvidence} exist so that a later
 * reader can challenge the conclusion on its merits instead of having to trust it. An entry that is
 * not {@code approved} carries no authority: the verifier treats an unapproved entry proposing
 * automation as if it proposed human review, which makes the safe direction the default when
 * approval is missing.
 *
 * <p><strong>Invariants:</strong> a mandatory human tier must name a human role; an entry claiming
 * {@link DecisionTier#AUTOMATE} may not name a category that is not mechanically decidable;
 * confidence lies in {@code [0,100]}.
 *
 * @param entryId stable identifier
 * @param stepId the procedure step this entry governs
 * @param category the decision category at that step
 * @param tier the tier this entry asserts
 * @param policySource synthetic citation supporting the assertion
 * @param reason why a person is or is not required
 * @param citizenImpact effect on the applicant's rights or interests
 * @param reversibility how easily a wrong outcome can be undone
 * @param exceptionTrigger what routes a case out of the normal path
 * @param requiredRole the role that must act when a person is required
 * @param minimumEvidence the least evidence needed to act
 * @param confidence confidence in the assertion, from 0 to 100
 * @param version version of this entry
 * @param approved whether a reviewer has approved the entry
 */
public record HumanNecessity(
        String entryId,
        String stepId,
        RuleCategory category,
        DecisionTier tier,
        String policySource,
        String reason,
        String citizenImpact,
        Reversibility reversibility,
        String exceptionTrigger,
        ReviewerRole requiredRole,
        String minimumEvidence,
        int confidence,
        String version,
        boolean approved) {

    /**
     * How easily an incorrect outcome at a step can be undone.
     */
    public enum Reversibility {
        /** The outcome can be corrected with no lasting effect. */
        FULLY_REVERSIBLE,
        /** The outcome can be corrected, but not without cost to the applicant. */
        REVERSIBLE_WITH_BURDEN,
        /** Some effects cannot be undone. */
        PARTIALLY_IRREVERSIBLE,
        /** The outcome cannot be undone. */
        IRREVERSIBLE
    }

    public HumanNecessity {
        entryId = Identifiers.requireStable("entryId", entryId);
        stepId = Identifiers.requireStable("stepId", stepId);
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(tier, "tier");
        policySource = Identifiers.requireText("policySource", policySource);
        reason = Identifiers.requireText("reason", reason);
        citizenImpact = Identifiers.requireText("citizenImpact", citizenImpact);
        Objects.requireNonNull(reversibility, "reversibility");
        exceptionTrigger = exceptionTrigger == null ? "" : exceptionTrigger.strip();
        Objects.requireNonNull(requiredRole, "requiredRole");
        minimumEvidence = Identifiers.requireText("minimumEvidence", minimumEvidence);
        version = Identifiers.requireText("version", version);

        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException(
                    "Entry " + entryId + " confidence must be within [0,100] but was " + confidence);
        }
        if (tier.humanInvolved() && !requiredRole.human()) {
            throw new IllegalArgumentException(
                    "Entry " + entryId + " asserts tier " + tier + " but names no human role");
        }
        if (tier == DecisionTier.AUTOMATE && !category.mechanicallyDecidable()) {
            throw new IllegalArgumentException(
                    "Entry " + entryId + " asserts AUTOMATE for category " + category
                            + ", which is not mechanically decidable");
        }
        if (tier == DecisionTier.AUTOMATE && requiredRole != ReviewerRole.NONE) {
            throw new IllegalArgumentException(
                    "Entry " + entryId + " asserts AUTOMATE but names role " + requiredRole);
        }
    }

    /**
     * Returns the tier that may actually be relied on.
     *
     * <p>An unapproved entry is treated as asserting at least {@link DecisionTier#HUMAN_REQUIRED},
     * whatever it claims. This is the mechanism that stops a draft entry from authorising automation.
     *
     * @return the claimed tier when approved, otherwise the more cautious of the claim and
     *     {@link DecisionTier#HUMAN_REQUIRED}
     */
    public DecisionTier effectiveTier() {
        return approved ? tier : DecisionTier.escalate(tier, DecisionTier.HUMAN_REQUIRED);
    }

    public boolean mandatoryHumanGate() {
        return effectiveTier().mandatoryHumanGate();
    }

    public EvidenceReference reference() {
        return new EvidenceReference(
                EvidenceReference.Kind.HUMAN_NECESSITY_ENTRY, entryId, reason);
    }

    public Json toJson() {
        return Json.obj()
                .put("entryId", entryId)
                .put("stepId", stepId)
                .put("category", category)
                .put("tier", tier)
                .put("effectiveTier", effectiveTier())
                .put("policySource", policySource)
                .put("reason", reason)
                .put("citizenImpact", citizenImpact)
                .put("reversibility", reversibility)
                .put("exceptionTrigger", exceptionTrigger)
                .put("requiredRole", requiredRole)
                .put("minimumEvidence", minimumEvidence)
                .put("confidence", confidence)
                .put("version", version)
                .put("approved", approved)
                .build();
    }
}
