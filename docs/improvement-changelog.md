# Improvement changelog

Author: Buddy Guzman (`bguzman`).

Every retained or removed experiment below uses the same 15-case oracle unless stated otherwise.

| Stage | What changed and why | Evidence | Decision / learning |
|---|---|---|---|
| Baseline | One general-purpose agent judged every case with no typed decomposition, Human Necessity Map or verifier. | 46.67% exact oracle agreement; 60% human-gate recall; two unsafe removals. | Kept as the fair comparison. It may block releases and catches the visible appeal deletion, so it is not deliberately powerless. |
| 1 - Deterministic verifier | Added policy criteria, graph checks, abstention and minimal counterexamples. | Advanced reaches 100% agreement and gate recall, zero unsafe removals and 100% finding precision/recall. | Kept. The main improvement is verification authority, not extra prompting. |
| 2 - Human Necessity Map | Made the human/mechanical boundary a versioned, approved and hashable artifact. | One duplicated clerical gate is safely removable; appeal and duty-control removals are blocked. | Kept. Digital transformation must distinguish duplicated labor from judgment and rights protection. |
| 3 - Bounded agents | Split rule mapping, boundary discovery and repair advice into contracts with enforced permissions. | Hostile, unavailable and replay agents produce identical advanced decisions; invalid output is rejected and retried once. | Kept as advisory analysis. Agents add explanations and repair proposals but cannot approve. |
| 4 - All agents on every case | Initially invoked three specialised agents for all 15 cases. | 45 advanced calls; 21 had no relevant fixture or remit. | **Removed.** Purposeful orchestration matters more than component count. The final plan makes 24 relevant calls: 15 rule-mapping, seven boundary and two repair. |
| 5 - Primary release semantics | Initial release status folded findings from the primary proposal and deliberately unsafe evaluation variants together. | A safe primary proposal appeared blocked because cases 14 and 15 were designed to fail. | Fixed. Scenario findings remain visible, while only primary-proposal findings determine its release outcome. |
| 6 - Read-only dashboard | Initial dashboard GET called the persistence use case twice per architecture. | Merely opening `/` executed four runs and wrote database rows. | Fixed with `preview`; POST remains the explicit state-changing operation. An HTTP test pins the behavior. |
| 7 - Real Java 25 context | Initial `ScopedValue` binding surrounded the caller while work ran in independently created executor threads. | The binding was not consumed or verified inside agent tasks. | Fixed by binding a case-specific context inside each virtual-thread task and rejecting missing/mismatched context. |
| 8 - Artifact drift gate | Initial artifact test wrote reports before comparing them. | A stale report could be silently replaced during a normal build. | Fixed. Normal tests are read-only; explicit write mode regenerates evidence and deletes stale trajectories. |
| 9 - Interoperability seam | Application code initially imported synthetic `Demo*` factories directly. | A real procedure source would have required editing the use case and hard-coded version identifiers remained in the harness. | Fixed with `EvaluationDatasetPort`, an immutable snapshot and a synthetic adapter. No fictional vendor connector was added. |
| 10 - Visible agent value | The dashboard listed trace identifiers and observation counts, so an outside reader could not see what the specialised agents actually contributed. | A collapsed native `<details>` view now exposes every validated observation's agent, subject, proposed tier, category, confidence, rationale and cited evidence, labelled advisory; case 15 is walked through on the page. | Kept. Advisory work should be legible without becoming authoritative — the verifier still decides, and `AgentIndependenceTest` is unchanged. |
| 11 - Dead code and comment pass | Removed dead loaders/models, unused persistence methods, an unused validation starter and repetitive method Javadoc. | Clean compile, module verification and full test gate; generated evidence remains reproducible. | Kept only comments that explain invariants, non-obvious decisions or public contracts. |
| 12 - Dependency and configuration hardening | Embedded Tomcat was upgraded from the BOM's 11.0.24 to 11.0.25 via an explicit `tomcat.version` override, and three error-response hardening properties were migrated to the keys that still bind in Spring Boot 4. | Packaged jar contains only `tomcat-embed-*-11.0.25`; 420 tests pass with zero compiler warnings; generated evidence unchanged. | Kept. A dependency a build resolves is a dependency the build owns, and a security property that no longer binds is not a control. |
| 13 - Accessible evidence dashboard | Replaced implicit browser colors and always-open wide evidence tables with explicit light/dark design tokens, responsive scroll regions, semantic definition lists, landmarks and native progressive disclosure, without adding JavaScript or a frontend toolchain. | 18 HTTP integration tests now verify local CSS delivery, responsive landmarks, accessible table structure, model-derived summary values, no inline style or script, no external assets and read-only rendering; the full gate passes 425 tests. | Kept; evidence must remain readable across desktop and mobile without adding a second build system or weakening determinism. |
| 14 - One evidence surface | The published artifacts and the dashboard showed a raw run while `CivLintService.evaluate()` appended `M.REPLAY.AGREEMENT` before storing, so one evaluation had two canonical hashes depending on the surface a reader used. A single `ReplayVerification` operation now compares two raw runs, fails if they differ and appends the metric once; every surface calls it. The allow rationale was also reworded: it credited the deterministic verifier, which the baseline does not have. The same audit removed two duplicated computations: the policy-pack hash was re-serialised once per case and agent inside the invocation loop, and `save()` built the canonical document twice. A negative-control pass also found the stored `canonical_hash` column was never read back, so a wrong value there would have persisted while every test passed; the stored row is now asserted to be self-verifying. | 437 tests (416 unit, 21 HTTP), zero failures, errors or skips; 93.28% line and 87.13% branch coverage; warm `GET /` fell from about 44 ms to about 27 ms with generated evidence byte-identical; advanced `fadb5f9431761ceab8ac270a963ad45925c2f3159999719f9b00731601fadc74` and baseline `c23626249b4a3e77e070fe435affa616a2ef732ab9295fb1de0a6034120e0b3d` now agree across all five evidence surfaces. | Kept. A reproducibility claim that differs by surface is not a reproducibility claim, and a rationale naming a component the run did not use is inaccurate even when the outcome is right. |

