package com.minisql.region;

import com.minisql.region.config.RegionServerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServerTest {
    @Test
    void startAndCloseAreIdempotent() {
        RegionServerConfig config = RegionServerConfig.builder()
                .host("127.0.0.1")
                .port(9090)
                .storageRoot(Paths.get("target/region-test"))
                .build();

        RegionServer server = new RegionServer(config);
        assertFalse(server.isRunning());

        server.start();
        server.start();
        assertTrue(server.isRunning());
        assertSame(config, server.getConfig());

        server.close();
        server.close();
        assertFalse(server.isRunning());
    }
}
