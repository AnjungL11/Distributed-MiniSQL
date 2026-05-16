package com.minisql.client.cache;

import com.minisql.client.model.Endpoint;
import com.minisql.common.TableSchema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MetadataCache {
    private final AtomicReference<Endpoint> activeMaster = new AtomicReference<>();
    private final Map<String, Endpoint> tableRoutes = new ConcurrentHashMap<>();
    private final Map<String, TableSchema> tableSchemas = new ConcurrentHashMap<>();

    public Optional<Endpoint> getActiveMaster() {
        return Optional.ofNullable(activeMaster.get());
    }

    public void updateActiveMaster(Endpoint endpoint) {
        activeMaster.set(endpoint);
    }

    public void invalidateActiveMaster() {
        activeMaster.set(null);
    }

    public Optional<Endpoint> getTableRoute(String tableName) {
        return Optional.ofNullable(tableRoutes.get(normalizeTableName(tableName)));
    }

    public void updateTableRoute(String tableName, Endpoint endpoint) {
        tableRoutes.put(normalizeTableName(tableName), endpoint);
    }

    public void invalidateTableRoute(String tableName) {
        tableRoutes.remove(normalizeTableName(tableName));
    }

    public void clearTableRoutes() {
        tableRoutes.clear();
    }

    public Optional<TableSchema> getTableSchema(String tableName) {
        return Optional.ofNullable(tableSchemas.get(normalizeTableName(tableName)));
    }

    public void updateTableSchema(String tableName, TableSchema schema) {
        tableSchemas.put(normalizeTableName(tableName), schema);
    }

    public void invalidateTableSchema(String tableName) {
        tableSchemas.remove(normalizeTableName(tableName));
    }

    public void clearTableSchemas() {
        tableSchemas.clear();
    }

    private String normalizeTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        return tableName.trim().toLowerCase();
    }
}
