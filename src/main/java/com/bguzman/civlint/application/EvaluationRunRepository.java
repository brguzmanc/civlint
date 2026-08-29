package com.bguzman.civlint.application;

import com.bguzman.civlint.domain.EvaluationRun;
import java.util.List;
import java.util.Optional;

/**
 * The port through which runs are stored and retrieved.
 *
 * <p>Declared here rather than in an adapter so that the application layer depends on an interface it
 * owns. Implementations must preserve the ordering the domain types guarantee, because a store that
 * reordered findings would break the canonical-hash comparison a reproducibility check relies on.
 */
public interface EvaluationRunRepository {

    EvaluationRun save(EvaluationRun run);

    Optional<EvaluationRun> findById(String runId);

    List<String> listRunIds();
}
