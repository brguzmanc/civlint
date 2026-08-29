package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Json;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The condition a {@link PolicyRule} checks, expressed as data rather than as code.
 *
 * <p>Criteria are a closed set so that the evaluator can be an exhaustive {@code switch}: adding a
 * criterion without teaching the evaluator about it becomes a compile error rather than a silently
 * unchecked rule. Evaluation itself lives in the {@code policy} module; this type carries no
 * behaviour, which keeps the domain free of I/O and of any dependency on request handling.
 */
public sealed interface RuleCriterion {

    String summary();

    Json toJson();

    /**
     * Requires that at least one of the listed evidence types is present.
     *
     * @param anyOf acceptable evidence types; must not be empty
     */
    record EvidencePresent(Set<EvidenceType> anyOf) implements RuleCriterion {
        public EvidencePresent {
            Objects.requireNonNull(anyOf, "anyOf");
            if (anyOf.isEmpty()) {
                throw new IllegalArgumentException("EvidencePresent requires at least one type");
            }
            anyOf = Collections.unmodifiableSet(EnumSet.copyOf(anyOf));
        }

        @Override
        public String summary() {
            return "At least one of: " + names(anyOf);
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "EVIDENCE_PRESENT")
                    .put("anyOf", Json.strings(sortedNames(anyOf)))
                    .build();
        }
    }

    /**
     * Requires that every listed evidence type is present.
     *
     * @param allOf required evidence types; must not be empty
     */
    record EvidenceAllPresent(Set<EvidenceType> allOf) implements RuleCriterion {
        public EvidenceAllPresent {
            Objects.requireNonNull(allOf, "allOf");
            if (allOf.isEmpty()) {
                throw new IllegalArgumentException("EvidenceAllPresent requires at least one type");
            }
            allOf = Collections.unmodifiableSet(EnumSet.copyOf(allOf));
        }

        @Override
        public String summary() {
            return "All of: " + names(allOf);
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "EVIDENCE_ALL_PRESENT")
                    .put("allOf", Json.strings(sortedNames(allOf)))
                    .build();
        }
    }

    /**
     * Requires that every supplied item of evidence is legible and adequately certified.
     */
    record EvidenceUsable() implements RuleCriterion {
        @Override
        public String summary() {
            return "Every supplied item of evidence is legible and adequately certified";
        }

        @Override
        public Json toJson() {
            return Json.obj().put("criterion", "EVIDENCE_USABLE").build();
        }
    }

    /**
     * Requires that authoritative sources do not disagree about the listed fields.
     *
     * @param fields record fields to check; must not be empty
     */
    record NoAuthoritativeConflict(List<String> fields) implements RuleCriterion {
        public NoAuthoritativeConflict {
            Objects.requireNonNull(fields, "fields");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("NoAuthoritativeConflict requires at least one field");
            }
            fields = fields.stream().sorted().distinct().toList();
        }

        @Override
        public String summary() {
            return "Authoritative sources agree about: " + String.join(", ", fields);
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "NO_AUTHORITATIVE_CONFLICT")
                    .put("fields", Json.strings(fields))
                    .build();
        }
    }

    /**
     * Requires that any difference in the listed name fields is resolvable by the deterministic
     * normalisations in {@link Names}.
     *
     * @param fields name fields to compare; must not be empty
     */
    record NameChangeMechanicallyResolvable(List<String> fields) implements RuleCriterion {
        public NameChangeMechanicallyResolvable {
            Objects.requireNonNull(fields, "fields");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("NameChangeMechanicallyResolvable requires a field");
            }
            fields = fields.stream().sorted().distinct().toList();
        }

        @Override
        public String summary() {
            return "Differences in " + String.join(", ", fields) + " are formatting only";
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "NAME_CHANGE_MECHANICALLY_RESOLVABLE")
                    .put("fields", Json.strings(fields))
                    .build();
        }
    }

    /**
     * Requires that a regional identifier maps to a national identifier consistently across the
     * record and the evidence.
     *
     * @param sourceField the regional identifier field
     * @param targetField the national identifier field
     */
    record IdentifierMappingConsistent(String sourceField, String targetField)
            implements RuleCriterion {
        public IdentifierMappingConsistent {
            Objects.requireNonNull(sourceField, "sourceField");
            Objects.requireNonNull(targetField, "targetField");
        }

        @Override
        public String summary() {
            return sourceField + " maps consistently to " + targetField;
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "IDENTIFIER_MAPPING_CONSISTENT")
                    .put("sourceField", sourceField)
                    .put("targetField", targetField)
                    .build();
        }
    }

    /**
     * Requires that a closed historical entry is not edited in place.
     */
    record HistoricalRecordNotMutated() implements RuleCriterion {
        @Override
        public String summary() {
            return "A closed historical entry is referenced, not edited in place";
        }

        @Override
        public Json toJson() {
            return Json.obj().put("criterion", "HISTORICAL_RECORD_NOT_MUTATED").build();
        }
    }

    /**
     * Requires that a request characteristic is absent; the rule engages when the flag is set.
     *
     * <p>This is how policy expresses "an accessibility request must reach the accessibility
     * reviewer": the criterion is unmet precisely when the flag is present.
     *
     * @param flag the characteristic that engages the rule
     */
    record FlagAbsent(RequestFlag flag) implements RuleCriterion {
        public FlagAbsent {
            Objects.requireNonNull(flag, "flag");
        }

        @Override
        public String summary() {
            return "Request is not marked " + flag.name();
        }

        @Override
        public Json toJson() {
            return Json.obj().put("criterion", "FLAG_ABSENT").put("flag", flag).build();
        }
    }

    /**
     * Requires that a certified order of the given type accompanies the request whenever the
     * requested change is a substantive change of recorded content.
     *
     * @param orderType the type of certified order required
     */
    record CertifiedOrderRequiredForSubstantiveChange(EvidenceType orderType)
            implements RuleCriterion {
        public CertifiedOrderRequiredForSubstantiveChange {
            Objects.requireNonNull(orderType, "orderType");
        }

        @Override
        public String summary() {
            return "A substantive change is accompanied by a certified " + orderType.label();
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "CERTIFIED_ORDER_REQUIRED_FOR_SUBSTANTIVE_CHANGE")
                    .put("orderType", orderType)
                    .build();
        }
    }

    /**
     * A structural invariant about a procedure version rather than about a single request.
     *
     * <p>Structural rules live in the same policy pack as case rules so that a single approved,
     * hashable artifact governs both kinds of check. They are not case-scoped: the case evaluator
     * reports them satisfied, and the {@code verification} module dispatches on
     * {@link #invariant()} to run the corresponding graph check.
     *
     * @param invariant which invariant the rule asserts
     */
    record StructuralInvariant(Invariant invariant) implements RuleCriterion {

        /**
         * The closed set of structural invariants CivLint checks on a procedure version.
         */
        public enum Invariant {
            /** Every appeal route in the existing version survives in the proposed one. */
            APPEAL_ROUTE_PRESERVED,
            /** No role both prepares and approves the same decision. */
            SEPARATION_OF_DUTIES_HELD,
            /** Approval gates cannot be reached out of their declared order. */
            APPROVAL_ORDER_HELD,
            /** Every mandatory human gate the Human Necessity Map requires is present. */
            HUMAN_GATE_PRESERVED,
            /** Every declared step is reachable from the entry step. */
            ALL_STEPS_REACHABLE,
            /** A terminal step is reachable from every reachable step. */
            TERMINAL_REACHABLE,
            /** The step graph contains no cycle. */
            NO_CYCLES,
            /** No step declares a tier its policy category forbids. */
            TIER_PERMITTED_FOR_CATEGORY,
            /** The version's declared policy binding matches the pack in force. */
            POLICY_BINDING_CONSISTENT
        }

        public StructuralInvariant {
            Objects.requireNonNull(invariant, "invariant");
        }

        @Override
        public String summary() {
            return "Structural invariant: " + invariant.name();
        }

        @Override
        public Json toJson() {
            return Json.obj()
                    .put("criterion", "STRUCTURAL_INVARIANT")
                    .put("invariant", invariant)
                    .build();
        }
    }

    private static String names(Set<EvidenceType> types) {
        return String.join(", ", sortedNames(types));
    }

    private static List<String> sortedNames(Set<EvidenceType> types) {
        return types.stream().map(Enum::name).sorted().toList();
    }
}
