package com.deepthoughtnet.clinic.api.inventory;

import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
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

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stocks")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('billing.create')")
    public List<StockRecord> listStocks() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listStocks(tenantId);
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

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('billing.create')")
    public InventoryTransactionRecord createTransaction(@jakarta.validation.Valid @RequestBody InventoryTransactionCommand request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return inventoryService.createTransaction(tenantId, request, actorAppUserId);
    }

    @GetMapping("/transactions")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('audit.read')")
    public List<InventoryTransactionRecord> listTransactions() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return inventoryService.listTransactions(tenantId);
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
}
