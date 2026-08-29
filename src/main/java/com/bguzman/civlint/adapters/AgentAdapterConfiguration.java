package com.bguzman.civlint.adapters;

import com.bguzman.civlint.agents.AgentModelPort;
import com.bguzman.civlint.agents.ReplayAgentAdapter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the default, offline agent path.
 *
 * <p>The replay adapter is the default deliberately: the demonstration must run with no API key, no
 * network access and no paid model. A live adapter can be supplied by defining another
 * {@link AgentModelPort} bean, and nothing about judging depends on one existing.
 */
@Configuration
public class AgentAdapterConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentModelPort.class)
    public AgentModelPort replayAgentModelPort() {
        return new ReplayAgentAdapter();
    }

    /**
     * Supplies the clock used for run timing.
     *
     * <p>Injected rather than taken statically so that a test can pin it and prove that no verdict
     * depends on the time of day.
     *
     * @return the system UTC clock
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}
