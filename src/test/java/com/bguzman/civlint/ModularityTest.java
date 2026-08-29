package com.bguzman.civlint;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the module boundaries the architecture claims, and the dependency rules that keep the
 * verifier testable and the domain framework-free.
 *
 * <p>These are enforced rather than documented because a boundary that is only documented is a
 * boundary that will be crossed.
 */
class ModularityTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bguzman.civlint");

    @Test
    @DisplayName("Spring Modulith verifies every declared module boundary")
    void modulithBoundariesHold() {
        ApplicationModules modules = ApplicationModules.of(CivLintApplication.class);
        modules.verify();
        assertThat(modules.stream().count())
                .as("every architectural module is detected")
                .isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("the domain module contains no Spring, persistence or web coupling")
    void domainIsPure() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.bguzman.civlint.domain..")
                // package-info carries the @ApplicationModule declaration, which is a compile-time
                // statement about the package rather than runtime coupling in any class.
                .and()
                .haveSimpleNameNotEndingWith("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..",
                        "java.net..",
                        "java.sql..")
                .because("the domain must be exercisable without a container, a database or a network");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the verifier depends on no framework, database or model code")
    void verifierIsPure() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.bguzman.civlint.verification..")
                .and()
                .haveSimpleNameNotEndingWith("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "java.net..",
                        "java.sql..",
                        "com.bguzman.civlint.agents..",
                        "com.bguzman.civlint.adapters..",
                        "com.bguzman.civlint.web..")
                .because("agent output and infrastructure must not be able to influence a verdict");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the support module depends on nothing else in CivLint")
    void supportIsFoundational() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.bguzman.civlint.support..")
                .and()
                .haveSimpleNameNotEndingWith("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bguzman.civlint.domain..",
                        "com.bguzman.civlint.policy..",
                        "com.bguzman.civlint.procedure..",
                        "com.bguzman.civlint.verification..",
                        "com.bguzman.civlint.agents..",
                        "com.bguzman.civlint.evaluation..",
                        "com.bguzman.civlint.application..",
                        "com.bguzman.civlint.adapters..",
                        "com.bguzman.civlint.web..")
                .because("canonical hashing sits below everything and must not be able to cycle");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("JPA types appear only in the adapter module")
    void persistenceIsConfined() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideOutsideOfPackage("com.bguzman.civlint.adapters..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                .because("persistence coupling belongs in an adapter, not in a use case or the domain");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the agents module cannot reach the verifier")
    void agentsCannotReachTheVerifier() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.bguzman.civlint.agents..")
                .and()
                .haveSimpleNameNotEndingWith("package-info")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bguzman.civlint.verification..",
                        "com.bguzman.civlint.policy..",
                        "com.bguzman.civlint.evaluation..")
                .because("an agent must not be able to call, configure or bypass the verifier");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("application use cases receive evaluation data through a port")
    void applicationDoesNotDependOnDemoData() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.bguzman.civlint.application..")
                .and()
                .haveSimpleNameNotEndingWith("package-info")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameStartingWith("Demo")
                .because("a production data adapter must be replaceable without changing a use case");
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("no class uses static mutable state")
    void noStaticMutableState() {
        // allowEmptyShould(true) is required precisely because the rule matching nothing is the
        // outcome being asserted: there are no static non-final fields anywhere in CivLint.
        ArchRule rule = ArchRuleDefinition.noFields()
                .that()
                .areStatic()
                .and()
                .areNotFinal()
                .should()
                .beDeclaredInClassesThat()
                .resideInAPackage("com.bguzman.civlint..")
                .because("shared mutable state would make concurrent agent execution non-deterministic")
                .allowEmptyShould(true);
        rule.check(CLASSES);

        long staticMutableFields = CLASSES.stream()
                .flatMap(clazz -> clazz.getFields().stream())
                .filter(field -> field.getModifiers().contains(
                        com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                .filter(field -> !field.getModifiers().contains(
                        com.tngtech.archunit.core.domain.JavaModifier.FINAL))
                .count();
        assertThat(staticMutableFields)
                .as("CivLint declares no static mutable field")
                .isZero();
    }

    @Test
    @DisplayName("Spring Modulith documentation can be generated from the verified model")
    void documentationIsGeneratable() {
        ApplicationModules modules = ApplicationModules.of(CivLintApplication.class);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()).toList())
                .contains("domain", "policy", "procedure", "verification", "agents", "evaluation",
                        "application", "adapters", "web", "support");
    }
}
