# Region Server Module

This module implements the data-node side of Distributed MiniSQL.

## Current Scope

Step 1 added the bootstrapping layer:

- `RegionServerConfig` reads Region Server runtime settings from defaults and command-line arguments.
- `RegionServerInfo` describes the node identity shared with Zookeeper and Master.
- `NetworkUtil` resolves a usable IPv4 address with a localhost fallback.
- `RegionServer` owns the module lifecycle and will later wire storage, RPC, Zookeeper, and Master heartbeat services.

Step 2 adds the local storage layer:

- `RegionStorageEngine` is the public storage facade.
- `MemStore` and `TableStore` keep table rows in memory for fast reads and writes.
- `WalLog` stores append-only JSON lines in `wal.log` before MemStore mutation.
- `DiskStore` writes table schemas and snapshots under `tables/[tableName]/`.

## Storage Layout

```text
[storageRoot]/
  wal.log
  tables/
    [tableName]/
      schema.json
      data.jsonl
```

`flush()` writes full table snapshots to `data.jsonl` and truncates `wal.log`.
On startup, the engine loads table snapshots first and then replays remaining WAL entries.

Step 3 adds the SQL execution layer:

- `SqlParser` parses a small MiniSQL subset into `SqlCommand`.
- `SqlExecutor` maps commands to storage operations and always returns `SqlExecutionResult`.
- `RegionServiceImpl.executeSQL` exposes the executor through the generated Thrift interface and serializes results as JSON strings.

Supported SQL subset:

- `CREATE TABLE table_name (column TYPE [PRIMARY KEY], ...)`
- `DROP TABLE table_name`
- `INSERT INTO table_name [(columns...)] VALUES (values...)`
- `SELECT *|columns FROM table_name [WHERE column = value]`
- `UPDATE table_name SET column = value [, ...] WHERE column = value`
- `DELETE FROM table_name WHERE column = value`

Unsupported or invalid SQL returns a JSON failure result instead of escaping as an unchecked exception.

Step 4 adds the Thrift RPC runtime:

- `RegionRpcServer` starts a `RegionService` processor on the configured port.
- `RegionServer.start()` now opens local storage, wires `SqlExecutor`, and starts RPC.
- `RegionServer.close()` stops RPC and flushes storage.
- `RegionSyncPayload` carries JSON SQL payloads for the current `syncData` contract.

`executeSQL` returns `SqlExecutionResult` JSON. `syncData(tableName, payload)` expects a UTF-8 JSON payload:

```json
{"tableName":"student","sqlStatement":"INSERT INTO student (id, name) VALUES (1, 'Alice')"}
```

The replication layer will later decide when and where to send these payloads.

Step 5 adds Zookeeper coordination:

- `RegionZkRegistry` creates an ephemeral node at `/regionservers/[ip:port]`.
- The znode payload is `RegionHeartbeatState` JSON with node id, endpoint, load score, held tables, and `lastHeartbeatMillis`.
- A scheduled heartbeat updates the same ephemeral node while the Curator session is alive.
- `ActiveMasterWatcher` tracks `/active_master` and exposes the latest `host:port` endpoint for later Master RPC heartbeats.

All paths are relative to the Curator namespace `minisql`, matching `docs/zookeeper_design.md`.

Step 6 adds Master heartbeats:

- `MasterClient` calls the existing `MasterService.sendHeartbeat` Thrift API.
- `MasterHeartbeatTask` periodically reads the current active master endpoint and sends Region state.
- `MasterInstructionHandler` records unsupported instructions and ignores them without stopping the heartbeat loop.
- Master network failures return `false` for that heartbeat and do not affect local storage or Region RPC service.

Step 7 adds the minimal replication path:

- `ReplicationManager` keeps follower endpoints and sends successful mutating SQL to them.
- `RegionPeerClient` calls follower `RegionService.syncData`.
- `RegionSyncPayload` includes a `syncId`, `tableName`, and original `sqlStatement`.
- `RegionServiceImpl` replicates successful `CREATE`, `DROP`, `INSERT`, `UPDATE`, and `DELETE` calls when a replication manager is attached.
- Follower `syncData` executes the payload locally and records `syncId` values so repeated payloads are idempotent within the process.

## Runtime Arguments

Arguments can be passed either as `--key=value` or `--key value`.

- `--host`: RPC host advertised to other modules.
- `--port`: Region RPC port. Defaults to `region.rpc.port` from `application.properties`.
- `--storage`: local storage root. Defaults to `region.storage.path`.
- `--node-id`: stable node id. Defaults to `host:port`.
- `--heartbeat-interval-ms`: heartbeat interval used by later Zookeeper/Master tasks.
- `--rpc-timeout-ms`: Thrift client timeout for outbound Region calls. Defaults to `client.rpc.timeout.ms`.
- `--zookeeper-enabled`: enable or disable Zookeeper registration. Defaults to `true`.

Example:

```bash
java com.minisql.region.RegionServer --port=9091 --storage=./data/region-9091
```

## Next Steps

The following implementation step will add integration coverage. It should include focused tests before commit.
