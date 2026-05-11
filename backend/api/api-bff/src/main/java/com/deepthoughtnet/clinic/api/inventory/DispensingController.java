package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.api.inventory.dto.DispenseRequest;
import com.deepthoughtnet.clinic.api.inventory.dto.PrescriptionDispenseResponse;
import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/dispensing")
public class DispensingController {
    private final PrescriptionDispensingService dispensingService;

    public DispensingController(PrescriptionDispensingService dispensingService) {
        this.dispensingService = dispensingService;
    }

    @GetMapping("/queue")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('prescription.read')")
    public List<PrescriptionDispenseResponse> queue() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return dispensingService.queue(tenantId);
    }

    @GetMapping("/{prescriptionId}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('billing.create') or @permissionChecker.hasPermission('prescription.read')")
    public PrescriptionDispenseResponse view(@PathVariable UUID prescriptionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return dispensingService.view(tenantId, prescriptionId);
    }

    @PostMapping("/{prescriptionId}/dispense")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('billing.create')")
    public PrescriptionDispenseResponse dispense(@PathVariable UUID prescriptionId, @jakarta.validation.Valid @RequestBody DispenseRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        String tenantRole = RequestContextHolder.require().tenantRole();
        boolean canOverride = "CLINIC_ADMIN".equalsIgnoreCase(tenantRole)
                || "PHARMACIST".equalsIgnoreCase(tenantRole)
                || "PHARMA".equalsIgnoreCase(tenantRole)
                || "PHARMACY".equalsIgnoreCase(tenantRole);
        return dispensingService.dispense(tenantId, prescriptionId, request, actorAppUserId, canOverride);
    }

    @PostMapping("/{prescriptionId}/bill")
    @PreAuthorize("@permissionChecker.hasPermission('billing.create')")
    public BillRecord generateBill(@PathVariable UUID prescriptionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return dispensingService.createMedicineBill(tenantId, prescriptionId, actorAppUserId);
    }
}
