package com.minisql.region.model;

import com.minisql.region.config.RegionServerConfig;

import java.util.Objects;

/**
 * Stable identity and endpoint advertised by a Region Server.
 */
public final class RegionServerInfo {
    private final String nodeId;
    private final String host;
    private final int port;

    public RegionServerInfo(String nodeId, String host, int port) {
        this.nodeId = requireText(nodeId, "nodeId");
        this.host = requireText(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
    }

    public static RegionServerInfo fromConfig(RegionServerConfig config) {
        Objects.requireNonNull(config, "config");
        return new RegionServerInfo(config.getNodeId(), config.getHost(), config.getPort());
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "RegionServerInfo{"
                + "nodeId='" + nodeId + '\''
                + ", host='" + host + '\''
                + ", port=" + port
                + '}';
    }
}
