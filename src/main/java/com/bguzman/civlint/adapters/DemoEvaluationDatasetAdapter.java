package com.bguzman.civlint.adapters;

import com.bguzman.civlint.application.EvaluationDatasetPort;
import com.bguzman.civlint.evaluation.DemoCases;
import com.bguzman.civlint.evaluation.DemoHumanNecessity;
import com.bguzman.civlint.evaluation.DemoPolicy;
import com.bguzman.civlint.evaluation.DemoProcedures;
import org.springframework.stereotype.Component;

@Component
public final class DemoEvaluationDatasetAdapter implements EvaluationDatasetPort {

    private static final Dataset DATASET = new Dataset(
            DemoPolicy.pack(),
            DemoProcedures.procedure(),
            DemoHumanNecessity.map(),
            DemoCases.cases(),
            DemoProcedures.VERSION_EXISTING,
            DemoProcedures.VERSION_PROPOSED);

    @Override
    public Dataset load() {
        return DATASET;
    }
}
