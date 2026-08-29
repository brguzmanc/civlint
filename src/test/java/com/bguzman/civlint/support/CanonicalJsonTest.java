package com.bguzman.civlint.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verifies the canonical JSON contract that every CivLint hash depends on.
 */
class CanonicalJsonTest {

    private enum TestEnum {
        FIRST,
        SECOND
    }

    @Test
    @DisplayName("object members are emitted in code-point order regardless of insertion order")
    void sortsMembers() {
        Json a = Json.obj().put("zulu", 1).put("alpha", 2).put("mike", 3).build();
        Json b = Json.obj().put("mike", 3).put("zulu", 1).put("alpha", 2).build();

        assertThat(CanonicalJson.write(a)).isEqualTo("{\"alpha\":2,\"mike\":3,\"zulu\":1}");
        assertThat(CanonicalJson.write(a)).isEqualTo(CanonicalJson.write(b));
        assertThat(CanonicalJson.hash(a)).isEqualTo(CanonicalJson.hash(b));
    }

    @Test
    @DisplayName("insertion order of a raw map cannot leak into canonical output")
    void ignoresRawMapOrder() {
        Map<String, Json> forward = new LinkedHashMap<>();
        forward.put("a", Json.of(1));
        forward.put("b", Json.of(2));
        Map<String, Json> reverse = new LinkedHashMap<>();
        reverse.put("b", Json.of(2));
        reverse.put("a", Json.of(1));

        assertThat(CanonicalJson.write(new Json.Obj(forward)))
                .isEqualTo(CanonicalJson.write(new Json.Obj(reverse)));
    }

    @Test
    @DisplayName("array order is significant and preserved")
    void preservesArrayOrder() {
        Json ascending = Json.strings(List.of("a", "b"));
        Json descending = Json.strings(List.of("b", "a"));

        assertThat(CanonicalJson.write(ascending)).isEqualTo("[\"a\",\"b\"]");
        assertThat(CanonicalJson.write(descending)).isEqualTo("[\"b\",\"a\"]");
        assertThat(CanonicalJson.hash(ascending)).isNotEqualTo(CanonicalJson.hash(descending));
    }

