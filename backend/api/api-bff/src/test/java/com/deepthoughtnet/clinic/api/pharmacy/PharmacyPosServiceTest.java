package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePrescriptionEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePrescriptionRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class PharmacyPosServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private InventoryService inventoryService;
    private MedicineRepository medicineRepository;
    private StockRepository stockRepository;
    private InventoryLocationRepository locationRepository;
    private PatientRepository patientRepository;
    private ClinicProfileService clinicProfileService;
    private PharmacyCashierShiftRepository cashierShiftRepository;
    private PharmacySaleRepository saleRepository;
    private PharmacySaleItemRepository saleItemRepository;
    private PharmacySalePaymentRepository salePaymentRepository;
    private PharmacySaleReturnRepository saleReturnRepository;
    private PharmacySalePrescriptionRepository salePrescriptionRepository;
    private AuditEventPublisher auditEventPublisher;
    private ObjectStorageService storageService;
    private PharmacyPosService service;

    private InventoryLocationEntity location;
    private final List<PharmacySaleEntity> sales = new ArrayList<>();
    private final List<PharmacySaleItemEntity> items = new ArrayList<>();
    private final List<PharmacySalePaymentEntity> payments = new ArrayList<>();
    private final List<PharmacySaleReturnEntity> returns = new ArrayList<>();
    private final List<PharmacySalePrescriptionEntity> prescriptions = new ArrayList<>();
    private final List<PharmacyCashierShiftEntity> shifts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        medicineRepository = mock(MedicineRepository.class);
        stockRepository = mock(StockRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        patientRepository = mock(PatientRepository.class);
        clinicProfileService = mock(ClinicProfileService.class);
        cashierShiftRepository = mock(PharmacyCashierShiftRepository.class);
        saleRepository = mock(PharmacySaleRepository.class);
        saleItemRepository = mock(PharmacySaleItemRepository.class);
        salePaymentRepository = mock(PharmacySalePaymentRepository.class);
        saleReturnRepository = mock(PharmacySaleReturnRepository.class);
        salePrescriptionRepository = mock(PharmacySalePrescriptionRepository.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        storageService = mock(ObjectStorageService.class);

        location = InventoryLocationEntity.create(tenantId, "Main Pharmacy", "MAIN", "PHARMACY", true);
        when(locationRepository.findByTenantIdAndDefaultLocationTrue(tenantId)).thenReturn(Optional.of(location));
        when(cashierShiftRepository.save(any())).thenAnswer(invocation -> {
            PharmacyCashierShiftEntity entity = invocation.getArgument(0);
            shifts.removeIf(existing -> existing.getId().equals(entity.getId()));
            shifts.add(entity);
            return entity;
        });
        when(cashierShiftRepository.existsByTenantIdAndCashierUserIdAndStatus(eq(tenantId), any(), eq("OPEN"))).thenAnswer(invocation -> {
            UUID cashierUserId = invocation.getArgument(1);
            return shifts.stream().anyMatch(shift -> shift.getTenantId().equals(tenantId) && shift.getCashierUserId().equals(cashierUserId) && "OPEN".equals(shift.getStatus()));
        });
        when(cashierShiftRepository.findByTenantIdAndCashierUserIdAndStatus(eq(tenantId), any(), eq("OPEN"))).thenAnswer(invocation -> {
            UUID cashierUserId = invocation.getArgument(1);
            return shifts.stream().filter(shift -> shift.getTenantId().equals(tenantId) && shift.getCashierUserId().equals(cashierUserId) && "OPEN".equals(shift.getStatus())).findFirst();
        });
        when(cashierShiftRepository.findByTenantIdAndId(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID shiftId = invocation.getArgument(1);
            return shifts.stream().filter(shift -> shift.getTenantId().equals(tenantId) && shift.getId().equals(shiftId)).findFirst();
        });
        when(cashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId)).thenAnswer(invocation -> new ArrayList<>(shifts));
        when(cashierShiftRepository.findByTenantIdAndCashierUserIdOrderByOpenedAtDesc(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID cashierUserId = invocation.getArgument(1);
            return shifts.stream().filter(shift -> shift.getTenantId().equals(tenantId) && shift.getCashierUserId().equals(cashierUserId)).toList();
        });

        when(saleRepository.save(any())).thenAnswer(invocation -> {
            PharmacySaleEntity entity = invocation.getArgument(0);
            sales.removeIf(existing -> existing.getId().equals(entity.getId()));
            sales.add(entity);
            return entity;
        });
        when(saleRepository.findByTenantIdAndId(eq(tenantId), any())).thenAnswer(invocation ->
                sales.stream().filter(entity -> entity.getId().equals(invocation.getArgument(1))).findFirst());
        when(saleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenAnswer(invocation -> new ArrayList<>(sales));
        when(saleRepository.findByTenantIdAndSaleNumber(eq(tenantId), any())).thenReturn(Optional.empty());

        when(saleItemRepository.save(any())).thenAnswer(invocation -> {
            PharmacySaleItemEntity entity = invocation.getArgument(0);
            items.removeIf(existing -> existing.getId().equals(entity.getId()));
            items.add(entity);
            return entity;
        });
        when(saleItemRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID saleId = invocation.getArgument(1);
            return items.stream().filter(item -> item.getSaleId().equals(saleId)).toList();
        });

        when(salePaymentRepository.save(any())).thenAnswer(invocation -> {
            PharmacySalePaymentEntity entity = invocation.getArgument(0);
            payments.removeIf(existing -> existing.getId().equals(entity.getId()));
            payments.add(entity);
            return entity;
        });
        when(salePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID saleId = invocation.getArgument(1);
            return payments.stream().filter(item -> item.getSaleId().equals(saleId)).toList();
        });
        when(salePaymentRepository.findByTenantIdAndCashierShiftIdOrderByCreatedAtAsc(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID shiftId = invocation.getArgument(1);
            return payments.stream().filter(item -> shiftId.equals(item.getCashierShiftId())).toList();
        });
        when(salePaymentRepository.findByTenantIdAndReceiptNumber(eq(tenantId), any())).thenReturn(Optional.empty());

        when(saleReturnRepository.save(any())).thenAnswer(invocation -> {
            PharmacySaleReturnEntity entity = invocation.getArgument(0);
            returns.removeIf(existing -> existing.getId().equals(entity.getId()));
            returns.add(entity);
            return entity;
        });
        when(saleReturnRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(eq(tenantId), any())).thenAnswer(invocation -> {
            UUID saleId = invocation.getArgument(1);
            return returns.stream().filter(item -> item.getSaleId().equals(saleId)).toList();
        });
        when(saleReturnRepository.findByTenantIdAndReturnNumber(eq(tenantId), any())).thenReturn(List.of());

        when(salePrescriptionRepository.save(any())).thenAnswer(invocation -> {
            PharmacySalePrescriptionEntity entity = invocation.getArgument(0);
            prescriptions.removeIf(existing -> existing.getId().equals(entity.getId()));
            prescriptions.add(entity);
            return entity;
        });
        when(salePrescriptionRepository.findByTenantIdAndId(eq(tenantId), any())).thenAnswer(invocation ->
                prescriptions.stream().filter(entity -> entity.getId().equals(invocation.getArgument(1))).findFirst());
        when(salePrescriptionRepository.existsByTenantIdAndStorageKey(eq(tenantId), any())).thenReturn(false);
        when(storageService.buildDocumentStorageKey(eq(tenantId), any())).thenReturn("tenants/" + tenantId + "/documents/rx-file.pdf");

        when(inventoryService.createTransaction(eq(tenantId), any(), eq(actorId))).thenAnswer(invocation -> {
            InventoryTransactionCommand command = invocation.getArgument(1);
            return new InventoryTransactionRecord(
                    UUID.randomUUID(),
                    tenantId,
                    command.medicineId(),
                    command.stockBatchId(),
                    command.locationId(),
                    command.targetLocationId(),
                    command.transactionType(),
                    command.quantity(),
                    0,
                    0,
                    command.reason(),
                    command.referenceType(),
                    command.referenceId(),
                    command.createdBy(),
                    command.notes(),
                    OffsetDateTime.now()
            );
        });

        service = new PharmacyPosService(
                inventoryService,
                medicineRepository,
                stockRepository,
                locationRepository,
                patientRepository,
                clinicProfileService,
                cashierShiftRepository,
                saleRepository,
                saleItemRepository,
                salePaymentRepository,
                saleReturnRepository,
                auditEventPublisher,
                salePrescriptionRepository,
                storageService,
                new ObjectMapper()
        );
    }

    @Test
    void createSaleUsesFefoAndSplitsAcrossBatches() {
        openShiftFor(actorId);
        MedicineEntity medicine = medicine("Amoxicillin");
        StockEntity expired = stock(medicine.getId(), "EXP", LocalDate.now().minusDays(1), 20, "11.00");
        StockEntity earliest = stock(medicine.getId(), "EARLY", LocalDate.now().plusDays(10), 2, "12.00");
        StockEntity later = stock(medicine.getId(), "LATE", LocalDate.now().plusDays(20), 5, "12.00");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(expired, earliest, later));

        PharmacyPosSaleResponse sale = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null,
                "Walk-in customer",
                "9999999999",
                null,
                OffsetDateTime.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                PaymentMode.CASH,
                null,
                null,
                "Take medicines after food",
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 4, new BigDecimal("12.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        assertThat(sale.items()).hasSize(2);
        assertThat(sale.items().get(0).batchNumber()).isEqualTo("EARLY");
        assertThat(sale.items().get(0).quantity()).isEqualTo(2);
        assertThat(sale.items().get(1).batchNumber()).isEqualTo("LATE");
        assertThat(sale.items().get(1).quantity()).isEqualTo(2);
        assertThat(sale.payments()).hasSize(1);
        assertThat(sale.dueAmount()).isEqualByComparingTo("38.00");

        ArgumentCaptor<InventoryTransactionCommand> captor = ArgumentCaptor.forClass(InventoryTransactionCommand.class);
        verify(inventoryService, org.mockito.Mockito.times(2)).createTransaction(eq(tenantId), captor.capture(), eq(actorId));
        assertThat(captor.getAllValues()).extracting(InventoryTransactionCommand::stockBatchId)
                .containsExactly(earliest.getId(), later.getId());
        verify(auditEventPublisher).record(any());
    }

    @Test
    void createSaleBlocksWhenStockIsInsufficient() {
        MedicineEntity medicine = medicine("Ibuprofen");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId()))
                .thenReturn(List.of(stock(medicine.getId(), "B1", LocalDate.now().plusDays(20), 1, "5.00")));

        assertThatThrownBy(() -> service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 2, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void openShiftCreatesCurrentShiftForCashier() {
        PharmacyPosShiftResponse shift = service.openShift(tenantId, actorId, new PharmacyPosOpenShiftRequest(new BigDecimal("1000.00"), "Opening drawer"));

        assertThat(shift.status()).isEqualTo("OPEN");
        assertThat(shift.openingCashAmount()).isEqualByComparingTo("1000.00");
        assertThat(service.getCurrentShift(tenantId, actorId).id()).isEqualTo(shift.id());
    }

    @Test
    void openShiftBlocksSecondOpenShiftForSameCashier() {
        service.openShift(tenantId, actorId, new PharmacyPosOpenShiftRequest(new BigDecimal("500.00"), null));

        assertThatThrownBy(() -> service.openShift(tenantId, actorId, new PharmacyPosOpenShiftRequest(new BigDecimal("250.00"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("open cashier shift already exists");
    }

    @Test
    void expiredBatchesAreExcludedFromAvailableStock() {
        MedicineEntity medicine = medicine("Cetirizine");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId()))
                .thenReturn(List.of(stock(medicine.getId(), "OLD", LocalDate.now().minusDays(2), 10, "3.00")));

        assertThatThrownBy(() -> service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("3.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void returnRestocksReusableQuantityAndUpdatesStatus() {
        openShiftFor(actorId);
        MedicineEntity medicine = medicine("Paracetamol");
        StockEntity batch = stock(medicine.getId(), "P1", LocalDate.now().plusDays(30), 10, "4.00");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(batch));

        PharmacyPosSaleResponse created = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("8.00"), PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 2, new BigDecimal("4.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        PharmacyPosSaleResponse returned = service.returnSale(tenantId, UUID.fromString(created.id().toString()), new PharmacyPosReturnRequest(
                "Damaged strip returned",
                PaymentMode.CASH,
                null,
                null,
                List.of(new PharmacyPosReturnLineRequest(created.items().get(0).id(), 1, true))
        ), actorId);

        assertThat(returned.status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(returned.returns()).hasSize(1);
        assertThat(returned.returns().get(0).reusable()).isTrue();

        ArgumentCaptor<InventoryTransactionCommand> captor = ArgumentCaptor.forClass(InventoryTransactionCommand.class);
        verify(inventoryService, org.mockito.Mockito.atLeastOnce()).createTransaction(eq(tenantId), captor.capture(), eq(actorId));
        assertThat(captor.getAllValues().stream().anyMatch(command -> command.transactionType() == com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType.RETURN)).isTrue();
        verify(auditEventPublisher, org.mockito.Mockito.atLeast(2)).record(any());
    }

    @Test
    void getSaleRespectsTenantIsolation() {
        assertThatThrownBy(() -> service.getSale(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sale not found");
    }

    @Test
    void createSaleWithPaymentIsBlockedWithoutOpenShift() {
        MedicineEntity medicine = medicine("Domperidone");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId()))
                .thenReturn(List.of(stock(medicine.getId(), "D1", LocalDate.now().plusDays(30), 3, "5.00")));

        assertThatThrownBy(() -> service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"),
                PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Open cashier shift required");
    }

    @Test
    void uploadPrescriptionAndLinkItToSale() throws Exception {
        openShiftFor(actorId);
        MedicineEntity medicine = medicine("Cefixime");
        StockEntity batch = stock(medicine.getId(), "CF1", LocalDate.now().plusDays(30), 10, "8.00");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(batch));

        PharmacyPosPrescriptionUploadResponse upload = service.uploadPrescription(
                tenantId,
                new MockMultipartFile("file", "rx.pdf", "application/pdf", "hello".getBytes()),
                actorId
        );

        PharmacyPosSaleResponse sale = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", "9999999999", upload.documentId(), OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("8.00"), PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        assertThat(sale.prescriptionDocumentId()).isEqualTo(upload.documentId());
        assertThat(sale.prescriptionFileName()).isEqualTo("rx.pdf");
        assertThat(prescriptions).singleElement().satisfies(row -> assertThat(row.getLinkedSaleId()).isEqualTo(sale.id()));
    }

    @Test
    void createSaleRejectsPrescriptionFromAnotherTenant() {
        MedicineEntity medicine = medicine("Metformin");
        StockEntity batch = stock(medicine.getId(), "M1", LocalDate.now().plusDays(30), 10, "6.00");
        PharmacySalePrescriptionEntity prescription = PharmacySalePrescriptionEntity.create(
                tenantId,
                actorId,
                "rx.pdf",
                "application/pdf",
                4,
                "abc",
                "tenants/" + tenantId + "/documents/rx.pdf"
        );
        prescriptions.add(prescription);
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(batch));
        UUID otherTenantId = UUID.randomUUID();
        when(salePrescriptionRepository.findByTenantIdAndId(otherTenantId, prescription.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSale(otherTenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, prescription.getId(), OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("6.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prescription document not found");
    }

    @Test
    void nonReusableReturnDoesNotRestockStock() {
        openShiftFor(actorId);
        MedicineEntity medicine = medicine("Azithromycin");
        StockEntity batch = stock(medicine.getId(), "AZ1", LocalDate.now().plusDays(30), 10, "10.00");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(batch));

        PharmacyPosSaleResponse created = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"), PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        service.returnSale(tenantId, UUID.fromString(created.id().toString()), new PharmacyPosReturnRequest(
                "Open pack",
                PaymentMode.CASH,
                null,
                null,
                List.of(new PharmacyPosReturnLineRequest(created.items().get(0).id(), 1, false))
        ), actorId);

        ArgumentCaptor<InventoryTransactionCommand> captor = ArgumentCaptor.forClass(InventoryTransactionCommand.class);
        verify(inventoryService, org.mockito.Mockito.times(1)).createTransaction(eq(tenantId), captor.capture(), eq(actorId));
        assertThat(captor.getValue().transactionType()).isEqualTo(com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType.SALE);
    }

    @Test
    void returnBlocksWhenQuantityExceedsRemainingSoldQuantity() {
        openShiftFor(actorId);
        MedicineEntity medicine = medicine("Ofloxacin");
        StockEntity batch = stock(medicine.getId(), "OF1", LocalDate.now().plusDays(30), 10, "7.00");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(batch));

        PharmacyPosSaleResponse created = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("14.00"), PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 2, new BigDecimal("7.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        assertThatThrownBy(() -> service.returnSale(tenantId, UUID.fromString(created.id().toString()), new PharmacyPosReturnRequest(
                "Attempted excess return",
                PaymentMode.CASH,
                null,
                null,
                List.of(new PharmacyPosReturnLineRequest(created.items().get(0).id(), 3, true))
        ), actorId)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return quantity exceeds sold quantity");
    }

    @Test
    void recordPaymentLinksToCurrentOpenShift() {
        PharmacyCashierShiftEntity shift = openShiftFor(actorId);
        MedicineEntity medicine = medicine("Levocetirizine");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(stock(medicine.getId(), "L1", LocalDate.now().plusDays(30), 10, "6.00")));

        PharmacyPosSaleResponse created = service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("6.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        PharmacyPosSaleResponse paid = service.recordPayment(tenantId, created.id(), new PharmacyPosPaymentRequest(
                new BigDecimal("6.00"), PaymentMode.CASH, null, null, LocalDate.now(), OffsetDateTime.now()
        ), actorId);

        assertThat(paid.payments()).hasSize(1);
        assertThat(paid.payments().get(0).cashierShiftId()).isEqualTo(shift.getId());
    }

    @Test
    void closeShiftCalculatesExpectedTotalsAndVariance() {
        PharmacyCashierShiftEntity shift = openShiftFor(actorId);
        MedicineEntity medicine = medicine("Rabeprazole");
        when(medicineRepository.findByTenantIdAndId(tenantId, medicine.getId())).thenReturn(Optional.of(medicine));
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId)).thenReturn(List.of(medicine));
        when(stockRepository.findSellableBatchesForUpdate(tenantId, location.getId(), medicine.getId())).thenReturn(List.of(stock(medicine.getId(), "R1", LocalDate.now().plusDays(30), 10, "100.00")));

        service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), PaymentMode.CASH, null, null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 1, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);
        service.createSale(tenantId, new PharmacyPosCreateSaleRequest(
                null, "Walk-in", null, null, OffsetDateTime.now(), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.00"), PaymentMode.UPI, "upi-ref", null, null,
                List.of(new PharmacyPosSaleLineRequest(medicine.getId(), 2, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO))
        ), actorId);

        PharmacyPosShiftResponse closed = service.closeShift(tenantId, shift.getId(), actorId, false, new PharmacyPosCloseShiftRequest(
                new BigDecimal("95.00"), new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, "Close test"
        ));

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.expectedCashAmount()).isEqualByComparingTo("100.00");
        assertThat(closed.expectedUpiAmount()).isEqualByComparingTo("200.00");
        assertThat(closed.expectedTotalAmount()).isEqualByComparingTo("300.00");
        assertThat(closed.varianceAmount()).isEqualByComparingTo("-5.00");
    }

    @Test
    void cashierCannotCloseAnotherCashierShift() {
        UUID otherCashierId = UUID.randomUUID();
        PharmacyCashierShiftEntity shift = openShiftFor(otherCashierId);

        assertThatThrownBy(() -> service.closeShift(tenantId, shift.getId(), actorId, false, new PharmacyPosCloseShiftRequest(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owning cashier");
    }

    @Test
    void clinicAdminCanViewTenantShifts() {
        PharmacyCashierShiftEntity first = openShiftFor(actorId);
        first.close(actorId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        cashierShiftRepository.save(first);
        openShiftFor(UUID.randomUUID());

        List<PharmacyPosShiftResponse> rows = service.listShifts(tenantId, actorId, true, null, null, null, null);

        assertThat(rows).hasSize(2);
    }

    @Test
    void listShiftsRespectsTenantIsolation() {
        openShiftFor(actorId);

        assertThat(service.listShifts(UUID.randomUUID(), actorId, true, null, null, null, null)).isEmpty();
    }

    private MedicineEntity medicine(String name) {
        MedicineEntity medicine = MedicineEntity.create(tenantId, name, "TABLET");
        medicine.update(name, "TABLET", null, null, null, name, null, null, null, null, "strip", null, null, null, null, null, null, new BigDecimal("10.00"), new BigDecimal("5.00"), true);
        return medicine;
    }

    private StockEntity stock(UUID medicineId, String batchNumber, LocalDate expiryDate, int quantity, String sellingPrice) {
        StockEntity stock = StockEntity.create(tenantId, medicineId, location.getId());
        stock.update(location.getId(), null, null, null, batchNumber, null, expiryDate, LocalDate.now(), "Supplier", quantity, quantity, 2, new BigDecimal("1.00"), new BigDecimal("2.00"), new BigDecimal(sellingPrice), true);
        return stock;
    }

    private PharmacyCashierShiftEntity openShiftFor(UUID cashierUserId) {
        PharmacyCashierShiftEntity shift = PharmacyCashierShiftEntity.open(tenantId, cashierUserId, cashierUserId, new BigDecimal("1000.00"), null);
        cashierShiftRepository.save(shift);
        return shift;
    }
}
