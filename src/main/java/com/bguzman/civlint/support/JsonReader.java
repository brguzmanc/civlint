package com.bguzman.civlint.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A strict, bounded JSON reader for untrusted text.
 *
 * <p>CivLint reads two kinds of text it does not control: policy documents and agent responses. This
 * reader is the boundary for both. It is deliberately small and deliberately strict, and it exists
 * instead of a general-purpose library for three reasons:
 *
 * <ol>
 *   <li><strong>Bounded by construction.</strong> Input length, nesting depth and element count are
 *       capped, so a hostile or malformed document cannot exhaust memory or the call stack.
 *   <li><strong>No dynamic behaviour.</strong> There is no type coercion, no reference resolution, no
 *       polymorphic instantiation and no annotation processing. Text cannot cause a class to be
 *       loaded or a method to be invoked, so a policy document has no mechanism by which to act.
 *   <li><strong>Produces inert data.</strong> The result is a {@link Json} tree of records. Any
 *       instruction-like content inside a string stays a string; nothing interprets it.
 *   </ol>
 *
 * <p>Strictness beyond the JSON grammar: trailing content after the top-level value is rejected,
 * duplicate object member names are rejected (they are a classic way to smuggle a second value past a
 * validator that reads the first), and leading zeros, {@code NaN}, {@code Infinity} and comments are
 * all rejected.
 */
public final class JsonReader {

    /** Maximum accepted input length in characters. */
    public static final int MAX_LENGTH = 1 << 20;

    /** Maximum accepted nesting depth. */
    public static final int MAX_DEPTH = 32;

    /** Maximum accepted number of values in one document. */
    public static final int MAX_VALUES = 20_000;

    private final String text;
    private int position;
    private int depth;
    private int values;

    private JsonReader(String text) {
        this.text = text;
    }

    public static Json read(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_LENGTH) {
            throw new JsonParseException(
                    "Input of " + text.length() + " characters exceeds the limit of " + MAX_LENGTH);
        }
        JsonReader reader = new JsonReader(text);
        reader.skipWhitespace();
        Json value = reader.readValue();
        reader.skipWhitespace();
        if (reader.position != text.length()) {
            throw new JsonParseException(
                    "Trailing content after the top-level value at offset " + reader.position);
        }
        return value;
    }

    private Json readValue() {
        if (++values > MAX_VALUES) {
            throw new JsonParseException("Document contains more than " + MAX_VALUES + " values");
        }
        if (position >= text.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> new Json.Str(readString());
            case 't' -> readLiteral("true", Json.TRUE);
            case 'f' -> readLiteral("false", Json.FALSE);
            case 'n' -> readLiteral("null", Json.NULL);
            default -> readNumber();
        };
    }

    private Json readObject() {
        enter();
        expect('{');
        Map<String, Json> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            leave();
            return new Json.Obj(members);
        }
        while (true) {
            skipWhitespace();
            String name = readString();
            if (members.containsKey(name)) {
                throw new JsonParseException(
                        "Duplicate member name \"" + name + "\"; a document must state each member once");
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            members.put(name, readValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                position++;
                continue;
            }
            if (next == '}') {
                position++;
                leave();
                return new Json.Obj(members);
            }
            throw new JsonParseException("Expected ',' or '}' at offset " + position);
        }
    }

    private Json readArray() {
        enter();
        expect('[');
        List<Json> elements = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            leave();
            return new Json.Arr(elements);
        }
        while (true) {
            skipWhitespace();
            elements.add(readValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                position++;
                continue;
            }
            if (next == ']') {
                position++;
                leave();
                return new Json.Arr(elements);
            }
            throw new JsonParseException("Expected ',' or ']' at offset " + position);
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw new JsonParseException("Unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                out.append(readEscape());
                continue;
            }
            if (c < 0x20) {
                throw new JsonParseException(
                        "Unescaped control character U+" + String.format("%04X", (int) c) + " in string");
            }
            out.append(c);
        }
    }

    private char readEscape() {
        if (position >= text.length()) {
            throw new JsonParseException("Unterminated escape sequence");
        }
        char c = text.charAt(position++);
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> readUnicodeEscape();
            default -> throw new JsonParseException("Invalid escape \\" + c);
        };
    }

    private char readUnicodeEscape() {
        if (position + 4 > text.length()) {
            throw new JsonParseException("Truncated \\u escape");
        }
        String hex = text.substring(position, position + 4);
        position += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid \\u escape \"" + hex + "\"");
        }
    }

    private Json readNumber() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        int digitsStart = position;
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        int intDigits = position - digitsStart;
        if (intDigits == 0) {
            throw new JsonParseException("Expected a value at offset " + start);
        }
        if (intDigits > 1 && text.charAt(digitsStart) == '0') {
            throw new JsonParseException("Leading zeros are not permitted at offset " + digitsStart);
        }
        if (position < text.length() && text.charAt(position) == '.') {
            position++;
            int fracStart = position;
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
            if (position == fracStart) {
                throw new JsonParseException("Expected digits after '.' at offset " + fracStart);
            }
        }
        if (position < text.length()
                && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
            position++;
            if (position < text.length()
                    && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                position++;
            }
            int expStart = position;
            while (position < text.length() && Character.isDigit(text.charAt(position))) {
                position++;
            }
            if (position == expStart) {
                throw new JsonParseException("Expected digits in exponent at offset " + expStart);
            }
        }
        return new Json.Num(new BigDecimal(text.substring(start, position)));
    }

    private Json readLiteral(String literal, Json value) {
        if (!text.startsWith(literal, position)) {
            throw new JsonParseException("Expected " + literal + " at offset " + position);
        }
        position += literal.length();
        return value;
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw new JsonParseException("Nesting deeper than " + MAX_DEPTH + " levels");
        }
    }

    private void leave() {
        depth--;
    }

    private char peek() {
        if (position >= text.length()) {
            throw new JsonParseException("Unexpected end of input");
        }
        return text.charAt(position);
    }

    private void expect(char expected) {
        if (position >= text.length() || text.charAt(position) != expected) {
            throw new JsonParseException("Expected '" + expected + "' at offset " + position);
        }
        position++;
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                position++;
            } else {
                return;
            }
        }
    }
}
