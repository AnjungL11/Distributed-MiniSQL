package com.minisql.region.replication;

import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.rpc.RegionRpcServer;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplicationManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void leaderWriteReplicatesToFollower() throws Exception {
        RegionStorageEngine followerEngine = new RegionStorageEngine(tempDir.resolve("follower"));
        followerEngine.start();
        SqlExecutor followerExecutor = new SqlExecutor(followerEngine);
        int followerPort = freePort();
        RegionRpcServer followerServer = new RegionRpcServer(
                followerPort,
                new RegionServiceImpl(followerExecutor));
        followerServer.start();

        try {
            RegionStorageEngine leaderEngine = new RegionStorageEngine(tempDir.resolve("leader"));
            leaderEngine.start();
            ReplicationManager replicationManager = new ReplicationManager(new RegionPeerClient(3000));
            replicationManager.setPeers(Arrays.asList(new MasterEndpoint("127.0.0.1", followerPort)));
            RegionServiceImpl leaderService = new RegionServiceImpl(new SqlExecutor(leaderEngine), replicationManager);

            leaderService.executeSQL("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");
            leaderService.executeSQL("INSERT INTO student (id, name) VALUES (1, 'Alice')");

            SqlExecutionResult result = followerExecutor.execute("SELECT name FROM student WHERE id = 1");
            assertEquals("Alice", result.getRows().get(0).get(0));
        } finally {
            followerServer.stop();
        }
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
