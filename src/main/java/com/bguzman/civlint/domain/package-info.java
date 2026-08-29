/**
 * Pure business concepts: procedures, policy, human necessity, findings and results.
 *
 * <p>Everything in this module is immutable, self-validating and free of framework coupling. There
 * are no Spring annotations, no persistence types, no network access and no model calls, which is
 * what allows the deterministic verifier to be exercised without a container.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Domain",
        allowedDependencies = "support")
package com.bguzman.civlint.domain;
