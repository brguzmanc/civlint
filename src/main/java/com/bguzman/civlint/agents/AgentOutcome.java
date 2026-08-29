package com.bguzman.civlint.agents;

import com.bguzman.civlint.domain.AgentObservation;
import com.bguzman.civlint.domain.AgentTrace;
import java.util.List;
import java.util.Objects;

/**
 * The result of one agent invocation: its trace, and nothing else.
 *
 * <p>Observations are reached through the trace rather than returned alongside it, so a caller cannot
 * read observations from an invocation whose status says they were rejected.
 *
 * @param trace the complete record of the invocation
 */
public record AgentOutcome(AgentTrace trace) {

    public AgentOutcome {
        Objects.requireNonNull(trace, "trace");
    }

    public List<AgentObservation> usableObservations() {
        return trace.usable() ? trace.observations() : List.of();
    }
}
