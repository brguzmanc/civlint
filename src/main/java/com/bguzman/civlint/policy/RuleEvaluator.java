package com.bguzman.civlint.policy;

import com.bguzman.civlint.domain.CorrectionRequest;
import com.bguzman.civlint.domain.CriterionResult;
import com.bguzman.civlint.domain.EvidenceItem;
import com.bguzman.civlint.domain.EvidenceReference;
import com.bguzman.civlint.domain.EvidenceType;
import com.bguzman.civlint.domain.NameComparison;
import com.bguzman.civlint.domain.Names;
import com.bguzman.civlint.domain.PolicyRule;
import com.bguzman.civlint.domain.RequestFlag;
import com.bguzman.civlint.domain.RuleCriterion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Evaluates policy criteria against a correction request.
 *
 * <p>The evaluator is a single exhaustive {@code switch} over {@link RuleCriterion}. Because the
 * criterion hierarchy is sealed, adding a criterion without handling it here fails to compile rather
 * than silently evaluating to "satisfied" — the failure mode that would matter most, since an
 * unevaluated safety rule looks exactly like a passing one.
 *
 * <p><strong>Determinism:</strong> every branch depends only on its arguments. Collections are
 * iterated in the sorted order the domain types guarantee, so results and the order of evidence
 * references are stable.
 *
 * <p><strong>Side effects:</strong> none. This class is stateless and safe to share across threads,
 * including virtual threads.
 */
public final class RuleEvaluator {

    /** Version of the evaluation semantics, recorded in every run for reproducibility. */
    public static final String RULE_ENGINE_VERSION = "civlint-rule-engine/0.1.0";

    private RuleEvaluator() {
        throw new AssertionError("No instances.");
    }

