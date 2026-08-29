package com.bguzman.civlint.evaluation;

/**
 * Thrown when two raw runs over identical inputs disagree, or when a run already carrying
 * {@link Metrics#REPLAY_AGREEMENT} is offered for comparison.
 *
 * <p>Unlike an unreachable model, this is not an expected condition: the system failed to hold a
 * property it publishes, so nothing is published. It extends {@link IllegalStateException} rather
 * than {@link IllegalArgumentException} because the caller supplied nothing invalid.
 */
public class ReplayVerificationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ReplayVerificationException(String message) {
        super(message);
    }
}
