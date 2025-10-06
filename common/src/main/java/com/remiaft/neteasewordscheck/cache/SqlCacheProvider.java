package com.remiaft.neteasewordscheck.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqlCacheProvider implements CacheProvider {
    private static final String TABLE = "netease_words_cache";

    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public SqlCacheProvider(ConnectionProvider connectionProvider, Logger logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                             "content VARCHAR(512) PRIMARY KEY," +
                             "violation TEXT," +
                             "checked_at TIMESTAMP NOT NULL" +
                             ")")) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to initialize SQL cache", exception);
        }
    }

    @Override
    public Optional<CacheEntry> get(String content, Duration ttl) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT violation, checked_at FROM " + TABLE + " WHERE content = ?")) {
            statement.setString(1, content);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp timestamp = rs.getTimestamp("checked_at");
                if (timestamp == null) {
                    return Optional.empty();
                }
                Instant checkedAt = timestamp.toInstant();
                if (checkedAt.plus(ttl).isBefore(Instant.now())) {
                    delete(content);
                    return Optional.empty();
                }
                String violation = rs.getString("violation");
                return Optional.of(new CacheEntry(content, violation, checkedAt));
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to query cache", exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(String content, String violation) {
        Instant now = Instant.now();
        try (Connection connection = connectionProvider.getConnection()) {
            deleteInternal(connection, content);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + TABLE + " (content, violation, checked_at) VALUES (?, ?, ?)")) {
                insert.setString(1, content);
                insert.setString(2, violation);
                insert.setTimestamp(3, Timestamp.from(now));
                insert.executeUpdate();
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to store cache entry", exception);
        }
    }

    private void delete(String content) {
        try (Connection connection = connectionProvider.getConnection()) {
            deleteInternal(connection, content);
        } catch (SQLException exception) {
            logger.log(Level.FINE, "Failed to delete cache entry", exception);
        }
    }

    private void deleteInternal(Connection connection, String content) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE content = ?")) {
            delete.setString(1, content);
            delete.executeUpdate();
        }
    }

    @Override
    public void close() {
        // Nothing to close; connections are provided per operation.
    }
}
