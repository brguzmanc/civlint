package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The step graph of a single procedure version, with its approval gates and separation-of-duty
 * constraints.
 *
 * <p><strong>Referential invariants enforced at construction</strong> (structural findings such as
 * unreachability are <em>not</em> enforced here — they are reported by the verifier so that an
 * unsafe proposed version can still be loaded and explained rather than failing to parse):
 *
 * <ul>
 *   <li>Step identifiers are unique.
 *   <li>Every transition endpoint, gate step, and separation-of-duty step names an existing step.
 *   <li>The entry step exists and at least one terminal step exists.
 *   <li>Approval-gate sequence numbers are unique.
 * </ul>
 *
 * <p>All collections are stored in deterministic order: steps by identifier, transitions by
 * {@link Transition#sortKey()}, gates by sequence then identifier, duties by identifier.
 *
 * @param steps steps keyed by identifier, ordered by identifier
 * @param transitions transitions in stable sort-key order
 * @param entryStepId identifier of the step where the procedure begins
 * @param approvalGates approval gates in sequence order
 * @param separationOfDuties separation-of-duty constraints in identifier order
 */
public record ProcedureGraph(
        SequencedMap<String, ProcedureStep> steps,
        List<Transition> transitions,
        String entryStepId,
        List<ApprovalGate> approvalGates,
        List<SeparationOfDuty> separationOfDuties) {

    public ProcedureGraph {
        Objects.requireNonNull(steps, "steps");
        SequencedMap<String, ProcedureStep> stepCopy = new TreeMap<>();
        steps.forEach((id, step) -> {
            Objects.requireNonNull(step, () -> "step " + id);
            if (!step.stepId().equals(Identifiers.requireStable("step key", id))) {
                throw new IllegalArgumentException(
                        "Step key " + id + " does not match step identifier " + step.stepId());
            }
            stepCopy.put(step.stepId(), step);
        });
        steps = Collections.unmodifiableSequencedMap(stepCopy);

        entryStepId = Identifiers.requireStable("entryStepId", entryStepId);
        if (!stepCopy.containsKey(entryStepId)) {
            throw new IllegalArgumentException("Entry step " + entryStepId + " is not a declared step");
        }

        Objects.requireNonNull(transitions, "transitions");
        for (Transition transition : transitions) {
            Objects.requireNonNull(transition, "transition");
            requireStep(stepCopy, transition.fromStepId(), "transition source");
            requireStep(stepCopy, transition.toStepId(), "transition target");
        }
        transitions = transitions.stream()
                .sorted(Comparator.comparing(Transition::sortKey))
                .distinct()
                .toList();

        Objects.requireNonNull(approvalGates, "approvalGates");
        Set<Integer> sequences = new TreeSet<>();
        Set<String> gateIds = new TreeSet<>();
        for (ApprovalGate gate : approvalGates) {
            Objects.requireNonNull(gate, "approval gate");
            requireStep(stepCopy, gate.stepId(), "approval gate step");
            if (!gateIds.add(gate.gateId())) {
                throw new IllegalArgumentException("Duplicate approval gate identifier " + gate.gateId());
            }
            if (!sequences.add(gate.sequence())) {
                throw new IllegalArgumentException(
                        "Duplicate approval gate sequence " + gate.sequence() + " at " + gate.gateId());
            }
        }
        approvalGates = approvalGates.stream()
                .sorted(Comparator.comparingInt(ApprovalGate::sequence).thenComparing(ApprovalGate::gateId))
                .toList();

        Objects.requireNonNull(separationOfDuties, "separationOfDuties");
        Set<String> dutyIds = new TreeSet<>();
        for (SeparationOfDuty duty : separationOfDuties) {
            Objects.requireNonNull(duty, "separation of duty");
            requireStep(stepCopy, duty.preparingStepId(), "separation-of-duty preparing step");
            requireStep(stepCopy, duty.approvingStepId(), "separation-of-duty approving step");
            if (!dutyIds.add(duty.dutyId())) {
                throw new IllegalArgumentException("Duplicate separation-of-duty identifier " + duty.dutyId());
            }
        }
        separationOfDuties = separationOfDuties.stream()
                .sorted(Comparator.comparing(SeparationOfDuty::dutyId))
                .toList();

        if (stepCopy.values().stream().noneMatch(step -> step.kind() == StepKind.TERMINAL)) {
            throw new IllegalArgumentException("A procedure graph must declare at least one TERMINAL step");
        }
    }

    private static void requireStep(
            SequencedMap<String, ProcedureStep> steps, String stepId, String what) {
        if (!steps.containsKey(stepId)) {
            throw new IllegalArgumentException(what + " " + stepId + " is not a declared step");
        }
    }

    public Optional<ProcedureStep> step(String stepId) {
        return Optional.ofNullable(steps.get(stepId));
    }

    public SequencedSet<String> successors(String stepId) {
        SequencedSet<String> out = new TreeSet<>();
        transitions.stream()
                .filter(t -> t.fromStepId().equals(stepId))
                .forEach(t -> out.add(t.toStepId()));
        return Collections.unmodifiableSequencedSet(out);
    }

    public SequencedSet<String> terminalStepIds() {
        SequencedSet<String> out = new TreeSet<>();
        steps.values().stream()
                .filter(step -> step.kind() == StepKind.TERMINAL)
                .forEach(step -> out.add(step.stepId()));
        return Collections.unmodifiableSequencedSet(out);
    }

    /**
     * Computes the steps reachable from the entry step by breadth-first traversal.
     *
     * <p>Traversal visits successors in ascending identifier order so the result — and any finding
     * derived from it — does not depend on collection iteration order.
     *
     * @return reachable step identifiers in ascending order, always including the entry step
     */
    public SequencedSet<String> reachableStepIds() {
        SequencedSet<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(entryStepId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            successors(current).forEach(next -> {
                if (!visited.contains(next)) {
                    queue.addLast(next);
                }
            });
        }
        SequencedSet<String> sorted = new TreeSet<>(visited);
        return Collections.unmodifiableSequencedSet(sorted);
    }

    public SequencedSet<String> unreachableStepIds() {
        SequencedSet<String> reachable = reachableStepIds();
        SequencedSet<String> out = new TreeSet<>(steps.keySet());
        out.removeAll(reachable);
        return Collections.unmodifiableSequencedSet(out);
    }

    /**
     * Finds one cycle in the graph, if any exists.
     *
     * <p>A single minimal example is returned rather than every cycle, because the purpose is to give
     * a reader a concrete counterexample. Successors are explored in ascending identifier order, so
     * the cycle reported for a given graph is always the same one.
     *
     * @return the steps forming a cycle, in traversal order, or empty when the graph is acyclic
     */
    public Optional<List<String>> findCycle() {
        Set<String> permanentlyDone = new TreeSet<>();
        for (String start : steps.keySet()) {
            if (permanentlyDone.contains(start)) {
                continue;
            }
            List<String> path = new ArrayList<>();
            Set<String> onPath = new LinkedHashSet<>();
            Optional<List<String>> cycle = depthFirst(start, path, onPath, permanentlyDone);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private Optional<List<String>> depthFirst(
            String current, List<String> path, Set<String> onPath, Set<String> done) {
        if (onPath.contains(current)) {
            int from = path.indexOf(current);
            List<String> cycle = new ArrayList<>(path.subList(from, path.size()));
            cycle.add(current);
            return Optional.of(List.copyOf(cycle));
        }
        if (done.contains(current)) {
            return Optional.empty();
        }
        onPath.add(current);
        path.add(current);
        for (String next : successors(current)) {
            Optional<List<String>> cycle = depthFirst(next, path, onPath, done);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        onPath.remove(current);
        path.removeLast();
        done.add(current);
        return Optional.empty();
    }

    public SequencedSet<String> appealStepIds() {
        SequencedSet<String> out = new TreeSet<>();
        steps.values().stream()
                .filter(step -> step.appealPath() || step.kind() == StepKind.APPEAL)
                .forEach(step -> out.add(step.stepId()));
        return Collections.unmodifiableSequencedSet(out);
    }

    public SequencedSet<String> mandatoryHumanGateStepIds() {
        SequencedSet<String> out = new TreeSet<>();
        steps.values().stream()
                .filter(ProcedureStep::mandatoryHumanGate)
                .forEach(step -> out.add(step.stepId()));
        return Collections.unmodifiableSequencedSet(out);
    }

    public int totalHumanTouchCost() {
        return steps.values().stream()
                .filter(ProcedureStep::humanTouch)
                .mapToInt(ProcedureStep::touchCost)
                .sum();
    }

    public Optional<ApprovalGate> gate(String gateId) {
        return approvalGates.stream().filter(g -> g.gateId().equals(gateId)).findFirst();
    }

    public Json toJson() {
        List<Json> stepJson = new ArrayList<>();
        steps.values().forEach(step -> stepJson.add(step.toJson()));
        return Json.obj()
                .put("entryStepId", entryStepId)
                .put("steps", Json.array(stepJson))
                .put("transitions", Json.array(transitions.stream().map(Transition::toJson).toList()))
                .put("approvalGates", Json.array(approvalGates.stream().map(ApprovalGate::toJson).toList()))
                .put(
                        "separationOfDuties",
                        Json.array(separationOfDuties.stream().map(SeparationOfDuty::toJson).toList()))
                .build();
    }
}
