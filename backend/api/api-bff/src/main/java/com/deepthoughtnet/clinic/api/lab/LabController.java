package com.deepthoughtnet.clinic.api.lab;

import com.deepthoughtnet.clinic.api.lab.dto.LabOrderCreateRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderDirectCreateRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderDoctorReviewRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabCsvImportResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderItemResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderPaymentRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderPublishReportRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderResultRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderResultResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderSamplesCollectRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderSampleCollectionRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabOrderVerificationRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabSampleCollectionRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabSampleReceiveRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabSampleRejectRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabSampleResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabTestRequest;
import com.deepthoughtnet.clinic.api.lab.dto.LabTestResponse;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus;
import com.deepthoughtnet.clinic.api.lab.LabCsvService;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDirectCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderDoctorReviewCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultComponentCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultEntryCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultItemCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPaymentCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderPublishReportCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderStatusRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderVerificationCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleCollectionCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleReceiveCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabSampleRejectCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@RestController
@Validated
@RequestMapping("/api/lab")
public class LabController {
    private final LabService labService;
    private final LabCsvService labCsvService;

    public LabController(LabService labService, LabCsvService labCsvService) {
        this.labService = labService;
        this.labCsvService = labCsvService;
    }

    @GetMapping("/categories")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.read') or @permissionChecker.hasPermission('lab.test.manage')")
    public List<String> categories() {
        return labService.listCategories();
    }

