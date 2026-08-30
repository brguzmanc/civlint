# Third-party notices

CivLint - author Buddy Guzman (`bguzman`).

Every third-party component is used according to its own licence. This is not a claim of legal review.

The submitted source archive does **not** include `target/`, a dependency cache or copied third-party
JARs. Maven resolves dependencies from their publishers under the licenses of those projects.

## Direct project dependencies and build tools

| Component | Scope | Version source | Project license |
|---|---|---|---|
| Spring Boot starters: Web, Thymeleaf, Data JPA, Flyway, Actuator | runtime | Spring Boot 4.1.1 BOM | Apache-2.0 |
| H2 Database | runtime | pinned to 2.3.232 | MPL-2.0 or EPL-1.0 |
| Spring Modulith API | runtime | Spring Modulith 2.1.1 BOM | Apache-2.0 |
| Spring Boot Starter Test | test | Spring Boot 4.1.1 BOM | Apache-2.0 |
| Spring Modulith Core | test | Spring Modulith 2.1.1 BOM | Apache-2.0 |
| JaCoCo Maven Plugin | build/test | 0.8.15 | EPL-2.0 |
| Apache Maven Wrapper | build | Maven 3.9.14 distribution | Apache-2.0 |
| Maven Checkstyle Plugin | build | 3.6.0 | Apache-2.0 |
| Checkstyle | build | 14.0.0 | LGPL-2.1-or-later |
| SpotBugs Maven Plugin | build | 4.10.4.0 | Apache-2.0 |
| SpotBugs engine | build | 4.10.4, resolved by the Maven plugin | LGPL-2.1-or-later |

Transitive libraries include Spring Framework, Hibernate ORM, Flyway, Thymeleaf, Tomcat, Jackson,
JUnit, AssertJ, ArchUnit, Logback, Mockito, SLF4J and their dependencies. Licenses vary by component
and resolved version. This source-only notice is intentionally non-exhaustive; the authoritative
license and notice terms are those published and distributed by each component's publisher.

Their resolved versions are controlled by the two BOMs, with **one deliberate exception**:
embedded Apache Tomcat (Apache-2.0) is pinned to 11.0.25 by the `tomcat.version` property in
`pom.xml`, ahead of the version the Spring Boot 4.1.1 BOM would select, as a security patch override.
See `docs/security-and-data.md` for the reason and the verification command.

Resolved versions can be inspected without relying on this hand-maintained document:

```bash
./mvnw dependency:tree
./mvnw help:effective-pom
```

## Original work and excluded components

Application source, synthetic policy/procedure fixtures, evaluation cases, reports and documentation
were created for this project. Maven wrapper scripts and third-party dependencies are not claimed as
original work.

No credential, private information or third-party dataset is included.

If a compiled executable is redistributed, its distributor must preserve all notices
and license obligations applicable to every packaged transitive artifact.
