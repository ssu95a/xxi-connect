package ru.inversion.msrv.config;

import ru.inversion.msrv.TargetItem;
import ru.inversion.utils.converter.TypeConverter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.lang.String.join;

/** Параметры конкретного target */
public final class TargetConfig implements Configuration {

    private final TargetItem targetItem;
    private final Map<String, Object> tlpProperties;
    private final Config config;

    public TargetConfig(TargetItem targetItem, Config config, Map<String, Object> tlpProperties) {
        this.targetItem    = Objects.requireNonNull(targetItem, "targetItem");
        this.config        = Objects.requireNonNull(config, "config");
        this.tlpProperties = Map.copyOf(Objects.requireNonNull(tlpProperties, "tlpProperties"));
    }

    @Override
    public String getString(String key, boolean required) {

        key = Config.normalizeKey(key);

        String direct = directString(key);
        if( direct != null )
            return direct;

        Object value = tlpProperties.get(key);
        if( value != null )
            return String.valueOf(value);

        return config.getString( key, required );
    }

    @Override
    public <T> T get(String key, Class<T> typeClass, boolean require) {
        key = Config.normalizeKey(key);

        Object direct = directValue(key);
        if (direct != null) {
            return convert(direct, typeClass, key);
        }

        Object value = tlpProperties.get(key);
        if (value != null) {
            return convert(value, typeClass, key);
        }

        return config.get(key, typeClass, require);
    }

    /** */
    @Override
    public Map<String, Object> snapshot(String prefixFor)
    {
        prefixFor = Config.normalizeKey( prefixFor );

        final Map<String, Object> out = new LinkedHashMap<>( config.snapshot(prefixFor) );

        for( Map.Entry<String, Object> e : tlpProperties.entrySet())
        {
            String k = e.getKey();
            Object v = e.getValue();

            if (k == null || v == null)
                continue;

            if( k.startsWith(prefixFor) )
                out.put(k, v);
        }

        if( "pool.".startsWith(prefixFor) || prefixFor.startsWith("pool.") ) {
            out.put("pool.jdbcUrl", targetItem.jdbcUrl());
            out.put("pool.name", defaultPoolName());
        }

        if( "target.".startsWith(prefixFor) || prefixFor.startsWith("target."))
            out.put("target.alias", targetItem.nrmAlias());

        return Map.copyOf(out);
    }

    private String directString(String key) {
        Object v = directValue(key);
        return v == null ? null : String.valueOf(v);
    }

    private Object directValue(String key) {
        return switch (key) {
            case "pool.jdbcUrl" -> targetItem.jdbcUrl();
            case "target.alias" -> targetItem.nrmAlias();
            case "target.vendor"-> targetItem.vendorDb();
            case "pool.name"    -> defaultPoolName();
            default -> null;
        };
    }

    /** */
    private String defaultPoolName( ) {
        return join("-","xxi", "connect", targetItem.nrmAlias().toLowerCase(Locale.ROOT), targetItem.vendorDb().postfix() );
    }

    private static <T> T convert( Object value, Class<T> typeClass, String key )
    {
        try {
            return TypeConverter.convert(value, typeClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert target config value for key: " + key, e );
        }
    }
}