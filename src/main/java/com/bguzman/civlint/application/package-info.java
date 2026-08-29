/**
 * Use cases, ports and orchestration.
 *
 * <p>This module owns the transaction boundary and the ports through which persistence and models are
 * reached. It contains no decision logic: every verdict comes from the {@code verification} module.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Application",
        allowedDependencies = {"domain", "policy", "procedure", "verification", "agents", "evaluation", "support"})
package com.bguzman.civlint.application;
