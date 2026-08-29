/**
 * The deterministic verifier: the authoritative pass/fail component.
 *
 * <p>Agents may propose; only this module concludes. Every function here is a pure function of its
 * arguments — no clock, no randomness, no network, no database, no model output — so that the same
 * inputs always yield the same findings, counterexamples and release decision.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Verification",
        allowedDependencies = {"domain", "policy", "procedure", "support"})
package com.bguzman.civlint.verification;
