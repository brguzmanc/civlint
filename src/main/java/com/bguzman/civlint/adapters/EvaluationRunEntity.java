package com.bguzman.civlint.adapters;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * JPA mapping for a stored evaluation run.
 *
 * <p>The indexed columns exist for querying; {@code canonicalDocument} holds the exact canonical JSON
 * whose SHA-256 is {@code canonicalHash}. Keeping the document verbatim means a stored run can be
 * re-hashed and compared byte for byte, which a decomposed mapping could not guarantee.
 */
@Entity
@Table(name = "evaluation_run")
public class EvaluationRunEntity {

    @Id
    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "policy_pack_id", nullable = false, length = 64)
    private String policyPackId;

    @Column(name = "policy_hash", nullable = false, length = 64)
    private String policyHash;

    @Column(name = "human_necessity_map_hash", nullable = false, length = 64)
    private String humanNecessityMapHash;

    @Column(name = "existing_version_id", nullable = false, length = 64)
    private String existingVersionId;

    @Column(name = "proposed_version_id", nullable = false, length = 64)
    private String proposedVersionId;

    @Column(name = "existing_version_hash", nullable = false, length = 64)
    private String existingVersionHash;

    @Column(name = "proposed_version_hash", nullable = false, length = 64)
    private String proposedVersionHash;

    @Column(name = "rule_engine_version", nullable = false, length = 64)
    private String ruleEngineVersion;

    @Column(name = "verifier_version", nullable = false, length = 64)
    private String verifierVersion;

    @Column(name = "release_outcome", nullable = false, length = 16)
    private String releaseOutcome;

    @Column(name = "blocking_finding_count", nullable = false)
    private int blockingFindingCount;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "trace_count", nullable = false)
    private int traceCount;

    @Column(name = "canonical_hash", nullable = false, length = 64)
    private String canonicalHash;

    @Lob
    @Column(name = "canonical_document", nullable = false)
    private String canonicalDocument;

    @Column(name = "started_at_epoch_milli", nullable = false)
    private long startedAtEpochMilli;

    @Column(name = "duration_millis", nullable = false)
    private long durationMillis;

    /** Required by JPA. */
    protected EvaluationRunEntity() {
    }

    public EvaluationRunEntity(
            String runId,
            String mode,
            String policyPackId,
            String policyHash,
            String humanNecessityMapHash,
            String existingVersionId,
            String proposedVersionId,
            String existingVersionHash,
            String proposedVersionHash,
            String ruleEngineVersion,
            String verifierVersion,
            String releaseOutcome,
            int blockingFindingCount,
            int findingCount,
            int traceCount,
            String canonicalHash,
            String canonicalDocument,
            long startedAtEpochMilli,
            long durationMillis) {
        this.runId = runId;
        this.mode = mode;
        this.policyPackId = policyPackId;
        this.policyHash = policyHash;
        this.humanNecessityMapHash = humanNecessityMapHash;
        this.existingVersionId = existingVersionId;
        this.proposedVersionId = proposedVersionId;
        this.existingVersionHash = existingVersionHash;
        this.proposedVersionHash = proposedVersionHash;
        this.ruleEngineVersion = ruleEngineVersion;
        this.verifierVersion = verifierVersion;
        this.releaseOutcome = releaseOutcome;
        this.blockingFindingCount = blockingFindingCount;
        this.findingCount = findingCount;
        this.traceCount = traceCount;
        this.canonicalHash = canonicalHash;
        this.canonicalDocument = canonicalDocument;
        this.startedAtEpochMilli = startedAtEpochMilli;
        this.durationMillis = durationMillis;
    }

    public String getRunId() {
        return runId;
    }

    public String getCanonicalHash() {
        return canonicalHash;
    }

    public String getCanonicalDocument() {
        return canonicalDocument;
    }

}
