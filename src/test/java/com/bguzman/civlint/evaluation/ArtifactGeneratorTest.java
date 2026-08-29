package com.bguzman.civlint.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bguzman.civlint.agents.ReplayAgentAdapter;
import com.bguzman.civlint.agents.AgentDefinition;
import com.bguzman.civlint.domain.AgentTrace;
import com.bguzman.civlint.domain.EvaluationCase;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.Finding;
import com.bguzman.civlint.domain.MetricResult;
import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Generates the checked-in evaluation artifacts and asserts that regenerating them changes nothing.
 *
 * <p>The artifacts are produced from an actual run rather than written by hand, so
 * {@code evaluation-results.json} and {@code evaluation-report.md} cannot drift from what the code
 * does. Because the run is deterministic, regenerating them is a no-op, and this test fails if the
 * committed artifacts do not match a fresh run — which makes a stale published number a build
 * failure rather than a discrepancy nobody notices.
 */
class ArtifactGeneratorTest {

    private static final Path ROOT = Path.of(System.getProperty("civlint.artifact.root", "."));
    private static final String REPRESENTATIVE_CASE_ID = "CASE.15.DUTY.VIOLATION";

    @Test
    @DisplayName("evaluation artifacts are generated and are byte-stable")
    void generateArtifacts() {
        EvaluationHarness harness = new EvaluationHarness(new ReplayAgentAdapter(), Clock.systemUTC());
        // The same operation the API, the store and the dashboard use, so a hash published here is
        // the hash those surfaces publish.
        EvaluationRun advanced = harness.runReplayVerified(EvaluationRun.Mode.ADVANCED);
        EvaluationRun baseline = harness.runReplayVerified(EvaluationRun.Mode.BASELINE);
        EvaluationRun advancedAgain = harness.runReplayVerified(EvaluationRun.Mode.ADVANCED);

        assertThat(advancedAgain.canonicalHash())
                .as("the run used to publish artifacts is reproducible")
                .isEqualTo(advanced.canonicalHash());

        String resultsJson = CanonicalJson.write(resultsDocument(advanced, baseline));
        String report = reportMarkdown(advanced, baseline);

        boolean writeMode = Boolean.getBoolean("civlint.artifact.write");
        verifyOrWrite(ROOT.resolve("evaluation-results.json"), resultsJson + "\n", writeMode);
        verifyOrWrite(ROOT.resolve("evaluation-report.md"), report, writeMode);
        verifyOrWriteTrajectories(baseline, advanced, writeMode);

        // Regeneration is a no-op: the same inputs produce the same bytes.
        assertThat(CanonicalJson.write(resultsDocument(
                        harness.runReplayVerified(EvaluationRun.Mode.ADVANCED),
                        harness.runReplayVerified(EvaluationRun.Mode.BASELINE))))
                .isEqualTo(resultsJson);
        assertThat(read(ROOT.resolve("evaluation-results.json"))).isEqualTo(resultsJson + "\n");
        assertThat(read(ROOT.resolve("evaluation-report.md"))).isEqualTo(report);
    }

    private static Json resultsDocument(EvaluationRun advanced, EvaluationRun baseline) {
        return Json.obj()
                .put("product", "CivLint")
                .put("author", "Buddy Guzman (bguzman)")
                .put("dataMode", "DEMO")
                .put(
                        "dataStatement",
                        "All data is synthetic and describes a fictional Federated Civil Registry. No "
                                + "real person, record, office, agency or law is represented.")
                .put("caseCount", DemoCases.CASE_COUNT)
                .put("policyPackId", advanced.policyPackId())
                .put("policyHash", advanced.policyHash())
                .put("humanNecessityMapHash", advanced.humanNecessityMapHash())
                .put("existingVersionId", advanced.existingVersionId())
                .put("existingVersionHash", advanced.existingVersionHash())
                .put("proposedVersionId", advanced.proposedVersionId())
                .put("proposedVersionHash", advanced.proposedVersionHash())
                .put("ruleEngineVersion", advanced.ruleEngineVersion())
                .put("verifierVersion", advanced.verifierVersion())
                .put("advanced", runDocument(advanced))
                .put("baseline", runDocument(baseline))
                .put("oracle", oracleDocument())
                .build();
    }

