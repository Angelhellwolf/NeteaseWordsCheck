package com.remiaft.neteasewordscheck.service;

import com.remiaft.neteasewordscheck.cache.CacheProvider;
import com.remiaft.neteasewordscheck.cache.H2ConnectionProvider;
import com.remiaft.neteasewordscheck.cache.MySqlConnectionProvider;
import com.remiaft.neteasewordscheck.cache.SqlCacheProvider;
import com.remiaft.neteasewordscheck.config.PluginConfiguration;
import com.remiaft.neteasewordscheck.http.NeteaseApiClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class NeteaseWordsCheckCore implements AutoCloseable {
    private final WordCheckService wordCheckService;

    public NeteaseWordsCheckCore(Path dataFolder,
                                 PluginConfiguration configuration,
                                 Logger logger) throws IOException {
        Files.createDirectories(dataFolder);
        CacheProvider cacheProvider = createCacheProvider(dataFolder, configuration, logger);
        cacheProvider.initialize();
        this.wordCheckService = new WordCheckService(cacheProvider,
                new NeteaseApiClient(logger),
                configuration.getCacheTtl(),
                configuration.getWorkerThreads(),
                logger);
    }

    private CacheProvider createCacheProvider(Path dataFolder,
                                               PluginConfiguration configuration,
                                               Logger logger) {
        switch (configuration.getCacheMode()) {
            case MYSQL:
                return new SqlCacheProvider(new MySqlConnectionProvider(configuration.getMySqlSettings()), logger);
            case H2:
            default:
                Path databaseFile = dataFolder.resolve("netease_words");
                return new SqlCacheProvider(new H2ConnectionProvider(databaseFile), logger);
        }
    }

    public WordCheckService getWordCheckService() {
        return wordCheckService;
    }

    @Override
    public void close() {
        wordCheckService.close();
    }
}
