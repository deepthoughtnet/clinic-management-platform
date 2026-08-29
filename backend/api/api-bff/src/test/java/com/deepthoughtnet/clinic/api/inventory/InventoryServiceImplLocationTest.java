package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryServiceImpl;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceImplLocationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private InventoryLocationRepository locationRepository;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        MedicineRepository medicineRepository = mock(MedicineRepository.class);
        StockRepository stockRepository = mock(StockRepository.class);
        InventoryTransactionRepository transactionRepository = mock(InventoryTransactionRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        PhysicalCountSessionRepository physicalCountSessionRepository = mock(PhysicalCountSessionRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(UUID.randomUUID());
        when(medicine.getTenantId()).thenReturn(TENANT_ID);
        when(medicine.getMedicineName()).thenReturn("Paracetamol");
        when(medicine.getMedicineType()).thenReturn("TABLET");
        when(medicine.isActive()).thenReturn(true);
        when(medicineRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(medicine));

        when(locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(TENANT_ID)).thenReturn(List.of());
        when(locationRepository.findByTenantIdAndLocationNameIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(locationRepository.existsByTenantIdAndLocationNameIgnoreCaseAndIdNot(any(), any(), any())).thenReturn(false);
        when(locationRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());
        when(locationRepository.save(any(InventoryLocationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
    void createLocationPersistsSecondaryPharmacyAsActiveAndNonDefault() {
        InventoryLocationRecord saved = service.saveLocation(
                TENANT_ID,
                null,
                new InventoryLocationUpsertCommand("Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", false, true),
                ACTOR_ID
        );

        assertThat(saved.locationName()).isEqualTo("Secondary Pharmacy");
        assertThat(saved.locationCode()).isEqualTo("SECONDARY_PHARMACY");
        assertThat(saved.locationType()).isEqualTo("PHARMACY");
        assertThat(saved.defaultLocation()).isFalse();
        assertThat(saved.active()).isTrue();
    }

    @Test
    void listLocationsIncludesCreatedSecondaryPharmacy() {
        InventoryLocationEntity secondary = InventoryLocationEntity.create(TENANT_ID, "Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", false);
        InventoryLocationEntity main = InventoryLocationEntity.create(TENANT_ID, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true);
        when(locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(TENANT_ID)).thenReturn(List.of(main, secondary));

        assertThat(service.listLocations(TENANT_ID))
                .extracting(InventoryLocationRecord::locationName)
                .contains("Secondary Pharmacy");
    }

    @Test
    void saveLocationRejectsDuplicateNameWithinTenant() {
        InventoryLocationEntity duplicate = InventoryLocationEntity.create(TENANT_ID, "Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", false);
        when(locationRepository.findByTenantIdAndLocationNameIgnoreCase(TENANT_ID, "Secondary Pharmacy")).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.saveLocation(
                TENANT_ID,
                null,
                new InventoryLocationUpsertCommand("Secondary Pharmacy", "WARD_PHARMACY", "PHARMACY", false, true),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Location already exists with this name");
    }

    @Test
    void saveLocationCanDeactivateAnExistingLocation() {
        InventoryLocationEntity existing = InventoryLocationEntity.create(TENANT_ID, "Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", false);
        when(locationRepository.findByTenantIdAndId(TENANT_ID, existing.getId())).thenReturn(Optional.of(existing));

        InventoryLocationRecord updated = service.saveLocation(
                TENANT_ID,
                existing.getId(),
                new InventoryLocationUpsertCommand("Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", false, false),
                ACTOR_ID
        );

        assertThat(updated.active()).isFalse();
        assertThat(updated.locationName()).isEqualTo("Secondary Pharmacy");
    }

    @Test
    void saveLocationClearsPreviousDefaultWhenNewDefaultIsSelected() {
        InventoryLocationEntity currentDefault = InventoryLocationEntity.create(TENANT_ID, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true);
        when(locationRepository.findByTenantIdOrderByDefaultLocationDescLocationNameAsc(TENANT_ID)).thenReturn(List.of(currentDefault));

        InventoryLocationRecord created = service.saveLocation(
                TENANT_ID,
                null,
                new InventoryLocationUpsertCommand("Secondary Pharmacy", "SECONDARY_PHARMACY", "PHARMACY", true, true),
                ACTOR_ID
        );

        assertThat(created.defaultLocation()).isTrue();
        assertThat(currentDefault.isDefaultLocation()).isFalse();
    }
}
