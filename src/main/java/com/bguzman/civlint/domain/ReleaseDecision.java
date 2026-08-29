package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.Json;
import java.util.List;
import java.util.Objects;

/**
 * Whether a proposed procedure version may ship, and on what basis.
 *
 * <p>The rationale is implementation-neutral on purpose. Both architectures build a decision through
 * this type, but only one of them has a deterministic verifier, so a rationale that credited the
 * verifier described the baseline inaccurately: it reports what was or was not produced, not what
 * produced it.
 *
 * <p><strong>Invariant:</strong> the outcome is a pure function of the findings. {@link #from(List)}
 * blocks whenever any finding is release-blocking and allows otherwise. No confidence score, agent
 * observation or override can produce {@link Outcome#ALLOW} while a blocking finding stands — which
 * is what makes the safety gate structural rather than advisory.
 *
 * @param outcome allow or block
 * @param blockingFindingIds identifiers of the findings that block, in ascending order
 * @param rationale human-readable statement of the basis for the decision
 */
public record ReleaseDecision(Outcome outcome, List<String> blockingFindingIds, String rationale) {

    /**
     * The two possible release outcomes.
     */
    public enum Outcome {
        /** No blocking finding stands; the change may ship. */
        ALLOW,
        /** At least one mandatory safeguard would be lost; the change must not ship. */
        BLOCK
    }

    public ReleaseDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(rationale, "rationale");
        blockingFindingIds = List.copyOf(Objects.requireNonNull(blockingFindingIds, "blockingFindingIds"))
                .stream()
                .sorted()
                .distinct()
                .toList();
        boolean blocks = outcome == Outcome.BLOCK;
        boolean hasBlockingFindings = !blockingFindingIds.isEmpty();
        if (blocks != hasBlockingFindings) {
            throw new IllegalArgumentException(
                    "Outcome " + outcome + " disagrees with " + blockingFindingIds.size()
                            + " blocking findings");
        }
    }

    public static ReleaseDecision from(List<Finding> findings) {
        Objects.requireNonNull(findings, "findings");
        List<String> blocking = findings.stream()
                .filter(Finding::releaseBlocked)
                .map(Finding::findingId)
                .sorted()
                .toList();
        if (blocking.isEmpty()) {
            return new ReleaseDecision(
                    Outcome.ALLOW,
                    List.of(),
                    "No release-blocking result was produced for the primary proposal.");
        }
        return new ReleaseDecision(
                Outcome.BLOCK,
                blocking,
                "Release is blocked by " + blocking.size()
                        + " finding(s) that would remove or weaken a mandatory safeguard.");
    }

    public boolean blocked() {
        return outcome == Outcome.BLOCK;
    }

    public Json toJson() {
        return Json.obj()
                .put("outcome", outcome)
                .put("blockingFindingIds", Json.strings(blockingFindingIds))
                .put("rationale", rationale)
                .build();
    }
}
