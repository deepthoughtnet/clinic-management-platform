package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateEntity;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateRepository;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateSettings;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ResponseStatusException;

class PrescriptionTemplateServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private PrescriptionTemplateRepository repository;
    private AuditEventPublisher auditEventPublisher;
    private ClinicalDocumentService clinicalDocumentService;
    private ObjectStorageService objectStorageService;
    private PrescriptionTemplateService service;
    private final List<PrescriptionTemplateEntity> history = new ArrayList<>();
    private PrescriptionTemplateEntity active;

    @BeforeEach
    void setUp() {
        repository = mock(PrescriptionTemplateRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        clinicalDocumentService = mock(ClinicalDocumentService.class);
        objectStorageService = mock(ObjectStorageService.class);
        service = new PrescriptionTemplateService(repository, auditEventPublisher, clinicalDocumentService, objectStorageService);

        when(repository.findByTenantIdAndActiveTrue(TENANT_ID)).thenAnswer(invocation -> active == null ? List.of() : List.of(active));
        when(repository.findByTenantIdOrderByTemplateVersionDesc(TENANT_ID)).thenAnswer(invocation -> history.stream()
                .sorted((left, right) -> Integer.compare(right.getTemplateVersion(), left.getTemplateVersion()))
                .toList());
        when(repository.save(any(PrescriptionTemplateEntity.class))).thenAnswer(invocation -> {
            PrescriptionTemplateEntity entity = invocation.getArgument(0);
            history.add(entity);
            if (entity.isActive()) {
                active = entity;
            }
            return entity;
        });
    }

    @Test
    void uploadLogoPersistsDocumentReferenceAndVersionHistory() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clinic-logo.png", "image/png", pngBytes());
        when(clinicalDocumentService.upload(any())).thenReturn(clinicalDocumentRecord(DOCUMENT_ID, TENANT_ID, ACTOR_ID, "clinic-logo.png", "image/png"));

        PrescriptionTemplateRecord saved = service.uploadLogo(TENANT_ID, ACTOR_ID, file);

        assertThat(saved.clinicLogoDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(saved.templateVersion()).isEqualTo(1);
        assertThat(saved.active()).isTrue();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getClinicLogoDocumentId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void removeLogoCreatesNewVersionWithNullReference() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "clinic-logo.png", "image/png", pngBytes());
        when(clinicalDocumentService.upload(any())).thenReturn(clinicalDocumentRecord(DOCUMENT_ID, TENANT_ID, ACTOR_ID, "clinic-logo.png", "image/png"));
        service.uploadLogo(TENANT_ID, ACTOR_ID, file);

        PrescriptionTemplateRecord removed = service.removeLogo(TENANT_ID, ACTOR_ID);

        assertThat(removed.clinicLogoDocumentId()).isNull();
        assertThat(removed.templateVersion()).isEqualTo(2);
        assertThat(removed.active()).isTrue();
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getClinicLogoDocumentId()).isNull();
    }

    @Test
    void uploadLogoRejectsInvalidMimeType() {
        MockMultipartFile file = new MockMultipartFile("file", "clinic-logo.txt", "text/plain", "not an image".getBytes());

        assertThatThrownBy(() -> service.uploadLogo(TENANT_ID, ACTOR_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Logo must be PNG, JPG, JPEG, or WEBP");
    }

    @Test
    void uploadLogoRejectsOversizedFiles() {
        byte[] bytes = new byte[(2 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "clinic-logo.png", "image/png", bytes);

        assertThatThrownBy(() -> service.uploadLogo(TENANT_ID, ACTOR_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("2 MB or smaller");
    }

    @Test
    void uploadLogoRejectsEmptyFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "clinic-logo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.uploadLogo(TENANT_ID, ACTOR_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Logo file is required");
    }

    @Test
    void resolveReturnsEmptyForMissingLogoReference() {
        when(clinicalDocumentService.get(TENANT_ID, DOCUMENT_ID)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Document not found"));

        assertThat(service.resolve(TENANT_ID, DOCUMENT_ID)).isEmpty();
    }

    @Test
    void resolvePropagatesDatabaseFailuresWithoutRollingBackTheCaller() {
        when(clinicalDocumentService.get(TENANT_ID, DOCUMENT_ID)).thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> service.resolve(TENANT_ID, DOCUMENT_ID))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("database unavailable");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static ClinicalDocumentRecord clinicalDocumentRecord(UUID id, UUID tenantId, UUID uploadedBy, String fileName, String mediaType) {
        return new ClinicalDocumentRecord(
                id,
                tenantId,
                tenantId,
                null,
                "PRESCRIPTION_TEMPLATE",
                "branding-logo",
                uploadedBy,
                "Clinic Admin",
                ClinicalDocumentType.OTHER,
                "Clinic logo",
                null,
                null,
                "OTHER",
                fileName,
                mediaType,
                128L,
                "checksum",
                "bucket",
                "storage-key",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "NOT_STARTED",
                "NOT_STARTED",
                "COMPLETED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                null,
                true,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
