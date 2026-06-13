package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryServiceImpl;
import com.deepthoughtnet.clinic.inventory.service.model.StockRecord;
import com.deepthoughtnet.clinic.inventory.service.model.StockUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceImplStockTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID MEDICINE_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private MedicineRepository medicineRepository;
    private StockRepository stockRepository;
    private InventoryLocationRepository locationRepository;
    private AuditEventPublisher auditEventPublisher;
    private List<InventoryTransactionEntity> savedTransactions;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        medicineRepository = mock(MedicineRepository.class);
        stockRepository = mock(StockRepository.class);
        InventoryTransactionRepository transactionRepository = mock(InventoryTransactionRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        savedTransactions = new ArrayList<>();

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(MEDICINE_ID);
        when(medicine.getTenantId()).thenReturn(TENANT_ID);
        when(medicine.getMedicineName()).thenReturn("Paracetamol");
        when(medicine.getMedicineType()).thenReturn("TABLET");
        when(medicine.isActive()).thenReturn(true);
        when(medicine.getCreatedAt()).thenReturn(OffsetDateTime.now());
        when(medicine.getUpdatedAt()).thenReturn(OffsetDateTime.now());

        InventoryLocationEntity location = mock(InventoryLocationEntity.class);
        when(location.getId()).thenReturn(LOCATION_ID);
        when(location.getLocationName()).thenReturn("Main Pharmacy");

        when(medicineRepository.findByTenantIdAndId(TENANT_ID, MEDICINE_ID)).thenReturn(Optional.of(medicine));
        when(locationRepository.findByTenantIdAndId(TENANT_ID, LOCATION_ID)).thenReturn(Optional.of(location));
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransactionEntity.class))).thenAnswer(invocation -> {
            InventoryTransactionEntity entity = invocation.getArgument(0);
            savedTransactions.add(entity);
            return entity;
        });

        service = new InventoryServiceImpl(
                medicineRepository,
                stockRepository,
                transactionRepository,
                locationRepository,
                auditEventPublisher,
                new ObjectMapper()
        );
    }

    @Test
    void canCreateTwoBatchesForSameMedicineWithSameBarcodeWhenBatchNumbersDiffer() {
        when(stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(TENANT_ID, MEDICINE_ID, LOCATION_ID, "B001"))
                .thenReturn(Optional.empty());
        when(stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(TENANT_ID, MEDICINE_ID, LOCATION_ID, "B002"))
                .thenReturn(Optional.empty());

        StockRecord first = service.createStock(TENANT_ID, stockCommand("B001", "890100000001"), ACTOR_ID);
        StockRecord second = service.createStock(TENANT_ID, stockCommand("B002", "890100000001"), ACTOR_ID);

        assertThat(first.batchNumber()).isEqualTo("B001");
        assertThat(second.batchNumber()).isEqualTo("B002");
        assertThat(first.barcode()).isEqualTo("890100000001");
        assertThat(second.barcode()).isEqualTo("890100000001");
        assertThat(savedTransactions).hasSize(2);
        assertThat(savedTransactions).allSatisfy(tx -> assertThat(tx.getTransactionType()).isEqualTo("STOCK_IN"));
        org.mockito.Mockito.verify(auditEventPublisher, org.mockito.Mockito.atLeast(4)).record(any());
    }

    @Test
    void cannotCreateDuplicateBatchForSameMedicineAndLocation() {
        when(stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(TENANT_ID, MEDICINE_ID, LOCATION_ID, "B001"))
                .thenReturn(Optional.of(StockEntity.create(TENANT_ID, MEDICINE_ID, LOCATION_ID)));

        assertThatThrownBy(() -> service.createStock(TENANT_ID, stockCommand("B001", "890100000001"), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number.");
    }

    @Test
    void increasingExistingBatchQuantityCreatesStockInMovementWithoutChangingTenantRules() {
        StockEntity existing = StockEntity.create(TENANT_ID, MEDICINE_ID, LOCATION_ID);
        existing.update(LOCATION_ID, "890100000001", null, null, "B001", "REF-1", null, null, "Acme Pharma", 10, 10, 5, new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("12.00"), true);
        when(stockRepository.findByTenantIdAndId(TENANT_ID, existing.getId())).thenReturn(Optional.of(existing));
        when(stockRepository.findByTenantIdAndMedicineIdAndLocationIdAndBatchNumberIgnoreCase(TENANT_ID, MEDICINE_ID, LOCATION_ID, "B001"))
                .thenReturn(Optional.of(existing));

        StockUpsertCommand updated = new StockUpsertCommand(
                MEDICINE_ID,
                LOCATION_ID,
                "890100000001",
                null,
                null,
                "B001",
                "REF-1",
                null,
                null,
                "Acme Pharma",
                15,
                15,
                5,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("12.00"),
                true
        );

        StockRecord saved = service.updateStock(TENANT_ID, existing.getId(), updated, ACTOR_ID);

        assertThat(saved.quantityOnHand()).isEqualTo(15);
        assertThat(savedTransactions).hasSize(1);
        InventoryTransactionEntity tx = savedTransactions.getFirst();
        assertThat(tx.getTransactionType()).isEqualTo("PURCHASE");
        assertThat(tx.getBeforeQuantity()).isEqualTo(10);
        assertThat(tx.getAfterQuantity()).isEqualTo(15);
        assertThat(tx.getQuantity()).isEqualTo(5);
        assertThat(tx.getNotes()).isEqualTo("REF-1");
        org.mockito.Mockito.verify(auditEventPublisher, org.mockito.Mockito.atLeast(2)).record(any());
    }

    @Test
    void createStockRejectsNegativeQuantities() {
        StockUpsertCommand invalid = new StockUpsertCommand(
                MEDICINE_ID,
                LOCATION_ID,
                "890100000001",
                null,
                null,
                "BNEG",
                null,
                null,
                null,
                "Acme Pharma",
                5,
                -1,
                5,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("12.00"),
                true
        );

        assertThatThrownBy(() -> service.createStock(TENANT_ID, invalid, ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantityOnHand cannot be negative");
    }

    @Test
    void createTransactionRejectsAdjustmentThatWouldGoNegative() {
        StockEntity existing = StockEntity.create(TENANT_ID, MEDICINE_ID, LOCATION_ID);
        existing.update(LOCATION_ID, "890100000001", null, null, "B002", "REF-2", null, null, "Acme Pharma", 2, 2, 5, new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("12.00"), true);
        when(stockRepository.findByTenantIdAndId(TENANT_ID, existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createTransaction(TENANT_ID, new com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand(
                MEDICINE_ID,
                existing.getId(),
                LOCATION_ID,
                null,
                com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType.ADJUSTMENT_OUT,
                5,
                "Physical count variance",
                "PHYSICAL_STOCK_COUNT",
                existing.getId(),
                ACTOR_ID,
                "Counted lower than system"
        ), ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock available.");
    }

    @Test
    void createTransactionSupportsVendorReturnAndWriteOffMovements() {
        StockEntity existing = StockEntity.create(TENANT_ID, MEDICINE_ID, LOCATION_ID);
        existing.update(LOCATION_ID, "890100000001", null, null, "B003", "REF-3", null, null, "Acme Pharma", 20, 20, 5, new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("12.00"), true);
        when(stockRepository.findByTenantIdAndId(TENANT_ID, existing.getId())).thenReturn(Optional.of(existing));

        service.createTransaction(TENANT_ID, new com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand(
                MEDICINE_ID,
                existing.getId(),
                LOCATION_ID,
                null,
                com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType.VENDOR_RETURN_OUT,
                5,
                "Vendor return",
                "VENDOR_RETURN",
                existing.getId(),
                ACTOR_ID,
                "Wrong supply"
        ), ACTOR_ID);

        service.createTransaction(TENANT_ID, new com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand(
                MEDICINE_ID,
                existing.getId(),
                LOCATION_ID,
                null,
                com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType.WRITE_OFF,
                3,
                "Write-off",
                "WRITE_OFF",
                existing.getId(),
                ACTOR_ID,
                "Expired stock"
        ), ACTOR_ID);

        assertThat(savedTransactions).hasSize(2);
        assertThat(savedTransactions.get(0).getTransactionType()).isEqualTo("VENDOR_RETURN_OUT");
        assertThat(savedTransactions.get(0).getBeforeQuantity()).isEqualTo(20);
        assertThat(savedTransactions.get(0).getAfterQuantity()).isEqualTo(15);
        assertThat(savedTransactions.get(1).getTransactionType()).isEqualTo("WRITE_OFF");
        assertThat(savedTransactions.get(1).getBeforeQuantity()).isEqualTo(15);
        assertThat(savedTransactions.get(1).getAfterQuantity()).isEqualTo(12);
    }

    private StockUpsertCommand stockCommand(String batchNumber, String barcode) {
        return new StockUpsertCommand(
                MEDICINE_ID,
                LOCATION_ID,
                barcode,
                null,
                null,
                batchNumber,
                null,
                null,
                null,
                "Acme Pharma",
                10,
                10,
                5,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("12.00"),
                true
        );
    }
}
