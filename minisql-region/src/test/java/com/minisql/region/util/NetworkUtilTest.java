package com.minisql.region.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkUtilTest {
    @Test
    void resolvesSomeLocalAddress() {
        assertNotNull(NetworkUtil.resolveLocalIpv4());
    }

    @Test
    void loopbackAddressIsNotAdvertisedAsUsableIpv4() throws Exception {
        assertFalse(NetworkUtil.isUsableIpv4(InetAddress.getByName("127.0.0.1")));
    }
}
