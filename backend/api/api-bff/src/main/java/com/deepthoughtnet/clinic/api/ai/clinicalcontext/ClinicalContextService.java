package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.DiagnosisSummary;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.DocumentIntelligence;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.LabIntelligence;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.MedicationSummary;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.IntakeSummary;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.VitalsSnapshot;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.PatientSnapshot;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.TimelineEvent;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.TimelineSummary;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.VisitSummary;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeEntity;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.deepthoughtnet.clinic.api.clinicalmemory.service.PatientLongitudinalMemoryService;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderRepository;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabOrderResultRepository;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestRepository;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.consultation.service.ConsultationVitalsCalculator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ClinicalContextService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalContextService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter HUMAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final List<String> ANTIBIOTIC_HINTS = List.of(
            "amox",
            "augmentin",
            "azith",
            "cef",
            "cipro",
            "doxy",
            "levo",
            "metro",
            "oflox",
            "cefix",
            "cefuro",
            "clarith",
            "rifax"
    );
    private static final List<String> LONG_TERM_STEROID_HINTS = List.of(
            "predni",
            "dexa",
            "methylpred",
            "hydrocortisone",
            "betamethasone"
    );

    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineRepository prescriptionMedicineRepository;
    private final PrescriptionTestRepository prescriptionTestRepository;
    private final ClinicalDocumentRepository clinicalDocumentRepository;
    private final PatientClinicalIntakeRepository patientClinicalIntakeRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabOrderResultRepository labOrderResultRepository;
    private final PatientLongitudinalMemoryService longitudinalMemoryService;
    private final ObjectMapper objectMapper;

    public ClinicalContextService(PatientRepository patientRepository,
                                  ConsultationRepository consultationRepository,
                                  PrescriptionRepository prescriptionRepository,
                                  PrescriptionMedicineRepository prescriptionMedicineRepository,
                                  PrescriptionTestRepository prescriptionTestRepository,
                                  ClinicalDocumentRepository clinicalDocumentRepository,
                                  PatientClinicalIntakeRepository patientClinicalIntakeRepository,
                                  LabOrderRepository labOrderRepository,
                                  LabOrderResultRepository labOrderResultRepository,
                                  PatientLongitudinalMemoryService longitudinalMemoryService,
                                  ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.prescriptionTestRepository = prescriptionTestRepository;
        this.clinicalDocumentRepository = clinicalDocumentRepository;
        this.patientClinicalIntakeRepository = patientClinicalIntakeRepository;
        this.labOrderRepository = labOrderRepository;
        this.labOrderResultRepository = labOrderResultRepository;
        this.longitudinalMemoryService = longitudinalMemoryService;
        this.objectMapper = objectMapper;
    }

    public ClinicalContextResponse buildClinicalContext(UUID tenantId, UUID patientId, UUID consultationId) {
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Patient not found"));
        PatientLongitudinalMemoryProfile longitudinalProfile = longitudinalMemoryService.buildProfile(tenantId, patientId);

        List<ConsultationEntity> consultations = consultationRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId);
        List<PrescriptionEntity> prescriptions = prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId);
        List<ClinicalDocumentEntity> documents = clinicalDocumentRepository.findByTenantIdAndPatientIdAndActiveTrueOrderByCreatedAtDesc(tenantId, patientId);
        List<PatientClinicalIntakeEntity> clinicalIntakes = patientClinicalIntakeRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId);
        List<LabOrderEntity> labOrders = labOrderRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId);

        List<ConsultationEntity> historicalConsultations = consultations.stream()
                .filter(this::isCompletedConsultation)
                .filter(consultation -> consultationId == null || !consultation.getId().equals(consultationId))
                .limit(5)
                .toList();
        ConsultationEntity currentConsultation = consultations.stream()
                .filter(consultation -> consultationId != null && consultationId.equals(consultation.getId()))
                .findFirst()
                .orElse(null);

        Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId = loadPrescriptionMedicines(tenantId, prescriptions, 5);
        Map<UUID, List<PrescriptionTestEntity>> testsByPrescriptionId = loadPrescriptionTests(tenantId, prescriptions, 5);

        List<String> activeMedications = buildActiveMedications(patient, prescriptions, medicinesByPrescriptionId);
        List<String> discontinuedMedications = buildDiscontinuedMedications(activeMedications, prescriptions, medicinesByPrescriptionId);
        List<String> recentAntibiotics = findRecentAntibiotics(prescriptions, medicinesByPrescriptionId);
        List<String> duplicateMedicines = findDuplicateMedicines(patient, prescriptions, medicinesByPrescriptionId);
        List<String> medicationAlerts = buildMedicationAlerts(patient, activeMedications, recentAntibiotics, duplicateMedicines);

        List<VisitSummary> previousVisits = historicalConsultations.stream()
                .map(consultation -> toVisitSummary(consultation, prescriptions, medicinesByPrescriptionId, testsByPrescriptionId))
                .toList();

        String lastVisitDiagnosis = historicalConsultations.isEmpty() ? null : compactText(firstNonBlank(historicalConsultations.get(0).getDiagnosis(), historicalConsultations.get(0).getChiefComplaints()));
        List<String> previousDiagnoses = historicalConsultations.stream()
                .map(ConsultationEntity::getDiagnosis)
                .filter(this::hasText)
                .map(this::compactText)
                .distinct()
                .limit(5)
                .toList();

        LabIntelligence labIntelligence = buildLabIntelligence(tenantId, labOrders, longitudinalProfile);
        DocumentIntelligence documentIntelligence = buildDocumentIntelligence(documents);
        IntakeSummary intakeSummary = buildIntakeSummary(clinicalIntakes, documents);
        String hydratedVitals = buildHydratedConsultationVitals(currentConsultation, intakeSummary);
        if (currentConsultation != null && isConsultationVitalsNull(currentConsultation) && intakeSummary != null && intakeSummary.latestVitals() != null) {
            log.info("[CONSULTATION-VITALS-HYDRATION] consultationId={} patientId={} hydratedFields={} source=INTAKE",
                    currentConsultation.getId(),
                    patientId,
                    summarizeHydratedVitals(intakeSummary.latestVitals()));
        }
        TimelineSummary timelineSummary = buildTimelineSummary(consultations, prescriptions, clinicalIntakes, documents, labOrders, longitudinalMemoryService.buildTimelineEvents(tenantId, patientId, 8));

        PatientSnapshot patientSnapshot = new PatientSnapshot(
                buildPatientName(patient),
                patient.getAgeYears(),
                patient.getGender() == null ? null : patient.getGender().name(),
                mergeText(patient.getExistingConditions(), longitudinalProfile.knownConditions()),
                compactText(patient.getAllergies()),
                mergeTextList(activeMedications, longitudinalProfile.longTermMedications()),
                consultations.isEmpty() ? null : formatDate(consultations.get(0).getCreatedAt())
        );

        DiagnosisSummary diagnosisSummary = new DiagnosisSummary(lastVisitDiagnosis, previousDiagnoses);
        String aiSummary = buildAiSummary(patientSnapshot, medicationAlerts, labIntelligence, documentIntelligence, timelineSummary);
        String aiPromptContext = buildPromptContext(patientSnapshot, previousVisits, diagnosisSummary, medicationAlerts, intakeSummary, labIntelligence, documentIntelligence, timelineSummary, historicalConsultations, currentConsultation, hydratedVitals);
        MedicationSummary medicationSummary = new MedicationSummary(activeMedications, discontinuedMedications, recentAntibiotics, duplicateMedicines, medicationAlerts);
        ClinicalContextResponse.LongitudinalMemory longitudinalMemory = toLongitudinalMemory(longitudinalProfile);
        String clinicalContextJson = buildClinicalContextJson(patientSnapshot, previousVisits, medicationSummary, diagnosisSummary, intakeSummary, labIntelligence, documentIntelligence, timelineSummary, longitudinalMemory, historicalConsultations, currentConsultation, hydratedVitals);
        traceClinicalContext(tenantId, patientId, consultationId, patientSnapshot, labIntelligence, documentIntelligence, longitudinalMemory);
        traceAiContext(tenantId, patientId, consultationId, clinicalContextJson, aiPromptContext, longitudinalProfile, labIntelligence, documentIntelligence, historicalConsultations);

        return new ClinicalContextResponse(
                tenantId,
                patientId,
                consultationId,
                patientSnapshot,
                previousVisits,
                medicationSummary,
                diagnosisSummary,
                intakeSummary,
                labIntelligence,
                documentIntelligence,
                timelineSummary,
                longitudinalMemory,
                aiSummary,
                aiPromptContext,
                clinicalContextJson,
                OffsetDateTime.now()
        );
    }

    public void enrichPromptInput(Map<String, Object> input, ClinicalContextResponse context) {
        if (input == null || context == null) {
            return;
        }
        input.put("clinicalContext", context);
        input.put("clinicalContextSummary", context.aiSummary());
        input.put("clinicalContextJson", context.clinicalContextJson());
        input.put("aiPromptContext", context.aiPromptContext());
    }

    private Map<UUID, List<PrescriptionMedicineEntity>> loadPrescriptionMedicines(UUID tenantId, List<PrescriptionEntity> prescriptions, int limit) {
        Map<UUID, List<PrescriptionMedicineEntity>> byPrescription = new LinkedHashMap<>();
        prescriptions.stream()
                .limit(limit)
                .forEach(prescription -> byPrescription.put(
                        prescription.getId(),
                        prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())
                ));
        return byPrescription;
    }

    private Map<UUID, List<PrescriptionTestEntity>> loadPrescriptionTests(UUID tenantId, List<PrescriptionEntity> prescriptions, int limit) {
        Map<UUID, List<PrescriptionTestEntity>> byPrescription = new LinkedHashMap<>();
        prescriptions.stream()
                .limit(limit)
                .forEach(prescription -> byPrescription.put(
                        prescription.getId(),
                        prescriptionTestRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId())
                ));
        return byPrescription;
    }

    private List<String> buildActiveMedications(PatientEntity patient, List<PrescriptionEntity> prescriptions, Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId) {
        LinkedHashSet<String> medications = new LinkedHashSet<>();
        splitFreeText(patient.getLongTermMedications()).forEach(medications::add);

        PrescriptionEntity currentPrescription = prescriptions.stream()
                .filter(prescription -> prescription.getStatus() != PrescriptionStatus.CANCELLED && prescription.getStatus() != PrescriptionStatus.SUPERSEDED)
                .findFirst()
                .orElse(prescriptions.isEmpty() ? null : prescriptions.get(0));
        if (currentPrescription != null) {
            medicinesByPrescriptionId.getOrDefault(currentPrescription.getId(), List.of()).stream()
                    .map(PrescriptionMedicineEntity::getMedicineName)
                    .filter(this::hasText)
                    .map(this::compactText)
                    .forEach(medications::add);
        }
        return medications.stream().limit(8).toList();
    }

    private List<String> buildDiscontinuedMedications(List<String> activeMedications,
                                                     List<PrescriptionEntity> prescriptions,
                                                     Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId) {
        Set<String> activeNormalized = activeMedications.stream().map(this::normalizeMedicineName).filter(this::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> discontinued = new LinkedHashSet<>();
        prescriptions.stream()
                .skip(1)
                .flatMap(prescription -> medicinesByPrescriptionId.getOrDefault(prescription.getId(), List.of()).stream())
                .map(PrescriptionMedicineEntity::getMedicineName)
                .filter(this::hasText)
                .map(this::compactText)
                .filter(name -> !activeNormalized.contains(normalizeMedicineName(name)))
                .forEach(discontinued::add);
        return discontinued.stream().limit(8).toList();
    }

    private List<String> findRecentAntibiotics(List<PrescriptionEntity> prescriptions, Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
        LinkedHashSet<String> antibiotics = new LinkedHashSet<>();
        for (PrescriptionEntity prescription : prescriptions) {
            if (prescription.getCreatedAt() != null && prescription.getCreatedAt().isBefore(cutoff)) {
                continue;
            }
            for (PrescriptionMedicineEntity medicine : medicinesByPrescriptionId.getOrDefault(prescription.getId(), List.of())) {
                if (isAntibiotic(medicine.getMedicineName())) {
                    antibiotics.add(compactText(medicine.getMedicineName()));
                }
            }
        }
        return antibiotics.stream().limit(5).toList();
    }

    private List<String> findDuplicateMedicines(PatientEntity patient, List<PrescriptionEntity> prescriptions, Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId) {
        Map<String, Long> frequency = new LinkedHashMap<>();
        splitFreeText(patient.getLongTermMedications()).forEach(med -> frequency.merge(normalizeMedicineName(med), 1L, Long::sum));
        prescriptions.stream()
                .flatMap(prescription -> medicinesByPrescriptionId.getOrDefault(prescription.getId(), List.of()).stream())
                .map(PrescriptionMedicineEntity::getMedicineName)
                .filter(this::hasText)
                .map(this::compactText)
                .forEach(name -> frequency.merge(normalizeMedicineName(name), 1L, Long::sum));
        return frequency.entrySet().stream()
                .filter(entry -> entry.getValue() > 1 && hasText(entry.getKey()))
                .map(Map.Entry::getKey)
                .map(this::compactDisplayName)
                .limit(5)
                .toList();
    }

    private List<String> buildMedicationAlerts(PatientEntity patient,
                                               List<String> activeMedications,
                                               List<String> recentAntibiotics,
                                               List<String> duplicateMedicines) {
        List<String> alerts = new ArrayList<>();
        if (!recentAntibiotics.isEmpty()) {
            alerts.add("Previous antibiotic within 14 days: " + joinCompact(recentAntibiotics, 3));
        }
        if (!duplicateMedicines.isEmpty()) {
            alerts.add("Duplicate medicine: " + joinCompact(duplicateMedicines, 3));
        }
        if (hasAllergyOverlap(patient.getAllergies(), activeMedications)) {
            alerts.add("Allergy warning: review active medicines against recorded allergies.");
        }
        if (containsHint(activeMedications, LONG_TERM_STEROID_HINTS)) {
            alerts.add("Long-term steroid caution: verify dose, duration, and taper plan.");
        }
        return alerts;
    }

    private VisitSummary toVisitSummary(ConsultationEntity consultation,
                                        List<PrescriptionEntity> prescriptions,
                                        Map<UUID, List<PrescriptionMedicineEntity>> medicinesByPrescriptionId,
                                        Map<UUID, List<PrescriptionTestEntity>> testsByPrescriptionId) {
        PrescriptionEntity prescription = prescriptions.stream()
                .filter(item -> Objects.equals(item.getConsultationId(), consultation.getId()))
                .findFirst()
                .orElse(null);
        List<String> medicineNames = prescription == null
                ? List.of()
                : medicinesByPrescriptionId.getOrDefault(prescription.getId(), List.of()).stream().map(PrescriptionMedicineEntity::getMedicineName).filter(this::hasText).map(this::compactText).limit(4).toList();
        List<String> testNames = prescription == null
                ? List.of()
                : testsByPrescriptionId.getOrDefault(prescription.getId(), List.of()).stream().map(PrescriptionTestEntity::getTestName).filter(this::hasText).map(this::compactText).limit(4).toList();
        String treatmentSummary = joinSegments(segments(
                medicineNames.isEmpty() ? null : "Medicines: " + joinCompact(medicineNames, 4),
                testNames.isEmpty() ? null : "Tests: " + joinCompact(testNames, 4)
        ));
        return new VisitSummary(
                consultation.getId(),
                formatDate(consultation.getCreatedAt()),
                compactText(firstNonBlank(consultation.getDiagnosis(), consultation.getChiefComplaints())),
                treatmentSummary,
                compactText(consultation.getAdvice())
        );
    }

    private LabIntelligence buildLabIntelligence(UUID tenantId, List<LabOrderEntity> labOrders, PatientLongitudinalMemoryProfile longitudinalProfile) {
        List<LabOrderEntity> recentOrders = labOrders.stream().limit(8).toList();
        List<LabOrderResultEntity> allResults = new ArrayList<>();
        for (LabOrderEntity order : recentOrders) {
            allResults.addAll(labOrderResultRepository.findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(tenantId, order.getId()));
        }

        List<String> latestLabReports = recentOrders.stream()
                .filter(order -> order.getReportPublishedAt() != null || order.getReportGeneratedAt() != null || order.getResultEnteredAt() != null)
                .limit(3)
                .map(this::formatLabReportSummary)
                .filter(this::hasText)
                .toList();

        List<String> abnormalValues = buildAbnormalValues(allResults, longitudinalProfile);

        List<String> previousTrends = buildLabTrends(allResults);
        List<String> pendingInvestigations = recentOrders.stream()
                .filter(order -> isPendingLabOrderStatus(order.getStatus()))
                .limit(6)
                .map(this::formatPendingLabOrderSummary)
                .toList();

        String latestHbA1c = longitudinalProfile.latestHbA1c() == null ? findLatestLabValue(allResults, "HBA1C") : formatLongitudinalConcept(longitudinalProfile.latestHbA1c());
        String latestBloodSugar = longitudinalProfile.latestBloodSugar() == null ? null : formatLongitudinalConcept(longitudinalProfile.latestBloodSugar());
        String latestLipidSummary = longitudinalProfile.latestLipidSummary().isEmpty() ? null : longitudinalProfile.latestLipidSummary().stream().map(this::formatLongitudinalConcept).collect(Collectors.joining(" • "));
        String latestBloodPressure = longitudinalProfile.latestBloodPressure() == null ? null : formatLongitudinalConcept(longitudinalProfile.latestBloodPressure());
        String latestBmi = longitudinalProfile.latestBmi() == null ? null : formatLongitudinalConcept(longitudinalProfile.latestBmi());
        String latestCbc = findLatestLabValue(allResults, "CBC");
        String latestCreatinine = findLatestLabValue(allResults, "CREATININE");

        String latestLabReport = hasText(longitudinalProfile == null ? null : longitudinalProfile.mostRecentLaboratorySummary())
                ? longitudinalProfile.mostRecentLaboratorySummary()
                : (latestLabReports.isEmpty() ? null : latestLabReports.get(0));
        if (latestLabReport == null && !recentOrders.isEmpty()) {
            latestLabReport = formatPendingLabOrderSummary(recentOrders.get(0));
        }

        return new LabIntelligence(
                latestLabReport,
                abnormalValues,
                previousTrends,
                pendingInvestigations,
                latestHbA1c,
                latestCbc,
                latestCreatinine,
                latestBloodSugar,
                latestLipidSummary,
                latestBloodPressure,
                latestBmi
        );
    }

    private DocumentIntelligence buildDocumentIntelligence(List<ClinicalDocumentEntity> documents) {
        List<String> recentReports = documents.stream()
                .limit(5)
                .map(this::formatDocument)
                .toList();
        List<String> radiology = documents.stream()
                .filter(document -> isType(document, "RADIOLOGY_REPORT", "X_RAY", "MRI_CT"))
                .limit(5)
                .map(this::formatDocument)
                .toList();
        List<String> referrals = documents.stream()
                .filter(document -> isType(document, "REFERRAL_LETTER", "REFERRAL"))
                .limit(5)
                .map(this::formatDocument)
                .toList();
        List<String> dischargeSummaries = documents.stream()
                .filter(document -> isType(document, "DISCHARGE_SUMMARY"))
                .limit(5)
                .map(this::formatDocument)
                .toList();
        return new DocumentIntelligence(recentReports, radiology, referrals, dischargeSummaries);
    }

    private TimelineSummary buildTimelineSummary(List<ConsultationEntity> consultations,
                                                 List<PrescriptionEntity> prescriptions,
                                                 List<PatientClinicalIntakeEntity> clinicalIntakes,
                                                 List<ClinicalDocumentEntity> documents,
                                                 List<LabOrderEntity> labOrders,
                                                 List<ClinicalContextResponse.TimelineEvent> longitudinalEvents) {
        List<TimelineEvent> events = new ArrayList<>();
        clinicalIntakes.stream().limit(5).forEach(intake ->
                events.add(new TimelineEvent(
                        formatDate(intake.getCreatedAt()),
                        intake.isComplete() ? "Clinical Intake Complete" : "Clinical Intake Pending",
                        compactText(firstNonBlank(intake.getChiefComplaint(), formatVitalsSnapshot(toVitalsSnapshot(intake)))),
                        "INTAKE"
                )));
        consultations.stream().limit(5).forEach(consultation ->
                events.add(new TimelineEvent(
                        formatDate(consultation.getCreatedAt()),
                        "Consultation",
                        compactText(firstNonBlank(consultation.getDiagnosis(), consultation.getChiefComplaints())),
                        "CONSULTATION"
                )));
        prescriptions.stream().limit(5).forEach(prescription ->
                events.add(new TimelineEvent(
                        formatDate(prescription.getCreatedAt()),
                        "Prescription",
                        compactText(firstNonBlank(prescription.getDiagnosisSnapshot(), prescription.getPrescriptionNumber())),
                        "PRESCRIPTION"
                )));
        documents.stream().limit(5).forEach(document ->
                events.add(new TimelineEvent(
                        formatHumanDate(firstNonNull(document.getReportDate(), document.getCreatedAt() == null ? null : document.getCreatedAt().toLocalDate())),
                        documentLabel(document),
                        compactText(firstNonBlank(document.getTitle(), document.getDescription(), documentLabel(document))),
                        "DOCUMENT"
                )));
        labOrders.stream().limit(5).forEach(order ->
                events.add(new TimelineEvent(
                        formatHumanDate(firstNonNullDateTime(order.getReportPublishedAt(), order.getReportGeneratedAt(), order.getResultEnteredAt(), order.getOrderedAt())),
                        "Lab Order",
                        compactText(firstNonBlank(order.getNotes(), order.getSampleType(), order.getExternalReferenceNumber(), order.getStatus() == null ? null : order.getStatus().name())),
                        "LAB_ORDER"
                )));
        longitudinalEvents.stream().limit(12).forEach(event ->
                events.add(new TimelineEvent(
                        event.occurredOn(),
                        event.title(),
                        event.detail(),
                        event.type()
                )));

        events.sort(Comparator.comparing((TimelineEvent event) -> parseDateValue(event.occurredOn()), Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        List<TimelineEvent> limited = events.stream().limit(8).toList();
        String recentImportantEvents = limited.stream()
                .map(event -> joinSegments(segments(event.occurredOn(), event.title(), event.detail())))
                .filter(this::hasText)
                .collect(Collectors.joining(" • "));
        return new TimelineSummary(limited, recentImportantEvents);
    }

    private IntakeSummary buildIntakeSummary(List<PatientClinicalIntakeEntity> clinicalIntakes, List<ClinicalDocumentEntity> documents) {
        PatientClinicalIntakeEntity latest = clinicalIntakes.stream().findFirst().orElse(null);
        if (latest == null) {
            return new IntakeSummary(false, null, null, null, List.of(), null, null, null, null);
        }
        List<PatientClinicalIntakeEntity> recent = clinicalIntakes.stream().limit(5).toList();
        VitalsSnapshot latestVitals = new VitalsSnapshot(
                latest.getHeightCm(),
                latest.getWeightKg(),
                latest.getBmi(),
                ConsultationVitalsCalculator.bmiCategory(latest.getWeightKg(), latest.getHeightCm()),
                latest.getBloodPressureSystolic(),
                latest.getBloodPressureDiastolic(),
                latest.getPulseRate(),
                latest.getTemperature(),
                latest.getTemperatureUnit() == null ? null : latest.getTemperatureUnit().name(),
                latest.getSpo2(),
                latest.getRespiratoryRate(),
                latest.getRandomBloodSugar(),
                latest.getPainScore()
        );
        List<String> abnormalVitalsAlerts = buildVitalsAlerts(latest);
        String vitalsTrendSummary = buildVitalsTrendSummary(recent);
        String uploadedDocumentSummary = buildIntakeDocumentSummary(documents);
        return new IntakeSummary(
                latest.isComplete(),
                compactText(latest.getChiefComplaint(), 160),
                latestVitals,
                vitalsTrendSummary,
                abnormalVitalsAlerts,
                uploadedDocumentSummary,
                compactText(latest.getNotes(), 220),
                compactText(latest.getRecordedByName(), 120),
                formatDateTime(latest.getCreatedAt())
        );
    }

    private VitalsSnapshot toVitalsSnapshot(PatientClinicalIntakeEntity intake) {
        if (intake == null) {
            return null;
        }
        return new VitalsSnapshot(
                intake.getHeightCm(),
                intake.getWeightKg(),
                intake.getBmi(),
                ConsultationVitalsCalculator.bmiCategory(intake.getWeightKg(), intake.getHeightCm()),
                intake.getBloodPressureSystolic(),
                intake.getBloodPressureDiastolic(),
                intake.getPulseRate(),
                intake.getTemperature(),
                intake.getTemperatureUnit() == null ? null : intake.getTemperatureUnit().name(),
                intake.getSpo2(),
                intake.getRespiratoryRate(),
                intake.getRandomBloodSugar(),
                intake.getPainScore()
        );
    }

    private List<String> buildVitalsAlerts(PatientClinicalIntakeEntity intake) {
        List<String> alerts = new ArrayList<>();
        if (intake == null) {
            return alerts;
        }
        if (intake.getBloodPressureSystolic() != null && intake.getBloodPressureSystolic() >= 140) {
            alerts.add("High systolic BP: " + intake.getBloodPressureSystolic());
        }
        if (intake.getBloodPressureDiastolic() != null && intake.getBloodPressureDiastolic() >= 90) {
            alerts.add("High diastolic BP: " + intake.getBloodPressureDiastolic());
        }
        if (intake.getPulseRate() != null && (intake.getPulseRate() < 50 || intake.getPulseRate() > 110)) {
            alerts.add("Pulse outside typical range: " + intake.getPulseRate());
        }
        if (intake.getTemperature() != null && intake.getTemperature() >= 38.0) {
            alerts.add("Fever noted: " + intake.getTemperature());
        }
        if (intake.getSpo2() != null && intake.getSpo2() < 94) {
            alerts.add("Low SpO2: " + intake.getSpo2());
        }
        if (intake.getRespiratoryRate() != null && intake.getRespiratoryRate() >= 24) {
            alerts.add("Respiratory rate elevated: " + intake.getRespiratoryRate());
        }
        if (intake.getPainScore() != null && intake.getPainScore() >= 7) {
            alerts.add("Pain score elevated: " + intake.getPainScore() + "/10");
        }
        if (intake.getRandomBloodSugar() != null && intake.getRandomBloodSugar() >= 200) {
            alerts.add("Random blood sugar elevated: " + intake.getRandomBloodSugar());
        }
        return alerts;
    }

    private String buildVitalsTrendSummary(List<PatientClinicalIntakeEntity> clinicalIntakes) {
        if (clinicalIntakes.isEmpty()) {
            return null;
        }
        List<String> snapshots = clinicalIntakes.stream()
                .limit(3)
                .map(this::toVitalsSnapshot)
                .map(this::formatVitalsSnapshot)
                .filter(this::hasText)
                .toList();
        if (snapshots.isEmpty()) {
            return null;
        }
        return String.join(" → ", snapshots);
    }

    private String buildIntakeDocumentSummary(List<ClinicalDocumentEntity> documents) {
        List<String> recentIntakeDocs = documents.stream()
                .filter(document -> "CLINICAL_INTAKE".equalsIgnoreCase(document.getSourceModule()) || "RECEPTION".equalsIgnoreCase(document.getUploadSource()))
                .limit(3)
                .map(this::formatDocument)
                .toList();
        if (recentIntakeDocs.isEmpty()) {
            return null;
        }
        return joinCompact(recentIntakeDocs, 3);
    }

    private String formatVitalsSnapshot(VitalsSnapshot vitals) {
        if (vitals == null) {
            return null;
        }
        return joinSegments(segments(
                vitals.bloodPressureSystolic() == null || vitals.bloodPressureDiastolic() == null ? null : "BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic(),
                vitals.pulseRate() == null ? null : "Pulse " + vitals.pulseRate(),
                vitals.temperature() == null ? null : "Temp " + compactText(String.valueOf(vitals.temperature()), 16) + (hasText(vitals.temperatureUnit()) ? " " + vitals.temperatureUnit() : ""),
                vitals.spo2() == null ? null : "SpO2 " + vitals.spo2() + "%",
                vitals.respiratoryRate() == null ? null : "RR " + vitals.respiratoryRate(),
                vitals.randomBloodSugar() == null ? null : "RBS " + compactText(String.valueOf(vitals.randomBloodSugar()), 16),
                vitals.painScore() == null ? null : "Pain " + vitals.painScore() + "/10",
                vitals.bmi() == null ? null : "BMI " + compactText(String.format(Locale.ROOT, "%.1f", vitals.bmi()), 12) + (hasText(vitals.bmiCategory()) ? " " + vitals.bmiCategory() : "")
        ));
    }

    private String buildAiSummary(PatientSnapshot patientSnapshot,
                                  List<String> medicationAlerts,
                                  LabIntelligence labIntelligence,
                                  DocumentIntelligence documentIntelligence,
                                  TimelineSummary timelineSummary) {
        List<String> parts = new ArrayList<>();
        parts.add(joinSegments(segments(
                patientSnapshot.patientName(),
                patientSnapshot.ageYears() == null ? null : patientSnapshot.ageYears() + "y",
                patientSnapshot.gender()
        )));
        if (hasText(patientSnapshot.chronicConditions())) {
            parts.add("Chronic: " + patientSnapshot.chronicConditions());
        }
        if (hasText(patientSnapshot.allergies())) {
            parts.add("Allergies: " + patientSnapshot.allergies());
        }
        if (!patientSnapshot.currentMedications().isEmpty()) {
            parts.add("Active meds: " + joinCompact(patientSnapshot.currentMedications(), 5));
        }
        if (!medicationAlerts.isEmpty()) {
            parts.add("Medication alerts: " + joinCompact(medicationAlerts, 3));
        }
        if (hasText(labIntelligence.latestLabReport()) || !labIntelligence.abnormalValues().isEmpty()) {
            parts.add("Lab: " + joinSegments(segments(labIntelligence.latestLabReport(), labIntelligence.abnormalValues().isEmpty() ? null : "Abnormal: " + joinCompact(labIntelligence.abnormalValues(), 3))));
        }
        if (hasText(labIntelligence.lastHbA1c()) || hasText(labIntelligence.latestBloodSugar()) || hasText(labIntelligence.latestLipidSummary())) {
            parts.add("Longitudinal labs: " + joinSegments(segments(
                    hasText(labIntelligence.lastHbA1c()) ? "HbA1c: " + labIntelligence.lastHbA1c() : null,
                    hasText(labIntelligence.latestBloodSugar()) ? "Blood sugar: " + labIntelligence.latestBloodSugar() : null,
                    hasText(labIntelligence.latestLipidSummary()) ? "Lipid profile: " + labIntelligence.latestLipidSummary() : null
            )));
        }
        if (!documentIntelligence.recentReports().isEmpty()) {
            parts.add("Documents: " + joinCompact(documentIntelligence.recentReports(), 3));
        }
        if (hasText(timelineSummary.recentImportantEvents())) {
            parts.add("Timeline: " + compactText(timelineSummary.recentImportantEvents(), 240));
        }
        return String.join(" | ", parts.stream().filter(this::hasText).toList());
    }

    private String buildPromptContext(PatientSnapshot patientSnapshot,
                                      List<VisitSummary> previousVisits,
                                      DiagnosisSummary diagnosisSummary,
                                      List<String> medicationAlerts,
                                      IntakeSummary intakeSummary,
                                      LabIntelligence labIntelligence,
                                      DocumentIntelligence documentIntelligence,
                                      TimelineSummary timelineSummary,
                                      List<ConsultationEntity> historicalConsultations,
                                      ConsultationEntity consultation,
                                      String hydratedVitals) {
        List<String> lines = new ArrayList<>();
        lines.add("Patient: " + joinSegments(segments(
                patientSnapshot.patientName(),
                patientSnapshot.ageYears() == null ? null : patientSnapshot.ageYears() + "y",
                patientSnapshot.gender()
        )));
        if (hasText(patientSnapshot.chronicConditions())) {
            lines.add("Chronic conditions: " + patientSnapshot.chronicConditions());
        }
        if (hasText(patientSnapshot.allergies())) {
            lines.add("Allergies: " + patientSnapshot.allergies());
        }
        if (!patientSnapshot.currentMedications().isEmpty()) {
            lines.add("Current medications: " + joinCompact(patientSnapshot.currentMedications(), 5));
        }
        if (intakeSummary != null && (hasText(intakeSummary.chiefComplaint()) || intakeSummary.latestVitals() != null || hasText(intakeSummary.notes()) || hasText(hydratedVitals))) {
            lines.add("Current visit: " + joinSegments(segments(
                    hasText(intakeSummary.chiefComplaint()) ? "Chief complaint: " + intakeSummary.chiefComplaint() : null,
                    hydratedVitals == null ? (intakeSummary.latestVitals() == null ? null : "Vitals: " + formatVitalsSnapshot(intakeSummary.latestVitals())) : "Vitals: " + hydratedVitals,
                    hasText(intakeSummary.notes()) ? "Notes: " + intakeSummary.notes() : null
            )));
        }
        if (!medicationAlerts.isEmpty()) {
            lines.add("Medication alerts: " + joinCompact(medicationAlerts, 4));
        }
        if (intakeSummary != null && (hasText(intakeSummary.vitalsTrendSummary()) || !intakeSummary.abnormalVitalsAlerts().isEmpty() || hasText(intakeSummary.uploadedDocumentSummary()))) {
            lines.add("Intake support: " + joinSegments(segments(
                    hasText(intakeSummary.vitalsTrendSummary()) ? "Trend: " + intakeSummary.vitalsTrendSummary() : null,
                    intakeSummary.abnormalVitalsAlerts().isEmpty() ? null : "Alerts: " + joinCompact(intakeSummary.abnormalVitalsAlerts(), 4),
                    hasText(intakeSummary.uploadedDocumentSummary()) ? "Documents: " + intakeSummary.uploadedDocumentSummary() : null
            )));
        }
        if (hasText(labIntelligence.latestLabReport()) || !labIntelligence.abnormalValues().isEmpty() || !labIntelligence.pendingInvestigations().isEmpty()) {
            lines.add("Labs: " + joinSegments(segments(
                    hasText(labIntelligence.latestLabReport()) ? labIntelligence.latestLabReport() : null,
                    labIntelligence.abnormalValues().isEmpty() ? null : "Abnormal: " + joinCompact(labIntelligence.abnormalValues(), 4),
                    labIntelligence.pendingInvestigations().isEmpty() ? null : "Pending: " + joinCompact(labIntelligence.pendingInvestigations(), 4),
                    hasText(labIntelligence.lastHbA1c()) ? "HbA1c: " + labIntelligence.lastHbA1c() : null,
                    hasText(labIntelligence.latestBloodSugar()) ? "Blood sugar: " + labIntelligence.latestBloodSugar() : null,
                    hasText(labIntelligence.latestLipidSummary()) ? "Lipid summary: " + labIntelligence.latestLipidSummary() : null,
                    hasText(labIntelligence.latestBloodPressure()) ? "Blood pressure: " + labIntelligence.latestBloodPressure() : null,
                    hasText(labIntelligence.latestBmi()) ? "BMI: " + labIntelligence.latestBmi() : null,
                    hasText(labIntelligence.lastCbc()) ? "CBC: " + labIntelligence.lastCbc() : null,
                    hasText(labIntelligence.lastCreatinine()) ? "Creatinine: " + labIntelligence.lastCreatinine() : null
            )));
        }
        String pendingOrderGuidance = buildPendingOrderGuidance(labIntelligence);
        if (hasText(pendingOrderGuidance)) {
            lines.add(pendingOrderGuidance);
        }
        if (!documentIntelligence.recentReports().isEmpty() || !documentIntelligence.radiology().isEmpty() || !documentIntelligence.referrals().isEmpty() || !documentIntelligence.dischargeSummaries().isEmpty()) {
            lines.add("Recent reports: " + joinSegments(segments(
                    documentIntelligence.recentReports().isEmpty() ? null : joinCompact(dedupeRecentReportsForPrompt(documentIntelligence.recentReports()), 4),
                    documentIntelligence.radiology().isEmpty() ? null : "Radiology: " + joinCompact(documentIntelligence.radiology(), 3),
                    documentIntelligence.referrals().isEmpty() ? null : "Referrals: " + joinCompact(documentIntelligence.referrals(), 3),
                    documentIntelligence.dischargeSummaries().isEmpty() ? null : "Discharge: " + joinCompact(documentIntelligence.dischargeSummaries(), 3)
            )));
        }
        if (!previousVisits.isEmpty()) {
            lines.add("History: " + previousVisits.stream().limit(5).map(visit -> joinSegments(segments(visit.consultationDate(), visit.diagnosis()))).collect(Collectors.joining(" | ")));
        } else if (diagnosisSummary != null && (hasText(diagnosisSummary.lastVisitDiagnosis()) || !diagnosisSummary.previousDiagnoses().isEmpty())) {
            lines.add("History: " + joinSegments(segments(
                    hasText(diagnosisSummary.lastVisitDiagnosis()) ? "Last visit: " + diagnosisSummary.lastVisitDiagnosis() : null,
                    diagnosisSummary.previousDiagnoses().isEmpty() ? null : "Previous: " + joinCompact(diagnosisSummary.previousDiagnoses(), 5)
            )));
        }
        if (hasText(timelineSummary.recentImportantEvents())) {
            lines.add("Safety context: " + timelineSummary.recentImportantEvents());
        }
        if (consultation != null) {
            lines.add("Current consultation status: " + consultation.getStatus());
        }
        return String.join("\n", lines.stream().filter(this::hasText).toList());
    }

    private String buildClinicalContextJson(PatientSnapshot patientSnapshot,
                                            List<VisitSummary> previousVisits,
                                            MedicationSummary medicationSnapshot,
                                            DiagnosisSummary diagnosisSummary,
                                            IntakeSummary intakeSummary,
                                            LabIntelligence labIntelligence,
                                            DocumentIntelligence documentIntelligence,
                                            TimelineSummary timelineSummary,
                                            ClinicalContextResponse.LongitudinalMemory longitudinalMemory,
                                            List<ConsultationEntity> historicalConsultations,
                                            ConsultationEntity consultation,
                                            String hydratedVitals) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientSummary", patientSnapshot);
        payload.put("previousVisits", previousVisits);
        payload.put("medicationHistory", medicationSnapshot);
        payload.put("diagnosisHistory", diagnosisSummary);
        payload.put("intakeSummary", intakeSummary);
        payload.put("labIntelligence", labIntelligence);
        payload.put("documentIntelligence", documentIntelligence);
        payload.put("timelineSummary", timelineSummary);
        payload.put("longitudinalMemory", longitudinalMemory);
        payload.put("aiSummary", buildAiSummary(patientSnapshot, medicationSnapshot.alerts(), labIntelligence, documentIntelligence, timelineSummary));
        payload.put("aiPromptContext", buildPromptContext(patientSnapshot, previousVisits, diagnosisSummary, medicationSnapshot.alerts(), intakeSummary, labIntelligence, documentIntelligence, timelineSummary, historicalConsultations, consultation, hydratedVitals));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }

    private ClinicalContextResponse.LongitudinalMemory toLongitudinalMemory(PatientLongitudinalMemoryProfile profile) {
        if (profile == null) {
            return new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), null);
        }
        return new ClinicalContextResponse.LongitudinalMemory(
                toConceptDtos(profile.knownConditions()),
                toConceptDtos(profile.longTermMedications()),
                toConceptDto(profile.latestHbA1c()),
                toConceptDto(profile.latestBloodSugar()),
                toConceptDtos(profile.latestLipidSummary()),
                toConceptDto(profile.latestBloodPressure()),
                toConceptDto(profile.latestBmi()),
                toConceptDtos(profile.riskFlags()),
                toConceptDtos(profile.history()),
                profile.mostRecentLaboratorySummary()
        );
    }

    private List<ClinicalContextResponse.LongitudinalConcept> toConceptDtos(List<LongitudinalConceptSnapshot> concepts) {
        return concepts == null ? List.of() : concepts.stream().map(this::toConceptDto).toList();
    }

    private ClinicalContextResponse.LongitudinalConcept toConceptDto(LongitudinalConceptSnapshot concept) {
        if (concept == null) {
            return null;
        }
        return new ClinicalContextResponse.LongitudinalConcept(
                concept.conceptFamily(),
                concept.conceptKey(),
                concept.label(),
                concept.valueText(),
                concept.valueUnit(),
                concept.sourceDocumentTitle(),
                concept.sourceDocumentType(),
                concept.sourceDocumentId() == null ? null : concept.sourceDocumentId().toString(),
                concept.observedOn() == null ? null : concept.observedOn().toString(),
                concept.confidence(),
                concept.verificationStatus(),
                concept.evidenceText()
        );
    }

    private String mergeText(String raw, List<LongitudinalConceptSnapshot> concepts) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (hasText(raw)) {
            parts.add(compactText(raw));
        }
        if (concepts != null) {
            concepts.stream().map(LongitudinalConceptSnapshot::label).filter(this::hasText).forEach(parts::add);
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private List<String> mergeTextList(List<String> raw, List<LongitudinalConceptSnapshot> concepts) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (raw != null) {
            raw.stream().filter(this::hasText).map(this::compactText).forEach(merged::add);
        }
        if (concepts != null) {
            concepts.stream().map(LongitudinalConceptSnapshot::label).filter(this::hasText).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private void traceClinicalContext(UUID tenantId,
                                      UUID patientId,
                                      UUID consultationId,
                                      PatientSnapshot patientSnapshot,
                                      LabIntelligence labIntelligence,
                                      DocumentIntelligence documentIntelligence,
                                      ClinicalContextResponse.LongitudinalMemory longitudinalMemory) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "[JEEV-LONG-MEM-TRACE] clinical-context tenantId={} patientId={} consultationId={} existingConditions={} latestHbA1c={} latestBloodSugar={} latestLipidSummary={} riskFlags={} recentReportsCount={}",
                tenantId,
                patientId,
                consultationId,
                patientSnapshot == null ? null : patientSnapshot.chronicConditions(),
                labIntelligence == null ? null : labIntelligence.lastHbA1c(),
                labIntelligence == null ? null : labIntelligence.latestBloodSugar(),
                labIntelligence == null ? null : labIntelligence.latestLipidSummary(),
                longitudinalMemory == null ? null : longitudinalMemory.riskFlags(),
                documentIntelligence == null || documentIntelligence.recentReports() == null ? 0 : documentIntelligence.recentReports().size()
        );
    }

    private void traceAiContext(UUID tenantId,
                                UUID patientId,
                                UUID consultationId,
                                String clinicalContextJson,
                                String aiPromptContext,
                                PatientLongitudinalMemoryProfile longitudinalProfile,
                                LabIntelligence labIntelligence,
                                DocumentIntelligence documentIntelligence,
                                List<ConsultationEntity> historicalConsultations) {
        if (!log.isInfoEnabled()) {
            return;
        }
        int conditionCount = longitudinalProfile == null || longitudinalProfile.knownConditions() == null ? 0 : longitudinalProfile.knownConditions().size();
        int labCount = 0;
        if (longitudinalProfile != null) {
            if (longitudinalProfile.latestHbA1c() != null) {
                labCount++;
            }
            if (longitudinalProfile.latestBloodSugar() != null) {
                labCount++;
            }
            if (longitudinalProfile.latestBloodPressure() != null) {
                labCount++;
            }
            if (longitudinalProfile.latestBmi() != null) {
                labCount++;
            }
            labCount += longitudinalProfile.latestLipidSummary() == null ? 0 : longitudinalProfile.latestLipidSummary().size();
        }
        int abnormalLabCount = labIntelligence == null || labIntelligence.abnormalValues() == null ? 0 : labIntelligence.abnormalValues().size();
        int reportCount = documentIntelligence == null || documentIntelligence.recentReports() == null ? 0 : documentIntelligence.recentReports().size();
        int historyCount = historicalConsultations == null ? 0 : historicalConsultations.size();
        int dedupedConceptCount = longitudinalProfile == null || longitudinalProfile.history() == null ? 0 : longitudinalProfile.history().size();
        String hba1cValue = longitudinalProfile == null || longitudinalProfile.latestHbA1c() == null ? null : longitudinalProfile.latestHbA1c().valueText();
        String latestBloodSugar = longitudinalProfile == null || longitudinalProfile.latestBloodSugar() == null ? null : longitudinalProfile.latestBloodSugar().valueText();
        List<String> selectedSourceDocumentIds = new ArrayList<>();
        if (longitudinalProfile != null) {
            if (longitudinalProfile.latestHbA1c() != null && longitudinalProfile.latestHbA1c().sourceDocumentId() != null) {
                selectedSourceDocumentIds.add(String.valueOf(longitudinalProfile.latestHbA1c().sourceDocumentId()));
            }
            if (longitudinalProfile.latestBloodSugar() != null && longitudinalProfile.latestBloodSugar().sourceDocumentId() != null) {
                selectedSourceDocumentIds.add(String.valueOf(longitudinalProfile.latestBloodSugar().sourceDocumentId()));
            }
            if (longitudinalProfile.latestLipidSummary() != null) {
                longitudinalProfile.latestLipidSummary().stream()
                        .map(LongitudinalConceptSnapshot::sourceDocumentId)
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .forEach(selectedSourceDocumentIds::add);
            }
        }
        log.info(
                "[AI-CONTEXT-TRACE] tenantId={} patientId={} consultationId={} contextChars={} promptContextChars={} conditionCount={} labCount={} abnormalLabCount={} reportCount={} historyCount={} filteredRecommendationCount={} dedupedConceptCount={} hba1cValue={} latestBloodSugar={} lipidCount={} selectedSourceDocumentIds={}",
                tenantId,
                patientId,
                consultationId,
                clinicalContextJson == null ? 0 : clinicalContextJson.length(),
                aiPromptContext == null ? 0 : aiPromptContext.length(),
                conditionCount,
                labCount,
                abnormalLabCount,
                reportCount,
                historyCount,
                0,
                dedupedConceptCount,
                hba1cValue,
                latestBloodSugar,
                longitudinalProfile == null || longitudinalProfile.latestLipidSummary() == null ? 0 : longitudinalProfile.latestLipidSummary().size(),
                selectedSourceDocumentIds.stream().distinct().toList()
        );
    }

    private String formatLongitudinalConcept(LongitudinalConceptSnapshot concept) {
        if (concept == null) {
            return null;
        }
        String value = joinSegments(segments(concept.label(), concept.valueText() == null ? null : concept.valueText() + (hasText(concept.valueUnit()) ? " " + concept.valueUnit() : "")));
        String source = hasText(concept.sourceDocumentTitle()) ? "Source: " + concept.sourceDocumentTitle() : null;
        String date = concept.observedOn() == null ? null : concept.observedOn().toString();
        return joinSegments(segments(value, date, source));
    }

    private List<String> buildAbnormalValues(List<LabOrderResultEntity> allResults, PatientLongitudinalMemoryProfile profile) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (profile != null) {
            addDerivedAbnormalValue(values, profile.latestHbA1c());
            addDerivedAbnormalValue(values, profile.latestBloodSugar());
            if (profile.latestLipidSummary() != null) {
                profile.latestLipidSummary().forEach(snapshot -> addDerivedAbnormalValue(values, snapshot));
            }
        }
        if (values.isEmpty() && allResults != null) {
            allResults.stream()
                    .filter(this::isAbnormalResult)
                    .map(this::formatResultSummary)
                    .filter(this::hasText)
                    .forEach(values::add);
        }
        return values.stream().limit(6).toList();
    }

    private String buildPendingOrderGuidance(LabIntelligence labIntelligence) {
        if (labIntelligence == null || labIntelligence.pendingInvestigations() == null || labIntelligence.pendingInvestigations().isEmpty()) {
            return null;
        }
        List<String> guidance = new ArrayList<>();
        if (labIntelligence.pendingInvestigations().stream().anyMatch(value -> containsAny(value, "cbc"))) {
            guidance.add("Complete pending CBC/lab order before placing duplicate.");
        }
        if (hasText(labIntelligence.lastHbA1c())) {
            guidance.add("HbA1c already available from latest report; repeat only if clinically needed.");
        }
        return guidance.isEmpty() ? null : "Lab guidance: " + joinCompact(guidance, 3);
    }

    private String buildHydratedConsultationVitals(ConsultationEntity consultation, IntakeSummary intakeSummary) {
        String consultationVitals = buildConsultationVitals(consultation);
        if (hasText(consultationVitals)) {
            return consultationVitals;
        }
        return intakeSummary == null || intakeSummary.latestVitals() == null ? null : "INTAKE " + formatVitalsSnapshot(intakeSummary.latestVitals());
    }

    private String buildConsultationVitals(ConsultationEntity consultation) {
        if (consultation == null) {
            return null;
        }
        return joinSegments(segments(
                consultation.getBloodPressureSystolic() == null || consultation.getBloodPressureDiastolic() == null ? null : "BP " + consultation.getBloodPressureSystolic() + "/" + consultation.getBloodPressureDiastolic(),
                consultation.getPulseRate() == null ? null : "Pulse " + consultation.getPulseRate(),
                consultation.getTemperature() == null ? null : "Temp " + consultation.getTemperature() + (consultation.getTemperatureUnit() == null ? "" : " " + consultation.getTemperatureUnit().name()),
                consultation.getSpo2() == null ? null : "SpO2 " + consultation.getSpo2(),
                consultation.getRespiratoryRate() == null ? null : "RR " + consultation.getRespiratoryRate(),
                consultation.getWeightKg() == null ? null : "Weight " + compactText(String.valueOf(consultation.getWeightKg()), 16),
                consultation.getHeightCm() == null ? null : "Height " + compactText(String.valueOf(consultation.getHeightCm()), 16)
        ));
    }

    private List<String> dedupeRecentReportsForPrompt(List<String> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> deduped = new LinkedHashMap<>();
        for (String report : reports) {
            if (!hasText(report)) {
                continue;
            }
            String normalized = normalizeRecentReportKey(report);
            deduped.putIfAbsent(normalized, report);
        }
        if (deduped.size() > 3) {
            return deduped.values().stream().limit(3).toList();
        }
        return new ArrayList<>(deduped.values());
    }

    private String normalizeRecentReportKey(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("retest\\s*\\d+", "retest");
        normalized = normalized.replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", "");
        normalized = normalized.replaceAll("[^a-z0-9]+", " ").trim();
        return normalized;
    }

    private boolean isConsultationVitalsNull(ConsultationEntity consultation) {
        return consultation != null
                && consultation.getBloodPressureSystolic() == null
                && consultation.getBloodPressureDiastolic() == null
                && consultation.getPulseRate() == null
                && consultation.getTemperature() == null
                && consultation.getSpo2() == null
                && consultation.getRespiratoryRate() == null
                && consultation.getWeightKg() == null
                && consultation.getHeightCm() == null;
    }

    private String summarizeHydratedVitals(ClinicalContextResponse.VitalsSnapshot vitals) {
        if (vitals == null) {
            return null;
        }
        return joinSegments(segments(
                vitals.bloodPressureSystolic() == null || vitals.bloodPressureDiastolic() == null ? null : "BP",
                vitals.pulseRate() == null ? null : "Pulse",
                vitals.temperature() == null ? null : "Temp",
                vitals.spo2() == null ? null : "SpO2",
                vitals.respiratoryRate() == null ? null : "RR",
                vitals.randomBloodSugar() == null ? null : "RBS",
                vitals.bmi() == null ? null : "BMI"
        ));
    }

    private boolean containsAny(String value, String... terms) {
        if (!hasText(value) || terms == null || terms.length == 0) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (hasText(term) && normalized.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void addDerivedAbnormalValue(Set<String> values, LongitudinalConceptSnapshot concept) {
        if (concept == null) {
            return;
        }
        String flag = deriveAbnormalFlag(concept.label(), concept.valueText(), concept.valueUnit(), concept.evidenceText());
        if (!hasText(flag) || "Normal".equalsIgnoreCase(flag) || "Unknown".equalsIgnoreCase(flag)) {
            return;
        }
        String summary = joinSegments(segments(
                concept.label(),
                concept.valueText() == null ? null : concept.valueText() + (hasText(concept.valueUnit()) ? " " + concept.valueUnit() : ""),
                flag
        ));
        if (hasText(summary)) {
            values.add(summary);
        }
    }

    private String deriveAbnormalFlag(String label, String valueText, String unit, String evidenceText) {
        String evidence = evidenceText == null ? "" : evidenceText.toLowerCase(Locale.ROOT);
        if (evidence.contains("high")) {
            return "High";
        }
        if (evidence.contains("low")) {
            return "Low";
        }
        Double numeric = parseNumericValue(valueText);
        if (numeric == null) {
            numeric = parseNumericValue(evidenceText);
        }
        if (numeric == null || !hasText(label)) {
            return "Unknown";
        }
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        if (normalizedLabel.contains("hba1c") || normalizedLabel.contains("a1c") || normalizedLabel.contains("glycated hemoglobin")) {
            return numeric >= 6.5 ? "High" : "Normal";
        }
        if (normalizedLabel.contains("blood sugar") || normalizedLabel.contains("glucose") || normalizedLabel.contains("rbs")) {
            return numeric > 140 ? "High" : "Normal";
        }
        if (normalizedLabel.contains("ldl")) {
            return numeric >= 100 ? "High" : "Normal";
        }
        if (normalizedLabel.contains("hdl")) {
            return numeric < 40 ? "Low" : "Normal";
        }
        if (normalizedLabel.contains("triglyceride")) {
            return numeric >= 150 ? "High" : "Normal";
        }
        if (normalizedLabel.contains("cholesterol")) {
            return numeric >= 200 ? "High" : "Normal";
        }
        return "Unknown";
    }

    private Double parseNumericValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(value);
            return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isPendingLabOrderStatus(com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus status) {
        return status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.ORDERED
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.PAYMENT_PENDING
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.PAID
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.READY_FOR_COLLECTION
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.SAMPLE_COLLECTED
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.PROCESSING
                || status == com.deepthoughtnet.clinic.api.lab.db.LabOrderStatus.RESULT_ENTERED;
    }

    private boolean isAbnormalResult(LabOrderResultEntity result) {
        if (result == null) {
            return false;
        }
        if (result.isCriticalResult()) {
            return true;
        }
        String flag = result.getResultFlag();
        if (!hasText(flag)) {
            return false;
        }
        String normalized = flag.trim().toUpperCase(Locale.ROOT);
        return !(normalized.equals("NORMAL") || normalized.equals("N") || normalized.equals("OK") || normalized.equals("WITHIN_RANGE"));
    }

    private String formatResultSummary(LabOrderResultEntity result) {
        return joinSegments(segments(
                compactText(firstNonBlank(result.getTestName(), result.getParameterName(), result.getComponentName())),
                hasText(result.getResultValue()) ? result.getResultValue() + (hasText(result.getUnit()) ? " " + result.getUnit() : "") : null,
                hasText(result.getResultFlag()) ? "(" + result.getResultFlag() + ")" : null
        ));
    }

    private List<String> buildLabTrends(List<LabOrderResultEntity> results) {
        Map<String, List<LabOrderResultEntity>> grouped = results.stream()
                .filter(result -> hasText(result.getTestName()))
                .collect(Collectors.groupingBy(result -> normalizeMedicineName(result.getTestName()), LinkedHashMap::new, Collectors.toList()));
        List<String> trends = new ArrayList<>();
        for (String target : List.of("hba1c", "cbc", "creatinine")) {
            List<LabOrderResultEntity> matches = grouped.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(target) || entry.getKey().contains(target.replace("a1c", "a1c")))
                    .flatMap(entry -> entry.getValue().stream())
                    .sorted(Comparator.comparing(LabOrderResultEntity::getCreatedAt).reversed())
                    .limit(3)
                    .toList();
            if (matches.size() > 1) {
                trends.add(compactDisplayName(target) + ": " + matches.stream().map(this::formatResultSummary).collect(Collectors.joining(" → ")));
            }
        }
        return trends.stream().limit(5).toList();
    }

    private String findLatestLabValue(List<LabOrderResultEntity> results, String target) {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        return results.stream()
                .sorted(Comparator.comparing(LabOrderResultEntity::getCreatedAt).reversed())
                .filter(result -> {
                    String candidate = normalizeMedicineName(joinSegments(segments(result.getTestName(), result.getParameterName(), result.getComponentName())));
                    return candidate.contains(normalizedTarget) || candidate.replace(" ", "").contains(normalizedTarget.replace(" ", ""));
                })
                .map(this::formatResultSummary)
                .findFirst()
                .orElse(null);
    }

    private boolean isType(ClinicalDocumentEntity document, String... types) {
        if (document == null || document.getDocumentType() == null) {
            return false;
        }
        String actual = document.getDocumentType().name();
        for (String type : types) {
            if (actual.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private String formatDocument(ClinicalDocumentEntity document) {
        return joinSegments(segments(
                formatHumanDate(firstNonNull(document.getReportDate(), document.getCreatedAt() == null ? null : document.getCreatedAt().toLocalDate())),
                compactText(firstNonBlank(document.getTitle(), documentLabel(document))),
                compactText(documentStatusLabel(document))
        ));
    }

    private String documentLabel(ClinicalDocumentEntity document) {
        if (document == null || document.getDocumentType() == null) {
            return "Document";
        }
        return switch (document.getDocumentType()) {
            case EXTERNAL_LAB_REPORT, INTERNAL_LAB_REPORT, LAB_REPORT -> "Lab Report";
            case RADIOLOGY_REPORT, X_RAY, MRI_CT -> "Radiology";
            case REFERRAL_LETTER, REFERRAL -> "Referral";
            case DISCHARGE_SUMMARY -> "Discharge Summary";
            case OLD_PRESCRIPTION, PRESCRIPTION -> "Prescription";
            case INSURANCE_DOCUMENT, INSURANCE -> "Insurance";
            case IDENTITY_DOCUMENT -> "Identity Document";
            default -> "Document";
        };
    }

    private String compactText(String value) {
        return compactText(value, 120);
    }

    private String compactText(String value, int maxLength) {
        if (!hasText(value)) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        int cutoff = Math.max(0, maxLength - 3);
        return cleaned.substring(0, cutoff) + "...";
    }

    private String compactDisplayName(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isCompletedConsultation(ConsultationEntity consultation) {
        return consultation != null && consultation.getStatus() == com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus.COMPLETED;
    }

    private String documentStatusLabel(ClinicalDocumentEntity document) {
        if (document == null) {
            return null;
        }
        String status = firstNonBlank(document.getAiExtractionStatus(), document.getVerificationStatus());
        if (!hasText(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "REVIEW_REQUIRED", "AI_REVIEW_REQUIRED", "PENDING_REVIEW", "UNVERIFIED", "NOT_REVIEWED" -> "Pending Review";
            case "APPROVED" -> "Verified";
            case "REJECTED" -> "Rejected";
            case "FAILED" -> "AI Failed";
            default -> compactText(status);
        };
    }

    private String formatHumanDate(LocalDate value) {
        return value == null ? null : value.format(HUMAN_DATE_FORMAT);
    }

    private LocalDate firstNonNull(LocalDate... values) {
        if (values == null) {
            return null;
        }
        for (LocalDate value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDate firstNonNullDateTime(OffsetDateTime... values) {
        if (values == null) {
            return null;
        }
        for (OffsetDateTime value : values) {
            if (value != null) {
                return value.toLocalDate();
            }
        }
        return null;
    }

    private String formatLabReportSummary(LabOrderEntity order) {
        if (order == null) {
            return null;
        }
        String title = sanitizeLabTitle(firstNonBlank(order.getNotes(), order.getSampleType(), order.getExternalLabVendor(), order.getExternalReferenceNumber()));
        if (!hasText(title)) {
            title = isPendingLabOrderStatus(order.getStatus()) ? "Pending lab order" : "Lab report";
        }
        return joinSegments(segments(
                formatHumanDate(firstNonNullDateTime(order.getReportPublishedAt(), order.getReportGeneratedAt(), order.getResultEnteredAt(), order.getOrderedAt())),
                compactText(title),
                order.getStatus() != null && isPendingLabOrderStatus(order.getStatus()) ? "Pending" : null
        ));
    }

    private String formatPendingLabOrderSummary(LabOrderEntity order) {
        if (order == null) {
            return null;
        }
        String title = sanitizeLabTitle(firstNonBlank(order.getNotes(), order.getSampleType(), order.getExternalLabVendor(), order.getExternalReferenceNumber()));
        if (!hasText(title)) {
            title = isPendingLabOrderStatus(order.getStatus()) ? "Pending lab order" : "Lab order";
        }
        return joinSegments(segments(
                formatHumanDate(firstNonNullDateTime(order.getReportPublishedAt(), order.getReportGeneratedAt(), order.getResultEnteredAt(), order.getOrderedAt())),
                compactText(title),
                order.getStatus() == null ? null : order.getStatus().name().replace('_', ' ')
        ));
    }

    private String documentStatusLike(LabOrderEntity order) {
        if (order == null || order.getStatus() == null) {
            return null;
        }
        return switch (order.getStatus()) {
            case ORDERED, PAYMENT_PENDING, PAID, READY_FOR_COLLECTION, SAMPLE_COLLECTED, PROCESSING, RESULT_ENTERED -> "Pending";
            case REPORT_READY, REPORT_GENERATED, DOCTOR_REVIEWED, DELIVERED -> "Completed";
            default -> order.getStatus().name().replace('_', ' ');
        };
    }

    private String sanitizeLabTitle(String title) {
        if (!hasText(title)) {
            return null;
        }
        String normalized = title.trim();
        if (normalized.matches("(?i)^LAB-[A-Z0-9-]+$")) {
            return null;
        }
        if (normalized.matches("(?i)^null$")) {
            return null;
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 77) + "...";
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String joinSegments(Collection<String> values) {
        return values.stream().filter(this::hasText).map(this::compactText).filter(this::hasText).collect(Collectors.joining(" | "));
    }

    private String joinCompact(Collection<String> values, int limit) {
        return values.stream().filter(this::hasText).map(this::compactText).filter(this::hasText).limit(limit).collect(Collectors.joining(", "));
    }

    private boolean containsHint(Collection<String> values, List<String> hints) {
        return values.stream()
                .filter(this::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> hints.stream().anyMatch(value::contains));
    }

    private boolean hasAllergyOverlap(String allergies, Collection<String> medicines) {
        if (!hasText(allergies) || medicines.isEmpty()) {
            return false;
        }
        String normalizedAllergies = allergies.toLowerCase(Locale.ROOT);
        return medicines.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedAllergies::contains);
    }

    private boolean isAntibiotic(String medicineName) {
        if (!hasText(medicineName)) {
            return false;
        }
        String normalized = medicineName.toLowerCase(Locale.ROOT);
        return ANTIBIOTIC_HINTS.stream().anyMatch(normalized::contains);
    }

    private String normalizeMedicineName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(mg|mcg|ml|g|iu|units|iu/|mcg/)?\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String formatDate(OffsetDateTime value) {
        return value == null ? null : value.toLocalDate().format(DATE_FORMAT);
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.format(DATE_FORMAT);
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? null : value.toLocalDate().format(DATE_FORMAT);
    }

    private LocalDate parseDateValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildPatientName(PatientEntity patient) {
        return joinSegments(segments(patient.getFirstName(), patient.getLastName()));
    }

    private List<String> splitFreeText(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;\\n•/]+"))
                .map(String::trim)
                .filter(this::hasText)
                .map(this::compactText)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private List<String> segments(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).toList();
    }

}
