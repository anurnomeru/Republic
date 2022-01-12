package ink.anur.config;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import ink.anur.common.struct.RepublicNode;
import ink.anur.inject.config.Configuration;
import ink.anur.inject.config.ConfigurationIgnore;

/**
 * Created by Anur on 2020/9/16
 */
@Configuration(prefix = "inet")
public class InetConfiguration {
    private String localAddr;
    private String clusterAddr;
    private long timeoutMs = 30000;

    @ConfigurationIgnore
    private List<RepublicNode> allCache;

    @ConfigurationIgnore
    private RepublicNode local;

    @Nonnull
    public List<RepublicNode> getCluster() {
        if (allCache == null) {
            synchronized (this) {
                if (allCache == null) {
                    allCache = Stream.of(clusterAddr.split(";"))
                            .map(RepublicNode.Companion::construct)
                            .collect(Collectors.toList());
                }
            }
        }

        return allCache;
    }

    @Nonnull
    public String getLocalNodeAddr() {
        if (localAddr == null) {
            synchronized (this) {
                if (localAddr == null) {
                    localAddr = "0.0.0.0:" + findFreePort();
                }
            }
        }

        return localAddr;
    }

    @Nonnull
    public RepublicNode getLocalNode() {
        if (local == null) {
            synchronized (this) {
                if (local == null) {
                    local = RepublicNode.Companion.construct(getLocalNodeAddr());
                }
            }
        }

        return local;
    }

    @Nonnull
    public Long getTimeoutMs() {
        return timeoutMs;
    }

    private static int findFreePort() {
        int port = 0;
        // For ServerSocket port number 0 means that the port number is automatically allocated.
        try (ServerSocket socket = new ServerSocket(0)) {
            // Disable timeout and reuse address after closing the socket.
            socket.setReuseAddress(true);
            port = socket.getLocalPort();
        } catch (IOException ignored) {
        }
        if (port > 0) {
            return port;
        }
        throw new RuntimeException("Could not find a free port");
    }
}
