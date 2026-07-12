package com.deepthoughtnet.clinic.api.support;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public abstract class PostgresTestContainerSupport {
    protected static final String TEST_DATABASE = "clinic_management_test";
    protected static final String TEST_USER = "clinic_test";
    protected static final String TEST_PASSWORD = "clinic_test";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName(TEST_DATABASE)
            .withUsername(TEST_USER)
            .withPassword(TEST_PASSWORD);

    static {
        POSTGRES.start();
    }

    protected static DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    protected static void executeSql(String sql) {
        try (Connection connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected static void assertContainerRunning() {
        Assertions.assertTrue(POSTGRES.isRunning());
    }
}
