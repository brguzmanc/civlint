package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.TreeSet;

/**
 * The first-class artifact recording, step by step, where human judgment is necessary and why.
 *
 * <p>This is the map the verifier checks a proposed procedure version against. It is versioned and
 * hashable for the same reason the policy pack is: a change in what CivLint considers safe must be
 * visible as a change in a hash, never as a quiet change in behaviour.
 *
 * <p><strong>Invariants:</strong> entry identifiers are unique; at most one entry governs a given
 * step and category pair; entries are stored in ascending identifier order.
 *
 * @param mapId stable identifier
 * @param version version string
 * @param procedureId the procedure this map governs
 * @param entries the entries, in ascending identifier order
 */
public record HumanNecessityMap(
        String mapId, String version, String procedureId, List<HumanNecessity> entries) {

    public HumanNecessityMap {
        mapId = Identifiers.requireStable("mapId", mapId);
        version = Identifiers.requireText("version", version);
        procedureId = Identifiers.requireStable("procedureId", procedureId);
        Objects.requireNonNull(entries, "entries");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A Human Necessity Map must contain at least one entry");
        }
        TreeSet<String> ids = new TreeSet<>();
        TreeSet<String> stepCategory = new TreeSet<>();
        for (HumanNecessity entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.entryId())) {
                throw new IllegalArgumentException("Duplicate entry identifier " + entry.entryId());
            }
            String key = entry.stepId() + '|' + entry.category().name();
            if (!stepCategory.add(key)) {
                throw new IllegalArgumentException(
                        "Two entries govern step " + entry.stepId() + " for category " + entry.category());
            }
        }
        entries = entries.stream().sorted(Comparator.comparing(HumanNecessity::entryId)).toList();
    }

    public List<HumanNecessity> entriesForStep(String stepId) {
        Objects.requireNonNull(stepId, "stepId");
        return entries.stream().filter(e -> e.stepId().equals(stepId)).toList();
    }

    public Optional<HumanNecessity> entry(String stepId, RuleCategory category) {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(category, "category");
        return entries.stream()
                .filter(e -> e.stepId().equals(stepId) && e.category() == category)
                .findFirst();
    }

    public Optional<DecisionTier> requiredTierForStep(String stepId) {
        return entriesForStep(stepId).stream()
                .map(HumanNecessity::effectiveTier)
                .reduce(DecisionTier::escalate);
    }

    public SequencedSet<String> mandatoryHumanGateStepIds() {
        SequencedSet<String> out = new TreeSet<>();
        entries.stream()
                .filter(HumanNecessity::mandatoryHumanGate)
                .forEach(e -> out.add(e.stepId()));
        return Collections.unmodifiableSequencedSet(out);
    }

    public Json toJson() {
        return Json.obj()
                .put("mapId", mapId)
                .put("version", version)
                .put("procedureId", procedureId)
                .put("entries", Json.array(entries.stream().map(HumanNecessity::toJson).toList()))
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toJson());
    }
}
