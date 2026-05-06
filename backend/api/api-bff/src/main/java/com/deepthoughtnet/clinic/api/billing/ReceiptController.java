package com.deepthoughtnet.clinic.api.billing;

import com.deepthoughtnet.clinic.api.billing.dto.ReceiptResponse;
import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptPdf;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final BillingService billingService;
    private final NotificationActionService notificationActionService;

    public ReceiptController(BillingService billingService, NotificationActionService notificationActionService) {
        this.billingService = billingService;
        this.notificationActionService = notificationActionService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public ReceiptResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(billingService.findReceipt(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found")));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('patient.read')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ReceiptPdf pdf = billingService.generateReceiptPdf(tenantId, id, actorAppUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("@permissionChecker.hasPermission('payment.collect') or @permissionChecker.hasPermission('notification.send')")
    public ReceiptResponse send(@PathVariable UUID id, @RequestParam(required = false, defaultValue = "email") String channel) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        notificationActionService.sendReceipt(tenantId, id, channel, actorAppUserId);
        return toResponse(billingService.findReceipt(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found")));
    }

    private ReceiptResponse toResponse(ReceiptRecord record) {
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
}
