package com.minisql.region.zk;

import com.minisql.common.ZkConstants;
import com.minisql.region.model.MasterEndpoint;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ActiveMasterWatcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ActiveMasterWatcher.class);

    private final CuratorFramework client;
    private final int pollIntervalMs;
    private final AtomicReference<MasterEndpoint> currentMaster = new AtomicReference<>();
    private ScheduledExecutorService scheduler;

    public ActiveMasterWatcher(CuratorFramework client, int pollIntervalMs) {
        this.client = client;
        this.pollIntervalMs = pollIntervalMs;
    }

    public void start() {
        refreshNow();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "active-master-watcher");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::safeRefresh, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    public Optional<MasterEndpoint> getCurrentMaster() {
        return Optional.ofNullable(currentMaster.get());
    }

    public void refreshNow() {
        try {
            if (client.checkExists().forPath(ZkConstants.ACTIVE_MASTER_PATH) == null) {
                currentMaster.set(null);
                return;
            }
            byte[] data = client.getData().forPath(ZkConstants.ACTIVE_MASTER_PATH);
            currentMaster.set(MasterEndpoint.parse(new String(data, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            logger.warn("Failed to refresh active master endpoint", e);
            currentMaster.set(null);
        }
    }

    private void safeRefresh() {
        try {
            refreshNow();
        } catch (Exception e) {
            logger.warn("Unexpected active master refresh failure", e);
        }
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
