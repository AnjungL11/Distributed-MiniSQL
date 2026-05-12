package com.minisql.region.zk;

import com.minisql.common.ZkConstants;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveMasterWatcherTest {
    @Test
    void refreshesActiveMasterEndpoint() throws Exception {
        try (TestingServer server = new TestingServer()) {
            CuratorFramework client = client(server);
            client.start();
            try {
                ActiveMasterWatcher watcher = new ActiveMasterWatcher(client, 50);
                watcher.start();
                assertTrue(watcher.getCurrentMaster().isEmpty());

                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .forPath(ZkConstants.ACTIVE_MASTER_PATH, "127.0.0.1:8080".getBytes(StandardCharsets.UTF_8));
                watcher.refreshNow();
                assertEquals("127.0.0.1:8080", watcher.getCurrentMaster().orElseThrow().asAddress());

                client.setData().forPath(ZkConstants.ACTIVE_MASTER_PATH, "127.0.0.2:8081".getBytes(StandardCharsets.UTF_8));
                watcher.refreshNow();
                assertEquals("127.0.0.2:8081", watcher.getCurrentMaster().orElseThrow().asAddress());
                watcher.close();
            } finally {
                client.close();
            }
        }
    }

    private CuratorFramework client(TestingServer server) {
        return CuratorFrameworkFactory.builder()
                .connectString(server.getConnectString())
                .namespace(ZkConstants.ZK_NAMESPACE)
                .retryPolicy(new ExponentialBackoffRetry(100, 3))
                .build();
    }
}
