     package com.minisql.master;

import com.minisql.common.ConfigReader;
import com.minisql.common.JsonUtil;
import com.minisql.common.TableSchema;
import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;
import com.minisql.rpc.master.MasterService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MasterServer extends LeaderSelectorListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MasterServer.class);
    
    private final ZkClient zkClient;
    private final String masterName;
    private final int rpcPort;
    private LeaderSelector leaderSelector;
    private final ClusterStatusManager clusterStatusManager;
    private CuratorCache regionDirCache;
    private TServer thriftServer;

    public MasterServer() {
        this.zkClient = new ZkClient();
        // 先启动客户端，状态变为 STARTED，才能执行后面的 ZK 操作
        this.zkClient.start();
        // 从统一配置文件读取自身配置
        this.masterName = ConfigReader.getString("master.name", "Master-Node-1");
        this.rpcPort = ConfigReader.getInt("master.rpc.port", 8080);
        
        this.clusterStatusManager = new ClusterStatusManager();
        // 确保 ZK 中的基础目录已经创建
        this.zkClient.initZkDirectories();
        
        // 初始化 Leader 选主器，指定排队路径和监听器
        this.leaderSelector = new LeaderSelector(zkClient.getClient(), ZkConstants.MASTER_ELECTION_PATH, this);
        // 保证一旦因网络抖动丢失 Leader 身份，还能重新排队参与下一轮抢占
        this.leaderSelector.autoRequeue();
    }

    public void start() {
        // zkClient.start();
        logger.info("[{}] 进程启动完毕，加入 ZK 选主队列...", masterName);
        leaderSelector.start();
    }

    /**
     * 当该实例成功抢占到分布式锁时，Curator 会回调此方法。
     * 只要这个方法不退出，该实例就会一直保持 Active Master 状态
     */
    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        logger.info("=================================================");
        logger.info("🚀 竞选成功！[{}] 正式晋升为 Active Master！", masterName);
        logger.info("=================================================");
        
        // 获取本机的实际 IP（生产环境中需结合网卡获取，此处演示直接获取本地 IP）
        String activeMasterAddress = InetAddress.getLocalHost().getHostAddress() + ":" + rpcPort;
        
        try {
            // 将自己的 RPC 地址写到 ZK 的 /active_master 节点
            // 如果节点残留则先删除
            if (client.checkExists().forPath(ZkConstants.ACTIVE_MASTER_PATH) != null) {
                client.delete().forPath(ZkConstants.ACTIVE_MASTER_PATH);
            }
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(ZkConstants.ACTIVE_MASTER_PATH, activeMasterAddress.getBytes(StandardCharsets.UTF_8));
            
            logger.info("已向集群广播服务地址: {}", activeMasterAddress);

            // 只有当选为 Active Master 的实体，才开启对 Region Server 节点的事件快照同步与监听
            initRegionDirectoryWatcher();

            // 让上面的 Watcher 把存活节点加载进 clusterStatusManager
            Thread.sleep(500); 
            
            // 清理孤儿表
            reconcileOrphanedTables(client);
            // 启动对外服务的 Thrift RPC 服务器
            startRpcServer();

        } catch (InterruptedException e) {
            logger.warn("[{}] 进程被中断，即将交出 Active Master 权限...", masterName);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Active Master 运行期间发生致命异常", e);
        } privateWayExit();
    }

    /**
     * 清理遗留的孤儿表
     */
    private void reconcileOrphanedTables(CuratorFramework client) {
        logger.info("开始执行元数据对账：检查是否存在失效的孤儿表...");
        try {
            // 获取 ZK 中所有表的名字
            if (client.checkExists().forPath(ZkConstants.TABLES_ROOT) == null) {
                return; // 如果还没建过表，直接返回
            }
            List<String> tables = client.getChildren().forPath(ZkConstants.TABLES_ROOT);
            List<String> aliveRegions = client.getChildren().forPath(ZkConstants.REGION_SERVERS_ROOT);

            int orphanCount = 0;
            for (String tableName : tables) {
                String tablePath = ZkConstants.getTablePath(tableName);
                byte[] data = client.getData().forPath(tablePath);
                if (data != null && data.length > 0) {
                    TableSchema schema = JsonUtil.fromJson(new String(data, StandardCharsets.UTF_8), TableSchema.class);
                    String regionNode = schema.getPrimaryKey();

                    // 如果这张表所属的 Region 已经不在存活列表里了
                    if (!aliveRegions.contains(regionNode)) {
                        logger.warn("🚨 发现孤儿表 [{}] (原属节点 {} 已离线)，准备重新分配...", tableName, regionNode);
                        
                        String newBestNode = clusterStatusManager.getLowestLoadNode();
                        // 如果负载均衡器还没预热好
                        if (newBestNode == null && !aliveRegions.isEmpty()) {
                            newBestNode = aliveRegions.get(0);
                        }

                        if (newBestNode != null) {
                            schema.setPrimaryKey(newBestNode);
                            client.setData().forPath(tablePath, JsonUtil.toJson(schema).getBytes(StandardCharsets.UTF_8));
                            logger.info("✨ 对账修复成功：表 [{}] 已重新分配至健康节点 {}", tableName, newBestNode);
                            orphanCount++;
                        } else {
                            logger.error("❌ 无法修复表 [{}]，当前集群没有任何存活的 Region 节点！", tableName);
                        }
                    }
                }
            }
            if (orphanCount == 0) {
                logger.info("✅ 元数据对账完成：所有表状态健康，无遗留孤儿表。");
            } else {
                logger.info("✅ 元数据对账完成：共修复了 {} 张孤儿表。", orphanCount);
            }
        } catch (Exception e) {
            logger.error("元数据对账执行异常", e);
        }
    }

    /**
     * 开启分布式目录树节点快照监听
     */
    private void initRegionDirectoryWatcher() {
        regionDirCache = CuratorCache.build(zkClient.getClient(), ZkConstants.REGION_SERVERS_ROOT);
        CuratorCacheListener listener = CuratorCacheListener.builder()
                .forCreates(childData -> {
                    String nodeName = parseZNodeName(childData.getPath());
                    if (!nodeName.isEmpty()) {
                        clusterStatusManager.addNode(nodeName);

                        logger.info("监控到新 Region 节点上线: {}", nodeName);
                        // 孤儿表分配
                        reconcileOrphanedTables(zkClient.getClient());
                    }
                })
                .forDeletes(childData -> {
                    String nodeName = parseZNodeName(childData.getPath());
                    if (!nodeName.isEmpty()) {
                        clusterStatusManager.removeNode(nodeName);
                        // TODO: 待完成扩展触发自动 Failover 逻辑
                        logger.warn("🚨 节点 {} 宕机，开始执行数据表重平衡(Failover)!", nodeName);
                        
                        try {
                            // 遍历 ZK 中所有的表
                            List<String> tables = zkClient.getClient().getChildren().forPath(ZkConstants.TABLES_ROOT);
                            for (String tableName : tables) {
                                String tablePath = ZkConstants.getTablePath(tableName);
                                byte[] data = zkClient.getClient().getData().forPath(tablePath);
                                TableSchema schema = JsonUtil.fromJson(new String(data, StandardCharsets.UTF_8), TableSchema.class);
                                
                                // 如果发现这个表是分配给那个死掉的节点的
                                if (nodeName.equals(schema.getPrimaryKey())) {
                                    // 重新在活着的节点里挑一个最好的
                                    String newBestNode = clusterStatusManager.getLowestLoadNode();
                                    if (newBestNode != null) {
                                        schema.setPrimaryKey(newBestNode); // 重新分配
                                        zkClient.getClient().setData().forPath(tablePath, JsonUtil.toJson(schema).getBytes(StandardCharsets.UTF_8));
                                        logger.info("✨ 成功将表 {} 从宕机节点 {} 迁移至健康节点 {}", tableName, nodeName, newBestNode);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error("执行 Failover 失败", e);
                        }
                    }
                })
                .build();
        
        regionDirCache.listenable().addListener(listener);
        regionDirCache.start();
        logger.info("集群状态监听器已成功挂载至：{}", ZkConstants.REGION_SERVERS_ROOT);
    }

    private String parseZNodeName(String path) {
        if (path.equals(ZkConstants.REGION_SERVERS_ROOT)) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private void startRpcServer() throws Exception {
        // 将具体的业务逻辑实现类绑定到 Thrift Processor 上
        MasterService.Processor<MasterServiceImpl> processor = 
                new MasterService.Processor<>(new MasterServiceImpl(zkClient, clusterStatusManager));
        
        // 绑定监听端口
        TServerTransport serverTransport = new TServerSocket(rpcPort);
        
        // 配置线程池工作模式
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        args.minWorkerThreads(5);
        args.maxWorkerThreads(20);
        
        thriftServer = new TThreadPoolServer(args);
        
        logger.info("Master RPC Server 开始接客，监听端口: {}", rpcPort);
        thriftServer.serve(); // 线程将阻塞在此处，直到服务被 stop()
    }

    // private void stopRpcServer() {
    //     if (thriftServer != null && thriftServer.isServing()) {
    //         thriftServer.stop();
    //         logger.info("Master RPC Server 监听已停止。");
    //     }
    // }
    private void privateWayExit() {
        if (regionDirCache != null) {
            regionDirCache.close();
        }
        if (thriftServer != null && thriftServer.isServing()) {
            thriftServer.stop();
        }
        logger.info("[{}] 已平滑交出权限，降级为备份实例。", masterName);
    }

    public static void main(String[] args) {
        MasterServer server = new MasterServer();
        server.start();
        
        // 保持主线程一直存活，否则程序直接退出了
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.info("Master 主程序平滑退出");
        }
    }
}