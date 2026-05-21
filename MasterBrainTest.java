package com.minisql.master;

import com.minisql.common.ZkClient;
import com.minisql.common.ZkConstants;
import com.minisql.rpc.master.HeartbeatRequest;
import com.minisql.rpc.master.MasterService;
import com.minisql.rpc.master.RoutingResponse;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.zookeeper.CreateMode;

public class MasterBrainTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====== 准备测试环境 ======");
        
        // 1. 启动一个本地的 ZK 客户端，用来模拟 Region Server 注册临时节点
        ZkClient zkClient = new ZkClient();
        zkClient.start();
        
        System.out.println("\n[模拟动作 1] 两台 Region Server 启动，向 ZK 注册...");
        String region1Path = ZkConstants.getRegionServerPath("127.0.0.1", 9091);
        String region2Path = ZkConstants.getRegionServerPath("127.0.0.1", 9092);
        
        // 模拟创建临时节点
        zkClient.getClient().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(region1Path);
        zkClient.getClient().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(region2Path);
        
        // 暂停 1 秒，给 Master 的 Watcher 一点反应时间
        Thread.sleep(1000); 

        System.out.println("\n[模拟动作 2] Client 连接 Master (8080端口)...");
        TTransport transport = new TSocket("127.0.0.1", 8080);
        transport.open();
        TProtocol protocol = new TBinaryProtocol(transport);
        MasterService.Client masterRpcClient = new MasterService.Client(protocol);

        System.out.println("\n[模拟动作 3] Region Server 开始发送心跳上报负载...");
        // 假设 Node1 比较忙，负载 80.5；Node2 比较空闲，负载 10.2
        HeartbeatRequest hb1 = new HeartbeatRequest();
        hb1.setRegionServerIp("127.0.0.1");
        hb1.setPort(9091);
        hb1.setLoadScore(80.5);
        
        HeartbeatRequest hb2 = new HeartbeatRequest();
        hb2.setRegionServerIp("127.0.0.1");
        hb2.setPort(9092);
        hb2.setLoadScore(10.2);
        
        masterRpcClient.sendHeartbeat(hb1);
        masterRpcClient.sendHeartbeat(hb2);
        Thread.sleep(500); // 让内存稍微消化一下

        System.out.println("\n[模拟动作 4] Client 发起建表请求 (测试负载均衡)...");
        // 预期：Master 应该把表分配给负载较低的 Node2 (9092)
        boolean createResult = masterRpcClient.createTable("course_table");
        System.out.println("建表请求返回: " + createResult);

        System.out.println("\n[模拟动作 5] Client 查询表路由 (测试路由分发)...");
        RoutingResponse routingResponse = masterRpcClient.getTableRouting("course_table");
        System.out.println("获取路由返回 -> IP: " + routingResponse.getRegionServerIp() + ", 端口: " + routingResponse.getPort());

        System.out.println("\n[模拟动作 6] 拔网线！模拟负载最低的 Region2 (9092) 宕机...");
        // 模拟节点断网，临时节点被 ZK 删除
        zkClient.getClient().delete().forPath(region2Path);
        Thread.sleep(1000); // 观察 Master 控制台是否有离线报警

        System.out.println("\n====== 测试结束 ======");
        transport.close();
        zkClient.close();
    }
}