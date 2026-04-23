package ru.inversion.msrv.metrics;

import jakarta.servlet.http.HttpServletRequest;

public final class NoopMetricsPublisher implements MetricsPublisher {

    @Override
    public void publish(HttpServletRequest req, Metrics.Context metrics) {
        // no-op
    }
}