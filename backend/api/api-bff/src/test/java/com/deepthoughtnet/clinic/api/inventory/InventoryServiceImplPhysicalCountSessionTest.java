package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.inventory.db.InventoryLocationEntity;
import com.deepthoughtnet.clinic.inventory.db.InventoryLocationRepository;
import com.deepthoughtnet.clinic.inventory.db.InventoryTransactionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionEntity;
import com.deepthoughtnet.clinic.inventory.db.PhysicalCountSessionRepository;
import com.deepthoughtnet.clinic.inventory.db.StockRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryServiceImpl;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountAuditFields;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountReviewChecklist;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionLine;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionSaveCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryServiceImplPhysicalCountSessionTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    private MedicineRepository medicineRepository;
    private StockRepository stockRepository;
    private InventoryTransactionRepository transactionRepository;
    private InventoryLocationRepository locationRepository;
    private PhysicalCountSessionRepository physicalCountSessionRepository;
    private Map<UUID, PhysicalCountSessionEntity> savedSessions;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        medicineRepository = mock(MedicineRepository.class);
        stockRepository = mock(StockRepository.class);
        transactionRepository = mock(InventoryTransactionRepository.class);
        locationRepository = mock(InventoryLocationRepository.class);
        physicalCountSessionRepository = mock(PhysicalCountSessionRepository.class);
        AuditEventPublisher auditEventPublisher = mock(AuditEventPublisher.class);
        savedSessions = new LinkedHashMap<>();

        MedicineEntity medicine = mock(MedicineEntity.class);
        when(medicine.getId()).thenReturn(UUID.randomUUID());
        when(medicine.getTenantId()).thenReturn(TENANT_ID);
        when(medicine.getMedicineName()).thenReturn("Paracetamol");
        when(medicine.getMedicineType()).thenReturn("TABLET");
        when(medicine.isActive()).thenReturn(true);
        when(medicineRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.of(medicine));

        InventoryLocationEntity location = InventoryLocationEntity.create(TENANT_ID, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true);
        when(locationRepository.findByTenantIdAndId(TENANT_ID, LOCATION_ID)).thenReturn(Optional.of(location));
        when(locationRepository.findByTenantIdAndId(OTHER_TENANT_ID, LOCATION_ID)).thenReturn(Optional.empty());

        when(physicalCountSessionRepository.save(any(PhysicalCountSessionEntity.class))).thenAnswer(invocation -> {
            PhysicalCountSessionEntity entity = invocation.getArgument(0);
            savedSessions.put(entity.getId(), entity);
            return entity;
        });
        when(physicalCountSessionRepository.findByTenantIdAndId(TENANT_ID, SESSION_ID)).thenAnswer(invocation -> Optional.ofNullable(savedSessions.get(invocation.getArgument(1))));
        when(physicalCountSessionRepository.findByTenantIdAndId(OTHER_TENANT_ID, SESSION_ID)).thenReturn(Optional.empty());
        when(physicalCountSessionRepository.findByTenantIdOrderByUpdatedAtDesc(TENANT_ID)).thenAnswer(invocation -> new ArrayList<>(savedSessions.values()));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new InventoryServiceImpl(
                medicineRepository,
                stockRepository,
                transactionRepository,
                locationRepository,
                physicalCountSessionRepository,
                auditEventPublisher,
                objectMapper
        );
    }

    @Test
    void savePhysicalCountSessionPersistsAndReloadsExactValuesWithoutChangingInventory() {
        PhysicalCountSessionRecord saved = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThat(saved.id()).isEqualTo(SESSION_ID);
        assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.locationName()).isEqualTo("Main Pharmacy");
        assertThat(saved.status()).isEqualTo("IN_PROGRESS");
        assertThat(saved.lines()).extracting(PhysicalCountSessionLine::countedQty).containsExactly("55", "98");
        assertThat(saved.lines()).extracting(PhysicalCountSessionLine::reason).containsExactly("System match", "Variance reason");

        PhysicalCountSessionRecord reloaded = service.findPhysicalCountSession(TENANT_ID, SESSION_ID).orElseThrow();
        assertThat(reloaded.locationName()).isEqualTo("Main Pharmacy");
        assertThat(reloaded.lines()).extracting(PhysicalCountSessionLine::countedQty).containsExactly("55", "98");
        assertThat(reloaded.lines()).extracting(PhysicalCountSessionLine::reason).containsExactly("System match", "Variance reason");
        assertThat(savedSessions).hasSize(1);
        verifyNoInteractions(stockRepository);
    }

    @Test
    void secondSaveUpdatesExistingSessionInsteadOfCreatingDuplicateRecords() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("PHARMACIST"));
        PhysicalCountSessionRecord updated = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "54", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThat(updated.lines()).extracting(PhysicalCountSessionLine::countedQty).containsExactly("54", "98");
        assertThat(savedSessions).hasSize(1);
    }

    @Test
    void savePhysicalCountSessionRejectsUnknownLocationInCurrentTenant() {
        UUID missingLocation = UUID.randomUUID();

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, commandWithLocation(missingLocation), ACTOR_ID, roles("PHARMACIST")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Location not found");
    }

    @Test
    void savePhysicalCountSessionRespectsTenantIsolationForReloads() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThat(service.findPhysicalCountSession(OTHER_TENANT_ID, SESSION_ID)).isEmpty();
    }

    @Test
    void pharmacistCanCreateEditSaveAndSubmitPhysicalCountSessions() {
        PhysicalCountSessionRecord created = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles("PHARMACIST"));
        assertThat(created.status()).isEqualTo("DRAFT");

        PhysicalCountSessionRecord inProgress = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("PHARMACIST"));
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");

        PhysicalCountSessionRecord submitted = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("PHARMACIST"));
        assertThat(submitted.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void pharmacistCannotReviewApproveRejectReturnOrPostPhysicalCountSessions() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("PHARMACIST"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "55", "98"), ACTOR_ID, roles("PHARMACIST")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have permission to review physical count sessions.");
    }

    @Test
    void inventoryManagerCannotCreateOrEditMakerSessions() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You do not have permission to edit physical count sessions.");
    }

    @Test
    void inventoryManagerCanReviewApproveRejectReturnAndPostButCannotEditMakerCounts() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        PhysicalCountSessionRecord reviewed = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER"));
        assertThat(reviewed.status()).isEqualTo("REVIEWED");

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "54", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Counted quantities and maker line details cannot be modified in the current workflow state.");

        PhysicalCountSessionRecord approved = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "APPROVED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER"));
        assertThat(approved.status()).isEqualTo("APPROVED");

        PhysicalCountSessionRecord posted = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "POSTED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER"));
        assertThat(posted.status()).isEqualTo("POSTED");
    }

    @Test
    void invalidCheckerLifecycleTransitionsAreRejected() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("PHARMACIST"));

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "APPROVED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Submitted sessions can only be saved as SUBMITTED or REVIEWED.");

        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER"));

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "POSTED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reviewed sessions can only be saved as REVIEWED, APPROVED, REJECTED, or IN_PROGRESS.");
    }

    @Test
    void clinicAdminCanPerformEveryPhysicalCountAction() {
        PhysicalCountSessionRecord created = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(created.status()).isEqualTo("DRAFT");

        PhysicalCountSessionRecord inProgress = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");

        PhysicalCountSessionRecord submitted = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(submitted.status()).isEqualTo("SUBMITTED");

        PhysicalCountSessionRecord reviewed = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(reviewed.status()).isEqualTo("REVIEWED");

        PhysicalCountSessionRecord approved = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "APPROVED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(approved.status()).isEqualTo("APPROVED");

        PhysicalCountSessionRecord posted = service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "POSTED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        assertThat(posted.status()).isEqualTo("POSTED");
    }

    @Test
    void postedPhysicalCountSessionsCannotBeModifiedByNonClinicAdmins() {
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "IN_PROGRESS", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "SUBMITTED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "REVIEWED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "APPROVED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));
        service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "POSTED", "55", "98"), ACTOR_ID, roles("CLINIC_ADMIN"));

        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "APPROVED", "55", "98"), ACTOR_ID, roles("PHARMACY_INVENTORY_MANAGER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Posted physical count sessions cannot be modified.");
    }

    @Test
    void unauthorizedRolesCannotSavePhysicalCountSessions() {
        for (String role : List.of("AUDITOR", "PHARMACY_POS_USER", "TENANT_ADMIN")) {
        assertThatThrownBy(() -> service.savePhysicalCountSession(TENANT_ID, SESSION_ID, command("Main Pharmacy", "DRAFT", "55", "98"), ACTOR_ID, roles(role)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("You do not have permission to save physical count sessions.");
        }
    }

    private PhysicalCountSessionSaveCommand command(String locationName, String status, String countedQtyOne, String countedQtyTwo) {
        return new PhysicalCountSessionSaveCommand(
                "UAT Physical Count 01",
                LOCATION_ID,
                locationName,
                "ENTIRE_INVENTORY",
                "Entire Inventory",
                "MONTHLY_COUNT",
                status,
                List.of(
                        line("line-1", "AMX-UAT-B01", "Amoxicillin 500 mg Capsule", countedQtyOne, "System match"),
                        line("line-2", "CET-UAT-B01", "Cetirizine 10 mg Tablet", countedQtyTwo, "Variance reason")
                ),
                new PhysicalCountAuditFields(
                        "UAT User",
                        "2026-08-28T10:00:00Z",
                        "UAT User",
                        "2026-08-28T10:00:00Z",
                        "2026-08-28T10:15:00Z",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PhysicalCountReviewChecklist(false, false, false, false)
                )
        );
    }

    private PhysicalCountSessionSaveCommand commandWithLocation(UUID locationId) {
        return new PhysicalCountSessionSaveCommand(
                "UAT Physical Count 01",
                locationId,
                "Missing Location",
                "ENTIRE_INVENTORY",
                "Entire Inventory",
                "MONTHLY_COUNT",
                "IN_PROGRESS",
                List.of(line("line-1", "AMX-UAT-B01", "Amoxicillin 500 mg Capsule", "55", "System match")),
                new PhysicalCountAuditFields(
                        "UAT User",
                        "2026-08-28T10:00:00Z",
                        "UAT User",
                        "2026-08-28T10:00:00Z",
                        "2026-08-28T10:15:00Z",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new PhysicalCountReviewChecklist(false, false, false, false)
                )
        );
    }

    private PhysicalCountSessionLine line(String id, String batchNumber, String medicineName, String countedQty, String reason) {
        return new PhysicalCountSessionLine(
                id,
                UUID.randomUUID(),
                medicineName,
                batchNumber,
                LOCATION_ID,
                "Main Pharmacy",
                UUID.randomUUID(),
                55,
                countedQty,
                reason,
                null,
                false,
                false
        );
    }

    private Set<String> roles(String... roles) {
        return Set.of(roles);
    }
}
