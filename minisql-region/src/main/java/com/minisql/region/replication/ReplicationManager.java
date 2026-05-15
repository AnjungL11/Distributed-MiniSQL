package com.minisql.region.replication;

import com.minisql.region.model.MasterEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReplicationManager {
    private static final Logger logger = LoggerFactory.getLogger(ReplicationManager.class);

    private final RegionPeerClient peerClient;
    private final List<MasterEndpoint> peers = new ArrayList<>();

    public ReplicationManager(RegionPeerClient peerClient) {
        this.peerClient = peerClient;
    }

    public synchronized void setPeers(List<MasterEndpoint> newPeers) {
        peers.clear();
        peers.addAll(newPeers);
    }

    public synchronized List<MasterEndpoint> getPeers() {
        return Collections.unmodifiableList(new ArrayList<>(peers));
    }

    public synchronized boolean hasPeers() {
        return !peers.isEmpty();
    }

    public int replicateSql(String tableName, String sqlStatement) {
        List<MasterEndpoint> snapshot = getPeers();
        int successCount = 0;
        for (MasterEndpoint peer : snapshot) {
            try {
                if (peerClient.syncSql(peer, tableName, sqlStatement)) {
                    successCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to sync SQL to follower {}: {}", peer.asAddress(), e.getMessage());
            }
        }
        return successCount;
    }
}
