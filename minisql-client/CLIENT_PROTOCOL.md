# Client Module and RPC Contract

This file documents the Client-side contract without changing Master or Region implementation ownership.

## Client Responsibilities

- Provide `MiniSqlClient.execute(String sql)` as the simple Java API.
- Provide `ClientMain` as the interactive CLI entry point.
- Resolve the active Master from Zookeeper path `/active_master`, with config fallback to `master.rpc.host` and `master.rpc.port`.
- Cache metadata on the client side:
  - active Master endpoint
  - table name to Region endpoint
  - table name to `TableSchema`
- Invalidate cached table routes when a Region call fails, then retry once after re-fetching routing from Master.
- Parse SQL only far enough to identify operation type, table name, and `CREATE TABLE` schema for local metadata caching.

## Current RPC Calls Used by Client

Master service:

```thrift
RoutingResponse getTableRouting(1: string tableName)
bool createTable(1: string tableName)
```

Region service:

```thrift
string executeSQL(1: string sqlStatement)
```

The Region return string is parsed as:

```json
{
  "success": true,
  "message": "OK",
  "columns": ["id", "name"],
  "rows": [["1", "Alice"]],
  "affectedRows": 1
}
```

## Integration Sequence

1. Client parses SQL and extracts the table name.
2. For `CREATE TABLE`, Client calls `MasterService.createTable(tableName)` and caches the parsed schema locally.
3. Client asks Master for table routing on cache miss.
4. Client calls `RegionService.executeSQL(sql)` on the routed Region.
5. Client parses returned JSON into `SqlResult`.
6. On Region RPC failure, Client invalidates only the affected table route and retries once.

## Ownership Boundary

Client owns the API, local cache, routing consumption, and protocol documentation. Master owns actual route selection and load-balancing policy. Region owns SQL execution, storage, replication behavior, and heartbeat registration.
