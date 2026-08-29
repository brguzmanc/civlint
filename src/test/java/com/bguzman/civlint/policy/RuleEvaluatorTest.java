package com.bguzman.civlint.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bguzman.civlint.domain.CorrectionRequest;
import com.bguzman.civlint.domain.CriterionResult;
import com.bguzman.civlint.domain.EvidenceItem;
import com.bguzman.civlint.domain.EvidenceType;
import com.bguzman.civlint.domain.RegistryRecord;
import com.bguzman.civlint.domain.RequestFlag;
import com.bguzman.civlint.domain.RuleCriterion;
import com.bguzman.civlint.support.CanonicalJson;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verifies each policy criterion, with particular attention to the distinction between "checked and
 * failed" and "could not be checked".
 */
class RuleEvaluatorTest {

    private static RegistryRecord record(String given, String surname, boolean historical) {
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put(RegistryRecord.GIVEN_NAME, given);
        fields.put(RegistryRecord.SURNAME, surname);
        fields.put(RegistryRecord.REGIONAL_ID, "RG.X.1");
        return new RegistryRecord("R.X", "RG.X", fields, historical);
    }

    private static EvidenceItem item(String id, EvidenceType type, boolean certified,
            boolean legible, Map<String, String> claims) {
        return new EvidenceItem(id, type, "Office", "REF." + id, certified, legible,
                new TreeMap<>(claims));
    }

    private static CorrectionRequest request(RegistryRecord record, Map<String, String> requested,
            List<EvidenceItem> evidence, Set<RequestFlag> flags) {
        return new CorrectionRequest("CASE.X", record, new TreeMap<>(requested), evidence, flags);
    }

    private static CorrectionRequest simple(List<EvidenceItem> evidence) {
        return request(record("María", "Serrano", false), Map.of(), evidence,
                EnumSet.noneOf(RequestFlag.class));
    }

