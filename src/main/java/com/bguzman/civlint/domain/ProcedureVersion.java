package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * A named, hashable version of a procedure.
 *
 * <p>The {@link #canonicalHash()} covers the graph and the declared policy binding but deliberately
 * excludes the human-readable {@code label} and {@code notes}. Renaming a version must not change its
 * hash, because the hash is what a reproducibility check compares; conversely, moving a single
 * transition must change it.
 *
 * @param procedureId identifier of the procedure this version belongs to
 * @param versionId stable identifier of this version
 * @param label human-readable version label
 * @param graph the step graph
 * @param policyPackId identifier of the policy pack this version is written against
 * @param policyVersion version string of that policy pack
 * @param notes free-text notes; excluded from the canonical hash
 */
public record ProcedureVersion(
        String procedureId,
        String versionId,
        String label,
        ProcedureGraph graph,
        String policyPackId,
        String policyVersion,
        String notes) {

    public ProcedureVersion {
        procedureId = Identifiers.requireStable("procedureId", procedureId);
        versionId = Identifiers.requireStable("versionId", versionId);
        label = Identifiers.requireText("label", label);
        Objects.requireNonNull(graph, "graph");
        policyPackId = Identifiers.requireStable("policyPackId", policyPackId);
        policyVersion = Identifiers.requireText("policyVersion", policyVersion);
        notes = notes == null ? "" : notes.strip();
    }

    public Json toCanonicalJson() {
        return Json.obj()
                .put("procedureId", procedureId)
                .put("versionId", versionId)
                .put("policyPackId", policyPackId)
                .put("policyVersion", policyVersion)
                .put("graph", graph.toJson())
                .build();
    }

    public Json toJson() {
        return Json.obj()
                .put("procedureId", procedureId)
                .put("versionId", versionId)
                .put("label", label)
                .put("policyPackId", policyPackId)
                .put("policyVersion", policyVersion)
                .put("notes", notes)
                .put("graph", graph.toJson())
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toCanonicalJson());
    }
}
