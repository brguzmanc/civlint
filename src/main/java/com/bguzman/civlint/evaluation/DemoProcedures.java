package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.ApprovalGate;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.Procedure;
import com.bguzman.civlint.domain.ProcedureGraph;
import com.bguzman.civlint.domain.ProcedureStep;
import com.bguzman.civlint.domain.ProcedureVersion;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCategory;
import com.bguzman.civlint.domain.SeparationOfDuty;
import com.bguzman.civlint.domain.StepKind;
import com.bguzman.civlint.domain.Transition;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.SequencedMap;

/**
 * The four synthetic procedure versions used in the demonstration.
 *
 * <p>{@link #existingRegional()} is the human-heavy regional procedure in force.
 * {@link #proposedNational()} is the safe modernisation. The two remaining versions are deliberately
 * unsafe and exist so that the verifier can be shown refusing them:
 * {@link #proposedWithAppealRemoved()} deletes the appeal route, and
 * {@link #proposedWithDutyViolation()} gives preparation and approval to the same role.
 *
 * <p>All step titles, roles, regions and identifiers are invented.
 */
public final class DemoProcedures {

    public static final String PROCEDURE_ID = "FCR.NAMECORR";

    public static final String VERSION_EXISTING = "V1.REGIONAL";

    public static final String VERSION_PROPOSED = "V2.NATIONAL";

    public static final String VERSION_APPEAL_REMOVED = "V2.NOAPPEAL";

    public static final String VERSION_DUTY_VIOLATION = "V2.NOSOD";

    public static final String STEP_INTAKE = "S.INTAKE";

    public static final String STEP_FORMAT = "S.FORMAT.CHECK";

    public static final String STEP_EVIDENCE = "S.EVIDENCE.CHECK";

    public static final String STEP_CLERICAL_ONE = "S.CLERICAL.ONE";

    public static final String STEP_CLERICAL_TWO = "S.CLERICAL.TWO";

    public static final String STEP_IDENTITY = "S.IDENTITY.MATCH";

    public static final String STEP_LEGAL = "S.LEGAL.REVIEW";

    public static final String STEP_ACCESSIBILITY = "S.ACCESSIBILITY.REVIEW";

    public static final String STEP_PREPARE = "S.PREPARE";

    public static final String STEP_DECIDE = "S.DECIDE";

    public static final String STEP_APPROVE = "S.APPROVE";

    public static final String STEP_NOTIFY = "S.NOTIFY";

    public static final String STEP_APPEAL = "S.APPEAL";

    public static final String STEP_RECORD = "S.RECORD";

    public static final String STEP_END = "S.END";

    public static final String GATE_DECIDE = "G.DECIDE";

    public static final String GATE_APPROVE = "G.APPROVE";

    public static final String GATE_LEGAL = "G.LEGAL";

    public static final String DUTY_PREPARE_APPROVE = "SOD.PREPARE.APPROVE";

    private DemoProcedures() {
        throw new AssertionError("No instances.");
    }

    public static Procedure procedure() {
        return new Procedure(
                PROCEDURE_ID,
                "Name and record correction (Federated Civil Registry, synthetic)",
                "Fictional Federated Civil Registry. Synthetic demonstration data; no real records.",
                List.of(
                        existingRegional(),
                        proposedNational(),
                        proposedWithAppealRemoved(),
                        proposedWithDutyViolation()));
    }

