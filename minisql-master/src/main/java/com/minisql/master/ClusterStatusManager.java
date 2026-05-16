package com.minisql.master;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局在册内存集群管理器
 * 负责维护在线的 Region Server 节点列表以及其实时的负载状态
 */
public class ClusterStatusManager {
    private static final Logger logger = LoggerFactory.getLogger(ClusterStatusManager.class);
    
    // 内存缓存：Key 为 ip:port，Value 为最近上报的负载得分
    private final Map<String, Double> activeNodes = new ConcurrentHashMap<>();

    public void addNode(String serverId) {
        activeNodes.putIfAbsent(serverId, 0.0);
        logger.info("▶ 内存注册表：检测到新的 Region Server 上线 -> {}", serverId);
    }

    public void removeNode(String serverId) {
        activeNodes.remove(serverId);
        logger.warn("❌ 内存注册表：检测到 Region Server 离线或崩溃 -> {}", serverId);
    }

    public void updateNodeLoad(String serverId, double loadScore) {
        if (activeNodes.containsKey(serverId)) {
            activeNodes.put(serverId, loadScore);
        }
    }

    /**
     * 负载均衡核心算法：选择当前负载得分最低的在线物理节点
     */
    public String getLowestLoadNode() {
        if (activeNodes.isEmpty()) {
            return null;
        }
        String bestNode = null;
        double minLoad = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : activeNodes.entrySet()) {
            if (entry.getValue() < minLoad) {
                minLoad = entry.getValue();
                bestNode = entry.getKey();
            }
        }
        return bestNode;
    }

    public Map<String, Double> getActiveNodes() {
        return activeNodes;
    }
}