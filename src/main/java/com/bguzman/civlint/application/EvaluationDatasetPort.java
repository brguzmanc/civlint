package com.bguzman.civlint.application;

import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.Procedure;
import java.util.List;
import java.util.Objects;

/**
 * Supplies one consistent evaluation dataset. External-system integrations implement this port;
 * this build uses a synthetic adapter.
 */
public interface EvaluationDatasetPort {

    Dataset load();

    record Dataset(
            PolicyPack policyPack,
            Procedure procedure,
            HumanNecessityMap humanNecessityMap,
            List<EvaluationCase> cases,
            String existingVersionId,
            String proposedVersionId) {

        public Dataset {
            Objects.requireNonNull(policyPack, "policyPack");
            Objects.requireNonNull(procedure, "procedure");
            Objects.requireNonNull(humanNecessityMap, "humanNecessityMap");
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
            Objects.requireNonNull(existingVersionId, "existingVersionId");
            Objects.requireNonNull(proposedVersionId, "proposedVersionId");
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("An evaluation dataset must contain cases");
            }
            if (!humanNecessityMap.procedureId().equals(procedure.procedureId())) {
                throw new IllegalArgumentException("The Human Necessity Map targets another procedure");
            }
            if (procedure.version(existingVersionId).isEmpty()
                    || procedure.version(proposedVersionId).isEmpty()) {
                throw new IllegalArgumentException("The selected procedure versions do not exist");
            }
        }
    }
}
