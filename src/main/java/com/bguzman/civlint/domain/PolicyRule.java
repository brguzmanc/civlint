package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * One rule of an approved policy pack.
 *
 * <p><strong>Invariants enforced at construction:</strong>
 *
 * <ul>
 *   <li>A rule whose category is not {@link RuleCategory#mechanicallyDecidable()} may not declare
 *       {@link DecisionTier#AUTOMATE} when engaged. Policy categories reserved for people cannot be
 *       automated by writing a permissive rule.
 *   <li>A rule that engages a human must name a human {@link ReviewerRole}.
 *   <li>A rule that blocks release must be at least {@link Severity#HIGH}, so a release-blocking
 *       finding can never be presented as cosmetic.
 * </ul>
 *
 * @param ruleId stable identifier
 * @param category the policy category
 * @param title short human-readable title
 * @param policySource synthetic citation identifying where the rule comes from
 * @param criterion the condition checked
 * @param tierWhenEngaged the tier that applies when the criterion is not met
 * @param requiredRole the role that must act when the rule engages
 * @param severity severity of the resulting finding
 * @param blocksRelease whether engaging this rule prevents a release
 * @param explanationCode stable machine-readable code for the finding this rule produces
 */
public record PolicyRule(
        String ruleId,
        RuleCategory category,
        String title,
        String policySource,
        RuleCriterion criterion,
        DecisionTier tierWhenEngaged,
        ReviewerRole requiredRole,
        Severity severity,
        boolean blocksRelease,
        String explanationCode) {

    public PolicyRule {
        ruleId = Identifiers.requireStable("ruleId", ruleId);
        Objects.requireNonNull(category, "category");
        title = Identifiers.requireText("title", title);
        policySource = Identifiers.requireText("policySource", policySource);
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(tierWhenEngaged, "tierWhenEngaged");
        Objects.requireNonNull(requiredRole, "requiredRole");
        Objects.requireNonNull(severity, "severity");
        explanationCode = Identifiers.requireStable("explanationCode", explanationCode);

        if (!category.mechanicallyDecidable() && tierWhenEngaged == DecisionTier.AUTOMATE) {
            throw new IllegalArgumentException(
                    "Rule " + ruleId + " is in category " + category
                            + ", which is not mechanically decidable, so it cannot resolve to AUTOMATE");
        }
        if (tierWhenEngaged.humanInvolved() && !requiredRole.human()) {
            throw new IllegalArgumentException(
                    "Rule " + ruleId + " resolves to " + tierWhenEngaged + " but names no human role");
        }
        if (blocksRelease && severity.ordinal() < Severity.HIGH.ordinal()) {
            throw new IllegalArgumentException(
                    "Rule " + ruleId + " blocks release, so its severity must be HIGH or CRITICAL");
        }
    }

    public EvidenceReference reference() {
        return EvidenceReference.rule(ruleId, title + " (" + policySource + ")");
    }

    public Json toJson() {
        return Json.obj()
                .put("ruleId", ruleId)
                .put("category", category)
                .put("title", title)
                .put("policySource", policySource)
                .put("criterion", criterion.toJson())
                .put("tierWhenEngaged", tierWhenEngaged)
                .put("requiredRole", requiredRole)
                .put("severity", severity)
                .put("blocksRelease", blocksRelease)
                .put("explanationCode", explanationCode)
                .build();
    }
}
