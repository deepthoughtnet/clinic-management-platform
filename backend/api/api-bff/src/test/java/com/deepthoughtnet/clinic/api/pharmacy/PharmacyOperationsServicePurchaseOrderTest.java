package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PharmacyOperationsServicePurchaseOrderTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private PurchaseOrderRepository purchaseOrderRepository;
    private SupplierRepository supplierRepository;
    private MedicineRepository medicineRepository;
    private PharmacyOperationsService service;

    @BeforeEach
    void setUp() {
        InventoryService inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        PharmacyReconciliationRepository reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        InventoryLocationRepository locationRepository = mock(InventoryLocationRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        GoodsReceiptRepository goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        PrescriptionDispensingService dispensingService = mock(PrescriptionDispensingService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> ocrProvider = mock(ObjectProvider.class);
        when(ocrProvider.getIfAvailable()).thenReturn(null);

        SupplierEntity supplier = mock(SupplierEntity.class);
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
        when(supplier.getSupplierName()).thenReturn("Acme Pharma");
        when(supplier.isActive()).thenReturn(true);
        when(supplierRepository.findByTenantIdAndId(TENANT_ID, SUPPLIER_ID)).thenReturn(Optional.of(supplier));

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicine.isActive()).thenReturn(true);
        when(medicine.getMedicineName()).thenReturn("Paracetamol 500");
        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));

        when(purchaseOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new PharmacyOperationsService(
                inventoryService,
                medicineRepository,
                stockRepository,
                supplierRepository,
                reconciliationRepository,
                locationRepository,
                purchaseOrderRepository,
                supplierInvoiceRepository,
                goodsReceiptRepository,
                dispensingService,
                auditEventPublisher,
                storageService,
                ocrProvider,
                new ObjectMapper(),
                new NoOpTransactionManager()
        );
    }

    @Test
    void savePurchaseOrderPersistsLineItemsAndDetailReloadUsesStoredItemsJson() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                SUPPLIER_ID,
                "PO-2026-000001",
                "2026-07-01",
                "2026-07-10",
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        12,
                        new BigDecimal("1.25"),
                        new BigDecimal("1.25"),
                        new BigDecimal("12"),
                        null,
                        null,
                        null
                )),
                "DRAFT"
        );

        PurchaseOrderRecord saved = service.savePurchaseOrder(TENANT_ID, request, ACTOR_ID);

        assertThat(saved.poNumber()).isEqualTo("PO-2026-000001");
        assertThat(saved.approvalNote()).isEqualTo("DRAFT");
        assertThat(saved.itemsJson()).contains("\"medicineId\":\"%s\"".formatted(MEDICINE_ID));
        assertThat(saved.itemsJson()).contains("\"unitCost\":1.25");
        assertThat(saved.itemsJson()).contains("\"taxPercent\":12");
    }

    @Test
    void cancelPurchaseOrderMarksRecordCancelled() {
        PurchaseOrderEntityFixture fixture = new PurchaseOrderEntityFixture();
        when(purchaseOrderRepository.findByTenantIdAndId(TENANT_ID, fixture.entity.getId())).thenReturn(Optional.of(fixture.entity));

        PurchaseOrderRecord cancelled = service.cancelPurchaseOrder(TENANT_ID, fixture.entity.getId(), "supplier requested", ACTOR_ID);

        assertThat(cancelled.approvalNote()).isEqualTo("CANCELLED:supplier requested");
        assertThat(cancelled.matchingStatus()).isEqualTo("CANCELLED");
    }

    private final class PurchaseOrderEntityFixture {
        private final com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity entity =
                com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity.create(
                        TENANT_ID,
                        SUPPLIER_ID,
                        "PO-2026-000002",
                        LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-07-10"),
                        "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":12,\"expectedUnitCost\":1.25,\"unitCost\":1.25,\"taxPercent\":12}]".formatted(MEDICINE_ID),
                        ACTOR_ID
                );
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
