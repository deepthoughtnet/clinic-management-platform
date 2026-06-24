package com.deepthoughtnet.clinic.api.patientportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PatientPortalOtpServiceTest {
    private static final UUID TENANT_A_ID = UUID.randomUUID();
    private static final UUID TENANT_B_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();

    private TenantRepository tenantRepository;
    private PatientRepository patientRepository;
    private AppUserProvisioner appUserProvisioner;
    private AppUserRepository appUserRepository;
    private PatientPortalOtpChallengeRepository challengeRepository;
    private PatientPortalSessionTokenService sessionTokenService;
    private PatientPortalAuthProperties properties;
    private PatientPortalOtpService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        patientRepository = mock(PatientRepository.class);
        appUserProvisioner = mock(AppUserProvisioner.class);
        appUserRepository = mock(AppUserRepository.class);
        challengeRepository = mock(PatientPortalOtpChallengeRepository.class);
        properties = new PatientPortalAuthProperties();
        properties.setExposeDevOtp(true);
        properties.setSessionSecret("test-patient-portal-secret");
        sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalOtpService(
                tenantRepository,
                patientRepository,
                appUserProvisioner,
                appUserRepository,
                challengeRepository,
                sessionTokenService,
                properties
        );
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Optional.of(tenant("tenant-a", TENANT_A_ID)));
        when(tenantRepository.findByCode("tenant-b")).thenReturn(Optional.of(tenant("tenant-b", TENANT_B_ID)));
        when(tenantRepository.findByCode("demo-clinic")).thenReturn(Optional.of(tenant("demo-clinic", TENANT_B_ID)));
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(challengeRepository.save(any(PatientPortalOtpChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestOtpExposesDevOtpOnlyWhenEnabled() {
        var response = service.requestOtp("tenant-a", "+91 98765 43210");

        assertThat(response.accepted()).isTrue();
        assertThat(response.devOtp()).matches("\\d{6}");

        properties.setExposeDevOtp(false);
        sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalOtpService(
                tenantRepository,
                patientRepository,
                appUserProvisioner,
                appUserRepository,
                challengeRepository,
                sessionTokenService,
                properties
        );
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.empty());

        var hiddenResponse = service.requestOtp("tenant-a", "9876543210");

        assertThat(hiddenResponse.accepted()).isTrue();
        assertThat(hiddenResponse.devOtp()).isNull();
    }

    @Test
    void requestOtpAllowsPhoneOnlyWithoutContext() {
        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.empty());

        var response = service.requestOtp("9876543210", (PatientPortalOtpContext) null);

        assertThat(response.accepted()).isTrue();
        assertThat(response.devOtp()).matches("\\d{6}");
    }

    @Test
    void verifyOtpRejectsExpiredChallenges() throws Exception {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        setField(challenge, "expiresAt", OffsetDateTime.now().minusSeconds(5));
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("expired");
    }

    @Test
    void verifyOtpStopsAfterMaximumAttempts() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));

        for (int attempt = 0; attempt < 5; attempt++) {
            service.verifyOtp("tenant-a", "9876543210", "999999");
        }

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("attempts exceeded");
        assertThat(challenge.getAttempts()).isEqualTo(5);
    }

    @Test
    void verifyOtpIsTenantIsolated() {
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_B_ID, "9876543210"))
                .thenReturn(Optional.empty());

        var response = service.verifyOtp("tenant-b", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("Unable to verify patient access");
        verify(patientRepository, never()).findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(eq(TENANT_A_ID), any());
    }

    @Test
    void verifyOtpReturnsSafeMessageWhenNoPatientMatches() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.empty());

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isFalse();
        assertThat(response.registrationRequired()).isTrue();
        assertThat(response.registrationSessionToken()).isNotBlank();
        assertThat(response.message()).contains("Complete your quick registration");
    }

    @Test
    void verifyOtpFallsBackToDemoClinicRegistrationWhenNoTenantContextIsAvailable() {
        PatientPortalOtpChallengeEntity challenge = challenge(null, "9876543210", "123456");
        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByMobileIgnoreCaseAndActiveTrue("9876543210")).thenReturn(List.of());

        var response = service.verifyOtp("9876543210", "123456", (PatientPortalOtpContext) null);

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isFalse();
        assertThat(response.registrationRequired()).isTrue();
        assertThat(response.tenantId()).isEqualTo(TENANT_B_ID.toString());
        assertThat(response.tenantCode()).isEqualTo("demo-clinic");
        assertThat(response.registrationSessionToken()).isNotBlank();
        assertThat(response.message()).contains("Complete your quick registration");
    }

    @Test
    void verifyOtpIssuesPatientSessionForMatchedPatient() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + PATIENT_ID, null, "Riya Sharma");

        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(patient));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.tenantId()).isEqualTo(TENANT_A_ID.toString());
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
        assertThat(response.patientSessionToken()).isNotBlank();
        assertThat(response.registrationSessionToken()).isNull();
        assertThat(appUser.getPatientId()).isEqualTo(PATIENT_ID);
    }

    @Test
    void verifyOtpResolvesTenantFromUniquePatientWhenContextIsMissing() {
        PatientPortalOtpChallengeEntity challenge = challenge(null, "9876543210", "123456");
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + PATIENT_ID, null, "Riya Sharma");

        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByMobileIgnoreCaseAndActiveTrue("9876543210")).thenReturn(List.of(patient));
        when(tenantRepository.findById(TENANT_A_ID)).thenReturn(Optional.of(tenant("tenant-a", TENANT_A_ID)));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("9876543210", "123456", (PatientPortalOtpContext) null);

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.tenantId()).isEqualTo(TENANT_A_ID.toString());
        assertThat(response.tenantCode()).isEqualTo("tenant-a");
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
        assertThat(response.patientSessionToken()).isNotBlank();
    }

    private TenantEntity tenant(String code, UUID id) {
        TenantEntity tenant = TenantEntity.create(code, "Tenant " + code, "TRIAL");
        setField(tenant, "id", id);
        return tenant;
    }

    private PatientPortalOtpChallengeEntity challenge(UUID tenantId, String phone, String otp) {
        return PatientPortalOtpChallengeEntity.create(
                tenantId,
                phone,
                new BCryptPasswordEncoder().encode(otp),
                OffsetDateTime.now().plusMinutes(5)
        );
    }

    private PatientEntity patientEntity(UUID tenantId, UUID patientId, String mobile) {
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-001");
        patient.update(
                "Riya",
                "Sharma",
                com.deepthoughtnet.clinic.patient.service.model.PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                32,
                mobile,
                "riya@example.com",
                "Line 1",
                null,
                "Pune",
                "MH",
                "India",
                "411001",
                "Emergency",
                "8888888888",
                "O+",
                "Peanuts",
                "Asthma",
                "Inhaler",
                "Appendectomy",
                null,
                true
        );
        setField(patient, "id", patientId);
        return patient;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
