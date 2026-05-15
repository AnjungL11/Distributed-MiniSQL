package com.minisql.region.storage;

import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;

import java.util.UUID;

public class WalEntry {
    private String entryId;
    private long timestampMillis;
    private WalOperation operation;
    private String tableName;
    private TableSchema schema;
    private RowRecord row;
    private String rowKey;

    public WalEntry() {
    }

    public static WalEntry createTable(TableSchema schema) {
        WalEntry entry = base(WalOperation.CREATE_TABLE, schema.getTableName());
        entry.schema = schema;
        return entry;
    }

    public static WalEntry dropTable(String tableName) {
        return base(WalOperation.DROP_TABLE, tableName);
    }

    public static WalEntry upsert(String tableName, RowRecord row) {
        WalEntry entry = base(WalOperation.UPSERT_ROW, tableName);
        entry.row = row;
        entry.rowKey = row.getRowKey();
        return entry;
    }

    public static WalEntry deleteRow(String tableName, String rowKey) {
        WalEntry entry = base(WalOperation.DELETE_ROW, tableName);
        entry.rowKey = rowKey;
        return entry;
    }

    private static WalEntry base(WalOperation operation, String tableName) {
        WalEntry entry = new WalEntry();
        entry.entryId = UUID.randomUUID().toString();
        entry.timestampMillis = System.currentTimeMillis();
        entry.operation = operation;
        entry.tableName = tableName;
        return entry;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }

    public WalOperation getOperation() {
        return operation;
    }

    public void setOperation(WalOperation operation) {
        this.operation = operation;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public TableSchema getSchema() {
        return schema;
    }

    public void setSchema(TableSchema schema) {
        this.schema = schema;
    }

    public RowRecord getRow() {
        return row;
    }

    public void setRow(RowRecord row) {
        this.row = row;
    }

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }
}
