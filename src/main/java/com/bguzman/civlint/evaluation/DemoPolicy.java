package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvidenceType;
import com.bguzman.civlint.domain.PolicyPack;
import com.bguzman.civlint.domain.PolicyRule;
import com.bguzman.civlint.domain.RegistryRecord;
import com.bguzman.civlint.domain.RequestFlag;
import com.bguzman.civlint.domain.ReviewerRole;
import com.bguzman.civlint.domain.RuleCategory;
import com.bguzman.civlint.domain.RuleCriterion;
import com.bguzman.civlint.domain.RuleCriterion.StructuralInvariant.Invariant;
import com.bguzman.civlint.domain.Severity;
import java.util.EnumSet;
import java.util.List;

/**
 * The approved synthetic policy pack for the fictional Federated Civil Registry.
 *
 * <p>Every citation in {@code policySource} is invented. None refers to a real statute, regulation,
 * directive or administrative instruction of any real jurisdiction.
 *
 * <p>Rule identifiers are chosen so that their sort order is meaningful: because the verifier breaks
 * ties by lowest finding identifier, and finding identifiers embed the rule identifier, the ordering
 * decides which reviewer role is named when several rules reach the same tier. Appeal rights sort
 * before other structural rules for that reason.
 */
public final class DemoPolicy {

    public static final String PACK_ID = "FCR.NAMECORR.POLICY";

    public static final String VERSION = "2026.08.1";

    private DemoPolicy() {
        throw new AssertionError("No instances.");
    }

