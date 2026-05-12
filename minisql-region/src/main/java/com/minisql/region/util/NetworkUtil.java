package com.minisql.region.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class NetworkUtil {
    private static final String LOCALHOST = "127.0.0.1";

    private NetworkUtil() {
    }

    public static String resolveLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!isUsableInterface(networkInterface)) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isUsableIpv4(address)) {
                        return address.getHostAddress();
                    }
                }
            }
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost instanceof Inet4Address) {
                return localHost.getHostAddress();
            }
        } catch (Exception ignored) {
            return LOCALHOST;
        }
        return LOCALHOST;
    }

    public static boolean isUsableIpv4(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isAnyLocalAddress()
                && !address.isMulticastAddress();
    }

    private static boolean isUsableInterface(NetworkInterface networkInterface) throws Exception {
        return networkInterface != null
                && networkInterface.isUp()
                && !networkInterface.isLoopback()
                && !networkInterface.isVirtual();
    }
}
