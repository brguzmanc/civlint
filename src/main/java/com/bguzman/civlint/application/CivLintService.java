package com.bguzman.civlint.application;

import com.bguzman.civlint.agents.AgentModelPort;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.Procedure;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.evaluation.EvaluationHarness;
import com.bguzman.civlint.procedure.VersionComparison;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application service behind the API and the dashboard.
 *
 * <p>{@link #preview} and {@link #evaluate} return the same replay-verified document and differ only
 * in whether it is stored, so a published canonical hash does not depend on which surface a reader
 * arrived through.
 */
@Service
public class CivLintService {

    private final EvaluationRunRepository repository;
    private final EvaluationDatasetPort datasetPort;
    private final AgentModelPort modelPort;
    private final Clock clock;

    public CivLintService(
            EvaluationRunRepository repository,
            EvaluationDatasetPort datasetPort,
            AgentModelPort modelPort,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.datasetPort = Objects.requireNonNull(datasetPort, "datasetPort");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PolicyPack policyPack() {
        return datasetPort.load().policyPack();
    }

    public Procedure procedure() {
        return datasetPort.load().procedure();
    }

    public HumanNecessityMap humanNecessityMap() {
        return datasetPort.load().humanNecessityMap();
    }

    public List<EvaluationCase> cases() {
        return datasetPort.load().cases();
    }

    public Optional<VersionComparison> compare(String existingVersionId, String proposedVersionId) {
        Procedure procedure = datasetPort.load().procedure();
        Optional<ProcedureVersion> existing = procedure.version(existingVersionId);
        Optional<ProcedureVersion> proposed = procedure.version(proposedVersionId);
        if (existing.isEmpty() || proposed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(VersionComparison.compare(existing.get(), proposed.get()));
    }

    /** Produces a replay-verified run without storing anything; the dashboard renders this. */
    public EvaluationRun preview(EvaluationRun.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return harness().runReplayVerified(mode);
    }

    @Transactional
    public EvaluationRun evaluate(EvaluationRun.Mode mode) {
        return repository.save(preview(mode));
    }

    private EvaluationHarness harness() {
        EvaluationDatasetPort.Dataset dataset = datasetPort.load();
        return new EvaluationHarness(
                dataset.policyPack(),
                dataset.procedure(),
                dataset.humanNecessityMap(),
                dataset.cases(),
                dataset.existingVersionId(),
                dataset.proposedVersionId(),
                modelPort,
                clock);
    }

    public Optional<EvaluationRun> run(String runId) {
        return repository.findById(runId);
    }

    public List<String> runIds() {
        return repository.listRunIds();
    }
}
