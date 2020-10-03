package ink.anur.config;

import javax.annotation.Nonnull;

import ink.anur.inject.config.Configuration;

/**
 * Created by Anur on 2020/9/16
 */
@Configuration(prefix = "elect")
public class ElectConfiguration {
    private Long electionTimeoutMs;
    private Long votesBackOffMs;
    private Long heartBeatMs;

    @Nonnull
    public Long getElectionTimeoutMs() {
        return electionTimeoutMs;
    }

    @Nonnull
    public Long getVotesBackOffMs() {
        return votesBackOffMs;
    }

    @Nonnull
    public Long getHeartBeatMs() {
        return heartBeatMs;
    }
}
