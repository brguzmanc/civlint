package com.bguzman.civlint.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies identifier validation, including that results do not depend on the default locale.
 */
class IdentifiersTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "STEP.1", "STEP_ONE", "A-B", "C1", "STEP.INTAKE-01"})
    @DisplayName("well-formed identifiers are accepted")
    void acceptsValid(String value) {
        assertThat(Identifiers.requireStable("id", value)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "", " ", "_AB", ".AB", "-AB", "A B", "A/B", "A:B", "A+B", "A#B"})
    @DisplayName("malformed identifiers are rejected")
    void rejectsInvalid(String value) {
        assertThatThrownBy(() -> Identifiers.requireStable("id", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("identifiers are upper-cased")
    void upperCases() {
        assertThat(Identifiers.requireStable("id", "step.intake")).isEqualTo("STEP.INTAKE");
    }

    @Test
    @DisplayName("length bounds are enforced at 2 and 64 characters")
    void enforcesLength() {
        assertThat(Identifiers.requireStable("id", "AB")).isEqualTo("AB");
        String exactly64 = "A".repeat(64);
        assertThat(Identifiers.requireStable("id", exactly64)).hasSize(64);
        String tooLong = "A".repeat(65);
        assertThatThrownBy(() -> Identifiers.requireStable("id", tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("upper-casing does not depend on the default locale")
    void isLocaleIndependent() {
        Locale.setDefault(new Locale.Builder().setLanguage("tr").setRegion("TR").build());
        // In Turkish, the default-locale upper case of "i" is a dotted capital I, which would not
        // match the identifier pattern. Locale.ROOT keeps the result ASCII.
        assertThat(Identifiers.requireStable("id", "instep")).isEqualTo("INSTEP");
    }

    @Test
    @DisplayName("null identifiers are rejected with the field name")
    void rejectsNull() {
        assertThatThrownBy(() -> Identifiers.requireStable("stepId", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("stepId");
    }

    @Test
    @DisplayName("text is trimmed but otherwise preserved")
    void trimsText() {
        assertThat(Identifiers.requireText("t", "  hello world  ")).isEqualTo("hello world");
        assertThat(Identifiers.requireText("t", "María")).isEqualTo("María");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("blank text is rejected")
    void rejectsBlankText(String value) {
        assertThatThrownBy(() -> Identifiers.requireText("field", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }
}
