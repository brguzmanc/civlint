package com.bguzman.civlint.web;

import com.bguzman.civlint.application.CivLintService;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Json;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read-only JSON API, plus the one endpoint that starts an evaluation.
 *
 * <p>Every response body is CivLint's canonical JSON rather than a framework-serialised object graph.
 * That means what a caller reads is byte-for-byte the document whose SHA-256 is the published hash,
 * so a third party can verify a hash from an API response alone.
 */
@RestController
@RequestMapping("/api")
public class CivLintApiController {

    private final CivLintService service;

    public CivLintApiController(CivLintService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping(value = "/procedures", produces = MediaType.APPLICATION_JSON_VALUE)
    public String procedures() {
        return CanonicalJson.write(service.procedure().toJson());
    }

    @GetMapping(value = "/procedures/{id}/comparison", produces = MediaType.APPLICATION_JSON_VALUE)
    public String comparison(
            @PathVariable String id,
            @RequestParam(defaultValue = "V1.REGIONAL") String existing,
            @RequestParam(defaultValue = "V2.NATIONAL") String proposed) {
        if (!service.procedure().procedureId().equals(id)) {
            throw new NotFoundException("No procedure with identifier " + id);
        }
        return CanonicalJson.write(service.compare(existing, proposed)
                .orElseThrow(() -> new NotFoundException(
                        "Procedure " + id + " does not have both versions " + existing + " and " + proposed))
                .toJson());
    }

    @GetMapping(value = "/policy", produces = MediaType.APPLICATION_JSON_VALUE)
    public String policy() {
        return CanonicalJson.write(service.policyPack().toJson());
    }

    @GetMapping(value = "/human-necessity", produces = MediaType.APPLICATION_JSON_VALUE)
    public String humanNecessity() {
        return CanonicalJson.write(service.humanNecessityMap().toJson());
    }

    @GetMapping(value = "/cases", produces = MediaType.APPLICATION_JSON_VALUE)
    public String cases() {
        return CanonicalJson.write(Json.array(
                service.cases().stream().map(c -> c.toJson()).toList()));
    }

    @PostMapping(value = "/evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> evaluate(@RequestParam(defaultValue = "ADVANCED") String mode) {
        EvaluationRun.Mode parsed;
        try {
            parsed = EvaluationRun.Mode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "mode must be ADVANCED or BASELINE, but was \"" + mode + "\"");
        }
        EvaluationRun run = service.evaluate(parsed);
        return ResponseEntity.created(URI.create("/api/evaluations/" + run.runId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(CanonicalJson.write(run.toCanonicalJson()));
    }

    @GetMapping(value = "/evaluations", produces = MediaType.APPLICATION_JSON_VALUE)
    public String evaluations() {
        return CanonicalJson.write(Json.strings(service.runIds()));
    }

    @GetMapping(value = "/evaluations/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String run(@PathVariable String runId) {
        return CanonicalJson.write(require(runId).toCanonicalJson());
    }

    @GetMapping(value = "/evaluations/{runId}/findings", produces = MediaType.APPLICATION_JSON_VALUE)
    public String findings(@PathVariable String runId) {
        return CanonicalJson.write(Json.array(
                require(runId).findings().stream().map(f -> f.toJson()).toList()));
    }

    @GetMapping(value = "/evaluations/{runId}/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public String metrics(@PathVariable String runId) {
        return CanonicalJson.write(Json.array(
                require(runId).metrics().stream().map(m -> m.toJson()).toList()));
    }

    @GetMapping(value = "/evaluations/{runId}/traces", produces = MediaType.APPLICATION_JSON_VALUE)
    public String traces(@PathVariable String runId) {
        return CanonicalJson.write(Json.array(
                require(runId).traces().stream().map(t -> t.toJson()).toList()));
    }

    @GetMapping(
            value = "/evaluations/{runId}/counterexamples",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String counterexamples(@PathVariable String runId) {
        return CanonicalJson.write(Json.array(
                require(runId).findings().stream()
                        .flatMap(f -> f.counterexample().stream())
                        .map(c -> c.toJson())
                        .toList()));
    }

    @GetMapping(value = "/evaluations/{runId}/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public String report(@PathVariable String runId) {
        EvaluationRun run = require(runId);
        return CanonicalJson.write(Json.obj()
                .put("runId", run.runId())
                .put("mode", run.mode())
                .put("canonicalHash", run.canonicalHash())
                .put("policyPackId", run.policyPackId())
                .put("policyHash", run.policyHash())
                .put("humanNecessityMapHash", run.humanNecessityMapHash())
                .put("existingVersionHash", run.existingVersionHash())
                .put("proposedVersionHash", run.proposedVersionHash())
                .put("ruleEngineVersion", run.ruleEngineVersion())
                .put("verifierVersion", run.verifierVersion())
                .put("releaseOutcome", run.releaseDecision().outcome())
                .put("dataMode", "DEMO")
                .put(
                        "dataStatement",
                        "All data is synthetic and describes a fictional Federated Civil Registry. "
                                + "No real person, record, office or law is represented.")
                .build());
    }

    @GetMapping(value = "/data-mode", produces = MediaType.APPLICATION_JSON_VALUE)
    public String dataMode() {
        return CanonicalJson.write(Json.obj()
                .put("mode", "DEMO")
                .put("permits", "Synthetic fictional data only")
                .put(
                        "statement",
                        "This deployment holds no real personal information. The demonstration boundary "
                                + "is a temporal safety measure, not the product's permanent purpose.")
                .build());
    }

    private EvaluationRun require(String runId) {
        return service.run(runId)
                .orElseThrow(() -> new NotFoundException("No stored run with identifier " + runId));
    }
}
