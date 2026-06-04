package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalAuthProperties;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionPrincipal;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalRegistrationRequest;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PatientPortalRegistrationServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_APP_USER_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_APP_USER_ID = UUID.randomUUID();

    private PatientRepository patientRepository;
    private PatientService patientService;
    private AppUserProvisioner appUserProvisioner;
    private AppUserRepository appUserRepository;
    private PatientPortalRegistrationService service;

    @BeforeEach
    void setUp() {
        patientRepository = mock(PatientRepository.class);
        patientService = mock(PatientService.class);
        appUserProvisioner = mock(AppUserProvisioner.class);
        appUserRepository = mock(AppUserRepository.class);

        PatientPortalAuthProperties properties = new PatientPortalAuthProperties();
        properties.setSessionSecret("test-patient-portal-secret");
        PatientPortalSessionTokenService sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalRegistrationService(
                patientRepository,
                patientService,
                appUserProvisioner,
                appUserRepository,
                sessionTokenService
        );

        RequestContextHolder.set(new RequestContext(new TenantId(TENANT_ID), ACTOR_APP_USER_ID, "registration-sub", Set.of("PATIENT_REGISTRATION"), null, "corr-1"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new PatientPortalSessionPrincipal("registration-sub", TENANT_ID, null, "9876543210", "New patient", Set.of("PATIENT_REGISTRATION")),
                "token",
                List.of()
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsPatientWhenVerifiedMobileHasNoExistingRecord() {
        AppUserEntity patientAppUser = AppUserEntity.create(TENANT_ID, "patientportal:" + TENANT_ID + ":" + PATIENT_ID, null, "Riya Sharma");
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCase(TENANT_ID, "9876543210")).thenReturn(Optional.empty());
        when(patientService.create(eq(TENANT_ID), any(), eq(ACTOR_APP_USER_ID))).thenReturn(patientRecord());
        when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patientEntity(true)));
        when(appUserProvisioner.upsertAndReturnId(eq(TENANT_ID), eq("patientportal:" + TENANT_ID + ":" + PATIENT_ID), eq("riya@example.com"), eq("Riya Sharma")))
                .thenReturn(PATIENT_APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, PATIENT_APP_USER_ID)).thenReturn(Optional.of(patientAppUser));
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, ACTOR_APP_USER_ID)).thenReturn(Optional.of(AppUserEntity.create(TENANT_ID, "registration-sub", null, "New patient")));

        var response = service.complete(request());

        assertThat(response.created()).isTrue();
        assertThat(response.linkedExistingPatient()).isFalse();
        assertThat(response.patientSessionToken()).isNotBlank();
        verify(patientService).create(eq(TENANT_ID), any(), eq(ACTOR_APP_USER_ID));
    }

    @Test
    void linksExistingActivePatientWithoutCreatingDuplicate() {
        AppUserEntity patientAppUser = AppUserEntity.create(TENANT_ID, "patientportal:" + TENANT_ID + ":" + PATIENT_ID, null, "Riya Sharma");
        when(patientRepository.findFirstByTenantIdAndMobileIgnoreCase(TENANT_ID, "9876543210")).thenReturn(Optional.of(patientEntity(true)));
        when(appUserProvisioner.upsertAndReturnId(eq(TENANT_ID), eq("patientportal:" + TENANT_ID + ":" + PATIENT_ID), eq("riya@example.com"), eq("Riya Sharma")))
                .thenReturn(PATIENT_APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, PATIENT_APP_USER_ID)).thenReturn(Optional.of(patientAppUser));
        when(appUserRepository.findByTenantIdAndId(TENANT_ID, ACTOR_APP_USER_ID)).thenReturn(Optional.of(AppUserEntity.create(TENANT_ID, "registration-sub", null, "New patient")));

        var response = service.complete(request());

        assertThat(response.created()).isFalse();
        assertThat(response.linkedExistingPatient()).isTrue();
        verify(patientService, never()).create(eq(TENANT_ID), any(), eq(ACTOR_APP_USER_ID));
    }

    private PatientPortalRegistrationRequest request() {
        return new PatientPortalRegistrationRequest(
                "Riya",
                "Sharma",
                PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                null,
                "riya@example.com",
                "Line 1",
                null,
                "Pune",
                "MH",
                "India",
                "411001",
                "Emergency Contact",
                "9999999999"
        );
    }

    private PatientRecord patientRecord() {
        return new PatientRecord(
                PATIENT_ID,
                TENANT_ID,
                "PAT-001",
                "Riya",
                "Sharma",
                PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                32,
                "9876543210",
                "riya@example.com",
                "Line 1",
                null,
                "Pune",
                "MH",
                "India",
                "411001",
                "Emergency Contact",
                "9999999999",
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private PatientEntity patientEntity(boolean active) {
        PatientEntity patient = PatientEntity.create(TENANT_ID, "PAT-001");
        patient.update(
                "Riya",
                "Sharma",
                PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                32,
                "9876543210",
                "riya@example.com",
                "Line 1",
                null,
                "Pune",
                "MH",
                "India",
                "411001",
                "Emergency Contact",
                "9999999999",
                null,
                null,
                null,
                null,
                null,
                null,
                active
        );
        setField(patient, "id", PATIENT_ID);
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
