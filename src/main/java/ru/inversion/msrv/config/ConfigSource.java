package ru.inversion.msrv.config;

import java.util.Map;
import java.util.function.Predicate;

/** */
public interface ConfigSource extends AutoCloseable {

    /** */
    String name();

    /** */
    Object get(String key); // null if absent

    /** */
    default void snapshotTo( Predicate<String> filter, Map<String,Object> snapshot ) { }

    /** */
    @Override
    default void close() {};
}
