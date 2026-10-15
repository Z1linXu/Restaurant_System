package com.restaurant.system.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Creates and removes only its own fresh database on an explicit loopback test cluster. */
public final class CommercialPostgres implements AutoCloseable {
    private final String adminUrl;
    private final String user;
    private final String password;
    private final String database;
    public final DriverManagerDataSource dataSource;

    public CommercialPostgres() throws Exception {
        adminUrl = System.getenv("COMMERCIAL_TEST_POSTGRES_URL");
        if (adminUrl == null || !adminUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/commercial_[a-zA-Z0-9_]+")) {
            throw new IllegalStateException("An explicit isolated commercial loopback test database is required");
        }
        user = System.getenv().getOrDefault("COMMERCIAL_TEST_POSTGRES_USER", "commercial_test");
        password = System.getenv().getOrDefault("COMMERCIAL_TEST_POSTGRES_PASSWORD", "");
        database = "commercial_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password); var sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE " + database);
        }
        dataSource = new DriverManagerDataSource(adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database, user, password);
    }

    @Override public void close() throws Exception {
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password); var sql = admin.createStatement()) {
            sql.execute("DROP DATABASE " + database);
        }
    }
}
