package com.remiaft.neteasewordscheck.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

public final class PluginConfiguration {

    public enum CacheMode {
        H2,
        MYSQL;

        public static CacheMode fromString(String value) {
            if (value == null || value.isBlank()) {
                return H2;
            }
            return CacheMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final CacheMode cacheMode;
    private final Duration cacheTtl;
    private final boolean blockOnViolation;
    private final MySqlSettings mySqlSettings;
    private final int workerThreads;

    private PluginConfiguration(CacheMode cacheMode,
                                Duration cacheTtl,
                                boolean blockOnViolation,
                                MySqlSettings mySqlSettings,
                                int workerThreads) {
        this.cacheMode = Objects.requireNonNull(cacheMode, "cacheMode");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl");
        this.blockOnViolation = blockOnViolation;
        this.mySqlSettings = Objects.requireNonNull(mySqlSettings, "mySqlSettings");
        this.workerThreads = workerThreads;
    }

    public static PluginConfiguration fromProperties(Properties properties) {
        CacheMode mode = CacheMode.fromString(properties.getProperty("cache.mode", "H2"));
        long ttlMinutes = parseLong(properties.getProperty("cache.ttl-minutes"), 60L);
        boolean block = Boolean.parseBoolean(properties.getProperty("chat.block-on-violation", "true"));
        int threads = (int) parseLong(properties.getProperty("worker-threads"), 4L);

        MySqlSettings settings = new MySqlSettings(
                properties.getProperty("mysql.host", "localhost"),
                (int) parseLong(properties.getProperty("mysql.port"), 3306L),
                properties.getProperty("mysql.database", "netease_words"),
                properties.getProperty("mysql.username", "root"),
                properties.getProperty("mysql.password", ""),
                Boolean.parseBoolean(properties.getProperty("mysql.use-ssl", "false"))
        );

        return new PluginConfiguration(mode, Duration.ofMinutes(ttlMinutes), block, settings, Math.max(1, threads));
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static PluginConfiguration defaults() {
        return new PluginConfiguration(CacheMode.H2, Duration.ofMinutes(60), true,
                new MySqlSettings("localhost", 3306, "netease_words", "root", "", false), 4);
    }

    public CacheMode getCacheMode() {
        return cacheMode;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public boolean isBlockOnViolation() {
        return blockOnViolation;
    }

    public MySqlSettings getMySqlSettings() {
        return mySqlSettings;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }
}
