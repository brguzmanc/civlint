package com.bguzman.civlint.domain;

/**
 * The role a step plays in a procedure.
 *
 * <p>{@link #consequential()} marks the kinds where an outcome is imposed on a person. CivLint never
 * permits a consequential step to be fully automated, which is enforced structurally rather than by
 * convention: the verifier rejects any proposed version that assigns {@link DecisionTier#AUTOMATE}
 * to a consequential step.
 */
public enum StepKind {

    /** Receiving the request and its evidence. */
    INTAKE("Intake", false),

    /** A deterministic check with no judgment. */
    MECHANICAL_CHECK("Mechanical check", false),

    /** A person reads the file without deciding the outcome. */
    CLERICAL_REVIEW("Clerical review", false),

    /** The substantive decision on the request. */
    DECISION("Decision", true),

    /** A sign-off that gives the decision effect. */
    APPROVAL("Approval", true),

    /** Telling the applicant the outcome and their options. */
    NOTIFICATION("Notification", false),

    /** Reconsideration of a decision at the applicant's request. */
    APPEAL("Appeal", true),

    /** Recording the outcome for retention and audit. */
    RECORDING("Recording", false),

    /** An end state of the procedure. */
    TERMINAL("Terminal", false);

    private final String label;
    private final boolean consequential;

    StepKind(String label, boolean consequential) {
        this.label = label;
        this.consequential = consequential;
    }

    public String label() {
        return label;
    }

    public boolean consequential() {
        return consequential;
    }
}
