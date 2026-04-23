package ru.inversion.msrv;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.config.ProcessorCacheSource;
import ru.inversion.msrv.validation.Errors;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Objects;

import static org.slf4j.LoggerFactory.getLogger;
import static ru.inversion.msrv.validation.Errors.ErrorCode.ADMIN_COMMAND_INVALID;

/** */
public final class AdminServlet extends HttpServlet {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private final TargetRegistry registry;
    private final ProcessorCacheSource overrides;
    private final Runnable killCommand;

    /** */
    public AdminServlet(Config config, TargetRegistry registry, Runnable killCommand)
    {
        this.registry    = Objects.requireNonNull( registry, "'registry' is null" );
        this.overrides   = Objects.requireNonNull( config,   "'config' is null"   ).processorCacheSource();
        this.killCommand = killCommand;
    }

    /** */
    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp ) throws IOException {

        final String pi = PathTools.normalizePath( req.getPathInfo() );

        switch (pi) {
            case "/ping" -> {
                writeOk( resp, xmlOk("adminPing") );
                return;
            }
            case "/targets" -> {
                writeOk( resp, targetsXml() );
                return;
            }
            case "/pools/metrics" -> {
                writeOk(resp, poolsMetricsXml());
                return;
            }
            case "/config/overrides" -> {
                writeOk(resp, overridesXml());
                return;
            }
        }

        final String[] p = PathTools.splitPath(pi);

        if( p.length == 4 && "targets".equals(p[0]) && "pool".equals(p[2]) && "metrics".equals(p[3]) )
        {
            writeOk( resp, poolMetricsXml(p[1]) );
            return;
        }

        throw Errors.badRequest( "Unknown admin endpoint" );
    }

    /** */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        final String pi = PathTools.normalizePath( req.getPathInfo() );

        if ("/kill".equals(pi)) {
            handleKill(req, resp);
            return;
        }

        if ("/config/overrides/set".equals(pi) )
        {
            final String key   = normalizeOverrideKey( req.getParameter("key") );
            final String value = requireParam( req, "value" );

            overrides.set( key, value );

            writeOk( resp, overrideChangedXml("overrideSet", key, value, false) );
            return;
        }

        if ("/config/overrides/unset".equals(pi)) {
            final String key = normalizeOverrideKey(req.getParameter("key"));

            overrides.unset(key);

            writeOk(resp, overrideChangedXml("overrideUnset", key, null, false));
            return;
        }

        final String[] p = PathTools.splitPath(pi);

        if (p.length == 4 && "targets".equals(p[0]) && "pool".equals(p[2]) && "hard-reset".equals(p[3])) {
            final boolean done = registry.hardResetPool(p[1]);
            writeOk(resp, hardResetXml(p[1], done));
            return;
        }

        if (p.length == 4 && "targets".equals(p[0]) && "pool".equals(p[2]) && "soft-reset".equals(p[3])) {
            final boolean done = registry.softResetPool(p[1]);
            writeOk( resp, softResetXml(p[1], done));
            return;
        }

        throw Errors.of( ADMIN_COMMAND_INVALID, "Unknown admin endpoint" );
    }

    private static String softResetXml(String alias, boolean done) {
        IDco d = new Dco("poolSoftReset");
        d.e("status").set("OK");
        d.e("alias" ).set(alias);
        d.e("done"  ).set(String.valueOf(done));
        d.e("effect").set("Soft eviction requested for current pool connections");
        return d.asXml();
    }

    /** */
    private void handleKill(HttpServletRequest req, HttpServletResponse rsp) throws IOException {

        logger.warn("admin.kill.accepted uri={} remote={}", req.getRequestURI(), req.getRemoteAddr() );

        if( killCommand == null )
        {
            rsp.setStatus( HttpServletResponse.SC_SERVICE_UNAVAILABLE );
            rsp.setContentType("text/plain; charset=utf-8");
            rsp.setHeader     ("Cache-Control", "no-store");
            rsp.getWriter().write("fail\n");
            rsp.getWriter().write("warn: kill command is not set! \n");
            rsp.getWriter().flush();

            return;
        }

        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/plain; charset=utf-8");
        rsp.setHeader     ("Cache-Control", "no-store");
        rsp.getWriter().write("OK\n");
        rsp.getWriter().write("action=kill\n");
        rsp.getWriter().flush();

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            try {
                logger.warn("admin.kill.execute");
                killCommand.run();
            } catch (Throwable th) {
                logger.error("admin.kill.fail err={}", th.toString(), th);
            }
        }, "xxi-admin-kill");

        t.setDaemon(true);
        t.start();
    }

    /** */
    private static void writeOk(HttpServletResponse resp, String xml) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/xml; charset=utf-8");
        resp.getWriter().write(xml);
    }

    /** */
    private static String requireParam( HttpServletRequest req, String name ) {
        final String value = req.getParameter(name);
        if( value == null )
            throw Errors.badRequest( "Missing parameter: " + name);
        return value;
    }

    /** */
    private static String normalizeOverrideKey(String rawKey)
    {
        final String key = Config.normalizeKey(rawKey);

        if( !(key.startsWith("pool.") || key.startsWith("ds.")) )
            throw Errors.of( ADMIN_COMMAND_INVALID, "Only pool.* and ds.* override keys are allowed" );

        return key;
    }

    /** */
    private static String xmlOk(String root) {
        final IDco d = new Dco(root);
        d.e("status").set("OK");
        return d.asXml();
    }

    /** */
    private String targetsXml() {
        final IDco d = new Dco("adminTargets");
        d.e("status").set("OK");
        IDco list = d.e("targets");

        for( String alias : registry.aliases())
             list.append("target").a("alias").set(alias);

        return d.asXml();
    }

    private String poolsMetricsXml() {
        IDco d = new Dco("adminPoolsMetrics");
        d.e("status").set("OK");
        IDco list = d.e("pools");

        for (Map.Entry<String, Map<String, Object>> e : registry.poolMetricsAll().entrySet()) {
            IDco item = list.e("pool");
            item.a("alias").set(e.getKey());
            writeMap(item, e.getValue());
        }

        return d.asXml();
    }

    /** */
    private String poolMetricsXml(String alias) {
        final IDco d = new Dco("adminPoolMetrics");
        d.e("status").set("OK" );
        d.e("alias" ).set(alias);
        writeMap(d.e("metrics"), registry.poolMetrics(alias));
        return d.asXml();
    }

    /** */
    private String overridesXml()
    {
        IDco d = new Dco("adminOverrides");
        d.e("status"   ).set("OK");
        d.e("semantics").set("Overrides affect only pools created after explicit hard reset");

        IDco list = d.e("overrides");

        for(Map.Entry<String, String> e : overrides.snapshot().entrySet()) {
            IDco item = list.e("override");
            item.a("key").set(e.getKey());
            item.e("value").set(e.getValue() == null ? "" : e.getValue());
        }

        return d.asXml();
    }

    private static String overrideChangedXml(String root, String key, String value, boolean appliedNow) {
        IDco d = new Dco(root);
        d.e("status").set("OK");
        d.e("key").set(key);
        if (value != null) {
            d.e("value").set(value);
        }
        d.e("appliedToLivePool").set(String.valueOf(appliedNow));
        d.e("hardResetRequired").set("true");
        return d.asXml();
    }

    private static String hardResetXml(String alias, boolean done) {
        IDco d = new Dco("poolHardReset");
        d.e("status ").set("OK");
        d.e("alias" ).set( alias );
        d.e("done"  ).set( String.valueOf(done) );
        d.e("info"  ).set("Current pool instance removed; next resolve(alias) will build pool using current overrides/config");
        return d.asXml();
    }

    private static void writeMap(IDco parent, Map<String, Object> m) {
        for( Map.Entry<String, Object> e : m.entrySet()) {
            parent.e(e.getKey()).set(e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
    }
}