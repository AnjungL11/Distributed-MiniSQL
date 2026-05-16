package com.minisql.client.sql;

import com.minisql.common.TableSchema;

public class SqlStatement {
    private final SqlOperation operation;
    private final String tableName;
    private final String normalizedSql;
    private final TableSchema schema;

    public SqlStatement(SqlOperation operation, String tableName, String normalizedSql) {
        this(operation, tableName, normalizedSql, null);
    }

    public SqlStatement(SqlOperation operation, String tableName, String normalizedSql, TableSchema schema) {
        this.operation = operation;
        this.tableName = tableName;
        this.normalizedSql = normalizedSql;
        this.schema = schema;
    }

    public SqlOperation getOperation() {
        return operation;
    }

    public String getTableName() {
        return tableName;
    }

    public String getNormalizedSql() {
        return normalizedSql;
    }

    public TableSchema getSchema() {
        return schema;
    }

    public boolean requiresTableRouting() {
        return operation != SqlOperation.UNKNOWN && tableName != null && !tableName.isEmpty();
    }
}
