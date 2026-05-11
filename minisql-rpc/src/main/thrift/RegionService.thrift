namespace java com.zju.minisql.rpc.region

service RegionService {
    // 供 Client 执行具体 SQL
    string executeSQL(1: string sqlStatement);
    
    // 供 Master 发送主从同步指令
    bool syncData(1: string tableName, 2: binary data);
}