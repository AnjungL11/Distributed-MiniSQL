package com.minisql.region.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServerConfigTest {
    @Test
    void defaultsReadGlobalProperties() {
        RegionServerConfig config = RegionServerConfig.defaults();

        assertEquals(9090, config.getPort());
        assertTrue(config.getStorageRoot().endsWith(Paths.get("minisql_data")));
        assertEquals(config.getHost() + ":" + config.getPort(), config.getNodeId());
        assertEquals(RegionServerConfig.DEFAULT_HEARTBEAT_INTERVAL_MS, config.getHeartbeatIntervalMs());
    }

    @Test
    void parsesEqualsStyleArguments() {
        RegionServerConfig config = RegionServerConfig.fromArgs(new String[] {
                "--host=10.0.0.8",
                "--port=9191",
                "--storage=target/region-a",
                "--node-id=region-a",
                "--heartbeat-interval-ms=5000"
        });

        assertEquals("10.0.0.8", config.getHost());
        assertEquals(9191, config.getPort());
        assertTrue(config.getStorageRoot().endsWith(Paths.get("target/region-a")));
        assertEquals("region-a", config.getNodeId());
        assertEquals(5000, config.getHeartbeatIntervalMs());
    }

    @Test
    void parsesSeparatedArguments() {
        RegionServerConfig config = RegionServerConfig.fromArgs(new String[] {
                "--host", "127.0.0.1",
                "--port", "9192",
                "--storage-root", "target/region-b"
        });

        assertEquals("127.0.0.1", config.getHost());
        assertEquals(9192, config.getPort());
        assertTrue(config.getStorageRoot().endsWith(Paths.get("target/region-b")));
        assertEquals("127.0.0.1:9192", config.getNodeId());
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> RegionServerConfig.fromArgs(new String[] {
                "--port=70000"
        }));
    }

    @Test
    void rejectsMissingArgumentValue() {
        assertThrows(IllegalArgumentException.class, () -> RegionServerConfig.fromArgs(new String[] {
                "--port"
        }));
    }
}
