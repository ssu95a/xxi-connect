package ru.inversion.msrv;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.inversion.msrv.config.Config;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.io.IOException;
import java.lang.management.ManagementFactory;

public final class StateServlet extends HttpServlet {

    private final TargetRegistry registry;
    private final Config config;

    public StateServlet(TargetRegistry registry, Config config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        final String pi = U.nvl( req.getPathInfo(), S.EMPTY_STRING );

        resp.setHeader("Cache-Control", "no-store");
        resp.setContentType("text/plain; charset=utf-8");

        switch (pi) {
            case "", "/", "/info" -> ok( resp, buildInfo() );
            case "/ping" -> ok(resp, "OK");
            case "/ready"-> {
                if (registry.isReady()) {
                    ok(resp, "READY: " + registry.statusInfo());
                } else {
                    serviceUnavailable(resp, "NOT_READY: " + registry.statusInfo());
                }
            }
            default -> notFound(resp, "NOT FOUND");
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }

    private String buildInfo() {

        final String version = firstNonBlank( StateServlet.class.getPackage() == null ? null : StateServlet.class.getPackage().getImplementationVersion(),"dev" );

        final long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        return  "service=xxi-connect\n"
                + "instanceId=" + config.serverInstanceId() + "\n"
                + "version=" + version + "\n"
                + "java=" + System.getProperty("java.version") + "\n"
                + "uptimeSec=" + (uptimeMs / 1000L) + "\n"
                + registry.statusInfo();
    }


    private static void ok(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(200);
        resp.getWriter().write(msg + "\n");
    }

    private static void serviceUnavailable(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(503);
        resp.getWriter().write(msg + "\n");
    }

    private static void notFound(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(404);
        resp.getWriter().write(msg + "\n");
    }
}