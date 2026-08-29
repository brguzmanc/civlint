package com.bguzman.civlint.agents;

/**
 * The port through which an agent reaches a model.
 *
 * <p>The interface is intentionally narrow: text in, text out. It cannot expose a tool, a file
 * handle, a database connection or a credential, so a model reached through it has no mechanism by
 * which to act on the system — whatever a policy document or case fixture may contain.
 *
 * <p>Implementations must be safe to call from multiple virtual threads at once.
 */
public interface AgentModelPort {

    String invoke(AgentRequest request);

    String adapterId();
}