    @GetMapping("/tests")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.read') or @permissionChecker.hasPermission('lab.test.manage')")
    public List<LabTestResponse> listTests(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return labService.listTests(tenantId, search, active).stream().map(this::toResponse).toList();
    }

    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.manage')")
    public LabTestResponse createTest(@Valid @RequestBody LabTestRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.createTest(tenantId, toCommand(request), actorAppUserId));
    }

    @PostMapping(value = "/tests/import-csv", consumes = "multipart/form-data")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.manage')")
    public LabCsvImportResponse importTests(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return labCsvService.importCsv(tenantId, file.getBytes(), actorAppUserId);
    }

    @GetMapping(value = "/tests/import-template", produces = "text/csv")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.manage')")
    public ResponseEntity<Resource> importTemplate() throws java.io.IOException {
        return csvResponse("lab-tests-import-template.csv", labCsvService.importTemplateCsv());
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.manage')")
    public LabTestResponse updateTest(@PathVariable UUID id, @Valid @RequestBody LabTestRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.updateTest(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/tests/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('lab.test.manage')")
    public LabTestResponse deactivateTest(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.deactivateTest(tenantId, id, actorAppUserId));
    }

    @GetMapping("/orders")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.read') or @permissionChecker.hasPermission('lab.order.collect_payment') or @permissionChecker.hasPermission('lab.order.collect_sample') or @permissionChecker.hasPermission('lab.order.result_entry') or @permissionChecker.hasPermission('lab.order.generate_report') or @permissionChecker.hasPermission('lab.order.review') or @permissionChecker.hasPermission('lab.order.create')")
    public List<LabOrderResponse> listOrders(
            @RequestParam(required = false) UUID consultationId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID effectiveDoctorUserId = resolveDoctorScope(doctorUserId);
        return labService.listOrders(tenantId, consultationId, patientId, effectiveDoctorUserId, parseStatus(status), search).stream().map(this::toResponse).toList();
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.read') or @permissionChecker.hasPermission('lab.order.collect_payment') or @permissionChecker.hasPermission('lab.order.collect_sample') or @permissionChecker.hasPermission('lab.order.result_entry') or @permissionChecker.hasPermission('lab.order.generate_report') or @permissionChecker.hasPermission('lab.order.review') or @permissionChecker.hasPermission('lab.order.create')")
    public LabOrderResponse getOrder(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        LabOrderRecord order = labService.findOrder(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
        ensureDoctorCanAccess(order);
        return toResponse(order);
    }

    @PostMapping("/consultations/{consultationId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.create')")
    public LabOrderResponse createOrder(@PathVariable UUID consultationId, @Valid @RequestBody LabOrderCreateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.createOrderFromConsultation(tenantId, consultationId, new LabOrderCreateCommand(request.testIds(), request.notes()), actorAppUserId));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.create')")
    public LabOrderResponse createStandaloneOrder(@Valid @RequestBody LabOrderDirectCreateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.createOrder(tenantId, new LabOrderDirectCreateCommand(
                request.patientId(),
                request.orderOrigin(),
                request.requestedByInternalDoctorId(),
                request.externalDoctorName(),
                request.externalDoctorMobile(),
                request.externalClinicName(),
                request.referralSource(),
                request.testIds(),
                request.notes()
        ), actorAppUserId));
    }

    @PostMapping("/orders/{id}/payments")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.collect_payment')")
    public LabOrderResponse collectPayment(@PathVariable UUID id, @Valid @RequestBody LabOrderPaymentRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.collectPayment(tenantId, id, new LabOrderPaymentCommand(
                request.paymentDate(),
                request.paymentDateTime(),
                request.amount(),
                request.paymentMode(),
                request.referenceNumber(),
                request.notes(),
                request.receivedBy()
        ), actorAppUserId));
    }

    @PostMapping("/orders/{id}/sample-collection")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.collect_sample')")
    public LabOrderResponse collectSample(@PathVariable UUID id, @Valid @RequestBody LabOrderSampleCollectionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.collectSample(tenantId, id, new LabOrderSampleCollectionCommand(
                request.sampleType(),
                request.collectedBy(),
                request.collectedAt(),
                request.notes()
        ), actorAppUserId));
    }

    @GetMapping("/orders/{orderId}/samples")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.read') or @permissionChecker.hasPermission('lab.order.collect_sample') or @permissionChecker.hasPermission('lab.order.result_entry')")
    public List<LabSampleResponse> listSamples(@PathVariable UUID orderId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return labService.listSamples(tenantId, orderId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/orders/{orderId}/samples/collect")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.collect_sample')")
    public List<LabSampleResponse> collectSamples(@PathVariable UUID orderId, @Valid @RequestBody LabOrderSamplesCollectRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return labService.collectSamples(
                        tenantId,
                        orderId,
                        request.samples().stream().map(this::toCommand).toList(),
                        actorAppUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/samples/{sampleId}/receive")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.collect_sample')")
    public LabSampleResponse receiveSample(@PathVariable UUID sampleId, @RequestBody(required = false) LabSampleReceiveRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        LabSampleReceiveRequest effectiveRequest = request == null ? new LabSampleReceiveRequest(null, null) : request;
        return toResponse(labService.receiveSample(tenantId, sampleId, new LabSampleReceiveCommand(
                effectiveRequest.receivedAt(),
                parseUuid(effectiveRequest.receivedBy(), "receivedBy")
        ), actorAppUserId));
    }

    @PostMapping("/samples/{sampleId}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.collect_sample')")
    public LabSampleResponse rejectSample(@PathVariable UUID sampleId, @Valid @RequestBody LabSampleRejectRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.rejectSample(tenantId, sampleId, new LabSampleRejectCommand(
                request.rejectionReason(),
                request.recollectionRequired(),
                request.notes()
        ), actorAppUserId));
    }

    @PostMapping("/orders/{id}/results")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.result_entry')")
    public LabOrderResponse enterResults(@PathVariable UUID id, @Valid @RequestBody LabOrderResultRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        List<LabOrderResultItemCommand> items = request.items().stream()
                .map(item -> new LabOrderResultItemCommand(
                        item.labOrderItemId(),
                        item.resultValue(),
                        item.unit(),
                        item.referenceRange(),
                        mapComponentResults(item.componentResults())
                ))
                .toList();
        return toResponse(labService.enterResults(tenantId, id, new LabOrderResultEntryCommand(items, request.comments()), actorAppUserId));
    }

    @GetMapping(value = "/orders/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.generate_report')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        LabOrderResultPdf pdf = labService.generateReportPdf(tenantId, id, actorAppUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @PostMapping("/orders/{id}/doctor-review")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.review')")
    public LabOrderResponse reviewOrder(@PathVariable UUID id, @Valid @RequestBody LabOrderDoctorReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.reviewReport(tenantId, id, new LabOrderDoctorReviewCommand(
                request.decision() == null ? null : request.decision().name(),
                request.reason(),
                request.comments()
        ), actorAppUserId));
    }

    @PostMapping("/orders/{id}/verify")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.review')")
    public LabOrderResponse verifyOrder(@PathVariable UUID id, @Valid @RequestBody LabOrderVerificationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(labService.verifyResults(tenantId, id, new LabOrderVerificationCommand(
                request.decision() == null ? null : request.decision().name(),
                request.reason(),
                request.comments()
        ), actorAppUserId));
    }

    @PostMapping("/orders/{id}/publish-report")
    @PreAuthorize("@permissionChecker.hasPermission('lab.order.generate_report')")
    public LabOrderResponse publishReport(@PathVariable UUID id, @Valid @RequestBody(required = false) LabOrderPublishReportRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        LabOrderPublishReportRequest effectiveRequest = request == null ? new LabOrderPublishReportRequest(List.of(), null) : request;
        return toResponse(labService.publishReport(tenantId, id, new LabOrderPublishReportCommand(
                effectiveRequest.deliveryChannels() == null ? List.of() : effectiveRequest.deliveryChannels(),
                effectiveRequest.publishNotes()
        ), actorAppUserId));
    }

    private LabTestUpsertCommand toCommand(LabTestRequest request) {
        return new LabTestUpsertCommand(
                request.testCode(),
                request.testName(),
                request.category(),
                request.department(),
                request.sampleType(),
                request.unit(),
                request.referenceRange(),
                request.turnaroundTime(),
                request.price(),
                request.active(),
                request.parameters() == null ? List.of() : request.parameters().stream()
                        .map(parameter -> new LabTestParameterUpsertCommand(
                                parameter.parameterName(),
                                parameter.unit(),
                                parameter.normalRange(),
                                parameter.criticalRange(),
                                parameter.sortOrder()
                        ))
                        .toList()
        );
    }

    private List<LabOrderResultComponentCommand> mapComponentResults(List<LabOrderResultRequest.LabOrderResultComponentRequest> componentResults) {
        if (componentResults == null || componentResults.isEmpty()) {
            return Collections.emptyList();
        }
        return componentResults.stream()
                .map(component -> new LabOrderResultComponentCommand(
                        component.parameterName(),
                        component.componentName(),
                        component.resultValue(),
                        component.unit(),
                        component.referenceRange()
                ))
                .toList();
    }

    private LabSampleCollectionCommand toCommand(LabSampleCollectionRequest request) {
        return new LabSampleCollectionCommand(
                parseUuid(request.labOrderItemId(), "labOrderItemId"),
                request.specimenType(),
                request.containerType(),
                request.collectedAt(),
                parseUuid(request.collectedBy(), "collectedBy"),
                request.notes()
        );
    }

    private ResponseEntity<Resource> csvResponse(String filename, String csv) {
        byte[] bytes = csv == null ? new byte[0] : csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(new ByteArrayResource(bytes));
    }

    private LabTestResponse toResponse(LabTestRecord record) {
        return new LabTestResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.testCode(),
                record.testName(),
                record.category(),
                record.department(),
                record.sampleType(),
                record.unit(),
                record.referenceRange(),
                record.turnaroundTime(),
                record.price(),
                record.active(),
                record.parameters().stream()
                        .map(parameter -> new LabTestResponse.LabTestParameterResponse(
                                parameter.id() == null ? null : parameter.id().toString(),
                                parameter.labTestId() == null ? null : parameter.labTestId().toString(),
                                parameter.parameterName(),
                                parameter.unit(),
                                parameter.normalRange(),
                                parameter.criticalRange(),
                                parameter.sortOrder(),
                                parameter.createdAt(),
                                parameter.updatedAt()
                        ))
                        .toList(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private LabOrderResponse toResponse(LabOrderRecord record) {
        return new LabOrderResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.orderNumber(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.consultationId() == null ? null : record.consultationId().toString(),
                record.orderOrigin(),
                record.requestedByInternalDoctorId() == null ? null : record.requestedByInternalDoctorId().toString(),
                record.externalDoctorName(),
                record.externalDoctorMobile(),
                record.externalClinicName(),
                record.referralSource(),
                record.notes(),
                record.status(),
                record.orderedAt(),
                record.billId() == null ? null : record.billId().toString(),
                record.billNumber(),
                record.billStatus(),
                record.billTotalAmount(),
                record.billDueAmount(),
                record.externalLabVendor(),
                record.externalReferenceNumber(),
                record.deliveredAt(),
                record.deliveredByUserId() == null ? null : record.deliveredByUserId().toString(),
                record.paymentCollectedAt(),
                record.readyForCollectionAt(),
                record.sampleAccessionNumber(),
                record.sampleBarcodeValue(),
                record.sampleSummaryStatus(),
                record.sampleType(),
                record.sampleCollectedAt(),
                record.sampleCollectedByUserId() == null ? null : record.sampleCollectedByUserId().toString(),
                record.sampleCollectedBy(),
                record.sampleCollectionNotes(),
                record.processingStartedAt(),
                record.resultEnteredAt(),
                record.resultComments(),
                record.reportGeneratedAt(),
                record.reportGeneratedByUserId() == null ? null : record.reportGeneratedByUserId().toString(),
                record.reportGeneratedBy(),
                record.reportFilename(),
                record.reportPublishedAt(),
                record.reportPublishedByUserId() == null ? null : record.reportPublishedByUserId().toString(),
                record.reportDeliveryStatus(),
                record.reportDeliveryChannels(),
                record.reportDeliveryNotes(),
                record.doctorReviewedAt(),
                record.doctorReviewedByUserId() == null ? null : record.doctorReviewedByUserId().toString(),
                record.doctorReviewedBy(),
                record.doctorReviewDecision(),
                record.doctorReviewReason(),
                record.doctorComments(),
                record.labVerifiedAt(),
                record.labVerifiedBy() == null ? null : record.labVerifiedBy().toString(),
                record.labVerifiedByName(),
                record.labVerificationDecision(),
                record.labVerificationComments(),
                record.labVerificationReason(),
                record.attachments().stream()
                        .map(attachment -> new LabOrderResponse.LabOrderAttachmentResponse(
                                attachment.id() == null ? null : attachment.id().toString(),
                                attachment.labOrderId() == null ? null : attachment.labOrderId().toString(),
                                attachment.attachmentType(),
                                attachment.originalFilename(),
                                attachment.mediaType(),
                                attachment.storageKey(),
                                attachment.sizeBytes(),
                                attachment.checksumSha256(),
                                attachment.dicomMetadataJson(),
                                attachment.uploadedByUserId() == null ? null : attachment.uploadedByUserId().toString(),
                                attachment.createdAt()
                        ))
                        .toList(),
                record.items().stream().map(this::toResponse).toList(),
                record.samples().stream().map(this::toResponse).toList(),
                record.results().stream().map(this::toResponse).toList(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private LabSampleResponse toResponse(LabSampleRecord record) {
        return new LabSampleResponse(
                record.id() == null ? null : record.id().toString(),
                record.labOrderId() == null ? null : record.labOrderId().toString(),
                record.labOrderItemId() == null ? null : record.labOrderItemId().toString(),
                record.accessionNumber(),
                record.barcodeValue(),
                record.specimenType(),
                record.containerType(),
                record.status(),
                record.collectedAt(),
                record.collectedBy() == null ? null : record.collectedBy().toString(),
                record.receivedAt(),
                record.receivedBy() == null ? null : record.receivedBy().toString(),
                record.rejectionReason(),
                record.recollectionRequired(),
                record.notes()
        );
    }

    private UUID resolveDoctorScope(UUID requestedDoctorUserId) {
        String tenantRole = normalizeRole(RequestContextHolder.require().tenantRole());
        if (!"DOCTOR".equals(tenantRole)) {
            return requestedDoctorUserId;
        }
        UUID currentDoctorUserId = RequestContextHolder.require().appUserId();
        if (requestedDoctorUserId == null || currentDoctorUserId.equals(requestedDoctorUserId)) {
            return currentDoctorUserId;
        }
        throw new AccessDeniedException("Doctors can only view their own lab orders");
    }

    private void ensureDoctorCanAccess(LabOrderRecord order) {
        String tenantRole = normalizeRole(RequestContextHolder.require().tenantRole());
        if (!"DOCTOR".equals(tenantRole)) {
            return;
        }
        UUID currentDoctorUserId = RequestContextHolder.require().appUserId();
        if (order.doctorUserId() != null && order.doctorUserId().equals(currentDoctorUserId)) {
            return;
        }
        throw new AccessDeniedException("Doctors can only view their own patient lab reports");
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private LabOrderResultResponse toResponse(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultRecord record) {
        return new LabOrderResultResponse(
                record.id() == null ? null : record.id().toString(),
                record.labOrderId() == null ? null : record.labOrderId().toString(),
                record.labOrderItemId() == null ? null : record.labOrderItemId().toString(),
                record.testCode(),
                record.testName(),
                record.parameterName(),
                record.componentName(),
                record.resultValue(),
                record.unit(),
                record.referenceRange(),
                record.sortOrder(),
                record.resultFlag(),
                record.criticalResult(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private LabOrderItemResponse toResponse(com.deepthoughtnet.clinic.api.lab.service.model.LabOrderItemRecord record) {
        return new LabOrderItemResponse(
                record.id() == null ? null : record.id().toString(),
                record.labTestId() == null ? null : record.labTestId().toString(),
                record.testCode(),
                record.testName(),
                record.category(),
                record.department(),
                record.sampleType(),
                record.unit(),
                record.referenceRange(),
                record.turnaroundTime(),
                record.price(),
                record.sortOrder(),
                record.createdAt(),
                record.parameters().stream()
                        .map(parameter -> new LabTestResponse.LabTestParameterResponse(
                                parameter.id() == null ? null : parameter.id().toString(),
                                parameter.labTestId() == null ? null : parameter.labTestId().toString(),
                                parameter.parameterName(),
                                parameter.unit(),
                                parameter.normalRange(),
                                parameter.criticalRange(),
                                parameter.sortOrder(),
                                parameter.createdAt(),
                                parameter.updatedAt()
                        ))
                        .toList()
        );
    }

    private LabOrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return LabOrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid lab order status");
        }
    }

    private UUID parseUuid(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }
}
