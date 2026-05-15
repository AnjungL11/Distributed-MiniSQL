package com.minisql.region.zk;

import com.minisql.common.JsonUtil;
import com.minisql.common.ZkConstants;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.region.model.RegionServerInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegionZkRegistryTest {
    @Test
    void registersEphemeralNodeAndRefreshesHeartbeat() throws Exception {
        try (TestingServer server = new TestingServer()) {
            CuratorFramework client = client(server);
            client.start();
            try {
                Set<String> tables = new TreeSet<>();
                tables.add("student");
                RegionServerInfo info = new RegionServerInfo("node-a", "127.0.0.1", 19090);
                RegionZkRegistry registry = new RegionZkRegistry(client, info, () -> tables, 50);

                registry.start();
                assertTrue(registry.isRegistered());
                RegionHeartbeatState first = readState(client, registry.getNodePath());

                Thread.sleep(5);
                tables.add("course");
                registry.refreshHeartbeat();
                RegionHeartbeatState second = readState(client, registry.getNodePath());

                assertEquals("node-a", second.getNodeId());
                assertEquals(2, second.getHoldingRegions().size());
                assertTrue(second.getLastHeartbeatMillis() >= first.getLastHeartbeatMillis());

                registry.close();
                assertFalse(registry.isRegistered());
            } finally {
                client.close();
            }
        }
    }

    private RegionHeartbeatState readState(CuratorFramework client, String path) throws Exception {
        byte[] data = client.getData().forPath(path);
        return JsonUtil.fromJson(new String(data, StandardCharsets.UTF_8), RegionHeartbeatState.class);
    }

    private CuratorFramework client(TestingServer server) {
        return CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .namespace(ZkConstants.ZK_NAMESPACE)
                .retryPolicy(new ExponentialBackoffRetry(100, 3))
                .build();
    }
}
