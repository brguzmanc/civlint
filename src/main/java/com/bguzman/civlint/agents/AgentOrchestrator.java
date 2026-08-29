package com.bguzman.civlint.agents;

import com.bguzman.civlint.domain.AgentTrace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs independent agent invocations concurrently on virtual threads and returns results in a stable
 * order.
 *
 * <p>Concurrency here is an efficiency measure only, and the design makes that verifiable rather than
 * merely intended:
 *
 * <ul>
 *   <li>Each task reads only its own immutable {@link AgentRequest}; no mutable state is shared.
 *   <li>Results are collected by <em>submission index</em>, not by completion order, and then sorted
 *       by trace identifier. Scheduling therefore cannot affect the output.
 *   <li>Each virtual-thread task binds an immutable, case-specific {@link RunContext} as a
 *       {@link ScopedValue}; arbitrary executor threads do not need to inherit a parent binding.
 * </ul>
 *
 * <p>{@code StructuredTaskScope} would express this more directly but is a preview API in Java 25, so
 * a virtual-thread-per-task executor is used instead. See {@code docs/architecture.md}.
 */
public final class AgentOrchestrator {

    private final AgentRunner runner;

    public AgentOrchestrator(AgentModelPort port) {
        this.runner = new AgentRunner(Objects.requireNonNull(port, "port"));
    }

    /**
     * One agent invocation to perform.
     *
     * @param definition the agent to invoke
     * @param request the request to send
     */
    public record Invocation(AgentDefinition definition, AgentRequest request) {

        public Invocation {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(request, "request");
        }
    }

    public List<AgentOutcome> runAll(RunContext context, List<Invocation> invocations) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(invocations, "invocations");
        if (invocations.isEmpty()) {
            return List.of();
        }

        return executeAll(context, invocations);
    }

    private List<AgentOutcome> executeAll(RunContext context, List<Invocation> invocations) {
        List<Callable<AgentOutcome>> tasks = new ArrayList<>(invocations.size());
        for (Invocation invocation : invocations) {
            tasks.add(() -> RunContext.callWith(
                    context.forCase(invocation.request().promptKey()),
                    () -> runner.run(invocation.definition(), invocation.request())));
        }

        List<AgentOutcome> collected = new ArrayList<>(invocations.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AgentOutcome>> futures = new ArrayList<>(tasks.size());
            for (Callable<AgentOutcome> task : tasks) {
                futures.add(executor.submit(task));
            }
            // Collected by submission index, so completion order cannot leak into the result.
            for (Future<AgentOutcome> future : futures) {
                try {
                    collected.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting an agent", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException(
                            "An agent task failed unexpectedly; agent-side problems are recorded as "
                                    + "trace statuses, so this indicates a defect in CivLint",
                            e.getCause());
                }
            }
        }
        return collected.stream()
                .sorted(Comparator.comparing(outcome -> outcome.trace().traceId()))
                .toList();
    }

    public static List<AgentTrace> traces(List<AgentOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        return outcomes.stream()
                .map(AgentOutcome::trace)
                .sorted(Comparator.comparing(AgentTrace::traceId))
                .toList();
    }
}
