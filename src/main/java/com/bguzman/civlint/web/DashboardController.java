package com.bguzman.civlint.web;

import com.bguzman.civlint.application.CivLintService;
import com.bguzman.civlint.domain.AgentObservation;
import com.bguzman.civlint.domain.EvaluationRun;
import com.bguzman.civlint.domain.Finding;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renders the dashboard.
 *
 * <p>Both architectures are run on each page load and shown side by side, because a comparison a
 * reader has to assemble from two separate pages is a comparison they will not check.
 */
@Controller
public class DashboardController {

    private final CivLintService service;

    public DashboardController(CivLintService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        EvaluationRun advanced = service.preview(EvaluationRun.Mode.ADVANCED);
        EvaluationRun baseline = service.preview(EvaluationRun.Mode.BASELINE);

        model.addAttribute("advanced", advanced);
        model.addAttribute("baseline", baseline);
        model.addAttribute("policy", service.policyPack());
        model.addAttribute("map", service.humanNecessityMap());
        model.addAttribute("cases", service.cases());
        model.addAttribute(
                "comparison",
                service.compare(advanced.existingVersionId(), advanced.proposedVersionId())
                        .orElseThrow(() -> new NotFoundException("The demonstration versions are missing")));

        List<Finding> counterexamples = advanced.findings().stream()
                .filter(finding -> finding.counterexample().isPresent())
                .toList();
        model.addAttribute("counterexamples", counterexamples);
        model.addAttribute("blocking", advanced.releaseBlockingFindings());

        // Traces are already sorted by identifier and observations within a trace by their own, so
        // flattening preserves the run's deterministic order.
        List<AgentObservation> observations = advanced.traces().stream()
                .flatMap(trace -> trace.observations().stream())
                .toList();
        model.addAttribute("observations", observations);
        return "dashboard";
    }
}
