package ru.inversion.msrv.metrics;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import ru.inversion.msrv.config.Config;

import java.io.File;

public final class PrometheusMetrics implements AutoCloseable {

    private final PrometheusMeterRegistry registry;
    private final JvmGcMetrics gcMetrics;
    private final LogbackMetrics logbackMetrics;

    public PrometheusMetrics(Config config) {

        registry = new PrometheusMeterRegistry( PrometheusConfig.DEFAULT );
        registry.config().commonTags( "application", "xxi-connect", "instance", config.serverInstanceId() );

        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);

        gcMetrics = new JvmGcMetrics();
        gcMetrics.bindTo(registry);

        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);

        new JvmInfoMetrics().bindTo(registry);
        new JvmCompilationMetrics().bindTo(registry);
        new DiskSpaceMetrics(new File(System.getProperty("user.dir"))).bindTo(registry);

        logbackMetrics = new LogbackMetrics();
        logbackMetrics.bindTo(registry);

    }

    public PrometheusMeterRegistry registry() {
        return registry;
    }

    @Override
    public void close() {
        logbackMetrics.close();
        gcMetrics.close();
        registry.close();
    }
}