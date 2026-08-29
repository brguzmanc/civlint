/**
 * The three bounded agents, their typed contracts, and the ports through which models are reached.
 *
 * <p>Agents propose; they never decide. Nothing in this module can produce a release approval, and
 * every response crosses a validating boundary before any of it reaches the verifier.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Agents",
        allowedDependencies = {"domain", "support"})
package com.bguzman.civlint.agents;