    private static Json runDocument(EvaluationRun run) {
        List<Json> metrics = run.metrics().stream()
                .filter(m -> m.unit() != MetricResult.Unit.MILLISECONDS)
                .map(MetricResult::toJson)
                .toList();
        return Json.obj()
                .put("mode", run.mode())
                .put("canonicalHash", run.canonicalHash())
                .put("releaseOutcome", run.releaseDecision().outcome())
                .put("releaseRationale", run.releaseDecision().rationale())
                .put("findingCount", run.findings().size())
                .put("scenarioBlockingFindingCount", run.releaseBlockingFindings().size())
                .put("traceCount", run.traces().size())
                .put(
                        "metrics",
                        Json.array(metrics))
                .put(
                        "caseOutcomes",
                        Json.array(run.caseOutcomes().stream()
                                .map(EvaluationRun.CaseOutcome::toJson)
                                .toList()))
                .put(
                        "findings",
                        Json.array(run.findings().stream().map(Finding::toJson).toList()))
                .build();
    }

    private static Json oracleDocument() {
        return Json.array(DemoCases.cases().stream()
                .map(c -> Json.obj()
                        .put("caseId", c.caseId())
                        .put("title", c.title())
                        .put("scope", c.scope())
                        .put("oracleTier", c.oracleTier())
                        .put("oracleRequiredRole", c.oracleRequiredRole())
                        .put(
                                "expectedExplanationCodes",
                                Json.strings(c.expectedExplanationCodes()))
                        .put("explanation", c.explanation())
                        .build())
                .toList());
    }

    private static String reportMarkdown(EvaluationRun advanced, EvaluationRun baseline) {
        StringBuilder out = new StringBuilder();
        out.append("# CivLint evaluation report\n\n");
        out.append("Author: Buddy Guzman (bguzman). Product: CivLint.\n\n");
        out.append("> **Synthetic data.** Every procedure, policy citation, role, region and applicant\n");
        out.append("> record in this report is invented for a fictional Federated Civil Registry.\n");
        out.append("> Nothing here represents real law, a real government body, or any real person.\n\n");
        out.append("This file is generated by `ArtifactGeneratorTest`, which fails if the committed\n");
        out.append("copy differs from a fresh run. The numbers below are therefore what the code\n");
        out.append("produced, not what was typed.\n\n");

        out.append("## Reproduction anchors\n\n");
        out.append("| Artifact | Value |\n|---|---|\n");
        out.append(row("Policy pack", advanced.policyPackId() + " @ `" + advanced.policyHash() + "`"));
        out.append(row("Human Necessity Map", "`" + advanced.humanNecessityMapHash() + "`"));
        out.append(row("Existing version", advanced.existingVersionId() + " @ `"
                + advanced.existingVersionHash() + "`"));
        out.append(row("Proposed version", advanced.proposedVersionId() + " @ `"
                + advanced.proposedVersionHash() + "`"));
        out.append(row("Rule engine", "`" + advanced.ruleEngineVersion() + "`"));
        out.append(row("Verifier", "`" + advanced.verifierVersion() + "`"));
        out.append(row("Advanced run hash", "`" + advanced.canonicalHash() + "`"));
        out.append(row("Baseline run hash", "`" + baseline.canonicalHash() + "`"));
        out.append("\n");

        out.append("## Metrics: baseline versus advanced\n\n");
        out.append("| Metric | Baseline | Advanced |\n|---|---|---|\n");
        for (MetricResult metric : advanced.metrics()) {
            if (metric.unit() == MetricResult.Unit.MILLISECONDS) {
                continue;
            }
            String baselineValue = baseline.metric(metric.metricId())
                    .map(MetricResult::display)
                    .orElse("not published");
            out.append("| ").append(metric.label()).append(" | ").append(baselineValue)
                    .append(" | ").append(metric.display()).append(" |\n");
        }
        out.append("\n");

        out.append("## Per-case agreement with the locked oracle\n\n");
        out.append("| Case | Oracle tier / role | Baseline | Advanced |\n|---|---|---|---|\n");
        for (EvaluationCase c : DemoCases.cases()) {
            out.append("| `").append(c.caseId()).append("` ").append(c.title()).append(" | ")
                    .append(c.oracleTier()).append(" / ").append(c.oracleRequiredRole()).append(" | ")
                    .append(outcomeCell(baseline, c)).append(" | ")
                    .append(outcomeCell(advanced, c)).append(" |\n");
        }
        out.append("\n");

        out.append("## Unsafe-scenario blocking findings (advanced)\n\n");
        if (advanced.releaseBlockingFindings().isEmpty()) {
            out.append("None.\n\n");
        } else {
            for (Finding finding : advanced.releaseBlockingFindings()) {
                out.append("### `").append(finding.explanationCode()).append("` — ")
                        .append(finding.subject().key()).append("\n\n");
                out.append(finding.explanation()).append("\n\n");
                out.append("- Required reviewer: ").append(finding.requiredReviewerRole().label())
                        .append("\n");
                out.append("- Rule: `").append(finding.ruleId()).append("`\n");
                finding.counterexample().ifPresent(cx -> out
                        .append("- Minimal counterexample (")
                        .append(cx.kind())
                        .append("): `")
                        .append(String.join(" -> ", cx.witnessPath()))
                        .append("`\n"));
                out.append("\n");
            }
        }

        out.append("## Agent execution summary (advanced)\n\n");
        out.append("| Status | Count |\n|---|---|\n");
        for (AgentTrace.Status status : AgentTrace.Status.values()) {
            long count = advanced.traces().stream().filter(t -> t.status() == status).count();
            out.append("| ").append(status).append(" | ").append(count).append(" |\n");
        }
        out.append("\nAgent output is not load-bearing: `AgentIndependenceTest` asserts that the\n");
        out.append("findings, per-case verdicts and release decision are identical when every agent\n");
        out.append("is unavailable and when every agent returns hostile output.\n");
        return out.toString();
    }

