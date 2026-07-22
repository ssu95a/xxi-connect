package ru.inversion.msrv;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import ru.inversion.msrv.config.Configuration;
import ru.inversion.msrv.tech_cred.TechCredentialsProvider;
import ru.inversion.utils.S;

import javax.sql.DataSource;
import java.lang.invoke.MethodHandles;
import java.net.PasswordAuthentication;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * PoolMan — оболочка над HikariDataSource для одного alias.
 */
public final class PoolMan implements Supplier<DataSource>, AutoCloseable {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private static final Object REQUIRED = new Object();

    private final AtomicReference<HikariDataSource> dataSourceRef = new AtomicReference<>();

    private final ReentrantLock poolLock = new ReentrantLock();

    private final Configuration config;
    private final TechCredentialsProvider techProvider;

    /** */
    private PoolMan( Configuration config, TechCredentialsProvider techProvider, HikariDataSource ds ) {
        this.config       = Objects.requireNonNull(config, "config");
        this.techProvider = Objects.requireNonNull(techProvider, "techProvider");
        this.dataSourceRef.set(Objects.requireNonNull(ds, "ds"));
    }

    @Override
    public DataSource get() {
        HikariDataSource ds = dataSourceRef.get();
        if( ds == null )
            throw new IllegalStateException("DataSource is closed");
        return ds;
    }

    public boolean isClosed() {
        return dataSourceRef.get() == null;
    }

    public String poolName( ) {
        HikariDataSource ds = dataSourceRef.get();
        return ds == null ? null : ds.getPoolName();
    }

    /**
     * Штатный reset:
     * просим Hikari мягко вытеснить соединения.
     * In-flight соединения могут дожить, новые будут пересозданы.
     */
    public void softReset() {

        final HikariDataSource hds = dataSourceRef.get();

        if( hds == null ) {
            logger.info("pool.soft_reset.skip reason=closed");
            return;
        }

        try {

            var mx = hds.getHikariPoolMXBean();

            if( mx != null )
            {
                logger.info("pool.soft_reset.start pool={}", hds.getPoolName());
                mx.softEvictConnections();
                logger.info("pool.soft_reset.ok pool={}", hds.getPoolName());
            }
        } catch (Throwable t) {
            logger.warn("pool.soft_reset.fail pool={} err={}", hds.getPoolName(), t.toString(), t);
        }
    }

    /**
     * Пересоздание datasource внутри текущего PoolMan:
     *  - создаём новый пул
     *  - swap на новый
     *  - мягко evict старые соединения
     *  - закрываем старый пул
     *
     * Подходит для применения новых pool.* / ds.* overrides.
     * Не перечитывает tlp snapshot.
     */
    public boolean hardReset() {

        final HikariDataSource fresh;
        try {
            fresh = createAndInitDataSource(config, techProvider);
        } catch (Throwable t) {
            logger.warn("pool.hard_reset.fail reason=create_fresh err={}", t.toString(), t);
            return false;
        }

        final HikariDataSource old;

        if (!poolLock.tryLock()) {
            try {
                fresh.close();
            } catch (Exception ignore) {
            }
            logger.info("pool.hard_reset.skip reason=busy");
            return false;
        }

        try {
            old = dataSourceRef.getAndSet(fresh);
            if (old == null) {
                try {
                    fresh.close();
                } catch (Exception ignore) {
                }
                logger.info("pool.hard_reset.skip reason=closed");
                return false;
            }
        } finally {
            poolLock.unlock();
        }

        try {
            logger.info("pool.hard_reset.start oldPool={} newPool={}", old.getPoolName(), fresh.getPoolName());

            var mx = old.getHikariPoolMXBean();
            if (mx != null) {
                mx.softEvictConnections();
            }

            old.close();

            logger.info("pool.hard_reset.ok newPool={}", fresh.getPoolName());
            return true;

        } catch (Throwable t) {
            logger.warn("pool.hard_reset.fail newPool={} err={}", fresh.getPoolName(), t.toString(), t);
            return false;
        }
    }

