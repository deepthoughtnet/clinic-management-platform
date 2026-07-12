package com.deepthoughtnet.clinic.api.config.db;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseSafetyGuardTest {
    @Test
    void testProfileRejectsClinicManagementDatabase() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refusing destructive database test against non-test database: clinic_management");
    }

    @Test
    void testProfileAllowsTestContainerDatabase() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        assertThatCode(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:tc:postgresql:15-alpine:///clinic_management_test")))
                .doesNotThrowAnyException();
    }

    @Test
    void localProfileAllowsClinicManagementDatabase() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management")))
                .doesNotThrowAnyException();
    }

    @Test
    void uatProfileRejectsDestructiveSeeding() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clinic.database.approved-uat", "true")
                .withProperty("clinic.database.destructive-seed-enabled", "true");
        environment.setActiveProfiles("uat");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_uat")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destructive seeding is enabled");
    }

    @Test
    void explicitProdIdentifierIsRequired() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clinic.database.production-identifier", "clinic_management_prod")
                .withProperty("spring.flyway.clean-disabled", "true");
        environment.setActiveProfiles("prod");
        assertThatCode(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_prod")))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileRejectsMissingIdentifier() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.flyway.clean-disabled", "true");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit production database identifier");
    }

    @Test
    void prodProfileRejectsDestructiveSeeding() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clinic.database.production-identifier", "clinic_management_prod")
                .withProperty("clinic.database.destructive-seed-enabled", "true")
                .withProperty("spring.flyway.clean-disabled", "true");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destructive seeding is enabled outside test/local/dev/docker");
    }

    @Test
    void prodProfileRejectsFlywayClean() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clinic.database.production-identifier", "clinic_management_prod")
                .withProperty("spring.flyway.clean-disabled", "false");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway clean is enabled outside test");
    }

    @Test
    void prodProfileRejectsTestSchemaCreator() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("clinic.database.production-identifier", "clinic_management_prod")
                .withProperty("clinic.database.test-schema-creator-enabled", "true")
                .withProperty("spring.flyway.clean-disabled", "true");
        environment.setActiveProfiles("prod");
        assertThatThrownBy(() -> DatabaseSafetyGuard.assertRuntimeDatabase(environment, dataSource("jdbc:postgresql://localhost:5432/clinic_management_prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test schema creation is enabled outside test");
    }

    private static DataSource dataSource(String jdbcUrl) {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        DataSource dataSource = mock(DataSource.class);
        try {
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getURL()).thenReturn(jdbcUrl);
            when(metaData.getUserName()).thenReturn("clinic_test");
            doNothing().when(connection).close();
            when(dataSource.getConnection()).thenReturn(connection);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build database test stub", ex);
        }
        return dataSource;
    }
}
