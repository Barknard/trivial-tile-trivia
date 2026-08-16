package com.barknard.trivialtile.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Picks the LAN address other devices on the same WiFi should connect to.
 * Mirrors the ordering the old Node server used: 192.168.x, then 10.x, then
 * 172.16-31.x, then whatever non-loopback IPv4 is left.
 */
public final class NetUtils {

    private NetUtils() {
    }

    public static List<String> localIpv4Addresses() {
        List<String> wifi = new ArrayList<>();
        List<String> other = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return wifi;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                try {
                    if (!nif.isUp() || nif.isLoopback()) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
                String name = nif.getName() == null ? "" : nif.getName().toLowerCase();
                // Skip cellular and virtual interfaces - players can't reach those.
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")
                        || name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("dummy")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (ip == null) {
                        continue;
                    }
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) {
                        wifi.add(ip);
                    } else {
                        other.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            Slog.e("net", "Could not enumerate network interfaces", e);
        }
        List<String> all = new ArrayList<>(wifi);
        all.addAll(other);
        return all;
    }

    /** Best guess at the address to hand out to players. Never null. */
    public static String bestLanAddress() {
        List<String> candidates = localIpv4Addresses();
        String pick = firstMatching(candidates, "192.168.");
        if (pick == null) {
            pick = firstMatching(candidates, "10.");
        }
        if (pick == null) {
            for (String ip : candidates) {
                if (ip.matches("^172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
                    pick = ip;
                    break;
                }
            }
        }
        if (pick == null && !candidates.isEmpty()) {
            pick = candidates.get(0);
        }
        return pick == null ? "localhost" : pick;
    }

    public static List<String> candidatesForLog() {
        return Collections.unmodifiableList(localIpv4Addresses());
    }

    private static String firstMatching(List<String> candidates, String prefix) {
        for (String ip : candidates) {
            if (ip.startsWith(prefix)) {
                return ip;
            }
        }
        return null;
    }
}
