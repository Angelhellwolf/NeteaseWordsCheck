package com.remiaft.neteasewordscheck.cache;

import com.remiaft.neteasewordscheck.config.MySqlSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class MySqlConnectionProvider implements ConnectionProvider {
    private final MySqlSettings settings;

    public MySqlConnectionProvider(MySqlSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public Connection getConnection() throws SQLException {
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=%s",
                settings.getHost(), settings.getPort(), settings.getDatabase(), settings.isUseSsl());
        return DriverManager.getConnection(url, settings.getUsername(), settings.getPassword());
    }
}
