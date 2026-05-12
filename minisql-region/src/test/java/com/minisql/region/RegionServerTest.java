package com.minisql.region;

import com.minisql.region.config.RegionServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServerTest {
    @TempDir
    Path tempDir;

    @Test
    void startAndCloseAreIdempotent() throws Exception {
        RegionServerConfig config = RegionServerConfig.builder()
                .host("127.0.0.1")
                .port(freePort())
                .storageRoot(tempDir)
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

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
