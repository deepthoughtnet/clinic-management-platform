package com.deepthoughtnet.clinic.vaccination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineCommand;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillStatus;
import com.deepthoughtnet.clinic.billing.service.model.BillUpsertCommand;
import com.deepthoughtnet.clinic.billing.service.model.DiscountType;
import com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.MedicineRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationEntity;
import com.deepthoughtnet.clinic.vaccination.db.PatientVaccinationRepository;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterEntity;
import com.deepthoughtnet.clinic.vaccination.db.VaccineMasterRepository;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationCommand;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VaccinationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID vaccineId = UUID.randomUUID();
    private PatientEntity patient;
    private VaccineMasterRepository vaccineMasterRepository;
    private PatientVaccinationRepository patientVaccinationRepository;
    private PatientRepository patientRepository;
    private ClinicProfileService clinicProfileService;
    private TenantUserManagementService tenantUserManagementService;
    private BillingService billingService;
    private InventoryService inventoryService;
    private NotificationHistoryService notificationHistoryService;
    private AuditEventPublisher auditEventPublisher;
    private VaccinationService service;
    private final Map<UUID, PatientVaccinationEntity> savedVaccinations = new HashMap<>();
    private final List<BillRecord> billedVaccinations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        patient = patient();
        VaccineMasterEntity vaccine = vaccine();
        vaccineMasterRepository = mock(VaccineMasterRepository.class);
        patientVaccinationRepository = mock(PatientVaccinationRepository.class);
        patientRepository = mock(PatientRepository.class);
        clinicProfileService = mock(ClinicProfileService.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        billingService = mock(BillingService.class);
        inventoryService = mock(InventoryService.class);
        notificationHistoryService = mock(NotificationHistoryService.class);
        auditEventPublisher = mock(AuditEventPublisher.class);
        service = new VaccinationService(
                vaccineMasterRepository,
                patientVaccinationRepository,
                patientRepository,
                clinicProfileService,
                tenantUserManagementService,
                billingService,
                inventoryService,
                notificationHistoryService,
                auditEventPublisher,
                new ObjectMapper()
        );

        when(patientVaccinationRepository.save(any(PatientVaccinationEntity.class))).thenAnswer(invocation -> {
            PatientVaccinationEntity entity = invocation.getArgument(0);
            savedVaccinations.put(entity.getId(), entity);
            return entity;
        });
        when(vaccineMasterRepository.save(any(VaccineMasterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)).thenAnswer(invocation -> List.copyOf(savedVaccinations.values()));
        when(patientVaccinationRepository.findByTenantIdAndPatientIdOrderByGivenDateDesc(tenantId, patientId)).thenAnswer(invocation -> List.copyOf(savedVaccinations.values()));
        when(patientVaccinationRepository.findByTenantIdAndId(eq(tenantId), any())).thenAnswer(invocation -> Optional.ofNullable(savedVaccinations.get(invocation.getArgument(1))));
        when(patientRepository.findByTenantIdAndId(tenantId, patientId)).thenReturn(Optional.of(patient));
        when(patientRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(patient));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(staff(actorId, "Rohit Nair")));
        when(vaccineMasterRepository.findByTenantIdAndId(tenantId, vaccineId)).thenReturn(Optional.of(vaccine));
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId)).thenReturn(List.of(vaccine));
        when(billingService.listByPatient(tenantId, patientId)).thenReturn(List.of());
        when(inventoryService.listMedicines(tenantId)).thenReturn(List.of());
        when(billingService.list(eq(tenantId), any(BillingSearchCriteria.class))).thenAnswer(invocation -> List.copyOf(billedVaccinations));
        when(billingService.createDraft(eq(tenantId), any(BillUpsertCommand.class), eq(actorId))).thenAnswer(invocation -> {
            BillRecord bill = createBillForVaccination();
            billedVaccinations.clear();
            billedVaccinations.add(bill);
            return bill;
        });
    }

    @Test
    void recordVaccinationWithAddToBillCreatesVaccinationBillItemThroughBillingService() {
        ArgumentCaptor<BillUpsertCommand> billCaptor = ArgumentCaptor.forClass(BillUpsertCommand.class);

        PatientVaccinationRecord record = service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-1",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null,
                        false
                ),
                actorId
        );

        verify(billingService).createDraft(eq(tenantId), billCaptor.capture(), eq(actorId));
        verify(billingService, never()).addLineItem(any(), any(), any(), any());

        BillUpsertCommand billCommand = billCaptor.getValue();
        assertThat(billCommand.patientId()).isEqualTo(patientId);
        assertThat(billCommand.billDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(billCommand.lines()).hasSize(1);
        BillLineCommand line = billCommand.lines().getFirst();
        assertThat(line.itemType()).isEqualTo(BillItemType.VACCINATION);
        assertThat(line.itemName()).isEqualTo("COVID-19 Vaccine");
        assertThat(line.quantity()).isEqualTo(1);
        assertThat(line.unitPrice()).isEqualByComparingTo("75.00");
        assertThat(line.referenceId()).isEqualTo(record.id());
        assertThat(line.batchNumber()).isEqualTo("LOT-1");

        assertThat(record.billId()).isNotNull();
        assertThat(record.billNumber()).isEqualTo("BILL-0001");
        assertThat(record.billStatus()).isEqualTo(BillStatus.DRAFT.name());
        assertThat(record.billLineId()).isEqualTo(billedVaccinations.getFirst().lines().getFirst().id());
    }

    @Test
    void billVaccinationCreatesNewBillWhenRequested() {
        PatientVaccinationRecord recorded = service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-2",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        );

        PatientVaccinationRecord billed = service.billVaccination(tenantId, patientId, recorded.id(), null, true, null, actorId);

        assertThat(billed.billId()).isNotNull();
        assertThat(billed.billNumber()).isEqualTo("BILL-0001");
        assertThat(billed.billStatus()).isEqualTo(BillStatus.DRAFT.name());
        assertThat(billed.billLineId()).isNotNull();
    }

    @Test
    void externalVaccinationKeepsRecordedByButDoesNotReuseActorAsAdministrator() {
        PatientVaccinationRecord recorded = service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        null,
                        "External vaccine",
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-EXT",
                        null,
                        "Imported external history",
                        "EXTERNAL",
                        "City Hospital",
                        null,
                        "UNVERIFIED",
                        null,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        );

        assertThat(recorded.source()).isEqualTo("EXTERNAL");
        assertThat(recorded.administeredByUserId()).isNull();
        assertThat(recorded.administeredByUserName()).isNull();
        assertThat(recorded.recordedByUserId()).isEqualTo(actorId);
        assertThat(recorded.recordedByUserName()).isEqualTo("Rohit Nair");
    }

    @Test
    void updateExternalVaccinationRejectsCrossTenantAccess() {
        PatientVaccinationRecord recorded = service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        null,
                        "External vaccine",
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-EXT",
                        null,
                        "Imported external history",
                        "EXTERNAL",
                        "City Hospital",
                        null,
                        "UNVERIFIED",
                        null,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        );

        UUID otherTenantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updateExternalVaccination(
                otherTenantId,
                patientId,
                recorded.id(),
                "Updated external place",
                null,
                "VERIFIED",
                actorId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vaccination record not found");
    }

    @Test
    void externalVaccinationCannotBeBilled() {
        PatientVaccinationRecord recorded = service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        null,
                        "External vaccine",
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-EXT",
                        null,
                        "Imported external history",
                        "EXTERNAL",
                        "City Hospital",
                        null,
                        "UNVERIFIED",
                        null,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        );

        assertThatThrownBy(() -> service.billVaccination(tenantId, patientId, recorded.id(), null, true, null, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("External vaccinations cannot be billed");
    }

    @Test
    void createVaccineRejectsInvalidAgeRange() {
        assertThatThrownBy(() -> service.createVaccine(
                tenantId,
                new com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand(
                        "Yellow Fever",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "IM",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "ADULT",
                        null,
                        365,
                        30,
                        30,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("12.34"),
                        true
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendedAgeDays");
    }

    @Test
    void createVaccineRejectsTooManyDecimals() {
        assertThatThrownBy(() -> service.createVaccine(
                tenantId,
                new com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand(
                        "Yellow Fever",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "IM",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "ADULT",
                        null,
                        30,
                        30,
                        365,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("12.345"),
                        true
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultPrice");
    }

    @Test
    void createVaccineRejectsInactiveInventoryItem() {
        UUID inventoryItemId = UUID.randomUUID();
        when(inventoryService.findMedicine(tenantId, inventoryItemId)).thenReturn(Optional.of(medicine(false)));

        assertThatThrownBy(() -> service.createVaccine(
                tenantId,
                new com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand(
                        "Yellow Fever",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "IM",
                        null,
                        null,
                        null,
                        inventoryItemId,
                        null,
                        false,
                        "ADULT",
                        null,
                        30,
                        30,
                        365,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("12.34"),
                        true
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Inventory item is inactive");
    }

    @Test
    void createVaccineRejectsMissingInventoryItemWhenStockTrackingEnabled() {
        assertThatThrownBy(() -> service.createVaccine(
                tenantId,
                vaccineCommand(true, null, "NONE", null),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inventoryItemId is required");
    }

    @Test
    void createVaccineAllowsMissingInventoryItemWhenStockTrackingDisabled() {
        var record = service.createVaccine(
                tenantId,
                vaccineCommand(false, null, "NONE", null),
                actorId
        );

        assertThat(record.stockTrackingEnabled()).isFalse();
        assertThat(record.inventoryItemId()).isNull();
    }

    @Test
    void createVaccineRejectsMissingCatchUpMaxAgeWhenPolicyRequiresIt() {
        assertThatThrownBy(() -> service.createVaccine(
                tenantId,
                vaccineCommand(false, null, "ALLOWED_UNTIL_AGE", null),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catchUpMaxAgeDays is required");
    }

    @Test
    void createVaccineAllowsCatchUpMaxAgeForOtherPoliciesToRemainOptional() {
        var record = service.createVaccine(
                tenantId,
                vaccineCommand(false, null, "NONE", null),
                actorId
        );

        assertThat(record.catchUpPolicy()).isEqualTo("NONE");
        assertThat(record.catchUpMaxAgeDays()).isNull();
    }

    @Test
    void createVaccineAcceptsClinicalIndicationsUpToBackendLimit() {
        String clinicalIndications = "a".repeat(1000);

        VaccineMasterEntity created = VaccineMasterEntity.create(tenantId, "seed");
        when(vaccineMasterRepository.findByTenantIdAndVaccineNameIgnoreCase(eq(tenantId), eq("Yellow Fever"))).thenReturn(Optional.empty());
        when(vaccineMasterRepository.findByTenantIdOrderByVaccineNameAsc(tenantId)).thenReturn(List.of(created));

        var record = service.createVaccine(
                tenantId,
                new com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand(
                        "Yellow Fever",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "IM",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        "ADULT",
                        null,
                        30,
                        30,
                        365,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        clinicalIndications,
                        new BigDecimal("12.34"),
                        true
                ),
                actorId
        );

        assertThat(record.clinicalIndications()).hasSize(1000);
    }

    @Test
    void recordVaccinationRejectsInvalidAdministeredByAndInvertedDueDate() {
        UUID invalidAdminId = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 7),
                        LocalDate.of(2026, 7, 1),
                        "LOT-3",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        invalidAdminId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nextDueDate");

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 7),
                        null,
                        "LOT-4",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        invalidAdminId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administeredByUserId");
    }

    @Test
    void recordVaccinationRejectsFutureDatesBirthViolationsAndDuplicates() {
        when(patient.getDateOfBirth()).thenReturn(LocalDate.of(2026, 7, 15));

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        0,
                        LocalDate.of(2026, 7, 20),
                        null,
                        "LOT-4",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        actorId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive whole number");

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.now().plusDays(1),
                        null,
                        "LOT-5",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        actorId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 1),
                        null,
                        "LOT-6",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        actorId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date of birth");

        service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 20),
                        null,
                        "LOT-7",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        actorId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        );

        assertThatThrownBy(() -> service.recordVaccination(
                tenantId,
                patientId,
                new PatientVaccinationCommand(
                        vaccineId,
                        null,
                        1,
                        LocalDate.of(2026, 7, 20),
                        null,
                        "LOT-7",
                        null,
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        actorId,
                        null,
                        false,
                        null,
                        false
                ),
                actorId
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been recorded");
    }

    private PatientEntity patient() {
        PatientEntity patient = mock(PatientEntity.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getPatientNumber()).thenReturn("PAT-1");
        when(patient.getFirstName()).thenReturn("Test");
        when(patient.getLastName()).thenReturn("Patient");
        when(patient.getMobile()).thenReturn("9999999999");
        when(patient.getEmail()).thenReturn("test.patient@example.com");
        when(patient.getAgeYears()).thenReturn(32);
        when(patient.getGender()).thenReturn(PatientGender.MALE);
        when(patient.getAllergies()).thenReturn(null);
        return patient;
    }

    private TenantUserRecord staff(UUID appUserId, String displayName) {
        return new TenantUserRecord(
                appUserId,
                tenantId,
                "keycloak-sub",
                "rohit.nair@jfcuat.local",
                displayName,
                "ACTIVE",
                "RECEPTIONIST",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "COMPLETED"
        );
    }

    private VaccineMasterEntity vaccine() {
        VaccineMasterEntity vaccine = mock(VaccineMasterEntity.class);
        when(vaccine.getId()).thenReturn(vaccineId);
        when(vaccine.getTenantId()).thenReturn(tenantId);
        when(vaccine.getVaccineName()).thenReturn("COVID-19 Vaccine");
        when(vaccine.getDescription()).thenReturn(null);
        when(vaccine.getManufacturer()).thenReturn("Pfizer");
        when(vaccine.getBrandName()).thenReturn("Comirnaty");
        when(vaccine.getVaccineGroup()).thenReturn(null);
        when(vaccine.getDoseNumber()).thenReturn(1);
        when(vaccine.getRoute()).thenReturn(null);
        when(vaccine.getAdministrationSite()).thenReturn(null);
        when(vaccine.getStorageTemperature()).thenReturn(null);
        when(vaccine.getNdcBarcode()).thenReturn(null);
        when(vaccine.getInventoryItemId()).thenReturn(null);
        when(vaccine.getInventoryItemCode()).thenReturn(null);
        when(vaccine.isStockTrackingEnabled()).thenReturn(false);
        when(vaccine.getScheduleType()).thenReturn(null);
        when(vaccine.getAgeGroup()).thenReturn(null);
        when(vaccine.getMinAgeDays()).thenReturn(null);
        when(vaccine.getRecommendedAgeDays()).thenReturn(null);
        when(vaccine.getMaxAgeDays()).thenReturn(null);
        when(vaccine.getRecommendedGapDays()).thenReturn(null);
        when(vaccine.getBoosterGapDays()).thenReturn(null);
        when(vaccine.getBoosterRules()).thenReturn(null);
        when(vaccine.isRecurring()).thenReturn(false);
        when(vaccine.getRecurrenceDays()).thenReturn(null);
        when(vaccine.getRecommendationPolicy()).thenReturn(null);
        when(vaccine.getCatchUpPolicy()).thenReturn(null);
        when(vaccine.getCatchUpMaxAgeDays()).thenReturn(null);
        when(vaccine.getApplicableAgeGroup()).thenReturn(null);
        when(vaccine.getClinicalIndications()).thenReturn(null);
        when(vaccine.getDefaultPrice()).thenReturn(new BigDecimal("75.00"));
        when(vaccine.isActive()).thenReturn(true);
        return vaccine;
    }

    private MedicineRecord medicine(boolean active) {
        return new MedicineRecord(
                UUID.randomUUID(),
                tenantId,
                "Vaccine stock",
                "TABLET",
                "BARCODE",
                null,
                "EXT-1",
                "Generic",
                "Brand",
                "Category",
                "Dosage",
                "Strength",
                "Unit",
                "Manufacturer",
                "Default dosage",
                "Default frequency",
                1,
                "Morning",
                "Instructions",
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                active,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private BillRecord createBillForVaccination() {
        UUID billId = UUID.randomUUID();
        UUID billLineId = UUID.randomUUID();
        UUID vaccinationId = savedVaccinations.values().stream().findFirst().map(PatientVaccinationEntity::getId).orElse(UUID.randomUUID());
        BillLineRecord line = new BillLineRecord(
                billLineId,
                BillItemType.VACCINATION,
                "COVID-19 Vaccine",
                1,
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                vaccinationId,
                1,
                BigDecimal.ZERO,
                "LOT-1",
                null
        );
        return new BillRecord(
                billId,
                tenantId,
                "BILL-0001",
                patientId,
                "PAT-1",
                "Test Patient",
                null,
                null,
                LocalDate.of(2026, 7, 7),
                BillStatus.DRAFT,
                new BigDecimal("75.00"),
                DiscountType.NONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("75.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("75.00"),
                null,
                "Vaccination billing",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(line)
        );
    }

    private com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand vaccineCommand(boolean stockTrackingEnabled, UUID inventoryItemId, String catchUpPolicy, Integer catchUpMaxAgeDays) {
        return new com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand(
                "Yellow Fever",
                null,
                null,
                null,
                null,
                null,
                "IM",
                null,
                null,
                null,
                inventoryItemId,
                null,
                stockTrackingEnabled,
                "ADULT",
                null,
                30,
                30,
                365,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                catchUpPolicy,
                catchUpMaxAgeDays,
                null,
                null,
                new BigDecimal("12.34"),
                true
        );
    }
}
