package com.deepthoughtnet.clinic.api.discover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DiscoverProviderServiceTypeMigrationTest extends PostgresTestContainerSupport {

    @Test
    void v133CanonicalizesLegacyServiceRowsAndRebuildsTheConstraint() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name(), "132").migrate();

            UUID providerId = UUID.randomUUID();
            insertApplication(schema.name(), providerId);
            insertService(schema.name(), providerId, UUID.randomUUID(), "CONSULTATIONS", "Consultations");
            insertService(schema.name(), providerId, UUID.randomUUID(), "LAB", "Lab");
            insertService(schema.name(), providerId, UUID.randomUUID(), "PROCEDURES", "Procedures");
            insertService(schema.name(), providerId, UUID.randomUUID(), "TELECONSULTATION", "Teleconsultation");
            insertService(schema.name(), providerId, UUID.randomUUID(), "PHARMACY", "Pharmacy");
            insertService(schema.name(), providerId, UUID.randomUUID(), "RADIOLOGY", "Radiology");

            long beforeCount = countRows(schema.name());

            Flyway flyway = flyway(schema.name());
            flyway.migrate();

            assertThat(countRows(schema.name())).isEqualTo(beforeCount);
            assertThat(selectServiceTypes(schema.name())).containsExactlyInAnyOrder(
                    "CONSULTATION",
                    "LAB_COLLECTION",
                    "MINOR_PROCEDURES",
                    "TELECONSULTATION",
                    "PREVENTIVE_CARE",
                    "LAB_COLLECTION"
            );
            assertThat(constraintDefinition(schema.name(), "ck_discover_provider_service_type"))
                    .contains("CONSULTATION")
                    .contains("TELECONSULTATION")
                    .contains("HEALTH_CHECKUPS")
                    .contains("VACCINATION")
                    .contains("MINOR_PROCEDURES")
                    .contains("HOME_VISIT")
                    .contains("LAB_COLLECTION")
                    .contains("CHRONIC_DISEASE_MANAGEMENT")
                    .contains("PREVENTIVE_CARE");
        }
    }

    @Test
    void v133NormalizesFormattedLegacyServiceValues() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name(), "132").migrate();

            UUID providerId = UUID.randomUUID();
            insertApplication(schema.name(), providerId);
            dropServiceConstraint(schema.name());
            insertService(schema.name(), providerId, UUID.randomUUID(), "  consultation  ", "Consultation");
            insertService(schema.name(), providerId, UUID.randomUUID(), "tele-consultation", "Teleconsultation");
            insertService(schema.name(), providerId, UUID.randomUUID(), "Health Checkup", "Health Checkup");
            insertService(schema.name(), providerId, UUID.randomUUID(), "minor-procedure", "Minor Procedure");
            insertService(schema.name(), providerId, UUID.randomUUID(), "home visits", "Home Visits");
            insertService(schema.name(), providerId, UUID.randomUUID(), "lab tests", "Lab Tests");
            insertService(schema.name(), providerId, UUID.randomUUID(), "preventive-services", "Preventive Services");

            flyway(schema.name()).migrate();

            assertThat(selectServiceTypes(schema.name()))
                    .containsExactlyInAnyOrder(
                            "CONSULTATION",
                            "TELECONSULTATION",
                            "HEALTH_CHECKUPS",
                            "MINOR_PROCEDURES",
                            "HOME_VISIT",
                            "LAB_COLLECTION",
                            "PREVENTIVE_CARE"
                    );
        }
    }

    @Test
    void v133FailsWithUsefulDiagnosticsForUnsupportedNullBlankAndUnknownValues() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name(), "132").migrate();

            UUID providerId = UUID.randomUUID();
            insertApplication(schema.name(), providerId);
            dropServiceConstraint(schema.name());
            dropServiceTypeNotNull(schema.name());
            insertService(schema.name(), providerId, UUID.randomUUID(), null, "Null service");
            insertService(schema.name(), providerId, UUID.randomUUID(), "   ", "Blank service");
            insertService(schema.name(), providerId, UUID.randomUUID(), "ARBITRARY", "Arbitrary");

            assertThatThrownBy(() -> flyway(schema.name()).migrate())
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Unexpected discover_provider_services.service_type values remain after canonicalization")
                    .hasMessageContaining("<NULL>")
                    .hasMessageContaining("<BLANK>")
                    .hasMessageContaining("ARBITRARY");
        }
    }

    @Test
    void migratedConstraintAcceptsCanonicalCodesAndRejectsUnknownValues() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            flyway(schema.name()).migrate();

            UUID providerId = UUID.randomUUID();
            insertApplication(schema.name(), providerId);
            for (String code : List.of(
                    "CONSULTATION",
                    "TELECONSULTATION",
                    "HEALTH_CHECKUPS",
                    "VACCINATION",
                    "MINOR_PROCEDURES",
                    "HOME_VISIT",
                    "LAB_COLLECTION",
                    "CHRONIC_DISEASE_MANAGEMENT",
                    "PREVENTIVE_CARE"
            )) {
                insertService(schema.name(), providerId, UUID.randomUUID(), code, code);
            }

            assertThat(selectServiceTypes(schema.name()))
                    .containsExactlyInAnyOrder(
                            "CONSULTATION",
                            "TELECONSULTATION",
                            "HEALTH_CHECKUPS",
                            "VACCINATION",
                            "MINOR_PROCEDURES",
                            "HOME_VISIT",
                            "LAB_COLLECTION",
                            "CHRONIC_DISEASE_MANAGEMENT",
                            "PREVENTIVE_CARE"
                    );

            assertThatThrownBy(() -> insertService(schema.name(), providerId, UUID.randomUUID(), "ARBITRARY", "Arbitrary"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_discover_provider_service_type");
        }
    }

    private static Flyway flyway(String schema) {
        return flyway(schema, null);
    }

    private static Flyway flyway(String schema, String target) {
        var configure = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl() + "?currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) {
            configure.target(target);
        }
        return configure.load();
    }

    private static void insertApplication(String schema, UUID id) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     insert into %s.discover_provider_applications (
                         id, reference_number, provider_type, status, token_hash, email, phone, password_hash
                     ) values (?, ?, 'INDIVIDUAL_DOCTOR', 'DRAFT', ?, ?, ?, ?)
                     """.formatted(schema))) {
            statement.setObject(1, id);
            statement.setString(2, "JDR-2026-" + id.toString().substring(0, 8).toUpperCase());
            statement.setString(3, "token-hash");
            statement.setString(4, "doctor@example.com");
            statement.setString(5, "9999999999");
            statement.setString(6, "password-hash");
            statement.executeUpdate();
        }
    }

    private static void dropServiceConstraint(String schema) throws Exception {
        executeSql(schema, "alter table discover_provider_services drop constraint if exists ck_discover_provider_service_type");
    }

    private static void dropServiceTypeNotNull(String schema) throws Exception {
        executeSql(schema, "alter table discover_provider_services alter column service_type drop not null");
    }

    private static void executeSql(String schema, String sql) throws Exception {
        try (Connection connection = connection();
             var statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute(sql);
        }
    }

    private static void insertService(String schema, UUID providerId, UUID id, String serviceType, String label) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     insert into %s.discover_provider_services (
                         id, provider_id, service_type, label, description, enabled
                     ) values (?, ?, ?, ?, ?, true)
                     """.formatted(schema))) {
            statement.setObject(1, id);
            statement.setObject(2, providerId);
            statement.setString(3, serviceType);
            statement.setString(4, label);
            statement.setString(5, label + " description");
            statement.executeUpdate();
        }
    }

    private static List<String> selectServiceTypes(String schema) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     select service_type
                     from %s.discover_provider_services
                     order by service_type
                     """.formatted(schema));
             var resultSet = statement.executeQuery()) {
            var values = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        }
    }

    private static long countRows(String schema) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     select count(*)
                     from %s.discover_provider_services
                     """.formatted(schema));
             var resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return 0L;
            }
            return resultSet.getLong(1);
        }
    }

    private static String constraintDefinition(String schema, String constraintName) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("""
                     select pg_get_constraintdef(con.oid)
                     from pg_constraint con
                     join pg_class rel on rel.oid = con.conrelid
                     join pg_namespace nsp on nsp.oid = rel.relnamespace
                     where rel.relname = 'discover_provider_services'
                       and con.conname = ?
                       and nsp.nspname = ?
                     """)) {
            statement.setString(1, constraintName);
            statement.setString(2, schema);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Constraint not found: " + constraintName);
                }
                return resultSet.getString(1);
            }
        }
    }

    private static ManagedSchema createSchema() throws Exception {
        String schema = "discover_service_type_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection();
             var statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
        }
        return new ManagedSchema(schema);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record ManagedSchema(String name) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            try (Connection connection = connection();
                 var statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + name + " cascade");
            }
        }
    }
}
