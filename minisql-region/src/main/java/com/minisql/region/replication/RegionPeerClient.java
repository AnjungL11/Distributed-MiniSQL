package com.minisql.region.replication;

import com.minisql.common.JsonUtil;
import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.rpc.RegionSyncPayload;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RegionPeerClient {
    private final int timeoutMs;

    public RegionPeerClient(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean syncSql(MasterEndpoint peer, String tableName, String sqlStatement) throws Exception {
        RegionSyncPayload payload = RegionSyncPayload.forSql(tableName, sqlStatement);
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        try (TTransport transport = new TSocket(peer.getHost(), peer.getPort(), timeoutMs)) {
            transport.open();
            RegionService.Client client = new RegionService.Client(new TBinaryProtocol(transport));
            return client.syncData(tableName, ByteBuffer.wrap(bytes));
        }
    }
}