    @Test
    @DisplayName("EvidencePresent is met when an acceptable usable item exists")
    void evidencePresentMet() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.EvidencePresent(EnumSet.of(EvidenceType.BIRTH_RECORD_EXTRACT)),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true, Map.of()))));
        assertThat(result.engaged()).isFalse();
        assertThat(result).isInstanceOf(CriterionResult.Met.class);
    }

    @Test
    @DisplayName("EvidencePresent is unmet when nothing acceptable was supplied")
    void evidencePresentUnmet() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.EvidencePresent(EnumSet.of(EvidenceType.BIRTH_RECORD_EXTRACT)),
                simple(List.of(item("E.1", EvidenceType.SWORN_DECLARATION, true, true, Map.of()))));
        assertThat(result).isInstanceOf(CriterionResult.Unmet.class);
        assertThat(((CriterionResult.Unmet) result).code()).isEqualTo("EVIDENCE_ABSENT");
        assertThat(((CriterionResult.Unmet) result).references()).isNotEmpty();
    }

    @Test
    @DisplayName("EvidencePresent abstains when an acceptable item exists but is unusable")
    void evidencePresentAbstains() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.EvidencePresent(EnumSet.of(EvidenceType.BIRTH_RECORD_EXTRACT)),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, false, Map.of()))));
        assertThat(result).isInstanceOf(CriterionResult.Abstain.class);
        assertThat(((CriterionResult.Abstain) result).code()).isEqualTo("EVIDENCE_NOT_USABLE");
    }

    @Test
    @DisplayName("EvidenceAllPresent names every missing type")
    void evidenceAllPresent() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.EvidenceAllPresent(EnumSet.of(
                        EvidenceType.BIRTH_RECORD_EXTRACT, EvidenceType.IDENTITY_DOCUMENT)),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true, Map.of()))));
        assertThat(result).isInstanceOf(CriterionResult.Unmet.class);
        assertThat(((CriterionResult.Unmet) result).message()).contains("Identity document");
        assertThat(((CriterionResult.Unmet) result).code()).isEqualTo("EVIDENCE_INCOMPLETE");
    }

    @Test
    @DisplayName("EvidenceUsable abstains and explains whether the problem is legibility")
    void evidenceUsable() {
        CriterionResult illegible = RuleEvaluator.evaluate(
                new RuleCriterion.EvidenceUsable(),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, false, Map.of()))));
        assertThat(illegible).isInstanceOf(CriterionResult.Abstain.class);
        assertThat(((CriterionResult.Abstain) illegible).message()).contains("could not be read");

        CriterionResult uncertified = RuleEvaluator.evaluate(
                new RuleCriterion.EvidenceUsable(),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, false, true, Map.of()))));
        assertThat(uncertified).isInstanceOf(CriterionResult.Abstain.class);
        assertThat(((CriterionResult.Abstain) uncertified).message()).contains("not certified");

        CriterionResult fine = RuleEvaluator.evaluate(
                new RuleCriterion.EvidenceUsable(),
                simple(List.of(item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true, Map.of()))));
        assertThat(fine.engaged()).isFalse();
    }

    @Test
    @DisplayName("NoAuthoritativeConflict ignores formatting differences between sources")
    void conflictIgnoresFormatting() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.NoAuthoritativeConflict(List.of(RegistryRecord.SURNAME)),
                simple(List.of(
                        item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true,
                                Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                        item("E.2", EvidenceType.NATIONAL_REGISTRY_ENTRY, true, true,
                                Map.of(RegistryRecord.SURNAME, "serrano vidal")))));
        assertThat(result.engaged()).as("a joiner and case difference is not a conflict").isFalse();
    }

    @Test
    @DisplayName("NoAuthoritativeConflict reports a genuine disagreement with both values")
    void conflictReported() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.NoAuthoritativeConflict(List.of(RegistryRecord.SURNAME)),
                simple(List.of(
                        item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true,
                                Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                        item("E.2", EvidenceType.NATIONAL_REGISTRY_ENTRY, true, true,
                                Map.of(RegistryRecord.SURNAME, "Serrano-Ortiz")))));
        assertThat(result).isInstanceOf(CriterionResult.Unmet.class);
        CriterionResult.Unmet unmet = (CriterionResult.Unmet) result;
        assertThat(unmet.code()).isEqualTo("AUTHORITATIVE_CONFLICT");
        assertThat(unmet.message()).contains("Serrano-Vidal").contains("Serrano-Ortiz");
        assertThat(unmet.message()).contains("not a mechanical act");
        assertThat(unmet.references()).hasSize(3);
    }

    @Test
    @DisplayName("a non-authoritative source disagreeing is not a conflict")
    void nonAuthoritativeDisagreement() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.NoAuthoritativeConflict(List.of(RegistryRecord.SURNAME)),
                simple(List.of(
                        item("E.1", EvidenceType.BIRTH_RECORD_EXTRACT, true, true,
                                Map.of(RegistryRecord.SURNAME, "Serrano-Vidal")),
                        item("E.2", EvidenceType.SWORN_DECLARATION, false, true,
                                Map.of(RegistryRecord.SURNAME, "Something-Else")))));
        assertThat(result.engaged()).isFalse();
    }

    @Test
    @DisplayName("NameChangeMechanicallyResolvable distinguishes formatting, substance and abstention")
    void nameChange() {
        RuleCriterion criterion =
                new RuleCriterion.NameChangeMechanicallyResolvable(List.of(RegistryRecord.GIVEN_NAME));

        CriterionResult formatting = RuleEvaluator.evaluate(criterion,
                request(record("Maria", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "María"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(formatting.engaged()).isFalse();

        CriterionResult substantive = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(substantive).isInstanceOf(CriterionResult.Unmet.class);
        assertThat(((CriterionResult.Unmet) substantive).code())
                .isEqualTo("SUBSTANTIVE_CHANGE_REQUESTED");

        CriterionResult abstained = RuleEvaluator.evaluate(criterion,
                request(record("Ana Serrano", "Vidal", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Ana Serrano Vidal"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(abstained).isInstanceOf(CriterionResult.Abstain.class);
        assertThat(((CriterionResult.Abstain) abstained).code())
                .isEqualTo("NAME_UNDECIDABLE_PART_STRUCTURE");
    }

    @Test
    @DisplayName("IdentifierMappingConsistent requires an authoritative source for the target")
    void mapping() {
        RuleCriterion criterion = new RuleCriterion.IdentifierMappingConsistent(
                RegistryRecord.REGIONAL_ID, RegistryRecord.NATIONAL_ID);

        CriterionResult unsupported = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.NATIONAL_ID, "NAT.1"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Unmet) unsupported).code()).isEqualTo("MAPPING_UNSUPPORTED");

        CriterionResult mismatch = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.NATIONAL_ID, "NAT.1"),
                        List.of(item("E.1", EvidenceType.NATIONAL_REGISTRY_ENTRY, true, true,
                                Map.of(RegistryRecord.NATIONAL_ID, "NAT.2"))),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Unmet) mismatch).code()).isEqualTo("MAPPING_INCONSISTENT");

        CriterionResult consistent = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.NATIONAL_ID, "NAT.1"),
                        List.of(item("E.1", EvidenceType.NATIONAL_REGISTRY_ENTRY, true, true,
                                Map.of(RegistryRecord.NATIONAL_ID, "NAT.1"))),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(consistent.engaged()).isFalse();

        CriterionResult sourceAltered = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.NATIONAL_ID, "NAT.1",
                                RegistryRecord.REGIONAL_ID, "RG.X.CHANGED"),
                        List.of(item("E.1", EvidenceType.NATIONAL_REGISTRY_ENTRY, true, true,
                                Map.of(RegistryRecord.NATIONAL_ID, "NAT.1"))),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Unmet) sourceAltered).code()).isEqualTo("MAPPING_SOURCE_ALTERED");
    }

    @Test
    @DisplayName("HistoricalRecordNotMutated permits comparison and refuses editing")
    void historical() {
        RuleCriterion criterion = new RuleCriterion.HistoricalRecordNotMutated();

        CriterionResult reading = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", true),
                        Map.of(RegistryRecord.GIVEN_NAME, "María"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(reading.engaged()).isFalse();

        CriterionResult editing = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", true),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Unmet) editing).code()).isEqualTo("HISTORICAL_RECORD_MUTATED");
        assertThat(((CriterionResult.Unmet) editing).message()).contains("new entry that references it");

        CriterionResult mutableRecord = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(mutableRecord.engaged()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RequestFlag.class)
    @DisplayName("FlagAbsent engages exactly when the flag is set")
    void flagAbsent(RequestFlag flag) {
        RuleCriterion criterion = new RuleCriterion.FlagAbsent(flag);

        CriterionResult without = RuleEvaluator.evaluate(criterion, simple(List.of()));
        assertThat(without.engaged()).isFalse();

        CriterionResult with = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false), Map.of(), List.of(), EnumSet.of(flag)));
        assertThat(with).isInstanceOf(CriterionResult.Unmet.class);
        assertThat(((CriterionResult.Unmet) with).code()).isEqualTo("FLAG_" + flag.name());
    }

    @Test
    @DisplayName("CertifiedOrderRequiredForSubstantiveChange only engages for a real change")
    void certifiedOrder() {
        RuleCriterion criterion = new RuleCriterion.CertifiedOrderRequiredForSubstantiveChange(
                EvidenceType.COURT_NAME_CHANGE_ORDER);

        CriterionResult noChange = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "María"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(noChange.engaged()).isFalse();

        CriterionResult missing = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"), List.of(),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Unmet) missing).code()).isEqualTo("CERTIFIED_ORDER_MISSING");

        CriterionResult present = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"),
                        List.of(item("E.1", EvidenceType.COURT_NAME_CHANGE_ORDER, true, true, Map.of())),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(present.engaged()).isFalse();

        CriterionResult unusable = RuleEvaluator.evaluate(criterion,
                request(record("María", "Serrano", false),
                        Map.of(RegistryRecord.GIVEN_NAME, "Mariana"),
                        List.of(item("E.1", EvidenceType.COURT_NAME_CHANGE_ORDER, false, true, Map.of())),
                        EnumSet.noneOf(RequestFlag.class)));
        assertThat(((CriterionResult.Abstain) unusable).code()).isEqualTo("CERTIFIED_ORDER_NOT_USABLE");
    }

    @Test
    @DisplayName("a structural invariant is reported met at case level, not silently mishandled")
    void structuralInvariantAtCaseLevel() {
        CriterionResult result = RuleEvaluator.evaluate(
                new RuleCriterion.StructuralInvariant(
                        RuleCriterion.StructuralInvariant.Invariant.APPEAL_ROUTE_PRESERVED),
                simple(List.of()));
        assertThat(result.engaged()).isFalse();
    }

    @Test
    @DisplayName("every criterion summary and canonical form is populated")
    void criteriaAreDescribable() {
        List<RuleCriterion> all = List.of(
                new RuleCriterion.EvidencePresent(EnumSet.of(EvidenceType.BIRTH_RECORD_EXTRACT)),
                new RuleCriterion.EvidenceAllPresent(EnumSet.of(EvidenceType.IDENTITY_DOCUMENT)),
                new RuleCriterion.EvidenceUsable(),
                new RuleCriterion.NoAuthoritativeConflict(List.of(RegistryRecord.SURNAME)),
                new RuleCriterion.NameChangeMechanicallyResolvable(List.of(RegistryRecord.GIVEN_NAME)),
                new RuleCriterion.IdentifierMappingConsistent("A", "B"),
                new RuleCriterion.HistoricalRecordNotMutated(),
                new RuleCriterion.FlagAbsent(RequestFlag.APPEAL_REQUESTED),
                new RuleCriterion.CertifiedOrderRequiredForSubstantiveChange(
                        EvidenceType.COURT_NAME_CHANGE_ORDER),
                new RuleCriterion.StructuralInvariant(
                        RuleCriterion.StructuralInvariant.Invariant.NO_CYCLES));
        all.forEach(criterion -> {
            assertThat(criterion.summary()).as("%s has a summary", criterion).isNotBlank();
            assertThat(CanonicalJson.write(criterion.toJson()))
                    .as("%s has a canonical form", criterion)
                    .contains("criterion");
        });
    }

    @Test
    @DisplayName("empty criterion collections are rejected at construction")
    void emptyCollections() {
        assertThatThrownBy(() -> new RuleCriterion.EvidencePresent(EnumSet.noneOf(EvidenceType.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleCriterion.EvidenceAllPresent(EnumSet.noneOf(EvidenceType.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleCriterion.NoAuthoritativeConflict(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleCriterion.NameChangeMechanicallyResolvable(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null arguments are rejected")
    void nulls() {
        assertThatThrownBy(() -> RuleEvaluator.evaluate((RuleCriterion) null, simple(List.of())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleEvaluator.evaluate(new RuleCriterion.EvidenceUsable(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
