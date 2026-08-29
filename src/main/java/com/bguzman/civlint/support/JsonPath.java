package com.bguzman.civlint.support;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed accessors for reading a parsed {@link Json} tree without casts.
 *
 * <p>Every accessor returns an {@link Optional} or throws {@link JsonParseException} with the path
 * that failed. Contract validation then reads as a list of explicit requirements, and a field that is
 * present with the wrong type is rejected rather than silently coerced — which is the failure mode
 * that lets malformed agent output reach a verifier.
 */
public final class JsonPath {

    private JsonPath() {
        throw new AssertionError("No instances.");
    }

    public static Json member(Json value, String path, String name) {
        Objects.requireNonNull(value, "value");
        if (!(value instanceof Json.Obj(var members))) {
            throw new JsonParseException(path + " must be an object");
        }
        Json member = members.get(name);
        if (member == null) {
            throw new JsonParseException(path + " is missing required member \"" + name + "\"");
        }
        return member;
    }

    public static Optional<Json> optionalMember(Json value, String path, String name) {
        if (!(value instanceof Json.Obj(var members))) {
            throw new JsonParseException(path + " must be an object");
        }
        return Optional.ofNullable(members.get(name));
    }

    public static String string(Json value, String path, String name) {
        Json member = member(value, path, name);
        if (!(member instanceof Json.Str(String s))) {
            throw new JsonParseException(path + "." + name + " must be a string");
        }
        return s;
    }

    public static int integer(Json value, String path, String name) {
        Json member = member(value, path, name);
        if (!(member instanceof Json.Num(BigDecimal n))) {
            throw new JsonParseException(path + "." + name + " must be a number");
        }
        try {
            return n.intValueExact();
        } catch (ArithmeticException e) {
            throw new JsonParseException(path + "." + name + " must be an integer, but was " + n);
        }
    }

    public static List<Json> array(Json value, String path, String name) {
        Json member = member(value, path, name);
        if (!(member instanceof Json.Arr(var elements))) {
            throw new JsonParseException(path + "." + name + " must be an array");
        }
        return elements;
    }

    public static <E extends Enum<E>> E enumeration(
            Json value, String path, String name, Class<E> type) {
        String raw = string(value, path, name);
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException(
                    path + "." + name + " must be one of "
                            + Arrays.toString(type.getEnumConstants()) + ", but was \"" + raw + "\"");
        }
    }
}
