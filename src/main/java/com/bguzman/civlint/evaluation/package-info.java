/**
 * The fixed evaluation harness: synthetic fixtures, the locked oracle, both runners and the metrics.
 *
 * <p>All data in this module is invented for demonstration. The Federated Civil Registry, its
 * regions, offices, roles and policy citations are fictional and describe no real jurisdiction, law
 * or person.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Evaluation",
        allowedDependencies = {"domain", "policy", "procedure", "verification", "agents", "support"})
package com.bguzman.civlint.evaluation;
