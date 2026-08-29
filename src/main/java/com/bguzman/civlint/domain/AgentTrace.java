package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The complete, replayable record of one agent invocation.
 *
 * <p>A trace pins the agent to the exact inputs it saw: the input hash, the policy hash and the
 * procedure versions. Two runs whose traces carry the same hashes and the same observations are the
 * same run, and a trace whose hashes differ explains a divergence without guesswork.
 *
 * <p><strong>Invariants:</strong> observations are stored in identifier order; a trace with status
 * {@link Status#SCHEMA_REJECTED} or {@link Status#FAILED} carries no observations, because output that
 * failed validation must not reach the verifier even partially.
 *
 * @param traceId stable identifier
 * @param agentId identifier of the agent
 * @param agentVersion version of the agent contract and prompt
 * @param inputHash canonical hash of the input the agent saw
 * @param policyHash canonical hash of the policy pack in force
 * @param procedureVersionIds the procedure versions in scope, in ascending order
 * @param observations the agent's proposals, in ascending identifier order
 * @param events ordered trace events describing what the agent did
 * @param retries how many times the call was retried after a schema rejection
 * @param status the final status of the invocation
 */
public record AgentTrace(
        String traceId,
        String agentId,
        String agentVersion,
        String inputHash,
        String policyHash,
        List<String> procedureVersionIds,
        List<AgentObservation> observations,
        List<TraceEvent> events,
        int retries,
        Status status) {

    /**
     * The outcome of an agent invocation.
     */
    public enum Status {
        /** The agent returned output that validated against its contract. */
        COMPLETED,
        /** The agent returned output that failed contract validation and was discarded. */
        SCHEMA_REJECTED,
        /** The agent could not be invoked or raised an error. */
        FAILED,
        /** The agent was not invoked because no fixture or adapter was available. */
        SKIPPED
    }

    /**
     * One step in an agent's recorded activity.
     *
     * @param sequence one-based position in the trace
     * @param kind what happened
     * @param detail human-readable detail, already redacted
     */
    public record TraceEvent(int sequence, Kind kind, String detail) {

        /**
         * The kinds of event a trace records.
         */
        public enum Kind {
            /** The agent received its input. */
            INPUT_RECEIVED,
            /** The agent's prompt or fixture key was resolved. */
            PROMPT_RESOLVED,
            /** A response was received from the model port. */
            RESPONSE_RECEIVED,
            /** The response was validated against the agent contract. */
            SCHEMA_VALIDATED,
            /** The response failed contract validation. */
            SCHEMA_REJECTED,
            /** The call was retried. */
            RETRY,
            /** An observation was accepted. */
            OBSERVATION_ACCEPTED,
            /** An observation was rejected as out of contract. */
            OBSERVATION_REJECTED,
            /** The invocation finished. */
            COMPLETED
        }

        public TraceEvent {
            Objects.requireNonNull(kind, "kind");
            detail = Identifiers.requireText("detail", detail);
            if (sequence < 1) {
                throw new IllegalArgumentException("Trace event sequence must be positive");
            }
        }

        public Json toJson() {
            return Json.obj().put("sequence", sequence).put("kind", kind).put("detail", detail).build();
        }
    }

    public AgentTrace {
        traceId = Identifiers.requireStable("traceId", traceId);
        agentId = Identifiers.requireStable("agentId", agentId);
        agentVersion = Identifiers.requireText("agentVersion", agentVersion);
        inputHash = requireDigest("inputHash", inputHash);
        policyHash = requireDigest("policyHash", policyHash);
        Objects.requireNonNull(procedureVersionIds, "procedureVersionIds");
        procedureVersionIds = procedureVersionIds.stream().sorted().distinct().toList();
        Objects.requireNonNull(observations, "observations");
        observations = observations.stream().sorted(AgentObservation.SORT_ORDER).toList();
        Objects.requireNonNull(events, "events");
        events = events.stream().sorted(Comparator.comparingInt(TraceEvent::sequence)).toList();
        Objects.requireNonNull(status, "status");
        if (retries < 0) {
            throw new IllegalArgumentException("Trace " + traceId + " has negative retries");
        }
        if ((status == Status.SCHEMA_REJECTED || status == Status.FAILED) && !observations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Trace " + traceId + " has status " + status
                            + " so it must not carry observations that could reach the verifier");
        }
    }

    private static String requireDigest(String name, String value) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lower-case hex SHA-256 digest");
        }
        return value;
    }

    public boolean usable() {
        return status == Status.COMPLETED;
    }

    public Json toJson() {
        return Json.obj()
                .put("traceId", traceId)
                .put("agentId", agentId)
                .put("agentVersion", agentVersion)
                .put("inputHash", inputHash)
                .put("policyHash", policyHash)
                .put("procedureVersionIds", Json.strings(procedureVersionIds))
                .put("observations", Json.array(observations.stream().map(AgentObservation::toJson).toList()))
                .put("events", Json.array(events.stream().map(TraceEvent::toJson).toList()))
                .put("retries", retries)
                .put("status", status)
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toJson());
    }
}
