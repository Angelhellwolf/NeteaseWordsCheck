package com.remiaft.neteasewordscheck.cache;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class H2ConnectionProvider implements ConnectionProvider {
    private final Path databaseFile;

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public H2ConnectionProvider(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
    }

    @Override
    public Connection getConnection() throws SQLException {
        String normalizedPath = databaseFile.toAbsolutePath().toString().replace("\\", "/");
        String url = "jdbc:h2:file:" + normalizedPath + ";MODE=MySQL;AUTO_SERVER=TRUE";
        return DriverManager.getConnection(url, "sa", "");
    }
}
