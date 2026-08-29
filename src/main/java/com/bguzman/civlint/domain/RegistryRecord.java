package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * A civil-registry record in the fictional federation, as held before or after a correction.
 *
 * <p><strong>Invariants:</strong> {@code fields} is immutable and ordered by field name. A record
 * marked {@code immutableHistorical} represents a closed historical entry: policy in the synthetic
 * federation forbids editing it, so a correction must be expressed as a new entry that references
 * it rather than as a mutation.
 *
 * @param recordId stable identifier of the record
 * @param regionCode synthetic region code of the holding office
 * @param fields record field values keyed by field name
 * @param immutableHistorical whether the entry is a closed historical entry that must not be edited
 */
public record RegistryRecord(
        String recordId,
        String regionCode,
        SequencedMap<String, String> fields,
        boolean immutableHistorical) {

    /** Field name for the given name. */
    public static final String GIVEN_NAME = "GIVEN_NAME";

    /** Field name for the surname. */
    public static final String SURNAME = "SURNAME";

    /** Field name for the date of birth. */
    public static final String DATE_OF_BIRTH = "DATE_OF_BIRTH";

    /** Field name for the regional record identifier. */
    public static final String REGIONAL_ID = "REGIONAL_ID";

    /** Field name for the national record identifier. */
    public static final String NATIONAL_ID = "NATIONAL_ID";

    public RegistryRecord {
        recordId = Identifiers.requireStable("recordId", recordId);
        regionCode = Identifiers.requireStable("regionCode", regionCode);
        Objects.requireNonNull(fields, "fields");
        SequencedMap<String, String> copy = new TreeMap<>();
        fields.forEach((field, value) -> copy.put(
                Identifiers.requireStable("field name", field),
                Objects.requireNonNull(value, () -> "value of field " + field)));
        fields = Collections.unmodifiableSequencedMap(copy);
    }

    public Optional<String> field(String field) {
        return Optional.ofNullable(fields.get(field));
    }

    public Json toJson() {
        List<Json> entries = fields.entrySet().stream()
                .map(e -> Json.obj().put("field", e.getKey()).put("value", e.getValue()).build())
                .toList();
        return Json.obj()
                .put("recordId", recordId)
                .put("regionCode", regionCode)
                .put("immutableHistorical", immutableHistorical)
                .put("fields", Json.array(entries))
                .build();
    }
}
