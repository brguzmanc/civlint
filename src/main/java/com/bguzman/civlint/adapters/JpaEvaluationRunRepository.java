package com.bguzman.civlint.adapters;

import com.bguzman.civlint.application.EvaluationRunRepository;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Digest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores runs in the relational schema and keeps the in-memory run object for retrieval.
 *
 * <p><strong>Documented limitation.</strong> The relational row holds the run's indexed summary and
 * the exact canonical document, which is what a reproducibility check needs: a stored run can be
 * re-hashed and compared byte for byte. It does not currently rehydrate a full
 * {@link EvaluationRun} object graph from SQL — reading a run back after a restart would require a
 * canonical-JSON-to-domain reader that does not yet exist. Until it does, {@link #findById(String)}
 * serves from an in-process cache populated on save, and this limitation is recorded in
 * {@code README.md} and {@code docs/architecture.md} rather than hidden behind an
 * implementation that appeared to work.
 */
@Repository
public class JpaEvaluationRunRepository implements EvaluationRunRepository {

    private final EvaluationRunJpaRepository delegate;
    private final Map<String, EvaluationRun> inProcess = new ConcurrentHashMap<>();

    public JpaEvaluationRunRepository(EvaluationRunJpaRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public EvaluationRun save(EvaluationRun run) {
        Objects.requireNonNull(run, "run");
        // Serialised once and hashed from those bytes: run.canonicalHash() would rebuild the same
        // document and re-serialise it, which is both wasted work and a second place to diverge.
        String canonical = CanonicalJson.write(run.toCanonicalJson());
        String canonicalHash = Digest.sha256Hex(canonical);
        delegate.save(new EvaluationRunEntity(
                run.runId(),
                run.mode().name(),
                run.policyPackId(),
                run.policyHash(),
                run.humanNecessityMapHash(),
                run.existingVersionId(),
                run.proposedVersionId(),
                run.existingVersionHash(),
                run.proposedVersionHash(),
                run.ruleEngineVersion(),
                run.verifierVersion(),
                run.releaseDecision().outcome().name(),
                run.releaseDecision().blockingFindingIds().size(),
                run.findings().size(),
                run.traces().size(),
                canonicalHash,
                canonical,
                run.startedAtEpochMilli(),
                run.durationMillis()));
        inProcess.put(run.runId(), run);
        return run;
    }

    @Override
    public Optional<EvaluationRun> findById(String runId) {
        Objects.requireNonNull(runId, "runId");
        return Optional.ofNullable(inProcess.get(runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listRunIds() {
        return delegate.findAllByOrderByRunIdAsc().stream()
                .map(EvaluationRunEntity::getRunId)
                .toList();
    }

}
