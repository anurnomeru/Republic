package ink.anur.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import ink.anur.common.struct.KanashiNode;
import ink.anur.inject.config.Configuration;

/**
 * Created by Anur on 2020/9/16
 */
@Configuration(prefix = "inet")
public class InetConfiguration {
    private String localServerName;
    private Integer localServerPort;
    private String clientAddr;
    private long timeoutMs = 2000L;

    private final Logger logger = LoggerFactory.getLogger(InetConfiguration.class);

    @Nonnull
    public List<KanashiNode> getCluster() {
        return Stream.of(clientAddr.split(";"))
                     .map(it -> {
                         String[] split = it.split(":");
                         return new KanashiNode(split[0], split[1], Integer.parseInt(split[2]));
                     })
                     .collect(Collectors.toList());
    }

    @Nonnull
    public synchronized String getLocalServerName() {
        if (localServerName == null) {
            localServerName = UUID.randomUUID().toString();
            logger.info("cause config 'inet.localServerName' is not specify, random one for identify local service :{}", localServerName);
        }

        return localServerName;
    }

    @Nonnull
    public Integer getLocalServerPort() {
        return localServerPort;
    }

    @Nonnull
    public Long getTimeoutMs() {
        return timeoutMs;
    }
}
