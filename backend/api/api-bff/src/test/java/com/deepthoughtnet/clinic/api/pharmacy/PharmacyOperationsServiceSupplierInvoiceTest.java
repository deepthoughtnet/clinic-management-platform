package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderEntity;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class PharmacyOperationsServiceSupplierInvoiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private SupplierInvoiceRepository supplierInvoiceRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private ObjectStorageService storageService;
    private PharmacyOperationsService service;
    private PurchaseOrderEntity purchaseOrder;
    private SupplierEntity supplier;

    @BeforeEach
    void setUp() {
        InventoryService inventoryService = mock(InventoryService.class);
        MedicineRepository medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PharmacyReconciliationRepository reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        InventoryLocationRepository locationRepository = mock(InventoryLocationRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        GoodsReceiptRepository goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        PrescriptionDispensingService dispensingService = mock(PrescriptionDispensingService.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        storageService = mock(ObjectStorageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> ocrProvider = mock(ObjectProvider.class);
        when(ocrProvider.getIfAvailable()).thenReturn(null);

        supplier = mock(SupplierEntity.class);
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
        when(supplier.getSupplierName()).thenReturn("Acme Pharma");
        when(supplier.isActive()).thenReturn(true);
        when(supplierRepository.findByTenantIdAndId(TENANT_ID, SUPPLIER_ID)).thenReturn(Optional.of(supplier));

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicine.isActive()).thenReturn(true);
        when(medicine.getMedicineName()).thenReturn("Paracetamol 500");
        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));

        purchaseOrder = PurchaseOrderEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                "PO-2026-000001",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-10"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":10,\"expectedUnitCost\":100.00,\"unitCost\":100.00,\"taxPercent\":0}]".formatted(MEDICINE_ID),
                ACTOR_ID
        );
        purchaseOrder.review("MATCHED", null, "GENERATED");
        when(purchaseOrderRepository.findByTenantIdAndId(TENANT_ID, purchaseOrder.getId())).thenReturn(Optional.of(purchaseOrder));

        when(supplierInvoiceRepository.save(any(SupplierInvoiceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.buildDocumentStorageKey(any(UUID.class), any(String.class))).thenReturn("tenant/supplier-invoice/test.pdf");

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
    void saveSupplierInvoiceRequiresVarianceReasonWhenAmountDiffersFromPo() {
        SupplierInvoiceRequest request = new SupplierInvoiceRequest(
                SUPPLIER_ID,
                purchaseOrder.getId(),
                "INV-1001",
                "2026-06-30",
                new BigDecimal("1100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1100.00"),
                List.of(new ProcurementLineRequest(
                        MEDICINE_ID,
                        "Paracetamol 500",
                        10,
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                null,
                "note"
        );

        assertThatThrownBy(() -> service.saveSupplierInvoice(TENANT_ID, null, request, ACTOR_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Variance reason is required");
    }

    @Test
    void lifecycleTransitionsMoveDraftToMatchedThenReadyForPayment() {
        SupplierInvoiceEntity invoice = SupplierInvoiceEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                purchaseOrder.getId(),
                "INV-1002",
                LocalDate.parse("2026-07-02"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":10,\"expectedUnitCost\":100.00,\"unitCost\":100.00,\"taxPercent\":0}]".formatted(MEDICINE_ID),
                ACTOR_ID
        );
        when(supplierInvoiceRepository.findByTenantIdAndId(TENANT_ID, invoice.getId())).thenReturn(Optional.of(invoice));

        SupplierInvoiceRecord matched = service.matchSupplierInvoice(TENANT_ID, invoice.getId(), ACTOR_ID);
        SupplierInvoiceRecord approved = service.approveSupplierInvoiceForPayment(TENANT_ID, invoice.getId(), ACTOR_ID);

        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(approved.status()).isEqualTo("READY_FOR_PAYMENT");
    }

    @Test
    void cancelSupplierInvoicePersistsCancelledStatusAndReason() {
        SupplierInvoiceEntity invoice = SupplierInvoiceEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                purchaseOrder.getId(),
                "INV-1003",
                LocalDate.parse("2026-07-02"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":10,\"expectedUnitCost\":100.00,\"unitCost\":100.00,\"taxPercent\":0}]".formatted(MEDICINE_ID),
                ACTOR_ID
        );
        invoice.markMatched();
        when(supplierInvoiceRepository.findByTenantIdAndId(TENANT_ID, invoice.getId())).thenReturn(Optional.of(invoice));

        SupplierInvoiceRecord cancelled = service.cancelSupplierInvoice(TENANT_ID, invoice.getId(), new SupplierInvoiceCancelRequest("duplicate invoice"), ACTOR_ID);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("duplicate invoice");
    }

    @Test
    void uploadSupplierInvoiceAttachmentStoresMetadataAgainstInvoice() throws Exception {
        SupplierInvoiceEntity invoice = SupplierInvoiceEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                purchaseOrder.getId(),
                "INV-1004",
                LocalDate.parse("2026-07-02"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":10,\"expectedUnitCost\":100.00,\"unitCost\":100.00,\"taxPercent\":0}]".formatted(MEDICINE_ID),
                ACTOR_ID
        );
        when(supplierInvoiceRepository.findByTenantIdAndId(TENANT_ID, invoice.getId())).thenReturn(Optional.of(invoice));

        SupplierInvoiceAttachmentResponse response = service.uploadSupplierInvoiceAttachment(
                TENANT_ID,
                invoice.getId(),
                new MockMultipartFile("file", "supplier-invoice.pdf", "application/pdf", "pdf".getBytes()),
                ACTOR_ID
        );

        assertThat(response.fileName()).isEqualTo("supplier-invoice.pdf");
        assertThat(invoice.getAttachmentFileName()).isEqualTo("supplier-invoice.pdf");
        verify(storageService).putObject("tenant/supplier-invoice/test.pdf", "application/pdf", "pdf".getBytes());
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
