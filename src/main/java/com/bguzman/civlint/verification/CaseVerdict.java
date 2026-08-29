package com.bguzman.civlint.verification;

import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.List;
import java.util.Objects;

/**
 * The verifier's conclusion about one case.
 *
 * @param caseId the case evaluated
 * @param tier the decision tier that applies
 * @param requiredRole the role that must act, or {@link ReviewerRole#NONE} when none must
 * @param findings the findings raised, in canonical order
 */
public record CaseVerdict(
        String caseId, DecisionTier tier, ReviewerRole requiredRole, List<Finding> findings) {

    public CaseVerdict {
        caseId = Identifiers.requireStable("caseId", caseId);
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(requiredRole, "requiredRole");
        findings = Objects.requireNonNull(findings, "findings").stream().sorted(Finding.SORT_ORDER).toList();
        if (tier.mandatoryHumanGate() && !requiredRole.human()) {
            throw new IllegalArgumentException(
                    "Verdict for " + caseId + " is at tier " + tier + " but names no human role");
        }
        if (tier == DecisionTier.AUTOMATE && requiredRole != ReviewerRole.NONE) {
            throw new IllegalArgumentException(
                    "Verdict for " + caseId + " is AUTOMATE but names role " + requiredRole);
        }
    }

    public Json toJson() {
        return Json.obj()
                .put("caseId", caseId)
                .put("tier", tier)
                .put("requiredRole", requiredRole)
                .put("findings", Json.array(findings.stream().map(Finding::toJson).toList()))
                .build();
    }
}
