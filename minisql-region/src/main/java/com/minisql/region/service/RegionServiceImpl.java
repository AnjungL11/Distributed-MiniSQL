package com.minisql.region.service;

import com.minisql.common.JsonUtil;
import com.minisql.region.replication.ReplicationManager;
import com.minisql.region.rpc.RegionSyncPayload;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.sql.SqlCommand;
import com.minisql.region.sql.SqlCommandType;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.sql.SqlParser;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegionServiceImpl implements RegionService.Iface {
    private final SqlExecutor sqlExecutor;
    private final SqlParser sqlParser;
    private final ReplicationManager replicationManager;
    private final Set<String> appliedSyncIds = ConcurrentHashMap.newKeySet();

    public RegionServiceImpl(SqlExecutor sqlExecutor) {
        this(sqlExecutor, null);
    }

    public RegionServiceImpl(SqlExecutor sqlExecutor, ReplicationManager replicationManager) {
        this.sqlExecutor = sqlExecutor;
        this.sqlParser = new SqlParser();
        this.replicationManager = replicationManager;
    }

    @Override
    public String executeSQL(String sqlStatement) throws TException {
        SqlExecutionResult result = sqlExecutor.execute(sqlStatement);
        replicateIfNeeded(sqlStatement, result);
        return JsonUtil.toJson(result);
    }

    @Override
    public boolean syncData(String tableName, ByteBuffer data) throws TException {
        if (tableName == null || tableName.trim().isEmpty() || data == null || data.remaining() == 0) {
            return false;
        }
        try {
            ByteBuffer copy = data.slice();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            RegionSyncPayload payload = JsonUtil.fromJson(new String(bytes, StandardCharsets.UTF_8), RegionSyncPayload.class);
            if (payload.getTableName() != null && !payload.getTableName().equals(tableName)) {
                return false;
            }
            if (payload.getSyncId() != null && appliedSyncIds.contains(payload.getSyncId())) {
                return true;
            }
            SqlExecutionResult result = sqlExecutor.execute(payload.getSqlStatement());
            if (result.isSuccess() && payload.getSyncId() != null) {
                appliedSyncIds.add(payload.getSyncId());
            }
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    private void replicateIfNeeded(String sqlStatement, SqlExecutionResult result) {
        if (!result.isSuccess() || replicationManager == null || !replicationManager.hasPeers()) {
            return;
        }
        try {
            SqlCommand command = sqlParser.parse(sqlStatement);
            if (isMutating(command.getType())) {
                replicationManager.replicateSql(command.getTableName(), sqlStatement);
            }
        } catch (Exception ignored) {
            // The local result is already known; replication will retry through future writes.
        }
    }

    private boolean isMutating(SqlCommandType type) {
        return type == SqlCommandType.CREATE_TABLE
                || type == SqlCommandType.DROP_TABLE
                || type == SqlCommandType.INSERT
                || type == SqlCommandType.UPDATE
                || type == SqlCommandType.DELETE;
    }
}
