# Representative agent trajectories

Author: Buddy Guzman (bguzman).

One generated trajectory is included for every agent used. All four use the same
challenging case so their instructions, actions, contract feedback and final
results can be compared directly. No external tool is called: the replay adapter
returns deterministic checked-in model output and raw text is deliberately redacted.

| Trajectory | Agent | Observations | Retries |
|---|---|---:|---:|
| [T.AGENT.BOUNDARY.CASE.15.DUTY.VIOLATION.json](T.AGENT.BOUNDARY.CASE.15.DUTY.VIOLATION.json) | AGENT.BOUNDARY | 1 | 0 |
| [T.AGENT.GENERALIST.CASE.15.DUTY.VIOLATION.json](T.AGENT.GENERALIST.CASE.15.DUTY.VIOLATION.json) | AGENT.GENERALIST | 1 | 0 |
| [T.AGENT.REPAIR.CASE.15.DUTY.VIOLATION.json](T.AGENT.REPAIR.CASE.15.DUTY.VIOLATION.json) | AGENT.REPAIR | 1 | 0 |
| [T.AGENT.RULEMAPPER.CASE.15.DUTY.VIOLATION.json](T.AGENT.RULEMAPPER.CASE.15.DUTY.VIOLATION.json) | AGENT.RULEMAPPER | 1 | 0 |

Full agent instructions and contract limits are in `../agent-contracts.md`.
The advanced verdict remains independent of agent output, proven by
`AgentIndependenceTest`.
