package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import java.time.LocalDate;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClinicalDocumentServiceTest {
    @Test
    void rejectsExecutableDocumentUploadsBeforeStorageWrite() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentService service = new ClinicalDocumentService(repository, storageService, appUserRepository, auditEventPublisher, "clinic-documents");

        assertThatThrownBy(() -> service.upload(new ClinicalDocumentUploadCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.OTHER,
                "Payload",
                null,
                "OTHER",
                null,
                null,
                "INTERNAL_ONLY",
                "payload.exe",
                "application/x-msdownload",
                new byte[] {1, 2, 3},
                null
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Executable files are not allowed");

        verifyNoInteractions(repository, storageService, auditEventPublisher, appUserRepository);
    }

    @Test
    void publishLabReportReusesExistingPublishedDocumentReference() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentService service = new ClinicalDocumentService(repository, storageService, appUserRepository, auditEventPublisher, "clinic-documents");

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ClinicalDocumentEntity existing = mock(ClinicalDocumentEntity.class);
        when(existing.getId()).thenReturn(UUID.randomUUID());
        when(existing.getDocumentType()).thenReturn(ClinicalDocumentType.LAB_REPORT);
        when(existing.getVisibility()).thenReturn("PATIENT_VISIBLE");
        when(existing.getVerificationStatus()).thenReturn("PUBLISHED");
        when(existing.getOcrStatus()).thenReturn("COMPLETED");
        when(existing.getAiIndexStatus()).thenReturn("COMPLETED");
        when(existing.getStorageKey()).thenReturn("tenant/documents/existing.pdf");
        when(repository.findFirstByTenantIdAndSourceModuleAndSourceEntityIdAndDocumentTypeAndActiveTrueOrderByCreatedAtDesc(
                tenantId,
                "LABORATORY",
                "order-123",
                ClinicalDocumentType.LAB_REPORT
        )).thenReturn(Optional.of(existing));
        when(repository.save(any(ClinicalDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.buildPatientDocumentStorageKey(any(), any(), any(), anyString())).thenReturn("tenant/patients/documents/repaired.pdf");

        var record = service.publishLabReport(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                UUID.randomUUID(),
                actorId,
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                LocalDate.parse("2026-07-05"),
                "LABORATORY",
                "LABORATORY",
                "order-123",
                "PATIENT_VISIBLE",
                "report.pdf",
                "application/pdf",
                new byte[] {1, 2, 3, 4},
                "Published after verification"
        ));

        verify(storageService, times(1)).putObject("tenant/documents/existing.pdf", "application/pdf", new byte[] {1, 2, 3, 4});
        verify(repository, times(1)).save(existing);
        org.assertj.core.api.Assertions.assertThat(record.id()).isEqualTo(existing.getId());
        org.assertj.core.api.Assertions.assertThat(record.documentType()).isEqualTo(ClinicalDocumentType.LAB_REPORT);
        org.assertj.core.api.Assertions.assertThat(record.visibility()).isEqualTo("PATIENT_VISIBLE");
        org.assertj.core.api.Assertions.assertThat(record.verificationStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishLabReportRepairsInvalidExistingStorageKey() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentService service = new ClinicalDocumentService(repository, storageService, appUserRepository, auditEventPublisher, "clinic-documents");

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        ClinicalDocumentEntity existing = ClinicalDocumentEntity.create(
                existingId,
                tenantId,
                patientId,
                UUID.randomUUID(),
                null,
                actorId,
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                "Published after verification",
                LocalDate.parse("2026-07-05"),
                "Doctor One",
                "LABORATORY",
                "report.pdf",
                "application/pdf",
                4L,
                "clinic-documents",
                "tenant/patients/documents/original.pdf",
                "checksum",
                "PATIENT_VISIBLE",
                "PUBLISHED",
                "COMPLETED",
                "COMPLETED",
                "LABORATORY",
                "order-123",
                actorId,
                actorId
        );
        setField(existing, "storageObjectKey", "undefined");
        when(repository.findFirstByTenantIdAndSourceModuleAndSourceEntityIdAndDocumentTypeAndActiveTrueOrderByCreatedAtDesc(
                tenantId,
                "LABORATORY",
                "order-123",
                ClinicalDocumentType.LAB_REPORT
        )).thenReturn(Optional.of(existing));
        when(repository.save(any(ClinicalDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var record = service.publishLabReport(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                UUID.randomUUID(),
                actorId,
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                LocalDate.parse("2026-07-05"),
                "LABORATORY",
                "LABORATORY",
                "order-123",
                "PATIENT_VISIBLE",
                "report.pdf",
                "application/pdf",
                new byte[] {1, 2, 3, 4},
                "Published after verification"
        ));

        verify(storageService, times(1)).putObject(anyString(), org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.eq(new byte[] {1, 2, 3, 4}));
        verify(repository, times(1)).save(existing);
        org.assertj.core.api.Assertions.assertThat(record.storageKey()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(record.sizeBytes()).isEqualTo(4L);
        org.assertj.core.api.Assertions.assertThat(record.storageKey()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(record.storageKey()).doesNotContain("undefined");
    }

    @Test
    void downloadUrlRejectsMissingOrInvalidStorageKey() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentService service = new ClinicalDocumentService(repository, storageService, appUserRepository, auditEventPublisher, "clinic-documents");

        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ClinicalDocumentEntity document = mock(ClinicalDocumentEntity.class);
        when(repository.findByTenantIdAndIdAndActiveTrue(tenantId, documentId)).thenReturn(Optional.of(document));
        when(document.getStorageObjectKey()).thenReturn("undefined");

        assertThatThrownBy(() -> service.downloadUrl(tenantId, documentId, java.time.Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage key");
        verifyNoInteractions(storageService);
    }

    @Test
    void downloadUrlRepairsPublishedLabDocumentStorageKeyAndSizeOnRead() {
        ClinicalDocumentRepository repository = mock(ClinicalDocumentRepository.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ClinicalDocumentService service = new ClinicalDocumentService(repository, storageService, appUserRepository, auditEventPublisher, "clinic-documents");

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ClinicalDocumentEntity document = ClinicalDocumentEntity.create(
                documentId,
                tenantId,
                patientId,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.LAB_REPORT,
                "Lab Report",
                "Published after verification",
                LocalDate.parse("2026-07-05"),
                "Doctor One",
                "LABORATORY",
                "report.pdf",
                "application/pdf",
                0L,
                "clinic-documents",
                "undefined",
                "checksum",
                "PATIENT_VISIBLE",
                "PUBLISHED",
                "COMPLETED",
                "COMPLETED",
                "LABORATORY",
                "order-123",
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        when(repository.findByTenantIdAndIdAndActiveTrue(tenantId, documentId)).thenReturn(Optional.of(document));
        when(repository.save(any(ClinicalDocumentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.statObjectSize("tenant/" + tenantId + "/patients/" + patientId + "/documents/" + documentId + "/report.pdf"))
                .thenReturn(8L);
        when(storageService.getObjectBytes("tenant/" + tenantId + "/patients/" + patientId + "/documents/" + documentId + "/report.pdf"))
                .thenReturn(new byte[] {37, 80, 68, 70, 45, 49, 46, 52});
        when(storageService.generatePresignedDownloadUrl(anyString(), any())).thenReturn("https://example.test/lab-report.pdf");

        String url = service.downloadUrl(tenantId, documentId, java.time.Duration.ofMinutes(10));

        verify(storageService).statObjectSize("tenant/" + tenantId + "/patients/" + patientId + "/documents/" + documentId + "/report.pdf");
        verify(storageService).generatePresignedDownloadUrl("tenant/" + tenantId + "/patients/" + patientId + "/documents/" + documentId + "/report.pdf", java.time.Duration.ofMinutes(10));
        verify(repository).save(document);
        org.assertj.core.api.Assertions.assertThat(url).isEqualTo("https://example.test/lab-report.pdf");
        org.assertj.core.api.Assertions.assertThat(document.getStorageKey()).doesNotContain("undefined");
        org.assertj.core.api.Assertions.assertThat(document.getSizeBytes()).isEqualTo(8L);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set test field " + fieldName, ex);
        }
    }
}
