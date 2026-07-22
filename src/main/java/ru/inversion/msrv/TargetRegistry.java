package ru.inversion.msrv;

import org.slf4j.Logger;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.config.TargetConfig;
import ru.inversion.msrv.tech_cred.TechCredentialsProvider;
import ru.inversion.msrv.validation.Errors;
import ru.inversion.msrv.validation.UnknownAliasException;
import ru.inversion.utils.Holder;
import ru.inversion.utils.S;
import ru.inversion.utils.U;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.LoggerFactory.getLogger;
import static ru.inversion.msrv.config.Config.Namespace.BOOT;
import static ru.inversion.msrv.validation.Errors.ErrorCode.REQUEST_TARGET_ALIAS_INVALID;

/**
 * <h5>TargetRegistry</h5>
 * <p>
 *  - targets.xml читается 1 раз на старте
 *  - alias -> TargetItem
 *  - xxi_tlp грузится lazy per alias и кэшируется до рестарта
 *  - pool создаётся lazy per alias и живёт до рестарта
 */
public final class TargetRegistry implements AutoCloseable {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private final Config config;
    private final TechCredentialsProvider techAuthenticator;

    /** */
    private final Map<String, TargetItem> targets;
    private final Set<String> disabledAliases = new HashSet<>();

    /** */
    private final ConcurrentHashMap<String, Map<String, Object>> tlpCache = new ConcurrentHashMap<>();

    /**  */
    private final ConcurrentHashMap<String, PoolMan> pools = new ConcurrentHashMap<>();

    /** */
    private final String smonAlias;

    /**
     * Конструктор сразу грузит targets.xml.
     * <p>
     * Любая ошибка конфигурации валит старт сервиса.
     */
    public TargetRegistry( Config config, TechCredentialsProvider techAuthenticator )
    {
        this.techAuthenticator = Objects.requireNonNull( techAuthenticator, "authenticator" );
        this.config            = Objects.requireNonNull( config, "config" );

        final Holder<String> sa= new Holder<>();
        this.targets           = loadTargetsFromXml( this.config, sa, disabledAliases );
        this.smonAlias         = sa.isPresent() ? sa.get() : null;

        DriverManager.setLoginTimeout( config.get(BOOT.resolve("jdbc.loginTimeoutSec"), Integer.class, 5) );

        logger.info( "targets.startup. ok targets={}", this.targets.size() );
    }

    /** Признак готовности для /state/ready. */
    public boolean isReady() {
        return !targets.isEmpty();
    }

    /* SMON zone*/
    public boolean hasSmonTarget() {
        return !S.isNullOrEmpty(smonAlias);
    }

    public String smonAlias() {
        return smonAlias;
    }

    public Optional<TargetItem> smonTarget() {
        return hasSmonTarget() ? Optional.ofNullable(targets.get(smonAlias)) : Optional.empty();
    }

    public Optional<TargetContext> smonContext() {
        return smonTarget().map(t -> resolve(t.nrmAlias()));
    }

    /**
     * Hard reset
     * удаляем текущий pool instance
     * с актуальным Config / ProcessorCacheSource / TargetConfig.
     */
    public boolean hardResetPool(String alias) {

        final TargetItem t = target(alias);
        final String     a = t.nrmAlias( );

        final PoolMan old = pools.remove(a);

        if( old == null ) {
            logger.info("pool.hard_reset.skip alias={} reason=no_pool", a);
            return false;
        }

        logger.info("pool.hard_reset.start alias={}", a);
        closeQuietly(old);
        logger.info("pool.hard_reset.ok alias={}", a);

        return true;
    }

    /** */
    public boolean softResetPool(String alias) {
        final TargetItem t = target(alias);
        final PoolMan pm = pools.get(t.nrmAlias());
        if (pm == null) {
            logger.info("pool.soft_reset.skip alias={} reason=no_pool", t.nrmAlias() );
            return false;
        }
        pm.softReset();
        return true;
    }

    /** Текстовый статус для /state. */
    public String statusInfo() {
        return "ready=" + !targets.isEmpty() + " targets=" + targets.size() + " pools=" + pools.size() + " tlpCache=" + tlpCache.size();
    }

    /** Список alias-ов, отсортированный */
    public List<String> aliases( ) {
        List<String> out = new ArrayList<>( targets.keySet());
        out.sort(String::compareTo);
        return out;
    }

    /** Получить target по alias. Alias нормализуется. */
    public TargetItem target( String rawAlias )
    {
        final String nrmAlias = TargetItem.normalizeAlias(rawAlias);

        if( nrmAlias == null )
            throw Errors.of( REQUEST_TARGET_ALIAS_INVALID, "Empty target alias");

        final TargetItem t = targets.get( nrmAlias );
        if( t != null )
            return t;

        if( disabledAliases.contains(nrmAlias) )
            throw Errors.of(Errors.ErrorCode.TARGET_ALIAS_DISABLED);

        throw new UnknownAliasException( rawAlias, nrmAlias );
    }

