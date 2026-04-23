package ru.inversion.msrv.config;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class EnvSource implements ConfigSource {

    private final String envPrefix; // e.g. "XXI_"

    public EnvSource(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    private String normalizeKey(String key) {
        final String k = key.toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_');

        if (envPrefix == null || envPrefix.isEmpty()) {
            return k;
        }

        return envPrefix + k;
    }

    private String toLogicalKey(String envKey) {
        String k = envKey;

        if (envPrefix != null && !envPrefix.isEmpty()) {
            if (!k.startsWith(envPrefix)) {
                return null;
            }
            k = k.substring(envPrefix.length());
        }

        if (k.isEmpty()) {
            return null;
        }

        return k.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    @Override
    public String name() {
        return "env" + envPrefix;
    }

    @Override
    public Object get(String key) {
        return System.getenv(normalizeKey(key));
    }

    @Override
    public void snapshotTo(Predicate<String> filter, Map<String, Object> snapshot) {
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            final String rawKey = e.getKey();
            final String value = e.getValue();

            if (rawKey == null || value == null) {
                continue;
            }

            final String logicalKey = toLogicalKey(rawKey);
            if (logicalKey != null && filter.test(logicalKey)) {
                snapshot.put(logicalKey, value);
            }
        }
    }
}