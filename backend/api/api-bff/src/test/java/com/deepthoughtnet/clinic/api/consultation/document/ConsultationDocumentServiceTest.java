package com.deepthoughtnet.clinic.api.consultation.document;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationDocumentServiceTest {
    @Mock
    private ConsultationService consultationService;

    @Mock
    private ClinicalDocumentService clinicalDocumentService;

    @InjectMocks
    private ConsultationDocumentService service;

    @Test
    void generateStoresConsultationPackageAsClinicalDocument() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID consultationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID actorId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        ConsultationRecord consultation = new ConsultationRecord(
                consultationId,
                tenantId,
                patientId,
                "P-1001",
                "Test Patient",
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "Dr Test",
                null,
                "Consultation body",
                "Fever",
                "Viral fever",
                "Clinical notes",
                "Advice",
                LocalDate.now(ZoneOffset.UTC),
                ConsultationStatus.DRAFT,
                null,
                null,
                null,
                null,
                TemperatureUnit.CELSIUS,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        when(consultationService.findById(tenantId, consultationId)).thenReturn(Optional.of(consultation));

        ClinicalDocumentRecord savedDocument = new ClinicalDocumentRecord(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                tenantId,
                patientId,
                consultationId,
                "CONSULTATION",
                consultationId.toString(),
                actorId,
                "Dr Test",
                ClinicalDocumentType.ATTACHMENT,
                "Consultation Package",
                "Consultation summary body",
                LocalDate.now(ZoneOffset.UTC),
                "DOCTOR",
                "consultation-package.pdf",
                "application/pdf",
                2048L,
                "checksum",
                "documents",
                "tenant/consultation-package.pdf",
                "INTERNAL_ONLY",
                "VERIFIED",
                "NOT_STARTED",
                "NOT_STARTED",
                "NOT_STARTED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        when(clinicalDocumentService.upload(org.mockito.ArgumentMatchers.any(ClinicalDocumentUploadCommand.class))).thenReturn(savedDocument);
        when(clinicalDocumentService.downloadUrl(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(savedDocument.id()), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn("https://example.test/document.pdf");

        GeneratedConsultationDocumentResponse response = service.generate(
                tenantId,
                consultationId,
                new ConsultationGeneratedDocumentRequest(
                        "Consultation Package",
                        "ATTACHMENT",
                        "Consultation summary body",
                        "ENGLISH",
                        "Notes",
                        "INTERNAL_ONLY"
                ),
                actorId
        );

        assertNotNull(response);
        assertEquals(savedDocument.id().toString(), response.documentId());
        assertEquals("https://example.test/document.pdf", response.downloadUrl());
        assertEquals("Consultation Package", response.title());
        assertEquals("ATTACHMENT", response.documentType());

        ArgumentCaptor<ClinicalDocumentUploadCommand> captor = ArgumentCaptor.forClass(ClinicalDocumentUploadCommand.class);
        verify(clinicalDocumentService).upload(captor.capture());
        ClinicalDocumentUploadCommand uploadCommand = captor.getValue();
        assertEquals(tenantId, uploadCommand.tenantId());
        assertEquals(patientId, uploadCommand.patientId());
        assertEquals(consultationId, uploadCommand.consultationId());
        assertEquals(actorId, uploadCommand.uploadedByAppUserId());
        assertEquals(ClinicalDocumentType.ATTACHMENT, uploadCommand.documentType());
        assertEquals("Consultation Package", uploadCommand.title());
    }
}
