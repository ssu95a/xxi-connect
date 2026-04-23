package ru.inversion.msrv.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

public final class FilePropertiesSource implements ConfigSource {

    private final Path path;
    private volatile Properties properties; // lazy loaded

    public FilePropertiesSource(Path path) { this.path = path; }

    /** */
    @Override
    public void close() {
        properties = null;
    }

    /** */
    @Override public String name() { return "file:" + path; }

    /** */
    @Override public String get(String key) {

        Properties p = properties;

        if( p == null )
        {
            synchronized (this) {
                p = properties;
                if( p == null )
                    properties = p = load();
            }
        }
        return p.getProperty(key);
    }

    @Override
    public void snapshotTo(Predicate<String> filter, Map<String, Object> snapshot) {

        Properties p = properties;
        if (p == null) {
            synchronized (this) {
                p = properties;
                if (p == null) {
                    properties = p = load();
                }
            }
        }

        for (String k : p.stringPropertyNames()) {
            if (k != null && filter.test(k)) {
                snapshot.put(k, p.getProperty(k));
            }
        }
    }

    /** */
    private Properties load() {

        Properties p = new Properties();

        if( path == null || !Files.exists(path) )
            return p;

        if( !Files.isRegularFile(path) ) {
            System.err.println("WARN: properties file too is not regular: " + path);
            return p;
        }

        try {

            if( Files.size(path) > 1024 * 1024) { // 1MB
                System.err.println("WARN: properties file too large: " + path);
                return p;
            }

            try( InputStream is = Files.newInputStream(path);
                 Reader r = new InputStreamReader(is, StandardCharsets.UTF_8))
            {
                p.load(r);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return p;
    }
}