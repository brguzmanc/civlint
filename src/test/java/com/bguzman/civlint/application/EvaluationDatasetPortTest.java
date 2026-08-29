package com.bguzman.civlint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.evaluation.DemoCases;
import com.bguzman.civlint.evaluation.DemoHumanNecessity;
import com.bguzman.civlint.evaluation.DemoPolicy;
import com.bguzman.civlint.evaluation.DemoProcedures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationDatasetPortTest {

    @Test
    void createsAnImmutableConsistentSnapshot() {
        ArrayList<EvaluationCase> cases =
                new ArrayList<>(DemoCases.cases());
        EvaluationDatasetPort.Dataset dataset = dataset(
                DemoHumanNecessity.map(), cases, DemoProcedures.VERSION_EXISTING);

        cases.clear();

        assertThat(dataset.cases()).hasSize(DemoCases.CASE_COUNT);
        assertThat(dataset.existingVersionId()).isEqualTo(DemoProcedures.VERSION_EXISTING);
    }

    @Test
    void rejectsAnEmptyCaseSet() {
        assertThatThrownBy(() -> dataset(
                        DemoHumanNecessity.map(), java.util.List.of(), DemoProcedures.VERSION_EXISTING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An evaluation dataset must contain cases");
    }

    @Test
    void rejectsAMapForAnotherProcedure() {
        HumanNecessityMap map = new HumanNecessityMap(
                "MAP.OTHER",
                "1",
                "PROC.OTHER",
                DemoHumanNecessity.map().entries());

        assertThatThrownBy(() -> dataset(map, DemoCases.cases(), DemoProcedures.VERSION_EXISTING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The Human Necessity Map targets another procedure");
    }

    @Test
    void rejectsAnUnknownSelectedVersion() {
        assertThatThrownBy(() -> dataset(DemoHumanNecessity.map(), DemoCases.cases(), "V.UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected procedure versions do not exist");
    }

    private static EvaluationDatasetPort.Dataset dataset(
            HumanNecessityMap map,
            List<EvaluationCase> cases,
            String existingVersionId) {
        return new EvaluationDatasetPort.Dataset(
                DemoPolicy.pack(),
                DemoProcedures.procedure(),
                map,
                cases,
                existingVersionId,
                DemoProcedures.VERSION_PROPOSED);
    }
}
