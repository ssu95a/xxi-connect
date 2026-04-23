package ru.inversion.msrv.metrics;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

public final class MetricsFilter implements Filter {

    private final MetricsPublisher publisher;
    private final String serverInstanceId;
    private final boolean enabled;

    public MetricsFilter(MetricsPublisher publisher, String serverInstanceId, boolean enabled) {
        this.publisher = (publisher != null ? publisher : new NoopMetricsPublisher());
        this.serverInstanceId = trimToNull(serverInstanceId);
        this.enabled = enabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse rsp = (HttpServletResponse) response;

        if (!enabled) {
            req.setAttribute(Metrics.REQUEST_ENABLED_ATTR, Boolean.FALSE);
            chain.doFilter(request, response);
            return;
        }

        req.setAttribute(Metrics.REQUEST_ENABLED_ATTR, Boolean.TRUE);

        final Metrics.Context mc = Metrics.Tools.getOrCreate(req);

        if (serverInstanceId != null) {
            mc.put(Metrics.Key.SERVER_INSTANCE_ID, serverInstanceId);
        }

        mc.put(Metrics.Key.REQ_URI, req.getRequestURI());

        fillClientNetwork(req, mc);

        try (Metrics.Scope span = Metrics.Tools.openSpan(req, "filter", "MetricsFilter", "wrapper_time")) {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException ex) {
                span.fail(ex);
                throw ex;
            } finally {
                mc.put(Metrics.Key.RESP_HTTP_STATUS, rsp.getStatus());
            }
        }

        mc.finish();
        publisher.publish(req, mc);
    }

    private static void fillClientNetwork(HttpServletRequest req, Metrics.Context mc) {
        final String remoteAddr = trimToNull(req.getRemoteAddr());
        final String xff = trimToNull(req.getHeader("X-Forwarded-For"));

        if (xff == null) {
            if (remoteAddr != null) {
                mc.put(Metrics.Key.CLIENT_NET_IP_ORIG, remoteAddr);
            }
            mc.put(Metrics.Key.CLIENT_NET_TRUSTED_PROXY, Boolean.FALSE);
            return;
        }

        final String[] chain = Arrays.stream(xff.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (chain.length > 0) {
            mc.put(Metrics.Key.CLIENT_NET_IP_ORIG, chain[0]);
        } else if (remoteAddr != null) {
            mc.put(Metrics.Key.CLIENT_NET_IP_ORIG, remoteAddr);
        }

        // Пока это означает "request came through proxy/XFF path".
        // Если появится allowlist доверенных proxy, заполняйте уже по trust-logic.
        mc.put(Metrics.Key.CLIENT_NET_TRUSTED_PROXY, Boolean.TRUE);
    }

    private static String trimToNull(String s) {
        if( s == null )
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}