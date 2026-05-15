package com.minisql.region.model;

public class MasterEndpoint {
    private String host;
    private int port;

    public MasterEndpoint() {
    }

    public MasterEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static MasterEndpoint parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("master endpoint must not be blank");
        }
        String[] parts = value.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("master endpoint must be host:port");
        }
        return new MasterEndpoint(parts[0], Integer.parseInt(parts[1]));
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String asAddress() {
        return host + ":" + port;
    }
}
