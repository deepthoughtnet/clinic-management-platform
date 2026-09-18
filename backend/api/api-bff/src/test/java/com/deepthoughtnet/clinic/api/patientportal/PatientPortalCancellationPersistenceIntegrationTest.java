package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ApiBffApplication;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientPortalAccessRequestService;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAuthorizedClinicRecord;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = ApiBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
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
class PatientPortalCancellationPersistenceIntegrationTest extends PostgresTestContainerSupport {
    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired private PatientPortalService patientPortalService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private TenantMembershipRepository membershipRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private PatientPortalAccessRequestService accessRequestService;
    @MockBean private AppUserProvisioner appUserProvisioner;

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void ownerAwareCancellationCommitsAndDisappearsFromFreshNetworkRead() {
        TenantEntity[] tenants = new TenantEntity[2];
        UUID[] currentPatientId = new UUID[1];
        UUID[] ownerPatientId = new UUID[1];
        UUID[] currentUserId = new UUID[1];
        UUID[] doctorUserId = new UUID[1];
        UUID ownerActorId = UUID.randomUUID();
        UUID[] appointmentId = new UUID[1];
        var appointmentCreatedAt = new java.time.OffsetDateTime[1];

        transactionTemplate.executeWithoutResult(status -> {
            tenants[0] = TenantEntity.create("current-" + UUID.randomUUID(), "Current Clinic", "TRIAL");
            tenants[1] = TenantEntity.create("owner-" + UUID.randomUUID(), "Owner Clinic", "TRIAL");
            entityManager.persist(tenants[0]);
            entityManager.persist(tenants[1]);
        });

        UUID currentTenant = tenants[0].getId();
        UUID ownerTenant = tenants[1].getId();
        transactionTemplate.executeWithoutResult(status -> {
            PatientEntity currentPatient = PatientEntity.create(currentTenant, "CURRENT-" + UUID.randomUUID());
            populatePatient(currentPatient, "999" + Math.abs(currentTenant.getLeastSignificantBits()));
            PatientEntity ownerPatient = PatientEntity.create(ownerTenant, "OWNER-" + UUID.randomUUID());
            populatePatient(ownerPatient, currentPatient.getMobile());
            patientRepository.saveAll(List.of(currentPatient, ownerPatient));
            currentPatientId[0] = currentPatient.getId();
            ownerPatientId[0] = ownerPatient.getId();

            AppUserEntity currentUser = AppUserEntity.create(currentTenant, "portal-" + UUID.randomUUID(), "portal@example.com", "Portal Patient");
            currentUser.setPatientId(currentPatient.getId());
            AppUserEntity doctor = AppUserEntity.create(ownerTenant, "doctor-" + UUID.randomUUID(), "doctor@example.com", "Owner Doctor");
            appUserRepository.saveAll(List.of(currentUser, doctor));
            currentUserId[0] = currentUser.getId();
            doctorUserId[0] = doctor.getId();
            membershipRepository.save(TenantMembershipEntity.create(ownerTenant, doctor.getId(), "DOCTOR"));

            AppointmentEntity appointment = AppointmentEntity.create(ownerTenant, ownerPatient.getId(), doctor.getId());
            appointment.update(LocalDate.now().plusDays(2), LocalTime.of(10, 0), null, "Review", AppointmentType.SCHEDULED,
                    AppointmentStatus.BOOKED, null);
            appointmentRepository.save(appointment);
            appointmentId[0] = appointment.getId();
            appointmentCreatedAt[0] = appointment.getUpdatedAt();
        });

        when(accessRequestService.listAuthorizedClinics(any())).thenReturn(List.of(
                new PatientPortalAuthorizedClinicRecord(ownerTenant, "owner", "Owner Clinic", ownerPatientId[0],
                        "Portal Patient", true, "PATIENT_PORTAL_ACCESS_REQUEST")));
        when(appUserProvisioner.upsertAndReturnId(eq(ownerTenant), any(), any(), any())).thenReturn(ownerActorId);
        RequestContextHolder.set(new RequestContext(TenantId.of(currentTenant), currentUserId[0], "portal-sub",
                Set.of("PATIENT"), "PATIENT", "cancellation-persistence-test"));

        var confirmation = patientPortalService.cancelAppointmentInOwningTenant(
                appointmentId[0], ownerTenant, "Cancelled by patient", "integration-cancel-" + UUID.randomUUID());

        assertThat(confirmation.status()).isEqualTo("CANCELLED");
        entityManager.clear();
        var persisted = transactionTemplate.execute(status -> appointmentRepository.findByTenantIdAndId(ownerTenant, appointmentId[0]).orElseThrow());
        assertThat(persisted.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(persisted.getVersion()).isEqualTo(1);
        assertThat(persisted.getUpdatedAt()).isAfter(appointmentCreatedAt[0]);
        var upcoming = transactionTemplate.execute(status -> patientPortalService.careAiUpcomingAppointmentsAcrossAuthorizedClinics());
        assertThat(upcoming).noneMatch(row -> appointmentId[0].equals(row.appointmentId()));
        assertThat(RequestContextHolder.requireTenantId()).isEqualTo(currentTenant);
    }

    private void populatePatient(PatientEntity patient, String mobile) {
        patient.update("Portal", "Patient", com.deepthoughtnet.clinic.patient.service.model.PatientGender.UNKNOWN,
                null, null, mobile, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, true);
    }

}
