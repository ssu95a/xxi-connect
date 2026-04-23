package ru.inversion.msrv.config;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import ru.inversion.utils.Checks;

import java.util.regex.PatternSyntaxException;

import static org.slf4j.LoggerFactory.getLogger;
import static ru.inversion.msrv.config.PreparedObjectRegistry.ObjectType.PATTERN;

public final class PreparedObjectRegistry implements AutoCloseable {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    /** */
    enum ObjectType {
        PATTERN
    }

    /** */
    public record Key (ObjectType type, String configKey, int flags ) {
        public Key { Objects.requireNonNull( type, "'type' is null"); Objects.requireNonNull(configKey, "'configKey' is null"); }
    }

    /** */
    private record RegexpState(String matchPattern, Pattern compiled, String wrongPattern ) {
        public RegexpState { Objects.requireNonNull( matchPattern, "'matchPattern' is null"); Objects.requireNonNull( compiled, "'compiled' is null"); }
    }


    private final Config config;

    private final ConcurrentHashMap<Key, RegexpState> objects = new ConcurrentHashMap<>();

    /** */
    public PreparedObjectRegistry(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** */
    public Config config()
    {
        return config;
    }

    public Pattern regex(String configKey, String defaultRegex, int flags) {
        final Key cacheKey = new Key(ObjectType.PATTERN, configKey, flags);

        final String rawFromConfig = config.get(configKey, String.class, defaultRegex);
        final String normalized = normalizeRegex(rawFromConfig);

        final RegexpState state = objects.get(cacheKey);

        if (state != null)
        {
            if( state.wrongPattern() != null && state.wrongPattern().equals(normalized) ) {
                return state.compiled();
            }

            if(normalized.equals(state.matchPattern())) {
                return state.compiled();
            }
        }

        final Pattern compiled;
        try {
            compiled = Pattern.compile(normalized, flags);
        } catch (PatternSyntaxException e)
        {
            if (state != null)
            {
                final RegexpState rejectedState = new RegexpState( state.matchPattern(), state.compiled(), normalized);;

                objects.put(cacheKey, rejectedState);

                logger.warn(
                        "prepared.regex.reload.reject key={} regex={} reason={} fallback=previous",
                        configKey, normalized, e.toString()
                );

                return state.compiled();
            }

            final String safeDefault = normalizeRegex(defaultRegex);

            try {
                final Pattern defaultCompiled = Pattern.compile(safeDefault, flags);
                final RegexpState fallbackState = new RegexpState(safeDefault, defaultCompiled, normalized);
                objects.put(cacheKey, fallbackState);

                logger.warn(
                        "prepared.regex.init.reject key={} regex={} reason={} fallback=default",
                        configKey, normalized, e.toString()
                );

                return defaultCompiled;
            } catch (PatternSyntaxException e2) {
                throw new IllegalStateException(
                    "Invalid regex for key '" + configKey + "' and invalid defaultRegex: " + safeDefault, e2
                );
            }
        }

        final RegexpState next = objects.compute (
                cacheKey,
                (k, old) -> {
                    if (old != null && old.wrongPattern() != null && old.wrongPattern().equals(normalized))
                        return old;

                    return new RegexpState(normalized, compiled, null);
                }
        );

        return next.compiled();
    }

    public void invalidateRegex( String configKey, int flags ) {
        objects.remove(new Key( PATTERN, configKey, flags));
    }

    public void clear() {
        objects.clear();
    }

    public int size() {
        return objects.size();
    }

    /** */
    private static String normalizeRegex(String rawRegex) {
        final String s = Objects.toString( rawRegex, "" ).trim();
        return Checks.Require.text( s, "regex" );
    }

    @Override
    public void close()  {
        objects.clear();
    }
}