package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * One item of evidence attached to a correction request, together with the record-field values it
 * claims.
 *
 * <p><strong>Invariants:</strong> the identifier is a stable identifier; {@code claims} is an
 * immutable map ordered by field name so that canonical output does not depend on insertion order.
 * A field value is stored exactly as supplied — normalisation is a verifier concern, never a storage
 * concern, because the raw value is the evidence.
 *
 * @param evidenceId stable identifier, unique within a request
 * @param type the kind of document or attestation
 * @param issuingAuthority synthetic name of the issuing office
 * @param referenceId the issuer's own reference for the item
 * @param certified whether the item is certified by its issuer
 * @param legible whether the item could be read; an illegible item cannot support a mechanical
 *     conclusion
 * @param claims record-field values this item asserts, keyed by field name
 */
public record EvidenceItem(
        String evidenceId,
        EvidenceType type,
        String issuingAuthority,
        String referenceId,
        boolean certified,
        boolean legible,
        SequencedMap<String, String> claims) {

    public EvidenceItem {
        evidenceId = Identifiers.requireStable("evidenceId", evidenceId);
        Objects.requireNonNull(type, "type");
        issuingAuthority = Identifiers.requireText("issuingAuthority", issuingAuthority);
        referenceId = Identifiers.requireText("referenceId", referenceId);
        claims = copySorted(claims);
    }

    private static SequencedMap<String, String> copySorted(Map<String, String> source) {
        Objects.requireNonNull(source, "claims");
        SequencedMap<String, String> copy = new TreeMap<>();
        source.forEach((field, value) -> copy.put(
                Identifiers.requireText("claim field", field),
                Objects.requireNonNull(value, () -> "claim value for " + field)));
        return Collections.unmodifiableSequencedMap(copy);
    }

    /**
     * Indicates whether this item may support a mechanical conclusion.
     *
     * <p>An item that is illegible, or that is uncertified while coming from an authoritative
     * source, is not usable mechanically. It is not discarded: it is routed to a person.
     *
     * @return {@code true} when the item is legible and adequately certified
     */
    public boolean usableMechanically() {
        return legible && (certified || !type.authoritative());
    }

    public EvidenceReference reference() {
        return new EvidenceReference(
                EvidenceReference.Kind.EVIDENCE_ITEM, evidenceId, type.label() + " " + referenceId);
    }

    public Json toJson() {
        List<Json> claimEntries = claims.entrySet().stream()
                .map(e -> Json.obj().put("field", e.getKey()).put("value", e.getValue()).build())
                .toList();
        return Json.obj()
                .put("evidenceId", evidenceId)
                .put("type", type)
                .put("issuingAuthority", issuingAuthority)
                .put("referenceId", referenceId)
                .put("certified", certified)
                .put("legible", legible)
                .put("claims", Json.array(claimEntries))
                .build();
    }
}
