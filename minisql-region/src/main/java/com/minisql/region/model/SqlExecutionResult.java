package com.minisql.region.model;

import java.util.ArrayList;
import java.util.List;

public class SqlExecutionResult {
    private boolean success;
    private String message;
    private List<String> columns = new ArrayList<>();
    private List<List<String>> rows = new ArrayList<>();
    private int affectedRows;

    public SqlExecutionResult() {
    }

    public static SqlExecutionResult success(String message, int affectedRows) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.success = true;
        result.message = message;
        result.affectedRows = affectedRows;
        return result;
    }

    public static SqlExecutionResult query(List<String> columns, List<List<String>> rows) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.success = true;
        result.message = "OK";
        result.columns = new ArrayList<>(columns);
        result.rows = new ArrayList<>(rows);
        result.affectedRows = rows.size();
        return result;
    }

    public static SqlExecutionResult failure(String message) {
        SqlExecutionResult result = new SqlExecutionResult();
        result.success = false;
        result.message = message;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(int affectedRows) {
        this.affectedRows = affectedRows;
    }
}
