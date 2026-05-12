package com.minisql.region.storage;

import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class MemStore {
    private final Map<String, TableStore> tables = new LinkedHashMap<>();

    public synchronized void createTable(TableSchema schema) {
        String tableName = schema.getTableName();
        if (tables.containsKey(tableName)) {
            throw new IllegalArgumentException("Table already exists: " + tableName);
        }
        tables.put(tableName, new TableStore(schema));
    }

    public synchronized void putTable(TableStore tableStore) {
        tables.put(tableStore.getTableName(), tableStore);
    }

    public synchronized void dropTable(String tableName) {
        requireTable(tableName);
        tables.remove(tableName);
    }

    public synchronized RowRecord upsert(String tableName, RowRecord row) {
        return requireTable(tableName).upsert(row);
    }

    public synchronized RowRecord insert(String tableName, Map<String, String> values) {
        return requireTable(tableName).insert(values);
    }

    public synchronized RowRecord buildInsertRow(String tableName, Map<String, String> values) {
        return requireTable(tableName).buildInsertRow(values);
    }

    public synchronized RowRecord update(String tableName, String rowKey, Map<String, String> updates) {
        return requireTable(tableName).update(rowKey, updates);
    }

    public synchronized RowRecord buildUpdatedRow(String tableName, String rowKey, Map<String, String> updates) {
        return requireTable(tableName).buildUpdatedRow(rowKey, updates);
    }

    public synchronized RowRecord delete(String tableName, String rowKey) {
        return requireTable(tableName).delete(rowKey);
    }

    public synchronized Optional<RowRecord> get(String tableName, String rowKey) {
        return requireTable(tableName).get(rowKey);
    }

    public synchronized List<RowRecord> scan(String tableName) {
        return requireTable(tableName).scan();
    }

    public synchronized Optional<TableSchema> getSchema(String tableName) {
        TableStore tableStore = tables.get(tableName);
        return tableStore == null ? Optional.empty() : Optional.of(tableStore.getSchema());
    }

    public synchronized List<TableStore> snapshotTables() {
        return new ArrayList<>(tables.values());
    }

    public synchronized Set<String> tableNames() {
        return Collections.unmodifiableSet(new TreeSet<>(tables.keySet()));
    }

    public synchronized boolean containsTable(String tableName) {
        return tables.containsKey(tableName);
    }

    private TableStore requireTable(String tableName) {
        TableStore tableStore = tables.get(tableName);
        if (tableStore == null) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }
        return tableStore;
    }
}
