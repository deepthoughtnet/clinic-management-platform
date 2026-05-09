package com.deepthoughtnet.clinic.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestRepository;
import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionUpsertCommand;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrescriptionServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();
    private static final UUID CONSULTATION_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final Map<UUID, PrescriptionEntity> prescriptions = new LinkedHashMap<>();
    private final Map<UUID, List<PrescriptionMedicineEntity>> medicines = new LinkedHashMap<>();
    private final Map<UUID, List<PrescriptionTestEntity>> tests = new LinkedHashMap<>();

    private PrescriptionRepository prescriptionRepository;
    private PrescriptionMedicineRepository medicineRepository;
    private PrescriptionTestRepository testRepository;
    private ConsultationService consultationService;
    private PatientRepository patientRepository;
    private TenantUserManagementService tenantUserManagementService;
    private ClinicProfileService clinicProfileService;
    private AuditEventPublisher auditEventPublisher;
    private PrescriptionService service;

    @BeforeEach
    void setUp() {
        prescriptionRepository = mock(PrescriptionRepository.class);
        medicineRepository = mock(PrescriptionMedicineRepository.class);
        testRepository = mock(PrescriptionTestRepository.class);
        consultationService = mock(ConsultationService.class);
        patientRepository = mock(PatientRepository.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        clinicProfileService = mock(ClinicProfileService.class);
        auditEventPublisher = mock(AuditEventPublisher.class);

        service = new PrescriptionService(
                prescriptionRepository,
                medicineRepository,
                testRepository,
                consultationService,
                patientRepository,
                tenantUserManagementService,
                clinicProfileService,
                auditEventPublisher,
                new ObjectMapper()
        );

        lenient().when(prescriptionRepository.save(any(PrescriptionEntity.class))).thenAnswer(invocation -> {
            PrescriptionEntity entity = invocation.getArgument(0);
            prescriptions.put(entity.getId(), entity);
            return entity;
        });
        lenient().when(prescriptionRepository.findByTenantIdAndId(any(), any())).thenAnswer(invocation -> {
            UUID tenantId = invocation.getArgument(0);
            UUID id = invocation.getArgument(1);
            PrescriptionEntity entity = prescriptions.get(id);
            return entity != null && tenantId.equals(entity.getTenantId()) ? Optional.of(entity) : Optional.empty();
        });
        lenient().when(prescriptionRepository.findByTenantIdOrderByCreatedAtDesc(any())).thenAnswer(invocation -> byTenant(invocation.getArgument(0), null, false));
        lenient().when(prescriptionRepository.findByTenantIdAndDoctorUserIdOrderByCreatedAtDesc(any(), any())).thenAnswer(invocation -> byTenant(invocation.getArgument(0), invocation.getArgument(1), true));
        lenient().when(prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(any(), any())).thenAnswer(invocation -> byTenantByPatient(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(prescriptionRepository.findByTenantIdAndConsultationIdOrderByVersionNumberAsc(any(), any())).thenAnswer(invocation -> byTenantAndConsultation(invocation.getArgument(0), invocation.getArgument(1), true));
        lenient().when(prescriptionRepository.findByTenantIdAndConsultationIdOrderByVersionNumberDesc(any(), any())).thenAnswer(invocation -> byTenantAndConsultation(invocation.getArgument(0), invocation.getArgument(1), false));
        lenient().when(prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(any(), any())).thenAnswer(invocation -> byTenantAndConsultation(invocation.getArgument(0), invocation.getArgument(1), false).stream().findFirst());
        lenient().when(prescriptionRepository.findFirstByTenantIdAndParentPrescriptionIdOrderByVersionNumberDesc(any(), any())).thenAnswer(invocation -> {
            UUID tenantId = invocation.getArgument(0);
            UUID parentId = invocation.getArgument(1);
            return prescriptions.values().stream()
                    .filter(entity -> tenantId.equals(entity.getTenantId()))
                    .filter(entity -> parentId.equals(entity.getParentPrescriptionId()))
                    .sorted((left, right) -> Integer.compare(right.getVersionNumber() == null ? 0 : right.getVersionNumber(), left.getVersionNumber() == null ? 0 : left.getVersionNumber()))
                    .findFirst();
        });
        lenient().when(prescriptionRepository.findByTenantIdAndPrescriptionNumber(any(), any())).thenReturn(Optional.empty());

        lenient().when(medicineRepository.save(any(PrescriptionMedicineEntity.class))).thenAnswer(invocation -> {
            PrescriptionMedicineEntity entity = invocation.getArgument(0);
            medicines.computeIfAbsent(entity.getPrescriptionId(), ignored -> new ArrayList<>()).add(entity);
            return entity;
        });
        lenient().when(medicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(any(), any())).thenAnswer(invocation -> {
            UUID prescriptionId = invocation.getArgument(1);
            return medicines.getOrDefault(prescriptionId, List.of()).stream()
                    .sorted((left, right) -> Integer.compare(left.getSortOrder() == null ? 0 : left.getSortOrder(), right.getSortOrder() == null ? 0 : right.getSortOrder()))
                    .toList();
        });
        lenient().doNothing().when(medicineRepository).deleteByTenantIdAndPrescriptionId(any(), any());

        lenient().when(testRepository.save(any(PrescriptionTestEntity.class))).thenAnswer(invocation -> {
            PrescriptionTestEntity entity = invocation.getArgument(0);
            tests.computeIfAbsent(entity.getPrescriptionId(), ignored -> new ArrayList<>()).add(entity);
            return entity;
        });
        lenient().when(testRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(any(), any())).thenAnswer(invocation -> {
            UUID prescriptionId = invocation.getArgument(1);
            return tests.getOrDefault(prescriptionId, List.of()).stream()
                    .sorted((left, right) -> Integer.compare(left.getSortOrder() == null ? 0 : left.getSortOrder(), right.getSortOrder() == null ? 0 : right.getSortOrder()))
                    .toList();
        });
        lenient().doNothing().when(testRepository).deleteByTenantIdAndPrescriptionId(any(), any());

        PatientEntity patient = PatientEntity.create(TENANT_ID, "PAT-001");
        patient.update("Anita", "Patel", null, null, null, "9999999999", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
        lenient().when(patientRepository.findByTenantIdAndId(TENANT_ID, PATIENT_ID)).thenReturn(Optional.of(patient));
        lenient().when(patientRepository.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(patient));

        lenient().when(tenantUserManagementService.list(TENANT_ID)).thenReturn(List.of(new TenantUserRecord(
                DOCTOR_ID,
                TENANT_ID,
                "doctor-sub",
                "doctor@clinic.local",
                "Doctor One",
                "ACTIVE",
                "DOCTOR",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "SYNCED"
        )));
        lenient().when(clinicProfileService.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        lenient().when(consultationService.findById(TENANT_ID, CONSULTATION_ID)).thenReturn(Optional.of(consultation()));
    }

    @Test
    void finalizedPrescriptionIsImmutableAndCorrectionCreatesNewVersion() {
        PrescriptionEntity parent = finalizeSavedPrescription();

        PrescriptionUpsertCommand correction = command("Acute bronchitis", "Finish course", LocalDate.now().plusDays(7));
        PrescriptionRecord child = service.createCorrectionVersion(TENANT_ID, parent.getId(), correction, ACTOR_ID, "FOLLOW_UP", "Follow-up correction");

        assertThat(child.versionNumber()).isEqualTo(2);
        assertThat(child.parentPrescriptionId()).isEqualTo(parent.getId());
        assertThat(child.status()).isEqualTo(PrescriptionStatus.DRAFT);
        assertThat(child.correctionReason()).isEqualTo("Follow-up correction");
        assertThat(child.flowType()).isEqualTo("FOLLOW_UP");

        PrescriptionEntity reloadedParent = prescriptions.get(parent.getId());
        assertThat(reloadedParent.getStatus()).isEqualTo(PrescriptionStatus.CORRECTED);
        assertThat(reloadedParent.getCorrectedAt()).isNotNull();

        PrescriptionRecord finalizedChild = service.finalizePrescription(TENANT_ID, child.id(), ACTOR_ID);
        assertThat(finalizedChild.status()).isEqualTo(PrescriptionStatus.FINALIZED);

        PrescriptionEntity supersededParent = prescriptions.get(parent.getId());
        assertThat(supersededParent.getStatus()).isEqualTo(PrescriptionStatus.SUPERSEDED);
        assertThat(supersededParent.getSupersededByPrescriptionId()).isEqualTo(child.id());
        assertThat(supersededParent.getSupersededAt()).isNotNull();

        ArgumentCaptor<AuditEventCommand> captor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditEventPublisher, times(5)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuditEventCommand::action)
                .containsExactlyInAnyOrder(
                        "prescription.finalized",
                        "prescription.corrected",
                        "prescription.version.created",
                        "prescription.superseded",
                        "prescription.finalized"
                );
    }

    @Test
    void historyReturnsAllVersionsForTheConsultationInOrder() {
        PrescriptionEntity parent = finalizeSavedPrescription();
        PrescriptionRecord child = service.createCorrectionVersion(TENANT_ID, parent.getId(), command("Diagnosis B", "Advice B", LocalDate.now().plusDays(10)), ACTOR_ID, "FOLLOW_UP", "Correction reason");
        service.finalizePrescription(TENANT_ID, child.id(), ACTOR_ID);

        List<PrescriptionRecord> history = service.history(TENANT_ID, child.id());

        assertThat(history).extracting(PrescriptionRecord::versionNumber).containsExactly(1, 2);
        assertThat(history).extracting(PrescriptionRecord::status).containsExactly(PrescriptionStatus.SUPERSEDED, PrescriptionStatus.FINALIZED);
        assertThat(history).extracting(PrescriptionRecord::parentPrescriptionId).containsExactly(null, parent.getId());
    }

    @Test
    void correctionIsTenantScoped() {
        PrescriptionEntity parent = finalizeSavedPrescription();
        PrescriptionEntity otherTenantPrescription = PrescriptionEntity.create(
                UUID.randomUUID(),
                PATIENT_ID,
                DOCTOR_ID,
                CONSULTATION_ID,
                APPOINTMENT_ID,
                "RX-OTHER"
        );
        otherTenantPrescription.finalizePrescription(ACTOR_ID);
        prescriptions.put(otherTenantPrescription.getId(), otherTenantPrescription);

        List<PrescriptionRecord> history = service.history(TENANT_ID, parent.getId());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void updateDraftRejectsMissingMedicines() {
        PrescriptionEntity parent = finalizeSavedPrescription();
        PrescriptionUpsertCommand invalid = new PrescriptionUpsertCommand(PATIENT_ID, DOCTOR_ID, CONSULTATION_ID, APPOINTMENT_ID, "Dx", "Advice", null, List.of(), List.of());

        assertThatThrownBy(() -> service.updateDraft(TENANT_ID, parent.getId(), invalid, ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one medicine is required");
    }

    @Test
    void updateDraftAllowsEditingAfterPreview() {
        PrescriptionEntity entity = PrescriptionEntity.create(TENANT_ID, PATIENT_ID, DOCTOR_ID, CONSULTATION_ID, APPOINTMENT_ID, "RX-002");
        entity.update("Dx", "Advice", LocalDate.now().plusDays(2));
        entity.preview();
        prescriptions.put(entity.getId(), entity);

        PrescriptionRecord updated = service.updateDraft(TENANT_ID, entity.getId(), command("Updated Dx", "Updated advice", LocalDate.now().plusDays(3)), ACTOR_ID);

        assertThat(updated.status()).isEqualTo(PrescriptionStatus.PREVIEWED);
        assertThat(updated.diagnosisSnapshot()).isEqualTo("Updated Dx");
    }

    @Test
    void updateDraftAfterPrintCreatesCorrectionVersion() {
        PrescriptionEntity entity = PrescriptionEntity.create(TENANT_ID, PATIENT_ID, DOCTOR_ID, CONSULTATION_ID, APPOINTMENT_ID, "RX-003");
        entity.update("Dx", "Advice", LocalDate.now().plusDays(2));
        entity.markPrinted();
        prescriptions.put(entity.getId(), entity);

        PrescriptionRecord updated = service.updateDraft(TENANT_ID, entity.getId(), command("Revised Dx", "Revised advice", LocalDate.now().plusDays(5)), ACTOR_ID);

        assertThat(updated.parentPrescriptionId()).isEqualTo(entity.getId());
        assertThat(updated.status()).isEqualTo(PrescriptionStatus.DRAFT);
        assertThat(updated.correctionReason()).isEqualTo("Correction after finalization");
    }

    private PrescriptionEntity finalizeSavedPrescription() {
        PrescriptionEntity entity = PrescriptionEntity.create(TENANT_ID, PATIENT_ID, DOCTOR_ID, CONSULTATION_ID, APPOINTMENT_ID, "RX-001");
        entity.update("Initial diagnosis", "Initial advice", LocalDate.now().plusDays(5));
        prescriptions.put(entity.getId(), entity);
        service.finalizePrescription(TENANT_ID, entity.getId(), ACTOR_ID);
        return prescriptions.get(entity.getId());
    }

    private PrescriptionUpsertCommand command(String diagnosis, String advice, LocalDate followUpDate) {
        return new PrescriptionUpsertCommand(
                PATIENT_ID,
                DOCTOR_ID,
                CONSULTATION_ID,
                APPOINTMENT_ID,
                diagnosis,
                advice,
                followUpDate,
                List.of(new PrescriptionMedicineCommand(
                        "Amoxicillin",
                        MedicineType.TABLET,
                        "500 mg",
                        "1 tablet",
                        "Twice daily",
                        "5 days",
                        Timing.AFTER_FOOD,
                        "Finish the full course",
                        1
                )),
                List.of(new PrescriptionTestCommand("CBC", "Check baseline", 1))
        );
    }

    private ConsultationRecord consultation() {
        return new ConsultationRecord(
                CONSULTATION_ID,
                TENANT_ID,
                PATIENT_ID,
                "PAT-001",
                "Anita Patel",
                DOCTOR_ID,
                "Doctor One",
                APPOINTMENT_ID,
                "Cough",
                "Fever",
                "Acute bronchitis",
                "Rest and hydration",
                "Take medicines with food",
                LocalDate.now().plusDays(7),
                ConsultationStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().minusDays(1)
        );
    }

    private List<PrescriptionEntity> byTenant(UUID tenantId, UUID doctorUserId, boolean filterDoctor) {
        return prescriptions.values().stream()
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> !filterDoctor || doctorUserId.equals(entity.getDoctorUserId()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }

    private List<PrescriptionEntity> byTenantByPatient(UUID tenantId, UUID patientId) {
        return prescriptions.values().stream()
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> patientId.equals(entity.getPatientId()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }

    private List<PrescriptionEntity> byTenantAndConsultation(UUID tenantId, UUID consultationId, boolean asc) {
        return prescriptions.values().stream()
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> consultationId.equals(entity.getConsultationId()))
                .sorted((left, right) -> asc
                        ? Integer.compare(left.getVersionNumber() == null ? 0 : left.getVersionNumber(), right.getVersionNumber() == null ? 0 : right.getVersionNumber())
                        : Integer.compare(right.getVersionNumber() == null ? 0 : right.getVersionNumber(), left.getVersionNumber() == null ? 0 : left.getVersionNumber()))
                .toList();
    }
}
