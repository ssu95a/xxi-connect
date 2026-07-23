package ru.inversion.msrv.metrics;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public final class PrometheusMetrics implements AutoCloseable {

    private final PrometheusMeterRegistry registry;
    private final JvmGcMetrics gcMetrics;

    public PrometheusMetrics() {

        registry = new PrometheusMeterRegistry( PrometheusConfig.DEFAULT );

        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);

        gcMetrics = new JvmGcMetrics();
        gcMetrics.bindTo(registry);

        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
    }

    public PrometheusMeterRegistry registry() {
        return registry;
    }

    @Override
    public void close() {
        gcMetrics.close();
        registry.close();
    }
}