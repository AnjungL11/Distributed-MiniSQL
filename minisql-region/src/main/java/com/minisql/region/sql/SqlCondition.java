package com.minisql.region.sql;

import com.minisql.region.model.RowRecord;

public class SqlCondition {
    private final String column;
    private final String value;

    public SqlCondition(String column, String value) {
        this.column = column;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public String getValue() {
        return value;
    }

    public boolean matches(RowRecord row) {
        String actual = row.getValues().get(column);
        return value == null ? actual == null : value.equals(actual);
    }
}
