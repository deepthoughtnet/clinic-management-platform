package com.deepthoughtnet.clinic.api.vaccination;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.vaccination.document.GeneratedVaccinationDocumentResponse;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationCertificateService;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationDocumentChannelRequest;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationPassportService;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationReminderService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VaccinationDocumentControllerRouteTest {
    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void passportEndpointGeneratesPassportDocument() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", null, null, "vaccination-doc-test"));

        VaccinationPassportService passportService = mock(VaccinationPassportService.class);
        when(passportService.generatePassport(tenantId, patientId, actorId)).thenReturn(new GeneratedVaccinationDocumentResponse(
                UUID.randomUUID().toString(),
                "https://example.test/passport.pdf",
                "600",
                "passport.pdf",
                "Immunization Passport",
                "PASS-001",
                "2026-07-07T10:00:00Z",
                "Rohit Nair"
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationDocumentController(
                passportService,
                mock(VaccinationCertificateService.class),
                mock(VaccinationReminderService.class),
                mock(ClinicalDocumentService.class),
                mock(com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher.class)
        )).build();

        mockMvc.perform(post("/api/patients/{patientId}/vaccination-documents/passport", patientId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Immunization Passport"))
                .andExpect(jsonPath("$.documentNumber").value("PASS-001"))
                .andExpect(jsonPath("$.generatedBy").value("Rohit Nair"));
    }

    @Test
    void downloadEndpointStreamsStoredPdfBytes() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID documentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", null, null, "vaccination-doc-test"));

        VaccinationPassportService passportService = mock(VaccinationPassportService.class);
        when(passportService.downloadPdf(tenantId, documentId)).thenReturn("PDF".getBytes(StandardCharsets.UTF_8));

        ClinicalDocumentService clinicalDocumentService = mock(ClinicalDocumentService.class);
        when(clinicalDocumentService.get(tenantId, documentId)).thenReturn(new ClinicalDocumentRecord(
                documentId,
                tenantId,
                patientId,
                null,
                "VACCINATION",
                null,
                actorId,
                "Rohit Nair",
                ClinicalDocumentType.VACCINATION,
                "Immunization Passport",
                null,
                null,
                "RECEPTION",
                "passport.pdf",
                "application/pdf",
                8L,
                "checksum",
                "documents",
                "tenant/passport.pdf",
                "PATIENT_VISIBLE",
                "VERIFIED",
                "NOT_STARTED",
                "NOT_STARTED",
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                OffsetDateTime.parse("2026-07-07T10:00:00Z"),
                OffsetDateTime.parse("2026-07-07T10:00:00Z")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationDocumentController(
                passportService,
                mock(VaccinationCertificateService.class),
                mock(VaccinationReminderService.class),
                clinicalDocumentService,
                mock(com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher.class)
        )).build();

        mockMvc.perform(get("/api/patients/{patientId}/vaccination-documents/{documentId}/pdf", patientId, documentId)
                        .param("mode", "DOWNLOAD"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(content().bytes("PDF".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void reminderQueueEndpointReturnsQueuedCount() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", null, null, "vaccination-doc-test"));

        VaccinationReminderService reminderService = mock(VaccinationReminderService.class);
        when(reminderService.queuePatientReminders(tenantId, patientId, actorId)).thenReturn(2);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationDocumentController(
                mock(VaccinationPassportService.class),
                mock(VaccinationCertificateService.class),
                reminderService,
                mock(ClinicalDocumentService.class),
                mock(com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher.class)
        )).build();

        mockMvc.perform(post("/api/patients/{patientId}/vaccination-documents/reminders/queue", patientId))
                .andExpect(status().isAccepted())
                .andExpect(content().string("2"));
    }

    @Test
    void sendEndpointAcceptsChannelPayload() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID documentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", null, null, "vaccination-doc-test"));

        VaccinationPassportService passportService = mock(VaccinationPassportService.class);
        doNothing().when(passportService).sendDocument(tenantId, documentId, "EMAIL", actorId);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccinationDocumentController(
                passportService,
                mock(VaccinationCertificateService.class),
                mock(VaccinationReminderService.class),
                mock(ClinicalDocumentService.class),
                mock(com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher.class)
        )).build();

        mockMvc.perform(post("/api/patients/{patientId}/vaccination-documents/{documentId}/send", patientId, documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isNoContent());
    }
}
