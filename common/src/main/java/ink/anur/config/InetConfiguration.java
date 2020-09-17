package ink.anur.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;
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

    @Nonnull
    public List<KanashiNode> getCluster() {
        return Stream.of(clientAddr.split(";"))
                     .map(it -> {
                         String[] split = it.split(":");
                         return new KanashiNode(split[0], split[1], Integer.parseInt(split[2]));
                     })
                     .collect(Collectors.toList());
    }

    @NotNull
    public String getLocalServerName() {
        return localServerName;
    }

    @NotNull
    public Integer getLocalServerPort() {
        return localServerPort;
    }
}
