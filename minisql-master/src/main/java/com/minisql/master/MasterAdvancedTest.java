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

public class MasterAdvancedTest {

    public static void main(String[] args) throws Exception {
        System.out.println("====== 准备进阶测试环境 ======");
        ZkClient zkClient = new ZkClient();
        zkClient.start();

        System.out.println("\n[准备] 启动 3 台模拟 Region Server...");
        String r1 = ZkConstants.getRegionServerPath("127.0.0.1", 9091);
        String r2 = ZkConstants.getRegionServerPath("127.0.0.1", 9092);
        String r3 = ZkConstants.getRegionServerPath("127.0.0.1", 9093);

        zkClient.getClient().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(r1);
        zkClient.getClient().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(r2);
        zkClient.getClient().create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(r3);
        Thread.sleep(1000);

        TTransport transport = new TSocket("127.0.0.1", 8080);
        transport.open();
        TProtocol protocol = new TBinaryProtocol(transport);
        MasterService.Client masterRpcClient = new MasterService.Client(protocol);

        System.out.println("\n[准备] 发送心跳初始化负载：Node1(很忙), Node2(极度空闲), Node3(微忙)...");
        
        // 使用 Setters 初始化对象
        HeartbeatRequest hb1 = new HeartbeatRequest();
        hb1.setRegionServerIp("127.0.0.1"); hb1.setPort(9091); hb1.setLoadScore(85.5);
        masterRpcClient.sendHeartbeat(hb1);

        HeartbeatRequest hb2 = new HeartbeatRequest();
        hb2.setRegionServerIp("127.0.0.1"); hb2.setPort(9092); hb2.setLoadScore(5.0);
        masterRpcClient.sendHeartbeat(hb2);

        HeartbeatRequest hb3 = new HeartbeatRequest();
        hb3.setRegionServerIp("127.0.0.1"); hb3.setPort(9093); hb3.setLoadScore(20.0);
        masterRpcClient.sendHeartbeat(hb3);
        
        Thread.sleep(500);

        // ==========================================
        // 测试任务一：带有完整 Schema 的建表
        // ==========================================
        System.out.println("\n▶ [测试一] 发起带 Schema 的建表请求 (建表 users)");
        // String usersSchema = "{\"tableName\":\"users\",\"columns\":[{\"name\":\"id\",\"type\":\"INT\"},{\"name\":\"name\",\"type\":\"STRING\"}]}";
        String usersSchema = "{\"tableName\":\"users\",\"columns\":[{\"columnName\":\"id\",\"type\":\"INT\"},{\"columnName\":\"name\",\"type\":\"STRING\"}]}";
        boolean createSuccess = masterRpcClient.createTable("users", usersSchema);
        System.out.println("建表 users 返回结果: " + createSuccess);
        
        RoutingResponse routeUsers = masterRpcClient.getTableRouting("users");
        System.out.println("users 表被分配到了: " + routeUsers.getRegionServerIp() + ":" + routeUsers.getPort());

        // ==========================================
        // 测试任务二：删表功能 (dropTable)
        // ==========================================
        System.out.println("\n▶ [测试二] 测试删表功能 (删除 users 表)");
        boolean dropSuccess = masterRpcClient.dropTable("users");
        System.out.println("删表 users 返回结果: " + dropSuccess);
        
        RoutingResponse routeAfterDrop = masterRpcClient.getTableRouting("users");
        System.out.println("再次查询 users 路由，期望失败 -> 状态: " + routeAfterDrop.isIsSuccess());

        // ==========================================
        // 测试任务三：终极容灾重平衡 (Failover)
        // ==========================================
        System.out.println("\n▶ [测试三] 准备容灾测试，新建一张重要表 (orders)");
        // String ordersSchema = "{\"tableName\":\"orders\",\"columns\":[{\"name\":\"order_id\",\"type\":\"INT\"}]}";
        String ordersSchema = "{\"tableName\":\"orders\",\"columns\":[{\"columnName\":\"order_id\",\"type\":\"INT\"}]}";
        masterRpcClient.createTable("orders", ordersSchema);
        RoutingResponse routeOrders = masterRpcClient.getTableRouting("orders");
        System.out.println("orders 表被分配到了: " + routeOrders.getRegionServerIp() + ":" + routeOrders.getPort() + " (预期是负载最低的 9092)");

        System.out.println("\n⚡ [警报] 拔网线！模拟持有 orders 表的 Node2 (9092) 突然宕机燃烧！");
        zkClient.getClient().delete().forPath(r2); // 删掉 9092 节点触发 Watcher
        
        System.out.println("等待 Master 的 Watcher 触发抢救机制 (2秒)...");
        Thread.sleep(2000); 

        System.out.println("\n🔍 [验收] Client 再次查询 orders 表的路由地址...");
        RoutingResponse failoverRoute = masterRpcClient.getTableRouting("orders");
        System.out.println("抢救后的 orders 表被转移到了: " + failoverRoute.getRegionServerIp() + ":" + failoverRoute.getPort());
        System.out.println("如果上面显示的是 9093 (活着的节点里负载最低的)，说明容灾测试完美成功！");

        System.out.println("\n====== 全部测试结束 ======");
        transport.close();
        zkClient.close();
    }
}