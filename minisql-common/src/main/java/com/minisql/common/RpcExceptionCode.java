package com.minisql.common;

/**
 * 全局统一的 RPC 调用状态/错误码
 */
public enum RpcExceptionCode {
    
    SUCCESS(200, "操作成功"),
    
    // Master 模块错误 (1000 - 1999)
    MASTER_NOT_AVAILABLE(1001, "未找到 Active Master"),
    TABLE_ALREADY_EXISTS(1002, "表已存在"),
    TABLE_NOT_FOUND(1003, "表不存在"),
    NO_AVAILABLE_REGION_SERVER(1004, "当前无存活的 Region Server 可用"),
    
    // Region Server 模块错误 (2000 - 2999)
    REGION_SERVER_OFFLINE(2001, "对应的 Region Server 已离线"),
    SQL_EXECUTE_ERROR(2002, "底层 SQL 执行异常"),
    
    // Zookeeper / 网络故障 (3000 - 3999)
    ZK_CONNECTION_TIMEOUT(3001, "Zookeeper 连接超时"),
    RPC_NETWORK_ERROR(3002, "RPC 网络通信异常");

    private final int code;
    private final String message;

    RpcExceptionCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}