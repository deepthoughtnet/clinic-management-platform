package com.deepthoughtnet.clinic.api.billing;

import com.deepthoughtnet.clinic.api.billing.dto.BillLineRequest;
import com.deepthoughtnet.clinic.api.billing.dto.BillLineResponse;
import com.deepthoughtnet.clinic.api.billing.dto.BillRequest;
import com.deepthoughtnet.clinic.api.billing.dto.BillResponse;
import com.deepthoughtnet.clinic.api.billing.dto.PaymentRequest;
import com.deepthoughtnet.clinic.api.billing.dto.PaymentResponse;
import com.deepthoughtnet.clinic.api.billing.dto.ReceiptResponse;
import com.deepthoughtnet.clinic.api.billing.dto.RefundRequest;
import com.deepthoughtnet.clinic.api.billing.dto.RefundResponse;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillPdf;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.billing.service.model.PaymentCommand;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.billing.service.model.RefundCommand;
import com.deepthoughtnet.clinic.billing.service.model.RefundRecord;
import java.time.LocalDate;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@RestController
@Validated
@RequestMapping("/api/bills")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('patient.read')")
    public List<BillResponse> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String paymentMode
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        BillStatus billStatus = parseStatus(status);
        PaymentMode mode = parsePaymentMode(paymentMode);
        return billingService.list(tenantId, new BillingSearchCriteria(patientId, billStatus, fromDate, toDate, mode)).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('billing.create')")
    public BillResponse create(@Valid @RequestBody BillRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(billingService.createDraft(tenantId, toCommand(request), actorAppUserId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('patient.read')")
    public BillResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(billingService.findById(tenantId, id).orElseThrow(() -> new IllegalArgumentException("Bill not found")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('billing.update') or @permissionChecker.hasPermission('billing.create')")
    public BillResponse update(@PathVariable UUID id, @Valid @RequestBody BillRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(billingService.updateDraft(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/issue")
    @PreAuthorize("@permissionChecker.hasPermission('billing.create')")
    public BillResponse issue(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(billingService.issue(tenantId, id, actorAppUserId));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('billing.create')")
    public BillResponse cancel(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(billingService.cancel(tenantId, id, actorAppUserId));
    }

    @PostMapping("/{billId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('payment.collect')")
    public PaymentResponse addPayment(@PathVariable UUID billId, @Valid @RequestBody PaymentRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toPaymentResponse(billingService.recordPayment(tenantId, billId, new PaymentCommand(
                request.paymentDate(),
                request.paymentDateTime(),
                request.amount(),
                request.paymentMode(),
                request.referenceNumber(),
                request.notes(),
                request.receivedBy()
        ), actorAppUserId));
    }

    @GetMapping("/{billId}/payments")
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public List<PaymentResponse> listPayments(@PathVariable UUID billId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return billingService.listPayments(tenantId, billId).stream().map(this::toPaymentResponse).toList();
    }

    @GetMapping("/{billId}/receipts")
    @PreAuthorize("@permissionChecker.hasPermission('billing.receipt') or @permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public List<ReceiptResponse> listReceipts(@PathVariable UUID billId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return billingService.listReceipts(tenantId, billId).stream().map(this::toReceiptResponse).toList();
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('billing.receipt') or @permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('patient.read')")
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        BillPdf pdf = billingService.generateBillPdf(tenantId, id, actorAppUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    private BillUpsertCommand toCommand(BillRequest request) {
        return new BillUpsertCommand(
                request.patientId(),
                request.consultationId(),
                request.appointmentId(),
                request.billDate(),
                request.discountType(),
                request.discountValue(),
                null,
                request.discountReason(),
                request.discountApprovedBy(),
                request.taxAmount(),
                request.notes(),
                request.lines() == null ? List.of() : request.lines().stream().map(this::toCommand).toList()
        );
    }

    private BillLineCommand toCommand(BillLineRequest request) {
        return new BillLineCommand(
                request.itemType(),
                request.itemName(),
                request.quantity(),
                request.unitPrice(),
                request.referenceId(),
                request.sortOrder(),
                request.lineDiscountAmount(),
                request.batchNumber(),
                request.dispensationReferenceId()
        );
    }

    private BillResponse toResponse(BillRecord record) {
        return new BillResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.billNumber(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.consultationId() == null ? null : record.consultationId().toString(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.billDate(),
                record.status(),
                record.subtotalAmount(),
                record.discountType(),
                record.discountValue(),
                record.discountAmount(),
                record.discountReason(),
                record.discountApprovedBy() == null ? null : record.discountApprovedBy().toString(),
                record.taxAmount(),
                record.totalAmount(),
                record.paidAmount(),
                record.refundedAmount(),
                record.netPaidAmount(),
                record.dueAmount(),
                record.invoiceEmailedAt(),
                record.notes(),
                record.createdAt(),
                record.updatedAt(),
                record.lines().stream().map(line -> new BillLineResponse(
                        line.id() == null ? null : line.id().toString(),
                        line.itemType(),
                        line.itemName(),
                        line.quantity(),
                        line.unitPrice(),
                        line.totalPrice(),
                        line.referenceId() == null ? null : line.referenceId().toString(),
                        line.lineDiscountAmount(),
                        line.batchNumber(),
                        line.dispensationReferenceId() == null ? null : line.dispensationReferenceId().toString(),
                        line.sortOrder()
                )).toList()
        );
    }

    private PaymentResponse toPaymentResponse(PaymentRecord record) {
        return new PaymentResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.billId() == null ? null : record.billId().toString(),
                record.paymentDate(),
                record.paymentDateTime(),
                record.amount(),
                record.paymentMode(),
                record.referenceNumber(),
                record.notes(),
                record.receivedBy() == null ? null : record.receivedBy().toString(),
                record.receiptId() == null ? null : record.receiptId().toString(),
                record.receiptNumber(),
                record.receiptDate(),
                record.createdAt()
        );
    }

    @PostMapping("/{billId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('payment.collect')")
    public RefundResponse addRefund(@PathVariable UUID billId, @Valid @RequestBody RefundRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        RefundRecord record = billingService.refund(tenantId, billId, new RefundCommand(
                request.paymentId(),
                request.amount(),
                request.reason(),
                request.refundMode(),
                request.notes(),
                request.refundedAt()
        ), actorAppUserId);
        return toRefundResponse(record);
    }

    @GetMapping("/{billId}/refunds")
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public List<RefundResponse> listRefunds(@PathVariable UUID billId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return billingService.listRefunds(tenantId, billId).stream().map(this::toRefundResponse).toList();
    }

    private RefundResponse toRefundResponse(RefundRecord record) {
        return new RefundResponse(
                record.id() == null ? null : record.id().toString(),
                record.billId() == null ? null : record.billId().toString(),
                record.paymentId() == null ? null : record.paymentId().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.amount(),
                record.reason(),
                record.refundMode(),
                record.refundedBy() == null ? null : record.refundedBy().toString(),
                record.refundedAt(),
                record.notes(),
                record.createdAt()
        );
    }

    private ReceiptResponse toReceiptResponse(ReceiptRecord record) {
        return new ReceiptResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.receiptNumber(),
                record.billId() == null ? null : record.billId().toString(),
                record.paymentId() == null ? null : record.paymentId().toString(),
                record.receiptDate(),
                record.amount(),
                record.createdAt()
        );
    }

    private BillStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BillStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid bill status");
        }
    }

    private PaymentMode parsePaymentMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment mode");
        }
    }
}
