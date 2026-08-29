/**
 * Procedure-graph analysis: version comparison, gate ordering and separation-of-duty checks.
 *
 * <p>Everything here is a pure function of the two procedure versions handed in. The module reports
 * differences and structural facts; deciding whether a difference is acceptable belongs to the
 * {@code verification} module, which owns the policy judgement.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Procedure",
        allowedDependencies = {"domain", "support"})
package com.bguzman.civlint.procedure;
