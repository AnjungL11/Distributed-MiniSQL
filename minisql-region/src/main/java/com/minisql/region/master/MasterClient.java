package com.minisql.region.master;

import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.rpc.master.HeartbeatRequest;
import com.minisql.rpc.master.HeartbeatResponse;
import com.minisql.rpc.master.MasterService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.util.ArrayList;

public class MasterClient {
    private final int timeoutMs;

    public MasterClient(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public HeartbeatResponse sendHeartbeat(MasterEndpoint endpoint, RegionHeartbeatState state) throws Exception {
        try (TTransport transport = new TSocket(endpoint.getHost(), endpoint.getPort(), timeoutMs)) {
            transport.open();
            MasterService.Client client = new MasterService.Client(new TBinaryProtocol(transport));
            return client.sendHeartbeat(toRequest(state));
        }
    }

    private HeartbeatRequest toRequest(RegionHeartbeatState state) {
        HeartbeatRequest request = new HeartbeatRequest();
        request.setRegionServerIp(state.getIp());
        request.setPort(state.getPort());
        request.setLoadScore(state.getLoadScore());
        request.setHoldingRegions(new ArrayList<>(state.getHoldingRegions()));
        return request;
    }
}
