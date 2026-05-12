package com.minisql.region.replication;

import com.minisql.common.JsonUtil;
import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.rpc.RegionRpcServer;
import com.minisql.region.rpc.RegionSyncPayload;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionPeerClientTest {
    @TempDir
    Path tempDir;

    @Test
    void duplicateSyncPayloadIsIdempotent() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir.resolve("follower"));
        engine.start();
        SqlExecutor executor = new SqlExecutor(engine);
        executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");
        RegionServiceImpl service = new RegionServiceImpl(executor);
        RegionSyncPayload payload = RegionSyncPayload.forSql(
                "student",
                "INSERT INTO student (id, name) VALUES (1, 'Alice')");
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);

        assertTrue(service.syncData("student", ByteBuffer.wrap(bytes)));
        assertTrue(service.syncData("student", ByteBuffer.wrap(bytes)));

        SqlExecutionResult result = executor.execute("SELECT * FROM student WHERE id = 1");
        assertEquals(1, result.getRows().size());
    }

    @Test
    void syncedWriteIsRecoveredFromWal() throws Exception {
        Path followerRoot = tempDir.resolve("wal-follower");
        RegionStorageEngine engine = new RegionStorageEngine(followerRoot);
        engine.start();
        SqlExecutor executor = new SqlExecutor(engine);
        executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");
        RegionServiceImpl service = new RegionServiceImpl(executor);

        RegionSyncPayload payload = RegionSyncPayload.forSql(
                "student",
                "INSERT INTO student (id, name) VALUES (1, 'Alice')");
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        assertTrue(service.syncData("student", ByteBuffer.wrap(bytes)));

        RegionStorageEngine recovered = new RegionStorageEngine(followerRoot);
        recovered.start();
        SqlExecutionResult result = new SqlExecutor(recovered).execute("SELECT name FROM student WHERE id = 1");
        assertEquals("Alice", result.getRows().get(0).get(0));
    }

    @Test
    void peerClientCallsFollowerSyncData() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir.resolve("peer"));
        engine.start();
        SqlExecutor executor = new SqlExecutor(engine);
        executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");
        int port = freePort();
        RegionRpcServer server = new RegionRpcServer(port, new RegionServiceImpl(executor));
        server.start();
        try {
            RegionPeerClient client = new RegionPeerClient(3000);
            assertTrue(client.syncSql(
                    new MasterEndpoint("127.0.0.1", port),
                    "student",
                    "INSERT INTO student (id, name) VALUES (1, 'Alice')"));
            assertEquals("Alice", executor.execute("SELECT name FROM student WHERE id = 1").getRows().get(0).get(0));
        } finally {
            server.stop();
        }
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
