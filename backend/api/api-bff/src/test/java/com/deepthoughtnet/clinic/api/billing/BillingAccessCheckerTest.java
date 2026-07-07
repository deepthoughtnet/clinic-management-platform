package com.deepthoughtnet.clinic.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BillingAccessCheckerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void labFrontDeskCanAccessLabBillAndReceipt() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        BillingAccessChecker accessChecker = new BillingAccessChecker(billingService, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));

        when(permissionChecker.hasAnyPermission("billing.receipt", "billing.read", "payment.collect")).thenReturn(false);
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "BILLING_USER", "PLATFORM_ADMIN")).thenReturn(false);
        when(billingService.findById(tenantId, billId)).thenReturn(Optional.of(labBill(tenantId, billId)));
        when(billingService.findReceipt(tenantId, receiptId)).thenReturn(Optional.of(new ReceiptRecord(
                receiptId,
                tenantId,
                "RCPT-001",
                billId,
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 6),
                new BigDecimal("450.00"),
                OffsetDateTime.parse("2026-07-06T10:15:30Z")
        )));

        assertThat(accessChecker.canAccessBill(billId)).isTrue();
        assertThat(accessChecker.canAccessReceipt(receiptId)).isTrue();
    }

    @Test
    void labFrontDeskCannotAccessUnrelatedBillOrReceipt() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        BillingAccessChecker accessChecker = new BillingAccessChecker(billingService, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("LAB_FRONT_DESK"), "LAB_FRONT_DESK", "cid"));

        when(permissionChecker.hasAnyPermission("billing.receipt", "billing.read", "payment.collect")).thenReturn(false);
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "BILLING_USER", "PLATFORM_ADMIN")).thenReturn(false);
        when(billingService.findById(tenantId, billId)).thenReturn(Optional.of(nonLabBill(tenantId, billId)));
        when(billingService.findReceipt(tenantId, receiptId)).thenReturn(Optional.of(new ReceiptRecord(
                receiptId,
                tenantId,
                "RCPT-002",
                billId,
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 6),
                new BigDecimal("450.00"),
                OffsetDateTime.parse("2026-07-06T10:15:30Z")
        )));

        assertThat(accessChecker.canAccessBill(billId)).isFalse();
        assertThat(accessChecker.canAccessReceipt(receiptId)).isFalse();
    }

    @Test
    void clinicAdminKeepsGeneralReceiptAccessForNonLabBills() {
        UUID tenantId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        BillingService billingService = mock(BillingService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        BillingAccessChecker accessChecker = new BillingAccessChecker(billingService, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        when(permissionChecker.hasAnyPermission("billing.receipt", "billing.read", "payment.collect")).thenReturn(false);
        when(permissionChecker.hasAnyRole("CLINIC_ADMIN", "BILLING_USER", "PLATFORM_ADMIN")).thenReturn(true);

        assertThat(accessChecker.canAccessBill(billId)).isTrue();
    }

    private BillRecord labBill(UUID tenantId, UUID billId) {
        return billRecord(tenantId, billId, List.of(new BillLineRecord(
                UUID.randomUUID(),
                BillItemType.TEST,
                "CBC",
                1,
                new BigDecimal("450.00"),
                new BigDecimal("450.00"),
                UUID.randomUUID(),
                1,
                BigDecimal.ZERO,
                null,
                null
        )));
    }

    private BillRecord nonLabBill(UUID tenantId, UUID billId) {
        return billRecord(tenantId, billId, List.of(new BillLineRecord(
                UUID.randomUUID(),
                BillItemType.MEDICINE,
                "Amoxicillin",
                1,
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                UUID.randomUUID(),
                1,
                BigDecimal.ZERO,
                null,
                null
        )));
    }

    private BillRecord billRecord(UUID tenantId, UUID billId, List<BillLineRecord> lines) {
        return new BillRecord(
                billId,
                tenantId,
                "BILL-001",
                UUID.randomUUID(),
                "PAT-001",
                "Demo Patient",
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 6),
                BillStatus.PAID,
                new BigDecimal("450.00"),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("450.00"),
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                new BigDecimal("450.00"),
                BigDecimal.ZERO,
                null,
                null,
                OffsetDateTime.parse("2026-07-06T10:15:30Z"),
                OffsetDateTime.parse("2026-07-06T10:15:30Z"),
                lines
        );
    }
}
