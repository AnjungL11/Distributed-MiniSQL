package com.minisql.region.config;

import com.minisql.common.ConfigReader;
import com.minisql.region.util.NetworkUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable runtime configuration for a Region Server process.
 */
public final class RegionServerConfig {
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 3000;

    private final String host;
    private final int port;
    private final Path storageRoot;
    private final String nodeId;
    private final int heartbeatIntervalMs;
    private final boolean zookeeperEnabled;

    private RegionServerConfig(Builder builder) {
        this.host = requireText(builder.host, "host");
        this.port = requirePort(builder.port);
        this.storageRoot = Objects.requireNonNull(builder.storageRoot, "storageRoot")
                .toAbsolutePath()
                .normalize();
        this.nodeId = builder.nodeId == null || builder.nodeId.trim().isEmpty()
                ? this.host + ":" + this.port
                : builder.nodeId.trim();
        this.heartbeatIntervalMs = requirePositive(builder.heartbeatIntervalMs, "heartbeatIntervalMs");
        this.zookeeperEnabled = builder.zookeeperEnabled;
    }

    public static RegionServerConfig defaults() {
        return builder().build();
    }

    public static RegionServerConfig fromArgs(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        Builder builder = builder();
        if (parsed.containsKey("host")) {
            builder.host(parsed.get("host"));
        }
        if (parsed.containsKey("port")) {
            builder.port(Integer.parseInt(parsed.get("port")));
        }
        if (parsed.containsKey("storage")) {
            builder.storageRoot(Paths.get(parsed.get("storage")));
        }
        if (parsed.containsKey("storage-root")) {
            builder.storageRoot(Paths.get(parsed.get("storage-root")));
        }
        if (parsed.containsKey("node-id")) {
            builder.nodeId(parsed.get("node-id"));
        }
        if (parsed.containsKey("heartbeat-interval-ms")) {
            builder.heartbeatIntervalMs(Integer.parseInt(parsed.get("heartbeat-interval-ms")));
        }
        if (parsed.containsKey("zookeeper-enabled")) {
            builder.zookeeperEnabled(Boolean.parseBoolean(parsed.get("zookeeper-enabled")));
        }
        return builder.build();
    }

    public static Builder builder() {
        String host = NetworkUtil.resolveLocalIpv4();
        int port = ConfigReader.getInt("region.rpc.port", 9090);
        String storagePath = ConfigReader.getString("region.storage.path", "./minisql_data/");
        boolean zookeeperEnabled = Boolean.parseBoolean(
                ConfigReader.getString("region.zookeeper.enabled", "true"));
        return new Builder()
                .host(host)
                .port(port)
                .storageRoot(Paths.get(storagePath))
                .heartbeatIntervalMs(DEFAULT_HEARTBEAT_INTERVAL_MS)
                .zookeeperEnabled(zookeeperEnabled);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public boolean isZookeeperEnabled() {
        return zookeeperEnabled;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        if (args == null) {
            return values;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unsupported argument: " + arg);
            }
            String token = arg.substring(2);
            int equals = token.indexOf('=');
            if (equals >= 0) {
                values.put(token.substring(0, equals), token.substring(equals + 1));
                continue;
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for argument: " + arg);
            }
            values.put(token, args[++i]);
        }
        return values;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePort(int value) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    @Override
    public String toString() {
        return "RegionServerConfig{"
                + "host='" + host + '\''
                + ", port=" + port
                + ", storageRoot=" + storageRoot
                + ", nodeId='" + nodeId + '\''
                + ", heartbeatIntervalMs=" + heartbeatIntervalMs
                + ", zookeeperEnabled=" + zookeeperEnabled
                + '}';
    }

    public static final class Builder {
        private String host;
        private int port;
        private Path storageRoot;
        private String nodeId;
        private int heartbeatIntervalMs;
        private boolean zookeeperEnabled;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder storageRoot(Path storageRoot) {
            this.storageRoot = storageRoot;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder heartbeatIntervalMs(int heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        public Builder zookeeperEnabled(boolean zookeeperEnabled) {
            this.zookeeperEnabled = zookeeperEnabled;
            return this;
        }

        public RegionServerConfig build() {
            return new RegionServerConfig(this);
        }
    }
}
