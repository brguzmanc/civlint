package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;

/**
 * A constraint that the person preparing a decision must not be the person approving it.
 *
 * <p>The constraint names two steps rather than two roles. Roles are read from the procedure version
 * being checked, so the same constraint detects a violation introduced by re-assigning either step —
 * which is how separation of duties is usually lost in practice: not by deleting the rule, but by
 * quietly giving both steps to the same role.
 *
 * @param dutyId stable identifier, unique within a procedure version
 * @param preparingStepId the step where the file is prepared
 * @param approvingStepId the step where the file is approved
 * @param description short human-readable statement of the constraint
 */
public record SeparationOfDuty(
        String dutyId, String preparingStepId, String approvingStepId, String description) {

    public SeparationOfDuty {
        dutyId = Identifiers.requireStable("dutyId", dutyId);
        preparingStepId = Identifiers.requireStable("preparingStepId", preparingStepId);
        approvingStepId = Identifiers.requireStable("approvingStepId", approvingStepId);
        description = Identifiers.requireText("description", description);
        if (preparingStepId.equals(approvingStepId)) {
            throw new IllegalArgumentException(
                    "Separation of duty " + dutyId + " names the same step for preparation and approval");
        }
    }

    public EvidenceReference reference() {
        return new EvidenceReference(
                EvidenceReference.Kind.SEPARATION_OF_DUTY, dutyId, description);
    }

    public Json toJson() {
        return Json.obj()
                .put("dutyId", dutyId)
                .put("preparingStepId", preparingStepId)
                .put("approvingStepId", approvingStepId)
                .put("description", description)
                .build();
    }
}
