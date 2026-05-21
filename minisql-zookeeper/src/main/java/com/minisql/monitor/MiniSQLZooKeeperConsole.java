package com.minisql.monitor;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.nio.charset.StandardCharsets;

/**
 * Zookeeper 业务状态监听控制台
 */
public class MiniSQLZooKeeperConsole {

    // 对应 ZK 真实端口
    private static final String ZK_ADDRESS = "127.0.0.1:22181";
    private static final String ZK_NAMESPACE = "minisql";

    // 核心目录路径定义
    private static final String MASTER_PATH = "/master_election";
    private static final String ACTIVE_MASTER_PATH = "/active_master";
    private static final String REGION_PATH = "/regionservers";
    private static final String CLIENT_PATH = "/clients";

    // 用于区分是首次启动 Master 还是发生容灾切换
    private static boolean hasInitialMaster = false;

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println(" Welcome to Distributed MiniSQL - ZooKeeper Console ");
        System.out.println(" Connected to ZooKeeper Server on port 22181");
        System.out.println("=======================================================\n");

        // 初始化 Curator 客户端并连接 Zookeeper
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(ZK_ADDRESS)
                .sessionTimeoutMs(40000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace(ZK_NAMESPACE)
                .build();

        client.start();

        try {
            // 确保基础目录结构存在，防止初次启动报错
            ensurePathExists(client, MASTER_PATH);
            // ensurePathExists(client, ACTIVE_MASTER_PATH);
            ensurePathExists(client, REGION_PATH);
            ensurePathExists(client, CLIENT_PATH);

            // 构建全路径的 Cache 监听器，监控 /minisql 下的所有子节点变动
            CuratorCache cache = CuratorCache.build(client, "/");
            
            CuratorCacheListener listener = CuratorCacheListener.builder()
                    .forCreates(node -> {
                        String path = node.getPath();
                        String data = new String(node.getData() != null ? node.getData() : new byte[0], StandardCharsets.UTF_8);
                        String nodeName = extractNodeName(path);

                        // 匹配 active_master 节点
                        if (path.equals(ACTIVE_MASTER_PATH)) {
                            if (!hasInitialMaster) {
                                System.out.println("<master>Server " + data + " registered as ACTIVE master");
                                hasInitialMaster = true;
                            } else {
                                // 如果之前已经有过 Master，说明这是容灾切换后新 Master 创建了节点
                                System.out.println("<master_change>Master change detected!");
                                System.out.println("<master>Server " + data + " is now the ACTIVE master");
                            }
                        }
                        
                        // 根据不同的路径打印日志
                        // if (path.startsWith(ACTIVE_MASTER_PATH) && !path.equals(ACTIVE_MASTER_PATH)) {
                        //     // 打印 Active Master 上线
                        //     System.out.println("<master>Server " + (data.isEmpty() ? nodeName : data) + " registered as ACTIVE master");
                        // } else if (path.startsWith(MASTER_PATH) && !path.equals(MASTER_PATH)) {
                        //     // 打印 Backup Master 参与竞选
                        //     System.out.println("<master-standby>Server " + nodeName + " joined master election");
                        // } else if (path.startsWith(REGION_PATH) && !path.equals(REGION_PATH)) {
                        //     // 打印 Region 注册
                        //     System.out.println("<region>Server " + nodeName + " registered as region");
                        // } else if (path.startsWith(CLIENT_PATH) && !path.equals(CLIENT_PATH)) {
                        //     // 打印 Client 接入
                        //     System.out.println("<client>Client " + nodeName + " has entered the system");
                        // }
                        else if (path.startsWith(MASTER_PATH + "/") && !path.equals(MASTER_PATH)) {
                            System.out.println("<master-standby>Server " + nodeName + " joined master election");
                        } else if (path.startsWith(REGION_PATH + "/") && !path.equals(REGION_PATH)) {
                            System.out.println("<region>Server " + nodeName + " registered as region");
                        } else if (path.startsWith(CLIENT_PATH + "/") && !path.equals(CLIENT_PATH)) {
                            System.out.println("<client>Client " + nodeName + " has entered the system");
                        }
                    })
                    // 处理节点数据更新
                    .forChanges((oldNode, node) -> {
                        String path = node.getPath();
                        String data = new String(node.getData() != null ? node.getData() : new byte[0], StandardCharsets.UTF_8);
                        if (path.equals(ACTIVE_MASTER_PATH)) {
                            System.out.println("<master_change>Master change to " + data);
                            hasInitialMaster = true; // 确保状态同步
                        }
                    })
                    // 处理节点掉线
                    .forDeletes(childData -> {
                        String path = childData.getPath();
                        String nodeName = extractNodeName(path);

                        String data = new String(childData.getData() != null ? childData.getData() : new byte[0], StandardCharsets.UTF_8);
                        // 节点掉线时的红色警报打印
                        // if (path.startsWith(ACTIVE_MASTER_PATH) && !path.equals(ACTIVE_MASTER_PATH)) {
                        //     System.err.println("<alert> Active Master [" + nodeName + "] is down! Triggering failover...");
                        // } else if (path.startsWith(REGION_PATH) && !path.equals(REGION_PATH)) {
                        //     System.err.println("<alert> Region Server [" + nodeName + "] disconnected! Heartbeat timeout.");
                        // }
                        if (path.equals(ACTIVE_MASTER_PATH)) {
                            System.err.println("<alert> Active Master [" + data + "] is down! Triggering failover...");
                        } else if (path.startsWith(REGION_PATH + "/") && !path.equals(REGION_PATH)) {
                            System.err.println("<alert> Region Server [" + nodeName + "] disconnected! Heartbeat timeout.");
                        }
                    })
                    .build();

            // 注册监听器并启动
            cache.listenable().addListener(listener);
            cache.start();

            // 挂起主线程，让控制台持续监听
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Console encountered an error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    /**
     * 辅助方法：确保 ZK 节点路径存在
     */
    private static void ensurePathExists(CuratorFramework client, String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }

    /**
     * 辅助方法：从绝对路径中提取最后的节点名
     */
    private static String extractNodeName(String path) {
        if (path == null || path.isEmpty()) return "Unknown";
        int lastSlashIndex = path.lastIndexOf('/');
        return lastSlashIndex >= 0 ? path.substring(lastSlashIndex + 1) : path;
    }
}