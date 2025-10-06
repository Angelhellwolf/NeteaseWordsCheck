package com.remiaft.neteasewordscheck.cache;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class H2ConnectionProvider implements ConnectionProvider {
    private final Path databaseFile;

    public H2ConnectionProvider(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
    }

    @Override
    public Connection getConnection() throws SQLException {
        String url = "jdbc:h2:" + databaseFile.toAbsolutePath().toString() + ";MODE=MySQL;AUTO_SERVER=TRUE";
        return DriverManager.getConnection(url, "sa", "");
    }
}
