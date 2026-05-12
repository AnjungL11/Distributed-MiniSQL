package com.minisql.common;

public class ColumnInfo {
    private String columnName;
    private ColumnType type;
    private int length; // 主要针对 CHAR/STRING 类型
    private boolean isPrimaryKey;
    private boolean isNullable;

    // 无参构造函数用于 Jackson 反序列化
    public ColumnInfo() {}

    public ColumnInfo(String columnName, ColumnType type, int length, boolean isPrimaryKey) {
        this.columnName = columnName;
        this.type = type;
        this.length = length;
        this.isPrimaryKey = isPrimaryKey;
        this.isNullable = !isPrimaryKey; // 默认主键不为空
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public ColumnType getType() {
        return type;
    }

    public void setType(ColumnType type) {
        this.type = type;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public void setNullable(boolean nullable) {
        isNullable = nullable;
    }
}