# Agent contracts

Author: Buddy Guzman (bguzman).

## The rule that governs everything here

Agents propose. They never decide. Nothing in the `agents` module can produce a release approval, and
ArchUnit forbids the module from even referencing `verification`, `policy` or `evaluation` — an agent
cannot call, configure or bypass the check that judges it.

## The three specialised agents

| Agent | Remit | May propose automation | May block a release |
|---|---|---|---|
| `AGENT.RULEMAPPER` | Extract candidate deterministic rules, identify policy references, propose Human Necessity Map entries | yes | no |
| `AGENT.BOUNDARY` | Surface difficult cases: ambiguity, conflicting evidence, accessibility, appeals, authority | **no** | no |
| `AGENT.REPAIR` | Explain findings and propose the smallest safe repair | **no** | no |

Plus `AGENT.GENERALIST`, the baseline architecture's single agent, which may do both — see below.

`mayProposeAutomation` is enforced, not documentation: `AgentRunner` drops an `AUTOMATE` observation
from an agent not permitted to make one and records an `OBSERVATION_REJECTED` trace event. An agent
whose remit is finding places automation would be unsafe has no business proposing automation, and
bounding what each agent may *say* is what makes three specialised agents different from three copies
of one agent.

Each agent also never does the thing that would undermine it: the Rule Mapper never approves its own
suggestions, the Boundary Case Agent never alters the authoritative oracle, and the Repair Advisor
never modifies the policy or marks a release safe. The first and third hold structurally — there is no
code path from an observation to an approval. The second holds because the oracle is a compile-time
constant in `DemoCases`.

## Release authority is a parameter, for fairness

Under the advanced contract an agent proposing `RELEASE_BLOCKED` is a contract breach: release
authority belongs to the verifier, and the attempt itself is worth seeing in a trace.

Applying that restriction to the baseline would rig the comparison. The baseline has no verifier, so
an architecture that cannot express "stop" would fail every safety case by construction rather than on
its merits. `AgentContract.validate(request, response, allowReleaseBlocked)` therefore takes the
permission explicitly, and only `AGENT.GENERALIST` has it —
`OracleAgreementTest.baselineMayBlock` asserts the baseline actually uses it on case 14.

## The JSON contract

```json
{
  "agentId": "AGENT.RULEMAPPER",
  "agentVersion": "0.1.0",
  "observations": [
    {
      "observationId": "OBS.RM.01.COMPLETE",
      "subject": { "type": "CASE", "id": "CASE.01.COMPLETE" },
      "proposedTier": "AUTOMATE",
      "category": "MECHANICAL",
      "rationale": "Every field matches the certified extract; no transformation is needed.",
      "confidence": 92,
      "references": [
        { "kind": "CASE_REQUEST", "targetId": "CASE.01.COMPLETE",
          "description": "Input examined by the agent" }
      ]
    }
  ]
}
```

`subject.type` is one of `CASE`, `STEP`, `GATE`, `VERSION`, `POLICY`, `STEP_PAIR`; `STEP_PAIR`
additionally requires `secondId`.

### Validation, which is stricter than the grammar

| Check | Rationale |
|---|---|
| Parsed by `JsonReader` | Bounds length (1 MiB), depth (32), values (20,000); rejects duplicate members, leading zeros, `NaN`, comments, trailing content |
| Unknown members rejected at every level | A field CivLint does not understand is not ignored — ignoring it is how an extra instruction goes unexamined |
| `agentId` and `agentVersion` must match the request | A fixture cannot be served for the wrong agent or the wrong contract version |
| `RELEASE_BLOCKED` rejected unless permitted | Release authority belongs to the verifier |
| ≤50 observations, ≤2,000-character rationale, ≤20 references | A response cannot enlarge a run's cost |
| Domain construction failure is a contract breach | An out-of-range confidence is reported with the same failure type as a malformed field |
| Observations returned sorted by identifier | Input order cannot affect output |

A contract breach is retried **once**, then the invocation is recorded `SCHEMA_REJECTED` with **no**
observations — `AgentTrace`'s constructor refuses to hold observations for a rejected or failed
invocation, so partial output cannot reach the verifier.

## Traces

Every invocation produces an `AgentTrace` pinning the agent to exactly what it saw: input hash, policy
hash, procedure versions in scope, observations, ordered events, retries, final status. Two runs whose
traces carry the same hashes and observations are the same run.

