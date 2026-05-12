package com.minisql.region.service;

import com.minisql.common.JsonUtil;
import com.minisql.region.rpc.RegionSyncPayload;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RegionServiceImpl implements RegionService.Iface {
    private final SqlExecutor sqlExecutor;

    public RegionServiceImpl(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public String executeSQL(String sqlStatement) throws TException {
        SqlExecutionResult result = sqlExecutor.execute(sqlStatement);
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
            SqlExecutionResult result = sqlExecutor.execute(payload.getSqlStatement());
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
}
