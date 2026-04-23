package ru.inversion.msrv;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class ServerStartupInfo {

    private ServerStartupInfo() {}

    public static void print(Server server, String... paths )
    {

        HostInfo host = hostInfo();
        List<String> ipv4 = localIPv4();

        boolean httpsEnabled = false;
        List<ConnectorInfo> connectors = new ArrayList<>();

        for (Connector c : server.getConnectors()) {
            if (!(c instanceof ServerConnector sc)) continue;

            int port = sc.getLocalPort(); // корректно только после server.start()
            String bindHost = sc.getHost();
            if (bindHost == null || bindHost.isBlank()) bindHost = "0.0.0.0";

            boolean isHttps = hasSsl(sc);
            httpsEnabled |= isHttps;

            connectors.add(new ConnectorInfo(bindHost, port, isHttps));
        }

        System.out.println("==================================================");
        System.out.println("XXIConnect Auth server started");
        System.out.println("Host name  : " + host.hostName);
        System.out.println("DNS (canon): " + host.canonicalName);
        System.out.println("HTTPS      : " + (httpsEnabled ? "ENABLED" : "DISABLED"));

        System.out.println("Connectors :");
        for (ConnectorInfo ci : connectors) {
            System.out.println("  - " + ci.scheme() + "://" + ci.bindHost + ":" + ci.port);
        }

        System.out.println("IPv4       :");
        if (ipv4.isEmpty()) {
            System.out.println("  - (no non-loopback IPv4 detected)");
        } else {
            for (String ip : ipv4) System.out.println("  - " + ip);
        }

        if (paths != null && paths.length > 0 && !connectors.isEmpty()) {
            System.out.println("Endpoints  :");
            for (ConnectorInfo ci : connectors) {
                String base = ci.scheme() + "://" + ci.bindHost + ":" + ci.port;

                if ("0.0.0.0".equals(ci.bindHost)) {
                    for (String ip : ipv4) {
                        for (String p : paths) {
                            System.out.println("  - " + base.replace("0.0.0.0", ip) + normalizePath(p));
                        }
                    }
                } else {
                    for (String p : paths) {
                        System.out.println("  - " + base + normalizePath(p));
                    }
                }
            }
        }
        System.out.println("==================================================");
    }

    private static boolean hasSsl(ServerConnector sc) {
        for (ConnectionFactory cf : sc.getConnectionFactories()) {
            if (cf instanceof SslConnectionFactory) return true;
        }
        return false;
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        return p.startsWith("/") ? p : ("/" + p);
    }

    private static HostInfo hostInfo() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            String host = safe(local.getHostName(), "?");
            String canon = safe(local.getCanonicalHostName(), "?");
            return new HostInfo(host, canon);
        } catch (Exception e) {
            return new HostInfo("?", "?");
        }
    }

    private static String safe(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isEmpty() ? def : t;
    }

    private static List<String> localIPv4() {
        try {
            List<String> out = new ArrayList<>();
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            if (ifs == null) return Collections.emptyList();

            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                } catch (Exception ignored) {
                    continue;
                }

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address)) continue;
                    if (a.isLoopbackAddress()) continue;

                    String ip = a.getHostAddress();
                    // На всякий случай отфильтруем 127.0.0.1 и пустое
                    if (ip == null || ip.isBlank() || ip.startsWith("127.")) continue;

                    out.add(ip);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private record HostInfo(String hostName, String canonicalName) {}
    private record ConnectorInfo(String bindHost, int port, boolean https) {
        String scheme() { return https ? "https" : "http"; }
    }
}