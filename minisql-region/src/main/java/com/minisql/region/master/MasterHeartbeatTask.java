package com.minisql.region.master;

import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.rpc.master.HeartbeatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class MasterHeartbeatTask implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MasterHeartbeatTask.class);

    private final MasterClient masterClient;
    private final Supplier<Optional<MasterEndpoint>> masterSupplier;
    private final Supplier<RegionHeartbeatState> stateSupplier;
    private final MasterInstructionHandler instructionHandler;
    private final int heartbeatIntervalMs;
    private ScheduledExecutorService scheduler;

    public MasterHeartbeatTask(
            MasterClient masterClient,
            Supplier<Optional<MasterEndpoint>> masterSupplier,
            Supplier<RegionHeartbeatState> stateSupplier,
            MasterInstructionHandler instructionHandler,
            int heartbeatIntervalMs) {
        this.masterClient = masterClient;
        this.masterSupplier = masterSupplier;
        this.stateSupplier = stateSupplier;
        this.instructionHandler = instructionHandler;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "master-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::sendOnce, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    public boolean sendOnce() {
        Optional<MasterEndpoint> endpoint = masterSupplier.get();
        if (endpoint.isEmpty()) {
            return false;
        }
        try {
            HeartbeatResponse response = masterClient.sendHeartbeat(endpoint.get(), stateSupplier.get());
            if (response != null && response.isSetInstruction()) {
                instructionHandler.handle(response.getInstruction());
            }
            return response != null && response.isIsSuccess();
        } catch (Exception e) {
            logger.warn("Failed to send heartbeat to Master {}: {}", endpoint.get().asAddress(), e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
