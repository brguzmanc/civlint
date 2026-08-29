package com.bguzman.civlint.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies SHA-256 helpers against published digests.
 */
class DigestTest {

    @Test
    @DisplayName("matches the published SHA-256 of the empty string")
    void hashesEmptyString() {
        assertThat(Digest.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("matches the published SHA-256 of \"abc\"")
    void hashesAbc() {
        assertThat(Digest.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("text and byte overloads agree for UTF-8 input")
    void overloadsAgree() {
        String text = "María Serrano-Vidal";
        assertThat(Digest.sha256Hex(text))
                .isEqualTo(Digest.sha256Hex(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("digests are lower-case hex of fixed length")
    void digestShape() {
        String hash = Digest.sha256Hex("anything");
        assertThat(hash).hasSize(Digest.SHA256_HEX_LENGTH).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("hashing is stable across repeated calls")
    void isStable() {
        String first = Digest.sha256Hex("stable");
        for (int i = 0; i < 50; i++) {
            assertThat(Digest.sha256Hex("stable")).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("a one-character difference changes the digest")
    void isSensitive() {
        assertThat(Digest.sha256Hex("abc")).isNotEqualTo(Digest.sha256Hex("abd"));
    }

    @Test
    @DisplayName("shorten truncates to 12 characters and leaves short input alone")
    void shortens() {
        assertThat(Digest.shorten(Digest.sha256Hex("x"))).hasSize(12);
        assertThat(Digest.shorten("abc")).isEqualTo("abc");
        assertThat(Digest.shorten("")).isEmpty();
    }

    @Test
    @DisplayName("null input is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> Digest.sha256Hex((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Digest.sha256Hex((byte[]) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Digest.shorten(null)).isInstanceOf(NullPointerException.class);
    }
}
