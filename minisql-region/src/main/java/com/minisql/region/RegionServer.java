package com.minisql.region;

import com.minisql.region.config.RegionServerConfig;
import com.minisql.region.model.RegionServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Region Server lifecycle root.
 *
 * <p>The class intentionally starts as a small coordinator. Later steps attach
 * storage, Thrift RPC, Zookeeper registration, Master heartbeat, and
 * replication behind this lifecycle instead of scattering startup logic.</p>
 */
public class RegionServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RegionServer.class);

    private final RegionServerConfig config;
    private final RegionServerInfo serverInfo;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RegionServer(RegionServerConfig config) {
        this.config = config;
        this.serverInfo = RegionServerInfo.fromConfig(config);
    }

    public static void main(String[] args) {
        RegionServerConfig config = RegionServerConfig.fromArgs(args);
        RegionServer server = new RegionServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "region-server-shutdown"));
        server.start();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            logger.info("Region Server already started: {}", serverInfo.getAddress());
            return;
        }
        logger.info("Region Server started: {}", serverInfo);
    }

    public boolean isRunning() {
        return running.get();
    }

    public RegionServerConfig getConfig() {
        return config;
    }

    public RegionServerInfo getServerInfo() {
        return serverInfo;
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            logger.info("Region Server stopped: {}", serverInfo.getAddress());
        }
    }
}
