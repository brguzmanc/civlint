package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One step of a procedure version.
 *
 * <p><strong>Invariants enforced at construction:</strong>
 *
 * <ul>
 *   <li>A step whose {@link StepKind#consequential()} is {@code true} may not declare
 *       {@link DecisionTier#AUTOMATE}. An unattended machine must not impose an outcome.
 *   <li>A step whose tier involves a human must name a human {@link ReviewerRole}, and a step at
 *       {@link DecisionTier#AUTOMATE} must name {@link ReviewerRole#NONE}. This closes the gap where
 *       a step claims to need review but assigns nobody to perform it.
 *   <li>{@code touchCost} is non-negative; it is the burden unit used by the touch-count metric.
 * </ul>
 *
 * @param stepId stable identifier, unique within a procedure version
 * @param title short human-readable title
 * @param kind the role the step plays
 * @param declaredTier the tier the procedure version claims for this step
 * @param categories the policy categories this step engages
 * @param requiredRole the role that must act, or {@link ReviewerRole#NONE} for automated steps
 * @param appealPath whether the step forms part of the appeal route
 * @param touchCost burden units consumed when a person performs this step
 */
public record ProcedureStep(
        String stepId,
        String title,
        StepKind kind,
        DecisionTier declaredTier,
        Set<RuleCategory> categories,
        ReviewerRole requiredRole,
        boolean appealPath,
        int touchCost) {

    public ProcedureStep {
        stepId = Identifiers.requireStable("stepId", stepId);
        title = Identifiers.requireText("title", title);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(declaredTier, "declaredTier");
        Objects.requireNonNull(requiredRole, "requiredRole");
        Objects.requireNonNull(categories, "categories");

        categories = categories.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(RuleCategory.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(categories));

        if (kind.consequential() && declaredTier == DecisionTier.AUTOMATE) {
            throw new IllegalArgumentException(
                    "Step " + stepId + " is a " + kind.label()
                            + " step, which imposes an outcome, so it cannot declare AUTOMATE");
        }
        if (declaredTier == DecisionTier.AUTOMATE && requiredRole != ReviewerRole.NONE) {
            throw new IllegalArgumentException(
                    "Step " + stepId + " declares AUTOMATE but assigns role " + requiredRole);
        }
        if (declaredTier != DecisionTier.AUTOMATE && !requiredRole.human()) {
            throw new IllegalArgumentException(
                    "Step " + stepId + " declares " + declaredTier + " but assigns no human role");
        }
        if (touchCost < 0) {
            throw new IllegalArgumentException("touchCost must not be negative for step " + stepId);
        }
    }

    public boolean mandatoryHumanGate() {
        return declaredTier.mandatoryHumanGate();
    }

    public boolean humanTouch() {
        return declaredTier != DecisionTier.AUTOMATE;
    }

    public EvidenceReference reference() {
        return EvidenceReference.step(stepId, title);
    }

    public Json toJson() {
        return Json.obj()
                .put("stepId", stepId)
                .put("title", title)
                .put("kind", kind)
                .put("declaredTier", declaredTier)
                .put("categories", Json.strings(categories.stream().map(Enum::name).sorted().toList()))
                .put("requiredRole", requiredRole)
                .put("appealPath", appealPath)
                .put("touchCost", touchCost)
                .build();
    }
}
