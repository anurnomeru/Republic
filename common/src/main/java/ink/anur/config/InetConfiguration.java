package ink.anur.config;


import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import ink.anur.common.struct.RepublicNode;
import ink.anur.inject.config.Configuration;

/**
 * Created by Anur on 2020/9/16
 */
@Configuration(prefix = "inet")
public class InetConfiguration {
    private String localServerAddr;
    private String clientAddr;
    private long timeoutMs = 2000L;

    /**
     * TODO 需要做内存优化
     */
    @Nonnull
    public List<RepublicNode> getCluster() {
        return Stream.of(clientAddr.split(";"))
                     .map(RepublicNode.Companion::construct)
                     .collect(Collectors.toList());
    }

    @Nonnull
    public RepublicNode getLocalServer() {
        return RepublicNode.Companion.construct(localServerAddr);
    }

    @Nonnull
    public Long getTimeoutMs() {
        return timeoutMs;
    }
}
