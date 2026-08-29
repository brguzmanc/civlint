package com.bguzman.civlint.domain;

/**
 * Synthetic reviewer roles in the fictional Federated Civil Registry.
 *
 * <p>These roles are demonstration metadata for a wholly invented federation. They do not describe
 * any real office, official, agency or job title.
 *
 * <p>The {@link #canApproveFor(ReviewerRole)} relation encodes the synthetic separation-of-duty
 * model: a role may never approve work it prepared itself.
 */
public enum ReviewerRole {

    /** No person is assigned; the step is machine-executed. */
    NONE("Automated step", false),

    /** Front-desk intake at a local office. */
    INTAKE_CLERK("Intake clerk", true),

    /** Prepares a correction file for decision. */
    RECORDS_OFFICER("Records officer", true),

    /** Decides ordinary corrections. */
    REGISTRY_SUPERVISOR("Registry supervisor", true),

    /** Reviews delegated-authority and statutory questions. */
    LEGAL_REVIEWER("Legal reviewer", true),

    /** Reviews accessibility accommodations and alternative evidence. */
    ACCESSIBILITY_REVIEWER("Accessibility reviewer", true),

    /** Hears appeals; independent of the original decision chain. */
    APPEALS_ADJUDICATOR("Appeals adjudicator", true),

    /** Oversees retention, audit and privacy obligations. */
    DATA_STEWARD("Data steward", true);

    private final String label;
    private final boolean human;

    ReviewerRole(String label, boolean human) {
        this.label = label;
        this.human = human;
    }

    public String label() {
        return label;
    }

    public boolean human() {
        return human;
    }

    /**
     * Indicates whether this role may approve work prepared by {@code preparer}.
     *
     * <p>The rule is intentionally simple and total: a human role may not approve its own
     * preparation, and {@link #NONE} may approve nothing at all. Approval by {@code NONE} would mean
     * an unattended machine signing off a consequential action, which CivLint never permits.
     *
     * @param preparer the role that prepared the work; must not be {@code null}
     * @return {@code true} when approval by this role is permissible
     * @throws NullPointerException if {@code preparer} is {@code null}
     */
    public boolean canApproveFor(ReviewerRole preparer) {
        if (this == NONE || preparer == null) {
            return false;
        }
        return this != preparer;
    }
}
