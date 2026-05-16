# Client 模块与 RPC 协议说明

本文档用于说明 Client 侧的职责边界、当前使用的 RPC 接口，以及 Client 与 Master、Region Server 的调用流程。本文档只描述 Client 侧约定，不改变 Master 或 Region Server 的实现职责。

## Client 职责

- 提供 `MiniSqlClient.execute(String sql)` 作为对外易用的 Java API。
- 提供 `ClientMain` 作为交互式命令行入口。
- 从 Zookeeper 的 `/active_master` 路径解析当前 Active Master 地址；若解析失败，则回退到配置项 `master.rpc.host` 和 `master.rpc.port`。
- 在 Client 侧缓存元数据：
  - Active Master 地址；
  - 表名到 Region Server 地址的路由映射；
  - 表名到 `TableSchema` 的表结构映射。
- 当 Region RPC 调用失败时，使对应表的路由缓存失效，并重新向 Master 获取路由后重试一次。
- 对 SQL 做轻量解析，仅识别操作类型、表名，以及 `CREATE TABLE` 中的表结构信息，用于本地元数据缓存。

## Client 当前使用的 RPC 接口

Master 服务：

```thrift
RoutingResponse getTableRouting(1: string tableName)
bool createTable(1: string tableName)
```

Region 服务：

```thrift
string executeSQL(1: string sqlStatement)
```

Region 返回的字符串会被 Client 解析为如下 JSON 结构：

```json
{
  "success": true,
  "message": "OK",
  "columns": ["id", "name"],
  "rows": [["1", "Alice"]],
  "affectedRows": 1
}
```

## 调用流程

1. Client 解析 SQL，并提取目标表名。
2. 对于 `CREATE TABLE`，Client 先调用 `MasterService.createTable(tableName)`，并在本地缓存解析出的表结构。
3. 当本地没有表路由缓存时，Client 向 Master 请求该表的 Region 路由。
4. Client 根据路由结果，调用目标 Region Server 的 `RegionService.executeSQL(sql)`。
5. Client 将 Region 返回的 JSON 字符串解析为 `SqlResult`。
6. 如果 Region RPC 调用失败，Client 只清除受影响表的路由缓存，并重新获取路由后重试一次。

