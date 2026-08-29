package com.bguzman.civlint.agents;

import com.bguzman.civlint.domain.AgentObservation;
import com.bguzman.civlint.domain.AgentTrace;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.support.Digest;
import com.bguzman.civlint.support.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Invokes one agent through a model port, validates the response and builds the trace.
 *
 * <p>The retry policy is deliberately fixed and small: one retry after a contract rejection, then the
 * invocation is recorded as {@code SCHEMA_REJECTED} with no observations. Retrying indefinitely would
 * make run duration depend on how badly an agent misbehaves, and accepting partial output would let
 * a response that failed validation influence a verdict.
 *
 * <p>Every event is recorded in sequence, including rejections, so a trace shows what the agent
 * actually did rather than only its final state.
 *
 * <p><strong>Redaction:</strong> the raw response text is never placed in a trace event. Only its
 * length and a validation outcome are recorded, so no unvalidated agent text reaches a log or a
 * dashboard.
 */
public final class AgentRunner {

    /** Number of retries permitted after a contract rejection. */
    public static final int MAX_RETRIES = 1;

    private final AgentModelPort port;

    public AgentRunner(AgentModelPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    /**
     * Invokes an agent and returns its trace.
     *
     * <p>Never throws for an agent-side problem: an unavailable model, a malformed response and a
     * contract breach are all recorded as trace statuses, because the verifier's conclusions do not
     * depend on any agent succeeding.
     *
     * @param definition the agent to invoke; must not be {@code null}
     * @param request the request to send; must not be {@code null}
     * @return the outcome, whose trace is always present
     * @throws NullPointerException if either argument is {@code null}
     */
    public AgentOutcome run(AgentDefinition definition, AgentRequest request) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(request, "request");
        RunContext context = RunContext.current()
                .orElseThrow(() -> new IllegalStateException("Agent invocation has no run context"));
        if (!context.caseId().equals(request.promptKey())) {
            throw new IllegalStateException(
                    "Run context case " + context.caseId()
                            + " does not match prompt " + request.promptKey());
        }

        List<AgentTrace.TraceEvent> events = new ArrayList<>();
        int sequence = 1;
        events.add(event(sequence++, AgentTrace.TraceEvent.Kind.INPUT_RECEIVED,
                "Input hash " + Digest.shorten(request.inputHash())
                        + ", policy hash " + Digest.shorten(request.policyHash())
                        + ", run context case " + context.caseId()
                        + ", correlation " + context.correlationId()));
        events.add(event(sequence++, AgentTrace.TraceEvent.Kind.PROMPT_RESOLVED,
                "Prompt key " + request.promptKey() + " via adapter " + port.adapterId()));

        int retries = 0;

        while (retries <= MAX_RETRIES) {
            String raw;
            try {
                raw = port.invoke(request);
            } catch (AgentUnavailableException e) {
                events.add(event(sequence, AgentTrace.TraceEvent.Kind.COMPLETED,
                        "Model unavailable; recorded as skipped"));
                return new AgentOutcome(trace(definition, request, events, retries,
                        AgentTrace.Status.SKIPPED, List.of()));
            }
            events.add(event(sequence++, AgentTrace.TraceEvent.Kind.RESPONSE_RECEIVED,
                    "Received " + raw.length() + " characters of unvalidated text"));

            try {
                List<AgentObservation> observations =
                        AgentContract.validate(request, raw, definition.mayBlockRelease());
                events.add(event(sequence++, AgentTrace.TraceEvent.Kind.SCHEMA_VALIDATED,
                        "Contract satisfied; " + observations.size() + " observation(s) parsed"));

                List<AgentObservation> accepted = new ArrayList<>();
                for (AgentObservation observation : observations) {
                    if (!definition.mayProposeAutomation()
                            && observation.proposedTier() == DecisionTier.AUTOMATE) {
                        events.add(event(sequence++, AgentTrace.TraceEvent.Kind.OBSERVATION_REJECTED,
                                "Rejected " + observation.observationId() + ": agent "
                                        + definition.agentId()
                                        + " is not permitted to propose automation"));
                        continue;
                    }
                    accepted.add(observation);
                    events.add(event(sequence++, AgentTrace.TraceEvent.Kind.OBSERVATION_ACCEPTED,
                            "Accepted " + observation.observationId() + " proposing "
                                    + observation.proposedTier() + " for " + observation.subject().key()));
                }
                events.add(event(sequence, AgentTrace.TraceEvent.Kind.COMPLETED,
                        "Completed with " + accepted.size() + " accepted observation(s)"));
                return new AgentOutcome(trace(definition, request, events, retries,
                        AgentTrace.Status.COMPLETED, accepted));
            } catch (JsonParseException e) {
                String rejection = e.getMessage();
                events.add(event(sequence++, AgentTrace.TraceEvent.Kind.SCHEMA_REJECTED,
                        "Contract breach: " + rejection));
                retries++;
                if (retries <= MAX_RETRIES) {
                    events.add(event(sequence++, AgentTrace.TraceEvent.Kind.RETRY,
                            "Retrying after contract breach (attempt " + (retries + 1) + ")"));
                }
            }
        }

        events.add(event(sequence, AgentTrace.TraceEvent.Kind.COMPLETED,
                "Discarded all output after " + retries + " contract breach(es)"));
        return new AgentOutcome(trace(definition, request, events, retries - 1,
                AgentTrace.Status.SCHEMA_REJECTED, List.of()));
    }

    private static AgentTrace.TraceEvent event(
            int sequence, AgentTrace.TraceEvent.Kind kind, String detail) {
        return new AgentTrace.TraceEvent(sequence, kind, detail);
    }

    private static AgentTrace trace(
            AgentDefinition definition,
            AgentRequest request,
            List<AgentTrace.TraceEvent> events,
            int retries,
            AgentTrace.Status status,
            List<AgentObservation> observations) {
        return new AgentTrace(
                traceId(definition, request),
                definition.agentId(),
                definition.agentVersion(),
                request.inputHash(),
                request.policyHash(),
                request.procedureVersionIds(),
                observations,
                events,
                Math.max(retries, 0),
                status);
    }

    private static String traceId(AgentDefinition definition, AgentRequest request) {
        // Deterministic: the same agent over the same prompt key always yields the same trace id, so
        // two runs can be compared trace by trace.
        return ("T." + definition.agentId() + "." + request.promptKey())
                .replaceAll("[^A-Za-z0-9_.-]", "-");
    }
}