    private static String outcomeCell(EvaluationRun run, EvaluationCase c) {
        return run.outcome(c.caseId())
                .map(o -> (o.agreesWithOracle() ? "agrees" : "**differs**") + " — "
                        + o.decidedTier() + " / " + o.requiredRole())
                .orElse("no outcome");
    }

    private static String row(String name, String value) {
        return "| " + name + " | " + value + " |\n";
    }

    private static void verifyOrWriteTrajectories(
            EvaluationRun baseline, EvaluationRun advanced, boolean writeMode) {
        Path directory = ROOT.resolve("docs/agent-traces");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + directory, e);
        }
        List<AgentTrace> representative = new ArrayList<>(Stream.concat(
                        baseline.traces().stream(), advanced.traces().stream())
                .filter(AgentTrace::usable)
                .filter(trace -> trace.traceId().endsWith("." + REPRESENTATIVE_CASE_ID))
                .sorted(java.util.Comparator.comparing(AgentTrace::agentId))
                .toList());
        assertThat(representative)
                .extracting(AgentTrace::agentId)
                .containsExactly(
                        AgentDefinition.BOUNDARY_CASE.agentId(),
                        AgentDefinition.BASELINE_GENERALIST.agentId(),
                        AgentDefinition.REPAIR_ADVISOR.agentId(),
                        AgentDefinition.RULE_MAPPER.agentId());

        Set<String> expectedFiles = representative.stream()
                .map(trace -> trace.traceId() + ".json")
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        Set<String> actualFiles;
        try (var paths = Files.list(directory)) {
            actualFiles = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + directory, e);
        }

        if (writeMode) {
            actualFiles.stream()
                    .filter(name -> !expectedFiles.contains(name))
                    .forEach(name -> delete(directory.resolve(name)));
        } else {
            assertThat(actualFiles)
                    .as("checked-in trace files must exactly match the current run")
                    .isEqualTo(expectedFiles);
        }

        for (AgentTrace trace : representative) {
            EvaluationRun run = trace.agentId().equals(AgentDefinition.BASELINE_GENERALIST.agentId())
                    ? baseline
                    : advanced;
            verifyOrWrite(
                    directory.resolve(trace.traceId() + ".json"),
                    CanonicalJson.write(trajectoryDocument(trace, run)) + "\n",
                    writeMode);
        }
        verifyOrWrite(directory.resolve("README.md"), trajectoryIndex(representative), writeMode);
    }

    private static Json trajectoryDocument(AgentTrace trace, EvaluationRun run) {
        AgentDefinition definition = definition(trace.agentId());
        EvaluationRun.CaseOutcome outcome = run.outcome(REPRESENTATIVE_CASE_ID).orElseThrow();
        return Json.obj()
                .put("trajectoryId", trace.traceId())
                .put("agent", Json.obj()
                        .put("agentId", definition.agentId())
                        .put("agentVersion", definition.agentVersion())
                        .put("displayName", definition.displayName())
                        .put("instruction", definition.remit())
                        .put("mayProposeAutomation", definition.mayProposeAutomation())
                        .put("mayBlockRelease", definition.mayBlockRelease())
                        .build())
                .put("caseId", REPRESENTATIVE_CASE_ID)
                .put("inputHash", trace.inputHash())
                .put("policyHash", trace.policyHash())
                .put("procedureVersionIds", Json.strings(trace.procedureVersionIds()))
                .put("tool", Json.obj()
                        .put("adapter", "replay")
                        .put("purpose", "Return a deterministic checked-in model response")
                        .put("rawResponseRecorded", false)
                        .build())
                .put("actionsAndFeedback", Json.array(
                        trace.events().stream().map(AgentTrace.TraceEvent::toJson).toList()))
                .put("observations", Json.array(
                        trace.observations().stream().map(o -> o.toJson()).toList()))
                .put("retries", trace.retries())
                .put("status", trace.status())
                .put("humanCheckpoint", Json.obj()
                        .put("required", outcome.requiredRole().human())
                        .put("role", outcome.requiredRole())
                        .put("decisionTier", outcome.decidedTier())
                        .build())
                .put("finalResult", Json.obj()
                        .put("agreesWithOracle", outcome.agreesWithOracle())
                        .put("caseDecision", outcome.decidedTier())
                        .put("primaryProposalReleaseOutcome", run.releaseDecision().outcome())
                        .build())
                .build();
    }

    private static String trajectoryIndex(List<AgentTrace> traces) {
        StringBuilder out = new StringBuilder();
        out.append("# Representative agent trajectories\n\n");
        out.append("Author: Buddy Guzman (bguzman).\n\n");
        out.append("One generated trajectory is included for every agent used. All four use the same\n");
        out.append("challenging case so their instructions, actions, contract feedback and final\n");
        out.append("results can be compared directly. No external tool is called: the replay adapter\n");
        out.append("returns deterministic checked-in model output and raw text is deliberately redacted.\n\n");
        out.append("| Trajectory | Agent | Observations | Retries |\n|---|---|---:|---:|\n");
        traces.forEach(trace -> out.append("| [")
                .append(trace.traceId()).append(".json](")
                .append(trace.traceId()).append(".json) | ")
                .append(trace.agentId()).append(" | ")
                .append(trace.observations().size()).append(" | ")
                .append(trace.retries()).append(" |\n"));
        out.append("\nFull agent instructions and contract limits are in `../agent-contracts.md`.\n");
        out.append("The advanced verdict remains independent of agent output, proven by\n");
        out.append("`AgentIndependenceTest`.\n");
        return out.toString();
    }

    private static AgentDefinition definition(String agentId) {
        return Stream.concat(
                        AgentDefinition.specialised().stream(),
                        Stream.of(AgentDefinition.BASELINE_GENERALIST))
                .filter(candidate -> candidate.agentId().equals(agentId))
                .findFirst()
                .orElseThrow();
    }

    private static void verifyOrWrite(Path path, String expected, boolean writeMode) {
        if (writeMode) {
            write(path, expected);
        } else {
            assertThat(read(path)).as("generated artifact %s", path).isEqualTo(expected);
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete stale trace " + path, e);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
