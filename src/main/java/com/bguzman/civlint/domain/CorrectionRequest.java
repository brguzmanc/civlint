package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A synthetic request to correct a civil-registry record: the input a case is evaluated against.
 *
 * <p><strong>Invariants:</strong> evidence is stored sorted by evidence identifier and flags are
 * stored in a fixed enum order, so two requests built from the same data are equal and hash the
 * same regardless of how their collections were assembled. Requested fields are stored raw, because
 * the verifier's job includes deciding whether a difference is merely formatting.
 *
 * <p>All data in every fixture built from this type is invented. No real person's record is
 * represented.
 *
 * @param caseId stable identifier of the evaluation case
 * @param currentRecord the record as currently held
 * @param requestedFields the field values the applicant asks for, keyed by field name
 * @param evidence supporting evidence, sorted by identifier
 * @param flags declared characteristics of the request
 */
public record CorrectionRequest(
        String caseId,
        RegistryRecord currentRecord,
        SequencedMap<String, String> requestedFields,
        List<EvidenceItem> evidence,
        Set<RequestFlag> flags) {

    public CorrectionRequest {
        caseId = Identifiers.requireStable("caseId", caseId);
        Objects.requireNonNull(currentRecord, "currentRecord");

        Objects.requireNonNull(requestedFields, "requestedFields");
        SequencedMap<String, String> fieldCopy = new TreeMap<>();
        requestedFields.forEach((field, value) -> fieldCopy.put(
                Identifiers.requireStable("requested field", field),
                Objects.requireNonNull(value, () -> "requested value for " + field)));
        requestedFields = Collections.unmodifiableSequencedMap(fieldCopy);

        Objects.requireNonNull(evidence, "evidence");
        Set<String> seen = new TreeSet<>();
        for (EvidenceItem item : evidence) {
            Objects.requireNonNull(item, "evidence item");
            if (!seen.add(item.evidenceId())) {
                throw new IllegalArgumentException("Duplicate evidence identifier: " + item.evidenceId());
            }
        }
        evidence = evidence.stream()
                .sorted(Comparator.comparing(EvidenceItem::evidenceId))
                .toList();

        Objects.requireNonNull(flags, "flags");
        flags = flags.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(RequestFlag.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    public boolean has(RequestFlag flag) {
        return flags.contains(flag);
    }

    public Optional<String> requested(String field) {
        return Optional.ofNullable(requestedFields.get(field));
    }

    public List<EvidenceItem> evidenceOfType(EvidenceType type) {
        Objects.requireNonNull(type, "type");
        return evidence.stream().filter(item -> item.type() == type).toList();
    }

    public List<EvidenceItem> authoritativeClaimsFor(String field) {
        Objects.requireNonNull(field, "field");
        return evidence.stream()
                .filter(item -> item.type().authoritative())
                .filter(item -> item.claims().containsKey(field))
                .toList();
    }

    public List<String> changedFields() {
        return requestedFields.entrySet().stream()
                .filter(e -> !Objects.equals(currentRecord.fields().get(e.getKey()), e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public Json toJson() {
        List<Json> requested = requestedFields.entrySet().stream()
                .map(e -> Json.obj().put("field", e.getKey()).put("value", e.getValue()).build())
                .toList();
        return Json.obj()
                .put("caseId", caseId)
                .put("currentRecord", currentRecord.toJson())
                .put("requestedFields", Json.array(requested))
                .put("evidence", Json.array(evidence.stream().map(EvidenceItem::toJson).toList()))
                .put("flags", Json.strings(flags.stream().map(Enum::name).sorted().toList()))
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toJson());
    }
}
