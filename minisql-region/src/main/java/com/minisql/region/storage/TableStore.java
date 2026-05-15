package com.minisql.region.storage;

import com.minisql.common.ColumnInfo;
import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TableStore {
    private final TableSchema schema;
    private final Map<String, RowRecord> rows = new LinkedHashMap<>();

    public TableStore(TableSchema schema) {
        if (schema == null || schema.getTableName() == null || schema.getTableName().trim().isEmpty()) {
            throw new IllegalArgumentException("schema and tableName are required");
        }
        this.schema = schema;
    }

    public String getTableName() {
        return schema.getTableName();
    }

    public TableSchema getSchema() {
        return schema;
    }

    public RowRecord insert(Map<String, String> values) {
        RowRecord row = buildInsertRow(values);
        rows.put(row.getRowKey(), row);
        return copy(row);
    }

    public RowRecord buildInsertRow(Map<String, String> values) {
        Map<String, String> normalized = normalizeValues(values);
        String newRowKey = resolveRowKey(normalized);
        if (rows.containsKey(newRowKey)) {
            throw new IllegalArgumentException("Duplicate row key: " + newRowKey);
        }
        return new RowRecord(newRowKey, normalized);
    }

    public RowRecord upsert(RowRecord row) {
        RowRecord copy = copy(row);
        rows.put(copy.getRowKey(), copy);
        return copy(copy);
    }

    public RowRecord update(String rowKey, Map<String, String> updates) {
        RowRecord updated = buildUpdatedRow(rowKey, updates);
        rows.remove(rowKey);
        rows.put(updated.getRowKey(), updated);
        return copy(updated);
    }

    public RowRecord buildUpdatedRow(String rowKey, Map<String, String> updates) {
        RowRecord current = requireRow(rowKey);
        Map<String, String> merged = new LinkedHashMap<>(current.getValues());
        for (Map.Entry<String, String> update : updates.entrySet()) {
            requireColumn(update.getKey());
            merged.put(update.getKey(), update.getValue());
        }
        String newRowKey = resolveRowKey(merged);
        if (!newRowKey.equals(rowKey) && rows.containsKey(newRowKey)) {
            throw new IllegalArgumentException("Duplicate row key: " + newRowKey);
        }
        return new RowRecord(newRowKey, merged);
    }

    public RowRecord delete(String rowKey) {
        RowRecord removed = rows.remove(rowKey);
        if (removed == null) {
            throw new IllegalArgumentException("Row does not exist: " + rowKey);
        }
        return copy(removed);
    }

    public Optional<RowRecord> get(String rowKey) {
        RowRecord row = rows.get(rowKey);
        return row == null ? Optional.empty() : Optional.of(copy(row));
    }

    public List<RowRecord> scan() {
        List<RowRecord> snapshot = new ArrayList<>();
        for (RowRecord row : rows.values()) {
            snapshot.add(copy(row));
        }
        return snapshot;
    }

    private Map<String, String> normalizeValues(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (ColumnInfo column : schema.getColumns()) {
            String columnName = column.getColumnName();
            if (!values.containsKey(columnName)) {
                if (!column.isNullable()) {
                    throw new IllegalArgumentException("Missing non-null column: " + columnName);
                }
                normalized.put(columnName, null);
            } else {
                normalized.put(columnName, values.get(columnName));
            }
        }
        for (String columnName : values.keySet()) {
            requireColumn(columnName);
        }
        return normalized;
    }

    private String resolveRowKey(Map<String, String> values) {
        String primaryKey = schema.getPrimaryKey();
        if (primaryKey == null || primaryKey.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        String key = values.get(primaryKey);
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary key value is required: " + primaryKey);
        }
        return key;
    }

    private void requireColumn(String columnName) {
        for (ColumnInfo column : schema.getColumns()) {
            if (column.getColumnName().equals(columnName)) {
                return;
            }
        }
        throw new IllegalArgumentException("Unknown column: " + columnName);
    }

    private RowRecord requireRow(String rowKey) {
        RowRecord row = rows.get(rowKey);
        if (row == null) {
            throw new IllegalArgumentException("Row does not exist: " + rowKey);
        }
        return row;
    }

    private RowRecord copy(RowRecord row) {
        return new RowRecord(row.getRowKey(), row.getValues());
    }
}
