/**
 * Adapters for persistence, agent-model wiring and evaluation datasets.
 *
 * <p>Framework and infrastructure coupling is confined to this module. JPA types appear nowhere else,
 * so the domain and the verifier remain testable without a database or a Spring context.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Adapters",
        allowedDependencies = {"application", "domain", "agents", "evaluation", "support"})
package com.bguzman.civlint.adapters;
