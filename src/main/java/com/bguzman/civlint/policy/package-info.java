/**
 * Policy-pack loading, validation and rule evaluation.
 *
 * <p>Evaluation is a pure function from a {@code RuleCriterion} and a {@code CorrectionRequest} to a
 * {@code CriterionResult}. Nothing here reads the clock, generates randomness, opens a socket or
 * calls a model, which is what allows the verifier built on top of it to be deterministic.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Policy",
        allowedDependencies = {"domain", "support"})
package com.bguzman.civlint.policy;
