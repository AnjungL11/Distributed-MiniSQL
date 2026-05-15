package com.minisql.region.sql;

import com.minisql.common.ColumnInfo;
import com.minisql.common.TableSchema;
import com.minisql.region.model.RowRecord;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.storage.RegionStorageEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SqlExecutor {
    private final RegionStorageEngine storageEngine;
    private final SqlParser parser;

    public SqlExecutor(RegionStorageEngine storageEngine) {
        this.storageEngine = storageEngine;
        this.parser = new SqlParser();
    }

    public SqlExecutionResult execute(String sql) {
        try {
            SqlCommand command = parser.parse(sql);
            switch (command.getType()) {
                case CREATE_TABLE:
                    storageEngine.createTable(command.getSchema());
                    return SqlExecutionResult.success("Table created: " + command.getTableName(), 0);
                case DROP_TABLE:
                    storageEngine.dropTable(command.getTableName());
                    return SqlExecutionResult.success("Table dropped: " + command.getTableName(), 0);
                case INSERT:
                    storageEngine.insert(command.getTableName(), buildInsertValues(command));
                    return SqlExecutionResult.success("Row inserted", 1);
                case SELECT:
                    return select(command);
                case UPDATE:
                    return update(command);
                case DELETE:
                    return delete(command);
                default:
                    return SqlExecutionResult.failure("Unsupported SQL command");
            }
        } catch (Exception e) {
            return SqlExecutionResult.failure(e.getMessage());
        }
    }

    private Map<String, String> buildInsertValues(SqlCommand command) {
        TableSchema schema = storageEngine.getSchema(command.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table does not exist: " + command.getTableName()));
        List<String> columns = command.getColumns();
        if (columns.isEmpty()) {
            columns = schemaColumns(schema);
        }
        if (columns.size() != command.getValues().size()) {
            throw new IllegalArgumentException("Column count does not match value count");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            values.put(columns.get(i), command.getValues().get(i));
        }
        return values;
    }

    private SqlExecutionResult select(SqlCommand command) {
        TableSchema schema = storageEngine.getSchema(command.getTableName())
                .orElseThrow(() -> new IllegalArgumentException("Table does not exist: " + command.getTableName()));
        List<String> selectedColumns = command.getColumns().isEmpty()
                ? schemaColumns(schema)
                : command.getColumns();
        List<List<String>> rows = new ArrayList<>();
        for (RowRecord row : storageEngine.scan(command.getTableName())) {
            if (command.getCondition() != null && !command.getCondition().matches(row)) {
                continue;
            }
            List<String> output = new ArrayList<>();
            for (String column : selectedColumns) {
                output.add(row.getValues().get(column));
            }
            rows.add(output);
        }
        return SqlExecutionResult.query(selectedColumns, rows);
    }

    private SqlExecutionResult update(SqlCommand command) throws IOException {
        int affected = 0;
        List<RowRecord> rows = new ArrayList<>(storageEngine.scan(command.getTableName()));
        for (RowRecord row : rows) {
            if (command.getCondition().matches(row)) {
                storageEngine.update(command.getTableName(), row.getRowKey(), command.getAssignments());
                affected++;
            }
        }
        return SqlExecutionResult.success("Rows updated", affected);
    }

    private SqlExecutionResult delete(SqlCommand command) throws IOException {
        int affected = 0;
        List<RowRecord> rows = new ArrayList<>(storageEngine.scan(command.getTableName()));
        for (RowRecord row : rows) {
            if (command.getCondition().matches(row)) {
                storageEngine.delete(command.getTableName(), row.getRowKey());
                affected++;
            }
        }
        return SqlExecutionResult.success("Rows deleted", affected);
    }

    private List<String> schemaColumns(TableSchema schema) {
        List<String> columns = new ArrayList<>();
        for (ColumnInfo column : schema.getColumns()) {
            columns.add(column.getColumnName());
        }
        return columns;
    }
}