`SKIPPED` means no fixture existed. It is recorded as its own status rather than as success or failure,
because a defaulted response would be indistinguishable from a real one.

**Redaction:** raw response text never enters a trace event — only its length and a validation
outcome. No unvalidated model text reaches a log, a trace file or the dashboard. Validated
observations *are* shown on the dashboard as advisory context, HTML-escaped by Thymeleaf so the
text arrives as data rather than markup, and labelled so no reader mistakes a proposal for a
decision.

## Execution

`AgentOrchestrator` runs independent invocations on virtual threads
(`Executors.newVirtualThreadPerTaskExecutor()`; `StructuredTaskScope` remains preview in JDK 25).
Each task binds its case-specific `RunContext` — run id, policy version, policy hash, case id and
correlation id — as a `ScopedValue` inside its virtual thread. `AgentRunner` rejects a missing or
mismatched context, and the binding is absent again after execution.

Results are collected by **submission index** and then sorted by trace identifier, so scheduling
cannot affect output. No mutable state is shared; no class in CivLint declares a static non-final
field, enforced by `ModularityTest`.

The Rule Mapper runs on all 15 cases, the Boundary Case Agent on the seven cases in its remit, and
the Repair Advisor on the two unsafe structural variants: 24 purposeful advanced invocations. The
earlier all-agents-on-all-cases design produced 45 calls, including 21 with no relevant fixture, and
was removed because component count is not evidence of useful orchestration.

## Replay and fixtures

`ReplayAgentAdapter` serves responses from
`/civlint/agents/replay/<agentId>/<promptKey>.json` on the classpath. The default path needs
**no API key, no internet access, no cloud credentials, no paid model and no external service**. A
missing fixture raises `AgentUnavailableException` and is recorded `SKIPPED` — never substituted with
a default.

`LiveAgentAdapter` is **not implemented**. `AgentModelPort` is the seam; no result in this repository
depends on a live model existing.

### The fixtures are authored, not recorded

**No live model was called.** The 39 fixtures were generated by
`tools/generate-replay-fixtures.py`, which is checked in so every authored assumption is inspectable
in one place rather than spread across 39 documents.

The baseline generalist's answers encode a documented model of single-agent behaviour:

| Case | Baseline answer | Modelled failure |
|---|---|---|
| 02 DIACRITIC | `HUMAN_REQUIRED` | Over-caution: an accent reads as a name change |
| 07 COMPOUND | `HUMAN_REQUIRED` | Over-caution: a compound surname looks unfamiliar |
| 08 CERTIFIED.NAMECHANGE | `AUTOMATE` | Under-caution: complete paperwork reads as routine |
| 10 MISSING.NONCRITICAL | `HUMAN_REQUIRED` | Refuses instead of routing an exception |
| 12 STRUCTURE | `AUTOMATE` | Asserts equality about a structure it cannot resolve |
| 13 ACCESSIBILITY | `HUMAN_REQUIRED`, wrong reviewer | Right tier, no typed routing |
| 14 APPEAL.REMOVED | `RELEASE_BLOCKED` | **Caught** — a deleted step is visible from a diff |
| 15 DUTY.VIOLATION | `AUTOMATE` | Missed — every step still exists and still has a human |

Cases 1, 3, 5, 6, 9 and 11 are answered correctly.

**Consequence:** the baseline-versus-advanced comparison measures **architecture under stated
assumptions**, not model quality. The advanced architecture's own safety results do not depend on the
fixtures at all — `AgentIndependenceTest` proves the verdicts are identical with hostile agents, with
silent agents, and with fixtures. See `docs/evaluation-methodology.md`.

## Representative trajectories

`docs/agent-traces/` contains exactly four representative generated trajectories: one for each agent,
all using challenging case 15. Each document includes the instruction, permissions, input hashes,
adapter response events, contract feedback, observations, retry count, human checkpoint and final
case result. Using one shared case makes the baseline miss and the specialised-agent behavior directly
comparable.

No external tool is called in DEMO mode. `ReplayAgentAdapter` is the deterministic model boundary;
raw response text is deliberately excluded from traces. The successful representative trajectories
have zero retries. Retry behavior is independently covered by `AgentContractTest` and
`AgentIndependenceTest` rather than manufactured for display.
