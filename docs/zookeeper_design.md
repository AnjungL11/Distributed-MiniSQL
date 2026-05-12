# 分布式 MiniSQL：Zookeeper 目录树设计规范

本规范定义了分布式 MiniSQL 系统在 Zookeeper 中的节点（ZNode）数据结构。
**注意：** 我们在客户端统一配置了 namespace 为 `minisql`，因此底层实际绝对路径会自动带上 `/minisql` 前缀。各模块代码中仅需关注**相对路径**即可。

## 目录树视图

```text
/minisql (Namespace)
  ├── /master_election        [持久节点] (内部为临时顺序节点，用于选主排队)
  ├── /active_master          [临时节点] (存储当前 Active Master 的 RPC 地址)
  ├── /regionservers          [持久节点] (Region Server 注册的父目录)
  │     ├── /192.168.1.10:9090  [临时节点] (具体的在线 Region Server)
  │     └── /192.168.1.11:9090  [临时节点] (具体的在线 Region Server)
  └── /tables                 [持久节点] (存放所有表元数据的根目录)
        ├── /student            [持久节点] (存储 student 表的路由映射信息)
        └── /course             [持久节点] (存储 course 表的路由映射信息)
```

## 节点详细说明

### 1. Master 高可用相关
* **`/master_election`**
  * **类型**：持久节点（Persistent）
  * **作用**：所有启动的 Master 实例使用 Curator 在此目录下创建临时顺序节点进行排队抢占。
* **`/active_master`**
  * **类型**：临时节点（Ephemeral）
  * **作用**：只有成功晋升为 Active 状态的 Master 才能创建此节点，并写入自己的 RPC 地址（如 `127.0.0.1:8080`）。Client 启动时读取此节点获取当前主控节点地址。

### 2. 集群监控相关
* **`/regionservers`**
  * **类型**：持久节点（Persistent）
  * **作用**：存储底层数据节点的父目录。Master 将监听此目录的子节点变更事件，以实现节点的上下线感知。
* **`/regionservers/[ip:port]`**
  * **类型**：临时节点（Ephemeral）
  * **作用**：Region Server 启动时向此目录注册自身。如果该 Server 宕机或断网，ZK 会因 Session 超时自动删除此节点。

### 3. 元数据路由相关
* **`/tables/[table_name]`**
  * **类型**：持久节点（Persistent）
  * **作用**：Master 在执行 DDL（建表）时创建，存储该表的数据分布路由信息。Client 可拉取并缓存在本地。