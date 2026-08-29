package com.bguzman.civlint.evaluation;

import com.bguzman.civlint.domain.CorrectionRequest;
import com.bguzman.civlint.domain.DecisionTier;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationCase.Scope;
import com.bguzman.civlint.domain.EvidenceItem;
import com.bguzman.civlint.domain.EvidenceType;
import com.bguzman.civlint.domain.RegistryRecord;
import com.bguzman.civlint.domain.RequestFlag;
import com.bguzman.civlint.domain.ReviewerRole;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * The fifteen fixed evaluation cases and their locked oracle.
 *
 * <p>The oracle in this class is the authority both architectures are scored against. It was fixed
 * before either runner existed and is not edited to accommodate a result; where a run disagrees with
 * it, the run is wrong or the oracle is challenged explicitly in
 * {@code docs/evaluation-methodology.md}.
 *
 * <p>Every applicant name, record identifier, region and document reference below is invented. None
 * describes a real person or a real record.
 */
public final class DemoCases {

    /** The number of evaluation cases, fixed by the evaluation design. */
    public static final int CASE_COUNT = 15;

    private DemoCases() {
        throw new AssertionError("No instances.");
    }

    public static List<EvaluationCase> cases() {
        return List.of(
                case01(), case02(), case03(), case04(), case05(),
                case06(), case07(), case08(), case09(), case10(),
                case11(), case12(), case13(), case14(), case15());
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture building blocks
    // ---------------------------------------------------------------------------------------------

    private static SequencedMap<String, String> fields(String... pairs) {
        SequencedMap<String, String> out = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static RegistryRecord record(boolean historical, String given, String surname) {
        return new RegistryRecord(
                "R.REGIONAL.001",
                "RG.NORTE",
                fields(
                        RegistryRecord.GIVEN_NAME, given,
                        RegistryRecord.SURNAME, surname,
                        RegistryRecord.DATE_OF_BIRTH, "1988-04-12",
                        RegistryRecord.REGIONAL_ID, "RG.NORTE.88.0412"),
                historical);
    }

    private static EvidenceItem evidence(
            String id,
            EvidenceType type,
            boolean certified,
            boolean legible,
            Map<String, String> claims) {
        return new EvidenceItem(
                id,
                type,
                "Regional Registry Office of Norte (synthetic)",
                "REF." + id,
                certified,
                legible,
                new TreeMap<>(claims));
    }

    private static EvidenceItem identityDocument(String reference) {
        return new EvidenceItem(
                "E.IDDOC",
                EvidenceType.IDENTITY_DOCUMENT,
                "Federated Identity Bureau (synthetic)",
                reference,
                true,
                true,
                new TreeMap<>());
    }

    private static CorrectionRequest request(
            String caseId,
            RegistryRecord current,
            SequencedMap<String, String> requested,
            List<EvidenceItem> evidence,
            Set<RequestFlag> flags) {
        return new CorrectionRequest(caseId, current, requested, evidence, flags);
    }

    private static Set<RequestFlag> noFlags() {
        return EnumSet.noneOf(RequestFlag.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Cases
    // ---------------------------------------------------------------------------------------------

    private static EvaluationCase case01() {
        String caseId = "CASE.01.COMPLETE";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Complete correction with valid evidence",
                "A well-formed request whose requested values already match the record, supported by a "
                        + "certified birth record extract and an identity document.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(
                                RegistryRecord.GIVEN_NAME, "María",
                                RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(
                                                RegistryRecord.GIVEN_NAME, "María",
                                                RegistryRecord.SURNAME, "Serrano-Vidal",
                                                RegistryRecord.DATE_OF_BIRTH, "1988-04-12")),
                                identityDocument("ID.2026.0001")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A single general-purpose agent is expected to reach the same answer; this case does "
                        + "not distinguish the two architectures.",
                "Every criterion is satisfied, so no finding is raised and the case is automated.",
                "Nothing is ambiguous, no evidence conflicts, and no substantive change is requested, "
                        + "so there is nothing for a person to decide.");
    }

    private static EvaluationCase case02() {
        String caseId = "CASE.02.DIACRITIC";
        RegistryRecord current = record(false, "Maria", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Diacritic mismatch with otherwise identical evidence",
                "The applicant asks for the accented form of a given name that the register holds "
                        + "unaccented; all other fields and all evidence agree.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.GIVEN_NAME, "María"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(
                                                RegistryRecord.GIVEN_NAME, "María",
                                                RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0002")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent may treat an accent change as a substantive name change and "
                        + "route it to a person, spending review time on a formatting difference.",
                "The comparator folds combining marks and reports NAME_EQUIV_DIACRITIC_FOLDED, so the "
                        + "case is automated with the applied normalisation named.",
                "Folding combining marks is a published, total transformation, so the two values are "
                        + "the same name written two ways.");
    }

    private static EvaluationCase case03() {
        String caseId = "CASE.03.WHITESPACE";
        RegistryRecord current = record(false, "María", "serrano-vidal  ");
        return new EvaluationCase(
                caseId,
                "Case and whitespace normalisation",
                "The register holds a surname in lower case with trailing whitespace; the applicant "
                        + "asks for the properly cased form.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0003")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent is likely to agree, though it may not state which "
                        + "normalisation it applied.",
                "The comparator reports NAME_EQUIV_WHITESPACE_CASE, the weakest normalisation that "
                        + "makes the values equal.",
                "Whitespace collapsing and case folding are deterministic and change no content.");
    }

    private static EvaluationCase case04() {
        String caseId = "CASE.04.DUPLICATE.CLERICAL";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Duplicate clerical approval in the existing process",
                "The existing regional procedure has a second clerical read that repeats the first "
                        + "with the same role and checklist. The proposed version removes it.",
                Scope.VERSION_COMPARISON,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.GIVEN_NAME, "María"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.GIVEN_NAME, "María")),
                                identityDocument("ID.2026.0004")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTO_WITH_EXCEPTION,
                ReviewerRole.REGISTRY_SUPERVISOR,
                List.of("HUMAN_GATE_SAFELY_REMOVED", "TIER_WEAKENED_WITHIN_POLICY"),
                "Without a Human Necessity Map a general-purpose agent has no approved basis for "
                        + "distinguishing this removal from an unsafe one, so it either blocks a safe "
                        + "improvement or permits removals it should not.",
                "The map's approved entry for the duplicated read authorises the removal, so it is "
                        + "recorded as HUMAN_GATE_SAFELY_REMOVED and no release-blocking finding is "
                        + "raised. Steps that lose all human involvement are surfaced for confirmation.",
                "The second read adds effort but no judgement, and separation of duties is preserved "
                        + "at preparation and approval, so removing it removes duplication rather than a "
                        + "safeguard.");
    }

    private static EvaluationCase case05() {
        String caseId = "CASE.05.MAPPING";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Regional-to-national identifier mapping",
                "The migration assigns a national identifier, and an authoritative national registry "
                        + "entry states the same value.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.NATIONAL_ID, "NAT.1988.004412"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.GIVEN_NAME, "María")),
                                evidence(
                                        "E.NATIONAL",
                                        EvidenceType.NATIONAL_REGISTRY_ENTRY,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.NATIONAL_ID, "NAT.1988.004412")),
                                identityDocument("ID.2026.0005")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent is likely to agree.",
                "The mapping is confirmed against an authoritative source and the regional identifier "
                        + "is unchanged, so the assignment is mechanical.",
                "An identifier assignment is mechanical exactly when an authoritative source already "
                        + "states the value; minting one without a source would not be.");
    }

    private static EvaluationCase case06() {
        String caseId = "CASE.06.HISTORICAL";
        RegistryRecord current = record(true, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Historical immutable identifier comparison",
                "A closed historical entry is compared against the request without being edited.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(
                                RegistryRecord.GIVEN_NAME, "María",
                                RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(
                                                RegistryRecord.GIVEN_NAME, "María",
                                                RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0006")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent may not notice that the entry is immutable, and so may not "
                        + "distinguish comparing it from editing it.",
                "Comparison of an immutable reference changes nothing, so the case is automated; had "
                        + "the request altered a field, CASE_HISTORICAL would have engaged.",
                "Reading a closed entry is mechanical. Editing one is forbidden, which is a different "
                        + "operation and is checked separately.");
    }

    private static EvaluationCase case07() {
        String caseId = "CASE.07.COMPOUND";
        RegistryRecord current = record(false, "María", "Serrano Vidal");
        return new EvaluationCase(
                caseId,
                "Compound surname with complete evidence",
                "The register holds a compound surname joined by a space; the applicant asks for the "
                        + "hyphenated form, with evidence agreeing.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0007")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent may treat a compound surname as unfamiliar and route it to a "
                        + "person even though the difference is only the joiner.",
                "Joiner normalisation reports NAME_EQUIV_COMPOUND_JOINER, so the case is automated.",
                "Hyphen and space are interchangeable joiners under a published rule, so the two "
                        + "values are the same surname.");
    }

    private static EvaluationCase case08() {
        String caseId = "CASE.08.CERTIFIED.NAMECHANGE";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Certified legal name change",
                "A substantive change of given name, accompanied by a certified name-change order.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.GIVEN_NAME, "Mariana"),
                        List.of(
                                evidence(
                                        "E.COURT",
                                        EvidenceType.COURT_NAME_CHANGE_ORDER,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana")),
                                identityDocument("ID.2026.0008")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTO_WITH_EXCEPTION,
                ReviewerRole.REGISTRY_SUPERVISOR,
                List.of("SUBSTANTIVE_CHANGE_REQUESTED"),
                "A general-purpose agent may either automate this because the paperwork looks complete "
                        + "or block it because the name changed, without distinguishing the two reasons.",
                "The change is recognised as substantive rather than cosmetic, and the certified order "
                        + "is confirmed present, so the normal path proceeds with the substantive change "
                        + "routed for confirmation.",
                "The order supplies the authority, so the change need not be decided afresh; but it is "
                        + "a real change of recorded content and must not be filed as a normalisation.");
    }

    private static EvaluationCase case09() {
        String caseId = "CASE.09.RENEWED.DOCUMENT";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Renewed document with a stable authoritative reference",
                "The applicant's identity document has been renewed and carries a new reference, while "
                        + "the authoritative registry entry is unchanged.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.REGIONAL",
                                        EvidenceType.REGIONAL_REGISTRY_ENTRY,
                                        true,
                                        true,
                                        Map.of(
                                                RegistryRecord.GIVEN_NAME, "María",
                                                RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.9009.RENEWED")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTOMATE,
                ReviewerRole.NONE,
                List.of(),
                "A general-purpose agent may treat the new document reference as a change requiring "
                        + "review.",
                "The document reference is not a record field, so its renewal engages no rule and the "
                        + "case is automated.",
                "A renewed document with a new reference does not change the record; the authoritative "
                        + "entry the record depends on is unchanged.");
    }

    private static EvaluationCase case10() {
        String caseId = "CASE.10.MISSING.NONCRITICAL";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Missing non-critical evidence",
                "An authoritative source supports the request but the supporting identity document is "
                        + "absent.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(evidence(
                                "E.BIRTH",
                                EvidenceType.BIRTH_RECORD_EXTRACT,
                                true,
                                true,
                                Map.of(RegistryRecord.SURNAME, "Serrano-Vidal"))),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.AUTO_WITH_EXCEPTION,
                ReviewerRole.RECORDS_OFFICER,
                List.of("EVIDENCE_INCOMPLETE"),
                "A general-purpose agent may reject the case outright for incomplete evidence rather "
                        + "than routing it as an exception.",
                "The missing item is identified by name and the case is routed to a records officer as "
                        + "an exception, leaving the normal path automated.",
                "The authoritative basis is present, so the case is not undecidable; what is missing is "
                        + "a supporting document, which is an exception rather than a refusal.");
    }

    private static EvaluationCase case11() {
        String caseId = "CASE.11.CONFLICT";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Conflicting authoritative records",
                "The birth record extract and the national registry entry state different surnames.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                evidence(
                                        "E.NATIONAL",
                                        EvidenceType.NATIONAL_REGISTRY_ENTRY,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Ortiz")),
                                identityDocument("ID.2026.0011")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.HUMAN_REQUIRED,
                ReviewerRole.REGISTRY_SUPERVISOR,
                List.of("AUTHORITATIVE_CONFLICT"),
                "A general-purpose agent may silently prefer one source — often the more recent — and "
                        + "produce a confident answer with no basis for the preference.",
                "The conflict is detected, both sources are cited, and the case is routed to a "
                        + "supervisor with a counterexample naming the two values.",
                "There is no mechanical basis for preferring one authoritative source over another, so "
                        + "the choice is a judgement and must be made by a person.");
    }

    private static EvaluationCase case12() {
        String caseId = "CASE.12.STRUCTURE";
        RegistryRecord current = record(false, "Ana Serrano", "Vidal");
        return new EvaluationCase(
                caseId,
                "Name structure the comparator cannot resolve",
                "The request adds a name part, which is neither a formatting difference nor a "
                        + "straightforward replacement.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.GIVEN_NAME, "Ana Serrano Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.GIVEN_NAME, "Ana Serrano Vidal")),
                                identityDocument("ID.2026.0012")),
                        noFlags()),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.HUMAN_REQUIRED,
                ReviewerRole.REGISTRY_SUPERVISOR,
                List.of("NAME_UNDECIDABLE_PART_STRUCTURE"),
                "A general-purpose agent must answer equal or different, and either answer is a guess "
                        + "about a name structure the rules do not cover.",
                "The comparator abstains, the abstention is escalated to a human decision, and the "
                        + "finding names the limitation rather than the applicant.",
                "The limitation is in CivLint's normaliser, not in the name. A comparator that cannot "
                        + "resolve a structure must decline rather than assert equality or difference.");
    }

    private static EvaluationCase case13() {
        String caseId = "CASE.13.ACCESSIBILITY";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Accessibility accommodation with alternative evidence",
                "The applicant requests an accommodation and offers an attestation in place of a "
                        + "document they cannot obtain.",
                Scope.CASE_LEVEL,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                evidence(
                                        "E.ALT",
                                        EvidenceType.ALTERNATIVE_ATTESTATION,
                                        false,
                                        true,
                                        Map.of())),
                        EnumSet.of(
                                RequestFlag.ACCESSIBILITY_ACCOMMODATION_REQUESTED,
                                RequestFlag.ALTERNATIVE_EVIDENCE_OFFERED)),
                DemoProcedures.VERSION_PROPOSED,
                DecisionTier.HUMAN_REQUIRED,
                ReviewerRole.ACCESSIBILITY_REVIEWER,
                List.of(
                        "EVIDENCE_INCOMPLETE",
                        "FLAG_ACCESSIBILITY_ACCOMMODATION_REQUESTED",
                        "FLAG_ALTERNATIVE_EVIDENCE_OFFERED"),
                "A general-purpose agent may route the case to whichever reviewer it names first, or "
                        + "treat the missing document as a refusal.",
                "Both accessibility rules engage and the case is routed specifically to the "
                        + "accessibility reviewer, with the missing document reported separately.",
                "An accommodation is a departure from the standard path, and only a person can decide "
                        + "whether the alternative evidence offered is adequate for this applicant.");
    }

    private static EvaluationCase case14() {
        String caseId = "CASE.14.APPEAL.REMOVED";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Proposed version removes an appeal path",
                "A proposed version deletes the appeal adjudication step and the appeal wording in the "
                        + "notification.",
                Scope.VERSION_COMPARISON,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0014")),
                        noFlags()),
                DemoProcedures.VERSION_APPEAL_REMOVED,
                DecisionTier.RELEASE_BLOCKED,
                ReviewerRole.APPEALS_ADJUDICATOR,
                List.of(
                        "APPEAL_ROUTE_REMOVED",
                        "HUMAN_GATE_REMOVED",
                        "HUMAN_GATE_SAFELY_REMOVED",
                        "TIER_WEAKENED_WITHIN_POLICY"),
                "Without a structural verifier there is nothing that compares appeal routes across "
                        + "versions, so a general-purpose agent reviewing changed steps has no reliable "
                        + "way to notice that the route is gone.",
                "The appeal route is compared across versions, both the deleted step and the lost "
                        + "notification wording are reported, and the release is blocked.",
                "An appeal is the applicant's remedy against the procedure itself. Removing it cannot "
                        + "be offset by any efficiency gain, so it blocks the release outright.");
    }

    private static EvaluationCase case15() {
        String caseId = "CASE.15.DUTY.VIOLATION";
        RegistryRecord current = record(false, "María", "Serrano-Vidal");
        return new EvaluationCase(
                caseId,
                "Proposed version violates separation of duties",
                "A proposed version reassigns approval to the same role that prepares the file.",
                Scope.VERSION_COMPARISON,
                request(
                        caseId,
                        current,
                        fields(RegistryRecord.SURNAME, "Serrano-Vidal"),
                        List.of(
                                evidence(
                                        "E.BIRTH",
                                        EvidenceType.BIRTH_RECORD_EXTRACT,
                                        true,
                                        true,
                                        Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                                identityDocument("ID.2026.0015")),
                        noFlags()),
                DemoProcedures.VERSION_DUTY_VIOLATION,
                DecisionTier.RELEASE_BLOCKED,
                ReviewerRole.LEGAL_REVIEWER,
                List.of(
                        "HUMAN_GATE_SAFELY_REMOVED",
                        "SEPARATION_OF_DUTIES_VIOLATED",
                        "TIER_WEAKENED_WITHIN_POLICY"),
                "The change looks innocuous step by step: every step still exists and every step still "
                        + "has a human. A reviewer comparing steps in isolation sees nothing wrong.",
                "The constraint is evaluated against the roles the proposed version actually assigns, "
                        + "the violation is reported with both steps named, and the release is blocked.",
                "Separation of duties is usually lost by reassignment rather than by deletion, so the "
                        + "check must read the roles in force rather than the presence of the rule.");
    }
}