    /**
     * Аварийное пересоздание datasource:
     *  - создаём новый пул
     *  - swap на новый
     *  - старый закрываем немедленно
     *
     * Может оборвать in-flight запросы.
     */
    public boolean hardResetEmergency() {
        final HikariDataSource fresh;
        try {
            fresh = createAndInitDataSource(config, techProvider);
        } catch (Throwable t) {
            logger.warn("pool.hard_reset_emergency.fail reason=create_fresh err={}", t.toString(), t);
            return false;
        }

        final HikariDataSource old;

        if (!poolLock.tryLock()) {
            try {
                fresh.close();
            } catch (Exception ignore) {
            }
            logger.info("pool.hard_reset_emergency.skip reason=busy");
            return false;
        }

        try {
            old = dataSourceRef.getAndSet(fresh);
            if (old == null) {
                try {
                    fresh.close();
                } catch (Exception ignore) {
                }
                logger.info("pool.hard_reset_emergency.skip reason=closed");
                return false;
            }
        } finally {
            poolLock.unlock();
        }

        try {
            logger.info("pool.hard_reset_emergency.start oldPool={} newPool={}", old.getPoolName(), fresh.getPoolName());
            old.close();
            logger.info("pool.hard_reset_emergency.ok newPool={}", fresh.getPoolName());
            return true;
        } catch (Throwable t) {
            logger.warn("pool.hard_reset_emergency.fail newPool={} err={}", fresh.getPoolName(), t.toString(), t);
            return false;
        }
    }

    @Override
    public void close( ) {
        poolLock.lock();
        try {

            HikariDataSource ds = dataSourceRef.getAndSet(null);

            if( ds != null )
            {
                try {
                    ds.close();
                } catch (Exception ignore) { }
            }

        } finally {
            poolLock.unlock();
        }
    }

    /** Снимок базовых метрик пула. */
    public Map<String, Object> fillMetrics() {

        HikariDataSource h = dataSourceRef.get();

        if( h == null )
            return Map.of("type", "none");

        var mx = h.getHikariPoolMXBean();
        if( mx == null ) {
            return Map.of(
                    "type", "hikari",
                    "pool", h.getPoolName()
            );
        }

        return Map.of (
                "type", "hikari",
                "pool",     h.getPoolName(),
                "active",   mx.getActiveConnections(),
                "idle",     mx.getIdleConnections(),
                "total",    mx.getTotalConnections(),
                "awaiting", mx.getThreadsAwaitingConnection(),
                "maxPool",  h.getMaximumPoolSize(),
                "minIdle",  h.getMinimumIdle()
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Configuration config, String name, Class<T> type, Object defValue) {
        boolean required = (defValue == REQUIRED);
        T t = config.get(name, type, required);
        return t == null ? (T) defValue : t;
    }

    /** Применяем ds.* как dataSourceProperties (ds.serverName -> serverName и т.п.) */
    private static void applyDataSourceProperties( HikariConfig hc, Configuration configuration)
    {
        // JDBC driver defaults
        final VendorDbEnum vendor = VendorDbEnum.fromJdbcUrl( hc.getJdbcUrl() );
        vendor.initializer().initialize(hc);

        Map<String, Object> p = configuration.snapshot("ds.");
        if( p == null || p.isEmpty() )
            return;

        for( var e : p.entrySet() )
        {
            String k = e.getKey();
            Object v = e.getValue();

            if( k == null || v == null )
                continue;

            if( !k.startsWith("ds.") )
                continue;

            hc.addDataSourceProperty( k.substring(3), String.valueOf(v) );
        }
    }

    /** */
    private static HikariDataSource createAndInitDataSource( Configuration config, TechCredentialsProvider techProvider )
    {
        final PasswordAuthentication techAuth = techProvider.get();
        final HikariConfig hc = new HikariConfig();

        hc.setJdbcUrl ( value(config, "pool.jdbcUrl", String.class, REQUIRED) );

        hc.setUsername( techAuth.getUserName() );
        hc.setPassword( String.valueOf( techAuth.getPassword() ) );

        hc.setPoolName( value( config, "pool.name", String.class, "xxi-connect-pool") );

        hc.setMaximumPoolSize(value(config, "pool.maximumPoolSize", Integer.class, 50));
        hc.setMinimumIdle    (value(config, "pool.minimumIdle", Integer.class, 5));
        hc.setConnectionTimeout(value(config, "pool.connectionTimeout", Long.class, 10_000L));
        hc.setValidationTimeout(value(config, "pool.validationTimeout", Long.class, 3_000L));
        hc.setIdleTimeout(value(config, "pool.idleTimeout", Long.class, 60_000L));
        hc.setMaxLifetime(value(config, "pool.maxLifetime", Long.class, 30 * 60_000L));

        Long leak = value(config, "pool.leakDetectionThreshold", Long.class, 0L);
        if( leak != null && leak > 0 )
            hc.setLeakDetectionThreshold(leak);

        String testQuery = value(config, "pool.connectionTestQuery", String.class, S.EMPTY_STRING );
        if(!S.isNullOrEmpty(testQuery) )
            hc.setConnectionTestQuery(testQuery);

        applyDataSourceProperties( hc, config);

        return new HikariDataSource(hc);
    }

    public static PoolMan createAndInit(Configuration config, TechCredentialsProvider techProvider) {
        HikariDataSource ds = createAndInitDataSource( config, techProvider );
        return new PoolMan(config, techProvider, ds);
    }
}