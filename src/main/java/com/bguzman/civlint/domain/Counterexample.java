package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * A concrete, minimal witness showing why a finding holds.
 *
 * <p>"Minimal" is a real constraint, not a label: each producer in the {@code verification} module
 * emits the smallest witness that still demonstrates the problem — the two steps that share a role,
 * the one gate whose appeal route disappeared, the shortest path that reaches no terminal state.
 * A large witness would be evidence that CivLint had not localised the fault.
 *
 * @param counterexampleId stable identifier
 * @param kind what sort of witness this is
 * @param description human-readable statement of what the witness shows
 * @param witnessPath the ordered steps, gates or fields that form the witness
 * @param witnessValues named values that make the witness concrete
 */
public record Counterexample(
        String counterexampleId,
        Kind kind,
        String description,
        List<String> witnessPath,
        SequencedMap<String, String> witnessValues) {

    /**
     * The sort of witness a counterexample carries.
     */
    public enum Kind {
        /** An appeal route present in the existing version has no counterpart in the proposed one. */
        APPEAL_ROUTE_REMOVED,
        /** A gate that could be appealed no longer can be. */
        APPEAL_RIGHT_WEAKENED,
        /** One role both prepares and approves the same decision. */
        SAME_ROLE_PREPARES_AND_APPROVES,
        /** Approval gates can be reached in an order other than the declared one. */
        APPROVAL_ORDER_VIOLATED,
        /** A step cannot be reached from the entry step. */
        STEP_UNREACHABLE,
        /** A path exists that never reaches a terminal step. */
        NO_TERMINAL_REACHABLE,
        /** The graph contains a cycle. */
        CYCLE_PRESENT,
        /** A mandatory human gate present in the existing version is gone or downgraded. */
        HUMAN_GATE_REMOVED,
        /** A step claims a tier its policy category does not permit. */
        TIER_NOT_PERMITTED,
        /** Two authoritative sources disagree about a field. */
        AUTHORITATIVE_CONFLICT,
        /** The mechanical comparison declined to conclude. */
        MECHANICAL_ABSTENTION,
        /** Required evidence is absent. */
        EVIDENCE_MISSING,
        /** The role named at a step cannot perform the action assigned to it. */
        ROLE_NOT_AUTHORISED
    }

    public Counterexample {
        counterexampleId = Identifiers.requireStable("counterexampleId", counterexampleId);
        Objects.requireNonNull(kind, "kind");
        description = Identifiers.requireText("description", description);
        Objects.requireNonNull(witnessPath, "witnessPath");
        if (witnessPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Counterexample " + counterexampleId + " must name at least one witness element");
        }
        witnessPath = List.copyOf(witnessPath);
        Objects.requireNonNull(witnessValues, "witnessValues");
        SequencedMap<String, String> copy = new TreeMap<>();
        witnessValues.forEach((k, v) -> copy.put(
                Objects.requireNonNull(k, "witness key"),
                Objects.requireNonNull(v, () -> "witness value for " + k)));
        witnessValues = Collections.unmodifiableSequencedMap(copy);
    }

    public static Counterexample of(
            String counterexampleId, Kind kind, String description, List<String> witnessPath) {
        return new Counterexample(counterexampleId, kind, description, witnessPath, new TreeMap<>());
    }

    public int witnessSize() {
        return witnessPath.size();
    }

    public Json toJson() {
        List<Json> values = witnessValues.entrySet().stream()
                .map(e -> Json.obj().put("name", e.getKey()).put("value", e.getValue()).build())
                .toList();
        return Json.obj()
                .put("counterexampleId", counterexampleId)
                .put("kind", kind)
                .put("description", description)
                .put("witnessPath", Json.strings(witnessPath))
                .put("witnessValues", Json.array(values))
                .build();
    }
}
