package com.deepthoughtnet.clinic.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispensationEntity;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispensationRepository;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispenseItemEntity;
import com.deepthoughtnet.clinic.api.inventory.db.PrescriptionDispenseItemRepository;
import com.deepthoughtnet.clinic.api.inventory.dto.DispenseRequest;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.StockEntity;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PrescriptionDispensingServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PRESCRIPTION_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID DEFAULT_LOCATION_ID = UUID.randomUUID();
    private final List<StockEntity> stockRows = new ArrayList<>();
    private final List<PrescriptionDispenseItemEntity> dispenseItems = new ArrayList<>();

    private PrescriptionService prescriptionService;
    private PrescriptionMedicineRepository prescriptionMedicineRepository;
    private PrescriptionDispensationRepository dispensationRepository;
    private PrescriptionDispenseItemRepository dispenseItemRepository;
    private MedicineRepository medicineRepository;
    private InventoryLocationRepository locationRepository;
    private StockRepository stockRepository;
    private InventoryService inventoryService;
    private BillingService billingService;
    private PrescriptionDispensingService service;
    private UUID medicineId;
    private MedicineEntity medicineRow;

    @BeforeEach
    void setUp() {
        prescriptionService = mock(PrescriptionService.class);
        prescriptionMedicineRepository = mock(PrescriptionMedicineRepository.class);
        dispensationRepository = mock(PrescriptionDispensationRepository.class);
        dispenseItemRepository = mock(PrescriptionDispenseItemRepository.class);
        medicineRepository = mock(MedicineRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        stockRepository = mock(StockRepository.class);
        inventoryService = mock(InventoryService.class);
        billingService = mock(BillingService.class);
        medicineRow = medicine();
        medicineId = medicineRow.getId();
        InventoryLocationEntity defaultLocation = defaultLocation();

        service = new PrescriptionDispensingService(
                prescriptionService,
                prescriptionMedicineRepository,
                dispensationRepository,
                dispenseItemRepository,
                medicineRepository,
                locationRepository,
                stockRepository,
                inventoryService,
                billingService
        );

        lenient().when(prescriptionService.findById(TENANT_ID, PRESCRIPTION_ID)).thenReturn(Optional.of(prescriptionRecord()));
        lenient().when(prescriptionService.list(TENANT_ID)).thenReturn(List.of(prescriptionRecord()));
        lenient().when(prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(TENANT_ID, PRESCRIPTION_ID))
                .thenReturn(List.of(prescriptionLine()));
        lenient().when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID))
                .thenReturn(List.of(medicineRow));
        lenient().when(medicineRepository.findByTenantIdAndId(TENANT_ID, medicineId)).thenReturn(Optional.of(medicineRow));
        lenient().when(medicineRepository.findByTenantIdAndMedicineNameIgnoreCase(TENANT_ID, "Amoxicillin")).thenReturn(Optional.of(medicineRow));
        lenient().when(locationRepository.findByTenantIdAndDefaultLocationTrue(TENANT_ID))
                .thenReturn(Optional.of(defaultLocation));
        lenient().when(inventoryService.createTransaction(any(), any(), any())).thenReturn(null);
        lenient().when(stockRepository.findByTenantIdAndLocationIdAndMedicineIdAndActiveTrueAndQuantityOnHandGreaterThanOrderByExpiryDateAscUpdatedAtAsc(any(), any(), any(), anyInt()))
                .thenAnswer(invocation -> stockRows.stream()
                        .filter(stock -> stock.getTenantId().equals(invocation.getArgument(0)))
                        .filter(stock -> stock.getLocationId().equals(invocation.getArgument(1)))
                        .filter(stock -> stock.getMedicineId().equals(invocation.getArgument(2)))
                        .filter(stock -> stock.isActive())
                        .filter(stock -> stock.getQuantityOnHand() > invocation.<Integer>getArgument(3))
                        .toList());
        lenient().when(stockRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> stockRows.stream()
                .filter(stock -> stock.getTenantId().equals(invocation.getArgument(0)))
                .filter(stock -> stock.getId().equals(invocation.getArgument(1)))
                .findFirst());
        lenient().when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dispensationRepository.findByTenantIdAndPrescriptionId(TENANT_ID, PRESCRIPTION_ID)).thenReturn(Optional.empty());
        lenient().when(dispensationRepository.save(any(PrescriptionDispensationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dispenseItemRepository.findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(TENANT_ID, PRESCRIPTION_ID)).thenReturn(dispenseItems);
        lenient().when(dispenseItemRepository.save(any(PrescriptionDispenseItemEntity.class))).thenAnswer(invocation -> {
            PrescriptionDispenseItemEntity item = invocation.getArgument(0);
            dispenseItems.removeIf(existing -> existing.getId().equals(item.getId()));
            dispenseItems.add(item);
            return item;
        });
    }

    @Test
    void partialDispenseTracksPendingQuantity() {
        stock(2, LocalDate.now().plusDays(10));

        var response = service.dispense(
                TENANT_ID,
                PRESCRIPTION_ID,
                new DispenseRequest("Amoxicillin", medicineId, 3, null, false, "PARTIAL"),
                ACTOR_ID,
                false
        );

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).status()).isEqualTo("PARTIALLY_DISPENSED");
        assertThat(response.lines().get(0).dispensedQuantity()).isEqualTo(2);
        assertThat(response.lines().get(0).pendingQuantity()).isEqualTo(1);
        assertThat(stockRows.get(0).getQuantityOnHand()).isEqualTo(0);
    }

    @Test
    void expiredStockIsBlockedFromDispensing() {
        stock(5, LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> service.dispense(
                TENANT_ID,
                PRESCRIPTION_ID,
                new DispenseRequest("Amoxicillin", medicineId, 1, null, false, "FULL"),
                ACTOR_ID,
                false
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void viewIncludesDoctorAndTimestamp() {
        stock(8, LocalDate.now().plusDays(20));

        var response = service.view(TENANT_ID, PRESCRIPTION_ID);

        assertThat(response.doctorName()).isEqualTo("Dr. Test Doctor");
        assertThat(response.prescriptionTimestamp()).isNotNull();
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().availabilityStatus()).isEqualTo("AVAILABLE");
    }

    private PrescriptionRecord prescriptionRecord() {
        OffsetDateTime now = OffsetDateTime.now();
        return new PrescriptionRecord(
                PRESCRIPTION_ID,
                TENANT_ID,
                PATIENT_ID,
                "PAT-1",
                "Test Patient",
                UUID.randomUUID(),
                "Dr. Test Doctor",
                CONSULTATION_ID,
                APPOINTMENT_ID,
                "RX-001",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                "Diagnosis",
                "Advice",
                null,
                PrescriptionStatus.FINALIZED,
                now,
                ACTOR_ID,
                null,
                null,
                now,
                now,
                List.of(),
                List.of()
        );
    }

    private PrescriptionMedicineEntity prescriptionLine() {
        return PrescriptionMedicineEntity.create(
                TENANT_ID,
                PRESCRIPTION_ID,
                "Amoxicillin",
                null,
                null,
                "1 tab",
                "1-0-1",
                "5 days",
                null,
                null,
                1
        );
    }

    private MedicineEntity medicine() {
        MedicineEntity entity = MedicineEntity.create(TENANT_ID, "Amoxicillin", "TABLET");
        entity.update(
                "Amoxicillin",
                "TABLET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("10.00"),
                null,
                true
        );
        return entity;
    }

    private InventoryLocationEntity defaultLocation() {
        InventoryLocationEntity entity = mock(InventoryLocationEntity.class);
        when(entity.getId()).thenReturn(DEFAULT_LOCATION_ID);
        when(entity.getTenantId()).thenReturn(TENANT_ID);
        when(entity.getLocationName()).thenReturn("Main Pharmacy");
        when(entity.isDefaultLocation()).thenReturn(true);
        when(entity.isActive()).thenReturn(true);
        return entity;
    }

    private StockEntity stock(int quantityOnHand, LocalDate expiryDate) {
        StockEntity entity = StockEntity.create(TENANT_ID, medicineId, DEFAULT_LOCATION_ID);
        entity.update("B1", null, null, "B1", null, expiryDate, null, null, quantityOnHand, quantityOnHand, 5, null, null, null, true);
        stockRows.add(entity);
        return entity;
    }
}
