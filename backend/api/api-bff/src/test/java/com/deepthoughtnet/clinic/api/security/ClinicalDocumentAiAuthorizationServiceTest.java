package com.deepthoughtnet.clinic.api.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClinicalDocumentAiAuthorizationServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void clinicAdminCanRepairMemory() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);
        ClinicalDocumentEntity document = document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, null);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "admin@jfcuat.local", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-auth-1"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN")).thenReturn(true);

        assertThatCode(() -> service.requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void clinicAdminCanReprocessAi() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);
        ClinicalDocumentEntity document = document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, null);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "admin@jfcuat.local", Set.of("ROLE_CLINIC_ADMIN"), "ROLE_CLINIC_ADMIN", "corr-auth-2"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN")).thenReturn(true);

        assertThatCode(() -> service.requireReprocessAccess(TENANT_ID, DOCUMENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void receptionistCannotRepairMemory() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);
        ClinicalDocumentEntity document = document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, null);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "reception@jfcuat.local", Set.of("RECEPTIONIST"), "RECEPTIONIST", "corr-auth-3"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("PLATFORM_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("DOCTOR")).thenReturn(false);
        when(permissionChecker.hasRole("RECEPTIONIST")).thenReturn(true);

        assertThatThrownBy(() -> service.requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("RECEPTIONIST is not authorized for document AI repair");
    }

    @Test
    void wrongTenantReturnsForbidden() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "admin@jfcuat.local", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-auth-4"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.empty());
        when(repository.existsById(DOCUMENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.requireReprocessAccess(TENANT_ID, DOCUMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("CLINIC_ADMIN is not authorized for document AI reprocess because selected tenant does not own this document");
    }

    @Test
    void missingTenantReturnsBadRequest() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);

        RequestContextHolder.set(new RequestContext(null, USER_ID, "admin@jfcuat.local", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-auth-5"));

        assertThatThrownBy(() -> service.requireRepairMemoryAccess(null, DOCUMENT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void doctorCanReprocessOwnConsultationDocument() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);
        UUID consultationId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, consultationId);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "doctor@jfcuat.local", Set.of("DOCTOR"), "DOCTOR", "corr-auth-6"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("PLATFORM_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("DOCTOR")).thenReturn(true);

        assertThatCode(() -> service.requireReprocessAccess(TENANT_ID, DOCUMENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void doctorDeniedWhenNotAssigned() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        DoctorAssignmentSecurityService doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        ClinicalDocumentAiAuthorizationService service = new ClinicalDocumentAiAuthorizationService(repository, permissionChecker, doctorAssignmentSecurityService);
        UUID consultationId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(TENANT_ID, PATIENT_ID, DOCUMENT_ID, consultationId);

        RequestContextHolder.set(new RequestContext(TenantId.of(TENANT_ID), USER_ID, "doctor@jfcuat.local", Set.of("DOCTOR"), "DOCTOR", "corr-auth-7"));
        when(repository.findByTenantIdAndId(TENANT_ID, DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "TENANT_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("PLATFORM_ADMIN")).thenReturn(false);
        when(permissionChecker.hasRole("DOCTOR")).thenReturn(true);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Consultation is not assigned to this doctor"))
                .when(doctorAssignmentSecurityService).requireConsultationAccess(TENANT_ID, consultationId);

        assertThatThrownBy(() -> service.requireRepairMemoryAccess(TENANT_ID, DOCUMENT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("DOCTOR is not authorized for document AI repair because Consultation is not assigned to this doctor");
    }

    private ClinicalDocumentEntity document(UUID tenantId, UUID patientId, UUID documentId, UUID consultationId) {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                tenantId,
                patientId,
                consultationId,
                null,
                USER_ID,
                ClinicalDocumentType.LAB_REPORT,
                "report.pdf",
                "application/pdf",
                120L,
                "checksum",
                "storage-key",
                "notes",
                null,
                null,
                null
        );
        try {
            java.lang.reflect.Field idField = ClinicalDocumentEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, documentId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
