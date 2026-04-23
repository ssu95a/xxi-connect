package ru.inversion.msrv.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class DefaultSource implements ConfigSource {

    private final Map<String, Object> defaults;

    public DefaultSource(Map<String, Object> defaults) {
        this.defaults = defaults == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
    }

    @Override
    public String name() {
        return "defaults";
    }

    @Override
    public Object get(String key) {
        return defaults.get(key);
    }

    @Override
    public void snapshotTo(Predicate<String> filter, Map<String, Object> snapshot) {
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();

            if (k != null && v != null && filter.test(k)) {
                snapshot.put(k, v);
            }
        }
    }
}