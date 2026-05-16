package com.minisql.client.zk;

import com.minisql.client.cache.MetadataCache;
import com.minisql.client.model.Endpoint;
import com.minisql.common.ConfigReader;
import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;

import java.nio.charset.StandardCharsets;

public class ActiveMasterResolver implements AutoCloseable {
    private final MetadataCache cache;
    private final Endpoint fallbackEndpoint;
    private ZkClient zkClient;

    public ActiveMasterResolver(MetadataCache cache) {
        this.cache = cache;
        String fallbackHost = ConfigReader.getString("master.rpc.host", "127.0.0.1");
        int fallbackPort = ConfigReader.getInt("master.rpc.port", 8080);
        this.fallbackEndpoint = new Endpoint(fallbackHost, fallbackPort);
    }

    public Endpoint resolve() {
        return cache.getActiveMaster().orElseGet(this::loadActiveMaster);
    }

    public void invalidate() {
        cache.invalidateActiveMaster();
    }

    private Endpoint loadActiveMaster() {
        Endpoint endpoint = readFromZookeeper();
        cache.updateActiveMaster(endpoint);
        return endpoint;
    }

    private Endpoint readFromZookeeper() {
        try {
            ensureZkClient();
            if (zkClient.getClient().checkExists().forPath(ZkConstants.ACTIVE_MASTER_PATH) == null) {
                return fallbackEndpoint;
            }
            byte[] data = zkClient.getClient().getData().forPath(ZkConstants.ACTIVE_MASTER_PATH);
            return Endpoint.parse(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return fallbackEndpoint;
        }
    }

    private void ensureZkClient() {
        if (zkClient == null) {
            zkClient = new ZkClient();
            zkClient.start();
        }
    }

    @Override
    public void close() {
        if (zkClient != null) {
            zkClient.close();
        }
    }
}
