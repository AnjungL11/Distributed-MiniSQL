package com.minisql.region.service;

import com.minisql.common.JsonUtil;
import com.minisql.region.model.SqlExecutionResult;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.TException;

import java.nio.ByteBuffer;

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
        return tableName != null && !tableName.trim().isEmpty() && data != null && data.remaining() > 0;
    }
}
