package com.bguzman.civlint.procedure;

import com.bguzman.civlint.domain.ApprovalGate;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.SeparationOfDuty;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The structural difference between an existing and a proposed procedure version.
 *
 * <p>This is a factual description with no judgement attached: it says a gate disappeared, not that
 * disappearing was wrong. Keeping the two apart matters because some removals are exactly what a
 * modernisation should do — a duplicated clerical sign-off, for instance — while others must block a
 * release. The {@code verification} module applies the policy that separates them.
 *
 * @param existingVersionId identifier of the existing version
 * @param proposedVersionId identifier of the proposed version
 * @param removedStepIds steps present in the existing version and absent from the proposed one
 * @param addedStepIds steps absent from the existing version and present in the proposed one
 * @param tierChanges steps whose declared tier changed
 * @param roleChanges steps whose required role changed
 * @param removedGateIds approval gates that disappeared
 * @param addedGateIds approval gates that appeared
 * @param gateChanges gates whose role, mandatory flag, appealability or sequence changed
 * @param removedDutyIds separation-of-duty constraints that disappeared
 * @param removedAppealStepIds appeal-route steps that disappeared
 */
public record VersionComparison(
        String existingVersionId,
        String proposedVersionId,
        List<String> removedStepIds,
        List<String> addedStepIds,
        List<TierChange> tierChanges,
        List<RoleChange> roleChanges,
        List<String> removedGateIds,
        List<String> addedGateIds,
        List<GateChange> gateChanges,
        List<String> removedDutyIds,
        List<String> removedAppealStepIds) {

    /**
     * A change to the decision tier declared for a step.
     *
     * @param stepId the step
     * @param existingTier the tier the existing version declared
     * @param proposedTier the tier the proposed version declares
     */
    public record TierChange(String stepId, DecisionTier existingTier, DecisionTier proposedTier) {

        public boolean weakens() {
            return existingTier.weakenedBy(proposedTier);
        }

        public Json toJson() {
            return Json.obj()
                    .put("stepId", stepId)
                    .put("existingTier", existingTier)
                    .put("proposedTier", proposedTier)
                    .put("weakens", weakens())
                    .build();
        }
    }

    /**
     * A change to the role required at a step.
     *
     * @param stepId the step
     * @param existingRole the role the existing version required
     * @param proposedRole the role the proposed version requires
     */
    public record RoleChange(
            String stepId,
            ReviewerRole existingRole,
            ReviewerRole proposedRole) {

        public Json toJson() {
            return Json.obj()
                    .put("stepId", stepId)
                    .put("existingRole", existingRole)
                    .put("proposedRole", proposedRole)
                    .build();
        }
    }

    /**
     * A change to an approval gate that exists in both versions.
     *
     * @param gateId the gate
     * @param appealabilityLost whether a gate that could be appealed no longer can be
     * @param mandatoryLost whether a gate that could not be skipped now can be
     * @param roleChanged whether the signing role changed
     * @param sequenceChanged whether the gate's position in the order changed
     */
    public record GateChange(
            String gateId,
            boolean appealabilityLost,
            boolean mandatoryLost,
            boolean roleChanged,
            boolean sequenceChanged) {

        public boolean weakensSafeguard() {
            return appealabilityLost || mandatoryLost;
        }

        public Json toJson() {
            return Json.obj()
                    .put("gateId", gateId)
                    .put("appealabilityLost", appealabilityLost)
                    .put("mandatoryLost", mandatoryLost)
                    .put("roleChanged", roleChanged)
                    .put("sequenceChanged", sequenceChanged)
                    .build();
        }
    }

    public VersionComparison {
        existingVersionId = Objects.requireNonNull(existingVersionId, "existingVersionId");
        proposedVersionId = Objects.requireNonNull(proposedVersionId, "proposedVersionId");
        removedStepIds = sortedCopy(removedStepIds, "removedStepIds");
        addedStepIds = sortedCopy(addedStepIds, "addedStepIds");
        removedGateIds = sortedCopy(removedGateIds, "removedGateIds");
        addedGateIds = sortedCopy(addedGateIds, "addedGateIds");
        removedDutyIds = sortedCopy(removedDutyIds, "removedDutyIds");
        removedAppealStepIds = sortedCopy(removedAppealStepIds, "removedAppealStepIds");
        tierChanges = Objects.requireNonNull(tierChanges, "tierChanges").stream()
                .sorted(Comparator.comparing(TierChange::stepId))
                .toList();
        roleChanges = Objects.requireNonNull(roleChanges, "roleChanges").stream()
                .sorted(Comparator.comparing(RoleChange::stepId))
                .toList();
        gateChanges = Objects.requireNonNull(gateChanges, "gateChanges").stream()
                .sorted(Comparator.comparing(GateChange::gateId))
                .toList();
    }

    private static List<String> sortedCopy(List<String> values, String name) {
        return Objects.requireNonNull(values, name).stream().sorted().distinct().toList();
    }

    public static VersionComparison compare(ProcedureVersion existing, ProcedureVersion proposed) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(proposed, "proposed");
        if (!existing.procedureId().equals(proposed.procedureId())) {
            throw new IllegalArgumentException(
                    "Cannot compare versions of different procedures: " + existing.procedureId()
                            + " and " + proposed.procedureId());
        }

        var existingSteps = existing.graph().steps();
        var proposedSteps = proposed.graph().steps();

        List<String> removedSteps = existingSteps.keySet().stream()
                .filter(id -> !proposedSteps.containsKey(id))
                .toList();
        List<String> addedSteps = proposedSteps.keySet().stream()
                .filter(id -> !existingSteps.containsKey(id))
                .toList();

        List<TierChange> tierChanges = existingSteps.values().stream()
                .filter(step -> proposedSteps.containsKey(step.stepId()))
                .filter(step -> proposedSteps.get(step.stepId()).declaredTier() != step.declaredTier())
                .map(step -> new TierChange(
                        step.stepId(),
                        step.declaredTier(),
                        proposedSteps.get(step.stepId()).declaredTier()))
                .toList();

        List<RoleChange> roleChanges = existingSteps.values().stream()
                .filter(step -> proposedSteps.containsKey(step.stepId()))
                .filter(step -> proposedSteps.get(step.stepId()).requiredRole() != step.requiredRole())
                .map(step -> new RoleChange(
                        step.stepId(),
                        step.requiredRole(),
                        proposedSteps.get(step.stepId()).requiredRole()))
                .toList();

        List<String> existingGateIds =
                existing.graph().approvalGates().stream().map(ApprovalGate::gateId).toList();
        List<String> proposedGateIds =
                proposed.graph().approvalGates().stream().map(ApprovalGate::gateId).toList();
        List<String> removedGates =
                existingGateIds.stream().filter(id -> !proposedGateIds.contains(id)).toList();
        List<String> addedGates =
                proposedGateIds.stream().filter(id -> !existingGateIds.contains(id)).toList();

        List<GateChange> gateChanges = existing.graph().approvalGates().stream()
                .filter(gate -> proposed.graph().gate(gate.gateId()).isPresent())
                .map(gate -> {
                    ApprovalGate after = proposed.graph().gate(gate.gateId()).orElseThrow();
                    return new GateChange(
                            gate.gateId(),
                            gate.appealable() && !after.appealable(),
                            gate.mandatory() && !after.mandatory(),
                            gate.requiredRole() != after.requiredRole(),
                            gate.sequence() != after.sequence());
                })
                .filter(change -> change.weakensSafeguard()
                        || change.roleChanged()
                        || change.sequenceChanged())
                .toList();

        List<String> existingDutyIds = existing.graph().separationOfDuties().stream()
                .map(SeparationOfDuty::dutyId)
                .toList();
        List<String> proposedDutyIds = proposed.graph().separationOfDuties().stream()
                .map(SeparationOfDuty::dutyId)
                .toList();
        List<String> removedDuties =
                existingDutyIds.stream().filter(id -> !proposedDutyIds.contains(id)).toList();

        var proposedAppealSteps = proposed.graph().appealStepIds();
        List<String> removedAppealSteps = existing.graph().appealStepIds().stream()
                .filter(id -> !proposedAppealSteps.contains(id))
                .toList();

        return new VersionComparison(
                existing.versionId(),
                proposed.versionId(),
                removedSteps,
                addedSteps,
                tierChanges,
                roleChanges,
                removedGates,
                addedGates,
                gateChanges,
                removedDuties,
                removedAppealSteps);
    }

    public boolean identical() {
        return removedStepIds.isEmpty()
                && addedStepIds.isEmpty()
                && tierChanges.isEmpty()
                && roleChanges.isEmpty()
                && removedGateIds.isEmpty()
                && addedGateIds.isEmpty()
                && gateChanges.isEmpty()
                && removedDutyIds.isEmpty()
                && removedAppealStepIds.isEmpty();
    }

    public List<TierChange> weakeningTierChanges() {
        return tierChanges.stream().filter(TierChange::weakens).toList();
    }

    public Optional<TierChange> tierChange(String stepId) {
        return tierChanges.stream().filter(c -> c.stepId().equals(stepId)).findFirst();
    }

    public Json toJson() {
        return Json.obj()
                .put("existingVersionId", existingVersionId)
                .put("proposedVersionId", proposedVersionId)
                .put("removedStepIds", Json.strings(removedStepIds))
                .put("addedStepIds", Json.strings(addedStepIds))
                .put("tierChanges", Json.array(tierChanges.stream().map(TierChange::toJson).toList()))
                .put("roleChanges", Json.array(roleChanges.stream().map(RoleChange::toJson).toList()))
                .put("removedGateIds", Json.strings(removedGateIds))
                .put("addedGateIds", Json.strings(addedGateIds))
                .put("gateChanges", Json.array(gateChanges.stream().map(GateChange::toJson).toList()))
                .put("removedDutyIds", Json.strings(removedDutyIds))
                .put("removedAppealStepIds", Json.strings(removedAppealStepIds))
                .build();
    }
}
