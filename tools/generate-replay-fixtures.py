#!/usr/bin/env python3
"""Generates the checked-in agent replay fixtures for CivLint.

Author: Buddy Guzman (bguzman)

These fixtures are AUTHORED STAND-INS, not recordings of a live model. This script exists so that
every authored assumption is inspectable in one place rather than buried in 39 JSON files. See
docs/agent-contracts.md for the assumption table and docs/evaluation-methodology.md for what this
does and does not license anyone to conclude.

The baseline generalist's answers encode a documented model of single-agent behaviour: over-caution
on unfamiliar-looking formatting, under-caution where paperwork appears complete, and an inability to
compare procedure versions structurally. It is deliberately NOT strawmanned - it gets the easy cases
right, catches the conflicting-records case, and catches the deleted appeal step, which is visible
from a step-list diff.
"""
import json
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/civlint/agents/replay"

CASES = [
    "CASE.01.COMPLETE", "CASE.02.DIACRITIC", "CASE.03.WHITESPACE",
    "CASE.04.DUPLICATE.CLERICAL", "CASE.05.MAPPING", "CASE.06.HISTORICAL",
    "CASE.07.COMPOUND", "CASE.08.CERTIFIED.NAMECHANGE", "CASE.09.RENEWED.DOCUMENT",
    "CASE.10.MISSING.NONCRITICAL", "CASE.11.CONFLICT", "CASE.12.STRUCTURE",
    "CASE.13.ACCESSIBILITY", "CASE.14.APPEAL.REMOVED", "CASE.15.DUTY.VIOLATION",
]

VERSION_CASES = {"CASE.04.DUPLICATE.CLERICAL", "CASE.14.APPEAL.REMOVED", "CASE.15.DUTY.VIOLATION"}

# --- Rule Mapper: proposes the mechanical reading. Right on easy cases, wrong on several hard ones,
# which is the point: the verifier must be able to correct it.
RULE_MAPPER = {
    "CASE.01.COMPLETE":            ("AUTOMATE",            "MECHANICAL", 92, "Every field matches the certified extract; no transformation is needed."),
    "CASE.02.DIACRITIC":           ("AUTOMATE",            "MECHANICAL", 90, "The values differ only by combining marks, which fold under a published rule."),
    "CASE.03.WHITESPACE":          ("AUTOMATE",            "MECHANICAL", 94, "The values differ only by case and trailing whitespace."),
    "CASE.04.DUPLICATE.CLERICAL":  ("AUTOMATE",            "MECHANICAL", 70, "The second clerical read repeats the first with the same role and checklist."),
    "CASE.05.MAPPING":             ("AUTOMATE",            "IDENTITY_CONSISTENCY", 88, "The national identifier is stated by an authoritative national registry entry."),
    "CASE.06.HISTORICAL":          ("AUTOMATE",            "MECHANICAL", 85, "The request compares against the historical entry without altering it."),
    "CASE.07.COMPOUND":            ("AUTOMATE",            "MECHANICAL", 82, "Hyphen and space are interchangeable compound joiners."),
    # Wrong: treats complete paperwork as making a substantive change mechanical.
    "CASE.08.CERTIFIED.NAMECHANGE":("AUTOMATE",            "MECHANICAL", 61, "A certified order accompanies the request, so the change appears fully supported."),
    "CASE.09.RENEWED.DOCUMENT":    ("AUTOMATE",            "MECHANICAL", 87, "The authoritative entry is unchanged; only a document reference was renewed."),
    "CASE.10.MISSING.NONCRITICAL": ("AUTO_WITH_EXCEPTION", "EVIDENCE_COMPLETENESS", 74, "An authoritative source is present but a supporting document is absent."),
    "CASE.11.CONFLICT":            ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 80, "Two authoritative sources state different surnames."),
    # Wrong: guesses that an added name part is the same name.
    "CASE.12.STRUCTURE":           ("AUTOMATE",            "MECHANICAL", 55, "The requested value contains the recorded value as a prefix, so it looks like the same name."),
    "CASE.13.ACCESSIBILITY":       ("HUMAN_REQUIRED",      "ACCESSIBILITY", 83, "An accommodation and alternative evidence were both offered."),
    "CASE.14.APPEAL.REMOVED":      ("AUTO_WITH_EXCEPTION", "APPEAL_RIGHTS", 60, "The proposed version has fewer steps than the existing one."),
    "CASE.15.DUTY.VIOLATION":      ("AUTO_WITH_EXCEPTION", "SEPARATION_OF_DUTIES", 52, "Approval was reassigned to another role."),
}

