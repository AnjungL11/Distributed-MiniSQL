namespace java com.minisql.rpc.region

service RegionService {
    // Called by Client to execute SQL on the routed Region.
    // Return value is a JSON string compatible with Client SqlResult:
    // {"success":true,"message":"OK","columns":[],"rows":[],"affectedRows":0}
    string executeSQL(1: string sqlStatement);

    // Called by Region leader or orchestration code to replicate SQL to a follower.
    bool syncData(1: string tableName, 2: binary data);
}
