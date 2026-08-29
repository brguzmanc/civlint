package com.bguzman.civlint.agents;

import com.bguzman.civlint.support.Identifiers;
import java.util.List;
import java.util.Objects;

/**
 * What an agent is asked to do, and the exact inputs it is permitted to see.
 *
 * <p>The request carries no secrets and no credentials by construction: there is no field in which
 * one could be placed. The {@code payload} is the canonical JSON of synthetic case and procedure
 * data only.
 *
 * @param agentId identifier of the agent being invoked
 * @param agentVersion version of the agent's contract and prompt
 * @param promptKey stable key naming the prompt or fixture to use. Constrained to the stable-identifier
 *     character set because {@link ReplayAgentAdapter} concatenates it into a classpath resource
 *     path, and a future dataset adapter may source case identifiers from an external system
 * @param inputHash canonical hash of the payload
 * @param policyHash canonical hash of the policy pack in force
 * @param procedureVersionIds procedure versions in scope, in ascending order
 * @param payload canonical JSON of the input the agent may read
 */
public record AgentRequest(
        String agentId,
        String agentVersion,
        String promptKey,
        String inputHash,
        String policyHash,
        List<String> procedureVersionIds,
        String payload) {

    public AgentRequest {
        agentId = Identifiers.requireStable("agentId", agentId);
        agentVersion = Identifiers.requireText("agentVersion", agentVersion);
        promptKey = Identifiers.requireStable("promptKey", promptKey);
        inputHash = Objects.requireNonNull(inputHash, "inputHash");
        policyHash = Objects.requireNonNull(policyHash, "policyHash");
        procedureVersionIds =
                Objects.requireNonNull(procedureVersionIds, "procedureVersionIds").stream()
                        .sorted()
                        .distinct()
                        .toList();
        payload = Objects.requireNonNull(payload, "payload");
    }
}