    /**
     * Builds the existing regional procedure version.
     *
     * <p>Fourteen steps, of which many place a person on the mechanical path: format checking,
     * evidence checking, identity matching and two clerical reads are all performed by staff.
     *
     * @return the existing version
     */
    public static ProcedureVersion existingRegional() {
        SequencedMap<String, ProcedureStep> steps = new LinkedHashMap<>();
        add(steps, STEP_INTAKE, "Receive correction request", StepKind.INTAKE,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.MECHANICAL),
                ReviewerRole.INTAKE_CLERK, false, 2);
        add(steps, STEP_FORMAT, "Check formatting and normalise fields", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.MECHANICAL),
                ReviewerRole.RECORDS_OFFICER, false, 2);
        add(steps, STEP_EVIDENCE, "Check evidence completeness", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.EVIDENCE_COMPLETENESS),
                ReviewerRole.RECORDS_OFFICER, false, 3);
        add(steps, STEP_CLERICAL_ONE, "First clerical read of the file", StepKind.CLERICAL_REVIEW,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.MECHANICAL),
                ReviewerRole.RECORDS_OFFICER, false, 2);
        // A second read of the same file by the same role. The Human Necessity Map records that this
        // adds no judgement, which is what makes its removal a safe reduction rather than a loss.
        add(steps, STEP_CLERICAL_TWO, "Second clerical read of the same file",
                StepKind.CLERICAL_REVIEW, DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.MECHANICAL),
                ReviewerRole.RECORDS_OFFICER, false, 2);
        add(steps, STEP_IDENTITY, "Match record fields across sources", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.IDENTITY_CONSISTENCY),
                ReviewerRole.RECORDS_OFFICER, false, 3);
        add(steps, STEP_LEGAL, "Review delegated authority", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.LEGAL_AUTHORITY),
                ReviewerRole.LEGAL_REVIEWER, false, 5);
        add(steps, STEP_ACCESSIBILITY, "Decide accessibility accommodation", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.ACCESSIBILITY),
                ReviewerRole.ACCESSIBILITY_REVIEWER, false, 4);
        // Prepared by hand in the regional procedure: an officer assembles the file each time.
        add(steps, STEP_PREPARE, "Prepare the decision file by hand", StepKind.CLERICAL_REVIEW,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.MECHANICAL),
                ReviewerRole.RECORDS_OFFICER, false, 3);
        add(steps, STEP_DECIDE, "Decide the correction", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.DISCRETIONARY),
                ReviewerRole.REGISTRY_SUPERVISOR, false, 5);
        add(steps, STEP_APPROVE, "Approve the decision", StepKind.APPROVAL,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.SEPARATION_OF_DUTIES),
                ReviewerRole.REGISTRY_SUPERVISOR, false, 4);
        add(steps, STEP_NOTIFY, "Notify the applicant and state appeal options",
                StepKind.NOTIFICATION, DecisionTier.AUTO_WITH_EXCEPTION,
                cats(RuleCategory.MECHANICAL), ReviewerRole.INTAKE_CLERK, true, 1);
        add(steps, STEP_APPEAL, "Adjudicate an appeal", StepKind.APPEAL,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.APPEAL_RIGHTS),
                ReviewerRole.APPEALS_ADJUDICATOR, true, 6);
        add(steps, STEP_RECORD, "Record the outcome for retention and audit", StepKind.RECORDING,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.RETENTION_AUDIT),
                ReviewerRole.DATA_STEWARD, false, 1);
        add(steps, STEP_END, "Procedure concluded", StepKind.TERMINAL, DecisionTier.AUTOMATE,
                cats(RuleCategory.MECHANICAL), ReviewerRole.NONE, false, 0);

        ProcedureGraph graph = new ProcedureGraph(
                steps,
                regionalTransitions(),
                STEP_INTAKE,
                List.of(
                        new ApprovalGate(GATE_LEGAL, STEP_LEGAL, ReviewerRole.LEGAL_REVIEWER, 1, false, true),
                        new ApprovalGate(GATE_DECIDE, STEP_DECIDE, ReviewerRole.REGISTRY_SUPERVISOR, 2, true, true),
                        new ApprovalGate(GATE_APPROVE, STEP_APPROVE, ReviewerRole.REGISTRY_SUPERVISOR, 3, true, true)),
                List.of(new SeparationOfDuty(
                        DUTY_PREPARE_APPROVE,
                        STEP_PREPARE,
                        STEP_APPROVE,
                        "The officer who prepares a correction file must not approve it")));

        return new ProcedureVersion(
                PROCEDURE_ID,
                VERSION_EXISTING,
                "Regional procedure in force",
                graph,
                DemoPolicy.PACK_ID,
                DemoPolicy.VERSION,
                "Human-heavy regional baseline: staff perform format, evidence and identity checks.");
    }

    private static List<Transition> regionalTransitions() {
        List<Transition> out = new ArrayList<>(nationalCommonTransitions());
        out.add(new Transition(STEP_CLERICAL_ONE, STEP_CLERICAL_TWO, "always"));
        out.add(new Transition(STEP_CLERICAL_TWO, STEP_IDENTITY, "always"));
        return List.copyOf(out);
    }

    private static List<Transition> nationalCommonTransitions() {
        return List.of(
                new Transition(STEP_INTAKE, STEP_FORMAT, "always"),
                new Transition(STEP_FORMAT, STEP_EVIDENCE, "always"),
                new Transition(STEP_EVIDENCE, STEP_CLERICAL_ONE, "always"),
                new Transition(STEP_IDENTITY, STEP_LEGAL, "delegated authority in question"),
                new Transition(STEP_IDENTITY, STEP_ACCESSIBILITY, "accommodation requested"),
                new Transition(STEP_IDENTITY, STEP_PREPARE, "no exception raised"),
                new Transition(STEP_LEGAL, STEP_PREPARE, "authority confirmed"),
                new Transition(STEP_ACCESSIBILITY, STEP_PREPARE, "accommodation decided"),
                new Transition(STEP_PREPARE, STEP_DECIDE, "always"),
                new Transition(STEP_DECIDE, STEP_APPROVE, "always"),
                new Transition(STEP_APPROVE, STEP_NOTIFY, "always"),
                new Transition(STEP_NOTIFY, STEP_APPEAL, "applicant appeals"),
                new Transition(STEP_NOTIFY, STEP_RECORD, "no appeal within the period"),
                new Transition(STEP_APPEAL, STEP_RECORD, "appeal concluded"),
                new Transition(STEP_RECORD, STEP_END, "always"));
    }

    /**
     * Builds the safe proposed national procedure version.
     *
     * <p>Mechanical steps become automated, the duplicated clerical read disappears, and every
     * mandatory human gate the Human Necessity Map requires is left in place.
     *
     * @return the safe proposed version
     */
    public static ProcedureVersion proposedNational() {
        return new ProcedureVersion(
                PROCEDURE_ID,
                VERSION_PROPOSED,
                "Proposed national digital procedure",
                nationalGraph(true, ReviewerRole.REGISTRY_SUPERVISOR),
                DemoPolicy.PACK_ID,
                DemoPolicy.VERSION,
                "Automates deterministic checks, keeps every mandatory human gate and the appeal route.");
    }

    public static ProcedureVersion proposedWithAppealRemoved() {
        return new ProcedureVersion(
                PROCEDURE_ID,
                VERSION_APPEAL_REMOVED,
                "Proposed national procedure without an appeal route",
                nationalGraph(false, ReviewerRole.REGISTRY_SUPERVISOR),
                DemoPolicy.PACK_ID,
                DemoPolicy.VERSION,
                "Deliberately unsafe: the appeal adjudication step is deleted.");
    }

    public static ProcedureVersion proposedWithDutyViolation() {
        return new ProcedureVersion(
                PROCEDURE_ID,
                VERSION_DUTY_VIOLATION,
                "Proposed national procedure with approval by the preparing role",
                nationalGraph(true, ReviewerRole.RECORDS_OFFICER),
                DemoPolicy.PACK_ID,
                DemoPolicy.VERSION,
                "Deliberately unsafe: approval is reassigned to the role that prepares the file.");
    }

    private static ProcedureGraph nationalGraph(boolean withAppeal, ReviewerRole approvingRole) {
        SequencedMap<String, ProcedureStep> steps = new LinkedHashMap<>();
        add(steps, STEP_INTAKE, "Receive correction request", StepKind.INTAKE,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.MECHANICAL),
                ReviewerRole.INTAKE_CLERK, false, 1);
        add(steps, STEP_FORMAT, "Normalise fields deterministically", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTOMATE, cats(RuleCategory.MECHANICAL), ReviewerRole.NONE, false, 0);
        add(steps, STEP_EVIDENCE, "Check evidence completeness", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.EVIDENCE_COMPLETENESS),
                ReviewerRole.RECORDS_OFFICER, false, 1);
        add(steps, STEP_CLERICAL_ONE, "Review exceptions raised by the automated checks",
                StepKind.CLERICAL_REVIEW, DecisionTier.AUTO_WITH_EXCEPTION,
                cats(RuleCategory.MECHANICAL), ReviewerRole.RECORDS_OFFICER, false, 1);
        add(steps, STEP_IDENTITY, "Match record fields across sources", StepKind.MECHANICAL_CHECK,
                DecisionTier.AUTO_WITH_EXCEPTION, cats(RuleCategory.IDENTITY_CONSISTENCY),
                ReviewerRole.RECORDS_OFFICER, false, 1);
        add(steps, STEP_LEGAL, "Review delegated authority", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.LEGAL_AUTHORITY),
                ReviewerRole.LEGAL_REVIEWER, false, 5);
        add(steps, STEP_ACCESSIBILITY, "Decide accessibility accommodation", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.ACCESSIBILITY),
                ReviewerRole.ACCESSIBILITY_REVIEWER, false, 4);
        add(steps, STEP_PREPARE, "Assemble the decision file automatically",
                StepKind.CLERICAL_REVIEW, DecisionTier.AUTO_WITH_EXCEPTION,
                cats(RuleCategory.MECHANICAL), ReviewerRole.RECORDS_OFFICER, false, 1);
        add(steps, STEP_DECIDE, "Decide the correction", StepKind.DECISION,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.DISCRETIONARY),
                ReviewerRole.REGISTRY_SUPERVISOR, false, 5);
        add(steps, STEP_APPROVE, "Approve the decision", StepKind.APPROVAL,
                DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.SEPARATION_OF_DUTIES),
                approvingRole, false, 4);
        add(steps, STEP_NOTIFY, "Notify the applicant and state appeal options",
                StepKind.NOTIFICATION, DecisionTier.AUTOMATE, cats(RuleCategory.MECHANICAL),
                ReviewerRole.NONE, withAppeal, 0);
        if (withAppeal) {
            add(steps, STEP_APPEAL, "Adjudicate an appeal", StepKind.APPEAL,
                    DecisionTier.HUMAN_REQUIRED, cats(RuleCategory.APPEAL_RIGHTS),
                    ReviewerRole.APPEALS_ADJUDICATOR, true, 6);
        }
        add(steps, STEP_RECORD, "Record the outcome for retention and audit", StepKind.RECORDING,
                DecisionTier.AUTOMATE, cats(RuleCategory.RETENTION_AUDIT), ReviewerRole.NONE, false, 0);
        add(steps, STEP_END, "Procedure concluded", StepKind.TERMINAL, DecisionTier.AUTOMATE,
                cats(RuleCategory.MECHANICAL), ReviewerRole.NONE, false, 0);

        List<Transition> transitions = new ArrayList<>(nationalCommonTransitions());
        // The duplicated clerical read is gone, so the first read leads straight to identity matching.
        transitions.add(new Transition(STEP_CLERICAL_ONE, STEP_IDENTITY, "always"));
        if (!withAppeal) {
            transitions.removeIf(t -> t.fromStepId().equals(STEP_APPEAL)
                    || t.toStepId().equals(STEP_APPEAL));
        }

        return new ProcedureGraph(
                steps,
                List.copyOf(transitions),
                STEP_INTAKE,
                List.of(
                        new ApprovalGate(GATE_LEGAL, STEP_LEGAL, ReviewerRole.LEGAL_REVIEWER, 1, false, true),
                        new ApprovalGate(GATE_DECIDE, STEP_DECIDE, ReviewerRole.REGISTRY_SUPERVISOR, 2, true, true),
                        new ApprovalGate(GATE_APPROVE, STEP_APPROVE, approvingRole, 3, true, true)),
                List.of(new SeparationOfDuty(
                        DUTY_PREPARE_APPROVE,
                        STEP_PREPARE,
                        STEP_APPROVE,
                        "The officer who prepares a correction file must not approve it")));
    }

    private static Set<RuleCategory> cats(RuleCategory... categories) {
        return categories.length == 0
                ? EnumSet.noneOf(RuleCategory.class)
                : EnumSet.copyOf(List.of(categories));
    }

    private static void add(
            SequencedMap<String, ProcedureStep> steps,
            String stepId,
            String title,
            StepKind kind,
            DecisionTier tier,
            Set<RuleCategory> categories,
            ReviewerRole role,
            boolean appealPath,
            int touchCost) {
        steps.put(stepId, new ProcedureStep(
                stepId, title, kind, tier, categories, role, appealPath, touchCost));
    }
}
