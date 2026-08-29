package com.bguzman.civlint.agents;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Immutable per-run context carried through agent execution by a {@link ScopedValue}.
 *
 * <p>A scoped value is used rather than a thread-local because agents run on virtual threads created
 * per task: the context must be inherited by each task without being mutable and without needing to
 * be cleaned up. Every field is immutable, which is the precondition for sharing it safely across
 * concurrently running agents.
 *
 * @param runId identifier of the run
 * @param policyVersion version of the policy pack in force
 * @param policyHash canonical hash of the policy pack in force
 * @param caseId the case being evaluated, where one applies
 * @param correlationId identifier tying together the log lines of one logical operation
 */
public record RunContext(
        String runId,
        String policyVersion,
        String policyHash,
        String caseId,
        String correlationId) {

    /**
     * The scoped value through which the current context is reached.
     *
     * <p>Bound with {@link #callWith(RunContext, java.util.concurrent.Callable)}. Unbound outside a
     * run, which {@link #current()} reports as an empty optional rather than as {@code null}.
     */
    private static final ScopedValue<RunContext> CURRENT = ScopedValue.newInstance();

    public RunContext {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(policyHash, "policyHash");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(correlationId, "correlationId");
    }

    public static Optional<RunContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static <T> T callWith(
            RunContext context, Callable<T> operation) throws Exception {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(operation, "operation");
        return ScopedValue.where(CURRENT, context).call(operation::call);
    }

    public RunContext forCase(String newCaseId) {
        return new RunContext(runId, policyVersion, policyHash, newCaseId, correlationId);
    }
}
