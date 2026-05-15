package com.minisql.region.rpc;

public class RegionSyncPayload {
    private String syncId;
    private String tableName;
    private String sqlStatement;

    public RegionSyncPayload() {
    }

    public RegionSyncPayload(String tableName, String sqlStatement) {
        this.syncId = java.util.UUID.randomUUID().toString();
        this.tableName = tableName;
        this.sqlStatement = sqlStatement;
    }

    public static RegionSyncPayload forSql(String tableName, String sqlStatement) {
        return new RegionSyncPayload(tableName, sqlStatement);
    }

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
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