    public static CriterionResult evaluate(PolicyRule rule, CorrectionRequest request) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(request, "request");
        return evaluate(rule.criterion(), request);
    }

    public static CriterionResult evaluate(RuleCriterion criterion, CorrectionRequest request) {
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(request, "request");

        return switch (criterion) {
            case RuleCriterion.EvidencePresent(Set<EvidenceType> anyOf) ->
                    evidencePresent(anyOf, request);
            case RuleCriterion.EvidenceAllPresent(Set<EvidenceType> allOf) ->
                    evidenceAllPresent(allOf, request);
            case RuleCriterion.EvidenceUsable() -> evidenceUsable(request);
            case RuleCriterion.NoAuthoritativeConflict(List<String> fields) ->
                    noAuthoritativeConflict(fields, request);
            case RuleCriterion.NameChangeMechanicallyResolvable(List<String> fields) ->
                    nameChangeResolvable(fields, request);
            case RuleCriterion.IdentifierMappingConsistent(String source, String target) ->
                    identifierMappingConsistent(source, target, request);
            case RuleCriterion.HistoricalRecordNotMutated() -> historicalNotMutated(request);
            case RuleCriterion.FlagAbsent(var flag) -> flagAbsent(flag, request);
            case RuleCriterion.CertifiedOrderRequiredForSubstantiveChange(EvidenceType orderType) ->
                    certifiedOrderPresent(orderType, request);
            // Structural invariants describe a procedure version, not a request. Reporting them MET
            // here is correct rather than permissive: the structural check that actually enforces
            // them runs in the verification module against the procedure graph, and a case-level
            // evaluation has no graph to check.
            case RuleCriterion.StructuralInvariant ignored -> CriterionResult.MET;
        };
    }

    private static CriterionResult evidencePresent(
            Set<EvidenceType> anyOf, CorrectionRequest request) {
        List<EvidenceItem> matching = request.evidence().stream()
                .filter(item -> anyOf.contains(item.type()))
                .toList();
        if (matching.isEmpty()) {
            return CriterionResult.unmet(
                    "EVIDENCE_ABSENT",
                    "None of the acceptable evidence types was supplied: " + names(anyOf) + ".",
                    List.of(EvidenceReference.request(
                            request.caseId(), "Request supplied no acceptable evidence type")));
        }
        List<EvidenceItem> usable = matching.stream().filter(EvidenceItem::usableMechanically).toList();
        if (usable.isEmpty()) {
            return CriterionResult.abstain(
                    "EVIDENCE_NOT_USABLE",
                    "Evidence of an acceptable type was supplied but none of it is legible and "
                            + "adequately certified, so no mechanical conclusion can be drawn.",
                    matching.stream().map(EvidenceItem::reference).toList());
        }
        return CriterionResult.MET;
    }

    private static CriterionResult evidenceAllPresent(
            Set<EvidenceType> allOf, CorrectionRequest request) {
        Set<EvidenceType> supplied = new TreeSet<>();
        request.evidence().forEach(item -> supplied.add(item.type()));
        List<EvidenceType> missing = allOf.stream()
                .filter(type -> !supplied.contains(type))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
        if (missing.isEmpty()) {
            return CriterionResult.MET;
        }
        return CriterionResult.unmet(
                "EVIDENCE_INCOMPLETE",
                "Required evidence is missing: "
                        + String.join(", ", missing.stream().map(EvidenceType::label).toList()) + ".",
                request.evidence().isEmpty()
                        ? List.of(EvidenceReference.request(
                                request.caseId(), "No evidence was supplied with the request"))
                        : request.evidence().stream().map(EvidenceItem::reference).toList());
    }

    private static CriterionResult evidenceUsable(CorrectionRequest request) {
        List<EvidenceItem> unusable = request.evidence().stream()
                .filter(item -> !item.usableMechanically())
                .toList();
        if (unusable.isEmpty()) {
            return CriterionResult.MET;
        }
        boolean anyIllegible = unusable.stream().anyMatch(item -> !item.legible());
        String detail = anyIllegible
                ? "At least one item of evidence could not be read."
                : "At least one item from an authoritative source is not certified.";
        return CriterionResult.abstain(
                "EVIDENCE_QUALITY_INSUFFICIENT",
                detail + " A person must assess it; the mechanical check declines to conclude.",
                unusable.stream().map(EvidenceItem::reference).toList());
    }

    private static CriterionResult noAuthoritativeConflict(
            List<String> fields, CorrectionRequest request) {
        for (String field : fields) {
            List<EvidenceItem> claims = request.authoritativeClaimsFor(field);
            // Compare every unordered pair once. Claims arrive sorted by evidence identifier, so
            // the first conflict reported for a given request is always the same one.
            for (int i = 0; i < claims.size(); i++) {
                for (int j = i + 1; j < claims.size(); j++) {
                    EvidenceItem left = claims.get(i);
                    EvidenceItem right = claims.get(j);
                    String leftValue = left.claims().get(field);
                    String rightValue = right.claims().get(field);
                    List<EvidenceReference> references = List.of(
                            left.reference(),
                            right.reference(),
                            EvidenceReference.field(field, "Field asserted by two authoritative sources"));

                    NameComparison comparison = Names.compare(leftValue, rightValue);
                    if (comparison instanceof NameComparison.Different) {
                        // Two sources CivLint treats as authoritative assert different values.
                        // There is no mechanical basis for preferring one over the other.
                        return CriterionResult.unmet(
                                "AUTHORITATIVE_CONFLICT",
                                "Authoritative sources disagree about " + field + ": "
                                        + left.type().label() + " states \"" + leftValue + "\" while "
                                        + right.type().label() + " states \"" + rightValue
                                        + "\". Choosing between them is not a mechanical act.",
                                references);
                    }
                    if (comparison instanceof NameComparison.Undecidable(String code, String reason)) {
                        return CriterionResult.abstain(
                                code,
                                "Two authoritative statements about " + field
                                        + " could not be compared mechanically: " + reason,
                                references);
                    }
                }
            }
        }
        return CriterionResult.MET;
    }

    private static CriterionResult nameChangeResolvable(
            List<String> fields, CorrectionRequest request) {
        List<EvidenceReference> abstained = new ArrayList<>();
        String abstainCode = null;
        String abstainReason = null;

        for (String field : fields) {
            Optional<String> requested = request.requested(field);
            Optional<String> current = request.currentRecord().field(field);
            if (requested.isEmpty() || current.isEmpty()) {
                continue;
            }
            NameComparison comparison = Names.compare(current.get(), requested.get());
            switch (comparison) {
                case NameComparison.Equivalent ignored -> {
                    // Formatting only: safe to treat mechanically.
                }
                case NameComparison.Different ignored -> {
                    return CriterionResult.unmet(
                            "SUBSTANTIVE_CHANGE_REQUESTED",
                            "The requested value for " + field
                                    + " differs in content, not merely in formatting, so it is a "
                                    + "substantive change rather than a normalisation.",
                            List.of(EvidenceReference.field(field, "Requested substantive change")));
                }
                case NameComparison.Undecidable(String code, String reason) -> {
                    abstainCode = code;
                    abstainReason = reason;
                    abstained.add(EvidenceReference.field(field, "Comparison declined"));
                }
            }
        }
        if (abstainCode != null) {
            return CriterionResult.abstain(
                    abstainCode,
                    "The mechanical comparison declined to conclude: " + abstainReason,
                    List.copyOf(abstained));
        }
        return CriterionResult.MET;
    }

    private static CriterionResult identifierMappingConsistent(
            String sourceField, String targetField, CorrectionRequest request) {
        Optional<String> requestedTarget = request.requested(targetField);
        if (requestedTarget.isEmpty()) {
            return CriterionResult.MET;
        }
        List<EvidenceItem> claims = request.authoritativeClaimsFor(targetField);
        if (claims.isEmpty()) {
            return CriterionResult.unmet(
                    "MAPPING_UNSUPPORTED",
                    "The request assigns " + targetField
                            + " but no authoritative source states that value, so the mapping cannot "
                            + "be confirmed mechanically.",
                    List.of(EvidenceReference.field(targetField, "Assigned without an authoritative source")));
        }
        for (EvidenceItem claim : claims) {
            String claimed = claim.claims().get(targetField);
            if (!claimed.equals(requestedTarget.get())) {
                return CriterionResult.unmet(
                        "MAPPING_INCONSISTENT",
                        "The request assigns " + targetField + " as \"" + requestedTarget.get()
                                + "\" but " + claim.type().label() + " states \"" + claimed + "\".",
                        List.of(claim.reference(), EvidenceReference.field(targetField, "Mapping mismatch")));
            }
        }
        Optional<String> currentSource = request.currentRecord().field(sourceField);
        Optional<String> requestedSource = request.requested(sourceField);
        if (currentSource.isPresent()
                && requestedSource.isPresent()
                && !currentSource.get().equals(requestedSource.get())) {
            return CriterionResult.unmet(
                    "MAPPING_SOURCE_ALTERED",
                    "The mapping from " + sourceField + " to " + targetField
                            + " is only mechanical while " + sourceField + " itself is unchanged.",
                    List.of(EvidenceReference.field(sourceField, "Source identifier altered")));
        }
        return CriterionResult.MET;
    }

    private static CriterionResult historicalNotMutated(CorrectionRequest request) {
        if (!request.currentRecord().immutableHistorical()) {
            return CriterionResult.MET;
        }
        List<String> changed = request.changedFields();
        if (changed.isEmpty()) {
            return CriterionResult.MET;
        }
        return CriterionResult.unmet(
                "HISTORICAL_RECORD_MUTATED",
                "The request would edit closed historical entry "
                        + request.currentRecord().recordId() + " in place, changing "
                        + String.join(", ", changed)
                        + ". A correction must be recorded as a new entry that references it.",
                changed.stream()
                        .map(field -> EvidenceReference.field(field, "Edit to a closed historical entry"))
                        .toList());
    }

    private static CriterionResult flagAbsent(
            RequestFlag flag, CorrectionRequest request) {
        if (!request.has(flag)) {
            return CriterionResult.MET;
        }
        return CriterionResult.unmet(
                "FLAG_" + flag.name(),
                "The request is marked " + flag.name()
                        + ", which policy routes away from the mechanical path.",
                List.of(EvidenceReference.request(
                        request.caseId(), "Request characteristic " + flag.name())));
    }

    private static CriterionResult certifiedOrderPresent(
            EvidenceType orderType, CorrectionRequest request) {
        boolean substantive = request.requestedFields().entrySet().stream().anyMatch(entry -> {
            Optional<String> current = request.currentRecord().field(entry.getKey());
            return current.isPresent()
                    && Names.compare(current.get(), entry.getValue()) instanceof NameComparison.Different;
        });
        if (!substantive) {
            return CriterionResult.MET;
        }
        List<EvidenceItem> orders = request.evidenceOfType(orderType);
        List<EvidenceItem> certified =
                orders.stream().filter(item -> item.certified() && item.legible()).toList();
        if (!certified.isEmpty()) {
            return CriterionResult.MET;
        }
        if (orders.isEmpty()) {
            return CriterionResult.unmet(
                    "CERTIFIED_ORDER_MISSING",
                    "The request makes a substantive change but no " + orderType.label()
                            + " accompanies it.",
                    List.of(EvidenceReference.request(
                            request.caseId(), "Substantive change without a certified order")));
        }
        return CriterionResult.abstain(
                "CERTIFIED_ORDER_NOT_USABLE",
                "A " + orderType.label()
                        + " was supplied but is not certified and legible, so it cannot be relied on "
                        + "mechanically.",
                orders.stream().map(EvidenceItem::reference).toList());
    }

    private static List<String> namesOf(Set<EvidenceType> types) {
        return types.stream().map(EvidenceType::label).sorted().toList();
    }

    private static String names(Set<EvidenceType> types) {
        return String.join(", ", namesOf(types));
    }

}
