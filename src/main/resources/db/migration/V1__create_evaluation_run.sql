-- CivLint schema, version 1.
--
-- A run is stored as its indexed summary plus the canonical JSON that produced its hash. Storing the
-- canonical document verbatim rather than a normalised object graph is deliberate: the hash is the
-- reproducibility contract, and re-serialising from decomposed rows would risk a store round-trip
-- changing the bytes the hash covers.
CREATE TABLE evaluation_run (
    run_id                   VARCHAR(64)   NOT NULL,
    mode                     VARCHAR(16)   NOT NULL,
    policy_pack_id           VARCHAR(64)   NOT NULL,
    policy_hash              VARCHAR(64)   NOT NULL,
    human_necessity_map_hash VARCHAR(64)   NOT NULL,
    existing_version_id      VARCHAR(64)   NOT NULL,
    proposed_version_id      VARCHAR(64)   NOT NULL,
    existing_version_hash    VARCHAR(64)   NOT NULL,
    proposed_version_hash    VARCHAR(64)   NOT NULL,
    rule_engine_version      VARCHAR(64)   NOT NULL,
    verifier_version         VARCHAR(64)   NOT NULL,
    release_outcome          VARCHAR(16)   NOT NULL,
    blocking_finding_count   INTEGER       NOT NULL,
    finding_count            INTEGER       NOT NULL,
    trace_count              INTEGER       NOT NULL,
    canonical_hash           VARCHAR(64)   NOT NULL,
    canonical_document       CLOB          NOT NULL,
    started_at_epoch_milli   BIGINT        NOT NULL,
    duration_millis          BIGINT        NOT NULL,
    CONSTRAINT pk_evaluation_run PRIMARY KEY (run_id)
);

CREATE INDEX idx_evaluation_run_mode ON evaluation_run (mode);
CREATE INDEX idx_evaluation_run_canonical_hash ON evaluation_run (canonical_hash);