# --- Boundary Case Agent: never proposes automation. Flags the hard cases.
BOUNDARY = {
    "CASE.08.CERTIFIED.NAMECHANGE":("AUTO_WITH_EXCEPTION", "IDENTITY_CONSISTENCY", 78, "The recorded content changes, which is distinct from a formatting normalisation even when authority is supplied."),
    "CASE.10.MISSING.NONCRITICAL": ("AUTO_WITH_EXCEPTION", "EVIDENCE_COMPLETENESS", 76, "A supporting document is absent; route as an exception rather than refusing."),
    "CASE.11.CONFLICT":            ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 91, "Preferring one authoritative source over another has no mechanical basis."),
    "CASE.12.STRUCTURE":           ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 86, "The number of name parts changes; no normalisation rule covers this, so equality must not be asserted."),
    "CASE.13.ACCESSIBILITY":       ("HUMAN_REQUIRED",      "ACCESSIBILITY", 93, "Whether the alternative evidence offered is adequate is a judgement about this applicant's circumstances."),
    "CASE.14.APPEAL.REMOVED":      ("HUMAN_REQUIRED",      "APPEAL_RIGHTS", 89, "The appeal adjudication step is absent from the proposed version."),
    "CASE.15.DUTY.VIOLATION":      ("HUMAN_REQUIRED",      "SEPARATION_OF_DUTIES", 84, "Preparation and approval resolve to the same role in the proposed version."),
}

# --- Repair Advisor: smallest safe repair for the unsafe variants.
REPAIR = {
    "CASE.14.APPEAL.REMOVED":      ("HUMAN_REQUIRED", "APPEAL_RIGHTS", 88, "Smallest safe repair: restore the appeal adjudication step and the notification transition that reaches it, leaving every automated check in place. Do not compensate by adding review elsewhere."),
    "CASE.15.DUTY.VIOLATION":      ("HUMAN_REQUIRED", "SEPARATION_OF_DUTIES", 90, "Smallest safe repair: return the approval step to a role other than the preparing role. Reassigning preparation instead would also satisfy the constraint but moves more of the procedure than necessary."),
}

