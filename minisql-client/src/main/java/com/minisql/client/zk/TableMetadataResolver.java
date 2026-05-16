package com.minisql.client.zk;

import com.minisql.client.cache.MetadataCache;
import com.minisql.common.JsonUtil;
import com.minisql.common.TableSchema;
import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class TableMetadataResolver implements AutoCloseable {
    private final MetadataCache cache;
    private ZkClient zkClient;

    public TableMetadataResolver(MetadataCache cache) {
        this.cache = cache;
    }

    public Optional<TableSchema> resolve(String tableName) {
        Optional<TableSchema> cached = cache.getTableSchema(tableName);
        if (cached.isPresent()) {
            return cached;
        }
        return loadFromZookeeper(tableName);
    }

    public void cache(TableSchema schema) {
        if (schema != null && schema.getTableName() != null) {
            cache.updateTableSchema(schema.getTableName(), schema);
        }
    }

    public void invalidate(String tableName) {
        cache.invalidateTableSchema(tableName);
    }

    private Optional<TableSchema> loadFromZookeeper(String tableName) {
        try {
            ensureZkClient();
            String tablePath = ZkConstants.getTablePath(tableName);
            if (zkClient.getClient().checkExists().forPath(tablePath) == null) {
                return Optional.empty();
            }
            byte[] data = zkClient.getClient().getData().forPath(tablePath);
            TableSchema schema = JsonUtil.fromJson(new String(data, StandardCharsets.UTF_8), TableSchema.class);
            if (schema.getTableName() == null || schema.getTableName().trim().isEmpty()) {
                schema.setTableName(tableName);
            }
            cache.updateTableSchema(tableName, schema);
            return Optional.of(schema);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void ensureZkClient() {
        if (zkClient == null) {
            zkClient = new ZkClient();
            zkClient.start();
        }
    }

    @Override
    public void close() {
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
