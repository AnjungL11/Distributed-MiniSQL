package com.minisql.client;

import com.minisql.client.cache.MetadataCache;
import com.minisql.client.model.Endpoint;
import com.minisql.client.model.SqlResult;
import com.minisql.client.rpc.MasterRpcClient;
import com.minisql.client.rpc.RegionRpcClient;
import com.minisql.client.sql.SqlOperation;
import com.minisql.client.sql.SqlStatement;
import com.minisql.client.sql.SqlStatementParser;
import com.minisql.client.zk.ActiveMasterResolver;
import com.minisql.client.zk.TableMetadataResolver;
import com.minisql.common.JsonUtil;
import com.minisql.common.TableSchema;
import com.minisql.rpc.master.RoutingResponse;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import java.net.InetAddress;
import java.util.UUID;
import java.util.Optional;

public class MiniSqlClient implements AutoCloseable {
    private final MetadataCache metadataCache;
    private final ActiveMasterResolver masterResolver;
    private final MasterRpcClient masterRpcClient;
    private final RegionRpcClient regionRpcClient;
    private final SqlStatementParser statementParser;
    private final TableMetadataResolver tableMetadataResolver;

    // z增加用于维持客户端临时节点的 ZK 客户端
    private final CuratorFramework zkClient;

    public MiniSqlClient() {
        this(new MetadataCache());
    }

    public MiniSqlClient(MetadataCache metadataCache) {
        this.metadataCache = metadataCache;
        this.masterResolver = new ActiveMasterResolver(metadataCache);
        this.masterRpcClient = new MasterRpcClient();
        this.regionRpcClient = new RegionRpcClient();
        this.statementParser = new SqlStatementParser();
        this.tableMetadataResolver = new TableMetadataResolver(metadataCache);

        // 初始化 ZK 客户端并注册 Client 节点
        this.zkClient = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:22181")
                .sessionTimeoutMs(40000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("minisql")
                .build();
        this.zkClient.start();
        registerClientNode();
    }

    // 在 Zookeeper 中注册临时节点
    private void registerClientNode() {
        try {
            String clientIp = InetAddress.getLocalHost().getHostAddress();
            String clientId = clientIp + "-" + UUID.randomUUID().toString().substring(0, 4);
            String clientZkPath = "/clients/" + clientId; // 路径

            this.zkClient.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(clientZkPath, "Online".getBytes());
        } catch (Exception e) {
            System.err.println("Warning: Failed to register Client to Zookeeper: " + e.getMessage());
        }
    }

    public SqlResult execute(String sql) {
        SqlStatement statement;
        try {
            statement = statementParser.parse(sql);
        } catch (Exception e) {
            return SqlResult.failure(e.getMessage());
        }

        if (!statement.requiresTableRouting()) {
            return SqlResult.failure("Unsupported SQL or missing table name: " + statement.getNormalizedSql());
        }

        try {
            if (statement.getOperation() == SqlOperation.CREATE_TABLE) {
                tableMetadataResolver.cache(statement.getSchema());
                // createTableMetadata(statement.getTableName());
                // 将解析出的 Schema 传给 Master 进行带字段建表
                createTableMetadata(statement.getTableName(), statement.getSchema());
            } else {
                tableMetadataResolver.resolve(statement.getTableName());
            }
            Endpoint region = resolveRegion(statement.getTableName());
            String json = regionRpcClient.executeSql(region, statement.getNormalizedSql());
            SqlResult result = JsonUtil.fromJson(json, SqlResult.class);
            if (statement.getOperation() == SqlOperation.DROP_TABLE && result.isSuccess()) {
                metadataCache.invalidateTableRoute(statement.getTableName());
                tableMetadataResolver.invalidate(statement.getTableName());
                // 通知 Master 从 Zookeeper 删除表元数据
                dropTableMetadata(statement.getTableName());
            }
            return result;
        } catch (Exception firstFailure) {
            metadataCache.invalidateTableRoute(statement.getTableName());
            try {
                Endpoint region = resolveRegion(statement.getTableName());
                String json = regionRpcClient.executeSql(region, statement.getNormalizedSql());
                return JsonUtil.fromJson(json, SqlResult.class);
            } catch (Exception secondFailure) {
                return SqlResult.failure(secondFailure.getMessage());
            }
        }
    }

    public void invalidateRoute(String tableName) {
        metadataCache.invalidateTableRoute(tableName);
    }

    public Optional<TableSchema> getTableSchema(String tableName) {
        return tableMetadataResolver.resolve(tableName);
    }

    public void invalidateTableMetadata(String tableName) {
        metadataCache.invalidateTableRoute(tableName);
        tableMetadataResolver.invalidate(tableName);
    }

    private void createTableMetadata(String tableName, TableSchema schema) throws Exception {
        Endpoint master = masterResolver.resolve();
        try {
            String schemaJson = JsonUtil.toJson(schema);
            boolean created = masterRpcClient.createTable(master, tableName, schemaJson);
            if (!created) {
                metadataCache.invalidateTableRoute(tableName);
                throw new IllegalStateException("Master 拒绝建表，可能是该表 (" + tableName + ") 已存在。");
            }
        } catch (Exception e) {
            masterResolver.invalidate();
            throw e;
        }
    }

    // 新增删除表元数据的逻辑
    private void dropTableMetadata(String tableName) {
        Endpoint master = masterResolver.resolve();
        try {
            masterRpcClient.dropTable(master, tableName);
        } catch (Exception e) {
            masterResolver.invalidate();
            System.err.println("Warning: failed to drop metadata on Master for table: " + tableName);
        }
    }

    private Endpoint resolveRegion(String tableName) throws Exception {
        return metadataCache.getTableRoute(tableName).orElseGet(() -> fetchRegionRoute(tableName));
    }

    private Endpoint fetchRegionRoute(String tableName) {
        Endpoint master = masterResolver.resolve();
        try {
            RoutingResponse response = masterRpcClient.getTableRouting(master, tableName);
            if (!response.isIsSuccess()) {
                throw new IllegalStateException("No route found for table: " + tableName);
            }
            Endpoint endpoint = new Endpoint(response.getRegionServerIp(), response.getPort());
            metadataCache.updateTableRoute(tableName, endpoint);
            return endpoint;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            masterResolver.invalidate();
            throw new IllegalStateException("Failed to fetch route for table " + tableName + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        masterResolver.close();
        tableMetadataResolver.close();

        // Client 退出时，安全关闭 ZooKeeper 连接，自动触发临时节点的删除
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
