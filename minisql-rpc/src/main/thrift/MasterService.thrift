namespace java com.minisql.rpc.master

// Region Server -> Master: heartbeat state.
struct HeartbeatRequest {
    1: string regionServerIp,       // Region RPC host or IP.
    2: i32 port,                    // Region RPC port.
    3: i32 loadScore,               // Lower means lighter load.
    4: list<string> holdingRegions  // Tables currently held by this Region.
}

struct HeartbeatResponse {
    1: bool isSuccess,
    2: string instruction // Scheduling instruction, e.g. "NONE" or "MigrateRegion".
}

// Master -> Client: table routing result.
struct RoutingResponse {
    1: bool isSuccess,       // false means no usable route was found.
    2: string regionServerIp,
    3: i32 port
}

service MasterService {
    // Called by Region Server.
    HeartbeatResponse sendHeartbeat(1: HeartbeatRequest request);

    // Called by Client. Client caches successful tableName -> Region endpoint mappings.
    RoutingResponse getTableRouting(1: string tableName);

    // Called by Client before sending CREATE TABLE SQL to Region.
    bool createTable(1: string tableName);
}
