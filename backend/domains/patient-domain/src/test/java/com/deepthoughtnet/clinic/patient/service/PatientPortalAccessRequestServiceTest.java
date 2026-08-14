package com.deepthoughtnet.clinic.patient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientPortalAccessRequestEntity;
import com.deepthoughtnet.clinic.patient.db.PatientPortalAccessRequestRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessContext;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestCommand;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestConflictException;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestStatus;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PatientPortalAccessRequestServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();

    private PatientPortalAccessRequestRepository requestRepository;
    private TenantRepository tenantRepository;
    private PatientRepository patientRepository;
    private PatientService patientService;
    private AppUserProvisioner appUserProvisioner;
    private AppUserRepository appUserRepository;
    private AuditEventPublisher auditEventPublisher;
    private PatientPortalAccessRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(PatientPortalAccessRequestRepository.class);
        tenantRepository = mock(TenantRepository.class);
        patientRepository = mock(PatientRepository.class);
        patientService = mock(PatientService.class);
        appUserProvisioner = mock(AppUserProvisioner.class);
        appUserRepository = mock(AppUserRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant("jeevanam-preview", TENANT_ID)));
        when(auditEventPublisher.record(any())).thenReturn(UUID.randomUUID());

        service = new PatientPortalAccessRequestService(
                requestRepository,
                tenantRepository,
                patientRepository,
                patientService,
                appUserProvisioner,
                appUserRepository,
                auditEventPublisher,
                new ObjectMapper()
        );
    }

    @Test
    void submitCreatesRequestedAccessRequest() {
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(PatientPortalAccessRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var record = service.submit(new PatientPortalAccessRequestCommand(
                "Amit Verma",
                "98765 43210",
                "amit@example.com",
                "Preview access",
                new PatientPortalAccessContext(null, null, TENANT_ID.toString(), null, null)
        ));

        assertThat(record.status()).isEqualTo(PatientPortalAccessRequestStatus.REQUESTED);
        assertThat(record.fullName()).isEqualTo("Amit Verma");
        assertThat(record.mobile()).isEqualTo("9876543210");
        assertThat(record.tenantId()).isEqualTo(TENANT_ID);
        verify(auditEventPublisher).record(any());
    }

    @Test
    void submitRejectsDuplicatePendingRequest() {
        PatientPortalAccessRequestEntity existing = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submit(new PatientPortalAccessRequestCommand(
                "Amit Verma",
                "9876543210",
                null,
                null,
                new PatientPortalAccessContext(null, null, TENANT_ID.toString(), null, null)
        ))).isInstanceOf(PatientPortalAccessRequestConflictException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void approveCreatesTemporaryAccessCodeAndLinksPatient() {
        PatientPortalAccessRequestEntity request = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_ID, "9876543210"))
                .thenReturn(List.of(patient()));
        when(requestRepository.save(any(PatientPortalAccessRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUserEntity appUser = AppUserEntity.create(TENANT_ID, "subject", null, "Amit Verma");
        when(appUserProvisioner.upsertAndReturnId(eq(TENANT_ID), any(), isNull(), eq("Amit Verma"))).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var record = service.approve(request.getId(), UUID.randomUUID(), "Platform Admin", null, null);

        assertThat(record.status()).isEqualTo(PatientPortalAccessRequestStatus.APPROVED);
        assertThat(record.linkedPatientId()).isEqualTo(PATIENT_ID);
        assertThat(record.temporaryAccessCode()).isNotBlank();
        assertThat(request.getStatus()).isEqualTo(PatientPortalAccessRequestStatus.APPROVED);
        assertThat(record.accessCodeExpiresAt()).isNotNull();
        verify(auditEventPublisher).record(any());
    }

    @Test
    void authenticateRehydratesMissingPatientAndActivatesApprovedRequest() {
        PatientPortalAccessRequestEntity request = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        request.approve(UUID.randomUUID(), "Platform Admin", null, null);
        request.attachAccessCode(new BCryptPasswordEncoder().encode("12345678"), request.getCreatedAt(), request.getCreatedAt().plusDays(7));
        when(requestRepository.findAll()).thenReturn(List.of(request));
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(request));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_ID, "9876543210")).thenReturn(List.of());
        PatientRecord createdPatient = patientRecord();
        when(patientService.create(eq(TENANT_ID), any(PatientUpsertCommand.class), isNull())).thenReturn(createdPatient);
        when(appUserProvisioner.upsertAndReturnId(eq(TENANT_ID), any(), eq("amit@example.com"), eq("Amit Verma"))).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(AppUserEntity.create(TENANT_ID, "subject", "amit@example.com", "Amit Verma")));

        var grant = service.authenticate(null, "9876543210", "12345678", null);

        assertThat(grant.tenantId()).isEqualTo(TENANT_ID);
        assertThat(grant.patientId()).isEqualTo(PATIENT_ID);
        assertThat(grant.patientDisplayName()).isEqualTo("Amit Verma");
        assertThat(request.getLinkedPatientId()).isEqualTo(PATIENT_ID);
        assertThat(request.getStatus()).isEqualTo(PatientPortalAccessRequestStatus.ACTIVE);
        verify(requestRepository, times(2)).save(request);
        verify(patientService).create(eq(TENANT_ID), any(PatientUpsertCommand.class), isNull());
        verify(auditEventPublisher).record(any());
    }

    @Test
    void revokePreventsLogin() {
        PatientPortalAccessRequestEntity request = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        request.revoke(UUID.randomUUID(), "Platform Admin", "Stopped preview access");
        when(requestRepository.findAll()).thenReturn(List.of(request));
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.authenticate(null, "9876543210", "12345678", null))
                .isInstanceOf(PatientPortalAccessRequestConflictException.class)
                .hasMessageContaining("not currently active");
    }

    private TenantEntity tenant(String code, UUID id) {
        TenantEntity tenant = TenantEntity.create(code, "Jeevanam Preview", "PRO");
        try {
            var field = TenantEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(tenant, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return tenant;
    }

    private PatientEntity patient() {
        PatientEntity patient = PatientEntity.create(TENANT_ID, "P-001");
        patient.update(
                "Amit",
                "Verma",
                PatientGender.MALE,
                null,
                null,
                "9876543210",
                "amit@example.com",
                null,
                null,
                "Pune",
                "Maharashtra",
                "India",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        try {
            var field = PatientEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(patient, PATIENT_ID);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return patient;
    }

    private PatientRecord patientRecord() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "P-001",
                "Amit",
                "Verma",
                PatientGender.MALE,
                null,
                null,
                "9876543210",
                "amit@example.com",
                null,
                null,
                "Pune",
                "Maharashtra",
                "India",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null
        );
    }
}
