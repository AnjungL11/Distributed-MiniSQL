package com.minisql.region.master;

import com.minisql.region.model.MasterEndpoint;
import com.minisql.region.model.RegionHeartbeatState;
import com.minisql.region.model.RegionServerInfo;
import com.minisql.rpc.master.HeartbeatRequest;
import com.minisql.rpc.master.HeartbeatResponse;
import com.minisql.rpc.master.MasterService;
import com.minisql.rpc.master.RoutingResponse;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterHeartbeatTaskTest {
    @Test
    void sendsHeartbeatToCurrentMaster() throws Exception {
        CapturingMaster master = new CapturingMaster("MigrateRegion");
        FakeMasterServer server = FakeMasterServer.start(master);
        try {
            MasterInstructionHandler handler = new MasterInstructionHandler();
            MasterHeartbeatTask task = new MasterHeartbeatTask(
                    new MasterClient(3000),
                    () -> Optional.of(new MasterEndpoint("127.0.0.1", server.getPort())),
                    () -> state("127.0.0.1", 19090),
                    handler,
                    1000);

            assertTrue(task.sendOnce());
            assertEquals("127.0.0.1", master.lastRequest.get().getRegionServerIp());
            assertEquals(19090, master.lastRequest.get().getPort());
            assertEquals("MigrateRegion", handler.getLastInstruction());
        } finally {
            server.stop();
        }
    }

    @Test
    void switchesMasterEndpoint() throws Exception {
        CapturingMaster first = new CapturingMaster("NONE");
        CapturingMaster second = new CapturingMaster("NONE");
        FakeMasterServer firstServer = FakeMasterServer.start(first);
        FakeMasterServer secondServer = FakeMasterServer.start(second);
        AtomicReference<MasterEndpoint> endpoint = new AtomicReference<>(
                new MasterEndpoint("127.0.0.1", firstServer.getPort()));
        try {
            MasterHeartbeatTask task = new MasterHeartbeatTask(
                    new MasterClient(3000),
                    () -> Optional.of(endpoint.get()),
                    () -> state("127.0.0.1", 19091),
                    new MasterInstructionHandler(),
                    1000);

            assertTrue(task.sendOnce());
            endpoint.set(new MasterEndpoint("127.0.0.1", secondServer.getPort()));
            assertTrue(task.sendOnce());

            assertEquals(1, first.count.get());
            assertEquals(1, second.count.get());
        } finally {
            firstServer.stop();
            secondServer.stop();
        }
    }

    @Test
    void unreachableMasterReturnsFalse() {
        MasterHeartbeatTask task = new MasterHeartbeatTask(
                new MasterClient(100),
                () -> Optional.of(new MasterEndpoint("127.0.0.1", freePort())),
                () -> state("127.0.0.1", 19092),
                new MasterInstructionHandler(),
                1000);

        assertFalse(task.sendOnce());
    }

    private RegionHeartbeatState state(String ip, int port) {
        return RegionHeartbeatState.from(
                new RegionServerInfo(ip + ":" + port, ip, port),
                Arrays.asList("student"),
                1);
    }

    private int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class CapturingMaster implements MasterService.Iface {
        private final String instruction;
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<HeartbeatRequest> lastRequest = new AtomicReference<>();

        private CapturingMaster(String instruction) {
            this.instruction = instruction;
        }

        @Override
        public HeartbeatResponse sendHeartbeat(HeartbeatRequest request) throws TException {
            count.incrementAndGet();
            lastRequest.set(request);
            return new HeartbeatResponse(true, instruction);
        }

        @Override
        public RoutingResponse getTableRouting(String tableName) throws TException {
            return new RoutingResponse(false, "", 0);
        }

        @Override
        public boolean createTable(String tableNam, String schemaJson) throws TException {
            return false;
        }

        @Override
        public boolean dropTable(String tableName) throws TException {
            return false;
        }
    }

    private static class FakeMasterServer {
        private final int port;
        private final TServer server;
        private final Thread thread;

        private FakeMasterServer(int port, TServer server, Thread thread) {
            this.port = port;
            this.server = server;
            this.thread = thread;
        }

        private static FakeMasterServer start(MasterService.Iface handler) throws Exception {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            TServerSocket socket = new TServerSocket(port);
            TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(socket)
                    .processor(new MasterService.Processor<>(handler)));
            Thread thread = new Thread(server::serve, "fake-master-" + port);
            thread.setDaemon(true);
            thread.start();
            for (int i = 0; i < 20 && !server.isServing(); i++) {
                Thread.sleep(50);
            }
            return new FakeMasterServer(port, server, thread);
        }

        private int getPort() {
            return port;
        }

        private void stop() throws Exception {
            server.stop();
            thread.join(2000);
        }
    }
}
