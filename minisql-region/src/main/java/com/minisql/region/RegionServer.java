package com.minisql.region;

import com.minisql.region.config.RegionServerConfig;
import com.minisql.region.model.RegionServerInfo;
import com.minisql.region.rpc.RegionRpcServer;
import com.minisql.region.service.RegionServiceImpl;
import com.minisql.region.sql.SqlExecutor;
import com.minisql.region.storage.RegionStorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private RegionStorageEngine storageEngine;
    private RegionRpcServer rpcServer;

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
        try {
            storageEngine = new RegionStorageEngine(config.getStorageRoot());
            storageEngine.start();
            SqlExecutor sqlExecutor = new SqlExecutor(storageEngine);
            rpcServer = new RegionRpcServer(config.getPort(), new RegionServiceImpl(sqlExecutor));
            rpcServer.start();
            logger.info("Region Server started: {}", serverInfo);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("Failed to start Region Server " + serverInfo.getAddress(), e);
        }
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

    public RegionStorageEngine getStorageEngine() {
        return storageEngine;
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            if (rpcServer != null) {
                rpcServer.stop();
            }
            if (storageEngine != null) {
                try {
                    storageEngine.close();
                } catch (IOException e) {
                    logger.warn("Failed to close Region storage cleanly", e);
                }
            }
            logger.info("Region Server stopped: {}", serverInfo.getAddress());
        }
    }
}
