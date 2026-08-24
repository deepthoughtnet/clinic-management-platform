package com.deepthoughtnet.clinic.api.lab;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class LaboratoryReportArtifactVersioningMigrationTest extends PostgresTestContainerSupport {

    @Test
    void v160RepairsDuplicateCurrentArtifactsAndBackfillsFinalConsolidatedContent() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name(), "159");
            flyway.migrate();

            UUID tenantId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID cbcItemId = UUID.randomUUID();
            UUID hba1cItemId = UUID.randomUUID();
            UUID artifactOneId = UUID.randomUUID();
            UUID artifactTwoId = UUID.randomUUID();

            insertOrder(schema.name(), tenantId, orderId);
            insertOrderItem(schema.name(), tenantId, orderId, cbcItemId, "CBC", "Complete Blood Count", 1);
            insertOrderItem(schema.name(), tenantId, orderId, hba1cItemId, "HBA1C", "HbA1c", 2);
            insertLifecycle(schema.name(), tenantId, orderId, cbcItemId, "PUBLISHED");
            insertLifecycle(schema.name(), tenantId, orderId, hba1cItemId, "PUBLISHED");

            insertArtifact(schema.name(), artifactOneId, tenantId, orderId, 1, "LAB-0F6F0AADC5-v1.pdf", "CURRENT", "INDIVIDUAL", "FINAL", "2026-08-23T04:30:00Z", "2026-08-23T04:30:00Z", "[\"" + cbcItemId + "\"]", "token-v1");
            insertArtifact(schema.name(), artifactTwoId, tenantId, orderId, 2, "LAB-0F6F0AADC5-v2.pdf", "CURRENT", "CONSOLIDATED", "FINAL", "2026-08-23T05:18:00Z", "2026-08-23T05:18:00Z", "[\"" + cbcItemId + "\"]", "token-v2");
            insertPublicationTest(schema.name(), tenantId, artifactOneId, cbcItemId, 1, "2026-08-23T04:30:00Z");
            insertPublicationTest(schema.name(), tenantId, artifactTwoId, cbcItemId, 1, "2026-08-23T05:18:00Z");

            flyway = flyway(schema.name());
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(countCurrentArtifacts(connection, schema.name(), orderId)).isEqualTo(1L);
                assertThat(currentArtifactStatus(connection, schema.name(), artifactOneId)).isEqualTo("SUPERSEDED");
                assertThat(currentArtifactStatus(connection, schema.name(), artifactTwoId)).isEqualTo("CURRENT");
                assertThat(currentArtifactSelectedIds(connection, schema.name(), artifactTwoId))
                        .containsExactly(cbcItemId.toString(), hba1cItemId.toString());
                assertThat(currentArtifactMode(connection, schema.name(), artifactTwoId)).isEqualTo("CONSOLIDATED");
                assertThat(currentArtifactType(connection, schema.name(), artifactTwoId)).isEqualTo("FINAL");
                assertThat(countPublicationTests(connection, schema.name(), artifactTwoId)).isEqualTo(2L);
                assertThat(uniqueCurrentIndexExists(connection, schema.name())).isTrue();
                assertThat(flyway.info().current()).isNotNull();
                assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(latestResolvedVersion(flyway));
            }
        }
    }

    @Test
    void v160OrdersFinalConsolidatedSelectedItemsDeterministically() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name(), "159");
            flyway.migrate();

            UUID tenantId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID hba1cItemId = UUID.randomUUID();
            UUID cbcItemId = UUID.randomUUID();
            UUID artifactId = UUID.randomUUID();

            insertOrder(schema.name(), tenantId, orderId);
            insertOrderItem(schema.name(), tenantId, orderId, hba1cItemId, "HBA1C", "HbA1c", 1);
            insertOrderItem(schema.name(), tenantId, orderId, cbcItemId, "CBC", "Complete Blood Count", 2);
            insertLifecycle(schema.name(), tenantId, orderId, hba1cItemId, "PUBLISHED");
            insertLifecycle(schema.name(), tenantId, orderId, cbcItemId, "PUBLISHED");

            insertArtifact(schema.name(), artifactId, tenantId, orderId, 1, "LAB-0F6F0AADC5-final.pdf", "CURRENT", "CONSOLIDATED", "FINAL", "2026-08-23T05:18:00Z", "2026-08-23T05:18:00Z", "[\"" + cbcItemId + "\"]", "token-final");
            insertPublicationTest(schema.name(), tenantId, artifactId, cbcItemId, 1, "2026-08-23T05:18:00Z");

            flyway = flyway(schema.name());
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(currentArtifactSelectedIds(connection, schema.name(), artifactId))
                        .containsExactly(hba1cItemId.toString(), cbcItemId.toString());
            }
        }
    }

    @Test
    void v160DoesNotWidenIncompleteOrCancelledTestsIntoFinalConsolidatedArtifacts() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name(), "159");
            flyway.migrate();

            UUID tenantId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID cbcItemId = UUID.randomUUID();
            UUID cancelledItemId = UUID.randomUUID();
            UUID artifactId = UUID.randomUUID();

            insertOrder(schema.name(), tenantId, orderId);
            insertOrderItem(schema.name(), tenantId, orderId, cbcItemId, "CBC", "Complete Blood Count", 1);
            insertOrderItem(schema.name(), tenantId, orderId, cancelledItemId, "HBA1C", "HbA1c", 2);
            insertLifecycle(schema.name(), tenantId, orderId, cbcItemId, "PUBLISHED");
            insertLifecycle(schema.name(), tenantId, orderId, cancelledItemId, "CANCELLED");

            insertArtifact(schema.name(), artifactId, tenantId, orderId, 1, "LAB-0F6F0AADC5-final.pdf", "CURRENT", "CONSOLIDATED", "FINAL", "2026-08-23T05:18:00Z", "2026-08-23T05:18:00Z", "[\"" + cbcItemId + "\"]", "token-final");
            insertPublicationTest(schema.name(), tenantId, artifactId, cbcItemId, 1, "2026-08-23T05:18:00Z");

            flyway = flyway(schema.name());
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(currentArtifactSelectedIds(connection, schema.name(), artifactId))
                        .containsExactly(cbcItemId.toString());
                assertThat(countPublicationTests(connection, schema.name(), artifactId)).isEqualTo(1L);
            }
        }
    }

    @Test
    void v160LeavesAlreadyCorrectFinalConsolidatedArtifactsStable() throws Exception {
        try (ManagedSchema schema = createSchema()) {
            Flyway flyway = flyway(schema.name(), "159");
            flyway.migrate();

            UUID tenantId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID cbcItemId = UUID.randomUUID();
            UUID hba1cItemId = UUID.randomUUID();
            UUID artifactId = UUID.randomUUID();

            insertOrder(schema.name(), tenantId, orderId);
            insertOrderItem(schema.name(), tenantId, orderId, cbcItemId, "CBC", "Complete Blood Count", 1);
            insertOrderItem(schema.name(), tenantId, orderId, hba1cItemId, "HBA1C", "HbA1c", 2);
            insertLifecycle(schema.name(), tenantId, orderId, cbcItemId, "PUBLISHED");
            insertLifecycle(schema.name(), tenantId, orderId, hba1cItemId, "PUBLISHED");

            insertArtifact(schema.name(), artifactId, tenantId, orderId, 1, "LAB-0F6F0AADC5-final.pdf", "CURRENT", "CONSOLIDATED", "FINAL", "2026-08-23T05:18:00Z", "2026-08-23T05:18:00Z", "[\"" + cbcItemId + "\",\"" + hba1cItemId + "\"]", "token-final");
            insertPublicationTest(schema.name(), tenantId, artifactId, cbcItemId, 1, "2026-08-23T05:18:00Z");
            insertPublicationTest(schema.name(), tenantId, artifactId, hba1cItemId, 1, "2026-08-23T05:18:00Z");

            flyway = flyway(schema.name());
            flyway.migrate();

            try (Connection connection = connection()) {
                assertThat(currentArtifactSelectedIds(connection, schema.name(), artifactId))
                        .containsExactly(cbcItemId.toString(), hba1cItemId.toString());
                assertThat(currentArtifactMode(connection, schema.name(), artifactId)).isEqualTo("CONSOLIDATED");
                assertThat(currentArtifactType(connection, schema.name(), artifactId)).isEqualTo("FINAL");
                assertThat(countCurrentArtifacts(connection, schema.name(), orderId)).isEqualTo(1L);
            }
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

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static ManagedSchema createSchema() throws Exception {
        String schema = "lab_report_artifact_versioning_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create schema if not exists " + schema);
        }
        return new ManagedSchema(schema);
    }

    private static void insertOrder(String schema, UUID tenantId, UUID orderId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                    insert into %s.lab_orders (
                        id, tenant_id, order_number, patient_id, patient_number, patient_name,
                        order_origin, status, ordered_at, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, 'CONSULTATION', 'REPORT_GENERATED', ?, ?, ?)
                     """.formatted(schema))) {
            OffsetDateTime now = OffsetDateTime.parse("2026-08-23T05:18:00Z");
            statement.setObject(1, orderId);
            statement.setObject(2, tenantId);
            statement.setString(3, "LAB-0F6F0AADC5");
            statement.setObject(4, UUID.randomUUID());
            statement.setString(5, "P-001");
            statement.setString(6, "Test Patient");
            statement.setObject(7, now);
            statement.setObject(8, now);
            statement.setObject(9, now);
            statement.executeUpdate();
        }
    }

    private static void insertOrderItem(String schema, UUID tenantId, UUID orderId, UUID itemId, String testCode, String testName, int sortOrder) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into %s.lab_order_items (
                         id, tenant_id, lab_order_id, test_code, test_name, category, price, sort_order, created_at
                     ) values (?, ?, ?, ?, ?, 'BIOCHEMISTRY', 100.00, ?, ?)
                     """.formatted(schema))) {
            OffsetDateTime now = OffsetDateTime.parse("2026-08-23T05:18:00Z");
            statement.setObject(1, itemId);
            statement.setObject(2, tenantId);
            statement.setObject(3, orderId);
            statement.setString(4, testCode);
            statement.setString(5, testName);
            statement.setInt(6, sortOrder);
            statement.setObject(7, now);
            statement.executeUpdate();
        }
    }

    private static void insertLifecycle(String schema, UUID tenantId, UUID orderId, UUID itemId, String state) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into %s.lab_order_test_lifecycles (
                         id, tenant_id, lab_order_id, lab_order_item_id, state, latest_result_revision, created_at, updated_at
                     ) values (?, ?, ?, ?, ?, 1, ?, ?)
                     """.formatted(schema))) {
            OffsetDateTime now = OffsetDateTime.parse("2026-08-23T05:18:00Z");
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenantId);
            statement.setObject(3, orderId);
            statement.setObject(4, itemId);
            statement.setString(5, state);
            statement.setObject(6, now);
            statement.setObject(7, now);
            statement.executeUpdate();
        }
    }

    private static void insertArtifact(String schema, UUID artifactId, UUID tenantId, UUID orderId, int artifactNumber, String filename, String reportStatus, String reportMode, String reportType, String generatedAt, String publishedAt, String selectedItemIds, String verificationToken) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into %s.lab_report_publication_artifacts (
                         id, tenant_id, lab_order_id, artifact_number, filename, storage_reference,
                         verification_token, verification_url, delivery_channels, selected_item_ids,
                         report_mode, report_type, report_status, generated_at, generated_by, published_at, published_by, notes
                     ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::text, ?, ?, ?, ?::timestamptz, ?, ?::timestamptz, ?, ?)
                     """.formatted(schema))) {
            statement.setObject(1, artifactId);
            statement.setObject(2, tenantId);
            statement.setObject(3, orderId);
            statement.setInt(4, artifactNumber);
            statement.setString(5, filename);
            statement.setString(6, "lab/report/" + filename);
            statement.setString(7, verificationToken);
            statement.setString(8, "/api/public/lab/reports/" + verificationToken + "/verify");
            statement.setString(9, "[\"PATIENT_PORTAL\"]");
            statement.setString(10, selectedItemIds);
            statement.setString(11, reportMode);
            statement.setString(12, reportType);
            statement.setString(13, reportStatus);
            statement.setString(14, generatedAt);
            statement.setObject(15, UUID.randomUUID());
            statement.setString(16, publishedAt);
            statement.setObject(17, UUID.randomUUID());
            statement.setString(18, "legacy");
            statement.executeUpdate();
        }
    }

    private static void insertPublicationTest(String schema, UUID tenantId, UUID artifactId, UUID itemId, int revisionNumber, String includedAt) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into %s.lab_report_publication_tests (
                         id, tenant_id, publication_artifact_id, lab_order_item_id, result_revision_number, included_at
                     ) values (?, ?, ?, ?, ?, ?::timestamptz)
                     """.formatted(schema))) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenantId);
            statement.setObject(3, artifactId);
            statement.setObject(4, itemId);
            statement.setInt(5, revisionNumber);
            statement.setString(6, includedAt);
            statement.executeUpdate();
        }
    }

    private static long countCurrentArtifacts(Connection connection, String schema, UUID orderId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from %s.lab_report_publication_artifacts
                where lab_order_id = ?
                  and report_status = 'CURRENT'
                """.formatted(schema))) {
            statement.setObject(1, orderId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private static String currentArtifactStatus(Connection connection, String schema, UUID artifactId) throws Exception {
        return selectString(connection, schema, "select report_status from %s.lab_report_publication_artifacts where id = ?", artifactId);
    }

    private static String currentArtifactMode(Connection connection, String schema, UUID artifactId) throws Exception {
        return selectString(connection, schema, "select report_mode from %s.lab_report_publication_artifacts where id = ?", artifactId);
    }

    private static String currentArtifactType(Connection connection, String schema, UUID artifactId) throws Exception {
        return selectString(connection, schema, "select report_type from %s.lab_report_publication_artifacts where id = ?", artifactId);
    }

    private static List<String> currentArtifactSelectedIds(Connection connection, String schema, UUID artifactId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select selected_item_ids
                from %s.lab_report_publication_artifacts
                where id = ?
                """.formatted(schema))) {
            statement.setObject(1, artifactId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return List.of();
                }
                String value = resultSet.getString(1);
                if (value == null || value.isBlank()) {
                    return List.of();
                }
                return Arrays.stream(value.replace("[", "").replace("]", "").replace("\"", "").split(","))
                        .map(String::trim)
                        .filter(token -> !token.isBlank())
                        .toList();
            }
        }
    }

    private static long countPublicationTests(Connection connection, String schema, UUID artifactId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from %s.lab_report_publication_tests
                where publication_artifact_id = ?
                """.formatted(schema))) {
            statement.setObject(1, artifactId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private static boolean uniqueCurrentIndexExists(Connection connection, String schema) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                select 1
                from pg_indexes
                where schemaname = ?
                  and tablename = 'lab_report_publication_artifacts'
                  and indexname = 'uq_lab_report_publication_artifacts_current'
                """)) {
            statement.setString(1, schema);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static String selectString(Connection connection, String schema, String sql, UUID artifactId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql.formatted(schema))) {
            statement.setObject(1, artifactId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString(1);
            }
        }
    }

    private static String latestResolvedVersion(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .max(MigrationVersion::compareTo)
                .map(MigrationVersion::getVersion)
                .orElseThrow(() -> new IllegalStateException("No Flyway migrations were resolved"));
    }

    private record ManagedSchema(String name) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            try (Connection connection = connection();
                 Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + name + " cascade");
            }
        }
    }
}