    /** */
    private PasswordAuthentication credentialsFor( TargetItem target )
    {
        final PasswordAuthentication targetAuth = target.auth();

        if( targetAuth == null )
            return techAuthenticator.get();

        final String targetUser = targetAuth.getUserName();
        final char[] targetPassword = targetAuth.getPassword();

        final boolean hasUser = !S.isNullOrEmpty(targetUser);

        final boolean hasPassword = targetPassword.length != 0;

        if( hasUser && hasPassword )
            return targetAuth;

        final PasswordAuthentication techAuth = techAuthenticator.get();

        return new PasswordAuthentication( hasUser ? targetUser : techAuth.getUserName(), hasPassword ? targetPassword : techAuth.getPassword() );
    }
    /**
     * TargetContext.
     */
    public TargetContext resolve( String alias )
    {
        final TargetItem t = target(alias);

        final PoolMan pm = pools.computeIfAbsent( t.nrmAlias(), a -> {
            final Map<String, Object> tlp = parametersFor(t);
            final TargetConfig targetConfig = new TargetConfig( t, config, tlp );

            logger.info("pool.create.start alias={} vendor={}", t.nrmAlias(), t.vendorDb());
            PoolMan created = PoolMan.createAndInit( targetConfig, () -> credentialsFor(t) );
            logger.info("pool.create.ok alias={} vendor={}", t.nrmAlias(), t.vendorDb());

            return created;
        });

        return new TargetContext(t, pm);
    }

    /** Метрики конкретного пула, по алиасу пула */
    public Map<String, Object> poolMetrics( String alias ) {

        final TargetItem t = target(alias);
        final PoolMan   pm = pools.get(t.nrmAlias());

        if( pm == null )
            return Map.of( "alias", t.nrmAlias(),"poolPresent", false );

        return pm.fillMetrics( );
    }

    /** Метрики всех уже созданных pool-ов. */
    public Map<String, Map<String, Object>> poolMetricsAll() {

        final Map<String, Map<String, Object>> out = new TreeMap<>();

        for( Map.Entry<String, PoolMan> e : pools.entrySet() ) {
             out.put(e.getKey(), e.getValue().fillMetrics());
        }

        return Map.copyOf(out);
    }

    /**
     * Закрыть все пулы.
     */
    @Override
    public void close()
    {
        for( PoolMan pm : pools.values() ) {
             closeQuietly(pm);
        }

        pools.clear();
        tlpCache.clear();
    }

    /** */
    private Map<String, Object> parametersFor(TargetItem ti) {
        return tlpCache.computeIfAbsent( ti.nrmAlias(), __ -> loadDbValues(ti) );
    }

    /**
     * Чтение targets.xml один раз на старте.
     */
    private static Map<String, TargetItem> loadTargetsFromXml(Config config, Holder<String> smonAlias, Set<String> disabledAliases)
    {
        final String targetsXmlPath = config.getString( BOOT.resolve("targetsXmlPath"), true );

        if( targetsXmlPath == null || targetsXmlPath.trim().isEmpty() )
            throw new IllegalStateException( "boot.targetsXmlPath is empty");

        final File xmlFile = new File(targetsXmlPath);
        if( !xmlFile.isFile() )
            throw new IllegalStateException(
                    "targets.xml not found: " + xmlFile.getAbsolutePath() +
                            ". Set explicit absolute path via '" + BOOT.resolve("targetsXmlPath") + "'"
            );

        final IDco dco = Dco.parseXml(xmlFile);
        final Map<String, TargetItem> newMap = new LinkedHashMap<>();

        for( IDco dc : dco.select("//target" ) )
        {
            final String aliasRaw = safeTrim( dc.a("alias").value() );

            if( dc.hasAttribute("enabled") && !dc.a("enabled").value(Boolean.class) ) {
                disabledAliases.add( TargetItem.normalizeAlias(aliasRaw) );
                continue;
            }

            final String jdbcUrl      = safeTrim( dc.e("jdbcUrl").value() );
            final VendorDbEnum vendor = VendorDbEnum.of(dc.a("vendor").value());

            PasswordAuthentication pa = null;

            {
                final String user     = safeTrim( (String)dc.getIfPresent("user") );
                final String password = (String)dc.getIfPresent("password");

                if( user != null || password != null )
                    pa = new PasswordAuthentication( user, password == null ? new char[0] : password.toCharArray() );

                dc.removeItem( "user", "password" );
            }

            final TargetItem ti = new TargetItem(aliasRaw, jdbcUrl, vendor, pa );

            validateTargetItem(ti);

            if( newMap.putIfAbsent(ti.nrmAlias(), ti) != null )
                logger.warn( "Duplicate target alias in targets.xml: {}. Duplicate skipped", ti.rawAlias() );
                //throw new IllegalArgumentException("Duplicate target alias in targets.xml: " + ti.rawAlias() );
        }

        if( newMap.isEmpty() )
            throw new IllegalStateException( "targets.xml contains zero targets" );

        final String smon = dco.single("/targets/@smon_alias").map(d -> safeTrim(d.value(String.class))).orElse(null);

        if( !S.isNullOrEmpty(smon) )
        {
            final String smonNrm = TargetItem.normalizeAlias(smon);

            if( smonNrm == null || !newMap.containsKey(smonNrm) )
                logger.warn( "targets.xml: smon_alias='{}' points to unknown or disabled target; SMON and DB metrics will be disabled for this server instance", smon );
            else
            {
                smonAlias.set(smonNrm);
                logger.info("targets.xml: smon enabled via alias={}", smonNrm);
            }
        }
        else {
            logger.info("targets.xml: 'smon_alias' is not set; SMON and DB metrics are disabled");
        }

        return Map.copyOf(newMap);
    }

