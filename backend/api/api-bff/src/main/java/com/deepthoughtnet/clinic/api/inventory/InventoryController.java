package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionSaveCommand;
import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;
    private final PharmacyOperationsService pharmacyOperationsService;
    private final InventoryTransactionViewMapper inventoryTransactionViewMapper;

    public InventoryController(
            InventoryService inventoryService,
            PharmacyOperationsService pharmacyOperationsService,
            InventoryTransactionViewMapper inventoryTransactionViewMapper
    ) {
        this.inventoryService = inventoryService;
        this.pharmacyOperationsService = pharmacyOperationsService;
        this.inventoryTransactionViewMapper = inventoryTransactionViewMapper;
    }

    @GetMapping("/stocks")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('billing.create')")
    public List<StockRecord> listStocks(@RequestParam(name = "locationId", required = false) UUID locationId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listStocks(tenantId, locationId);
    }

    @GetMapping("/stocks/search")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('billing.create')")
    public List<StockRecord> searchStocks(@RequestParam(name = "q", required = false) String query) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return pharmacyOperationsService.searchStocks(tenantId, query);
    }

    @GetMapping("/locations")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<InventoryLocationRecord> listLocations() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listLocations(tenantId);
    }

    @GetMapping("/physical-count-sessions")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<PhysicalCountSessionRecord> listPhysicalCountSessions() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listPhysicalCountSessions(tenantId);
    }

    @GetMapping("/physical-count-sessions/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public PhysicalCountSessionRecord getPhysicalCountSession(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.findPhysicalCountSession(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Physical count session not found"));
    }

    @PutMapping("/physical-count-sessions/{id}")
    @PreAuthorize("@permissionChecker.hasAnyRole('PHARMACIST', 'PHARMA', 'PHARMACY', 'PHARMACY_INVENTORY_MANAGER', 'CLINIC_ADMIN')")
    public PhysicalCountSessionRecord savePhysicalCountSession(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody PhysicalCountSessionSaveCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.savePhysicalCountSession(tenantId, id, request, actorAppUserId, currentPhysicalCountRoles());
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryLocationRecord createLocation(@jakarta.validation.Valid @RequestBody InventoryLocationUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.saveLocation(tenantId, null, request, actorAppUserId);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryLocationRecord updateLocation(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody InventoryLocationUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.saveLocation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public StockRecord createStock(@jakarta.validation.Valid @RequestBody StockUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.createStock(tenantId, request, actorAppUserId);
    }

    @PutMapping("/stocks/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public StockRecord updateStock(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody StockUpsertCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.updateStock(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryTransactionRecord transferStock(@jakarta.validation.Valid @RequestBody InventoryTransferRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.transferStock(tenantId, new InventoryTransferCommand(
                request.medicineId(),
                request.stockBatchId(),
                request.fromLocationId(),
                request.toLocationId(),
                request.quantity(),
                request.reason()
        ), actorAppUserId);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryTransactionResponse createTransaction(@jakarta.validation.Valid @RequestBody InventoryTransactionCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        InventoryTransactionRecord saved = inventoryService.createTransaction(tenantId, request, actorAppUserId);
        return inventoryTransactionViewMapper.toResponses(tenantId, List.of(saved)).getFirst();
    }

    @GetMapping("/transactions")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('audit.read')")
    public List<InventoryTransactionResponse> listTransactions() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryTransactionViewMapper.toResponses(tenantId, inventoryService.listTransactions(tenantId));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<LowStockRecord> lowStock() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listLowStock(tenantId);
    }

    @GetMapping("/alerts/expired")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<StockRecord> expired() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listExpiredStocks(tenantId);
    }

    @GetMapping("/alerts/expiring")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<StockRecord> expiring(@RequestParam(defaultValue = "30") int days) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listExpiringStocks(tenantId, days);
    }

    private Set<String> currentPhysicalCountRoles() {
        var context = RequestContextHolder.require();
        Set<String> roles = new LinkedHashSet<>();
        addRole(roles, context.tenantRole());
        if (context.tokenRoles() != null) {
            context.tokenRoles().forEach(role -> addRole(roles, role));
        }
        return roles;
    }

    private void addRole(Set<String> roles, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        roles.add(normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized);
    }
}
