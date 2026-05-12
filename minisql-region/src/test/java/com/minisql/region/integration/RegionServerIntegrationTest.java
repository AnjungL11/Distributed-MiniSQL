package com.minisql.region.integration;

import com.minisql.common.JsonUtil;
import com.minisql.common.ZkConstants;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.region.model.RegionServerInfo;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.rpc.RegionRpcServer;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import com.minisql.region.zk.RegionZkRegistry;
import com.minisql.rpc.region.RegionService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServerIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void rpcZookeeperAndRecoveryWorkTogether() throws Exception {
        try (TestingServer zkServer = new TestingServer()) {
            CuratorFramework zkClient = CuratorFrameworkFactory.builder()
                    .connectString(zkServer.getConnectString())
                    .namespace(ZkConstants.ZK_NAMESPACE)
                    .retryPolicy(new ExponentialBackoffRetry(100, 3))
                    .build();
            zkClient.start();

            int regionPort = freePort();
            Path storageRoot = tempDir.resolve("region");
            RegionStorageEngine engine = new RegionStorageEngine(storageRoot);
            engine.start();
            SqlExecutor executor = new SqlExecutor(engine);
            RegionRpcServer rpcServer = new RegionRpcServer(regionPort, new RegionServiceImpl(executor));
            RegionServerInfo info = new RegionServerInfo("node-it", "127.0.0.1", regionPort);
            RegionZkRegistry registry = new RegionZkRegistry(zkClient, info, engine::tableNames, 100);

            rpcServer.start();
            registry.start();
            try {
                assertTrue(registry.isRegistered());

                try (TTransport transport = new TSocket("127.0.0.1", regionPort, 3000)) {
                    transport.open();
                    RegionService.Client client = new RegionService.Client(new TBinaryProtocol(transport));
                    assertTrue(result(client.executeSQL("CREATE TABLE student (id INT PRIMARY KEY, name STRING)")).isSuccess());
                    assertTrue(result(client.executeSQL("INSERT INTO student (id, name) VALUES (1, 'Alice')")).isSuccess());
                    SqlExecutionResult query = result(client.executeSQL("SELECT name FROM student WHERE id = 1"));
                    assertEquals("Alice", query.getRows().get(0).get(0));
                }

                registry.refreshHeartbeat();
                RegionHeartbeatState state = readState(zkClient, registry.getNodePath());
                assertTrue(state.getHoldingRegions().contains("student"));
            } finally {
                registry.close();
                rpcServer.stop();
                assertFalse(registry.isRegistered());
                zkClient.close();
            }

            RegionStorageEngine recovered = new RegionStorageEngine(storageRoot);
            recovered.start();
            SqlExecutionResult recoveredQuery = new SqlExecutor(recovered)
                    .execute("SELECT name FROM student WHERE id = 1");
            assertEquals("Alice", recoveredQuery.getRows().get(0).get(0));
        }
    }

    private SqlExecutionResult result(String json) {
        return JsonUtil.fromJson(json, SqlExecutionResult.class);
    }

    private RegionHeartbeatState readState(CuratorFramework client, String path) throws Exception {
        byte[] data = client.getData().forPath(path);
        return JsonUtil.fromJson(new String(data, StandardCharsets.UTF_8), RegionHeartbeatState.class);
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
