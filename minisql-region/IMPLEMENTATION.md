# Region Server Implementation Notes

## Responsibilities Covered

The Region Server module now provides:

- Local SQL read/write service through `RegionService.executeSQL`.
- In-memory table organization through `MemStore` and `TableStore`.
- Disk persistence through `DiskStore` snapshots and `WalLog`.
- Zookeeper liveness registration through ephemeral `/regionservers/[ip:port]` nodes.
- Periodic heartbeat data refresh in Zookeeper.
- Active Master endpoint tracking through `/active_master`.
- Master heartbeat RPC using `MasterService.sendHeartbeat`.
- Minimal follower replication through `RegionService.syncData`.

## Runtime Flow

`RegionServer.start()` performs the local runtime wiring:

1. Starts `RegionStorageEngine`.
2. Wires `SqlExecutor` into `RegionServiceImpl`.
3. Starts `RegionRpcServer`.
4. If Zookeeper is enabled:
   - starts `ZkClient`,
   - creates required ZK directories,
   - registers the Region ephemeral node,
   - starts active master polling,
   - starts Master heartbeat.

`RegionServer.close()` reverses these resources and flushes storage before shutdown.

## Storage

Storage root layout:

```text
[storageRoot]/
  wal.log
  tables/
    [tableName]/
      schema.json
      data.jsonl
```

Write operations append WAL before mutating `MemStore`. `flush()` persists table snapshots and truncates WAL. Restart recovery loads snapshots first, then replays WAL.

## RPC Result Contract

`executeSQL` returns `SqlExecutionResult` serialized as JSON:

```json
{
  "success": true,
  "message": "OK",
  "columns": ["id", "name"],
  "rows": [["1", "Alice"]],
  "affectedRows": 1
}
```

`syncData` accepts `RegionSyncPayload` JSON bytes:

```json
{
  "syncId": "...",
  "tableName": "student",
  "sqlStatement": "INSERT INTO student (id, name) VALUES (1, 'Alice')"
}
```

Followers remember applied `syncId` values for in-process idempotence.

## Zookeeper Contract

All paths are relative to the Curator namespace `minisql`.

- `/regionservers/[ip:port]`: ephemeral Region node.
- `/active_master`: current Master endpoint as `host:port`.

Region znode payload:

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

## Test Coverage

The Region module tests cover:

- config parsing and lifecycle idempotence,
- WAL, MemStore, flush, and restart recovery,
- SQL parser and executor CRUD,
- Thrift RPC direct client calls,
- Zookeeper ephemeral registration and heartbeat refresh,
- active master endpoint refresh,
- Master heartbeat RPC with fake Master,
- follower replication and duplicate sync id handling,
- integrated RPC + ZK + recovery flow.
