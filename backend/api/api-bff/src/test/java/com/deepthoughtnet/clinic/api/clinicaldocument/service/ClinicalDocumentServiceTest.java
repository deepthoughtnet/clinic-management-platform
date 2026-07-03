package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
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
}
