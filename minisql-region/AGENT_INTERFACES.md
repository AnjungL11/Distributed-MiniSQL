# Region Server Agent Interface Contract

This document is written for other agents implementing Client, Master, or Zookeeper-adjacent work. Treat it as the current Region Server integration contract.

## Module Entry Point

Main class:

```text
com.minisql.region.RegionServer
```

Runtime arguments:

```text
--host <ip-or-host>
--port <region-rpc-port>
--storage <path>
--node-id <stable-node-id>
--heartbeat-interval-ms <milliseconds>
--rpc-timeout-ms <milliseconds>
--zookeeper-enabled <true|false>
```

Defaults come from `minisql-common/src/main/resources/application.properties`:

- `region.rpc.port`
- `region.storage.path`
- `client.rpc.timeout.ms`
- `region.zookeeper.enabled` defaults to `true` when absent.

## Thrift Service

Generated service:

```text
com.minisql.rpc.region.RegionService
```

Thrift definition:

```thrift
service RegionService {
    string executeSQL(1: string sqlStatement)
    bool syncData(1: string tableName, 2: binary data)
}
```

Transport/protocol used in tests:

```text
TThreadPoolServer + TBinaryProtocol + TSocket
```

Other modules can use the generated `RegionService.Client` directly.

## executeSQL Contract

Input:

```text
SQL string
```

Supported SQL subset:

```sql
CREATE TABLE table_name (column TYPE [PRIMARY KEY], ...)
DROP TABLE table_name
INSERT INTO table_name [(columns...)] VALUES (values...)
SELECT *|columns FROM table_name [WHERE column = value]
UPDATE table_name SET column = value [, ...] WHERE column = value
DELETE FROM table_name WHERE column = value
```

Supported column types map to `com.minisql.common.ColumnType`:

```text
INT, FLOAT, STRING, CHAR
```

Return:

`executeSQL` always returns a JSON string representing `com.minisql.region.model.SqlExecutionResult`.

Schema:

```json
{
  "success": true,
  "message": "OK",
  "columns": ["id", "name"],
  "rows": [["1", "Alice"]],
  "affectedRows": 1
}
```

Failure example:

```json
{
  "success": false,
  "message": "Unsupported SQL: ALTER TABLE student ADD age INT",
  "columns": [],
  "rows": [],
  "affectedRows": 0
}
```

Important behavior:

- Invalid SQL is returned as a failure JSON result, not thrown to the caller as a normal path.
- `WHERE` currently supports equality only: `column = value`.
- `executeSQL` does not contact Master for metadata. It executes against local Region storage.
- Mutating SQL may be replicated to configured followers when a `ReplicationManager` is attached.

## syncData Contract

Purpose:

Master or leader Region can call follower Region `syncData` to apply a replicated SQL mutation.

Input:

```text
tableName: target table name
data: UTF-8 JSON bytes for RegionSyncPayload
```

Payload class:

```text
com.minisql.region.rpc.RegionSyncPayload
```

Payload schema:

```json
{
  "syncId": "uuid-or-stable-id",
  "tableName": "student",
  "sqlStatement": "INSERT INTO student (id, name) VALUES (1, 'Alice')"
}
```

Return:

```text
true  -> payload was accepted or already applied
false -> invalid payload, table mismatch, or SQL execution failed
```

Important behavior:

- `tableName` argument must match payload `tableName` when payload tableName is present.
- `syncId` is used for in-process idempotence. Re-sending the same payload returns `true` after the first successful application.
- A follower executing `syncData` does not trigger outward replication through `RegionServiceImpl`.
- Applied sync SQL writes WAL through the normal storage path, so unflushed data is recoverable after restart.

## Zookeeper Contract

Curator namespace:

```text
minisql
```

All paths below are relative to that namespace.

Region registration path:

```text
/regionservers/[ip:port]
```

Node type:

```text
EPHEMERAL
```

Region znode payload:

Class:

```text
com.minisql.region.model.RegionHeartbeatState
```

JSON schema:

```json
{
  "nodeId": "127.0.0.1:9090",
  "ip": "127.0.0.1",
  "port": 9090,
  "loadScore": 1,
  "holdingRegions": ["student"],
  "lastHeartbeatMillis": 1710000000000
}
```

Active Master path consumed by Region:

```text
/active_master
```

Expected data:

```text
host:port
```

Example:

```text
127.0.0.1:8080
```

Region behavior:

- Creates `/regionservers` if missing.
- Creates or refreshes `/regionservers/[ip:port]`.
- Updates znode data every `heartbeatIntervalMs`.
- Deletes the znode on clean close; Zookeeper removes it on session loss.
- Polls `/active_master` and parses it as `MasterEndpoint`.

## Master Heartbeat Contract

Region calls existing generated service:

```text
com.minisql.rpc.master.MasterService.sendHeartbeat
```

Request mapping:

```text
HeartbeatRequest.regionServerIp <- RegionHeartbeatState.ip
HeartbeatRequest.port           <- RegionHeartbeatState.port
HeartbeatRequest.loadScore      <- RegionHeartbeatState.loadScore
HeartbeatRequest.holdingRegions <- RegionHeartbeatState.holdingRegions
```

Response handling:

```text
HeartbeatResponse.isSuccess
HeartbeatResponse.instruction
```

Current instruction behavior:

- Empty or `NONE` is ignored as success.
- Unknown instructions are recorded and ignored without stopping the heartbeat loop.
- Migration/load-balancing instruction execution is not implemented yet.

Network failure behavior:

- A failed heartbeat returns `false` inside `MasterHeartbeatTask`.
- Local SQL execution and Region RPC remain available.

## Local Storage Contract

Storage root layout:

```text
[storageRoot]/
  wal.log
  tables/
    [tableName]/
      schema.json
      data.jsonl
```

Write path:

```text
append WAL -> mutate MemStore
```

Recovery path:

```text
load snapshots -> replay wal.log
```

Flush path:

```text
write schema/data snapshots -> truncate wal.log
```

## Useful Integration Sequence

Client or integration agent can verify a Region with:

1. Start `RegionServer` with a port and storage directory.
2. Call `executeSQL("CREATE TABLE student (id INT PRIMARY KEY, name STRING)")`.
3. Call `executeSQL("INSERT INTO student (id, name) VALUES (1, 'Alice')")`.
4. Call `executeSQL("SELECT name FROM student WHERE id = 1")`.
5. Parse returned JSON as `SqlExecutionResult`.

Expected query result:

```json
{
  "success": true,
  "columns": ["name"],
  "rows": [["Alice"]],
  "affectedRows": 1
}
```

## Known Limits

- `executeSQL` returns JSON string because current thrift IDL returns `string`.
- SQL parser is deliberately small; no joins, order by, group by, indexes, transactions, or complex expressions.
- Replication peer discovery is not automatic yet. `ReplicationManager.setPeers(...)` must be called by orchestration code or a later Master instruction implementation.
- Replication idempotence is in-memory by `syncId`; persisted idempotence can be added later if required.
- Master migration instructions are only recorded and logged at this step.
