package com.bguzman.civlint.support;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validation for the stable identifiers CivLint uses as sort keys and evidence anchors.
 *
 * <p>Identifiers are constrained deliberately. They appear in canonical JSON, in finding codes and
 * in deterministic ordering, so an identifier that varies by locale or that contains characters
 * needing escaping would weaken both readability and reproducibility.
 */
public final class Identifiers {

    private static final Pattern STABLE_ID = Pattern.compile("[A-Z0-9][A-Z0-9_.-]{1,63}");

    private Identifiers() {
        throw new AssertionError("No instances.");
    }

    /**
     * Validates a stable identifier.
     *
     * <p>Accepted form: 2 to 64 characters, upper-case ASCII letters, digits, underscore, dot and
     * hyphen, starting with a letter or digit. Case is normalised with {@link Locale#ROOT} so that
     * the result never depends on the default locale.
     *
     * @param name field name used in the failure message
     * @param value candidate identifier
     * @return the identifier, upper-cased
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} does not match the accepted form
     */
    public static String requireStable(String name, String value) {
        Objects.requireNonNull(value, name);
        String normalised = value.toUpperCase(Locale.ROOT);
        if (!STABLE_ID.matcher(normalised).matches()) {
            throw new IllegalArgumentException(
                    name + " must be 2-64 chars of [A-Z0-9_.-] starting alphanumeric, but was: " + value);
        }
        return normalised;
    }

    public static String requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
