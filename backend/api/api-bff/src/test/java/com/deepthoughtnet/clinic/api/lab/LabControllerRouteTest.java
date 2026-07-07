package com.deepthoughtnet.clinic.api.lab;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigResponse;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderAttachmentRecord;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPaymentRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabPaymentReceiptRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;

class LabControllerRouteTest {

    private LabController controller(LabService labService, LabCsvService labCsvService, LabCatalogueConfigService labCatalogueConfigService) {
        return new LabController(labService, labCsvService, labCatalogueConfigService, mock(com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService.class));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void importTemplateUsesCsvNegotiationAndAttachmentHeaders() throws Exception {
        LabCsvService labCsvService = mock(LabCsvService.class);
        when(labCsvService.importTemplateCsv()).thenReturn("testCode,testName\nCBC,Complete Blood Count\n");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(mock(LabService.class), labCsvService, mock(LabCatalogueConfigService.class))).build();

        mockMvc.perform(get("/api/lab/tests/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("lab-tests-import-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("testCode,testName")));
    }

    @Test
    void listCategoryConfigUsesTenantScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LabCatalogueConfigService configService = mock(LabCatalogueConfigService.class);
        when(configService.listCategories(tenantId)).thenReturn(List.of(new LabCategoryConfigResponse("HEMATOLOGY", "Hematology", true, 1)));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(mock(LabService.class), mock(LabCsvService.class), configService)).build();

        mockMvc.perform(get("/api/lab/config/categories"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HEMATOLOGY")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hematology")));
    }

    @Test
    void createConsultationOrderAcceptsPatientIdTestIdsAndNotes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        when(labService.createOrderFromConsultation(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(consultationId),
                org.mockito.ArgumentMatchers.any(LabOrderCreateCommand.class),
                org.mockito.ArgumentMatchers.eq(actorId)
        )).thenReturn(sampleOrderRecord(tenantId, consultationId, patientId, UUID.randomUUID()));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/consultations/{consultationId}/orders", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "patientId", patientId,
                                "testIds", List.of(testId),
                                "notes", "Consultation request"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void createConsultationOrderReturnsFieldValidationForMissingPatientId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(mock(LabService.class), mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/consultations/{consultationId}/orders", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "testIds", List.of(testId),
                                "notes", "Consultation request"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("patientId")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Patient is required")));
    }

    @Test
    void collectPaymentReturnsReceiptMetadataInLabResponse() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        LabOrderRecord order = sampleOrderRecord(tenantId, UUID.randomUUID(), UUID.randomUUID(), billId);
        PaymentRecord payment = new PaymentRecord(
                paymentId,
                tenantId,
                billId,
                java.time.LocalDate.parse("2026-07-05"),
                java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z"),
                java.math.BigDecimal.valueOf(450),
                PaymentMode.CASH,
                null,
                null,
                actorId,
                receiptId,
                "REC-0001",
                java.time.LocalDate.parse("2026-07-05"),
                java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z")
        );
        when(labService.collectPayment(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(orderId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(actorId)
        )).thenReturn(new LabOrderPaymentRecord(order, payment));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/orders/{id}/payments", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "amount", 450,
                                "paymentMode", "CASH",
                                "receivedBy", actorId.toString()
                        ))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REC-0001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(receiptId.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(paymentId.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"paymentMode\":\"CASH\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"paymentReceipt\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"receiptNumber\":\"REC-0001\"")));
    }

    @Test
    void collectSamplesIgnoresClientCollectorEmailAndUsesAuthenticatedUser() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        when(labService.collectSamples(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(orderId),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(actorId)
        )).thenReturn(List.of());
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("LAB_TECHNICIAN"), "LAB_TECHNICIAN", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/orders/{orderId}/samples/collect", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "samples", List.of(Map.of(
                                        "labOrderItemId", orderItemId,
                                        "specimenType", "Blood",
                                        "containerType", "EDTA",
                                        "collectedAt", "2026-07-05T10:15:30Z",
                                        "collectedBy", "rohit.nair@jfcuat.local",
                                        "notes", "Collected at reception"
                                ))
                        ))))
                .andExpect(status().isOk());

        ArgumentCaptor<List<com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(labService).collectSamples(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(orderId),
                captor.capture(),
                org.mockito.ArgumentMatchers.eq(actorId)
        );
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().specimenType()).isEqualTo("Blood");
    }

    @Test
    void listOrdersIncludesPaymentReceiptSummaryForPaidLabOrders() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        LabOrderRecord order = sampleOrderRecord(tenantId, UUID.randomUUID(), UUID.randomUUID());
        when(labService.listOrders(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(List.of(order));
        when(labService.resolvePaymentReceipt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.Optional.of(new LabPaymentReceiptRecord(
                receiptId,
                "REC-0002",
                billId,
                "BILL-70A88C7D62",
                java.math.BigDecimal.valueOf(450),
                PaymentMode.UPI,
                "UPI-REF-22",
                actorId.toString(),
                java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z"),
                "/api/receipts/" + receiptId + "/pdf",
                "/api/receipts/" + receiptId + "/pdf"
        )));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(get("/api/lab/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("paymentReceipt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REC-0002")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UPI-REF-22")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"paymentMode\":\"UPI\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"collectedBy\":\"" + actorId + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"billNumber\":\"BILL-70A88C7D62\"")));
    }

    @Test
    void listOrdersIncludesReportDeliveryHistory() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService auditQueryService = mock(com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService.class);
        LabOrderRecord order = sampleOrderRecord(tenantId, UUID.randomUUID(), UUID.randomUUID());
        when(labService.listOrders(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(List.of(order));
        when(auditQueryService.listForEntity(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq("LAB_ORDER"),
                org.mockito.ArgumentMatchers.eq(order.id())
        )).thenReturn(List.of(new com.deepthoughtnet.clinic.platform.audit.AuditEventRecord(
                UUID.randomUUID(),
                tenantId,
                "LAB_ORDER",
                order.id(),
                "lab_order.report_email_sent",
                actorId,
                java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z"),
                "Email sent",
                "{\"channel\":\"EMAIL\"}"
        )));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LabController(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class), auditQueryService))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(get("/api/lab/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reportDeliveryHistory")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email sent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("lab_order.report_email_sent")));
    }

    @Test
    void downloadPdfReturnsPdfContent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        when(labService.generateReportPdf(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(orderId),
                org.mockito.ArgumentMatchers.eq(actorId)
        )).thenReturn(new LabOrderResultPdf("lab-report.pdf", new byte[] {37, 80, 68, 70, 45, 49, 46, 52}));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(get("/api/lab/orders/{id}/pdf", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(content().bytes(new byte[] {37, 80, 68, 70, 45, 49, 46, 52}));
    }

    @Test
    void attachmentViewStreamsStoredBytesWithInlineDisposition() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        when(labService.findOrder(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(orderId)))
                .thenReturn(java.util.Optional.of(sampleOrderRecord(tenantId, UUID.randomUUID(), UUID.randomUUID())));
        when(labService.findAttachment(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.eq(attachmentId)))
                .thenReturn(java.util.Optional.of(new LabOrderAttachmentRecord(
                        attachmentId,
                        orderId,
                        "REPORT_ATTACHMENT",
                        "scan.pdf",
                        "application/pdf",
                        "clinic-documents/tenant/demo/scan.pdf",
                        8L,
                        "abc123",
                        null,
                        actorId,
                        java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z")
                )));
        when(labService.downloadAttachmentBytes(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.eq(attachmentId)))
                .thenReturn(new byte[] {37, 80, 68, 70, 45, 49, 46, 52});
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(get("/api/lab/orders/{id}/attachments/{attachmentId}/view", orderId, attachmentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.containsString("application/pdf")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("inline")))
                .andExpect(content().bytes(new byte[] {37, 80, 68, 70, 45, 49, 46, 52}));
    }

    private LabOrderRecord sampleOrderRecord(UUID tenantId, UUID consultationId, UUID patientId) {
        return sampleOrderRecord(tenantId, consultationId, patientId, UUID.randomUUID());
    }

    private LabOrderRecord sampleOrderRecord(UUID tenantId, UUID consultationId, UUID patientId, UUID billId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID());
        data.put("tenantId", tenantId);
        data.put("orderNumber", "LAB-20260703-0001");
        data.put("patientId", patientId);
        data.put("patientNumber", "PAT-001");
        data.put("patientName", "Demo Patient");
        data.put("consultationId", consultationId);
        data.put("orderOrigin", "CONSULTATION");
        data.put("notes", "Consultation request");
        data.put("status", "ORDERED");
        data.put("billId", billId);
        data.put("billNumber", "BILL-70A88C7D62");
        data.put("billDueAmount", java.math.BigDecimal.ZERO);
        data.put("paymentCollectedAt", java.time.OffsetDateTime.parse("2026-07-05T10:15:30Z"));
        data.put("attachments", List.of());
        data.put("items", List.of());
        data.put("samples", List.of());
        data.put("results", List.of());
        return new ObjectMapper().findAndRegisterModules().convertValue(data, LabOrderRecord.class);
    }
}
