package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Json;
import java.util.Objects;

/**
 * What a {@link Finding} is about.
 *
 * <p>Subjects are a closed set of genuinely different shapes: a case-level finding names a case, a
 * structural finding names a pair of steps or a gate. Modelling that as a sealed hierarchy rather
 * than as a bag of nullable identifier fields means the renderer's {@code switch} is exhaustive and
 * a finding can never be built with the wrong combination of populated fields.
 */
public sealed interface FindingSubject {

    String key();

    Json toJson();

    /**
     * A finding about one evaluation case.
     *
     * @param caseId the case identifier
     */
    record OfCase(String caseId) implements FindingSubject {
        public OfCase {
            Objects.requireNonNull(caseId, "caseId");
        }

        @Override
        public String key() {
            return "CASE." + caseId;
        }

        @Override
        public Json toJson() {
            return Json.obj().put("subject", "CASE").put("caseId", caseId).build();
        }
    }

    /**
     * A finding about a single procedure step.
     *
     * @param stepId the step identifier
     */
    record OfStep(String stepId) implements FindingSubject {
        public OfStep {
            Objects.requireNonNull(stepId, "stepId");
        }

        @Override
        public String key() {
            return "STEP." + stepId;
        }

        @Override
        public Json toJson() {
            return Json.obj().put("subject", "STEP").put("stepId", stepId).build();
        }
    }

    /**
     * A finding about an ordered pair of steps, such as a separation-of-duty violation.
     *
     * @param firstStepId the first step in the pair
     * @param secondStepId the second step in the pair
     */
    record OfStepPair(String firstStepId, String secondStepId) implements FindingSubject {
        public OfStepPair {
            Objects.requireNonNull(firstStepId, "firstStepId");
            Objects.requireNonNull(secondStepId, "secondStepId");
        }

        @Override
        public String key() {
            return "STEPPAIR." + firstStepId + "." + secondStepId;
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("subject", "STEP_PAIR")
                    .put("firstStepId", firstStepId)
                    .put("secondStepId", secondStepId)
                    .build();
        }
    }

    /**
     * A finding about an approval gate.
     *
     * @param gateId the gate identifier
     */
    record OfGate(String gateId) implements FindingSubject {
        public OfGate {
            Objects.requireNonNull(gateId, "gateId");
        }

        @Override
        public String key() {
            return "GATE." + gateId;
        }

        @Override
        public Json toJson() {
            return Json.obj().put("subject", "GATE").put("gateId", gateId).build();
        }
    }

    /**
     * A finding about the procedure version as a whole.
     *
     * @param versionId the version identifier
     */
    record OfVersion(String versionId) implements FindingSubject {
        public OfVersion {
            Objects.requireNonNull(versionId, "versionId");
        }

        @Override
        public String key() {
            return "VERSION." + versionId;
        }

        @Override
        public Json toJson() {
            return Json.obj().put("subject", "VERSION").put("versionId", versionId).build();
        }
    }

    /**
     * A finding about the policy pack itself.
     *
     * @param packId the pack identifier
     */
    record OfPolicy(String packId) implements FindingSubject {
        public OfPolicy {
            Objects.requireNonNull(packId, "packId");
        }

        @Override
        public String key() {
            return "POLICY." + packId;
        }

        @Override
        public Json toJson() {
            return Json.obj().put("subject", "POLICY").put("packId", packId).build();
        }
    }
}
