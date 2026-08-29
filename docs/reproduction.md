# Reproduction guide

Author: Buddy Guzman (`bguzman`).

## Requirements

- JDK 25; verified with Java 25.0.3 and 25.0.4.1
- Git, or an extracted source archive
- Internet access for the first Maven/dependency download
- No API key, model account, cloud account or external service

Expected recurring cost: **USD 0.00**. Expected warm local runtime: about 20-30 seconds for the full
quality gate and under one second for the isolated 15-case evaluation. Hardware and first-download
time vary.

## Clean environment

From a submitted source archive:

```bash
unzip civlint.zip && cd civlint     # or: tar -xzf civlint.tar.gz && cd civlint
```

From a repository, if one is provided:

```bash
git clone <repository-url> civlint && cd civlint
```

Then, either way:

```bash
java -version
./mvnw -version
./mvnw clean verify
./mvnw javadoc:javadoc
```

Expected result: `BUILD SUCCESS`, all unit and integration tests passing, JaCoCo at or above 90% line
and 80% branch coverage, and no generated-evidence drift.

The Maven wrapper downloads Maven 3.9.14 from Maven Central. After dependencies are cached, an offline
verification is possible:

```bash
./mvnw -o clean verify
```

The same command run against a freshly extracted copy is the cheapest reproduction check: it must
report `BUILD SUCCESS` with no compiler warnings and no generated-evidence drift.

### Windows PowerShell

The commands in this guide are written for a POSIX shell. In PowerShell, three substitutions apply and
nothing else changes:

- run `.\mvnw.cmd` instead of `./mvnw`;
- run `curl.exe` instead of `curl`, so the examples keep cURL syntax rather than being rewritten by
  PowerShell's `curl` alias for `Invoke-WebRequest`;
- verify SHA-256 digests with `Get-FileHash -Algorithm SHA256 <file>` instead of `shasum -a 256`.

## Run the baseline and final solution

Start the application:

```bash
./mvnw spring-boot:run
```

In another terminal:

```bash
curl -s -X POST 'http://localhost:8080/api/evaluations?mode=BASELINE' > baseline.json
curl -s -X POST 'http://localhost:8080/api/evaluations?mode=ADVANCED' > advanced.json
```

Or open `http://localhost:8080/` to see both architectures and all 15 cases side by side. Loading the
dashboard is read-only; only POST creates a stored run.

Expected primary results:

| Metric | Baseline | Advanced |
|---|---:|---:|
| Exact oracle agreement | 46.67% | 100.00% |
| Mandatory-human-gate recall | 60.00% | 100.00% |
| Unsafe gate removals | 2 | 0 |

## Dashboard and API reference

`GET /` is a server-rendered Thymeleaf page with one local stylesheet (`/css/dashboard.css`). It has
no JavaScript, no external asset and no client-side state, and it is deliberately **not** Swagger or
an OpenAPI console: the project publishes no `/v3/api-docs` document and ships no Swagger UI. The
`/api/**` responses are intentionally raw canonical JSON so their bytes can be hashed and compared
directly. In DEMO mode no API key is required.

Everything below `/api` is read-only except `POST /api/evaluations`, which executes an evaluation and
stores the run. It answers `201 Created` with a `Location` header pointing at the new run.

| Method | Endpoint | Purpose | Success | Changes state |
|---|---|---|---|:--:|
| GET | `/` | HTML dashboard: outcome, metrics, findings, map, traces, hashes | 200 | no |
| GET | `/actuator/health` | Liveness of the application and its schema | 200 | no |
| GET | `/api/data-mode` | Data-mode statement and synthetic-data disclosure | 200 | no |
| GET | `/api/procedures` | Procedure and its versions | 200 | no |
| GET | `/api/procedures/{id}/comparison` | Structural diff between two versions | 200 | no |
| GET | `/api/policy` | Policy pack, rules and criteria | 200 | no |
| GET | `/api/human-necessity` | Approved Human Necessity Map | 200 | no |
| GET | `/api/cases` | The fixed 15-case set and its locked oracle | 200 | no |
| POST | `/api/evaluations?mode=BASELINE` | Run and store the baseline architecture | 201 + `Location` | **yes** |
| POST | `/api/evaluations?mode=ADVANCED` | Run and store the agent architecture | 201 + `Location` | **yes** |
| GET | `/api/evaluations` | Summaries of the stored runs | 200 | no |
| GET | `/api/evaluations/{runId}` | The canonical document for one run | 200 | no |
| GET | `/api/evaluations/{runId}/findings` | Findings produced by that run | 200 | no |
| GET | `/api/evaluations/{runId}/metrics` | Published metrics for that run | 200 | no |
| GET | `/api/evaluations/{runId}/traces` | Agent trajectories for that run | 200 | no |
| GET | `/api/evaluations/{runId}/counterexamples` | Minimal counterexamples for that run | 200 | no |
| GET | `/api/evaluations/{runId}/report` | Report view of that run | 200 | no |

