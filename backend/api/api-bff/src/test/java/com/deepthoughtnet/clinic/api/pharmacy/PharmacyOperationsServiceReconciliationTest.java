package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.service.PrescriptionDispensingService;
import com.deepthoughtnet.clinic.inventory.db.GoodsReceiptRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyReconciliationRepository;
import com.deepthoughtnet.clinic.inventory.db.PurchaseOrderRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierEntity;
import com.deepthoughtnet.clinic.inventory.db.SupplierRepository;
import com.deepthoughtnet.clinic.inventory.db.SupplierInvoiceRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class PharmacyOperationsServiceReconciliationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID STOCK_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID MAKER_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    private InventoryService inventoryService;
    private MedicineRepository medicineRepository;
    private StockRepository stockRepository;
    private SupplierRepository supplierRepository;
    private PharmacyReconciliationRepository reconciliationRepository;
    private InventoryLocationRepository locationRepository;
    private PurchaseOrderRepository purchaseOrderRepository;
    private SupplierInvoiceRepository supplierInvoiceRepository;
    private GoodsReceiptRepository goodsReceiptRepository;
    private PrescriptionDispensingService dispensingService;
    private ObjectStorageService storageService;
    private ObjectProvider<OcrProvider> ocrProvider;
    private ObjectMapper objectMapper;
    private PharmacyOperationsService service;
    private MedicineEntity medicine;
    private StockEntity stock;
    private SupplierEntity supplier;
    private InventoryLocationEntity location;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        stockRepository = mock(StockRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        reconciliationRepository = mock(PharmacyReconciliationRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        supplierInvoiceRepository = mock(SupplierInvoiceRepository.class);
        goodsReceiptRepository = mock(GoodsReceiptRepository.class);
        dispensingService = mock(PrescriptionDispensingService.class);
        storageService = mock(ObjectStorageService.class);
        ocrProvider = mock(ObjectProvider.class);
        objectMapper = new ObjectMapper();

        medicine = mock(MedicineEntity.class);
        stock = StockEntity.create(TENANT_ID, MEDICINE_ID, LOCATION_ID);
        supplier = mock(SupplierEntity.class);
        location = mock(InventoryLocationEntity.class);

        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicine.getMedicineName()).thenReturn("Paracetamol");
        stock.update(LOCATION_ID, null, null, null, "BATCH-1", null, null, null, null, 10, 10, null, null, null, null, true);
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
        when(supplier.getSupplierName()).thenReturn("Acme Pharma");
        when(location.getId()).thenReturn(LOCATION_ID);
        when(location.getTenantId()).thenReturn(TENANT_ID);
        when(location.getLocationName()).thenReturn("Main Pharmacy");
        when(location.isDefaultLocation()).thenReturn(true);
        when(location.isActive()).thenReturn(true);

        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));
        when(stockRepository.findByTenantIdAndId(TENANT_ID, STOCK_ID)).thenReturn(Optional.of(stock));
        when(stockRepository.findByTenantIdAndMedicineId(TENANT_ID, MEDICINE_ID)).thenReturn(List.of(stock));
        when(supplierRepository.findByTenantIdAndId(TENANT_ID, SUPPLIER_ID)).thenReturn(Optional.of(supplier));
        when(locationRepository.findByTenantIdAndId(TENANT_ID, LOCATION_ID)).thenReturn(Optional.of(location));
        when(locationRepository.findByTenantIdAndDefaultLocationTrue(TENANT_ID)).thenReturn(Optional.of(location));
        when(reconciliationRepository.save(any(PharmacyReconciliationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrProvider.getIfAvailable()).thenReturn(null);
        when(inventoryService.createTransaction(any(), any(), any())).thenAnswer(invocation -> {
            InventoryTransactionCommand command = invocation.getArgument(1);
            int next = stock.getQuantityOnHand();
            if (command.transactionType() == InventoryTransactionType.ADJUSTMENT_OUT || command.transactionType() == InventoryTransactionType.EXPIRED || command.transactionType() == InventoryTransactionType.DISPENSED || command.transactionType() == InventoryTransactionType.SALE) {
                next -= command.quantity();
            } else {
                next += command.quantity();
            }
            stock.setQuantityOnHand(next);
            return null;
        });

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
                objectMapper,
                new NoOpTransactionManager()
        );
    }

    @Test
    void makerCannotApproveOwnReconciliationAndDifferentReviewerCanPost() {
        PharmacyReconciliationEntity entity = PharmacyReconciliationEntity.create(TENANT_ID, MEDICINE_ID, STOCK_ID, SUPPLIER_ID, LOCATION_ID, 10, MAKER_ID);
        entity.captureCount(7, -3, "Cycle count");
        when(reconciliationRepository.findByTenantIdAndId(TENANT_ID, entity.getId())).thenReturn(Optional.of(entity));

        PharmacyReconciliationRecord submitted = service.submitReconciliation(TENANT_ID, entity.getId(), MAKER_ID);
        assertThat(submitted.status()).isEqualTo("SUBMITTED");
        assertThat(submitted.submittedBy()).isEqualTo(MAKER_ID);

        assertThatThrownBy(() -> service.approveReconciliation(TENANT_ID, entity.getId(), new ReconciliationDecisionRequest("checked"), MAKER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        PharmacyReconciliationRecord approved = service.approveReconciliation(TENANT_ID, entity.getId(), new ReconciliationDecisionRequest("checked"), REVIEWER_ID);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.reviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(approved.reviewDecision()).isEqualTo("APPROVED");

        PharmacyReconciliationRecord posted = service.postReconciliation(TENANT_ID, entity.getId(), new ReconciliationPostRequest(7, "posting"), REVIEWER_ID);
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.postedBy()).isEqualTo(REVIEWER_ID);
        assertThat(posted.postedAt()).isNotNull();
        assertThat(posted.confirmedAt()).isNotNull();
        assertThat(stock.getQuantityOnHand()).isEqualTo(7);
        verify(inventoryService).createTransaction(eq(TENANT_ID), any(InventoryTransactionCommand.class), eq(REVIEWER_ID));
    }

    @Test
    void rejectRequiresReason() {
        PharmacyReconciliationEntity entity = PharmacyReconciliationEntity.create(TENANT_ID, MEDICINE_ID, STOCK_ID, SUPPLIER_ID, LOCATION_ID, 10, MAKER_ID);
        entity.captureCount(9, -1, "Cycle count");
        when(reconciliationRepository.findByTenantIdAndId(TENANT_ID, entity.getId())).thenReturn(Optional.of(entity));

        service.submitReconciliation(TENANT_ID, entity.getId(), MAKER_ID);

        assertThatThrownBy(() -> service.rejectReconciliation(TENANT_ID, entity.getId(), new ReconciliationDecisionRequest(""), REVIEWER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
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
