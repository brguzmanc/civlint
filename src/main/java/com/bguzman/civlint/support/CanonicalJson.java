package com.bguzman.civlint.support;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Writes a {@link Json} tree to its single canonical textual form.
 *
 * <p>The canonical form is fully specified here so that two independent runs, on different machines
 * and different JVM builds, produce byte-identical output for equal inputs:
 *
 * <ol>
 *   <li>No insignificant whitespace anywhere.
 *   <li>Object members sorted by member name in code-point order (guaranteed by {@link Json.Obj}).
 *   <li>Array element order preserved exactly as supplied.
 *   <li>Strings escaped using the shortest legal form: {@code \"}, {@code \\}, {@code \b},
 *       {@code \f}, {@code \n}, {@code \r}, {@code \t}, and {@code \}{@code uXXXX} with lower-case
 *       hex digits for all other control characters below {@code U+0020}. No other character is
 *       escaped, so output is valid UTF-8 text.
 *   <li>Numbers written from {@link BigDecimal#stripTrailingZeros()} in plain (never scientific)
 *       notation, with {@code -0} normalised to {@code 0}.
 *   <li>Output encoded as UTF-8 with no byte-order mark.
 * </ol>
 *
 * <p><strong>Side effects:</strong> none; this class is stateless and thread-safe.
 */
public final class CanonicalJson {

    private CanonicalJson() {
        throw new AssertionError("No instances.");
    }

    public static String write(Json value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(value, out);
        return out.toString();
    }

    public static String hash(Json value) {
        return Digest.sha256Hex(write(value));
    }

    private static void writeValue(Json value, StringBuilder out) {
        switch (value) {
            case Json.Null() -> out.append("null");
            case Json.Bool(boolean b) -> out.append(b ? "true" : "false");
            case Json.Num(BigDecimal n) -> out.append(number(n));
            case Json.Str(String s) -> writeString(s, out);
            case Json.Arr(var elements) -> {
                out.append('[');
                boolean first = true;
                for (Json element : elements) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeValue(element, out);
                }
                out.append(']');
            }
            case Json.Obj(var members) -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Json> member : members.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(member.getKey(), out);
                    out.append(':');
                    writeValue(member.getValue(), out);
                }
                out.append('}');
            }
        }
    }

    static String number(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.signum() == 0) {
            // Collapses 0, -0, 0.00 and 0E-8 to a single representation.
            return "0";
        }
        return stripped.toPlainString();
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u").append(String.format("%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
