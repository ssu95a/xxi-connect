package ru.inversion.msrv;

import ru.inversion.msrv.config.TargetConfig;

import javax.crypto.spec.PBEKeySpec;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TargetContext — контекст одного alias + pool
 */
public final class TargetContext {

    private final TargetItem target;
    private final PoolMan    pool;

    /** */
    TargetContext(TargetItem target, PoolMan pool) {
        this.target = Objects.requireNonNull( target, "target");
        this.pool   = Objects.requireNonNull( pool,   "pool"  );
    }

    /** */
    public TargetItem targetItem() { return target; }

    /** */
    public String alias() { return target.nrmAlias(); }

    /** */
    public String jdbcUrl() { return target.jdbcUrl(); }

    /** */
    public VendorDbEnum vendorDb() { return target.vendorDb(); }

    /** */
    public DataSource dataSource() { return pool.get(); }

    /** */
    public PoolMan pool( ) { return pool; }

    /** */
    //public void hardResetPool() { pool.hardReset(); }

    /*
    public Map<String, Object> properties( ) {

        Map<String, Object> p = properties;
        if (p != null)
            return p;

        synchronized (this)
        {
            p = properties;
            if (p == null)
                properties = p = Map.copyOf( loader.apply( jdbcUrl() ) );
            return p;
        }
    }
    */

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws Exception;
    }
    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection c) throws Exception;
    }

    /** */
    public <T> T withConnection( SqlFunction<T> fn) {
        Objects.requireNonNull(fn, "fn");
        try (Connection c = pool.get().getConnection()) {
            return fn.apply(c);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException("DB operation failed for alias=" + target.rawAlias(), ex);
        }
    }

    public void withConnection(SqlConsumer fn) {
        Objects.requireNonNull(fn, "fn");
        try (Connection c = pool.get().getConnection()) {
            fn.accept(c);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException("DB operation failed for alias=" + target.rawAlias(), ex);
        }
    }

}
