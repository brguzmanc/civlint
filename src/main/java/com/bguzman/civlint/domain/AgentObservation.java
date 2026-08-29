package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One structured proposal from an agent.
 *
 * <p>An observation is a <em>proposal</em>, never a verdict. The type deliberately has no field that
 * could authorise a release, and the verifier consumes observations only as candidate hypotheses to
 * be checked. Confidence is recorded because it is useful to a reader, and is ignored by every
 * safety decision.
 *
 * @param observationId stable identifier
 * @param agentId identifier of the proposing agent
 * @param subject what the observation is about
 * @param proposedTier the tier the agent proposes
 * @param category the policy category the agent believes applies
 * @param rationale the agent's stated reasoning
 * @param confidence the agent's confidence from 0 to 100
 * @param references artifacts the agent cites
 */
public record AgentObservation(
        String observationId,
        String agentId,
        FindingSubject subject,
        DecisionTier proposedTier,
        RuleCategory category,
        String rationale,
        int confidence,
        List<EvidenceReference> references) {

    /** The canonical ordering for observations, by identifier. */
    public static final Comparator<AgentObservation> SORT_ORDER =
            Comparator.comparing(AgentObservation::observationId);

    public AgentObservation {
        observationId = Identifiers.requireStable("observationId", observationId);
        agentId = Identifiers.requireStable("agentId", agentId);
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(proposedTier, "proposedTier");
        Objects.requireNonNull(category, "category");
        rationale = Identifiers.requireText("rationale", rationale);
        Objects.requireNonNull(references, "references");
        references = references.stream()
                .sorted(Comparator.comparing(EvidenceReference::kind)
                        .thenComparing(EvidenceReference::targetId))
                .distinct()
                .toList();
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException(
                    "Observation " + observationId + " confidence must be within [0,100]");
        }
    }

    public EvidenceReference reference() {
        return new EvidenceReference(
                EvidenceReference.Kind.AGENT_OBSERVATION,
                observationId,
                agentId + " proposed " + proposedTier);
    }

    public Json toJson() {
        return Json.obj()
                .put("observationId", observationId)
                .put("agentId", agentId)
                .put("subject", subject.toJson())
                .put("proposedTier", proposedTier)
                .put("category", category)
                .put("rationale", rationale)
                .put("confidence", confidence)
                .put("references", Json.array(references.stream().map(EvidenceReference::toJson).toList()))
                .build();
    }
}
