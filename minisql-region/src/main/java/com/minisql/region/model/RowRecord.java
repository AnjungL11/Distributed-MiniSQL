package com.minisql.region.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A persisted row with a storage key and ordered column values.
 */
public class RowRecord {
    private String rowKey;
    private Map<String, String> values = new LinkedHashMap<>();

    public RowRecord() {
    }

    public RowRecord(String rowKey, Map<String, String> values) {
        this.rowKey = requireText(rowKey, "rowKey");
        this.values = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));
    }

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
