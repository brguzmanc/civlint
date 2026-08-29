package com.bguzman.civlint.domain;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of checking one {@link RuleCriterion} against a case.
 *
 * <p>The distinction between {@link Unmet} and {@link Abstain} is central to CivLint's claim to be
 * honest about automation. {@code Unmet} means "the check ran and the condition fails". {@code
 * Abstain} means "the check could not be performed reliably" — and an abstention can never be
 * resolved downwards into automation. Collapsing the two would let a check that never ran be
 * reported as a check that passed.
 */
public sealed interface CriterionResult {

    boolean engaged();

    /**
     * The criterion holds; nothing to report.
     */
    record Met() implements CriterionResult {
        @Override
        public boolean engaged() {
            return false;
        }
    }

    /**
     * The criterion was checked and does not hold.
     *
     * @param code stable machine-readable explanation code
     * @param message human-readable explanation
     * @param references artifacts that justify the conclusion
     */
    record Unmet(String code, String message, List<EvidenceReference> references)
            implements CriterionResult {
        public Unmet {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            references = List.copyOf(Objects.requireNonNull(references, "references"));
        }

        @Override
        public boolean engaged() {
            return true;
        }
    }

    /**
     * The criterion could not be checked reliably, so the mechanical layer declines to conclude.
     *
     * @param code stable machine-readable explanation code
     * @param message human-readable explanation of the limitation
     * @param references artifacts the abstention relates to
     */
    record Abstain(String code, String message, List<EvidenceReference> references)
            implements CriterionResult {
        public Abstain {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            references = List.copyOf(Objects.requireNonNull(references, "references"));
        }

        @Override
        public boolean engaged() {
            return true;
        }
    }

    /** Shared instance for the satisfied outcome. */
    CriterionResult MET = new Met();

    static CriterionResult unmet(String code, String message, List<EvidenceReference> references) {
        return new Unmet(code, message, references);
    }

    static CriterionResult abstain(String code, String message, List<EvidenceReference> references) {
        return new Abstain(code, message, references);
    }
}
