package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.List;
import java.util.Objects;

/**
 * One of the fixed evaluation cases, together with the locked oracle for it.
 *
 * <p>The oracle fields — {@link #oracleTier()}, {@link #oracleRequiredRole()} and
 * {@link #expectedExplanationCodes()} — are the authority a run is scored against. They are fixed
 * before either system runs and are never edited to accommodate a result; see
 * {@code docs/evaluation-methodology.md}. {@code baselineExpectation} and
 * {@code advancedExpectation} state, in advance, what each architecture is expected to do, so a
 * reader can see whether the comparison was set up to be winnable rather than fair.
 *
 * @param caseId stable identifier
 * @param title short human-readable title
 * @param description what the case exercises
 * @param scope whether the case evaluates a single request or a version comparison
 * @param request the synthetic input fixture
 * @param proposedVersionId the procedure version the case is evaluated against
 * @param oracleTier the correct decision tier
 * @param oracleRequiredRole the role that must act, or {@link ReviewerRole#NONE} when none must
 * @param expectedExplanationCodes explanation codes the verifier is expected to produce
 * @param baselineExpectation what the baseline architecture is expected to do
 * @param advancedExpectation what the advanced architecture is expected to do
 * @param explanation why the oracle is what it is
 */
public record EvaluationCase(
        String caseId,
        String title,
        String description,
        Scope scope,
        CorrectionRequest request,
        String proposedVersionId,
        DecisionTier oracleTier,
        ReviewerRole oracleRequiredRole,
        List<String> expectedExplanationCodes,
        String baselineExpectation,
        String advancedExpectation,
        String explanation) {

    /**
     * What a case evaluates.
     */
    public enum Scope {
        /** The case evaluates how one request is handled. */
        CASE_LEVEL,
        /** The case evaluates whether a proposed procedure version is safe to ship. */
        VERSION_COMPARISON
    }

    public EvaluationCase {
        caseId = Identifiers.requireStable("caseId", caseId);
        title = Identifiers.requireText("title", title);
        description = Identifiers.requireText("description", description);
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(request, "request");
        proposedVersionId = Identifiers.requireStable("proposedVersionId", proposedVersionId);
        Objects.requireNonNull(oracleTier, "oracleTier");
        Objects.requireNonNull(oracleRequiredRole, "oracleRequiredRole");
        Objects.requireNonNull(expectedExplanationCodes, "expectedExplanationCodes");
        expectedExplanationCodes = expectedExplanationCodes.stream().sorted().distinct().toList();
        baselineExpectation = Identifiers.requireText("baselineExpectation", baselineExpectation);
        advancedExpectation = Identifiers.requireText("advancedExpectation", advancedExpectation);
        explanation = Identifiers.requireText("explanation", explanation);

        if (!request.caseId().equals(caseId)) {
            throw new IllegalArgumentException(
                    "Case " + caseId + " carries a request for case " + request.caseId());
        }
        if (oracleTier.mandatoryHumanGate() && !oracleRequiredRole.human()) {
            throw new IllegalArgumentException(
                    "Case " + caseId + " has oracle tier " + oracleTier + " but names no human role");
        }
        if (oracleTier == DecisionTier.AUTOMATE && oracleRequiredRole != ReviewerRole.NONE) {
            throw new IllegalArgumentException(
                    "Case " + caseId + " expects AUTOMATE but names role " + oracleRequiredRole);
        }
    }

    public boolean requiresHumanGate() {
        return oracleTier.mandatoryHumanGate();
    }

    public Json toJson() {
        return Json.obj()
                .put("caseId", caseId)
                .put("title", title)
                .put("description", description)
                .put("scope", scope)
                .put("proposedVersionId", proposedVersionId)
                .put("oracleTier", oracleTier)
                .put("oracleRequiredRole", oracleRequiredRole)
                .put("expectedExplanationCodes", Json.strings(expectedExplanationCodes))
                .put("baselineExpectation", baselineExpectation)
                .put("advancedExpectation", advancedExpectation)
                .put("explanation", explanation)
                .put("request", request.toJson())
                .build();
    }
}
