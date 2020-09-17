package ink.anur.config;

import org.jetbrains.annotations.NotNull;

import ink.anur.inject.config.Configuration;

/**
 * Created by Anur on 2020/9/16
 */
@Configuration(prefix = "elect")
public class ElectConfiguration {
    private Long electionTimeoutMs;
    private Long votesBackOffMs;
    private Long heartBeatMs;

    @NotNull
    public Long getElectionTimeoutMs() {
        return electionTimeoutMs;
    }

    @NotNull
    public Long getVotesBackOffMs() {
        return votesBackOffMs;
    }

    @NotNull
    public Long getHeartBeatMs() {
        return heartBeatMs;
    }
}
