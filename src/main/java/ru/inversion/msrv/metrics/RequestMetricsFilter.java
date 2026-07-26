package ru.inversion.msrv.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

public final class RequestMetricsFilter implements Filter {

    private final MeterRegistry registry;

    public RequestMetricsFilter(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse rsp = (HttpServletResponse) response;

        if ("/metrics".equals(req.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        final Timer.Sample sample = Timer.start(registry);

        try {
            chain.doFilter(request, response);
        } finally {
            sample.stop(
                    registry.timer(
                            "xxi.http.request",
                            "method", req.getMethod(),
                            "status", Integer.toString(rsp.getStatus())
                    )
            );
        }
    }
}