# --- Baseline generalist: one agent, no verifier, no map, no structural comparison.
BASELINE = {
    "CASE.01.COMPLETE":            ("AUTOMATE",            "MECHANICAL", 88, "Nothing appears to be in dispute and the evidence is complete."),
    # Over-caution: an accent change reads as a name change.
    "CASE.02.DIACRITIC":           ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 64, "The given name is spelled differently from the register, so a person should confirm it."),
    "CASE.03.WHITESPACE":          ("AUTOMATE",            "MECHANICAL", 81, "The difference is only capitalisation and spacing."),
    "CASE.04.DUPLICATE.CLERICAL":  ("AUTO_WITH_EXCEPTION", "MECHANICAL", 58, "Steps were removed from the procedure; someone should look over the change."),
    "CASE.05.MAPPING":             ("AUTOMATE",            "IDENTITY_CONSISTENCY", 79, "The national identifier matches the national registry entry."),
    "CASE.06.HISTORICAL":          ("AUTOMATE",            "MECHANICAL", 75, "The values agree with the record."),
    # Over-caution: compound surname looks unfamiliar.
    "CASE.07.COMPOUND":            ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 60, "The surname format differs from the register and should be checked by a person."),
    # Under-caution: paperwork looks complete, so it files a substantive change as routine.
    "CASE.08.CERTIFIED.NAMECHANGE":("AUTOMATE",            "MECHANICAL", 66, "A certified order is attached, so the change is fully documented."),
    "CASE.09.RENEWED.DOCUMENT":    ("AUTOMATE",            "MECHANICAL", 77, "Only the document reference changed."),
    # Over-caution: refuses rather than routing as an exception.
    "CASE.10.MISSING.NONCRITICAL": ("HUMAN_REQUIRED",      "EVIDENCE_COMPLETENESS", 71, "Evidence is incomplete, so the request cannot proceed without a person."),
    "CASE.11.CONFLICT":            ("HUMAN_REQUIRED",      "IDENTITY_CONSISTENCY", 84, "The two registry entries disagree about the surname."),
    # Under-caution, and the failure that matters: asserts equality about a structure it cannot resolve.
    "CASE.12.STRUCTURE":           ("AUTOMATE",            "MECHANICAL", 57, "The requested name contains the recorded name, so it is the same person with a fuller name."),
    # Right tier, wrong reviewer: no typed routing.
    "CASE.13.ACCESSIBILITY":       ("HUMAN_REQUIRED",      "DISCRETIONARY", 72, "The request is unusual and should be reviewed by a supervisor."),
    # Catches this one: a deleted step is visible from a step-list diff.
    "CASE.14.APPEAL.REMOVED":      ("RELEASE_BLOCKED",     "APPEAL_RIGHTS", 74, "The appeal step is gone from the proposed procedure, which looks like a loss of a safeguard."),
    # Misses this one: every step still exists and every step still has a human assigned.
    "CASE.15.DUTY.VIOLATION":      ("AUTOMATE",            "MECHANICAL", 53, "All steps are still present and each still has a named human role, so the change looks safe."),
}

SUBJECT_FOR = lambda case_id: (
    {"type": "VERSION", "id": {"CASE.04.DUPLICATE.CLERICAL": "V2.NATIONAL",
                               "CASE.14.APPEAL.REMOVED": "V2.NOAPPEAL",
                               "CASE.15.DUTY.VIOLATION": "V2.NOSOD"}[case_id]}
    if case_id in VERSION_CASES else {"type": "CASE", "id": case_id}
)


def write(agent_id, version, case_id, entry, obs_prefix):
    tier, category, confidence, rationale = entry
    doc = {
        "agentId": agent_id,
        "agentVersion": version,
        "observations": [
            {
                "observationId": f"OBS.{obs_prefix}.{case_id}".replace("CASE.", ""),
                "subject": SUBJECT_FOR(case_id),
                "proposedTier": tier,
                "category": category,
                "rationale": rationale,
                "confidence": confidence,
                "references": [
                    {
                        "kind": "CASE_REQUEST" if case_id not in VERSION_CASES else "PROCEDURE_STEP",
                        "targetId": case_id if case_id not in VERSION_CASES else "S.APPROVE",
                        "description": "Input examined by the agent",
                    }
                ],
            }
        ],
    }
    directory = OUT / agent_id
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{case_id}.json"
    path.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


count = 0
for case_id in CASES:
    if case_id in RULE_MAPPER:
        write("AGENT.RULEMAPPER", "0.1.0", case_id, RULE_MAPPER[case_id], "RM"); count += 1
    if case_id in BOUNDARY:
        write("AGENT.BOUNDARY", "0.1.0", case_id, BOUNDARY[case_id], "BC"); count += 1
    if case_id in REPAIR:
        write("AGENT.REPAIR", "0.1.0", case_id, REPAIR[case_id], "RA"); count += 1
    if case_id in BASELINE:
        write("AGENT.GENERALIST", "0.1.0", case_id, BASELINE[case_id], "GEN"); count += 1

print(f"wrote {count} fixtures under {OUT.relative_to(ROOT)}")
