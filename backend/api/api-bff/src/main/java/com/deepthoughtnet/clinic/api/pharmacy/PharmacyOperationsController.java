package com.deepthoughtnet.clinic.api.pharmacy;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/pharmacy")
public class PharmacyOperationsController {
    private final PharmacyOperationsService service;

    public PharmacyOperationsController(PharmacyOperationsService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('billing.create')")
    public PharmacyDashboardResponse dashboard() {
        return service.dashboard(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/analytics")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public PharmacyAnalyticsResponse analytics() {
        return service.analytics(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/suppliers")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<SupplierRecord> listSuppliers() {
        return service.listSuppliers(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public SupplierRecord createSupplier(@RequestBody SupplierUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveSupplier(tenantId, null, request, actorAppUserId);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public SupplierRecord updateSupplier(@PathVariable UUID id, @RequestBody SupplierUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveSupplier(tenantId, id, request, actorAppUserId);
    }

    @GetMapping("/locations")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<InventoryLocationRecord> listLocations() {
        return service.listLocations(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryLocationRecord createLocation(@RequestBody InventoryLocationUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveLocation(tenantId, null, request, actorAppUserId);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryLocationRecord updateLocation(@PathVariable UUID id, @RequestBody InventoryLocationUpsertRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveLocation(tenantId, id, request, actorAppUserId);
    }

    @GetMapping("/reconciliations")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<PharmacyReconciliationRecord> listReconciliations() {
        return service.listReconciliations(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/reconciliations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord createReconciliation(@RequestBody ReconciliationCreateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.createReconciliation(tenantId, request, actorAppUserId);
    }

    @PutMapping("/reconciliations/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord updateReconciliation(@PathVariable UUID id, @RequestBody ReconciliationCreateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.updateReconciliation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/submit")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord submitReconciliation(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.submitReconciliation(tenantId, id, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord approveReconciliation(@PathVariable UUID id, @RequestBody(required = false) ReconciliationDecisionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.approveReconciliation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord rejectReconciliation(@PathVariable UUID id, @RequestBody ReconciliationDecisionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.rejectReconciliation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/post")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord postReconciliation(@PathVariable UUID id, @RequestBody(required = false) ReconciliationPostRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.postReconciliation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/confirm")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord confirmReconciliation(@PathVariable UUID id, @RequestBody ReconciliationConfirmRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.confirmReconciliation(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/review")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PharmacyReconciliationRecord reviewReconciliation(@PathVariable UUID id, @RequestBody StockSheetReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.reviewReconciliationSheet(tenantId, id, request, actorAppUserId);
    }

    @PostMapping("/reconciliations/{id}/sheet")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public ReconciliationUploadResponse uploadSheet(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws Exception {
        return service.uploadReconciliationSheet(RequestContextHolder.requireTenantId(), id, file);
    }

    @PostMapping("/inward")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public StockRecord inward(@RequestBody StockInwardRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.inwardStock(tenantId, request, actorAppUserId);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public InventoryTransactionRecord transfer(@RequestBody InventoryTransferRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.transferStock(tenantId, new InventoryTransferCommand(
                request.medicineId(),
                request.stockBatchId(),
                request.fromLocationId(),
                request.toLocationId(),
                request.quantity(),
                request.reason()
        ), actorAppUserId);
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<PurchaseOrderRecord> listPurchaseOrders() {
        return service.listPurchaseOrders(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public PurchaseOrderRecord createPurchaseOrder(@RequestBody PurchaseOrderRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.savePurchaseOrder(tenantId, request, actorAppUserId);
    }

    @GetMapping("/supplier-invoices")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<SupplierInvoiceRecord> listSupplierInvoices() {
        return service.listSupplierInvoices(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/supplier-invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public SupplierInvoiceRecord createSupplierInvoice(@RequestBody SupplierInvoiceRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveSupplierInvoice(tenantId, request, actorAppUserId);
    }

    @GetMapping("/goods-receipts")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')")
    public List<GoodsReceiptRecord> listGoodsReceipts() {
        return service.listGoodsReceipts(RequestContextHolder.requireTenantId());
    }

    @PostMapping("/goods-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public GoodsReceiptRecord createGoodsReceipt(@RequestBody GoodsReceiptRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.saveGoodsReceipt(tenantId, request, actorAppUserId);
    }

    @PostMapping("/goods-receipts/{id}/confirm")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage')")
    public GoodsReceiptRecord confirmGoodsReceipt(@PathVariable UUID id, @RequestBody(required = false) String approvalNote) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return service.confirmGoodsReceipt(tenantId, id, approvalNote, actorAppUserId);
    }

    @GetMapping("/substitutes")
    @PreAuthorize("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read') or @permissionChecker.hasPermission('billing.create')")
    public List<SubstituteSuggestionRecord> substitutes(@RequestParam UUID medicineId) {
        return service.suggestSubstitutes(RequestContextHolder.requireTenantId(), medicineId);
    }
}
