package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * A sign-off point in a procedure version, with its position in the required approval order.
 *
 * <p><strong>Invariants:</strong> {@code sequence} is positive and, within a procedure version,
 * unique; a mandatory gate must name a human role. {@code appealable} records whether a decision
 * taken at this gate can be challenged — the flag the verifier watches for silent removal.
 *
 * @param gateId stable identifier, unique within a procedure version
 * @param stepId the step at which the sign-off occurs
 * @param requiredRole the role that must sign off
 * @param sequence the gate's one-based position in the required order
 * @param mandatory whether the gate may never be skipped
 * @param appealable whether a decision at this gate may be appealed
 */
public record ApprovalGate(
        String gateId,
        String stepId,
        ReviewerRole requiredRole,
        int sequence,
        boolean mandatory,
        boolean appealable) {

    public ApprovalGate {
        gateId = Identifiers.requireStable("gateId", gateId);
        stepId = Identifiers.requireStable("stepId", stepId);
        Objects.requireNonNull(requiredRole, "requiredRole");
        if (sequence < 1) {
            throw new IllegalArgumentException("Gate " + gateId + " must have a positive sequence");
        }
        if (mandatory && !requiredRole.human()) {
            throw new IllegalArgumentException(
                    "Mandatory gate " + gateId + " must name a human role, but named " + requiredRole);
        }
    }

    public EvidenceReference reference() {
        return new EvidenceReference(
                EvidenceReference.Kind.APPROVAL_GATE,
                gateId,
                requiredRole.label() + " sign-off at " + stepId);
    }

    public Json toJson() {
        return Json.obj()
                .put("gateId", gateId)
                .put("stepId", stepId)
                .put("requiredRole", requiredRole)
                .put("sequence", sequence)
                .put("mandatory", mandatory)
                .put("appealable", appealable)
                .build();
    }
}
