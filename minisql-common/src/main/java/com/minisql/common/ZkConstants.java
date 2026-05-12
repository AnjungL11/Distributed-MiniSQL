package com.minisql.common;

/**
 * Zookeeper 目录树路径规范常量表
 */
public class ZkConstants {
    
    // Curator 客户端的基础命名空间
    public static final String ZK_NAMESPACE = "minisql";

    // Master 选主专用的路径
    public static final String MASTER_ELECTION_PATH = "/master_election";
    
    // 存放当前 Active Master RPC 地址的路径
    public static final String ACTIVE_MASTER_PATH = "/active_master";
    
    // 所有 Region Server 注册的父目录
    public static final String REGION_SERVERS_ROOT = "/regionservers";

    // 存放表元数据的父目录
    public static final String TABLES_ROOT = "/tables";

    /**
     * 工具方法：拼接具体的 Region Server 注册路径
     * @param ip   Region Server 的 IP
     * @param port Region Server 的 RPC 端口
     * @return 类似 "/regionservers/192.168.1.10:9090"
     */
    public static String getRegionServerPath(String ip, int port) {
        return REGION_SERVERS_ROOT + "/" + ip + ":" + port;
    }
    
    /**
     * 工具方法：拼接表元数据路径
     * @param tableName 表名
     * @return 类似 "/tables/student"
     */
    public static String getTablePath(String tableName) {
        return TABLES_ROOT + "/" + tableName;
    }
}