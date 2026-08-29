package com.bguzman.civlint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for CivLint.
 *
 * <p>CivLint compares an existing and a proposed version of a public procedure against an approved
 * policy model and a human-necessity map, then reports which steps may be automated and which must
 * remain under human control. Startup performs no network access and no schema creation beyond the
 * Flyway migrations bundled in the artifact.
 *
 * <p><strong>Invariant:</strong> this class contributes no business logic. All decision authority
 * lives in the {@code verification} module so that it stays testable without a Spring context.
 */
@SpringBootApplication
public class CivLintApplication {

    /** Not instantiable outside Spring's bootstrap. */
    CivLintApplication() {
        // Package-private: Spring instantiates the configuration class reflectively.
    }

    public static void main(String[] args) {
        SpringApplication.run(CivLintApplication.class, args);
    }
}
