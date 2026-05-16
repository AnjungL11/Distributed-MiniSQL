package com.minisql.client.model;

import java.util.Objects;

public class Endpoint {
    private final String host;
    private final int port;

    public Endpoint(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        this.host = host.trim();
        this.port = port;
    }

    public static Endpoint parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        String trimmed = value.trim();
        int separator = trimmed.lastIndexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            throw new IllegalArgumentException("endpoint must be host:port: " + value);
        }
        return new Endpoint(trimmed.substring(0, separator), Integer.parseInt(trimmed.substring(separator + 1)));
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Endpoint)) {
            return false;
        }
        Endpoint endpoint = (Endpoint) o;
        return port == endpoint.port && host.equals(endpoint.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
