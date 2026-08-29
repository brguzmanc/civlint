# Security and data boundaries

Author: Buddy Guzman (`bguzman`).

CivLint has not received a legal, security, accessibility or production audit. This document states
what this build actually enforces and what a future deployment would still require.

## Data boundary

DEMO mode contains synthetic fictional data only. No real person, government body, office, policy
citation or law appears in code, fixtures, traces, reports, logs or database rows.

This is a **temporal safety boundary**, not a claim that the product can never support real data.
Moving beyond it requires a named jurisdiction, approved data owner, lawful basis and implemented
controls. This repository must not be used with real data as-is.

## Consequential actions and human review

CivLint evaluates proposed workflows; it does not execute a government action. It cannot update a
registry, approve a citizen request or remove a production checkpoint.

The domain rejects full automation for consequential decisions, approvals and appeals. Ambiguity,
conflicting authority, accessibility exceptions, rights-impacting outcomes, appeals and failed duty
separation route to a named qualified role. Only a proven mechanical check can use `AUTOMATE`.

## Untrusted agent text

The model boundary exposes only `String invoke(AgentRequest)`. It provides no shell, file, database,
network callback or credential field.

- `JsonReader` bounds input length, nesting and value count; it rejects duplicate names, comments,
  invalid numbers and trailing content.
- `AgentContract` rejects unknown fields, identity/version mismatches and unauthorized release tiers.
- Invalid output is retried once and then discarded without observations.
- Raw model output is never written to a trace; only length and validation events are recorded.
- The verifier cannot import agent code, enforced by ArchUnit.

Validated observations — and only validated ones — are rendered on the dashboard as advisory context:
agent identifier, subject, proposed tier, category, confidence, rationale and cited evidence. Every
such field is emitted through Thymeleaf `th:text`, which HTML-escapes it, so agent text reaches the
page as data and never as markup. No template uses `th:utext`.

`JsonReaderTest.instructionTextIsInert` confirms that instruction-like text remains inert data,
`AgentIndependenceTest` confirms hostile output cannot alter the advanced verdict, and
`CivLintApiIT.dashboardRenders` asserts on the real rendered page that a rationale's apostrophe
appears escaped — a guard that fails if any field is switched to unescaped output.

## Local exposure

| Control | DEMO behavior |
|---|---|
| Database console | disabled |
| Actuator | health only, without details |
| Schema changes | Flyway only; Hibernate validates |
| Open EntityManager during views | disabled |
| Error bodies | no stack traces, binding details or rejected document echo |
| Administrative endpoints | `/env`, `/beans`, `/configprops`, `/mappings`, `/heapdump` tested closed |

## Future interoperability and real data

`EvaluationDatasetPort` permits a jurisdiction-specific adapter later, but the adapter is not the
security boundary. Before PILOT data is loaded, a deployment needs:

1. approved pseudonymization before CivLint receives the data;
2. authentication, role-based authorization and audit logging;
3. TLS and encryption at rest;
4. data minimization, retention and deletion rules;
5. isolated environments and secret management;
6. rate limiting and protection for state-changing endpoints;
7. jurisdiction-specific policy and privacy approval; and
8. independent domain, legal, security and accessibility review.

The current canonical hash is not anonymization. Hashes of low-entropy personal fields may be guessed;
a real deployment needs upstream pseudonymization or keyed hashing under an approved design.

## Dependency patch level

Embedded Apache Tomcat is the one dependency whose version this project overrides rather than
inheriting. The Spring Boot 4.1.1 BOM selects 11.0.24, which Apache lists as affected by
vulnerabilities fixed in 11.0.25, so `pom.xml` sets `tomcat.version` to **11.0.25**. Every other
version stays BOM-managed, because pinning without a stated reason removes the BOM's benefit.

Verify the packaged artifact rather than trusting this sentence:

```bash
./mvnw -q clean package -DskipTests && unzip -l target/civlint-0.1.0.jar | grep tomcat-embed
```

Expected: `tomcat-embed-core`, `tomcat-embed-el` and `tomcat-embed-websocket` at 11.0.25, and no
11.0.24 entry.

## Known gaps

- No authentication, authorization, CSRF protection or rate limiting.
- Plain local HTTP; no deployment TLS configuration.
- **No software-composition analysis in the build, and the one scan that was run is inconclusive.**
  A query of the OSV.dev public database covering all 93 resolved compile/runtime artifacts returned
  zero advisories on 28 August 2026. That result must not be read as "no vulnerabilities": a control
  query proves the database and the query shape work (`spring-web:5.3.0` returns 7 advisories,
  `tomcat-embed-core:11.0.20` returns 9), yet `tomcat-embed-core:11.0.24` returns **zero** — the
  advisory that motivated the Tomcat override above had not been ingested. GitHub Dependabot draws on
  the same advisory database, so it would not have surfaced this either. The override rests on
  Apache's own advisory, not on a scanner. A real SCA run remains outstanding.
- No independent verification of every transitive dependency's embedded license file.
- No production observability, backup, recovery or incident-response configuration.

These gaps are acceptable only for a local synthetic demonstration and are not production claims.
