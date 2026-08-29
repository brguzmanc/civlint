package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.HumanNecessity;
import com.bguzman.civlint.domain.HumanNecessity.Reversibility;
import com.bguzman.civlint.domain.HumanNecessityMap;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCategory;
import java.util.List;

/**
 * The approved Human Necessity Map for the demonstration procedure.
 *
 * <p>Each entry states its reasoning, not merely its conclusion, so that the position can be argued
 * with. The two entries worth reading closely are {@code HN.CLERICAL.TWO}, which is the only place
 * that authorises removing a human step, and {@code HN.DECIDE}, which refuses automation for a step a
 * naive reading might consider routine.
 *
 * <p>All citations are invented. None refers to real law.
 */
public final class DemoHumanNecessity {

    public static final String MAP_ID = "FCR.NAMECORR.HNM";

    public static final String VERSION = "2026.08.1";

    private DemoHumanNecessity() {
        throw new AssertionError("No instances.");
    }

    public static HumanNecessityMap map() {
        return new HumanNecessityMap(
                MAP_ID,
                VERSION,
                DemoProcedures.PROCEDURE_ID,
                List.of(
                        automated(
                                "HN.FORMAT",
                                DemoProcedures.STEP_FORMAT,
                                RuleCategory.MECHANICAL,
                                "Synthetic FCR-HN-1",
                                "Field normalisation is a total function of the input: whitespace, case, "
                                        + "combining marks and compound joiners are folded by published rules "
                                        + "with an explicit abstention path when no rule applies.",
                                "None. The applicant's recorded name is unchanged by normalisation; only "
                                        + "the comparison is normalised.",
                                "The normaliser declines to conclude, which routes the case to a reviewer.",
                                "The two values being compared."),
                        automated(
                                "HN.NOTIFY",
                                DemoProcedures.STEP_NOTIFY,
                                RuleCategory.MECHANICAL,
                                "Synthetic FCR-HN-2",
                                "Issuing a notification is the mechanical delivery of a decision already "
                                        + "taken by a person, together with the fixed appeal wording.",
                                "The applicant must receive an accurate statement of their appeal rights; "
                                        + "the wording is fixed and not composed per case.",
                                "Delivery fails, which is retried and then escalated.",
                                "The decision record and the applicant's contact details."),
                        automated(
                                "HN.RECORD",
                                DemoProcedures.STEP_RECORD,
                                RuleCategory.RETENTION_AUDIT,
                                "Synthetic FCR-HN-3",
                                "Writing an immutable audit entry is a deterministic append with no "
                                        + "discretion available to the writer.",
                                "None directly; the entry preserves the applicant's ability to show what "
                                        + "was decided and when.",
                                "The append fails or a retention rule is not satisfied.",
                                "The decision record and the retention class."),
                        // The single entry that authorises removing human involvement.
                        automated(
                                "HN.CLERICAL.TWO",
                                DemoProcedures.STEP_CLERICAL_TWO,
                                RuleCategory.MECHANICAL,
                                "Synthetic FCR-HN-4",
                                "The second clerical read repeats the first with the same role, the same "
                                        + "file and the same checklist. It adds a second pair of eyes but no "
                                        + "second judgement, and the checks it performs are the deterministic "
                                        + "ones the verifier now performs exhaustively. Removing it therefore "
                                        + "removes duplicated effort rather than a safeguard. Separation of "
                                        + "duties is preserved elsewhere, at preparation and approval.",
                                "None. No decision is taken at this step and no right depends on it.",
                                "The first read raises an exception, which is routed to a reviewer.",
                                "The correction file as read at the first clerical step."),
                        humanRequired(
                                "HN.INTAKE",
                                DemoProcedures.STEP_INTAKE,
                                RuleCategory.MECHANICAL,
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.INTAKE_CLERK,
                                "Synthetic FCR-HN-5",
                                "Receiving a request is mechanical when the submission is complete and "
                                        + "well-formed. An incomplete or unusual submission needs a person to "
                                        + "decide what was actually asked for.",
                                "An incorrectly recorded request can send the whole case down the wrong "
                                        + "path, and the applicant may not discover it until a decision issues.",
                                Reversibility.REVERSIBLE_WITH_BURDEN,
                                "The submission is incomplete, unreadable, or does not match a known form.",
                                "The submitted request and any attachments."),
                        humanRequired(
                                "HN.EVIDENCE",
                                DemoProcedures.STEP_EVIDENCE,
                                RuleCategory.EVIDENCE_COMPLETENESS,
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                "Synthetic FCR-HN-6",
                                "Presence and certification of a document are checkable mechanically. "
                                        + "Whether a document that is present but imperfect is good enough is not.",
                                "Rejecting a case for missing evidence imposes a further trip and a further "
                                        + "wait on the applicant.",
                                Reversibility.REVERSIBLE_WITH_BURDEN,
                                "Evidence is missing, illegible, or uncertified.",
                                "The list of supplied evidence and its certification status."),
                        humanRequired(
                                "HN.IDENTITY",
                                DemoProcedures.STEP_IDENTITY,
                                RuleCategory.IDENTITY_CONSISTENCY,
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                "Synthetic FCR-HN-7",
                                "Agreement between sources can be computed. Disagreement between two "
                                        + "authoritative sources cannot be resolved by computation, because "
                                        + "there is no mechanical basis for preferring one source.",
                                "Choosing the wrong source writes a wrong name into the register and may "
                                        + "propagate to documents the applicant depends on.",
                                Reversibility.PARTIALLY_IRREVERSIBLE,
                                "Two authoritative sources disagree, or a comparison abstains.",
                                "The conflicting field values and their sources."),
                        humanRequired(
                                "HN.PREPARE",
                                DemoProcedures.STEP_PREPARE,
                                RuleCategory.MECHANICAL,
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                "Synthetic FCR-HN-8",
                                "Assembling a file from checked components is mechanical. Deciding that the "
                                        + "file is ready for a decision when a check abstained is not.",
                                "An incomplete file leads the decision-maker to decide on a partial view.",
                                Reversibility.FULLY_REVERSIBLE,
                                "Any upstream check abstained or raised an exception.",
                                "The checked components of the correction file."),
                        humanRequired(
                                "HN.LEGAL",
                                DemoProcedures.STEP_LEGAL,
                                RuleCategory.LEGAL_AUTHORITY,
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.LEGAL_REVIEWER,
                                "Synthetic FCR-HN-9",
                                "Whether an office holds delegated authority to decide a particular "
                                        + "correction is a question of legal interpretation. CivLint has no "
                                        + "mechanism for interpreting an instrument of delegation and does not "
                                        + "pretend to.",
                                "Acting without authority produces a decision the applicant may later find "
                                        + "was void, after relying on it.",
                                Reversibility.PARTIALLY_IRREVERSIBLE,
                                "Always; this step is never mechanical.",
                                "The instrument of delegation and the scope of the requested correction."),
                        humanRequired(
                                "HN.ACCESSIBILITY",
                                DemoProcedures.STEP_ACCESSIBILITY,
                                RuleCategory.ACCESSIBILITY,
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.ACCESSIBILITY_REVIEWER,
                                "Synthetic FCR-HN-10",
                                "An accommodation is by definition a departure from the standard path, and "
                                        + "the standard path is the only thing a mechanical rule describes. "
                                        + "Deciding whether alternative evidence is adequate requires judgement "
                                        + "about this applicant's circumstances.",
                                "Refusing an accommodation can exclude an applicant from the procedure "
                                        + "altogether, which is among the most serious outcomes available.",
                                Reversibility.PARTIALLY_IRREVERSIBLE,
                                "Always; this step is never mechanical.",
                                "The accommodation requested and the alternative evidence offered."),
                        humanRequired(
                                "HN.DECIDE",
                                DemoProcedures.STEP_DECIDE,
                                RuleCategory.DISCRETIONARY,
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                "Synthetic FCR-HN-11",
                                "The decision is the act that changes the applicant's record. Even where "
                                        + "every input check passed, policy reserves the decision itself to a "
                                        + "person, because it is the point at which the state acts on someone.",
                                "The recorded name of a person is changed or refused. Downstream documents "
                                        + "and entitlements depend on it.",
                                Reversibility.PARTIALLY_IRREVERSIBLE,
                                "Always; this step is never mechanical.",
                                "The prepared correction file and the applicable policy rules."),
                        humanRequired(
                                "HN.APPROVE",
                                DemoProcedures.STEP_APPROVE,
                                RuleCategory.SEPARATION_OF_DUTIES,
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                "Synthetic FCR-HN-12",
                                "Approval exists to place a second, independent person between a prepared "
                                        + "file and an effective decision. Automating it would delete the "
                                        + "independence that is the whole purpose of the step.",
                                "An unapproved or self-approved decision removes the applicant's protection "
                                        + "against a single official acting alone.",
                                Reversibility.PARTIALLY_IRREVERSIBLE,
                                "Always; this step is never mechanical.",
                                "The decision, its file, and the identity of the preparing officer."),
                        humanRequired(
                                "HN.APPEAL",
                                DemoProcedures.STEP_APPEAL,
                                RuleCategory.APPEAL_RIGHTS,
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.APPEALS_ADJUDICATOR,
                                "Synthetic FCR-HN-13",
                                "An appeal is the applicant's remedy against the procedure itself. A "
                                        + "mechanism that automated its own appeal would offer no remedy at all.",
                                "Without a hearing, an incorrect decision stands with no route to correct it.",
                                Reversibility.IRREVERSIBLE,
                                "Always; this step is never mechanical.",
                                "The original decision, its file, and the grounds of appeal.")));
    }

    private static HumanNecessity automated(
            String entryId,
            String stepId,
            RuleCategory category,
            String policySource,
            String reason,
            String citizenImpact,
            String exceptionTrigger,
            String minimumEvidence) {
        return new HumanNecessity(
                entryId,
                stepId,
                category,
                DecisionTier.AUTOMATE,
                policySource,
                reason,
                citizenImpact,
                Reversibility.FULLY_REVERSIBLE,
                exceptionTrigger,
                ReviewerRole.NONE,
                minimumEvidence,
                95,
                VERSION,
                true);
    }

    private static HumanNecessity humanRequired(
            String entryId,
            String stepId,
            RuleCategory category,
            DecisionTier tier,
            ReviewerRole role,
            String policySource,
            String reason,
            String citizenImpact,
            Reversibility reversibility,
            String exceptionTrigger,
            String minimumEvidence) {
        return new HumanNecessity(
                entryId,
                stepId,
                category,
                tier,
                policySource,
                reason,
                citizenImpact,
                reversibility,
                exceptionTrigger,
                role,
                minimumEvidence,
                90,
                VERSION,
                true);
    }
}
