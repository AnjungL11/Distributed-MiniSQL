package com.minisql.region.model;

import java.util.ArrayList;
import java.util.List;

public class RegionHeartbeatState {
    private String nodeId;
    private String ip;
    private int port;
    private int loadScore;
    private List<String> holdingRegions = new ArrayList<>();
    private long lastHeartbeatMillis;

    public RegionHeartbeatState() {
    }

    public static RegionHeartbeatState from(RegionServerInfo info, List<String> holdingRegions, int loadScore) {
        RegionHeartbeatState state = new RegionHeartbeatState();
        state.nodeId = info.getNodeId();
        state.ip = info.getHost();
        state.port = info.getPort();
        state.loadScore = loadScore;
        state.holdingRegions = new ArrayList<>(holdingRegions);
        state.lastHeartbeatMillis = System.currentTimeMillis();
        return state;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getLoadScore() {
        return loadScore;
    }

    public void setLoadScore(int loadScore) {
        this.loadScore = loadScore;
    }

    public List<String> getHoldingRegions() {
        return holdingRegions;
    }

    public void setHoldingRegions(List<String> holdingRegions) {
        this.holdingRegions = holdingRegions;
    }

    public long getLastHeartbeatMillis() {
        return lastHeartbeatMillis;
    }

    public void setLastHeartbeatMillis(long lastHeartbeatMillis) {
        this.lastHeartbeatMillis = lastHeartbeatMillis;
    }
}