    /**
     * Валидация target-а на старте.
     */
    private static void validateTargetItem(TargetItem ti) {

        if( ti == null )
            throw new IllegalArgumentException("targets.xml: null target");

        if( ti.nrmAlias() == null)
            throw new IllegalArgumentException("targets.xml: empty alias");

        if( S.isNullOrEmpty( ti.jdbcUrl() ) )
            throw new IllegalArgumentException("targets.xml: empty jdbcUrl for alias=" + ti.rawAlias());

        if( ti.vendorDb() == null )
            throw new IllegalArgumentException("targets.xml: empty vendor for alias=" + ti.rawAlias());

        final VendorDbEnum vendorDb = VendorDbEnum.fromJdbcUrl( ti.jdbcUrl() );

        if( vendorDb != ti.vendorDb() )
            throw new IllegalArgumentException( "targets.xml: vendor mismatch for alias=" + ti.rawAlias() + " declared=" + ti.vendorDb() + " detected=" + vendorDb);
    }

    /**
     * Чтение runtime params из xxi_tlp через tech user!!! минуя пул !
     */
    private Map<String, Object> loadDbValues( TargetItem target )
    {
        final PasswordAuthentication pa = credentialsFor( target );
        final String user               = pa.getUserName();

        final Map<String, Object> out = new HashMap<>();

        try (

            Connection c = DriverManager.getConnection( target.jdbcUrl(), user, String.valueOf( pa.getPassword() ) );
            PreparedStatement ps = c.prepareStatement("SELECT ns, name, value FROM xxi_tlp");
            ResultSet rs = ps.executeQuery()
        )
        {
            while( rs.next() )
            {
                String ns    = rs.getString(1);
                String name  = rs.getString(2);
                String value = rs.getString(3);

                if( name == null )
                    continue;

                ns   = safeTrim(ns);
                name = safeTrim(name);

                if( S.isNullOrEmpty(name) )
                    continue;

                if( S.isNullOrEmpty(ns) ) {
                    logger.warn("xxi_tlp.skip alias={} reason=empty_ns name={}", target.nrmAlias(), name);
                    continue;
                }

                final String fullKey = ns + "." + name;
                if( out.containsKey(fullKey) )
                    logger.warn("xxi_tlp.duplicate.key alias={} key={}", target.nrmAlias(), fullKey);

                out.put(fullKey, value);            }

                logger.info("tlp.load.ok alias={} entries={}", target.nrmAlias(), out.size() );

                return Map.copyOf(out);

            } catch (SQLException e) {

            /*
             * xxi_tlp может отсутствовать, Для vendorDB проверяем код/SQLState "table not found".
             */

            if( isTableTLPMissing(e, target.vendorDb() ) ) {
                logger.info("tlp.load.skip alias={} reason=table_not_found", target.nrmAlias());
                return Map.of();
            }

            throw Errors.unavailable( e, "Target config DB unavailable" );
        }
    }

    private static boolean isTableTLPMissing(SQLException e, VendorDbEnum vendor )
    {
        if( e == null || vendor == null )
            return false;

        return switch (vendor) {
            case POSTGRES -> "42P01".equals(e.getSQLState()); // undefined_table
            case ORACLE   -> e.getErrorCode() == 942;         // ORA-00942 table or view does not exist
            default -> false;
        };
    }

    /* */
    private static void closeQuietly(PoolMan pm) {

        try {
            U.callIfNotNull( PoolMan::close, pm );
        } catch (Throwable t) {
            logger.warn("pool.close.fail err={}", t.getMessage(), t);
        }
    }

    private static String safeTrim( String s ) {
        return (s == null) ? null : s.trim();
    }
}