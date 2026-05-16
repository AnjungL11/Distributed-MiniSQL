package com.minisql.client.model;

import java.util.ArrayList;
import java.util.List;

public class SqlResult {
    private boolean success;
    private String message;
    private List<String> columns = new ArrayList<>();
    private List<List<String>> rows = new ArrayList<>();
    private int affectedRows;

    public SqlResult() {
    }

    public static SqlResult failure(String message) {
        SqlResult result = new SqlResult();
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

    public boolean hasRows() {
        return rows != null && !rows.isEmpty();
    }
}
