package com.deepthoughtnet.clinic.api.billing;

import com.deepthoughtnet.clinic.api.billing.dto.ReceiptResponse;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("billingAccessChecker")
public class BillingAccessChecker {
    private final BillingService billingService;
    private final PermissionChecker permissionChecker;

    public BillingAccessChecker(BillingService billingService, PermissionChecker permissionChecker) {
        this.billingService = billingService;
        this.permissionChecker = permissionChecker;
    }

    public boolean canAccessBill(UUID billId) {
        return hasGeneralBillingAccess() || isLabBill(billId);
    }

    public boolean canAccessReceipt(UUID receiptId) {
        if (hasGeneralBillingAccess()) {
            return true;
        }
        UUID tenantId = RequestContextHolder.requireTenantId();
        ReceiptRecord receipt = billingService.findReceipt(tenantId, receiptId).orElse(null);
        if (receipt == null || receipt.billId() == null) {
            return false;
        }
        return isLabBill(receipt.billId());
    }

    public boolean canAccessReceiptResponse(ReceiptResponse response) {
        if (response == null || response.id() == null) {
            return false;
        }
        return canAccessReceipt(UUID.fromString(response.id()));
    }

    private boolean hasGeneralBillingAccess() {
        return permissionChecker.hasAnyPermission("billing.receipt", "billing.read", "payment.collect")
                || permissionChecker.hasAnyRole("CLINIC_ADMIN", "BILLING_USER", "PLATFORM_ADMIN");
    }

    private boolean isLabBill(UUID billId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        BillRecord bill = billingService.findById(tenantId, billId).orElse(null);
        if (bill == null) {
            return false;
        }
        return bill.lines().stream().anyMatch(line -> line.itemType() == BillItemType.TEST);
    }
}