An unknown run, procedure or version answers `404` and an unusable `mode` answers `400`, both as RFC
9457 problem details rather than a stack trace.

Quick check that the page and its stylesheet are both served locally:

```bash
curl -I http://localhost:8080/
curl -I http://localhost:8080/css/dashboard.css
curl -s http://localhost:8080/ | grep -c '<script'
```

Expected: `200` and `text/html`, `200` and `text/css`, and `0` script elements.

## Run the evaluation directly

```bash
./mvnw test -Dtest=OracleAgreementTest
./mvnw test -Dtest=AgentIndependenceTest
./mvnw test -Dtest=DeterminismTest
./mvnw test -Dtest=ReplayVerificationTest
./mvnw test -Dtest=ModularityTest
```

These commands respectively verify the fixed oracle, independence from agent behavior, byte-stable
replay, the shared replay-verification operation behind every published hash, and architectural
boundaries.

## Generated reports and trajectories

A normal build verifies checked-in evidence without modifying it:

```bash
./mvnw test -Dtest=ArtifactGeneratorTest
```

After an intentional behavior change, regenerate once and review the diff:

```bash
./mvnw test -Dtest=ArtifactGeneratorTest -Dcivlint.artifact.write=true
./mvnw test -Dtest=ArtifactGeneratorTest
git diff -- evaluation-results.json evaluation-report.md docs/agent-traces
```

Write mode regenerates the two evaluation artifacts and exactly four representative trajectories,
deleting stale trajectory JSON files. Without the explicit property, the test is read-only and fails
if any evidence differs from a fresh execution.

## Compare deterministic outputs

Run the advanced endpoint twice:

```bash
curl -s -X POST 'http://localhost:8080/api/evaluations?mode=ADVANCED' > run-a.json
curl -s -X POST 'http://localhost:8080/api/evaluations?mode=ADVANCED' > run-b.json
shasum -a 256 run-a.json run-b.json
diff run-a.json run-b.json
```

Expected: equal digests and no diff. API responses use CivLint's canonical JSON, and timing fields are
excluded from the canonical document because identical work does not take identical wall-clock time.

That digest is also what the published artifacts and the dashboard show, because all of them come from
one replay-verified operation:

```bash
DIGEST=$(shasum -a 256 run-a.json | cut -d' ' -f1)
grep -c "$DIGEST" evaluation-results.json evaluation-report.md
curl -s http://localhost:8080/ | grep -c "$DIGEST"
```

Expected: a non-zero count from each. `CivLintApiIT` asserts the same across all five surfaces, for
both architectures, during `verify`.

## Data and fixtures

All required inputs are checked in:

- synthetic procedure, policy, Human Necessity Map and 15 cases in `evaluation/Demo*`;
- 39 authored model-response fixtures under `src/main/resources/civlint/agents/replay/`; and
- the generator in `tools/generate-replay-fixtures.py`.

Regenerating fixtures is not part of normal reproduction. If their documented assumptions are
intentionally changed:

```bash
python3 tools/generate-replay-fixtures.py
./mvnw test -Dtest=ArtifactGeneratorTest -Dcivlint.artifact.write=true
./mvnw clean verify
```

## Reproduction anchors

Record the exact Java and Maven output plus the policy, Human Necessity Map, procedure-version,
verifier and run hashes printed in `evaluation-report.md`. A differing input hash identifies changed
evidence; equal inputs with a differing canonical result identify nondeterminism.
