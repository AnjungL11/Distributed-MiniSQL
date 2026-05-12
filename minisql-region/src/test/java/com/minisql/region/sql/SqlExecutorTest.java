package com.minisql.region.sql;

import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.storage.RegionStorageEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void executesCreateInsertSelectUpdateDelete() throws Exception {
        SqlExecutor executor = executor();

        assertTrue(executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)").isSuccess());
        assertTrue(executor.execute("INSERT INTO student (id, name) VALUES (1, 'Alice')").isSuccess());

        SqlExecutionResult selected = executor.execute("SELECT id, name FROM student WHERE id = 1");
        assertTrue(selected.isSuccess());
        assertEquals(1, selected.getRows().size());
        assertEquals("Alice", selected.getRows().get(0).get(1));

        SqlExecutionResult updated = executor.execute("UPDATE student SET name = 'Bob' WHERE id = 1");
        assertEquals(1, updated.getAffectedRows());
        assertEquals("Bob", executor.execute("SELECT name FROM student WHERE id = 1").getRows().get(0).get(0));

        SqlExecutionResult deleted = executor.execute("DELETE FROM student WHERE name = 'Bob'");
        assertEquals(1, deleted.getAffectedRows());
        assertEquals(0, executor.execute("SELECT * FROM student").getRows().size());
    }

    @Test
    void insertWithoutColumnListUsesSchemaOrder() throws Exception {
        SqlExecutor executor = executor();
        executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");

        SqlExecutionResult inserted = executor.execute("INSERT INTO student VALUES (2, 'Carol')");

        assertTrue(inserted.isSuccess());
        assertEquals("Carol", executor.execute("SELECT name FROM student WHERE id = 2").getRows().get(0).get(0));
    }

    @Test
    void invalidSqlReturnsFailureResult() throws Exception {
        SqlExecutor executor = executor();

        SqlExecutionResult result = executor.execute("BAD SQL");

        assertFalse(result.isSuccess());
    }

    @Test
    void createTableWritesSchemaOnDisk() throws Exception {
        SqlExecutor executor = executor();

        SqlExecutionResult result = executor.execute("CREATE TABLE student (id INT PRIMARY KEY, name STRING)");

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(tempDir.resolve("tables").resolve("student").resolve("schema.json")));
    }

    private SqlExecutor executor() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        return new SqlExecutor(engine);
    }
}
