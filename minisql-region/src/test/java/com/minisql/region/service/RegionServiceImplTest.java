package com.minisql.region.service;

import com.minisql.common.JsonUtil;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void executeSqlReturnsJsonResult() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        RegionServiceImpl service = new RegionServiceImpl(new SqlExecutor(engine));

        SqlExecutionResult result = JsonUtil.fromJson(
                service.executeSQL("CREATE TABLE student (id INT PRIMARY KEY, name STRING)"),
                SqlExecutionResult.class);

        assertTrue(result.isSuccess());
    }

    @Test
    void syncDataRejectsEmptyPayloadForNow() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        RegionServiceImpl service = new RegionServiceImpl(new SqlExecutor(engine));

        assertFalse(service.syncData("student", ByteBuffer.allocate(0)));
    }
}
