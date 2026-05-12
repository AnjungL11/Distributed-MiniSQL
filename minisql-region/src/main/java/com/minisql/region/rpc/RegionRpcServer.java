package com.minisql.region.rpc;

import com.minisql.rpc.region.RegionService;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RegionRpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RegionRpcServer.class);

    private final int port;
    private final RegionService.Iface handler;
    private TServer server;
    private Thread serverThread;

    public RegionRpcServer(int port, RegionService.Iface handler) {
        this.port = port;
        this.handler = handler;
    }

    public synchronized void start() throws TTransportException {
        if (server != null && server.isServing()) {
            return;
        }
        TServerSocket serverSocket = new TServerSocket(port);
        RegionService.Processor<RegionService.Iface> processor = new RegionService.Processor<>(handler);
        server = new TThreadPoolServer(new TThreadPoolServer.Args(serverSocket).processor(processor));
        CountDownLatch serving = new CountDownLatch(1);
        serverThread = new Thread(() -> {
            serving.countDown();
            logger.info("Region Thrift RPC server listening on port {}", port);
            server.serve();
        }, "region-rpc-server-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
        awaitStartup(serving);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public int getPort() {
        return port;
    }

    private void awaitStartup(CountDownLatch serving) {
        try {
            serving.await(2, TimeUnit.SECONDS);
            for (int i = 0; i < 20; i++) {
                if (server.isServing()) {
                    return;
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
