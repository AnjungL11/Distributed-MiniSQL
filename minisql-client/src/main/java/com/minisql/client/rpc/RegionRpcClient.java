package com.minisql.client.rpc;

import com.minisql.client.model.Endpoint;
import com.minisql.common.ConfigReader;
import com.minisql.rpc.region.RegionService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class RegionRpcClient {
    private final int timeoutMs;

    public RegionRpcClient() {
        this(ConfigReader.getInt("client.rpc.timeout.ms", 3000));
    }

    public RegionRpcClient(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String executeSql(Endpoint region, String sql) throws Exception {
        try (TTransport transport = new TSocket(region.getHost(), region.getPort(), timeoutMs)) {
            transport.open();
            RegionService.Client client = new RegionService.Client(new TBinaryProtocol(transport));
            return client.executeSQL(sql);
        }
    }
}
