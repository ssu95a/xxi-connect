package ru.inversion.msrv;

import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.config.PreparedObjectRegistry;
import ru.inversion.msrv.metrics.*;
import ru.inversion.msrv.tech_cred.TechCredentialsProvider;
import ru.inversion.utils.AutoCloseableList;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ru.inversion.utils.S.nvl;

public final class XXIConnectApp {

    private static void printRuntimePaths() {
        String userDir = System.getProperty("user.dir");
        String appBase = firstNonBlank(
                System.getProperty("APP_BASE"),
                userDir == null ? null : userDir + "/.."
        );
        String logDir = firstNonBlank(
                System.getProperty("LOG_DIR"),
                appBase == null ? null : appBase + "/logs"
        );
        String logbackFile = System.getProperty("logback.configurationFile");
        String tmpDir = System.getProperty("java.io.tmpdir");

        System.out.println("=== XXI Connect runtime paths ===");
        System.out.println("user.dir                  = " + nvl(userDir));
        System.out.println("APP_BASE (effective)      = " + nvl(appBase));
        System.out.println("LOG_DIR  (effective)      = " + nvl(logDir));
        System.out.println("logback.configurationFile = " + nvl(logbackFile));
        System.out.println("java.io.tmpdir            = " + nvl(tmpDir));
        System.out.println("=================================");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {

        // printRuntimePaths();

        try (AutoCloseableList al = new AutoCloseableList()) {

            final Config config = Config.make();
            al.add(config);

            final PreparedObjectRegistry registry = new PreparedObjectRegistry(config);
            al.add(registry);

            final PrometheusMetrics prometheusMetrics = new PrometheusMetrics(config);
            al.add(prometheusMetrics);

            final TechCredentialsProvider techAuthProvider = TechCredentialsProvider.createDefault(config);
            al.add(techAuthProvider);

            final TargetRegistry targetRegistry = new TargetRegistry(config, techAuthProvider, prometheusMetrics.registry() );
            al.add(targetRegistry);

            final int port = config.get("boot.http.port", Integer.class, 8080);

            final Server server = new Server();
            server.setStopAtShutdown(true);
            server.setStopTimeout(15_000);

            al.add(() -> stopQuietly(server));

            final ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);

            final ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            ctx.setContextPath("/");

            ctx.addFilter(
                new FilterHolder(new RequestMetricsFilter(prometheusMetrics.registry())),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)
            );

            ctx.addFilter(
                new FilterHolder(new ErrorHandlingFilter()),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)
            );

            ctx.addFilter(
                new FilterHolder(
                        new SecurityHeadersFilter(true)
                ),
                "/*",
                EnumSet.of(DispatcherType.REQUEST)
            );

            ctx.addFilter (
                new FilterHolder(new InputFilter(targetRegistry, registry )),"/auth/*",EnumSet.of(DispatcherType.REQUEST)
            );

            ctx.addServlet( new ServletHolder(new AuthServlet(prometheusMetrics.registry())), "/auth/*");

            ctx.addServlet( new ServletHolder(new StateServlet(targetRegistry,config)), "/state/*" );

            ctx.addFilter(
                new FilterHolder(new AdminFilter(config)),
                "/admin/*",
                EnumSet.of(DispatcherType.REQUEST)
            );

            ctx.addServlet(new ServletHolder(new AdminServlet(config, targetRegistry, () -> {
                try {
                    server.stop();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to stop server", e);
                }
            })), "/admin/*");

            ctx.addServlet(
                    new ServletHolder(
                            new PrometheusServlet(prometheusMetrics.registry())
                    ),
                    "/metrics"
            );

            server.setHandler(ctx);

            server.start();

            ServerStartupInfo.print(server, "/auth", "/state", "/admin");

            server.join();

        } catch (Exception ex) {
            System.err.println("FATAL: " + ex);
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** */
    private static void stopQuietly(Server server) {
        try {
            server.stop();
        } catch (Exception ignored) {
        }

        try {
            server.destroy();
        } catch (Exception ignored) {
        }
    }

}