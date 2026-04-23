package ru.inversion.msrv.metrics;

import jakarta.servlet.http.HttpServletRequest;
import ru.inversion.utils.S;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Metrics {

    private Metrics() {
    }

    public static final String REQUEST_ATTR = Metrics.class.getName() + ".context";
    public static final String REQUEST_ENABLED_ATTR = Metrics.class.getName() + ".enabled";

    public enum Key {

        SERVER_INSTANCE_ID(
                "server.instance_id",
                "Идентификатор экземпляра сервера",
                "server_instance_id"
        ),
        CLIENT_NET_IP_ORIG(
                "client.net.ip_orig",
                "Исходный IP клиента",
                "client_net_ip_orig"
        ),
        CLIENT_NET_TRUSTED_PROXY(
                "client.net.trusted_proxy",
                "Trusted proxy применён",
                "client_net_trusted_proxy"
        ),
        REQ_ID(
                "req.id",
                "Request ID",
                "req_id"
        ),
        REQ_URI(
                "req.uri",
                "URI запроса",
                "req_uri"
        ),
        REQ_TOTAL_MS(
                "req.total_ms",
                "Полное время запроса",
                "req_total_ms"
        ),

        RESP_HTTP_STATUS(
                "resp.http_status",
                "HTTP status ответа",
                "resp_http_status"
        ),

        AUTH_ALIAS(
                "auth.alias",
                "Alias target DB",
                "auth_alias"
        ),
        AUTH_TARGET_VENDOR(
                "auth.target_vendor",
                "Vendor target DB",
                "auth_target_vendor"
        ),
        AUTH_MS_ACQUIRE(
                "auth.ms_acquire",
                "Время взятия connection",
                "auth_ms_acquire"
        ),
        AUTH_MS_DB(
                "auth.ms_db",
                "Время DB-auth",
                "auth_ms_db"
        ),
        AUTH_MS_ENCRYPT(
                "auth.ms_encrypt",
                "Время шифрования",
                "auth_ms_encrypt"
        ),
        RESULT_OUTCOME(
                "result.outcome",
                "Итог запроса",
                "result_outcome"
        ),
        RESULT_ERROR_CODE(
                "result.error_code",
                "Код ошибки",
                "result_error_code"
        ),
        RESULT_CLASS(
                "result.class",
                "Класс результата",
                "result_class"
        ),

        EX_CLASS(
                "ex.class",
                "Класс исключения",
                "ex_class"
        ),

        POOL_ACTIVE(
                "pool.active",
                "Активные соединения",
                "pool_active"
        ),
        POOL_IDLE(
                "pool.idle",
                "Свободные соединения",
                "pool_idle"
        ),
        POOL_TOTAL(
                "pool.total",
                "Всего соединений",
                "pool_total"
        ),
        POOL_AWAITING(
                "pool.awaiting",
                "Ожидающие потоки",
                "pool_awaiting"
        ),

        METRICS_COMPONENT_COUNT(
                "metrics.component_count",
                "Количество component metrics",
                "metrics_component_count"
        );

        private final String path;
        private final String description;
        private final String logKey;

        Key(String path, String description, String logKey) {
            this.path = path;
            this.description = description;
            this.logKey = logKey;
        }

        public String path() {
            return path;
        }

        public String description() {
            return description;
        }

        public String logKey() {
            return logKey;
        }
    }

    public static final class ComponentMetric {
        private final String namespace;
        private final String component;
        private final String metricType;
        private final long elapsedMs;
        private final String outcome;
        private final String errorClass;

        public ComponentMetric(String namespace,
                               String component,
                               String metricType,
                               long elapsedMs,
                               String outcome,
                               String errorClass) {
            this.namespace = namespace;
            this.component = component;
            this.metricType = metricType;
            this.elapsedMs = elapsedMs;
            this.outcome = outcome;
            this.errorClass = errorClass;
        }

        public String namespace() {
            return namespace;
        }

        public String component() {
            return component;
        }

        public String metricType() {
            return metricType;
        }

        public long elapsedMs() {
            return elapsedMs;
        }

        public String outcome() {
            return outcome;
        }

        public String errorClass() {
            return errorClass;
        }
    }

    public static final class Context {
        private final boolean enabled;
        private final long startedNs;
        private final EnumMap<Key, Object> values;
        private final List<ComponentMetric> componentMetrics;

        private long totalMs = -1L;
        private boolean finished;

        public Context(boolean enabled) {
            this.enabled = enabled;
            this.startedNs = enabled ? System.nanoTime() : 0L;
            this.values = enabled ? new EnumMap<>(Key.class) : null;
            this.componentMetrics = enabled ? new ArrayList<>() : null;
        }

        public boolean enabled() {
            return enabled;
        }

        public void put(Key key, Object value) {
            if (!enabled || key == null || value == null) {
                return;
            }
            values.put(key, value);
        }

        public Object get(Key key) {
            if (!enabled) {
                return null;
            }
            return values.get(key);
        }

        /** */
        public Map<Key, Object> values() {
            if(!enabled )
                return Collections.emptyMap();

            return Collections.unmodifiableMap(values);
        }

        /** */
        public List<ComponentMetric> componentMetrics() {
            if (!enabled)
                return Collections.emptyList();

            return Collections.unmodifiableList(componentMetrics);
        }

        /** */
        public void addComponentMetric(ComponentMetric metric) {
            if (!enabled || metric == null)
                return;

            componentMetrics.add(metric);
        }

        public long totalMs() {
            return totalMs;
        }

        public void finish() {
            if( !enabled || finished)
                 return;

            totalMs = (System.nanoTime() - startedNs) / 1_000_000L;
            values.put( Key.REQ_TOTAL_MS, totalMs );
            values.put( Key.METRICS_COMPONENT_COUNT, componentMetrics.size() );

            finished = true;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Context context;
        private final String namespace;
        private final String component;
        private final String metricType;
        private final long startedNs;
        private final boolean enabled;

        private String outcome = "OK";
        private String errorClass;
        private boolean closed;

        Scope(Context context, String namespace, String component, String metricType) {
            this.context = context;
            this.enabled = context != null && context.enabled();
            this.namespace = namespace;
            this.component = component;
            this.metricType = metricType;
            this.startedNs = enabled ? System.nanoTime() : 0L;
        }

        public Context context() {
            return context;
        }

        public void fail(Throwable th) {
            if (!enabled) {
                return;
            }

            outcome = "FAIL";
            if (th != null) {
                errorClass = th.getClass().getSimpleName();
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            if (!enabled) {
                return;
            }

            final long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L;
            context.addComponentMetric(
                    new ComponentMetric(
                            namespace,
                            component,
                            metricType,
                            elapsedMs,
                            outcome,
                            errorClass
                    )
            );
        }
    }

    public static final class Tools {
        private static final Context NOOP_CONTEXT = new Context(false);

        private Tools() {
        }

        public static boolean isEnabled(HttpServletRequest req) {
            return Boolean.TRUE.equals(req.getAttribute(REQUEST_ENABLED_ATTR));
        }

        public static Context get(HttpServletRequest req) {
            Object v = req.getAttribute(REQUEST_ATTR);
            return (v instanceof Context c) ? c : null;
        }

        public static Context getOrCreate(HttpServletRequest req) {

            if( !isEnabled(req) )
                return NOOP_CONTEXT;

            Context c = get(req);

            if( c != null )
                return c;


            c = new Context(true);
            req.setAttribute(REQUEST_ATTR, c);
            return c;
        }

        public static void put(HttpServletRequest req, Key key, Object value) {
            if (!isEnabled(req)) {
                return;
            }
            getOrCreate(req).put(key, value);
        }

        public static Scope openSpan(HttpServletRequest req,
                                     String namespace,
                                     String component,
                                     String metricType) {
            if (!isEnabled(req)) {
                return new Scope(NOOP_CONTEXT, "", "", "");
            }

            return new Scope(
                    getOrCreate(req),
                    safe(namespace),
                    safe(component),
                    safe(metricType)
            );
        }

        private static String safe(String s) {
            return Objects.requireNonNullElse( s, S.EMPTY_STRING );
        }
    }
}