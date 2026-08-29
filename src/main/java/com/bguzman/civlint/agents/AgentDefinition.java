package com.bguzman.civlint.agents;

import com.bguzman.civlint.support.Identifiers;
import java.util.List;
import java.util.Objects;

/**
 * The identity and remit of one bounded agent.
 *
 * <p>{@code remit} is documentation, but {@code mayProposeAutomation} is enforced: an agent whose
 * remit is to find hard cases has no business proposing that a case be automated, and the
 * orchestrator drops such an observation rather than passing it on. Bounding what each agent may say
 * is what makes three specialised agents different from three copies of one agent.
 *
 * @param agentId stable identifier
 * @param agentVersion version of the contract and prompt
 * @param displayName human-readable name
 * @param remit what this agent is for
 * @param mayProposeAutomation whether this agent may propose that a step or case be automated
 * @param mayBlockRelease whether this agent may claim release authority. True only for the baseline
 *     generalist, which has no deterministic verifier behind it and would otherwise be unable to
 *     express a refusal at all
 */
public record AgentDefinition(
        String agentId,
        String agentVersion,
        String displayName,
        String remit,
        boolean mayProposeAutomation,
        boolean mayBlockRelease) {

    /** The agent that extracts candidate deterministic rules and proposes map entries. */
    public static final AgentDefinition RULE_MAPPER = new AgentDefinition(
            "AGENT.RULEMAPPER",
            "0.1.0",
            "Rule Mapper",
            "Reads a synthetic procedure and policy pack, extracts candidate deterministic rules, "
                    + "identifies policy references and proposes Human Necessity Map entries. It never "
                    + "approves its own suggestions: proposals are recorded as observations and are "
                    + "checked by the deterministic verifier before they carry any weight.",
            true,
            false);

    /** The agent that surfaces difficult cases. */
    public static final AgentDefinition BOUNDARY_CASE = new AgentDefinition(
            "AGENT.BOUNDARY",
            "0.1.0",
            "Boundary Case Agent",
            "Selects and characterises difficult cases, concentrating on ambiguity, conflicting "
                    + "evidence, accessibility, appeals and authority. It never alters the authoritative "
                    + "oracle and never proposes automation, because its remit is to find the places "
                    + "where automation would be unsafe.",
            false,
            false);

    /** The agent that proposes the smallest safe repair for a finding. */
    public static final AgentDefinition REPAIR_ADVISOR = new AgentDefinition(
            "AGENT.REPAIR",
            "0.1.0",
            "Repair Advisor",
            "Explains findings and proposes the smallest repair that preserves human gates, appeal "
                    + "rights and separation of duties. It never modifies the policy and never marks a "
                    + "release safe.",
            false,
            false);

    /** The single general-purpose agent used by the baseline architecture. */
    public static final AgentDefinition BASELINE_GENERALIST = new AgentDefinition(
            "AGENT.GENERALIST",
            "0.1.0",
            "Baseline generalist",
            "One general-purpose agent asked to judge every case and every proposed change on its own, "
                    + "with no specialised remit, no typed decomposition, no Human Necessity Map and no "
                    + "deterministic verifier behind it.",
            true,
            true);

    public AgentDefinition {
        agentId = Identifiers.requireStable("agentId", agentId);
        agentVersion = Identifiers.requireText("agentVersion", agentVersion);
        displayName = Identifiers.requireText("displayName", displayName);
        remit = Identifiers.requireText("remit", remit);
        Objects.requireNonNull(agentId);
    }

    public static List<AgentDefinition> specialised() {
        return List.of(RULE_MAPPER, BOUNDARY_CASE, REPAIR_ADVISOR);
    }
}
