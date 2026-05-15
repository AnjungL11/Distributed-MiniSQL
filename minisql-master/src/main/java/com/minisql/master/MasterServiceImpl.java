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

public class MasterServiceImpl implements MasterService.Iface {
    private static final Logger logger = LoggerFactory.getLogger(MasterServiceImpl.class);
    private final ZkClient zkClient;

    public MasterServiceImpl(ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    @Override
    public HeartbeatResponse sendHeartbeat(HeartbeatRequest request) {
        logger.info("收到来自 Region Server 的心跳汇报: {}:{}，当前负载评分: {}", 
                request.getRegionServerIp(), request.getPort(), request.getLoadScore());
        
        // TODO: 后续这里将更新 Master 内存中的全局可用节点映射表
        
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
            
            // TODO: 后期实现负载均衡时，应从内存的可用 Region 列表中挑选一台返回
            // 目前为了让 Client 能跑通联调流程，先返回一个假地址或固定地址
            logger.info("路由成功，分配给表 {} 的 Region 节点为 127.0.0.1:9090", tableName);
            return new RoutingResponse(true, "127.0.0.1", 9090);
            
        } catch (Exception e) {
            logger.error("获取表路由信息时发生异常", e);
            return new RoutingResponse(false, "", 0);
        }
    }

    @Override
    public boolean createTable(String tableName) {
        logger.info("收到 Client 的建表请求: {}", tableName);
        String tablePath = ZkConstants.getTablePath(tableName);
        try {
            // 防重校验：表是否已经存在
            if (zkClient.getClient().checkExists().forPath(tablePath) != null) {
                logger.warn("建表失败：表 {} 已经存在于集群中", tableName);
                return false;
            }

            // 初始化该表的元数据结构对象
            TableSchema schema = new TableSchema();
            schema.setTableName(tableName);
            // 将对象序列化为 JSON 字符串
            String jsonSchema = JsonUtil.toJson(schema);

            // 将表结构持久化写入 Zookeeper
            zkClient.getClient().create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(tablePath, jsonSchema.getBytes(StandardCharsets.UTF_8));
            
            logger.info("成功创建表 {}，元数据已落盘 Zookeeper", tableName);
            return true;
            
        } catch (Exception e) {
            logger.error("执行建表逻辑时发生严重异常", e);
            return false;
        }
    }
}