## Defects caught during construction

The following failures changed the implementation rather than being hidden:

- A deterministic finding identifier could exceed the database's 64-character limit. Long tails now
  use a digest of the full identifier, with collision-regression coverage.
- Wall-clock duration entered the canonical document and made identical replays hash differently.
  Millisecond metrics are now excluded from canonical hashes.
- A private runtime exception used to escape nested conflict detection was never caught. Direct,
  deterministic returns replaced exception-driven control flow.
- Spring Boot 4 requires `spring-boot-starter-flyway`; bare Flyway did not auto-configure migrations.
- Hibernate schema validation caught `CHAR(64)` versus `VARCHAR(64)` drift between SQL and JPA.
- Spring Boot 4 removed the former test REST helper; the integration test now uses JDK
  `HttpClient` against a real random port.
- The Maven wrapper initially inherited a private mirror URL. It now points to Maven Central.
- Three error-response hardening properties were **silently inert**. Spring Boot 4.0.0 relocated
  `server.error.include-message`, `-stacktrace` and `-binding-errors` to `spring.web.error.*` at
  deprecation level `error`, so the configured values bound to nothing. Boot's defaults happened to
  match, so no behaviour changed — but the repository was claiming a control it was not exercising.
  The keys now bind.
- `VersionComparison.GateChange` had **no test coverage at all**: the demo dataset contains no gate
  change, so the `mandatoryLost`, `appealabilityLost`, `sequenceChanged` and `toJson` paths were never
  executed. Three tests now cover a gate that stops being mandatory, a gate that can no longer be
  appealed, and a gate that only moves in the order. Detecting a weakened safeguard is the tool's
  central claim; it should not have been the untested part.

## Removed during construction

- **All agents on every case** — 45 invocations, 21 without a relevant fixture or remit. Replaced by
  24 remit-selected calls.
- **File policy loader** — an unused and untested implementation. `EvaluationDatasetPort` is the
  extension point until a real external contract exists.

## Design stance

The failure mode driving these decisions is treating “the model produced an answer” as evidence that
a consequential workflow is safe.

The strongest agent workflow is sometimes one whose safety result is unchanged when the agents are
hostile or absent. Agents search, explain and propose; irreversible authority stays behind
deterministic evidence and qualified human checkpoints.
