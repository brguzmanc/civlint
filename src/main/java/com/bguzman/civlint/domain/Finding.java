package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Digest;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One conclusion reached by the deterministic verifier.
 *
 * <p><strong>Invariants enforced at construction:</strong>
 *
 * <ul>
 *   <li>At least one {@link EvidenceReference} is present. A finding with no evidence would be an
 *       assertion, and CivLint does not emit assertions.
 *   <li>{@code releaseBlocked} agrees with the decision tier: a finding at
 *       {@link DecisionTier#RELEASE_BLOCKED} blocks release and nothing else does.
 *   <li>A finding requiring a human names a human {@link ReviewerRole}.
 * </ul>
 *
 * <p>{@link #SORT_ORDER} defines the single ordering used everywhere findings are listed, so two
 * runs over the same input emit them in the same sequence.
 *
 * @param findingId stable identifier, deterministic for a given rule and subject
 * @param subject what the finding is about
 * @param severity severity of the finding
 * @param category the policy category engaged
 * @param decisionTier the tier that applies
 * @param ruleId identifier of the rule that produced the finding
 * @param explanation human-readable explanation
 * @param explanationCode stable machine-readable code
 * @param references artifacts justifying the finding, at least one
 * @param counterexample a minimal witness, where one applies
 * @param requiredReviewerRole the role that must act
 * @param releaseBlocked whether this finding prevents a release
 */
public record Finding(
        String findingId,
        FindingSubject subject,
        Severity severity,
        RuleCategory category,
        DecisionTier decisionTier,
        String ruleId,
        String explanation,
        String explanationCode,
        List<EvidenceReference> references,
        Optional<Counterexample> counterexample,
        ReviewerRole requiredReviewerRole,
        boolean releaseBlocked) {

    /** Maximum length of a finding identifier, matching the stable-identifier limit. */
    public static final int MAX_ID_LENGTH = 64;

    /**
     * The canonical ordering for findings: by identifier alone.
     *
     * <p>Identifiers already encode rule and subject, so a single-key comparator is both total and
     * stable. Sorting by severity first would reorder findings whenever a severity was retuned, which
     * would make canonical output churn for a cosmetic change.
     */
    public static final Comparator<Finding> SORT_ORDER = Comparator.comparing(Finding::findingId);

    public Finding {
        findingId = Identifiers.requireStable("findingId", findingId);
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(decisionTier, "decisionTier");
        ruleId = Identifiers.requireStable("ruleId", ruleId);
        explanation = Identifiers.requireText("explanation", explanation);
        explanationCode = Identifiers.requireStable("explanationCode", explanationCode);
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(counterexample, "counterexample");
        Objects.requireNonNull(requiredReviewerRole, "requiredReviewerRole");

        if (references.isEmpty()) {
            throw new IllegalArgumentException(
                    "Finding " + findingId + " must carry at least one evidence reference");
        }
        references = references.stream()
                .sorted(Comparator.comparing(EvidenceReference::kind).thenComparing(EvidenceReference::targetId))
                .distinct()
                .toList();

        if (releaseBlocked != (decisionTier == DecisionTier.RELEASE_BLOCKED)) {
            throw new IllegalArgumentException(
                    "Finding " + findingId + " has releaseBlocked=" + releaseBlocked
                            + " but tier " + decisionTier + "; the two must agree");
        }
        if (decisionTier.humanInvolved() && !requiredReviewerRole.human()) {
            throw new IllegalArgumentException(
                    "Finding " + findingId + " is at tier " + decisionTier + " but names no human role");
        }
    }

    /**
     * Builds a deterministic finding identifier from a rule and a subject.
     *
     * <p>The identifier is a pure function of its inputs, so the same problem detected in two runs
     * carries the same identifier and the two runs can be compared mechanically.
     *
     * @param ruleId the rule that produced the finding
     * @param subject what the finding is about
     * @return an identifier of the form {@code F.<rule>.<subject>}, sanitised to the identifier
     *     character set and, when it would exceed {@value #MAX_ID_LENGTH} characters, shortened by
     *     replacing the tail with a digest of the full identifier
     */
    public static String deterministicId(String ruleId, FindingSubject subject) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(subject, "subject");
        String raw = ("F." + ruleId + "." + subject.key()).replaceAll("[^A-Za-z0-9_.-]", "-");
        if (raw.length() <= MAX_ID_LENGTH) {
            return raw;
        }
        // A long rule identifier combined with a long subject key can exceed the identifier limit.
        // Truncating alone would risk collisions between two different long inputs sharing a prefix,
        // so the discarded tail is replaced by a digest of the whole raw identifier: still a pure
        // function of the inputs, still unique, and still within the limit.
        String digest = Digest.shorten(
                Digest.sha256Hex(raw));
        return raw.substring(0, MAX_ID_LENGTH - digest.length() - 1) + "." + digest;
    }

    public Json toJson() {
        Json.Builder builder = Json.obj()
                .put("findingId", findingId)
                .put("subject", subject.toJson())
                .put("severity", severity)
                .put("category", category)
                .put("decisionTier", decisionTier)
                .put("ruleId", ruleId)
                .put("explanation", explanation)
                .put("explanationCode", explanationCode)
                .put("references", Json.array(references.stream().map(EvidenceReference::toJson).toList()))
                .put("requiredReviewerRole", requiredReviewerRole)
                .put("releaseBlocked", releaseBlocked);
        builder.put(
                "counterexample",
                counterexample.map(Counterexample::toJson).orElse(Json.NULL));
        return builder.build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toJson());
    }
}
