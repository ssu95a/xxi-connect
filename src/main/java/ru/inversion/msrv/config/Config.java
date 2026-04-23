package ru.inversion.msrv.config;

import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.converter.TypeConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

import static ru.inversion.msrv.config.Config.Namespace.*;

public final class Config implements Configuration, AutoCloseable {

    public enum Namespace {

        BOOT,
        SERVER,
        ALIAS,
        AUTH,
        ADMIN,
        METRICS,
        POOL,
        DS,
        TARGET;

        public String resolve(String key) {
            return name().toLowerCase(Locale.ROOT) + '.' + key;
        }
    }

    private final ProcessorCacheSource processorCacheSource;

    private final List<ConfigSource> sources;

    /** */
    private Config(List<ConfigSource> sources) {
        this.sources = List.copyOf(sources);
        processorCacheSource =
        this.sources.stream().filter(cs-> cs instanceof ProcessorCacheSource )
                .findFirst().map(cs->(ProcessorCacheSource)cs).orElseThrow(()->new IllegalStateException("Bad config list"));
    }

    /** */
    public ProcessorCacheSource processorCacheSource() {
        return processorCacheSource;
    }

    /** */
    public static String normalizeKey(String key)
    {
        final String k = Checks.Require.text( key, "Config key").strip();

        for( int i = 0; i < k.length(); i++ )
        {
            char ch = k.charAt(i);
            if( ch < 0x20 || ch == 0x7F || Character.isWhitespace(ch) )
                throw new IllegalArgumentException("Config key contains forbidden whitespace/control chars: " + key);
        }

        return k;
    }

    /** */
    public static Config make() {

        final String externalConfig = System.getProperty("xxi.properties.file");
        final Path configFile = S.isNullOrEmpty(externalConfig) ? null : Paths.get(externalConfig).toAbsolutePath().normalize();

        final List<ConfigSource> src = new ArrayList<>(
            Arrays.asList (
                new ProcessorCacheSource(),
                new SystemPropertySource("xxi."),
                new EnvSource("XXI_")
            )
        );

        if( configFile != null && Files.isRegularFile(configFile) )
            src.add(new FilePropertiesSource(configFile));


        final Map<String, Object> defaults = new LinkedHashMap<>();

        defaults.put( BOOT.resolve("http.port"), "8080" );
        defaults.put( BOOT.resolve("http.maxBodyBytes"), 65536 );

        defaults.put( BOOT.resolve("targetsXmlPath"), "targets.xml" );

        defaults.put( ALIAS.resolve("maxLength"), 256 );
        defaults.put( ALIAS.resolve("matchPattern"), "^[A-Za-z0-9_.-:/\\-]+$" );

        defaults.put( AUTH.resolve("tech.user"),     "NO_TECH_USER_LOGIN_SET" );
        defaults.put( AUTH.resolve("tech.password"), "NO_TECH_USER_PASSWORD_SET" );
        defaults.put( ADMIN.resolve("token"),         S.EMPTY_STRING );
        defaults.put( METRICS.resolve("enabled"),     false );

        src.add( new DefaultSource(defaults));

        return new Config(src);
    }

    @Override
    public String getString(String key, boolean required) {

        key = normalizeKey(key);

        for( ConfigSource s : sources ) {
            Object v = s.get(key);
            if (v != null)
                return v.toString();
        }

        if( required )
            throw new IllegalArgumentException("Missing config key: " + key);

        return null;
    }

    /** */
    @Override
    public <T> T get( String key, Class<T> typeClass, boolean require ) {

        key = normalizeKey(key);

        for( ConfigSource s : sources )
        {
            Object v = s.get(key);

            if (v != null) {
                try {
                    return TypeConverter.convert(v, typeClass);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to convert config value for key: " + key, e);
                }
            }
        }

        if( require )
            throw new IllegalArgumentException( "Missing config key: " + key );

        return null;
    }

    public List<ConfigSource> sources() {
        return sources;
    }

    @Override
    public Map<String, Object> snapshot( String pf )
    {
        final String prefixFor = S.isNullOrEmpty(pf) ? null :  normalizeKey(pf);

        final Predicate<String> filter =
            prefixFor == null
                ? k -> true
                : k -> k != null && k.startsWith(prefixFor);

        final Map<String, Object> snapshot = new HashMap<>();
        final ListIterator<ConfigSource> it = sources.listIterator(sources.size());

        while (it.hasPrevious()) {
            ConfigSource cs = it.previous();
            cs.snapshotTo(filter, snapshot);
        }

        return Map.copyOf(snapshot);
    }

    /** */
    @Override
    public void close() {
        sources.forEach(ConfigSource::close);
    }

    /** */
    public String serverInstanceId() {

        final String serverKey = SERVER.resolve("instanceId");

        String serverId = getString( serverKey, false);

        if( !S.isNullOrEmpty(serverId)) {
            return serverId;
        }

        final int port = get( BOOT.resolve("http.port"), Integer.class, 8080 );

        String host = S.firstNonBlank( System.getenv("HOSTNAME"), System.getenv("COMPUTERNAME") );

        if( host == null )
        {
            try {
                host = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                host = "unknown-node";
            }
        }

        serverId = host + ":" + port;

        processorCacheSource.set( serverKey, serverId );

        return serverId;
    }

}