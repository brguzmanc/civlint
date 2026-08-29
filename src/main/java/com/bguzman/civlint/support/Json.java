package com.bguzman.civlint.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * A minimal, immutable JSON value tree used exclusively to produce canonical byte-stable output.
 *
 * <p>CivLint does not use a third-party JSON library for canonicalization. Canonical hashes are part
 * of the reproducibility contract, so their byte output must not change when a serialization library
 * is upgraded, when a library changes its default key ordering, or when it changes how it formats
 * numbers. This tree is therefore hand-rolled and its writer is fully specified by
 * {@link CanonicalJson}.
 *
 * <p><strong>Invariants:</strong> every implementation is immutable and every collection handed in
 * is defensively copied. {@link Obj} stores members in a {@link TreeMap} so member order is the
 * natural code-point order of the member names, independent of insertion order and independent of
 * any hash-map iteration order.
 */
public sealed interface Json {

    /**
     * A JSON object with members ordered by member name.
     *
     * @param members object members, kept sorted by name in code-point order
     */
    record Obj(SequencedMap<String, Json> members) implements Json {
        public Obj(Map<String, Json> members) {
            this(sorted(members));
        }

        private static SequencedMap<String, Json> sorted(Map<String, Json> members) {
            Objects.requireNonNull(members, "members");
            // TreeMap with natural String ordering == code-point ordering for the ASCII member
            // names CivLint emits, which is what the canonical form requires.
            SequencedMap<String, Json> copy = new TreeMap<>();
            members.forEach((name, value) -> copy.put(
                    Objects.requireNonNull(name, "member name"),
                    Objects.requireNonNull(value, () -> "value of member " + name)));
            return Collections.unmodifiableSequencedMap(copy);
        }
    }

    /**
     * A JSON array. Element order is significant and preserved exactly as supplied, so callers are
     * responsible for sorting when determinism requires it.
     *
     * @param elements array elements in emission order
     */
    record Arr(List<Json> elements) implements Json {
        public Arr {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    /**
     * A JSON string.
     *
     * @param value the unescaped string value
     */
    record Str(String value) implements Json {
        public Str {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number held as {@link BigDecimal} so that formatting is exact and independent of
     * binary floating-point rendering rules.
     *
     * @param value the numeric value
     */
    record Num(BigDecimal value) implements Json {
        public Num {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON boolean.
     *
     * @param value the boolean value
     */
    record Bool(boolean value) implements Json {}

    /**
     * The JSON {@code null} literal.
     */
    record Null() implements Json {}

    Json NULL = new Null();

    Json TRUE = new Bool(true);

    Json FALSE = new Bool(false);

    static Json of(String value) {
        return value == null ? NULL : new Str(value);
    }

    static Json of(long value) {
        return new Num(BigDecimal.valueOf(value));
    }

    static Json of(BigDecimal value) {
        return value == null ? NULL : new Num(value);
    }

    static Json of(boolean value) {
        return value ? TRUE : FALSE;
    }

    static Json of(Enum<?> value) {
        return value == null ? NULL : new Str(value.name());
    }

    static Json array(List<Json> elements) {
        return new Arr(elements);
    }

    static Json strings(List<String> values) {
        List<Json> out = new ArrayList<>(values.size());
        values.forEach(v -> out.add(of(v)));
        return new Arr(out);
    }

    static Builder obj() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link Obj}. Insertion order is irrelevant because the resulting object
     * sorts its members.
     */
    final class Builder {
        private final Map<String, Json> members = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String name, Json value) {
            members.put(name, value);
            return this;
        }

        public Builder put(String name, String value) {
            return put(name, Json.of(value));
        }

        public Builder put(String name, long value) {
            return put(name, Json.of(value));
        }

        public Builder put(String name, boolean value) {
            return put(name, Json.of(value));
        }

        public Builder put(String name, Enum<?> value) {
            return put(name, Json.of(value));
        }

        public Builder put(String name, BigDecimal value) {
            return put(name, Json.of(value));
        }

        public Json build() {
            return new Obj(members);
        }
    }
}
