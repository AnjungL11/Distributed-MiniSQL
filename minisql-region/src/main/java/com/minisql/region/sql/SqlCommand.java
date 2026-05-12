package com.minisql.region.sql;

import com.minisql.common.TableSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlCommand {
    private SqlCommandType type;
    private String tableName;
    private TableSchema schema;
    private List<String> columns = new ArrayList<>();
    private List<String> values = new ArrayList<>();
    private Map<String, String> assignments = new LinkedHashMap<>();
    private SqlCondition condition;

    public SqlCommandType getType() {
        return type;
    }

    public void setType(SqlCommandType type) {
        this.type = type;
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

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public Map<String, String> getAssignments() {
        return assignments;
    }

    public void setAssignments(Map<String, String> assignments) {
        this.assignments = assignments;
    }

    public SqlCondition getCondition() {
        return condition;
    }

    public void setCondition(SqlCondition condition) {
        this.condition = condition;
    }
}
