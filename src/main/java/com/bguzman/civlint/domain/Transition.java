package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;

/**
 * A directed edge between two steps of a procedure version.
 *
 * @param fromStepId the step the transition leaves
 * @param toStepId the step the transition enters
 * @param condition short human-readable label for when the transition is taken
 */
public record Transition(String fromStepId, String toStepId, String condition) {

    /**
     * Validates the transition.
     *
     * <p>Self-loops are rejected: in this procedure model a step that transitions to itself would
     * create an unbounded cycle with no observable progress.
     *
     * @throws NullPointerException if any component is {@code null}
     * @throws IllegalArgumentException if either identifier is malformed or the two are equal
     */
    public Transition {
        fromStepId = Identifiers.requireStable("fromStepId", fromStepId);
        toStepId = Identifiers.requireStable("toStepId", toStepId);
        condition = Identifiers.requireText("condition", condition);
        if (fromStepId.equals(toStepId)) {
            throw new IllegalArgumentException("Self-transition is not permitted at step " + fromStepId);
        }
    }

    public String sortKey() {
        return fromStepId + '>' + toStepId + '|' + condition;
    }

    public Json toJson() {
        return Json.obj()
                .put("fromStepId", fromStepId)
                .put("toStepId", toStepId)
                .put("condition", condition)
                .build();
    }
}
