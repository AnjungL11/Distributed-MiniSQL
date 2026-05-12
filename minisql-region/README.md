# Region Server Module

This module implements the data-node side of Distributed MiniSQL.

## Current Scope

Step 1 adds the bootstrapping layer only:

- `RegionServerConfig` reads Region Server runtime settings from defaults and command-line arguments.
- `RegionServerInfo` describes the node identity shared with Zookeeper and Master.
- `NetworkUtil` resolves a usable IPv4 address with a localhost fallback.
- `RegionServer` owns the module lifecycle and will later wire storage, RPC, Zookeeper, and Master heartbeat services.

## Runtime Arguments

Arguments can be passed either as `--key=value` or `--key value`.

- `--host`: RPC host advertised to other modules.
- `--port`: Region RPC port. Defaults to `region.rpc.port` from `application.properties`.
- `--storage`: local storage root. Defaults to `region.storage.path`.
- `--node-id`: stable node id. Defaults to `host:port`.
- `--heartbeat-interval-ms`: heartbeat interval used by later Zookeeper/Master tasks.

Example:

```bash
java com.minisql.region.RegionServer --port=9091 --storage=./data/region-9091
```

## Next Steps

The following implementation steps will add local storage, SQL execution, Thrift RPC, Zookeeper registration, Master heartbeat, and replication. Each step should include focused unit tests before commit.
