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
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID OTHER_PATIENT_ID = UUID.randomUUID();
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
        when(tenantRepository.findById(OTHER_TENANT_ID)).thenReturn(Optional.of(tenant("automation-lab", OTHER_TENANT_ID)));
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
    void submitRejectsAlreadyApprovedRequest() {
        PatientPortalAccessRequestEntity existing = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        existing.approve(UUID.randomUUID(), "Platform Admin", PATIENT_ID, "Amit Verma");
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submit(new PatientPortalAccessRequestCommand(
                "Amit Verma",
                "9876543210",
                null,
                null,
                new PatientPortalAccessContext(null, null, TENANT_ID.toString(), null, null)
        ))).isInstanceOf(PatientPortalAccessRequestConflictException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void submitRejectsAlreadyActiveRequest() {
        PatientPortalAccessRequestEntity existing = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        existing.approve(UUID.randomUUID(), "Platform Admin", PATIENT_ID, "Amit Verma");
        existing.activate();
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submit(new PatientPortalAccessRequestCommand(
                "Amit Verma",
                "9876543210",
                null,
                null,
                new PatientPortalAccessContext(null, null, TENANT_ID.toString(), null, null)
        ))).isInstanceOf(PatientPortalAccessRequestConflictException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void submitAllowsNewRequestAfterRevocation() {
        PatientPortalAccessRequestEntity revoked = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        revoked.revoke(UUID.randomUUID(), "Platform Admin", "Revoked for test");
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(revoked));
        when(requestRepository.save(any(PatientPortalAccessRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var record = service.submit(new PatientPortalAccessRequestCommand(
                "Amit Verma",
                "9876543210",
                null,
                null,
                new PatientPortalAccessContext(null, null, TENANT_ID.toString(), null, null)
        ));

        assertThat(record.status()).isEqualTo(PatientPortalAccessRequestStatus.REQUESTED);
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

    @Test
    void authenticateRejectsExpiredAccessCodes() {
        PatientPortalAccessRequestEntity request = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        request.approve(UUID.randomUUID(), "Platform Admin", PATIENT_ID, "Amit Verma");
        request.attachAccessCode(new BCryptPasswordEncoder().encode("12345678"), request.getCreatedAt(), request.getCreatedAt().minusDays(1));
        when(requestRepository.findAll()).thenReturn(List.of(request));
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.authenticate(null, "9876543210", "12345678", null))
                .isInstanceOf(PatientPortalAccessRequestConflictException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void listAuthorizedClinicsReturnsOnlyActiveTenantContexts() {
        PatientPortalAccessRequestEntity demo = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        demo.approve(UUID.randomUUID(), "Platform Admin", PATIENT_ID, "Amit Verma");
        demo.activate();
        PatientPortalAccessRequestEntity automation = PatientPortalAccessRequestEntity.create(OTHER_TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        automation.approve(UUID.randomUUID(), "Platform Admin", OTHER_PATIENT_ID, "Amit Verma");
        automation.activate();
        PatientPortalAccessRequestEntity revoked = PatientPortalAccessRequestEntity.create(UUID.randomUUID(), "Amit Verma", "9876543210", "9876543210", null, null);
        revoked.revoke(UUID.randomUUID(), "Platform Admin", "Revoked for test");
        when(requestRepository.findByMobileNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(List.of(demo, automation, revoked));
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient()));
        when(patientRepository.findByTenantIdAndId(OTHER_TENANT_ID, OTHER_PATIENT_ID)).thenReturn(Optional.of(otherPatient()));

        var clinics = service.listAuthorizedClinics("9876543210");

        assertThat(clinics).hasSize(2);
        assertThat(clinics).extracting("tenantId").containsExactly(TENANT_ID, OTHER_TENANT_ID);
        assertThat(clinics).extracting("authorizationSource").containsOnly("PATIENT_PORTAL_ACCESS_REQUEST");
        assertThat(clinics).allSatisfy(record -> assertThat(record.active()).isTrue());
        verify(patientRepository).findByTenantIdAndId(TENANT_ID, PATIENT_ID);
        verify(patientRepository).findByTenantIdAndId(OTHER_TENANT_ID, OTHER_PATIENT_ID);
    }

    @Test
    void findLatestByTenantAndMobileReturnsLatestRequestState() {
        PatientPortalAccessRequestEntity revoked = PatientPortalAccessRequestEntity.create(TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        revoked.revoke(UUID.randomUUID(), "Platform Admin", "Revoked for test");
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(revoked));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant("jeevanam-preview", TENANT_ID)));

        var record = service.findLatestByTenantAndMobile(TENANT_ID, "9876543210");

        assertThat(record).isPresent();
        assertThat(record.orElseThrow().status()).isEqualTo(PatientPortalAccessRequestStatus.REVOKED);
        assertThat(record.orElseThrow().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void switchAuthorizedClinicReturnsGrantForActiveTenantContext() {
        PatientPortalAccessRequestEntity automation = PatientPortalAccessRequestEntity.create(OTHER_TENANT_ID, "Amit Verma", "9876543210", "9876543210", null, null);
        automation.approve(UUID.randomUUID(), "Platform Admin", OTHER_PATIENT_ID, "Amit Verma");
        automation.activate();
        when(requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(OTHER_TENANT_ID, "9876543210"))
                .thenReturn(Optional.of(automation));
        when(patientRepository.findByTenantIdAndId(OTHER_TENANT_ID, OTHER_PATIENT_ID)).thenReturn(Optional.of(otherPatient()));
        AppUserEntity appUser = AppUserEntity.create(OTHER_TENANT_ID, "patientportal:" + OTHER_TENANT_ID + ":" + OTHER_PATIENT_ID, "amit@example.com", "Amit Verma");
        when(appUserProvisioner.upsertAndReturnId(eq(OTHER_TENANT_ID), any(), eq("amit@example.com"), eq("Amit Verma"))).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(OTHER_TENANT_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var grant = service.switchAuthorizedClinic("9876543210", OTHER_TENANT_ID);

        assertThat(grant.tenantId()).isEqualTo(OTHER_TENANT_ID);
        assertThat(grant.patientId()).isEqualTo(OTHER_PATIENT_ID);
        assertThat(grant.patientDisplayName()).isEqualTo("Amit Verma");
        assertThat(grant.patientMobile()).isEqualTo("9876543210");
        verify(appUserProvisioner).upsertAndReturnId(eq(OTHER_TENANT_ID), any(), eq("amit@example.com"), eq("Amit Verma"));
        verify(appUserRepository).findByTenantIdAndId(OTHER_TENANT_ID, APP_USER_ID);
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
        return patient(TENANT_ID, PATIENT_ID, "P-001", "Amit", "Verma", "9876543210", "amit@example.com");
    }

    private PatientEntity otherPatient() {
        return patient(OTHER_TENANT_ID, OTHER_PATIENT_ID, "P-201", "Amit", "Verma", "9876543210", "amit@example.com");
    }

    private PatientEntity patient(UUID tenantId, UUID patientId, String patientNumber, String firstName, String lastName, String mobile, String email) {
        PatientEntity patient = PatientEntity.create(tenantId, patientNumber);
        patient.update(
                firstName,
                lastName,
                PatientGender.MALE,
                null,
                null,
                mobile,
                email,
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
            field.set(patient, patientId);
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
