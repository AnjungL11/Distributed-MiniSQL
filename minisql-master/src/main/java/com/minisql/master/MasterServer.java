package com.minisql.master;

import com.minisql.common.ConfigReader;
import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;
import com.minisql.rpc.master.MasterService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class MasterServer extends LeaderSelectorListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MasterServer.class);
    
    private final ZkClient zkClient;
    private final String masterName;
    private final int rpcPort;
    private LeaderSelector leaderSelector;
    private TServer thriftServer;

    public MasterServer() {
        this.zkClient = new ZkClient();
        // 先启动客户端，状态变为 STARTED，才能执行后面的 ZK 操作
        this.zkClient.start();
        // 从统一配置文件读取自身配置
        this.masterName = ConfigReader.getString("master.name", "Master-Node-1");
        this.rpcPort = ConfigReader.getInt("master.rpc.port", 8080);
        
        // 确保 ZK 中的基础目录已经创建
        this.zkClient.initZkDirectories();
        
        // 初始化 Leader 选主器，指定排队路径和监听器
        this.leaderSelector = new LeaderSelector(zkClient.getClient(), ZkConstants.MASTER_ELECTION_PATH, this);
        // 保证一旦因网络抖动丢失 Leader 身份，还能重新排队参与下一轮抢占
        this.leaderSelector.autoRequeue();
    }

    public void start() {
        // zkClient.start();
        logger.info("[{}] 进程启动完毕，加入 ZK 选主队列...", masterName);
        leaderSelector.start();
    }

    /**
     * 当该实例成功抢占到分布式锁时，Curator 会回调此方法。
     * 只要这个方法不退出，该实例就会一直保持 Active Master 状态
     */
    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        logger.info("=================================================");
        logger.info("🚀 竞选成功！[{}] 正式晋升为 Active Master！", masterName);
        logger.info("=================================================");
        
        // 获取本机的实际 IP（生产环境中需结合网卡获取，此处演示直接获取本地 IP）
        String activeMasterAddress = InetAddress.getLocalHost().getHostAddress() + ":" + rpcPort;
        
        try {
            // 1. 将自己的 RPC 地址写到 ZK 的 /active_master 节点（临时节点）
            // 如果节点残留则先删除（应对极端僵尸节点情况）
            if (client.checkExists().forPath(ZkConstants.ACTIVE_MASTER_PATH) != null) {
                client.delete().forPath(ZkConstants.ACTIVE_MASTER_PATH);
            }
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(ZkConstants.ACTIVE_MASTER_PATH, activeMasterAddress.getBytes(StandardCharsets.UTF_8));
            
            logger.info("已向集群广播服务地址: {}", activeMasterAddress);

            // 2. 启动对外服务的 Thrift RPC 服务器（此方法会阻塞线程）
            startRpcServer();

        } catch (InterruptedException e) {
            logger.warn("[{}] 进程被中断，即将交出 Active Master 权限...", masterName);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Active Master 运行期间发生致命异常", e);
        } finally {
            // 退出方法意味着让出 Leader 锁，必须关闭对外服务
            stopRpcServer();
            logger.info("[{}] 已卸任 Active Master 状态，降级为 Backup。", masterName);
        }
    }

    private void startRpcServer() throws Exception {
        // 将具体的业务逻辑实现类绑定到 Thrift Processor 上
        MasterService.Processor<MasterServiceImpl> processor = 
                new MasterService.Processor<>(new MasterServiceImpl(zkClient));
        
        // 绑定监听端口
        TServerTransport serverTransport = new TServerSocket(rpcPort);
        
        // 配置线程池工作模式
        TThreadPoolServer.Args args = new TThreadPoolServer.Args(serverTransport);
        args.processor(processor);
        args.minWorkerThreads(5);
        args.maxWorkerThreads(20);
        
        thriftServer = new TThreadPoolServer(args);
        
        logger.info("Master RPC Server 开始接客，监听端口: {}", rpcPort);
        thriftServer.serve(); // 线程将阻塞在此处，直到服务被 stop()
    }

    private void stopRpcServer() {
        if (thriftServer != null && thriftServer.isServing()) {
            thriftServer.stop();
            logger.info("Master RPC Server 监听已停止。");
        }
    }

    public static void main(String[] args) {
        MasterServer server = new MasterServer();
        server.start();
        
        // 保持主线程一直存活，否则程序直接退出了
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.info("Master 主程序平滑退出");
        }
    }
}