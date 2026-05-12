package com.minisql.region.rpc;

public class RegionSyncPayload {
    private String tableName;
    private String sqlStatement;

    public RegionSyncPayload() {
    }

    public RegionSyncPayload(String tableName, String sqlStatement) {
        this.tableName = tableName;
        this.sqlStatement = sqlStatement;
    }

    public static RegionSyncPayload forSql(String tableName, String sqlStatement) {
        return new RegionSyncPayload(tableName, sqlStatement);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSqlStatement() {
        return sqlStatement;
    }

    public void setSqlStatement(String sqlStatement) {
        this.sqlStatement = sqlStatement;
    }
}
