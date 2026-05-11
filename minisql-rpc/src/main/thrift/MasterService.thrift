namespace java com.zju.minisql.rpc.master

// Region 汇报的心跳结构
struct HeartbeatRequest {
    1: string regionServerIp,
    2: i32 port,
    3: i32 loadScore,
    4: list<string> holdingRegions
}

struct HeartbeatResponse {
    1: bool isSuccess,
    2: string instruction // 例如返回 "MigrateRegion" 等调度指令
}

// Client 获取路由的结构
struct RoutingResponse {
    1: bool isSuccess,
    2: string regionServerIp,
    3: i32 port
}

service MasterService {
    // 供 Region Server 调用
    HeartbeatResponse sendHeartbeat(1: HeartbeatRequest request);
    
    // 供 Client 调用
    RoutingResponse getTableRouting(1: string tableName);
    
    // DDL 操作
    bool createTable(1: string tableName);
}