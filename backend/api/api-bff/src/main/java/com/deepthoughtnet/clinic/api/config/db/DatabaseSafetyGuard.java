package com.deepthoughtnet.clinic.api.config.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public final class DatabaseSafetyGuard {
    private static final Pattern POSTGRES_URL = Pattern.compile("^jdbc:postgresql://[^/]+/([^?]+)(?:\\?.*)?$");
    private static final Pattern TEST_MARKER = Pattern.compile("(?i)(?:^|[_-])(test|it|ci|e2e)(?:$|[_-])");
    private static final Set<String> DISALLOWED_DATABASES = Set.of(
            "clinic_management",
            "clinic_management_uat",
            "clinic_management_prod",
            "postgres",
            "keycloak"
    );

    private DatabaseSafetyGuard() {
    }

    public static DatabaseConnectionInfo inspect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            String user = metaData.getUserName();
            String databaseName = extractDatabaseName(url);
            return new DatabaseConnectionInfo(url, databaseName, safeDatabaseName(databaseName), user);
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to inspect database connection", ex);
        }
    }

    public static void assertTestDatabase(DataSource dataSource) {
        DatabaseConnectionInfo info = inspect(dataSource);
        if (!info.isTestDatabase()) {
            throw new IllegalStateException("Refusing destructive database test against non-test database: " + info.safeDatabaseName());
        }
    }

    public static void assertRuntimeDatabase(Environment environment, DataSource dataSource) {
        DatabaseConnectionInfo info = inspect(dataSource);
        boolean testProfile = hasProfile(environment, "test");
        boolean localLikeProfile = hasAnyProfile(environment, "local", "dev", "docker");
        boolean uatProfile = hasProfile(environment, "uat");
        boolean prodProfile = hasProfile(environment, "prod");

        if (testProfile) {
            assertTestDatabase(dataSource);
            return;
        }

        if (prodProfile) {
            String productionIdentifier = environment.getProperty("clinic.database.production-identifier");
            if (!StringUtils.hasText(productionIdentifier)) {
                throw new IllegalStateException("Refusing startup without explicit production database identifier for non-test profile: " + info.safeDatabaseName());
            }
            if (!productionIdentifier.equalsIgnoreCase(info.databaseName())) {
                throw new IllegalStateException("Refusing startup against unexpected production database: " + info.safeDatabaseName());
            }
        } else if (uatProfile) {
            boolean approved = containsApprovedUatMarker(info.databaseName())
                    || environment.getProperty("clinic.database.approved-uat", Boolean.class, false);
            if (!approved) {
                throw new IllegalStateException("Refusing startup against non-UAT database in uat profile: " + info.safeDatabaseName());
            }
        } else if (localLikeProfile || environment.getActiveProfiles().length == 0) {
            if (info.isTestDatabase() && environment.getActiveProfiles().length == 0) {
                enforceSafetyFlags(environment, info);
                return;
            }
            if (!"clinic_management".equalsIgnoreCase(info.databaseName())) {
                throw new IllegalStateException("Refusing startup against unexpected local database: " + info.safeDatabaseName());
            }
        }

        enforceSafetyFlags(environment, info);
    }

    public static void assertNoDestructiveTestFeatures(Environment environment, DatabaseConnectionInfo info) {
        enforceSafetyFlags(environment, info);
    }

    private static void enforceSafetyFlags(Environment environment, DatabaseConnectionInfo info) {
        boolean destructiveSeedEnabled = environment.getProperty("clinic.database.destructive-seed-enabled", Boolean.class, false);
        boolean testSchemaCreatorEnabled = environment.getProperty("clinic.database.test-schema-creator-enabled", Boolean.class, false);
        boolean flywayCleanDisabled = environment.getProperty("spring.flyway.clean-disabled", Boolean.class, true);
        boolean testProfile = hasProfile(environment, "test");
        boolean prodProfile = hasProfile(environment, "prod");
        boolean uatProfile = hasProfile(environment, "uat");

        if ((prodProfile || uatProfile) && destructiveSeedEnabled) {
            throw new IllegalStateException("Refusing startup because destructive seeding is enabled outside test/local/dev/docker: " + info.safeDatabaseName());
        }
        if ((prodProfile || uatProfile || !testProfile) && testSchemaCreatorEnabled) {
            throw new IllegalStateException("Refusing startup because test schema creation is enabled outside test: " + info.safeDatabaseName());
        }
        if (!testProfile && !flywayCleanDisabled) {
            throw new IllegalStateException("Refusing startup because Flyway clean is enabled outside test: " + info.safeDatabaseName());
        }
        if (prodProfile || uatProfile) {
            if (destructiveSeedEnabled || testSchemaCreatorEnabled) {
                throw new IllegalStateException("Refusing startup because destructive database features are enabled in a protected profile: " + info.safeDatabaseName());
            }
        }
    }

    private static boolean hasProfile(Environment environment, String profile) {
        for (String activeProfile : environment.getActiveProfiles()) {
            if (profile.equalsIgnoreCase(activeProfile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyProfile(Environment environment, String... profiles) {
        for (String profile : profiles) {
            if (hasProfile(environment, profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsApprovedUatMarker(String databaseName) {
        String normalized = databaseName.toLowerCase(Locale.ROOT);
        return normalized.contains("uat") || normalized.contains("preprod") || normalized.contains("staging");
    }

    private static String extractDatabaseName(String url) {
        if (!StringUtils.hasText(url)) {
            return "<unknown>";
        }
        if (url.startsWith("jdbc:tc:postgresql:")) {
            return url;
        }
        Matcher matcher = POSTGRES_URL.matcher(url);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        int slash = url.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < url.length()) {
            String tail = url.substring(slash + 1);
            int query = tail.indexOf('?');
            return query >= 0 ? tail.substring(0, query) : tail;
        }
        return url;
    }

    private static boolean isApprovedTestDatabaseName(String databaseName) {
        if (!StringUtils.hasText(databaseName)) {
            return false;
        }
        String normalized = databaseName.toLowerCase(Locale.ROOT);
        if (DISALLOWED_DATABASES.contains(normalized)) {
            return false;
        }
        if (normalized.startsWith("test_") || normalized.endsWith("_test") || normalized.startsWith("it_") || normalized.endsWith("_it")) {
            return true;
        }
        if (normalized.startsWith("ci_") || normalized.endsWith("_ci") || normalized.startsWith("e2e_") || normalized.endsWith("_e2e")) {
            return true;
        }
        return TEST_MARKER.matcher(normalized).find();
    }

    private static String safeDatabaseName(String databaseName) {
        return StringUtils.hasText(databaseName) ? databaseName : "<unknown>";
    }

    public record DatabaseConnectionInfo(String jdbcUrl, String databaseName, String safeDatabaseName, String userName) {
        boolean isTestDatabase() {
            if (!StringUtils.hasText(jdbcUrl)) {
                return false;
            }
            if (jdbcUrl.startsWith("jdbc:tc:postgresql:")) {
                return true;
            }
            return isApprovedTestDatabaseName(databaseName);
        }
    }
}
