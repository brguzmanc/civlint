package com.bguzman.civlint.domain;

/**
 * Severity attached to a {@link Finding}.
 *
 * <p>Severity communicates urgency to a reader. It deliberately does <em>not</em> decide whether a
 * release is blocked: that is determined by the rule and the resulting {@link DecisionTier}, so a
 * cosmetic re-labelling of severity can never make an unsafe change shippable.
 */
public enum Severity {

    /** Informational; no action required. */
    INFO,

    /** Worth a reviewer's attention but not an obstacle. */
    LOW,

    /** Should be addressed before the change ships. */
    MEDIUM,

    /** A safeguard is at risk. */
    HIGH,

    /** A mandatory safeguard is lost; the change must not ship. */
    CRITICAL;

    public static Severity max(Severity left, Severity right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
