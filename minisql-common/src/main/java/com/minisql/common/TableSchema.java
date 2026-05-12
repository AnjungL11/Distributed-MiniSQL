package com.minisql.common;

import java.util.List;

public class TableSchema {
    private String tableName;
    private List<ColumnInfo> columns;
    private String primaryKey;

    // 无参构造函数用于 Jackson 反序列化
    public TableSchema() {}

    public TableSchema(String tableName, List<ColumnInfo> columns) {
        this.tableName = tableName;
        this.columns = columns;
        
        // 自动解析出主键的字段名
        for (ColumnInfo col : columns) {
            if (col.isPrimaryKey()) {
                this.primaryKey = col.getColumnName();
                break;
            }
        }
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }
}