package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * A pointer from a finding back to the thing that justifies it.
 *
 * <p>Every finding CivLint emits carries at least one reference. This is what makes the tool
 * evidence-oriented rather than opinion-oriented: a reader can always ask "on what basis?" and get a
 * named artifact rather than a score.
 *
 * @param kind what sort of artifact is referenced
 * @param targetId stable identifier of the referenced artifact
 * @param description short human-readable description of the reference
 */
public record EvidenceReference(Kind kind, String targetId, String description) {

    /**
     * The sort of artifact a reference points at.
     */
    public enum Kind {
        /** An item of evidence supplied with a case. */
        EVIDENCE_ITEM,
        /** A field of the registry record under correction. */
        RECORD_FIELD,
        /** A step of a procedure version. */
        PROCEDURE_STEP,
        /** A transition between two steps. */
        PROCEDURE_TRANSITION,
        /** An approval gate. */
        APPROVAL_GATE,
        /** A separation-of-duty constraint. */
        SEPARATION_OF_DUTY,
        /** A rule in the policy pack. */
        POLICY_RULE,
        /** An entry of the Human Necessity Map. */
        HUMAN_NECESSITY_ENTRY,
        /** An observation proposed by an agent. */
        AGENT_OBSERVATION,
        /** The correction request as a whole, used when no narrower artifact applies. */
        CASE_REQUEST
    }

    public EvidenceReference {
        Objects.requireNonNull(kind, "kind");
        targetId = Identifiers.requireStable("targetId", targetId);
        description = Identifiers.requireText("description", description);
    }

    public static EvidenceReference rule(String ruleId, String description) {
        return new EvidenceReference(Kind.POLICY_RULE, ruleId, description);
    }

    public static EvidenceReference step(String stepId, String description) {
        return new EvidenceReference(Kind.PROCEDURE_STEP, stepId, description);
    }

    public static EvidenceReference field(String field, String description) {
        return new EvidenceReference(Kind.RECORD_FIELD, field, description);
    }

    /**
     * Creates a reference to the correction request as a whole.
     *
     * <p>Used when a finding is about the absence of something, where no narrower artifact exists to
     * point at.
     *
     * @param caseId the case identifier
     * @param description short description
     * @return a reference of kind {@link Kind#CASE_REQUEST}
     */
    public static EvidenceReference request(String caseId, String description) {
        return new EvidenceReference(Kind.CASE_REQUEST, caseId, description);
    }

    public Json toJson() {
        return Json.obj()
                .put("kind", kind)
                .put("targetId", targetId)
                .put("description", description)
                .build();
    }
}
