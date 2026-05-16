package com.minisql.client.rpc;

import com.minisql.client.model.Endpoint;
import com.minisql.common.ConfigReader;
import com.minisql.rpc.master.MasterService;
import com.minisql.rpc.master.RoutingResponse;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class MasterRpcClient {
    private final int timeoutMs;

    public MasterRpcClient() {
        this(ConfigReader.getInt("client.rpc.timeout.ms", 3000));
    }

    public MasterRpcClient(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public RoutingResponse getTableRouting(Endpoint master, String tableName) throws Exception {
        return withClient(master, client -> client.getTableRouting(tableName));
    }

    public boolean createTable(Endpoint master, String tableName) throws Exception {
        return withClient(master, client -> client.createTable(tableName));
    }

    private <T> T withClient(Endpoint endpoint, ClientCall<T> call) throws Exception {
        try (TTransport transport = new TSocket(endpoint.getHost(), endpoint.getPort(), timeoutMs)) {
            transport.open();
            MasterService.Client client = new MasterService.Client(new TBinaryProtocol(transport));
            return call.execute(client);
        }
    }

    private interface ClientCall<T> {
        T execute(MasterService.Client client) throws Exception;
    }
}
