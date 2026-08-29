package com.bguzman.civlint.agents;

/**
 * Thrown when a model cannot be reached or has no response for a request.
 *
 * <p>Treated as an expected condition rather than a failure of the run: the orchestrator records a
 * trace with status {@code SKIPPED} and continues, because the verifier does not
 * depend on agent output for any safety conclusion.
 */
public class AgentUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AgentUnavailableException(String message) {
        super(message);
    }
}
