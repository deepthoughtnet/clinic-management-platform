package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptEntity;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
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
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.ocr.spi.OcrProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PharmacyOperationsServiceGoodsReceiptTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private InventoryService inventoryService;
    private GoodsReceiptRepository goodsReceiptRepository;
    private SupplierRepository supplierRepository;
    private InventoryLocationRepository locationRepository;
    private MedicineRepository medicineRepository;
    private StockRepository stockRepository;
    private PharmacyOperationsService service;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        stockRepository = mock(StockRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        PharmacyReconciliationRepository reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        SupplierInvoiceRepository supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        PrescriptionDispensingService dispensingService = mock(PrescriptionDispensingService.class);
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OcrProvider> ocrProvider = mock(ObjectProvider.class);
        when(ocrProvider.getIfAvailable()).thenReturn(null);

        SupplierEntity supplier = mock(SupplierEntity.class);
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
        when(supplier.getSupplierName()).thenReturn("Apex Pharma");
        when(supplierRepository.findByTenantIdAndId(TENANT_ID, SUPPLIER_ID)).thenReturn(Optional.of(supplier));

        InventoryLocationEntity location = mock(InventoryLocationEntity.class);
        when(location.getId()).thenReturn(LOCATION_ID);
        when(location.getTenantId()).thenReturn(TENANT_ID);
        when(location.getLocationName()).thenReturn("Main Pharmacy");
        when(locationRepository.findByTenantIdAndId(TENANT_ID, LOCATION_ID)).thenReturn(Optional.of(location));

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));

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
                storageService,
                ocrProvider,
                new ObjectMapper(),
                new NoOpTransactionManager()
        );
    }

    @Test
    void confirmGoodsReceiptCreatesPurchaseMovementWithoutDoublingNewBatchQuantity() {
        GoodsReceiptEntity receipt = GoodsReceiptEntity.create(
                TENANT_ID,
                SUPPLIER_ID,
                null,
                null,
                "GRN-1001",
                OffsetDateTime.parse("2026-05-23T10:15:30Z"),
                LOCATION_ID,
                "[{\"medicineId\":\"%s\",\"medicineName\":\"Paracetamol 500\",\"quantity\":12,\"expectedUnitCost\":null,\"unitCost\":1.25,\"taxPercent\":null,\"batchNumber\":\"PARA-B001\",\"expiryDate\":\"2026-12-31\",\"sellingPrice\":1.75}]".formatted(MEDICINE_ID),
                ACTOR_ID
        );
        when(goodsReceiptRepository.findByTenantIdAndId(TENANT_ID, receipt.getId())).thenReturn(Optional.of(receipt));
        when(goodsReceiptRepository.save(any(GoodsReceiptEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockRepository.findByTenantIdAndMedicineIdAndLocationId(TENANT_ID, MEDICINE_ID, LOCATION_ID)).thenReturn(List.of());
        when(inventoryService.createStock(eq(TENANT_ID), any(StockUpsertCommand.class), eq(ACTOR_ID))).thenReturn(
                new StockRecord(UUID.randomUUID(), TENANT_ID, MEDICINE_ID, LOCATION_ID, "Main Pharmacy", "Paracetamol 500", "TABLET", null, null, null, "PARA-B001", "GRN-1001", LocalDate.parse("2026-12-31"), LocalDate.parse("2026-05-23"), "Apex Pharma", 12, 0, null, new BigDecimal("1.25"), new BigDecimal("1.25"), new BigDecimal("1.75"), true, OffsetDateTime.now(), OffsetDateTime.now())
        );

        GoodsReceiptRecord saved = service.confirmGoodsReceipt(TENANT_ID, receipt.getId(), "verified", ACTOR_ID);

        ArgumentCaptor<StockUpsertCommand> stockCaptor = ArgumentCaptor.forClass(StockUpsertCommand.class);
        verify(inventoryService).createStock(eq(TENANT_ID), stockCaptor.capture(), eq(ACTOR_ID));
        verify(inventoryService, never()).updateStock(eq(TENANT_ID), any(UUID.class), any(StockUpsertCommand.class), eq(ACTOR_ID));

        StockUpsertCommand stockCommand = stockCaptor.getValue();
        assertThat(stockCommand.quantityReceived()).isEqualTo(12);
        assertThat(stockCommand.quantityOnHand()).isZero();
        assertThat(stockCommand.purchaseReferenceNumber()).isEqualTo("GRN-1001");

        ArgumentCaptor<InventoryTransactionCommand> txCaptor = ArgumentCaptor.forClass(InventoryTransactionCommand.class);
        verify(inventoryService).createTransaction(eq(TENANT_ID), txCaptor.capture(), eq(ACTOR_ID));
        InventoryTransactionCommand tx = txCaptor.getValue();
        assertThat(tx.transactionType()).isEqualTo(InventoryTransactionType.PURCHASE);
        assertThat(tx.quantity()).isEqualTo(12);
        assertThat(tx.referenceType()).isEqualTo("GRN");
        assertThat(tx.referenceId()).isEqualTo(receipt.getId());
        assertThat(saved.receiptNumber()).isEqualTo("GRN-1001");
        assertThat(saved.matchingStatus()).isEqualTo("CONFIRMED");
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
