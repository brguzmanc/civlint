package com.bguzman.civlint.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 helpers used for every canonical artifact hash in CivLint.
 *
 * <p>Hashes are the mechanism by which reproducibility claims are checked: a run identifier, a
 * policy pack, a procedure version and an evaluation result all carry a digest, and two runs are
 * declared to agree only when their digests match.
 *
 * <p><strong>Side effects:</strong> none; stateless and thread-safe. A fresh {@link MessageDigest}
 * is obtained per call because instances are not thread-safe.
 */
public final class Digest {

    /** Length in characters of a hexadecimal SHA-256 digest. */
    public static final int SHA256_HEX_LENGTH = 64;

    private Digest() {
        throw new AssertionError("No instances.");
    }

    public static String sha256Hex(String text) {
        Objects.requireNonNull(text, "text");
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    public static String shorten(String hex) {
        Objects.requireNonNull(hex, "hex");
        return hex.length() <= 12 ? hex : hex.substring(0, 12);
    }
}
