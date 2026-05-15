package com.minisql.region.rpc;

import com.minisql.common.JsonUtil;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSyncPayloadTest {
    @TempDir
    Path tempDir;

    @Test
    void syncDataExecutesSqlPayload() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        SqlExecutor executor = new SqlExecutor(engine);
        executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");
        RegionServiceImpl service = new RegionServiceImpl(executor);

        byte[] payload = JsonUtil.toJson(RegionSyncPayload.forSql(
                "student",
                "INSERT INTO student (id, name) VALUES (1, 'Alice')"))
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(service.syncData("student", ByteBuffer.wrap(payload)));
        assertTrue(executor.execute("SELECT * FROM student WHERE id = 1").isSuccess());
    }

    @Test
    void syncDataRejectsMismatchedTable() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        RegionServiceImpl service = new RegionServiceImpl(new SqlExecutor(engine));
        byte[] payload = JsonUtil.toJson(RegionSyncPayload.forSql("course", "SELECT * FROM course"))
                .getBytes(StandardCharsets.UTF_8);

        assertFalse(service.syncData("student", ByteBuffer.wrap(payload)));
    }
}
