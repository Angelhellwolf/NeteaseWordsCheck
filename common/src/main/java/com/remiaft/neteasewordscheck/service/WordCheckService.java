package com.remiaft.neteasewordscheck.service;

import com.remiaft.neteasewordscheck.cache.CacheEntry;
import com.remiaft.neteasewordscheck.cache.CacheProvider;
import com.remiaft.neteasewordscheck.http.NeteaseApiClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

public final class WordCheckService implements AutoCloseable {
    private final CacheProvider cacheProvider;
    private final NeteaseApiClient apiClient;
    private final Duration cacheTtl;
    private final ExecutorService executor;
    private final Logger logger;

    public WordCheckService(CacheProvider cacheProvider,
                            NeteaseApiClient apiClient,
                            Duration cacheTtl,
                            int workerThreads,
                            Logger logger) {
        this.cacheProvider = cacheProvider;
        this.apiClient = apiClient;
        this.cacheTtl = cacheTtl;
        this.logger = logger;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "netease-check-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerThreads), factory);
    }

    public CompletableFuture<CheckResult> checkAsync(String content) {
        String normalized = normalize(content);
        if (normalized.isEmpty()) {
            return CompletableFuture.completedFuture(CheckResult.allowed());
        }
        Optional<CacheEntry> cached = cacheProvider.get(normalized, cacheTtl);
        if (cached.isPresent()) {
            CacheEntry entry = cached.get();
            if (entry.getViolation() == null || entry.getViolation().isBlank()) {
                return CompletableFuture.completedFuture(CheckResult.allowed());
            }
            return CompletableFuture.completedFuture(CheckResult.denied(entry.getViolation()));
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> violation = apiClient.checkContent(normalized);
            violation.ifPresentOrElse(
                    details -> cacheProvider.put(normalized, details),
                    () -> cacheProvider.put(normalized, null)
            );
            return violation.map(CheckResult::denied).orElseGet(CheckResult::allowed);
        }, executor);
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.trim();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            cacheProvider.close();
        } catch (Exception exception) {
            logger.fine("Failed to close cache provider cleanly: " + exception.getMessage());
        }
    }
}
