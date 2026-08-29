package com.bguzman.civlint.verification;

import com.bguzman.civlint.domain.ProcedureGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.TreeSet;

/**
 * Graph queries the structural checks need, each producing a concrete witness rather than a boolean.
 *
 * <p>A boolean answer to "can this gate be bypassed?" is not enough for a report a reviewer can act
 * on. These helpers return the actual path that demonstrates the problem, so a finding can name the
 * route rather than assert its existence.
 *
 * <p><strong>Determinism:</strong> every traversal expands successors in ascending step-identifier
 * order and uses breadth-first search, so the path returned for a given graph is always the same
 * one, and it is a shortest such path.
 */
public final class GraphAnalysis {

    private GraphAnalysis() {
        throw new AssertionError("No instances.");
    }

    public static Optional<List<String>> shortestPath(
            ProcedureGraph graph, String fromStepId, String toStepId) {
        return shortestPathAvoiding(graph, fromStepId, toStepId, null);
    }

    /**
     * Finds a shortest path between two steps that does not pass through a forbidden step.
     *
     * <p>This is the query that detects a bypassed approval gate: if a path to a later gate exists
     * that avoids an earlier one, the declared approval order is not actually enforced by the graph,
     * and the returned path is the proof.
     *
     * @param graph the graph to search; must not be {@code null}
     * @param fromStepId the step to start from; must not be {@code null}
     * @param toStepId the step to reach; must not be {@code null}
     * @param avoidStepId a step the path must not visit, or {@code null} to impose no restriction
     * @return the path inclusive of both endpoints, or empty when no such path exists
     * @throws NullPointerException if {@code graph}, {@code fromStepId} or {@code toStepId} is
     *     {@code null}
     */
    public static Optional<List<String>> shortestPathAvoiding(
            ProcedureGraph graph, String fromStepId, String toStepId, String avoidStepId) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(fromStepId, "fromStepId");
        Objects.requireNonNull(toStepId, "toStepId");

        if (fromStepId.equals(avoidStepId) || toStepId.equals(avoidStepId)) {
            return Optional.empty();
        }
        if (fromStepId.equals(toStepId)) {
            return Optional.of(List.of(fromStepId));
        }

        Map<String, String> parents = new LinkedHashMap<>();
        SequencedSet<String> visited = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromStepId);
        visited.add(fromStepId);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : graph.successors(current)) {
                if (next.equals(avoidStepId) || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                parents.put(next, current);
                if (next.equals(toStepId)) {
                    return Optional.of(reconstruct(parents, fromStepId, toStepId));
                }
                queue.addLast(next);
            }
        }
        return Optional.empty();
    }

    private static List<String> reconstruct(
            Map<String, String> parents, String fromStepId, String toStepId) {
        List<String> reversed = new ArrayList<>();
        String cursor = toStepId;
        while (cursor != null && !cursor.equals(fromStepId)) {
            reversed.add(cursor);
            cursor = parents.get(cursor);
        }
        reversed.add(fromStepId);
        return List.copyOf(reversed.reversed());
    }

    public static boolean dominates(ProcedureGraph graph, String gateStepId, String targetStepId) {
        Objects.requireNonNull(graph, "graph");
        return shortestPathAvoiding(graph, graph.entryStepId(), targetStepId, gateStepId).isEmpty();
    }

    /**
     * Finds the reachable steps from which no terminal step can be reached.
     *
     * <p>Such a step is a dead end: a case that arrives there can never be concluded, which in a
     * public procedure means an applicant left without an outcome.
     *
     * @param graph the graph to check; must not be {@code null}
     * @return offending step identifiers in ascending order
     * @throws NullPointerException if {@code graph} is {@code null}
     */
    public static SequencedSet<String> stepsWithNoTerminal(ProcedureGraph graph) {
        Objects.requireNonNull(graph, "graph");
        SequencedSet<String> terminals = graph.terminalStepIds();
        SequencedSet<String> offenders = new TreeSet<>();
        for (String stepId : graph.reachableStepIds()) {
            boolean canFinish = terminals.stream()
                    .anyMatch(terminal -> shortestPath(graph, stepId, terminal).isPresent());
            if (!canFinish) {
                offenders.add(stepId);
            }
        }
        return Collections.unmodifiableSequencedSet(offenders);
    }
}
