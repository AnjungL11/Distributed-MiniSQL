package com.minisql.region.rpc;

import com.minisql.common.JsonUtil;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRpcServerTest {
    @TempDir
    Path tempDir;

    @Test
    void thriftClientCanExecuteSql() throws Exception {
        int port = freePort();
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        RegionRpcServer server = new RegionRpcServer(port, new RegionServiceImpl(new SqlExecutor(engine)));
        server.start();
        try (TTransport transport = new TSocket("127.0.0.1", port, 3000)) {
            transport.open();
            RegionService.Client client = new RegionService.Client(new TBinaryProtocol(transport));

            SqlExecutionResult created = JsonUtil.fromJson(
                    client.executeSQL("CREATE TABLE student (id INT PRIMARY KEY, name STRING)"),
                    SqlExecutionResult.class);
            SqlExecutionResult inserted = JsonUtil.fromJson(
                    client.executeSQL("INSERT INTO student (id, name) VALUES (1, 'Alice')"),
                    SqlExecutionResult.class);
            SqlExecutionResult selected = JsonUtil.fromJson(
                    client.executeSQL("SELECT name FROM student WHERE id = 1"),
                    SqlExecutionResult.class);

            assertTrue(created.isSuccess());
            assertTrue(inserted.isSuccess());
            assertEquals("Alice", selected.getRows().get(0).get(0));
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
