package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.EvaluationRun;
import java.util.Objects;

/**
 * The single operation behind every published canonical hash.
 *
 * <p>The stored run, the API response body, the read-only dashboard preview and the generated
 * evaluation artifacts all call this, so their hashes are one number by construction. Before it
 * existed, some surfaces published a raw run and others a raw run plus an appended metric, so their
 * hashes disagreed for a reason unrelated to reproducibility.
 *
 * <p>Order matters: two <em>raw</em> runs are compared first, so the comparison never observes its own
 * result, and only then is {@link Metrics#REPLAY_AGREEMENT} appended to the first. Rejecting an input
 * that already carries that metric is what keeps the operation non-recursive.
 */
public final class ReplayVerification {

    private ReplayVerification() {
        throw new AssertionError("No instances.");
    }

    /**
     * Confirms that two independently constructed raw runs are identical and publishes the result.
     *
     * @param first the first raw run; the returned run is built from this one
     * @param second an independently constructed raw run of the same architecture
     * @return {@code first} with exactly one {@link Metrics#REPLAY_AGREEMENT} metric appended
     * @throws ReplayVerificationException if the modes differ, if either run already carries the
     *     agreement metric, or if the two canonical hashes differ
     */
    public static EvaluationRun verify(EvaluationRun first, EvaluationRun second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        requireRaw(first, "first");
        requireRaw(second, "second");
        if (first.mode() != second.mode()) {
            throw new ReplayVerificationException(
                    "Replay verification compares one architecture against itself, but was given "
                            + first.mode() + " and " + second.mode() + ".");
        }

        String firstHash = first.canonicalHash();
        String secondHash = second.canonicalHash();
        if (!firstHash.equals(secondHash)) {
            throw new ReplayVerificationException(
                    "Two raw " + first.mode() + " runs over identical inputs disagree: " + firstHash
                            + " and " + secondHash + ". Not reproducible, so nothing is published.");
        }
        return first.withMetric(Metrics.replayAgreement(firstHash, secondHash));
    }

    private static void requireRaw(EvaluationRun run, String position) {
        if (run.metric(Metrics.REPLAY_AGREEMENT).isPresent()) {
            throw new ReplayVerificationException("The " + position + " run already publishes "
                    + Metrics.REPLAY_AGREEMENT + "; only raw runs may be compared.");
        }
    }
}