    public static PolicyPack pack() {
        return new PolicyPack(
                PACK_ID,
                VERSION,
                "Federated Civil Registry name and record correction policy (synthetic)",
                "Fictional Federated Civil Registry. Synthetic demonstration policy; not real law.",
                List.of(
                        // --- Structural invariants -------------------------------------------------
                        new PolicyRule(
                                "R.APPEAL.PRESERVED",
                                RuleCategory.APPEAL_RIGHTS,
                                "An existing appeal route must survive a procedure change",
                                "Synthetic FCR-AR-1",
                                new RuleCriterion.StructuralInvariant(Invariant.APPEAL_ROUTE_PRESERVED),
                                DecisionTier.RELEASE_BLOCKED,
                                ReviewerRole.APPEALS_ADJUDICATOR,
                                Severity.CRITICAL,
                                true,
                                "APPEAL_PRESERVED"),
                        new PolicyRule(
                                "R.DUTY.SEPARATION",
                                RuleCategory.SEPARATION_OF_DUTIES,
                                "Preparation and approval must be performed by different roles",
                                "Synthetic FCR-SD-1",
                                new RuleCriterion.StructuralInvariant(
                                        Invariant.SEPARATION_OF_DUTIES_HELD),
                                DecisionTier.RELEASE_BLOCKED,
                                ReviewerRole.LEGAL_REVIEWER,
                                Severity.CRITICAL,
                                true,
                                "DUTY_SEPARATION"),
                        new PolicyRule(
                                "R.HUMANGATE.PRESERVED",
                                RuleCategory.DISCRETIONARY,
                                "A mandatory human gate must not be removed",
                                "Synthetic FCR-HG-1",
                                new RuleCriterion.StructuralInvariant(Invariant.HUMAN_GATE_PRESERVED),
                                DecisionTier.RELEASE_BLOCKED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.CRITICAL,
                                true,
                                "HUMANGATE_PRESERVED"),
                        new PolicyRule(
                                "R.ORDER.APPROVAL",
                                RuleCategory.APPROVAL_ORDERING,
                                "Approval gates must be reachable only in their declared order",
                                "Synthetic FCR-AO-1",
                                new RuleCriterion.StructuralInvariant(Invariant.APPROVAL_ORDER_HELD),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.HIGH,
                                false,
                                "ORDER_APPROVAL"),
                        new PolicyRule(
                                "R.STRUCT.CYCLES",
                                RuleCategory.APPROVAL_ORDERING,
                                "The step graph must not contain a cycle",
                                "Synthetic FCR-ST-2",
                                new RuleCriterion.StructuralInvariant(Invariant.NO_CYCLES),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.MEDIUM,
                                false,
                                "STRUCT_CYCLES"),
                        new PolicyRule(
                                "R.STRUCT.REACHABLE",
                                RuleCategory.APPROVAL_ORDERING,
                                "Every declared step must be reachable from intake",
                                "Synthetic FCR-ST-1",
                                new RuleCriterion.StructuralInvariant(Invariant.ALL_STEPS_REACHABLE),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.MEDIUM,
                                false,
                                "STRUCT_REACHABLE"),
                        new PolicyRule(
                                "R.STRUCT.TERMINAL",
                                RuleCategory.APPROVAL_ORDERING,
                                "A concluding step must be reachable from every reachable step",
                                "Synthetic FCR-ST-3",
                                new RuleCriterion.StructuralInvariant(Invariant.TERMINAL_REACHABLE),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.HIGH,
                                false,
                                "STRUCT_TERMINAL"),
                        new PolicyRule(
                                "R.STRUCT.TIERPERMITTED",
                                RuleCategory.LEGAL_AUTHORITY,
                                "A step must not claim a tier its policy category forbids",
                                "Synthetic FCR-LA-2",
                                new RuleCriterion.StructuralInvariant(
                                        Invariant.TIER_PERMITTED_FOR_CATEGORY),
                                DecisionTier.RELEASE_BLOCKED,
                                ReviewerRole.LEGAL_REVIEWER,
                                Severity.CRITICAL,
                                true,
                                "STRUCT_TIERPERMITTED"),
                        new PolicyRule(
                                "R.STRUCT.POLICYBINDING",
                                RuleCategory.RETENTION_AUDIT,
                                "A procedure version must be verified against the policy it declares",
                                "Synthetic FCR-RA-1",
                                new RuleCriterion.StructuralInvariant(
                                        Invariant.POLICY_BINDING_CONSISTENT),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.DATA_STEWARD,
                                Severity.HIGH,
                                false,
                                "STRUCT_POLICYBINDING"),

                        // --- Case-level rules ------------------------------------------------------
                        new PolicyRule(
                                "R.CASE.ACCESSIBILITY",
                                RuleCategory.ACCESSIBILITY,
                                "An accessibility accommodation must be decided by a person",
                                "Synthetic FCR-AC-1",
                                new RuleCriterion.FlagAbsent(
                                        RequestFlag.ACCESSIBILITY_ACCOMMODATION_REQUESTED),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.ACCESSIBILITY_REVIEWER,
                                Severity.MEDIUM,
                                false,
                                "CASE_ACCESSIBILITY"),
                        new PolicyRule(
                                "R.CASE.ALTEVIDENCE",
                                RuleCategory.ACCESSIBILITY,
                                "Alternative evidence must be assessed by a person",
                                "Synthetic FCR-AC-2",
                                new RuleCriterion.FlagAbsent(RequestFlag.ALTERNATIVE_EVIDENCE_OFFERED),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.ACCESSIBILITY_REVIEWER,
                                Severity.MEDIUM,
                                false,
                                "CASE_ALTEVIDENCE"),
                        new PolicyRule(
                                "R.CASE.APPEAL",
                                RuleCategory.APPEAL_RIGHTS,
                                "An appeal must be heard by an independent adjudicator",
                                "Synthetic FCR-AR-2",
                                new RuleCriterion.FlagAbsent(RequestFlag.APPEAL_REQUESTED),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.APPEALS_ADJUDICATOR,
                                Severity.MEDIUM,
                                false,
                                "CASE_APPEAL"),
                        new PolicyRule(
                                "R.CASE.CONFLICT",
                                RuleCategory.IDENTITY_CONSISTENCY,
                                "Conflicting authoritative records must be resolved by a person",
                                "Synthetic FCR-IC-1",
                                new RuleCriterion.NoAuthoritativeConflict(
                                        List.of(
                                                RegistryRecord.GIVEN_NAME,
                                                RegistryRecord.SURNAME,
                                                RegistryRecord.DATE_OF_BIRTH)),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.HIGH,
                                false,
                                "CASE_CONFLICT"),
                        new PolicyRule(
                                "R.CASE.CONTESTED",
                                RuleCategory.DISCRETIONARY,
                                "A contested record must be decided by a person",
                                "Synthetic FCR-DJ-1",
                                new RuleCriterion.FlagAbsent(RequestFlag.CONTESTED_BY_THIRD_PARTY),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.HIGH,
                                false,
                                "CASE_CONTESTED"),
                        new PolicyRule(
                                "R.CASE.DELEGATION",
                                RuleCategory.LEGAL_AUTHORITY,
                                "A claim of delegated authority must be reviewed by a legal reviewer",
                                "Synthetic FCR-LA-1",
                                new RuleCriterion.FlagAbsent(RequestFlag.DELEGATED_AUTHORITY_CLAIMED),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.LEGAL_REVIEWER,
                                Severity.HIGH,
                                false,
                                "CASE_DELEGATION"),
                        new PolicyRule(
                                "R.CASE.EVIDENCE.AUTHORITATIVE",
                                RuleCategory.EVIDENCE_COMPLETENESS,
                                "At least one authoritative source must support the request",
                                "Synthetic FCR-EC-1",
                                new RuleCriterion.EvidencePresent(EnumSet.of(
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        EvidenceType.REGIONAL_REGISTRY_ENTRY,
                                        EvidenceType.NATIONAL_REGISTRY_ENTRY,
                                        EvidenceType.COURT_NAME_CHANGE_ORDER)),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.HIGH,
                                false,
                                "CASE_EVIDENCE_AUTHORITATIVE"),
                        new PolicyRule(
                                "R.CASE.EVIDENCE.SUPPORTING",
                                RuleCategory.EVIDENCE_COMPLETENESS,
                                "A photographic identity document must accompany the request",
                                "Synthetic FCR-EC-2",
                                new RuleCriterion.EvidenceAllPresent(
                                        EnumSet.of(EvidenceType.IDENTITY_DOCUMENT)),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.LOW,
                                false,
                                "CASE_EVIDENCE_SUPPORTING"),
                        new PolicyRule(
                                "R.CASE.EVIDENCE.USABLE",
                                RuleCategory.EVIDENCE_COMPLETENESS,
                                "Supplied evidence must be legible and adequately certified",
                                "Synthetic FCR-EC-3",
                                new RuleCriterion.EvidenceUsable(),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.MEDIUM,
                                false,
                                "CASE_EVIDENCE_USABLE"),
                        new PolicyRule(
                                "R.CASE.HISTORICAL",
                                RuleCategory.RETENTION_AUDIT,
                                "A closed historical entry must not be edited in place",
                                "Synthetic FCR-RA-2",
                                new RuleCriterion.HistoricalRecordNotMutated(),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.DATA_STEWARD,
                                Severity.HIGH,
                                false,
                                "CASE_HISTORICAL"),
                        new PolicyRule(
                                "R.CASE.MAPPING",
                                RuleCategory.IDENTITY_CONSISTENCY,
                                "A national identifier must be supported by an authoritative source",
                                "Synthetic FCR-IC-2",
                                new RuleCriterion.IdentifierMappingConsistent(
                                        RegistryRecord.REGIONAL_ID, RegistryRecord.NATIONAL_ID),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.RECORDS_OFFICER,
                                Severity.HIGH,
                                false,
                                "CASE_MAPPING"),
                        new PolicyRule(
                                "R.CASE.NAMECHANGE",
                                RuleCategory.IDENTITY_CONSISTENCY,
                                "A change beyond formatting is a substantive change of record content",
                                "Synthetic FCR-IC-3",
                                new RuleCriterion.NameChangeMechanicallyResolvable(
                                        List.of(RegistryRecord.GIVEN_NAME, RegistryRecord.SURNAME)),
                                DecisionTier.AUTO_WITH_EXCEPTION,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.MEDIUM,
                                false,
                                "CASE_NAMECHANGE"),
                        new PolicyRule(
                                "R.CASE.ORDER.CERTIFIED",
                                RuleCategory.LEGAL_AUTHORITY,
                                "A substantive name change requires a certified order",
                                "Synthetic FCR-LA-3",
                                new RuleCriterion.CertifiedOrderRequiredForSubstantiveChange(
                                        EvidenceType.COURT_NAME_CHANGE_ORDER),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.LEGAL_REVIEWER,
                                Severity.HIGH,
                                false,
                                "CASE_ORDER_CERTIFIED"),
                        new PolicyRule(
                                "R.CASE.UNRECOGNISED",
                                RuleCategory.DISCRETIONARY,
                                "A case type the policy does not recognise must be decided by a person",
                                "Synthetic FCR-DJ-2",
                                new RuleCriterion.FlagAbsent(RequestFlag.UNRECOGNISED_CASE_TYPE),
                                DecisionTier.HUMAN_REQUIRED,
                                ReviewerRole.REGISTRY_SUPERVISOR,
                                Severity.HIGH,
                                false,
                                "CASE_UNRECOGNISED")));
    }
}
