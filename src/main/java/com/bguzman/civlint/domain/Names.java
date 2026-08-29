package com.bguzman.civlint.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic name normalisation and comparison.
 *
 * <p>These are the only name operations CivLint claims are mechanical. Each is a pure function of its
 * input, uses {@link Locale#ROOT} so results never depend on the ambient locale, and is covered by
 * {@code NamesTest}. Anything beyond them returns {@link NameComparison.Undecidable}.
 *
 * <p>What is treated as mechanical:
 *
 * <ul>
 *   <li>Collapsing runs of whitespace and trimming ends.
 *   <li>Case folding.
 *   <li>Folding combining marks (so {@code "María"} and {@code "Maria"} compare equal).
 *   <li>Normalising the hyphen and apostrophe characters used to join compound name parts.
 * </ul>
 *
 * <p>What is explicitly <em>not</em> treated as mechanical, and abstains instead:
 *
 * <ul>
 *   <li>Values mixing Latin and non-Latin scripts, where folding rules are not established here.
 *   <li>A change in the number of name parts that is not a pure compound join or split.
 *   <li>An empty normalised form on either side.
 * </ul>
 */
public final class Names {

    /** Code reported when two values are byte-identical. */
    public static final String CODE_IDENTICAL = "NAME_IDENTICAL";

    /** Code reported when values match after whitespace and case normalisation. */
    public static final String CODE_WHITESPACE_CASE = "NAME_EQUIV_WHITESPACE_CASE";

    /** Code reported when values match after additionally folding combining marks. */
    public static final String CODE_DIACRITIC = "NAME_EQUIV_DIACRITIC_FOLDED";

    /** Code reported when values match after additionally normalising compound joiners. */
    public static final String CODE_COMPOUND_JOINER = "NAME_EQUIV_COMPOUND_JOINER";

    /** Code reported when the values genuinely differ. */
    public static final String CODE_DIFFERENT = "NAME_DIFFERENT";

    /** Code reported when the values mix scripts and no folding rule is established. */
    public static final String CODE_MIXED_SCRIPT = "NAME_UNDECIDABLE_MIXED_SCRIPT";

    /** Code reported when the name-part structure changed in a way no rule covers. */
    public static final String CODE_STRUCTURE = "NAME_UNDECIDABLE_PART_STRUCTURE";

    /** Code reported when a value normalises to nothing. */
    public static final String CODE_EMPTY = "NAME_UNDECIDABLE_EMPTY";

    private Names() {
        throw new AssertionError("No instances.");
    }

    public static String normaliseWhitespaceAndCase(String value) {
        Objects.requireNonNull(value, "value");
        return value.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public static String foldDiacritics(String value) {
        Objects.requireNonNull(value, "value");
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    /**
     * Normalises the joiner characters used in compound names to a single space.
     *
     * <p>Hyphen, non-breaking hyphen, en dash and apostrophe variants are treated as the same
     * joiner, so {@code "SERRANO-VIDAL"} and {@code "SERRANO VIDAL"} compare equal.
     *
     * @param value raw value; must not be {@code null}
     * @return the value with joiners replaced by single spaces and whitespace collapsed
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String normaliseCompoundJoiners(String value) {
        Objects.requireNonNull(value, "value");
        return value.replaceAll("[\\-‐‑–—'’]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * Compares two name values through the escalating ladder of mechanical normalisations.
     *
     * <p>The ladder is ordered from least to most transformation, and the first step that produces
     * equality is reported. Reporting the weakest sufficient normalisation matters: it tells a
     * reviewer exactly how much interpretation was applied to call the values the same.
     *
     * @param current the value currently held in the record; must not be {@code null}
     * @param requested the value the applicant asked for; must not be {@code null}
     * @return an {@link NameComparison.Equivalent}, {@link NameComparison.Different} or
     *     {@link NameComparison.Undecidable} outcome
     * @throws NullPointerException if either argument is {@code null}
     */
    public static NameComparison compare(String current, String requested) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requested, "requested");

        if (current.equals(requested)) {
            return new NameComparison.Equivalent(CODE_IDENTICAL, current);
        }
        if (mixesScripts(current) || mixesScripts(requested)) {
            return new NameComparison.Undecidable(
                    CODE_MIXED_SCRIPT,
                    "A value mixes Latin and non-Latin characters; no folding rule is established "
                            + "for this combination, so the comparison is left to a reviewer.");
        }

        String currentCase = normaliseWhitespaceAndCase(current);
        String requestedCase = normaliseWhitespaceAndCase(requested);
        if (currentCase.isEmpty() || requestedCase.isEmpty()) {
            return new NameComparison.Undecidable(
                    CODE_EMPTY, "A value normalises to an empty string and cannot be compared.");
        }
        if (currentCase.equals(requestedCase)) {
            return new NameComparison.Equivalent(CODE_WHITESPACE_CASE, currentCase);
        }

        String currentFolded = normaliseWhitespaceAndCase(foldDiacritics(current));
        String requestedFolded = normaliseWhitespaceAndCase(foldDiacritics(requested));
        if (currentFolded.equals(requestedFolded)) {
            return new NameComparison.Equivalent(CODE_DIACRITIC, currentFolded);
        }

        String currentJoined = normaliseCompoundJoiners(currentFolded);
        String requestedJoined = normaliseCompoundJoiners(requestedFolded);
        if (currentJoined.equals(requestedJoined)) {
            return new NameComparison.Equivalent(CODE_COMPOUND_JOINER, currentJoined);
        }

        int currentParts = currentJoined.split(" ").length;
        int requestedParts = requestedJoined.split(" ").length;
        if (currentParts != requestedParts && shareAllShorterParts(currentJoined, requestedJoined)) {
            // One side is a strict prefix-by-parts of the other: a part was added or dropped.
            // That is a substantive change of the recorded name, not a formatting difference, and
            // no mechanical rule in this policy pack decides it.
            return new NameComparison.Undecidable(
                    CODE_STRUCTURE,
                    "The number of name parts changed from " + currentParts + " to " + requestedParts
                            + " without a compound join or split that this comparator recognises.");
        }
        return new NameComparison.Different(CODE_DIFFERENT);
    }

    private static boolean shareAllShorterParts(String left, String right) {
        String[] a = left.split(" ");
        String[] b = right.split(" ");
        int shorter = Math.min(a.length, b.length);
        for (int i = 0; i < shorter; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean mixesScripts(String value) {
        boolean latin = false;
        boolean other = false;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            if (script == Character.UnicodeScript.LATIN
                    || script == Character.UnicodeScript.COMMON
                    || script == Character.UnicodeScript.INHERITED) {
                latin = true;
            } else {
                other = true;
            }
        }
        return latin && other;
    }
}
