package ru.inversion.msrv.config;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ProcessorCacheSource implements ConfigSource {

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "overrides";
    }

    @Override
    public String get(String key) {
        return map.get(key);
    }

    /** */
    public void set( String key, String value )
    {
        if( value == null )
            map.remove(key);
        else
            map.put(key, value);
    }

    public void unset(String key) {
        if( key != null )
            map.remove(key);
    }

    public Map<String, String> snapshot() {
        return new TreeMap<>(map);
    }

    @Override
    public void snapshotTo(Predicate<String> filter, Map<String, Object> snapshot) {
        for (Map.Entry<String, String> e : map.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();

            if (k != null && v != null && filter.test(k)) {
                snapshot.put(k, v);
            }
        }
    }
}