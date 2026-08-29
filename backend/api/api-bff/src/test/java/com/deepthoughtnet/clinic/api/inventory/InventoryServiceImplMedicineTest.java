package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryServiceImpl;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceImplMedicineTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private MedicineRepository medicineRepository;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        InventoryTransactionRepository transactionRepository = mock(InventoryTransactionRepository.class);
        InventoryLocationRepository locationRepository = mock(InventoryLocationRepository.class);
        PhysicalCountSessionRepository physicalCountSessionRepository = mock(PhysicalCountSessionRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID)).thenReturn(List.of());
        when(medicineRepository.findByTenantIdAndBarcodeIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(medicineRepository.findByTenantIdAndExternalCodeIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(medicineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = new InventoryServiceImpl(
                medicineRepository,
                stockRepository,
                transactionRepository,
                locationRepository,
                physicalCountSessionRepository,
                auditEventPublisher,
                new ObjectMapper()
        );
    }

    @Test
    void createsMedicineWithTrimmedValues() {
        MedicineRecord saved = service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "  Paracetamol 500  ",
                        "tablet",
                        " PARA-500_A ",
                        " QR-500 ",
                        " EXT-500 ",
                        " Paracetamol ",
                        " Dolo ",
                        " Analgesic ",
                        " Tablet ",
                        " 500 mg ",
                        " Tablet ",
                        " ACME Pharma ",
                        " 1 tablet ",
                        " Twice daily ",
                        5,
                        " AFTER_FOOD ",
                        " Take after meals ",
                        new BigDecimal("25.00"),
                        new BigDecimal("5.00"),
                        true
                ),
                ACTOR_ID
        );

        assertThat(saved.medicineName()).isEqualTo("Paracetamol 500");
        assertThat(saved.medicineType()).isEqualTo("TABLET");
        assertThat(saved.strength()).isEqualTo("500 mg");
        assertThat(saved.barcode()).isEqualTo("PARA-500_A");
        assertThat(saved.defaultTiming()).isEqualTo("AFTER_FOOD");
        assertThat(saved.defaultPrice()).isEqualByComparingTo("25.00");
        assertThat(saved.taxRate()).isEqualByComparingTo("5.00");
    }

    @Test
    void createsSachetMedicineAndKeepsItEditable() {
        MedicineRecord created = service.createMedicine(
                TENANT_ID,
                medicineCommand("ORS Sachet", "SACHET", "ORS 21.8 g", null, new BigDecimal("18.00")),
                ACTOR_ID
        );

        assertThat(created.medicineType()).isEqualTo("SACHET");
        assertThat(created.strength()).isEqualTo("ORS 21.8 g");

        MedicineEntity existing = MedicineEntity.create(TENANT_ID, "ORS Sachet", "SACHET");
        existing.update("ORS Sachet", "SACHET", null, null, null, null, null, null, null, "ORS 21.8 g", null, null, null, null, null, null, null, null, null, true);
        when(medicineRepository.findByTenantIdAndId(TENANT_ID, existing.getId())).thenReturn(Optional.of(existing));

        MedicineRecord updated = service.updateMedicine(
                TENANT_ID,
                existing.getId(),
                medicineCommand("ORS Sachet Plus", "SACHET", "ORS 21.8 g", "AFTER_FOOD", new BigDecimal("20.00")),
                ACTOR_ID
        );

        assertThat(updated.medicineType()).isEqualTo("SACHET");
        assertThat(updated.medicineName()).isEqualTo("ORS Sachet Plus");
        assertThat(updated.defaultTiming()).isEqualTo("AFTER_FOOD");
    }

    @Test
    void allowsSameNameWhenTypeOrStrengthDiffers() {
        MedicineEntity existing = MedicineEntity.create(TENANT_ID, "Paracetamol 500", "TABLET");
        existing.update("Paracetamol 500", "TABLET", null, null, null, null, null, null, null, "500 mg", null, null, null, null, null, null, null, null, null, true);
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID)).thenReturn(List.of(existing));

        MedicineRecord saved = service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 650",
                        "TABLET",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "650 mg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                ),
                ACTOR_ID
        );

        assertThat(saved.medicineName()).isEqualTo("Paracetamol 650");
        assertThat(saved.strength()).isEqualTo("650 mg");
    }

    @Test
    void rejectsDuplicateIdentityWithinTenant() {
        MedicineEntity existing = MedicineEntity.create(TENANT_ID, "Paracetamol 500", "TABLET");
        existing.update("Paracetamol 500", "TABLET", null, null, null, null, null, null, null, "500 mg", null, null, null, null, null, null, null, null, null, true);
        when(medicineRepository.findByTenantIdOrderByMedicineNameAsc(TENANT_ID)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 500",
                        "TABLET",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "500 mg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                ),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Medicine already exists with the same name, type, and strength");
    }

    @Test
    void rejectsInvalidMedicineType() {
        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 500",
                        "PILL",
                        "bad code",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "500 mg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00"),
                        new BigDecimal("5.00"),
                        true
                ),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("medicineType must be one of TABLET, CAPSULE, SYRUP, INJECTION, DROP, OINTMENT, SACHET, OTHER");
    }

    @Test
    void rejectsInvalidTimingCodes() {
        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                medicineCommand("Paracetamol 500", "TABLET", "500 mg", "After food", new BigDecimal("25.00")),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTiming must be one of BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, ANYTIME");
    }

    @Test
    void rejectsInvalidBarcodeFormat() {
        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 500",
                        "TABLET",
                        "bad code",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "500 mg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00"),
                        new BigDecimal("5.00"),
                        true
                ),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Barcode can use letters, numbers, dashes, underscores, and slashes only.");
    }

    @Test
    void rejectsSymbolOnlyGenericAndBrandNames() {
        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 500",
                        "TABLET",
                        null,
                        null,
                        null,
                        "@@@###",
                        "@@@###",
                        null,
                        null,
                        "500 mg",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("25.00"),
                        new BigDecimal("5.00"),
                        true
                ),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generic name must include a letter or number.");
    }

    @Test
    void rejectsInvalidNumericBoundaries() {
        assertThatThrownBy(() -> service.createMedicine(
                TENANT_ID,
                new MedicineUpsertCommand(
                        "Paracetamol 500",
                        "TABLET",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "500 mg",
                        null,
                        null,
                        null,
                        null,
                        0,
                        "ANYTIME",
                        null,
                        new BigDecimal("1000000.00"),
                        new BigDecimal("101.00"),
                        true
                ),
                ACTOR_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultDurationDays must be between 1 and 365");
    }

    private MedicineUpsertCommand medicineCommand(String medicineName, String medicineType, String strength, String defaultTiming, BigDecimal defaultPrice) {
        return new MedicineUpsertCommand(
                medicineName,
                medicineType,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                strength,
                null,
                null,
                null,
                null,
                null,
                defaultTiming,
                null,
                defaultPrice,
                new BigDecimal("5.00"),
                true
        );
    }
}