    @Test
    @DisplayName("no insignificant whitespace is emitted")
    void emitsNoWhitespace() {
        Json nested = Json.obj()
                .put("outer", Json.obj().put("inner", Json.strings(List.of("x", "y"))).build())
                .build();

        assertThat(CanonicalJson.write(nested)).isEqualTo("{\"outer\":{\"inner\":[\"x\",\"y\"]}}");
        assertThat(CanonicalJson.write(nested)).doesNotContain(" ").doesNotContain("\n");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "-0, 0",
        "0.00, 0",
        "1, 1",
        "1.0, 1",
        "1.500, 1.5",
        "-2.50, -2.5",
        "100, 100",
        "0.1, 0.1",
    })
    @DisplayName("numbers are written in plain notation with trailing zeros stripped")
    void formatsNumbers(String input, String expected) {
        assertThat(CanonicalJson.number(new BigDecimal(input))).isEqualTo(expected);
    }

    @Test
    @DisplayName("large and small magnitudes never use scientific notation")
    void avoidsScientificNotation() {
        assertThat(CanonicalJson.number(new BigDecimal("1E+10"))).isEqualTo("10000000000");
        assertThat(CanonicalJson.number(new BigDecimal("1E-8"))).isEqualTo("0.00000001");
        assertThat(CanonicalJson.number(new BigDecimal("0E-20"))).isEqualTo("0");
    }

    @Test
    @DisplayName("strings use the shortest legal escape")
    void escapesStrings() {
        assertThat(CanonicalJson.write(Json.of("plain"))).isEqualTo("\"plain\"");
        assertThat(CanonicalJson.write(Json.of("with\"quote"))).isEqualTo("\"with\\\"quote\"");
        assertThat(CanonicalJson.write(Json.of("with\\slash"))).isEqualTo("\"with\\\\slash\"");
    }

    @Test
    @DisplayName("control characters below U+0020 are escaped, printable non-ASCII is not")
    void escapesControlCharacters() {
        assertThat(CanonicalJson.write(Json.of("a\nb"))).isEqualTo("\"a\\nb\"");
        assertThat(CanonicalJson.write(Json.of("a\tb"))).isEqualTo("\"a\\tb\"");
        assertThat(CanonicalJson.write(Json.of("a\rb"))).isEqualTo("\"a\\rb\"");
        assertThat(CanonicalJson.write(Json.of("a\bb"))).isEqualTo("\"a\\bb\"");
        assertThat(CanonicalJson.write(Json.of("a\fb"))).isEqualTo("\"a\\fb\"");

        String withStartOfHeading = "a" + (char) 1 + "b";
        assertThat(CanonicalJson.write(Json.of(withStartOfHeading))).isEqualTo("\"a\\u0001b\"");

        String withUnitSeparator = "a" + (char) 31 + "b";
        assertThat(CanonicalJson.write(Json.of(withUnitSeparator))).isEqualTo("\"a\\u001fb\"");

        assertThat(CanonicalJson.write(Json.of("María"))).isEqualTo("\"María\"");
        assertThat(CanonicalJson.write(Json.of("日本"))).isEqualTo("\"日本\"");
    }

    @Test
    @DisplayName("literals are emitted exactly")
    void writesLiterals() {
        assertThat(CanonicalJson.write(Json.NULL)).isEqualTo("null");
        assertThat(CanonicalJson.write(Json.TRUE)).isEqualTo("true");
        assertThat(CanonicalJson.write(Json.FALSE)).isEqualTo("false");
        assertThat(CanonicalJson.write(Json.of((String) null))).isEqualTo("null");
        assertThat(CanonicalJson.write(Json.of((BigDecimal) null))).isEqualTo("null");
        assertThat(CanonicalJson.write(Json.of(true))).isEqualTo("true");
        assertThat(CanonicalJson.write(Json.of(false))).isEqualTo("false");
    }

    @Test
    @DisplayName("enums are written as their stable constant name")
    void writesEnumNames() {
        assertThat(CanonicalJson.write(Json.of(TestEnum.SECOND))).isEqualTo("\"SECOND\"");
        assertThat(CanonicalJson.write(Json.of((Enum<?>) null))).isEqualTo("null");
    }

    @Test
    @DisplayName("member names are escaped like any other string")
    void escapesMemberNames() {
        Json value = Json.obj().put("a\"b", 1).build();
        assertThat(CanonicalJson.write(value)).isEqualTo("{\"a\\\"b\":1}");
    }

    @Test
    @DisplayName("null members and names are rejected at construction")
    void rejectsNulls() {
        Map<String, Json> withNullValue = new LinkedHashMap<>();
        withNullValue.put("a", null);
        assertThatThrownBy(() -> new Json.Obj(withNullValue)).isInstanceOf(NullPointerException.class);

        Map<String, Json> withNullName = new LinkedHashMap<>();
        withNullName.put(null, Json.of(1));
        assertThatThrownBy(() -> new Json.Obj(withNullName)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("built objects and arrays are immutable")
    void valuesAreImmutable() {
        Json.Obj object = (Json.Obj) Json.obj().put("a", 1).build();
        assertThatThrownBy(() -> object.members().put("b", Json.of(2)))
                .isInstanceOf(UnsupportedOperationException.class);

        Json.Arr array = (Json.Arr) Json.strings(List.of("a"));
        assertThatThrownBy(() -> array.elements().add(Json.of("b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a builder mutation after build does not affect the built value")
    void builderDoesNotAlias() {
        Json.Builder builder = Json.obj().put("a", 1);
        Json first = builder.build();
        builder.put("b", 2);
        Json second = builder.build();

        assertThat(CanonicalJson.write(first)).isEqualTo("{\"a\":1}");
        assertThat(CanonicalJson.write(second)).isEqualTo("{\"a\":1,\"b\":2}");
    }

    @Test
    @DisplayName("hash equals the SHA-256 of the canonical text")
    void hashMatchesWrite() {
        Json value = Json.obj().put("k", "v").build();
        assertThat(CanonicalJson.hash(value)).isEqualTo(Digest.sha256Hex(CanonicalJson.write(value)));
    }
}
