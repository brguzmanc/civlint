# Architecture

Author: Buddy Guzman (`bguzman`).

## Structure

CivLint is a **modular monolith with hexagonal boundaries** in one Maven artifact. This is the
smallest architecture that enforces the important rule: advisory agents and infrastructure cannot
acquire decision authority.

Package boundaries are declared with Spring Modulith and checked by `ModularityTest`, so a module can
be extracted later if an independently scalable or separately owned capability appears.

## Modules

| Module | Responsibility | May depend on |
|---|---|---|
| `support` | Canonical JSON, SHA-256, strict JSON reading, identifiers | none inside CivLint |
| `domain` | Immutable procedures, policy, findings and results | `support` |
| `policy` | Pure rule evaluation | `domain`, `support` |
| `procedure` | Graph and version analysis | `domain`, `support` |
| `verification` | Authoritative deterministic decisions | `domain`, `policy`, `procedure`, `support` |
| `agents` | Agent instructions, contracts, model port and orchestration | `domain`, `support` |
| `evaluation` | Synthetic data, fixed oracle, runners and metrics | domain logic plus `agents` |
| `application` | Use cases and ports | domain logic plus `evaluation` |
| `adapters` | Synthetic dataset, replay model and JPA implementations | application ports and their data |
| `web` | Canonical REST API and server-rendered dashboard | `application` and response types |

The build enforces these properties:

1. `verification` cannot reference `agents`, Spring, JPA, network or SQL APIs.
2. `agents` cannot reference `verification`, `policy` or `evaluation`.
3. JPA types exist only in `adapters`.
4. Application use cases cannot reference `Demo*` data factories.
5. No class declares static mutable state.

## Decision flow

1. The web adapter requests a baseline or advanced evaluation.
2. `CivLintService` loads one consistent snapshot through `EvaluationDatasetPort`.
3. Bounded agents run only on cases covered by their remits. Their validated output becomes traces.
4. `CaseVerifier` and `StructuralVerifier` evaluate policy, procedures and the Human Necessity Map.
5. `Metrics` compares the outcome with the locked oracle.
6. The primary proposal's findings alone determine its release decision. Deliberately unsafe
   evaluation variants remain visible as scenario findings but cannot falsely block the primary.
7. `ReplayVerification` compares two raw runs, fails if their canonical hashes differ, then appends
   exactly one `M.REPLAY.AGREEMENT` metric. Every publishing surface calls it, so one evaluation has
   one hash.
8. A POST stores the result through `EvaluationRunRepository`; dashboard GET requests use the same
   operation as a read-only preview and cause no persistence side effect.

There is deliberately no path from an `AgentObservation` to a verifier decision.
`AgentIndependenceTest` confirms that hostile, unavailable and replayed agents produce identical
advanced findings, case decisions and primary release outcomes.

## Human and mechanical decisions

Four tiers make the boundary explicit:

| Tier | Meaning |
|---|---|
| `AUTOMATE` | A proven mechanical check; no person acts |
| `AUTO_WITH_EXCEPTION` | Mechanical normal path with named exception routing |
| `HUMAN_REQUIRED` | A qualified person must decide |
| `RELEASE_BLOCKED` | The proposed change must not ship |

The Human Necessity Map is versioned and hashable. An unapproved entry escalates to at least
`HUMAN_REQUIRED`; it cannot silently authorize automation. Categories involving legal authority,
accessibility, discretion, appeals or separation of duties cannot be declared fully mechanical.

Mechanical work includes format normalization, schema checks, exact mappings, duplicate detection
and immutable-reference comparisons. Human review remains for ambiguity, conflicting evidence,
rights-impacting outcomes, accessibility exceptions, appeals, new case types and control removal.

## Determinism

Determinism is structural rather than conventional:

- ordered maps and sorted records remove insertion-order effects;
- `DecisionTier.escalate` is commutative, associative and idempotent;
- virtual-thread results are collected by submission index and then sorted by trace identifier;
- each task binds its case-specific Java 25 `ScopedValue` inside the virtual thread;
- the clock is injected and timing metrics, the generated run identifier and database identifiers are
  all excluded from canonical hashes;
- replay agreement is measured on two raw runs before the agreement metric is appended, so the
  comparison never observes its own result;
- canonical JSON has fixed member ordering, number formatting and escaping; and
- the verifier uses no network, database, model output, randomness or mutable global state.

`DeterminismTest` exercises different clocks, repeated concurrent runs and unavailable agents;
`ReplayVerificationTest` adds a deliberately induced disagreement.

## Interoperability boundary

Interoperability is required as a projection point but no external system was specified. CivLint
therefore implements the contract boundary, not an invented connector.

`EvaluationDatasetPort` returns one immutable snapshot containing:

- the approved policy pack;
- procedure versions;
- the Human Necessity Map;
- evaluation cases; and
- the selected existing and proposed versions.

`DemoEvaluationDatasetAdapter` supplies the synthetic data. A later adapter may translate BPMN 2.0,
REST/OpenAPI, a registry or a case-management API into the same domain records. Replacing the adapter
does not change `CivLintService`, `EvaluationHarness` or either verifier.

Outbound interoperability already has two seams: `EvaluationRunRepository` for storage and canonical
JSON endpoints for machine consumption.

## Persistence and API

Flyway owns a single H2 schema; Hibernate validates but never creates it. A stored row contains an
indexed summary and the exact canonical JSON used for hashing. Full object rehydration after restart
is not implemented and is disclosed as a limitation.

Every JSON endpoint uses `CanonicalJson`, including collection and data-mode responses. A caller can
hash the response bytes and compare them with the published digest. `CivLintApiIT` verifies the real
HTTP lifecycle, Flyway migration, closed administrative endpoints and read-only dashboard behavior.
