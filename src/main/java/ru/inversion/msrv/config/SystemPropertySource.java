package ru.inversion.msrv.config;

import ru.inversion.utils.S;

import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

public final class SystemPropertySource implements ConfigSource {

    private final String prefix;

    public SystemPropertySource(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String name() {
        return prefix == null ? "system-properties" : "system-properties(" + prefix + ")";
    }

    @Override
    public Object get(String key) {
        return System.getProperty(prefix == null ? key : prefix + key);
    }

    @Override
    public void snapshotTo(Predicate<String> filter, Map<String, Object> snapshot) {

        final Properties props = System.getProperties();

        for( String rawKey : props.stringPropertyNames() )
        {
            if( rawKey == null )
                continue;

            final String logicalKey;

            if(S.isNullOrEmpty(prefix) )
                logicalKey = rawKey;
            else
            {
                if( !rawKey.startsWith(prefix) )
                    continue;

                logicalKey = rawKey.substring( prefix.length() );
            }

            if( filter.test(logicalKey) )
                snapshot.put(logicalKey, props.getProperty(rawKey));
        }
    }
}