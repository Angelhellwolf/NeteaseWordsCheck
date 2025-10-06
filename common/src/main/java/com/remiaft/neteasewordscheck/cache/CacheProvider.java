package com.remiaft.neteasewordscheck.cache;

import java.io.Closeable;
import java.time.Duration;
import java.util.Optional;

public interface CacheProvider extends Closeable {

    void initialize();

    Optional<CacheEntry> get(String content, Duration ttl);

    void put(String content, String violation);

    @Override
    void close();
}
