package ru.inversion.msrv.metrics;

import jakarta.servlet.http.HttpServletRequest;

/* */
public interface MetricsPublisher extends AutoCloseable {

    /** */
    void publish( HttpServletRequest req, Metrics.Context metrics );

    @Override
    default void close() {
        // no-op
    }
}
