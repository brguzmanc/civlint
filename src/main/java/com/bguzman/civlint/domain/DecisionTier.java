package com.bguzman.civlint.domain;

/**
 * How much human control a step or case requires.
 *
 * <p>The constants form a total order of increasing caution, and the ordinal order is part of the
 * domain contract: {@link #escalate(DecisionTier, DecisionTier)} resolves competing conclusions by
 * always keeping the more cautious one. That property is what lets independent rules be evaluated in
 * any order while still producing one deterministic outcome.
 */
public enum DecisionTier {

    /** Fully mechanical: a deterministic check decides the case and no person needs to act. */
    AUTOMATE("Mechanical; no human action required", false, false),

    /** Mechanical in the normal path, with named exceptions routed to a reviewer. */
    AUTO_WITH_EXCEPTION("Mechanical with exception routing to a reviewer", true, false),

    /** A person must decide; automation may prepare material but must not conclude. */
    HUMAN_REQUIRED("A human decision is mandatory", true, false),

    /** The proposed change must not ship; a mandatory safeguard would be lost. */
    RELEASE_BLOCKED("Change blocked; a mandatory safeguard would be removed or weakened", true, true);

    private final String explanation;
    private final boolean humanInvolved;
    private final boolean blocksRelease;

    DecisionTier(String explanation, boolean humanInvolved, boolean blocksRelease) {
        this.explanation = explanation;
        this.humanInvolved = humanInvolved;
        this.blocksRelease = blocksRelease;
    }

    public String explanation() {
        return explanation;
    }

    public boolean humanInvolved() {
        return humanInvolved;
    }

    public boolean blocksRelease() {
        return blocksRelease;
    }

    public boolean mandatoryHumanGate() {
        return this == HUMAN_REQUIRED || this == RELEASE_BLOCKED;
    }

    /**
     * Combines two tiers by keeping the more cautious one.
     *
     * <p>This operation is associative, commutative and idempotent, so a set of rule conclusions
     * reduces to the same tier regardless of evaluation order. That is the reason concurrent rule
     * evaluation cannot change a verdict.
     *
     * @param left one tier; must not be {@code null}
     * @param right the other tier; must not be {@code null}
     * @return whichever tier requires more human control
     * @throws NullPointerException if either argument is {@code null}
     */
    public static DecisionTier escalate(DecisionTier left, DecisionTier right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    public boolean weakenedBy(DecisionTier proposed) {
        return proposed.ordinal() < this.ordinal();
    }
}
