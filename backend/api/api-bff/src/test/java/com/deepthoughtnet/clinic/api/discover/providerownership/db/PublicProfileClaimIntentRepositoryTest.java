package com.deepthoughtnet.clinic.api.discover.providerownership.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipRepository;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = PublicProfileClaimIntentRepositoryTest.TestApplication.class)
@Transactional
class PublicProfileClaimIntentRepositoryTest extends PostgresTestContainerSupport {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private PublicProfileClaimIntentRepository claimIntentRepository;

    @Autowired
    private PublicProfileOwnershipRepository ownershipRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void createClinicClaimIntentPersistsJsonbEvidence() throws Exception {
        UUID providerAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PublicProfileClaimIntentEntity intent = PublicProfileClaimIntentEntity.create(
                "claim-reference-1",
                PublicProfileType.CLINIC,
                "public-clinic-reference",
                "tenant-reference",
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                12L,
                OffsetDateTime.now().plusHours(12),
                "Healthcare initiated connection"
        );
        intent.submit(providerAccountId, "{\"verifiedPhone\":true,\"signals\":[\"phone\",\"address\"]}");

        claimIntentRepository.saveAndFlush(intent);
        entityManager.clear();

        PublicProfileClaimIntentEntity reloaded = claimIntentRepository.findByConnectionReference("claim-reference-1")
                .orElseThrow();

        JsonNode evidence = new ObjectMapper().readTree(reloaded.getEvidenceSnapshotJson());
        assertThat(evidence.get("verifiedPhone").asBoolean()).isTrue();
        assertThat(evidence.get("signals")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("""
                select jsonb_typeof(evidence_snapshot_json)
                from discover_provider_claim_intents
                where connection_reference = ?
                """, String.class, "claim-reference-1")).isEqualTo("object");
    }

    @Test
    void createClinicClaimIntentSupportsEmptyEvidence() throws Exception {
        UUID providerAccountId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        PublicProfileClaimIntentEntity intent = PublicProfileClaimIntentEntity.create(
                "claim-reference-2",
                PublicProfileType.CLINIC,
                "public-clinic-reference-2",
                "tenant-reference-2",
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                13L,
                OffsetDateTime.now().plusHours(12),
                "Healthcare initiated connection"
        );
        intent.submit(providerAccountId, null);

        claimIntentRepository.saveAndFlush(intent);
        entityManager.clear();

        PublicProfileClaimIntentEntity reloaded = claimIntentRepository.findByConnectionReference("claim-reference-2")
                .orElseThrow();

        assertThat(reloaded.getEvidenceSnapshotJson()).isEqualTo("{}");
        assertThat(jdbcTemplate.queryForObject("""
                select jsonb_typeof(evidence_snapshot_json)
                from discover_provider_claim_intents
                where connection_reference = ?
                """, String.class, "claim-reference-2")).isEqualTo("object");
    }

    @Test
    void claimIntentCreationRollsBackOnInvalidEvidence() {
        long claimIntentsBefore = claimIntentRepository.count();
        long ownershipsBefore = ownershipRepository.count();

        assertThatThrownBy(() -> {
            PublicProfileClaimIntentEntity intent = PublicProfileClaimIntentEntity.create(
                    "claim-reference-3",
                    PublicProfileType.CLINIC,
                    "public-clinic-reference-3",
                    "tenant-reference-3",
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    14L,
                    OffsetDateTime.now().plusHours(12),
                    "Healthcare initiated connection"
            );
            intent.submit(UUID.fromString("77777777-7777-7777-7777-777777777777"), "{\"broken\":");
            claimIntentRepository.saveAndFlush(intent);
        }).isInstanceOf(IllegalArgumentException.class);

        entityManager.clear();
        assertThat(claimIntentRepository.count()).isEqualTo(claimIntentsBefore);
        assertThat(ownershipRepository.count()).isEqualTo(ownershipsBefore);
    }

    @Test
    void ownershipEvidenceSnapshotRoundTripsAsJsonb() throws Exception {
        PublicProfileOwnershipEntity ownership = PublicProfileOwnershipEntity.create(
                "public-clinic-reference",
                PublicProfileType.CLINIC,
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "HEALTHCARE_INITIATED_CONNECTION",
                "tenant-reference",
                15L,
                "Claim submitted"
        );
        ownership.recordEvidenceSnapshot("{\"strongMatches\":[\"phone\",\"address\"],\"confidence\":\"HIGH\"}");

        ownershipRepository.saveAndFlush(ownership);
        entityManager.clear();

        PublicProfileOwnershipEntity reloaded = ownershipRepository.findById(ownership.getId()).orElseThrow();
        JsonNode evidence = new ObjectMapper().readTree(reloaded.getEvidenceSnapshotJson());
        assertThat(evidence.get("confidence").asText()).isEqualTo("HIGH");
        assertThat(jdbcTemplate.queryForObject("""
                select jsonb_typeof(evidence_snapshot_json)
                from discover_public_profile_ownerships
                where id = ?
                """, String.class, ownership.getId())).isEqualTo("object");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import(PersistenceScanConfig.class)
    static class TestApplication {
    }
}
