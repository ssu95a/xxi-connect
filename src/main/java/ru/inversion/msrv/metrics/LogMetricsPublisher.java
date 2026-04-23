package ru.inversion.msrv.metrics;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

public final class LogMetricsPublisher implements MetricsPublisher, AutoCloseable {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private final boolean enabled;

    public LogMetricsPublisher(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void publish(HttpServletRequest req, Metrics.Context mc) {

        if( !enabled || mc == null || !mc.enabled() )
            return;

        final StringBuilder sb = new StringBuilder(512);
        sb.append("metrics");

        for( Map.Entry<Metrics.Key, Object> e : mc.values().entrySet() )
             appendKv(sb, e.getKey().logKey(), e.getValue());

        appendKv(sb, "component_metrics", renderComponentMetrics(mc));

        logger.info(sb.toString());
    }

    private static String renderComponentMetrics(Metrics.Context mc) {
        if (mc.componentMetrics().isEmpty()) {
            return "[]";
        }

        final StringBuilder sb = new StringBuilder(256);
        sb.append('[');

        boolean first = true;
        for (Metrics.ComponentMetric cm : mc.componentMetrics()) {
            if (!first) {
                sb.append(',');
            }
            first = false;

            sb.append('{');
            appendInline(sb, "ns", cm.namespace());
            sb.append(',');
            appendInline(sb, "component", cm.component());
            sb.append(',');
            appendInline(sb, "type", cm.metricType());
            sb.append(',');
            appendInline(sb, "ms", cm.elapsedMs());
            sb.append(',');
            appendInline(sb, "outcome", cm.outcome());

            if (cm.errorClass() != null && !cm.errorClass().isBlank()) {
                sb.append(',');
                appendInline(sb, "error", cm.errorClass());
            }

            sb.append('}');
        }

        sb.append(']');
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }

        sb.append(' ')
                .append(key)
                .append('=')
                .append(quoteIfNeeded(String.valueOf(value)));
    }

    private static void appendInline(StringBuilder sb, String key, Object value) {
        sb.append(key)
                .append('=')
                .append(quoteIfNeeded(String.valueOf(value)));
    }

    private static String quoteIfNeeded(String s) {
        if (s == null) {
            return "\"\"";
        }

        boolean needsQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)
                    || ch == '"'
                    || ch == '='
                    || ch == ','
                    || ch == '['
                    || ch == ']'
                    || ch == '{'
                    || ch == '}') {
                needsQuotes = true;
                break;
            }
        }

        if (!needsQuotes) {
            return s;
        }

        String escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    @Override
    public void close() {
        // no-op
    }
}