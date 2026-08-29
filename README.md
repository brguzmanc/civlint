# CivLint

Deterministic regression testing for public-procedure redesign.

- **Author:** Buddy Guzman (`bguzman`)
- **Version:** 0.1.0
- **Runtime:** Java 25, Spring Boot 4.1.1
- **Data mode:** synthetic demonstration data only

> The Federated Civil Registry, its regions, offices, roles, policies and applicants are fictional.
> CivLint contains no real personal information, describes no real law and makes no binding decision.

## Problem and intended user

Public-service transformation teams must remove delay without removing safeguards. A normal process
diff can show that a human step disappeared, but it cannot tell whether the step was duplicated work
or the only appeal, authority or separation-of-duty control protecting a citizen.

CivLint gives change owners and qualified public-administration reviewers a reproducible answer:

- which checks are mechanical and can be automated;
- which cases need exception routing or human judgment;
- which proposed changes must not ship; and
- which rule, evidence and minimal counterexample supports each conclusion.

The central artifact is a versioned **Human Necessity Map**. It records why each human checkpoint is
or is not required. Agents propose observations; a deterministic verifier makes every decision.

## Quick start

Requirements: JDK 25 and network access for Maven's first dependency download. No API key, paid model,
cloud account or external service is required.

```bash
java -version
./mvnw -version
./mvnw clean verify
./mvnw spring-boot:run
```

Open `http://localhost:8080/` for the side-by-side baseline and advanced demonstration. Its Agent
trajectories section carries a collapsed advisory view of every validated agent observation — tier,
category, confidence, rationale and cited evidence — next to the decisions the verifier reached
independently of it.

### Dashboard and API

The dashboard is one server-rendered Thymeleaf page with a single local stylesheet: no JavaScript,
no frontend build and no external asset. `GET /` is read-only; only `POST /api/evaluations` executes
and stores a run, answering `201 Created` with a `Location` header. The `/api/**` responses are raw
canonical JSON, and the project intentionally publishes no OpenAPI document or Swagger UI. The full
endpoint table — method, purpose, success status and whether the call changes state — is in
[docs/reproduction.md](docs/reproduction.md#dashboard-and-api-reference).

Useful commands:

| Purpose | Command |
|---|---|
| Full quality gate | `./mvnw clean verify` |
| Fifteen-case evaluation | `./mvnw test -Dtest=OracleAgreementTest` |
| Prove agent independence | `./mvnw test -Dtest=AgentIndependenceTest` |
| Prove deterministic replay | `./mvnw test -Dtest=DeterminismTest` |
| Prove replay verification | `./mvnw test -Dtest=ReplayVerificationTest` |
| Verify module boundaries | `./mvnw test -Dtest=ModularityTest` |
| Check generated evidence for drift | `./mvnw test -Dtest=ArtifactGeneratorTest` |
| Regenerate evidence intentionally | `./mvnw test -Dtest=ArtifactGeneratorTest -Dcivlint.artifact.write=true` |
| Generate Javadoc | `./mvnw javadoc:javadoc` |

## Architecture

CivLint is one deployable modular monolith with hexagonal boundaries. Spring Modulith and ArchUnit
verify module direction, framework isolation and the absence of static mutable state.

Interoperability is an extension point, not a fictional integration:

- `EvaluationDatasetPort` supplies a consistent policy, procedure, Human Necessity Map, case set and
  selected versions.
- `DemoEvaluationDatasetAdapter` is the synthetic implementation used here.
- A future REST, OpenAPI, BPMN or case-management adapter replaces that bean without changing the
  application use case or verifier.
- `EvaluationRunRepository` is the outbound persistence port, while the canonical JSON API provides
  a stable machine-readable result boundary.

No vendor connector is included because no real target system or contract was provided.

## Evaluation

The same 15 cases and locked oracle are used for both architectures.

| Metric | Simple baseline | Agent solution | Change |
|---|---:|---:|---:|
| **Primary outcome** — exact tier-and-reviewer oracle agreement | 46.67% | 100.00% | +53.33 pp |
| Mandatory-human-gate recall | 60.00% | 100.00% | +40.00 pp |
| Unsafe gate removals | 2 | 0 | -2 |
| Separation-of-duty preservation | 0.00% | 100.00% | +100.00 pp |
| Finding precision / recall | unavailable / 0.00% | 100.00% / 100.00% | not comparable / +100.00 pp |
| Human time per task — verified human-touch burden | 43 touch units | 29 touch units | -32.56% |
| Cost per task | USD 0.00 | USD 0.00 | none |

The primary outcome is exact agreement on decision tier **and** required reviewer role, because a
right decision routed to the wrong authority is still a governance failure. Baseline finding
precision is unavailable rather than zero: with no verifier it produces no findings, so the ratio has
no denominator, and reporting 0% would overstate what was measured. Cost is USD 0.00 for both because
neither path calls a paid service.

The baseline responses are authored fixtures, not recorded live-model output. The comparison measures
architecture under those documented assumptions, not universal model quality. The advanced safety
results do not depend on those assumptions.

See [evaluation-report.md](evaluation-report.md) for generated results and
[evaluation-results.json](evaluation-results.json) for canonical machine-readable evidence.

## Documentation

| File | Purpose |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Modules, decision authority and interoperability |
| [docs/agent-contracts.md](docs/agent-contracts.md) | Instructions, limits and orchestration for every agent |
| [docs/agent-traces/README.md](docs/agent-traces/README.md) | One representative generated trajectory per agent |
| [docs/evaluation-methodology.md](docs/evaluation-methodology.md) | Cases, baseline fairness, metrics and limitations |
| [docs/improvement-changelog.md](docs/improvement-changelog.md) | Iterations, evidence, retained and removed experiments |
| [docs/reproduction.md](docs/reproduction.md) | Clean-environment commands, outputs, runtime and cost |
| [docs/security-and-data.md](docs/security-and-data.md) | Data, privacy, security and deployment boundaries |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | Direct dependency and licensing disclosure |

## Competition provenance

CivLint application code, synthetic policy and procedure data, tests, evaluation artifacts and
documentation were created during this challenge. Before kickoff, only general-purpose development
tools and third-party frameworks were available. No prebuilt CivLint implementation and no private
dataset were reused.

## Java 25 choices

Final `ScopedValue` for immutable per-agent context, virtual threads for independent agent
invocations, records, sealed interfaces, pattern matching and sequenced collections.

No preview or incubator API is used: the build carries no `--enable-preview` flag, and Maven Enforcer
rejects snapshots and dynamic versions.

## Known limitations

1. Agent responses are authored replay fixtures; no live-model adapter is included.
2. The oracle, policy and verifier share one author. Their agreement proves implementation
   consistency, not independent public-administration validation.
3. Full `EvaluationRun` objects are cached in process. The database stores the canonical document and
   indexed summary, but the object graph is not rehydrated after restart.
4. DEMO mode has no authentication, authorization, rate limiting or production data controls.
5. No legal, security, accessibility or production-readiness certification is claimed.

## Design stance

The failure mode this design guards against is mistaking agent fluency for agent correctness. A
confident, well-written wrong answer about an appeal route is more dangerous than no answer, because
it survives review.

So the workflow is built so its safety result does not move when the agents do. That is not a slogan
but a build step — `AgentIndependenceTest` runs the whole evaluation with hostile agents and with no
agents, and fails if any verdict changes.

Both points are developed, with the evidence and the experiments that produced them, in
[docs/improvement-changelog.md](docs/improvement-changelog.md).
