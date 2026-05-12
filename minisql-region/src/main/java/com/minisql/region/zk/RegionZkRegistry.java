package com.minisql.region.zk;

import com.minisql.common.JsonUtil;
import com.minisql.common.ZkConstants;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.region.model.RegionServerInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RegionZkRegistry implements AutoCloseable {
    private final CuratorFramework client;
    private final RegionServerInfo serverInfo;
    private final Supplier<Set<String>> holdingRegionsSupplier;
    private final int heartbeatIntervalMs;
    private final String nodePath;
    private ScheduledExecutorService scheduler;

    public RegionZkRegistry(
            CuratorFramework client,
            RegionServerInfo serverInfo,
            Supplier<Set<String>> holdingRegionsSupplier,
            int heartbeatIntervalMs) {
        this.client = client;
        this.serverInfo = serverInfo;
        this.holdingRegionsSupplier = holdingRegionsSupplier;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.nodePath = ZkConstants.getRegionServerPath(serverInfo.getHost(), serverInfo.getPort());
    }

    public void start() throws Exception {
        ensureRoot();
        byte[] data = heartbeatBytes();
        if (client.checkExists().forPath(nodePath) == null) {
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath, data);
        } else {
            client.setData().forPath(nodePath, data);
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "region-zk-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::safeRefreshHeartbeat, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void refreshHeartbeat() throws Exception {
        if (client.checkExists().forPath(nodePath) == null) {
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath, heartbeatBytes());
            return;
        }
        client.setData().forPath(nodePath, heartbeatBytes());
    }

    public boolean isRegistered() throws Exception {
        return client.checkExists().forPath(nodePath) != null;
    }

    public String getNodePath() {
        return nodePath;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        try {
            if (client.getZookeeperClient().isConnected() && client.checkExists().forPath(nodePath) != null) {
                client.delete().forPath(nodePath);
            }
        } catch (Exception ignored) {
            // Session close will clean the ephemeral node if explicit delete fails.
        }
    }

    private void safeRefreshHeartbeat() {
        try {
            refreshHeartbeat();
        } catch (Exception ignored) {
            // The next scheduled heartbeat will retry while the Curator session is alive.
        }
    }

    private void ensureRoot() throws Exception {
        if (client.checkExists().forPath(ZkConstants.REGION_SERVERS_ROOT) == null) {
            client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(ZkConstants.REGION_SERVERS_ROOT);
        }
    }

    private byte[] heartbeatBytes() {
        RegionHeartbeatState state = RegionHeartbeatState.from(
                serverInfo,
                new ArrayList<>(holdingRegionsSupplier.get()),
                holdingRegionsSupplier.get().size());
        return JsonUtil.toJson(state).getBytes(StandardCharsets.UTF_8);
    }
}
