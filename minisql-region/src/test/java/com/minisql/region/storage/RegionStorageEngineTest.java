package com.minisql.region.storage;

import com.minisql.common.ColumnInfo;
import com.minisql.common.ColumnType;
import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionStorageEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void createTablePersistsSchema() throws Exception {
        RegionStorageEngine engine = startedEngine();
        engine.createTable(studentSchema());

        assertTrue(Files.exists(engine.getDiskStore().schemaPath("student")));
        assertTrue(engine.getSchema("student").isPresent());
    }

    @Test
    void insertWritesWalAndMemStore() throws Exception {
        RegionStorageEngine engine = startedEngine();
        engine.createTable(studentSchema());

        RowRecord row = engine.insert("student", row("id", "1", "name", "Alice"));

        assertEquals("1", row.getRowKey());
        assertTrue(Files.size(engine.getWalLog().getWalPath()) > 0);
        assertEquals("Alice", engine.get("student", "1").orElseThrow().getValues().get("name"));
    }

    @Test
    void flushWritesSnapshotAndClearsWal() throws Exception {
        RegionStorageEngine engine = startedEngine();
        engine.createTable(studentSchema());
        engine.insert("student", row("id", "1", "name", "Alice"));

        engine.flush();

        assertTrue(Files.exists(engine.getDiskStore().dataPath("student")));
        assertTrue(Files.readString(engine.getDiskStore().dataPath("student")).contains("Alice"));
        assertEquals(0, Files.size(engine.getWalLog().getWalPath()));
    }

    @Test
    void restartReplaysWalAfterUnflushedInsert() throws Exception {
        RegionStorageEngine first = startedEngine();
        first.createTable(studentSchema());
        first.insert("student", row("id", "1", "name", "Alice"));

        RegionStorageEngine recovered = startedEngine();

        assertEquals("Alice", recovered.get("student", "1").orElseThrow().getValues().get("name"));
    }

    @Test
    void updateAndDeleteKeepDataConsistent() throws Exception {
        RegionStorageEngine engine = startedEngine();
        engine.createTable(studentSchema());
        engine.insert("student", row("id", "1", "name", "Alice"));

        engine.update("student", "1", row("name", "Bob"));
        assertEquals("Bob", engine.get("student", "1").orElseThrow().getValues().get("name"));

        engine.delete("student", "1");
        assertFalse(engine.get("student", "1").isPresent());
    }

    private RegionStorageEngine startedEngine() throws Exception {
        RegionStorageEngine engine = new RegionStorageEngine(tempDir);
        engine.start();
        return engine;
    }

    private TableSchema studentSchema() {
        return new TableSchema("student", Arrays.asList(
                new ColumnInfo("id", ColumnType.INT, 0, true),
                new ColumnInfo("name", ColumnType.STRING, 64, false)
        ));
    }

    private Map<String, String> row(String key, String value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    private Map<String, String> row(String key1, String value1, String key2, String value2) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put(key1, value1);
        row.put(key2, value2);
        return row;
    }
}
