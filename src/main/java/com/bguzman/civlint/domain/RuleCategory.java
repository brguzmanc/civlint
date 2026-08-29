package com.bguzman.civlint.domain;

/**
 * The closed set of policy-rule categories CivLint reasons about.
 *
 * <p>Each constant carries the tier that applies by default when a rule in the category is engaged,
 * and whether the category is inherently one a machine may conclude alone. Those two properties are
 * the bridge between a policy pack and the Human Necessity Map: a category marked
 * {@code mechanicallyDecidable} may be automated once its evidence checks pass, and a category that
 * is not may never be automated regardless of what an agent proposes.
 */
public enum RuleCategory {

    /** Deterministic transformation or comparison, such as normalising whitespace. */
    MECHANICAL("Mechanical and deterministic", DecisionTier.AUTOMATE, true),

    /** Whether the required evidence is present and readable. */
    EVIDENCE_COMPLETENESS("Evidence completeness", DecisionTier.AUTO_WITH_EXCEPTION, true),

    /** Whether identity or record fields agree across authoritative sources. */
    IDENTITY_CONSISTENCY("Identity or record consistency", DecisionTier.AUTO_WITH_EXCEPTION, true),

    /** Whether the acting office holds the legal or delegated authority to decide. */
    LEGAL_AUTHORITY("Legal or delegated authority", DecisionTier.HUMAN_REQUIRED, false),

    /** Accessibility accommodations and alternative forms of evidence. */
    ACCESSIBILITY("Accessibility or alternative evidence", DecisionTier.HUMAN_REQUIRED, false),

    /** Judgment that policy deliberately leaves to a person. */
    DISCRETIONARY("Discretionary judgment", DecisionTier.HUMAN_REQUIRED, false),

    /** Preservation of the right to appeal or seek review. */
    APPEAL_RIGHTS("Appeal and review rights", DecisionTier.HUMAN_REQUIRED, false),

    /** Requirement that preparation and approval be performed by different people. */
    SEPARATION_OF_DUTIES("Separation of duties", DecisionTier.HUMAN_REQUIRED, false),

    /** Requirement that approvals occur in a defined order. */
    APPROVAL_ORDERING("Approval ordering", DecisionTier.AUTO_WITH_EXCEPTION, true),

    /** Retention periods and audit-trail obligations. */
    RETENTION_AUDIT("Retention and audit", DecisionTier.AUTO_WITH_EXCEPTION, true),

    /** Confidentiality, minimisation and access control obligations. */
    SECURITY_PRIVACY("Security and privacy", DecisionTier.AUTO_WITH_EXCEPTION, true);

    private final String label;
    private final DecisionTier defaultTier;
    private final boolean mechanicallyDecidable;

    RuleCategory(String label, DecisionTier defaultTier, boolean mechanicallyDecidable) {
        this.label = label;
        this.defaultTier = defaultTier;
        this.mechanicallyDecidable = mechanicallyDecidable;
    }

    public String label() {
        return label;
    }

    public DecisionTier defaultTier() {
        return defaultTier;
    }

    /**
     * Indicates whether a machine may reach the final conclusion for this category.
     *
     * <p>A {@code false} result is a hard ceiling. No agent observation and no confidence score may
     * promote such a category to {@link DecisionTier#AUTOMATE}.
     *
     * @return {@code true} when full automation is permissible in principle
     */
    public boolean mechanicallyDecidable() {
        return mechanicallyDecidable;
    }
}
