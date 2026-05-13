package com.deepthoughtnet.clinic.api.billing;

import com.deepthoughtnet.clinic.api.billing.dto.BillLineRequest;
import com.deepthoughtnet.clinic.api.billing.dto.BillLineResponse;
import com.deepthoughtnet.clinic.api.billing.dto.BillRequest;
import com.deepthoughtnet.clinic.api.billing.dto.BillResponse;
import com.deepthoughtnet.clinic.api.billing.dto.InvoiceEmailSendResponse;
import com.deepthoughtnet.clinic.api.billing.dto.PaymentLedgerResponse;
import com.deepthoughtnet.clinic.api.billing.dto.PaymentRequest;
import com.deepthoughtnet.clinic.api.billing.dto.PaymentResponse;
import com.deepthoughtnet.clinic.api.billing.dto.ReceiptResponse;
import com.deepthoughtnet.clinic.api.billing.dto.RefundLedgerResponse;
import com.deepthoughtnet.clinic.api.billing.dto.RefundRequest;
import com.deepthoughtnet.clinic.api.billing.dto.RefundResponse;
import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
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
import java.util.Comparator;
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
    private final NotificationActionService notificationActionService;

    public BillingController(BillingService billingService, NotificationActionService notificationActionService) {
        this.billingService = billingService;
        this.notificationActionService = notificationActionService;
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

    @GetMapping("/payments")
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public List<PaymentLedgerResponse> listAllPayments(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String billNumber,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String receivedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        PaymentMode mode = parsePaymentMode(paymentMode);
        UUID patientUuid = parseUuid(patientId, "patientId");
        UUID receivedByUuid = parseUuid(receivedBy, "receivedBy");
        String billNumberFilter = normalizeText(billNumber);
        String freeText = normalizeText(search);

        List<BillRecord> bills = billingService.list(tenantId, new BillingSearchCriteria(patientUuid, null, null, null, null));
        List<PaymentLedgerResponse> rows = bills.stream()
                .flatMap(bill -> billingService.listPayments(tenantId, bill.id()).stream().map(payment -> toPaymentLedgerResponse(payment, bill)))
                .filter(row -> fromDate == null || !row.paymentDate().isBefore(fromDate))
                .filter(row -> toDate == null || !row.paymentDate().isAfter(toDate))
                .filter(row -> mode == null || row.paymentMode() == mode)
                .filter(row -> receivedByUuid == null || (row.receivedBy() != null && row.receivedBy().equals(receivedByUuid.toString())))
                .filter(row -> billNumberFilter == null || (row.billNumber() != null && row.billNumber().toLowerCase().contains(billNumberFilter)))
                .filter(row -> matchesSearch(row.patientName(), row.patientNumber(), row.billNumber(), freeText))
                .sorted(Comparator.comparing(PaymentLedgerResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return paginate(rows, page, size);
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

    @GetMapping("/refunds")
    @PreAuthorize("@permissionChecker.hasPermission('billing.read') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public List<RefundLedgerResponse> listAllRefunds(
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String billNumber,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String refundMode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        PaymentMode mode = parsePaymentMode(refundMode);
        UUID patientUuid = parseUuid(patientId, "patientId");
        String billNumberFilter = normalizeText(billNumber);
        String freeText = normalizeText(search);

        List<BillRecord> bills = billingService.list(tenantId, new BillingSearchCriteria(patientUuid, null, null, null, null));
        List<RefundLedgerResponse> rows = bills.stream()
                .flatMap(bill -> billingService.listRefunds(tenantId, bill.id()).stream().map(refund -> toRefundLedgerResponse(refund, bill)))
                .filter(row -> fromDate == null || !row.refundedAt().toLocalDate().isBefore(fromDate))
                .filter(row -> toDate == null || !row.refundedAt().toLocalDate().isAfter(toDate))
                .filter(row -> mode == null || row.refundMode() == mode)
                .filter(row -> billNumberFilter == null || (row.billNumber() != null && row.billNumber().toLowerCase().contains(billNumberFilter)))
                .filter(row -> matchesSearch(row.patientName(), row.patientNumber(), row.billNumber(), freeText))
                .sorted(Comparator.comparing(RefundLedgerResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return paginate(rows, page, size);
    }

    @PostMapping("/{billId}/send-invoice-email")
    @PreAuthorize("@permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('notification.send')")
    public InvoiceEmailSendResponse sendInvoiceEmail(@PathVariable UUID billId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        NotificationActionService.InvoiceEmailResult result = notificationActionService.sendInvoiceEmail(tenantId, billId, actorAppUserId);
        return new InvoiceEmailSendResponse(result.sent(), result.message(), result.recipientEmail(), result.sentAt());
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

    private PaymentLedgerResponse toPaymentLedgerResponse(PaymentRecord payment, BillRecord bill) {
        return new PaymentLedgerResponse(
                payment.id() == null ? null : payment.id().toString(),
                payment.tenantId() == null ? null : payment.tenantId().toString(),
                payment.billId() == null ? null : payment.billId().toString(),
                bill.billNumber(),
                bill.patientId() == null ? null : bill.patientId().toString(),
                bill.patientName(),
                bill.patientNumber(),
                payment.paymentDate(),
                payment.paymentDateTime(),
                payment.amount(),
                payment.paymentMode(),
                payment.referenceNumber(),
                payment.notes(),
                payment.receivedBy() == null ? null : payment.receivedBy().toString(),
                payment.receiptId() == null ? null : payment.receiptId().toString(),
                payment.receiptNumber(),
                payment.receiptDate(),
                bill.status(),
                bill.dueAmount(),
                payment.createdAt()
        );
    }

    private RefundLedgerResponse toRefundLedgerResponse(RefundRecord refund, BillRecord bill) {
        return new RefundLedgerResponse(
                refund.id() == null ? null : refund.id().toString(),
                refund.tenantId() == null ? null : refund.tenantId().toString(),
                refund.billId() == null ? null : refund.billId().toString(),
                bill.billNumber(),
                bill.patientId() == null ? null : bill.patientId().toString(),
                bill.patientName(),
                bill.patientNumber(),
                refund.paymentId() == null ? null : refund.paymentId().toString(),
                refund.amount(),
                refund.reason(),
                refund.refundMode(),
                refund.refundedBy() == null ? null : refund.refundedBy().toString(),
                refund.refundedAt(),
                refund.notes(),
                bill.status(),
                bill.dueAmount(),
                refund.createdAt()
        );
    }

    private UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private boolean matchesSearch(String patientName, String patientNumber, String billNumber, String search) {
        if (search == null) {
            return true;
        }
        String haystack = String.join(" ",
                patientName == null ? "" : patientName,
                patientNumber == null ? "" : patientNumber,
                billNumber == null ? "" : billNumber).toLowerCase();
        return haystack.contains(search);
    }

    private <T> List<T> paginate(List<T> rows, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 500));
        int safePage = Math.max(page, 0);
        int from = safePage * safeSize;
        if (from >= rows.size()) {
            return List.of();
        }
        int to = Math.min(rows.size(), from + safeSize);
        return rows.subList(from, to);
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
