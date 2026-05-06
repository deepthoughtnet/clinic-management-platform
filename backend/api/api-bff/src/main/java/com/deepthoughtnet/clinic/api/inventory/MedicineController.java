package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medicines")
public class MedicineController {
    private final InventoryService inventoryService;

    public MedicineController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('vaccination.manage')")
    public List<MedicineRecord> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listMedicines(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.findMedicine(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord create(@RequestBody MedicineUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.createMedicine(tenantId, request, actorAppUserId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord update(@PathVariable UUID id, @RequestBody MedicineUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.updateMedicine(tenantId, id, request, actorAppUserId);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.deactivateMedicine(tenantId, id, actorAppUserId);
    }
}
