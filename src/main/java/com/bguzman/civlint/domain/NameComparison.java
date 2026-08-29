package com.bguzman.civlint.domain;

/**
 * The outcome of comparing two name values mechanically.
 *
 * <p>The third variant is the point of this type. A comparator that must always answer "equal" or
 * "different" will, sooner or later, answer confidently and wrongly about a name whose structure it
 * was never designed for. {@link Undecidable} lets the mechanical layer decline, which routes the
 * case to a person instead of producing a false match.
 */
public sealed interface NameComparison {

    String code();

    /**
     * The two values are the same after a named, deterministic normalisation.
     *
     * @param code stable code naming the normalisation that made them equal
     * @param normalisedValue the shared normalised form
     */
    record Equivalent(String code, String normalisedValue) implements NameComparison {}

    /**
     * The two values genuinely differ; the requested change is a real change of content.
     *
     * @param code stable code naming the difference
     */
    record Different(String code) implements NameComparison {}

    /**
     * The comparator cannot decide and declines to guess.
     *
     * @param code stable code naming the limitation
     * @param reason human-readable explanation of what the comparator could not resolve
     */
    record Undecidable(String code, String reason) implements NameComparison {}
}
