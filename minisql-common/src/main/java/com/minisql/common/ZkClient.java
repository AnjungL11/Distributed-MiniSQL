package com.minisql.common;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局统一的 Zookeeper 客户端连接管理类
 */
public class ZkClient {
    private static final Logger logger = LoggerFactory.getLogger(ZkClient.class);
    private final CuratorFramework client;

    /**
     * 构造函数，初始化连接
     * @param zkAddress Zookeeper 集群地址，如 "127.0.0.1:2181"
     */
    // public ZkClient(String zkAddress) {
        // // 重试策略：初始等待 1000ms，最多重试 3 次
        // ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);

        // this.client = CuratorFrameworkFactory.builder()
        //         .connectString(zkAddress)
        //         .sessionTimeoutMs(5000)
        //         .connectionTimeoutMs(5000)
        //         .retryPolicy(retryPolicy)
        //         .namespace(ZkConstants.ZK_NAMESPACE) // 自动附加命名空间
        //         .build();
    // }
    public ZkClient() {
        String zkAddress = ConfigReader.getString("zookeeper.address", "127.0.0.1:2181");
        int sessionTimeout = ConfigReader.getInt("zookeeper.session.timeout.ms", 5000);
        // 重试策略：初始等待 1000ms，最多重试 3 次
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        
        this.client = CuratorFrameworkFactory.builder()
                    .connectString(zkAddress)
                    .sessionTimeoutMs(sessionTimeout)
                    .connectionTimeoutMs(5000)
                    .retryPolicy(retryPolicy)
                    .namespace(ZkConstants.ZK_NAMESPACE) // 自动附加命名空间
                    .build();
    }

    /**
     * 启动客户端连接
     */
    public void start() {
        client.start();
        logger.info("Zookeeper 客户端已启动，连接至命名空间: /{}", ZkConstants.ZK_NAMESPACE);
    }

    /**
     * 关闭客户端连接
     */
    public void close() {
        if (client != null) {
            client.close();
            logger.info("Zookeeper 客户端已关闭。");
        }
    }

    /**
     * 获取 Curator 原生客户端，供组员调用 Watcher 或锁等高级功能
     */
    public CuratorFramework getClient() {
        return client;
    }

    /**
     * 系统基建方法：Master 启动时调用此方法，初始化基础的持久化父目录
     */
    public void initZkDirectories() {
        try {
            // 初始化 RegionServers 根节点
            if (client.checkExists().forPath(ZkConstants.REGION_SERVERS_ROOT) == null) {
                client.create().creatingParentsIfNeeded()
                      .withMode(CreateMode.PERSISTENT)
                      .forPath(ZkConstants.REGION_SERVERS_ROOT, "Root for Region Servers".getBytes());
                logger.info("成功创建 ZK 持久目录: {}", ZkConstants.REGION_SERVERS_ROOT);
            }
            
            // 初始化 Tables 根节点
            if (client.checkExists().forPath(ZkConstants.TABLES_ROOT) == null) {
                client.create().creatingParentsIfNeeded()
                      .withMode(CreateMode.PERSISTENT)
                      .forPath(ZkConstants.TABLES_ROOT, "Root for Table Metadata".getBytes());
                logger.info("成功创建 ZK 持久目录: {}", ZkConstants.TABLES_ROOT);
            }
        } catch (Exception e) {
            logger.error("初始化 ZK 基础目录失败！", e);
            throw new RuntimeException("ZK Directory Initialization Failed", e);
        }
    }
}