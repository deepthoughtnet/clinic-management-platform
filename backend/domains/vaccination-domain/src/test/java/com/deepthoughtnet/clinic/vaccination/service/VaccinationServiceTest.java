package com.deepthoughtnet.clinic.vaccination.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        PatientEntity patient = patient();
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
        when(patientVaccinationRepository.findByTenantIdOrderByGivenDateDesc(tenantId)).thenAnswer(invocation -> List.copyOf(savedVaccinations.values()));
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
                        "Routine vaccination",
                        "INTERNAL",
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        null
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
        VaccineMasterEntity vaccine = VaccineMasterEntity.create(tenantId, "COVID-19 Vaccine");
        vaccine.update(
                "COVID-19 Vaccine",
                null,
                "Pfizer",
                "Comirnaty",
                null,
                1,
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
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("75.00"),
                true
        );
        return vaccine;
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
}
