package com.bguzman.civlint.agents;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Serves agent responses from fixtures checked into the artifact.
 *
 * <p>This is the default and only path the evaluation needs. It requires no API key, no
 * network access, no cloud credentials and no paid model, and it returns the same bytes on every run,
 * which is what makes deterministic replay checkable.
 *
 * <p>Fixtures live at {@code /civlint/agents/replay/<agentId>/<promptKey>.json} on the classpath.
 * A missing fixture is reported as {@link AgentUnavailableException} rather than substituted with a
 * default, because a silently defaulted response would be indistinguishable from a real one.
 *
 * <p><strong>Thread safety:</strong> stateless; safe to share across virtual threads.
 */
public final class ReplayAgentAdapter implements AgentModelPort {

    /** Classpath prefix under which replay fixtures are stored. */
    public static final String FIXTURE_ROOT = "/civlint/agents/replay/";

    @Override
    public String invoke(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        String resource = FIXTURE_ROOT + request.agentId() + "/" + request.promptKey() + ".json";
        try (InputStream stream = ReplayAgentAdapter.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AgentUnavailableException(
                        "No replay fixture at " + resource
                                + ". CivLint does not substitute a default response, so this invocation "
                                + "is recorded as skipped.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read replay fixture " + resource, e);
        }
    }

    @Override
    public String adapterId() {
        return "replay";
    }
}
