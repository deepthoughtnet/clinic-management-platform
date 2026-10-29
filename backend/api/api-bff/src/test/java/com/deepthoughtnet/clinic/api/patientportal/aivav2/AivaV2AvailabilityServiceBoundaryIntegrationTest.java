package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ApiBffApplication;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = ApiBffApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
        "clinic.ai.enabled=false",
        "clinic.ocr.enabled=false",
        "clinic.notifications.scheduler.enabled=false",
        "clinic.notifications.dispatcher.enabled=false",
        "clinic.carepilot.scheduler.enabled=false",
        "voice.stt.provider-order=mock",
        "voice.tts.provider-order=mock",
        "spring.flyway.enabled=true"
})
class AivaV2AvailabilityServiceBoundaryIntegrationTest extends PostgresTestContainerSupport {
    private static final UUID TENANT = UUID.fromString("4b1e2915-6d35-4a00-b9f8-2f0cc3bccb1f");
    private static final UUID DOCTOR = UUID.fromString("23cf0f04-3152-46ef-a0f6-3b243f90bbc5");
    private static final LocalDate DATE = LocalDate.of(2027, 10, 26);

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AivaV2BookingTools bookingTools;

    @Test
    void voiceAvailabilityUsesRealTenantScopedPortalAndAppointmentServices() {
        RequestContextHolder.clear();
        seedCanonicalCalendar();

        ProviderCandidate provider = new ProviderCandidate("amit", "care-private:" + DOCTOR, DOCTOR.toString(),
                null, TENANT.toString(), null, null, "Amit Verma", "General Medicine", "Jeevanam",
                ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, true, true, false));
        Instant now = Instant.parse("2026-10-23T00:00:00Z");
        BookingDraft seed = BookingDraft.create(null, TENANT.toString(), now);
        BookingDraft draft = new BookingDraft(seed.draftId(), null, TENANT.toString(), provider, null, DATE,
                null, null, (AvailabilityTimeConstraint) null, null, null, DraftStatus.COLLECTING,
                seed.revision(), seed.expiresAt(), null);

        var result = bookingTools.getBookingAvailability(draft, TENANT);

        assertThat(result.category()).isEqualTo("SUCCESS");
        assertThat(result.value().date()).isEqualTo(DATE);
        assertThat(result.value().slots()).hasSize(6);
    }

    private void seedCanonicalCalendar() {
        transactionTemplate.executeWithoutResult(status -> {
            TenantEntity tenant = TenantEntity.create("aiva-availability-" + UUID.randomUUID(),
                    "AIVA Availability Test", "TRIAL");
            ReflectionTestUtils.setField(tenant, "id", TENANT);
            entityManager.persist(tenant);

            AppUserEntity doctor = AppUserEntity.create(TENANT, "aiva-doctor-" + UUID.randomUUID(),
                    null, "Amit Verma");
            ReflectionTestUtils.setField(doctor, "id", DOCTOR);
            entityManager.persist(doctor);
            entityManager.persist(TenantMembershipEntity.create(TENANT, DOCTOR, "DOCTOR"));

            DoctorAvailabilityEntity availability = DoctorAvailabilityEntity.create(TENANT, DOCTOR);
            availability.update(DATE.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(12, 0),
                    null, null, 30, 1, true);
            entityManager.persist(availability);
            entityManager.flush();
        });
    }
}
