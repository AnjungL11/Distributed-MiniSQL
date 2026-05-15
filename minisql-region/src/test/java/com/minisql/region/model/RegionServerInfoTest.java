package com.minisql.region.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionServerInfoTest {
    @Test
    void buildsAddressFromHostAndPort() {
        RegionServerInfo info = new RegionServerInfo("node-a", "127.0.0.1", 9090);

        assertEquals("node-a", info.getNodeId());
        assertEquals("127.0.0.1", info.getHost());
        assertEquals(9090, info.getPort());
        assertEquals("127.0.0.1:9090", info.getAddress());
    }

    @Test
    void rejectsBlankNodeId() {
        assertThrows(IllegalArgumentException.class, () -> new RegionServerInfo(" ", "127.0.0.1", 9090));
    }
}
