package com.bguzman.civlint.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the deterministic name comparator, including that it abstains rather than guessing.
 */
class NamesTest {

    @Nested
    @DisplayName("normalisation primitives")
    class Primitives {

        @ParameterizedTest
        @CsvSource({
            "'  Maria  ', MARIA",
            "'Maria   Elena', 'MARIA ELENA'",
            "maria, MARIA",
            "'MARIA', MARIA",
        })
        @DisplayName("whitespace collapses and case folds")
        void normalisesWhitespaceAndCase(String input, String expected) {
            assertThat(Names.normaliseWhitespaceAndCase(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("tabs and newlines count as whitespace")
        void handlesOtherWhitespace() {
            assertThat(Names.normaliseWhitespaceAndCase("A\tB\nC")).isEqualTo("A B C");
        }

        @Test
        @DisplayName("combining marks are folded to base letters")
        void foldsDiacritics() {
            assertThat(Names.foldDiacritics("María")).isEqualTo("Maria");
            assertThat(Names.foldDiacritics("Ísabel")).isEqualTo("Isabel");
            assertThat(Names.foldDiacritics("Nuñez")).isEqualTo("Nunez");
            assertThat(Names.foldDiacritics("Serrano")).isEqualTo("Serrano");
        }

        @Test
        @DisplayName("compound joiners normalise to a single space")
        void normalisesJoiners() {
            assertThat(Names.normaliseCompoundJoiners("SERRANO-VIDAL")).isEqualTo("SERRANO VIDAL");
            assertThat(Names.normaliseCompoundJoiners("O'BRIEN")).isEqualTo("O BRIEN");
            assertThat(Names.normaliseCompoundJoiners("SERRANO VIDAL")).isEqualTo("SERRANO VIDAL");
        }

        @Test
        @DisplayName("normalisation primitives reject null")
        void rejectsNull() {
            assertThatThrownBy(() -> Names.normaliseWhitespaceAndCase(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Names.foldDiacritics(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Names.normaliseCompoundJoiners(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("equivalence, reported at the weakest sufficient normalisation")
    class Equivalence {

        @Test
        @DisplayName("identical values report IDENTICAL and apply no transformation")
        void identical() {
            NameComparison result = Names.compare("María Serrano", "María Serrano");
            assertThat(result).isInstanceOf(NameComparison.Equivalent.class);
            assertThat(result.code()).isEqualTo(Names.CODE_IDENTICAL);
            assertThat(((NameComparison.Equivalent) result).normalisedValue()).isEqualTo("María Serrano");
        }

        @ParameterizedTest
        @CsvSource({
            "'maria', 'MARIA'",
            "'  Maria  ', 'Maria'",
            "'Maria  Elena', 'Maria Elena'",
        })
        @DisplayName("case and whitespace differences report WHITESPACE_CASE, not diacritic folding")
        void whitespaceAndCaseOnly(String current, String requested) {
            NameComparison result = Names.compare(current, requested);
            assertThat(result).isInstanceOf(NameComparison.Equivalent.class);
            assertThat(result.code()).isEqualTo(Names.CODE_WHITESPACE_CASE);
        }

        @ParameterizedTest
        @CsvSource({
            "'María', 'Maria'",
            "'Maria', 'María'",
            "'Núñez', 'Nunez'",
            "'MARÍA', 'maria'",
        })
        @DisplayName("diacritic-only differences report DIACRITIC_FOLDED")
        void diacriticOnly(String current, String requested) {
            NameComparison result = Names.compare(current, requested);
            assertThat(result).isInstanceOf(NameComparison.Equivalent.class);
            assertThat(result.code()).isEqualTo(Names.CODE_DIACRITIC);
        }

        @ParameterizedTest
        @CsvSource({
            "'Serrano-Vidal', 'Serrano Vidal'",
            "'Serrano Vidal', 'Serrano-Vidal'",
            "'O''Brien', 'O Brien'",
        })
        @DisplayName("joiner-only differences report COMPOUND_JOINER")
        void joinerOnly(String current, String requested) {
            NameComparison result = Names.compare(current, requested);
            assertThat(result).isInstanceOf(NameComparison.Equivalent.class);
            assertThat(result.code()).isEqualTo(Names.CODE_COMPOUND_JOINER);
        }

        @Test
        @DisplayName("a compound surname with diacritics and a joiner resolves mechanically")
        void compoundWithDiacritics() {
            NameComparison result = Names.compare("Serrano-Vidál", "serrano vidal");
            assertThat(result).isInstanceOf(NameComparison.Equivalent.class);
            assertThat(result.code()).isEqualTo(Names.CODE_COMPOUND_JOINER);
        }

        @Test
        @DisplayName("comparison is symmetric for every equivalence outcome")
        void isSymmetric() {
            assertThat(Names.compare("María", "Maria").code())
                    .isEqualTo(Names.compare("Maria", "María").code());
            assertThat(Names.compare("A-B", "A B").code()).isEqualTo(Names.compare("A B", "A-B").code());
        }
    }

    @Nested
    @DisplayName("genuine differences")
    class Differences {

        @ParameterizedTest
        @CsvSource({
            "'Maria', 'Marcia'",
            "'Serrano', 'Vidal'",
            "'Ana Luisa', 'Ana Beatriz'",
        })
        @DisplayName("different content reports DIFFERENT")
        void different(String current, String requested) {
            NameComparison result = Names.compare(current, requested);
            assertThat(result).isInstanceOf(NameComparison.Different.class);
            assertThat(result.code()).isEqualTo(Names.CODE_DIFFERENT);
        }
    }

    @Nested
    @DisplayName("abstention: the comparator declines rather than guessing")
    class Abstention {

        @Test
        @DisplayName("mixed Latin and non-Latin scripts abstain")
        void mixedScript() {
            NameComparison result = Names.compare("Maria 日本", "Maria");
            assertThat(result).isInstanceOf(NameComparison.Undecidable.class);
            assertThat(result.code()).isEqualTo(Names.CODE_MIXED_SCRIPT);
            assertThat(((NameComparison.Undecidable) result).reason()).contains("reviewer");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("a value that normalises to nothing abstains")
        void emptyValue(String blank) {
            NameComparison result = Names.compare("Maria", blank);
            assertThat(result).isInstanceOf(NameComparison.Undecidable.class);
            assertThat(result.code()).isEqualTo(Names.CODE_EMPTY);
        }

        @Test
        @DisplayName("adding a name part abstains rather than reporting a plain difference")
        void partAdded() {
            NameComparison result = Names.compare("Ana Serrano", "Ana Serrano Vidal");
            assertThat(result).isInstanceOf(NameComparison.Undecidable.class);
            assertThat(result.code()).isEqualTo(Names.CODE_STRUCTURE);
            assertThat(((NameComparison.Undecidable) result).reason()).contains("2").contains("3");
        }

        @Test
        @DisplayName("dropping a name part abstains")
        void partDropped() {
            NameComparison result = Names.compare("Ana Serrano Vidal", "Ana Serrano");
            assertThat(result).isInstanceOf(NameComparison.Undecidable.class);
            assertThat(result.code()).isEqualTo(Names.CODE_STRUCTURE);
        }

        @Test
        @DisplayName("a part count change with unrelated content is a difference, not an abstention")
        void unrelatedPartCountChange() {
            NameComparison result = Names.compare("Ana Serrano", "Beatriz Vidal Ortiz");
            assertThat(result).isInstanceOf(NameComparison.Different.class);
        }

        @Test
        @DisplayName("an abstention is never reported as equivalent")
        void abstentionIsNotEquivalence() {
            NameComparison result = Names.compare("Ana Serrano", "Ana Serrano Vidal");
            assertThat(result).isNotInstanceOf(NameComparison.Equivalent.class);
        }
    }

    @Test
    @DisplayName("compare rejects null arguments")
    void rejectsNull() {
        assertThatThrownBy(() -> Names.compare(null, "A")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Names.compare("A", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("comparison is deterministic across repeated calls")
    void isDeterministic() {
        for (int i = 0; i < 100; i++) {
            assertThat(Names.compare("María Serrano-Vidal", "maria serrano vidal").code())
                    .isEqualTo(Names.CODE_COMPOUND_JOINER);
        }
    }
}
