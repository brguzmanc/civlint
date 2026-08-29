package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * A public procedure together with the versions CivLint knows about.
 *
 * <p><strong>Invariants:</strong> version identifiers are unique and stored in ascending order; every
 * version agrees with the procedure identifier. The {@code jurisdictionNote} exists to keep the
 * synthetic nature of the demonstration attached to the data itself rather than only to the
 * documentation.
 *
 * @param procedureId stable identifier
 * @param title human-readable title
 * @param jurisdictionNote statement of which fictional jurisdiction this describes
 * @param versions known versions, in ascending version-identifier order
 */
public record Procedure(
        String procedureId, String title, String jurisdictionNote, List<ProcedureVersion> versions) {

    public Procedure {
        procedureId = Identifiers.requireStable("procedureId", procedureId);
        title = Identifiers.requireText("title", title);
        jurisdictionNote = Identifiers.requireText("jurisdictionNote", jurisdictionNote);
        Objects.requireNonNull(versions, "versions");

        TreeSet<String> seen = new TreeSet<>();
        for (ProcedureVersion version : versions) {
            Objects.requireNonNull(version, "version");
            if (!version.procedureId().equals(procedureId)) {
                throw new IllegalArgumentException(
                        "Version " + version.versionId() + " belongs to procedure "
                                + version.procedureId() + ", not " + procedureId);
            }
            if (!seen.add(version.versionId())) {
                throw new IllegalArgumentException("Duplicate version identifier " + version.versionId());
            }
        }
        versions = versions.stream().sorted(Comparator.comparing(ProcedureVersion::versionId)).toList();
    }

    public Optional<ProcedureVersion> version(String versionId) {
        return versions.stream().filter(v -> v.versionId().equals(versionId)).findFirst();
    }

    public Json toJson() {
        return Json.obj()
                .put("procedureId", procedureId)
                .put("title", title)
                .put("jurisdictionNote", jurisdictionNote)
                .put("versions", Json.array(versions.stream().map(ProcedureVersion::toJson).toList()))
                .build();
    }
}
