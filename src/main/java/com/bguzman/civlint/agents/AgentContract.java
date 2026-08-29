package com.bguzman.civlint.agents;

import com.bguzman.civlint.domain.AgentObservation;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvidenceReference;
import com.bguzman.civlint.domain.FindingSubject;
import com.bguzman.civlint.domain.RuleCategory;
import com.bguzman.civlint.support.Json;
import com.bguzman.civlint.support.JsonParseException;
import com.bguzman.civlint.support.JsonPath;
import com.bguzman.civlint.support.JsonReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates raw agent output against the typed agent contract.
 *
 * <p>Nothing reaches the verifier without passing through here, and the checks are deliberately more
 * restrictive than the JSON grammar:
 *
 * <ul>
 *   <li><strong>An agent may not propose {@link DecisionTier#RELEASE_BLOCKED}.</strong> Release
 *       authority belongs to the deterministic verifier. An agent that claims it is rejected outright
 *       rather than having its claim quietly downgraded, because the attempt itself is a contract
 *       breach worth seeing in a trace.
 *   <li><strong>Unknown members are rejected</strong>, at the top level and per observation. A field
 *       CivLint does not understand is not ignored, because ignoring it is how an agent's extra
 *       instruction ends up unexamined.
 *   <li><strong>Counts and sizes are bounded</strong>, so a response cannot enlarge a run's cost.
 *   <li><strong>The declared agent identity must match the request</strong>, so a fixture cannot be
 *       served for the wrong agent.
 * </ul>
 */
public final class AgentContract {

    /** Maximum observations one agent may return for one request. */
    public static final int MAX_OBSERVATIONS = 50;

    /** Maximum length of a rationale, in characters. */
    public static final int MAX_RATIONALE_LENGTH = 2_000;

    /** Maximum references one observation may cite. */
    public static final int MAX_REFERENCES = 20;

    private static final List<String> TOP_LEVEL_MEMBERS =
            List.of("agentId", "agentVersion", "observations");

    private static final List<String> OBSERVATION_MEMBERS = List.of(
            "observationId", "subject", "proposedTier", "category", "rationale", "confidence",
            "references");

    private static final List<String> SUBJECT_MEMBERS = List.of("type", "id", "secondId");

    private static final List<String> REFERENCE_MEMBERS = List.of("kind", "targetId", "description");

    private AgentContract() {
        throw new AssertionError("No instances.");
    }

    public static List<AgentObservation> validate(AgentRequest request, String rawResponse) {
        return validate(request, rawResponse, false);
    }

    public static List<AgentObservation> validate(
            AgentRequest request, String rawResponse, boolean allowReleaseBlocked) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(rawResponse, "rawResponse");

        Json root = JsonReader.read(rawResponse);
        rejectUnknownMembers(root, "response", TOP_LEVEL_MEMBERS);

        String declaredAgentId = JsonPath.string(root, "response", "agentId");
        if (!declaredAgentId.equals(request.agentId())) {
            throw new JsonParseException(
                    "Response declares agentId \"" + declaredAgentId + "\" but was requested from \""
                            + request.agentId() + "\"");
        }
        String declaredVersion = JsonPath.string(root, "response", "agentVersion");
        if (!declaredVersion.equals(request.agentVersion())) {
            throw new JsonParseException(
                    "Response declares agentVersion \"" + declaredVersion + "\" but the request used \""
                            + request.agentVersion() + "\"");
        }

        List<Json> rawObservations = JsonPath.array(root, "response", "observations");
        if (rawObservations.size() > MAX_OBSERVATIONS) {
            throw new JsonParseException(
                    "Response contains " + rawObservations.size() + " observations, exceeding the limit of "
                            + MAX_OBSERVATIONS);
        }

        List<AgentObservation> accepted = new ArrayList<>(rawObservations.size());
        for (int i = 0; i < rawObservations.size(); i++) {
            accepted.add(readObservation(
                    request, rawObservations.get(i), "observations[" + i + "]", allowReleaseBlocked));
        }
        return accepted.stream().sorted(AgentObservation.SORT_ORDER).toList();
    }

    private static AgentObservation readObservation(
            AgentRequest request, Json value, String path, boolean allowReleaseBlocked) {
        rejectUnknownMembers(value, path, OBSERVATION_MEMBERS);

        String observationId = JsonPath.string(value, path, "observationId");
        DecisionTier tier = JsonPath.enumeration(value, path, "proposedTier", DecisionTier.class);
        if (tier == DecisionTier.RELEASE_BLOCKED && !allowReleaseBlocked) {
            throw new JsonParseException(
                    path + " proposes RELEASE_BLOCKED. Under this architecture release authority "
                            + "belongs to the deterministic verifier, so an agent may not propose it.");
        }
        RuleCategory category = JsonPath.enumeration(value, path, "category", RuleCategory.class);
        String rationale = JsonPath.string(value, path, "rationale");
        if (rationale.length() > MAX_RATIONALE_LENGTH) {
            throw new JsonParseException(
                    path + ".rationale is " + rationale.length() + " characters, exceeding the limit of "
                            + MAX_RATIONALE_LENGTH);
        }
        int confidence = JsonPath.integer(value, path, "confidence");

        FindingSubject subject = readSubject(JsonPath.member(value, path, "subject"), path + ".subject");

        List<Json> rawReferences = JsonPath.array(value, path, "references");
        if (rawReferences.size() > MAX_REFERENCES) {
            throw new JsonParseException(
                    path + ".references contains " + rawReferences.size() + " entries, exceeding the limit of "
                            + MAX_REFERENCES);
        }
        List<EvidenceReference> references = new ArrayList<>(rawReferences.size());
        for (int i = 0; i < rawReferences.size(); i++) {
            String refPath = path + ".references[" + i + "]";
            Json reference = rawReferences.get(i);
            rejectUnknownMembers(reference, refPath, REFERENCE_MEMBERS);
            references.add(new EvidenceReference(
                    JsonPath.enumeration(reference, refPath, "kind", EvidenceReference.Kind.class),
                    JsonPath.string(reference, refPath, "targetId"),
                    JsonPath.string(reference, refPath, "description")));
        }

        try {
            return new AgentObservation(
                    observationId,
                    request.agentId(),
                    subject,
                    tier,
                    category,
                    rationale,
                    confidence,
                    references);
        } catch (IllegalArgumentException e) {
            // Domain validation is part of the contract: an observation that cannot be constructed
            // is a contract breach, reported with the same failure type as a malformed field.
            throw new JsonParseException(path + " violates the domain contract: " + e.getMessage());
        }
    }

    private static FindingSubject readSubject(Json value, String path) {
        rejectUnknownMembers(value, path, SUBJECT_MEMBERS);
        String type = JsonPath.string(value, path, "type");
        String id = JsonPath.string(value, path, "id");
        return switch (type) {
            case "CASE" -> new FindingSubject.OfCase(id);
            case "STEP" -> new FindingSubject.OfStep(id);
            case "GATE" -> new FindingSubject.OfGate(id);
            case "VERSION" -> new FindingSubject.OfVersion(id);
            case "POLICY" -> new FindingSubject.OfPolicy(id);
            case "STEP_PAIR" -> new FindingSubject.OfStepPair(
                    id, JsonPath.string(value, path, "secondId"));
            default -> throw new JsonParseException(
                    path + ".type must be one of CASE, STEP, GATE, VERSION, POLICY, STEP_PAIR, but was \""
                            + type + "\"");
        };
    }

    private static void rejectUnknownMembers(Json value, String path, List<String> permitted) {
        if (!(value instanceof Json.Obj(var members))) {
            throw new JsonParseException(path + " must be an object");
        }
        for (String name : members.keySet()) {
            if (!permitted.contains(name)) {
                throw new JsonParseException(
                        path + " contains unknown member \"" + name
                                + "\"; CivLint rejects fields it does not understand rather than "
                                + "ignoring them. Permitted: " + permitted);
            }
        }
    }
}
