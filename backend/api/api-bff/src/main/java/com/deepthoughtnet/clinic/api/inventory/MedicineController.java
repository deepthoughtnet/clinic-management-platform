package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.api.pharmacy.MedicineImportResult;
import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping({"/api/medicines", "/api/pharmacy/medicines"})
public class MedicineController {
    private final InventoryService inventoryService;
    private final PermissionChecker permissionChecker;
    private final PharmacyOperationsService pharmacyOperationsService;

    public MedicineController(InventoryService inventoryService, PermissionChecker permissionChecker, PharmacyOperationsService pharmacyOperationsService) {
        this.inventoryService = inventoryService;
        this.permissionChecker = permissionChecker;
        this.pharmacyOperationsService = pharmacyOperationsService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('medicine.read') or @permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('vaccination.manage')")
    public List<MedicineRecord> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        List<MedicineRecord> medicines = inventoryService.listMedicines(tenantId);
        if (permissionChecker.hasPermission("inventory.manage")
                || permissionChecker.hasPermission("report.read")
                || permissionChecker.hasPermission("vaccination.manage")) {
            return medicines;
        }
        return medicines.stream().filter(MedicineRecord::active).toList();
    }

    @GetMapping("/search")
    @PreAuthorize("@permissionChecker.hasPermission('medicine.read') or @permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('vaccination.manage')")
    public List<MedicineRecord> search(@RequestParam(name = "q", required = false) String query) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return pharmacyOperationsService.searchMedicines(tenantId, query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('medicine.read') or @permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        MedicineRecord medicine = inventoryService.findMedicine(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found"));
        if (!medicine.active()
                && !permissionChecker.hasPermission("inventory.manage")
                && !permissionChecker.hasPermission("report.read")
                && !permissionChecker.hasPermission("vaccination.manage")) {
            throw new IllegalArgumentException("Medicine not found");
        }
        return medicine;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord create(@Valid @RequestBody MedicineUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.createMedicine(tenantId, request, actorAppUserId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord update(@PathVariable UUID id, @Valid @RequestBody MedicineUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.updateMedicine(tenantId, id, request, actorAppUserId);
    }

    @PostMapping(value = "/import-csv", consumes = "multipart/form-data")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineImportResult importCsv(@RequestParam("file") MultipartFile file) throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return pharmacyOperationsService.importMedicines(tenantId, file.getBytes(), actorAppUserId);
    }

    @GetMapping(value = "/import-template", produces = "text/csv")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public String importTemplate() {
        return pharmacyOperationsService.medicineImportTemplateCsv();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.deactivateMedicine(tenantId, id, actorAppUserId);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('vaccination.manage')")
    public MedicineRecord activate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.activateMedicine(tenantId, id, actorAppUserId);
    }
}
