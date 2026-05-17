package com.minisql.master;

import com.minisql.common.JsonUtil;
import com.minisql.common.TableSchema;
import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;
import com.minisql.rpc.master.HeartbeatRequest;
import com.minisql.rpc.master.HeartbeatResponse;
import com.minisql.rpc.master.MasterService;
import com.minisql.rpc.master.RoutingResponse;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MasterServiceImpl implements MasterService.Iface {
    private static final Logger logger = LoggerFactory.getLogger(MasterServiceImpl.class);
    private final ZkClient zkClient;
    // 引用全局的内存集群状态管理器
    private final ClusterStatusManager clusterStatusManager;

    public MasterServiceImpl(ZkClient zkClient, ClusterStatusManager clusterStatusManager) {
        this.zkClient = zkClient;
        this.clusterStatusManager = clusterStatusManager;
    }

    @Override
    public HeartbeatResponse sendHeartbeat(HeartbeatRequest request) {
        // logger.info("收到来自 Region Server 的心跳汇报: {}:{}，当前负载评分: {}", 
        //         request.getRegionServerIp(), request.getPort(), request.getLoadScore());
        
        // TODO: 待完成 Master 内存中的全局可用节点映射表
        String serverId = request.getRegionServerIp() + ":" + request.getPort();
        logger.debug("收到 Region Server 心跳汇报: {}, 负载评分: {}", serverId, request.getLoadScore());
        // 动态更新内存中该节点的负载评分和最后活跃时间
        clusterStatusManager.updateNodeLoad(serverId, request.getLoadScore());
        
        // return new HeartbeatResponse(true, "ACK_SUCCESS");
        return new HeartbeatResponse(true, "ACK_SUCCESS");
    }

    @Override
    public RoutingResponse getTableRouting(String tableName) {
        logger.info("收到 Client 请求获取表路由信息: {}", tableName);
        String tablePath = ZkConstants.getTablePath(tableName);
        try {
            // 检查表是否在 ZK 中存在
            if (zkClient.getClient().checkExists().forPath(tablePath) == null) {
                logger.warn("路由失败：表 {} 不存在", tableName);
                return new RoutingResponse(false, "", 0);
            }
            
            // TODO: 从内存的可用 Region 列表中挑选一台返回
            // 暂时先返回一个假地址或固定地址
            //  从 Zookeeper 中拉取该表元数据
            byte[] data = zkClient.getClient().getData().forPath(tablePath);
            String jsonSchema = new String(data, StandardCharsets.UTF_8);
            TableSchema schema = JsonUtil.fromJson(jsonSchema, TableSchema.class);

            // 读取当初分配的物理节点地址
            String assignedServer = schema.getPrimaryKey(); // 暂时借用字段存储
            if (assignedServer == null || !assignedServer.contains(":")) {
                assignedServer = clusterStatusManager.getLowestLoadNode();
                if (assignedServer == null) {
                    return new RoutingResponse(false, "", 0);
                }
                schema.setPrimaryKey(assignedServer);
                zkClient.getClient().setData().forPath(tablePath, JsonUtil.toJson(schema).getBytes(StandardCharsets.UTF_8));
            }

            String[] parts = assignedServer.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            // logger.info("路由成功，分配给表 {} 的 Region 节点为 127.0.0.1:9090", tableName);
            logger.info("路由成功，表 {} 当前托管在数据节点 -> {}:{}", tableName, ip, port);
            // return new RoutingResponse(true, "127.0.0.1", 9090);
            return new RoutingResponse(true, ip, port);
            
        } catch (Exception e) {
            logger.error("获取表路由信息时发生异常", e);
            return new RoutingResponse(false, "", 0);
        }
    }

    @Override
    public boolean createTable(String tableName, String schemaJson) {
        logger.info("收到 Client 的建表请求: {}", tableName);
        String tablePath = ZkConstants.getTablePath(tableName);
        try {
            // 检查表是否已经存在
            if (zkClient.getClient().checkExists().forPath(tablePath) != null) {
                logger.warn("建表失败：表 {} 已经存在", tableName);
                return false;
            }

            // 挑选负载最低的 Region 节点
            String bestRegionServer = clusterStatusManager.getLowestLoadNode();

            if (bestRegionServer == null) {
                logger.error("建表失败：当前集群中没有可用的在线 Region Server！");
                return false;
            }

            logger.info("负载均衡策略：选择将新表 {} 分配给数据节点 {}", tableName, bestRegionServer);

            // // 初始化该表的元数据结构对象
            // TableSchema schema = new TableSchema();
            // schema.setTableName(tableName);
            // schema.setPrimaryKey(bestRegionServer); // 暂时复用该字段标记路由目标服务器
            // // 将对象序列化为 JSON 字符串
            // String jsonSchema = JsonUtil.toJson(schema);

            // 将 Client 传过来的完整 schemaJson 转换回对象
            TableSchema schema = JsonUtil.fromJson(schemaJson, TableSchema.class);

            // 把 Master 分配的物理节点地址标记进去
            schema.setPrimaryKey(bestRegionServer);

            // 将表结构持久化写入 Zookeeper
            zkClient.getClient().create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(tablePath, JsonUtil.toJson(schema).getBytes(StandardCharsets.UTF_8));
            
            logger.info("成功创建表 {}，拓扑元数据已同步至 Zookeeper", tableName);
            return true;
            
        } catch (Exception e) {
            logger.error("执行建表逻辑时发生严重异常", e);
            return false;
        }
    }

    @Override
    public boolean dropTable(String tableName) {
        logger.info("收到删表请求: {}", tableName);
        String tablePath = ZkConstants.getTablePath(tableName);
        try {
            if (zkClient.getClient().checkExists().forPath(tablePath) != null) {
                // 从 ZK 中删除该表的元数据
                zkClient.getClient().delete().forPath(tablePath);
                logger.info("表 {} 的元数据已从集群移除", tableName);
                return true;
            }
        } catch (Exception e) {
            logger.error("删表失败", e);
        }
        return false;
    }
}