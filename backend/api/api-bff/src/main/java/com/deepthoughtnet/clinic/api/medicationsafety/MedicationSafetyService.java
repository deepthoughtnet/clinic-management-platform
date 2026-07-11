package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MedicationSafetyService {
    private static final Logger log = LoggerFactory.getLogger(MedicationSafetyService.class);

    private final ConsultationRepository consultationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineRepository prescriptionMedicineRepository;
    private final PatientRepository patientRepository;
    private final MedicineRepository medicineRepository;
    private final ClinicalContextService clinicalContextService;
    private final MedicationSafetyEngine medicationSafetyEngine;
    private final AuditEventPublisher auditEventPublisher;

    public MedicationSafetyService(ConsultationRepository consultationRepository,
                                   PrescriptionRepository prescriptionRepository,
                                   PrescriptionMedicineRepository prescriptionMedicineRepository,
                                   PatientRepository patientRepository,
                                   MedicineRepository medicineRepository,
                                   ClinicalContextService clinicalContextService,
                                   MedicationSafetyEngine medicationSafetyEngine,
                                   AuditEventPublisher auditEventPublisher) {
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.patientRepository = patientRepository;
        this.medicineRepository = medicineRepository;
        this.clinicalContextService = clinicalContextService;
        this.medicationSafetyEngine = medicationSafetyEngine;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public MedicationSafetyEvaluationResult evaluateForConsultation(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        ConsultationEntity consultation = consultationRepository.findByTenantIdAndId(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, consultation.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        PrescriptionEntity prescription = prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)
                .orElse(null);
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId);
        MedicationSafetyEvaluationRequest request = buildRequest(tenantId, consultation, patient, prescription, clinicalContext);
        MedicationSafetyEvaluationResult result = medicationSafetyEngine.evaluate(request);
        log.info(
                "[MED-SAFETY-TRACE] tenantId={} patientId={} consultationId={} prescriptionId={} rulesVersion={} ruleCount={} findingCount={} severity={}",
                tenantId,
                patient.getId(),
                consultationId,
                request.prescriptionId(),
                result.rulesVersion(),
                10,
                result.findings() == null ? 0 : result.findings().size(),
                result.overallSeverity()
        );
        log.info(
                "[MED-SAFETY-RENAL-TRACE] consultationId={} renalContextPresent={} creatininePresent={} egfrPresent={} observedOn={} verificationStatus={} renalRulesEvaluated={} renalFindings={} renalSourceDocumentIds={}",
                consultationId,
                request.renalContext() != null,
                request.renalContext() != null && hasText(request.renalContext().creatinine()),
                request.renalContext() != null && hasText(request.renalContext().egfr()),
                request.renalContext() == null ? null : firstNonBlank(request.renalContext().creatinineDate(), request.renalContext().egfrDate()),
                request.renalContext() == null ? null : request.renalContext().verificationStatus(),
                result.evaluationCoverage() != null && "EVALUATED".equalsIgnoreCase(result.evaluationCoverage().renalCoverageStatus()),
                result.findings() == null ? 0 : result.findings().stream().filter(finding -> finding != null && finding.ruleCode() != null && finding.ruleCode().startsWith("MED_RENAL")).count(),
                request.renalContext() == null ? List.of() : request.renalContext().sourceDocumentIds()
        );
        return result;
    }

    private MedicationSafetyEvaluationRequest buildRequest(UUID tenantId,
                                                            ConsultationEntity consultation,
                                                            PatientEntity patient,
                                                            PrescriptionEntity prescription,
                                                            ClinicalContextResponse clinicalContext) {
        List<MedicationSafetyMedicationItem> proposed = loadProposedMedications(tenantId, prescription);
        List<MedicationSafetyMedicationItem> current = filterDraftOverlap(loadCurrentMedications(clinicalContext, patient), proposed);
        MedicationSafetyEvaluationRequest.AllergySnapshot allergies = buildAllergySnapshot(patient);
        List<String> activeConditions = buildActiveConditions(patient, clinicalContext);
        MedicationSafetyEvaluationRequest.RenalSnapshot renal = buildRenalSnapshot(clinicalContext);
        MedicationSafetyEvaluationRequest.HepaticSnapshot hepatic = buildHepaticSnapshot(clinicalContext);
        return new MedicationSafetyEvaluationRequest(
                tenantId,
                patient.getId(),
                consultation.getId(),
                prescription == null ? null : prescription.getId(),
                prescription == null ? null : prescription.getStatus().name(),
                proposed,
                current,
                allergies,
                activeConditions,
                renal,
                hepatic,
                patient.getAgeYears(),
                patient.getGender() == null ? null : patient.getGender().name(),
                null,
                Map.of(
                        "patientNumber", patient.getPatientNumber(),
                        "consultationStatus", consultation.getStatus() == null ? null : consultation.getStatus().name()
                )
        );
    }

    private List<MedicationSafetyMedicationItem> loadProposedMedications(UUID tenantId, PrescriptionEntity prescription) {
        if (prescription == null) {
            return List.of();
        }
        Map<String, MedicineEntity> medicinesByName = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .filter(medicine -> medicine != null && hasText(medicine.getMedicineName()))
                .collect(Collectors.toMap(medicine -> normalizeProductName(medicine.getMedicineName()), medicine -> medicine, (left, right) -> left, LinkedHashMap::new));
        List<PrescriptionMedicineEntity> lines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId());
        List<MedicationSafetyMedicationItem> proposed = new ArrayList<>();
        for (PrescriptionMedicineEntity line : lines) {
            String lookupKey = normalizeProductName(line.getMedicineName());
            MedicineEntity medicine = medicinesByName.get(lookupKey);
            log.debug(
                    "[MED-SAFETY-IDENTITY-TRACE] prescriptionRowId={} medicineId={} medicineName={} genericName={} strength={} dosageForm={} exactIdentity={} ingredientIdentity={}",
                    line.getId(),
                    medicine == null ? null : medicine.getId(),
                    line.getMedicineName(),
                    medicine == null ? null : medicine.getGenericName(),
                    medicine == null ? line.getStrength() : firstNonBlank(line.getStrength(), medicine.getStrength()),
                    medicine == null ? null : medicine.getDosageForm(),
                    lookupKey,
                    medicine == null ? null : normalizeIngredientIdentity(medicine.getGenericName())
            );
            proposed.add(toMedicationItem(line, medicine, "PRESCRIPTION_DRAFT", prescription.getStatus() == null ? null : prescription.getStatus().name()));
        }
        return proposed;
    }

    private List<MedicationSafetyMedicationItem> loadCurrentMedications(ClinicalContextResponse clinicalContext, PatientEntity patient) {
        LinkedHashMap<String, MedicationSafetyMedicationItem> current = new LinkedHashMap<>();
        if (clinicalContext != null && clinicalContext.longitudinalMemory() != null && clinicalContext.longitudinalMemory().longTermMedications() != null) {
            clinicalContext.longitudinalMemory().longTermMedications().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.label()))
                    .forEach(item -> {
                        String key = normalizeMedicineName(item.label());
                        current.putIfAbsent(key, new MedicationSafetyMedicationItem(
                                null,
                                null,
                                item.label(),
                                key,
                                List.of(),
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
                                "LONGITUDINAL_MEMORY",
                                null,
                                item.verificationStatus(),
                                item.confidence(),
                                item.sourceDocumentId() == null ? null : item.sourceDocumentId().toString(),
                                item.sourceDocumentTitle(),
                                parseLocalDate(item.observedOn())
                        ));
                    });
        }
        if (clinicalContext != null && clinicalContext.patientSummary() != null && clinicalContext.patientSummary().currentMedications() != null) {
            clinicalContext.patientSummary().currentMedications().stream()
                    .filter(this::hasText)
                    .forEach(name -> current.putIfAbsent(normalizeMedicineName(name), new MedicationSafetyMedicationItem(
                            null,
                            null,
                            name,
                            normalizeMedicineName(name),
                            List.of(),
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
                            "PATIENT_CONTEXT",
                            "UNKNOWN",
                            null,
                            null,
                            null,
                            null,
                            null
                    )));
        }
        if (patient != null && hasText(patient.getLongTermMedications())) {
            for (String token : splitFreeText(patient.getLongTermMedications())) {
                current.putIfAbsent(normalizeMedicineName(token), new MedicationSafetyMedicationItem(
                        null,
                        null,
                        token,
                        normalizeMedicineName(token),
                        List.of(),
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
                        "PATIENT_RECORD",
                        "UNKNOWN",
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }
        return new ArrayList<>(current.values());
    }

    private List<MedicationSafetyMedicationItem> filterDraftOverlap(List<MedicationSafetyMedicationItem> current, List<MedicationSafetyMedicationItem> proposed) {
        if (current == null || current.isEmpty() || proposed == null || proposed.isEmpty()) {
            return current == null ? List.of() : current;
        }
        LinkedHashSet<String> proposedIds = new LinkedHashSet<>();
        for (MedicationSafetyMedicationItem item : proposed) {
            proposedIds.addAll(medicationOverlapKeys(item));
        }
        proposedIds.removeIf(key -> !hasText(key));
        return current.stream()
                .filter(item -> {
                    for (String key : medicationOverlapKeys(item)) {
                        if (proposedIds.contains(key)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    private List<String> medicationOverlapKeys(MedicationSafetyMedicationItem item) {
        if (item == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (hasText(item.medicineId())) {
            keys.add(item.medicineId().trim());
        }
        if (hasText(item.normalizedMedicineName())) {
            keys.add(normalizeMedicineName(item.normalizedMedicineName()));
        }
        if (hasText(item.medicineName())) {
            keys.add(normalizeMedicineName(item.medicineName()));
        }
        return new ArrayList<>(keys);
    }

    private MedicationSafetyMedicationItem toMedicationItem(PrescriptionMedicineEntity line, MedicineEntity medicine, String source, String status) {
        List<String> ingredients = medicine == null || !hasText(medicine.getGenericName()) ? List.of() : List.of(medicine.getGenericName());
        return new MedicationSafetyMedicationItem(
                line.getId() == null ? null : line.getId().toString(),
                medicine == null ? null : medicine.getId().toString(),
                line.getMedicineName(),
                normalizeMedicineName(line.getMedicineName()),
                ingredients,
                medicine == null ? null : medicine.getCategory(),
                medicine == null ? line.getStrength() : firstNonBlank(line.getStrength(), medicine.getStrength()),
                medicine == null ? null : medicine.getUnit(),
                line.getDosage(),
                null,
                line.getFrequency(),
                line.getDuration(),
                line.getTiming() == null ? null : line.getTiming().name(),
                null,
                false,
                source,
                status,
                "PENDING_REVIEW",
                null,
                null,
                null,
                null
        );
    }

    private MedicationSafetyEvaluationRequest.AllergySnapshot buildAllergySnapshot(PatientEntity patient) {
        String allergies = patient == null ? null : patient.getAllergies();
        List<String> terms = splitFreeText(allergies);
        boolean unknown = !hasText(allergies);
        boolean noKnown = hasText(allergies) && normalizeText(allergies).matches(".*\\b(nkda|no known allergies?|none|no allergies?)\\b.*");
        return new MedicationSafetyEvaluationRequest.AllergySnapshot(allergies, terms, unknown, noKnown, unknown ? "UNKNOWN" : "PENDING_REVIEW");
    }

    private List<String> buildActiveConditions(PatientEntity patient, ClinicalContextResponse clinicalContext) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (patient != null && hasText(patient.getExistingConditions())) {
            values.addAll(splitFreeText(patient.getExistingConditions()));
        }
        if (clinicalContext != null && clinicalContext.longitudinalMemory() != null && clinicalContext.longitudinalMemory().knownConditions() != null) {
            clinicalContext.longitudinalMemory().knownConditions().stream()
                    .map(item -> item == null ? null : item.label())
                    .filter(this::hasText)
                    .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private MedicationSafetyEvaluationRequest.RenalSnapshot buildRenalSnapshot(ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().renalContext() == null) {
            return null;
        }
        ClinicalContextResponse.RenalContext renalContext = clinicalContext.longitudinalClinicalContext().renalContext();
        return new MedicationSafetyEvaluationRequest.RenalSnapshot(
                renalContext.creatinine(),
                renalContext.creatinineDate(),
                renalContext.egfr(),
                renalContext.egfrDate(),
                renalContext.verificationStatus(),
                renalContext.stalenessDays(),
                renalContext.sourceDocumentIds()
        );
    }

    private MedicationSafetyEvaluationRequest.HepaticSnapshot buildHepaticSnapshot(ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.longitudinalMemory() == null || clinicalContext.longitudinalMemory().history() == null) {
            return null;
        }
        var latestAlt = clinicalContext.longitudinalMemory().history().stream()
                .filter(item -> item != null && hasText(item.conceptKey()) && normalizeMedicineName(item.conceptKey()).contains("alt"))
                .findFirst()
                .orElse(null);
        var latestAst = clinicalContext.longitudinalMemory().history().stream()
                .filter(item -> item != null && hasText(item.conceptKey()) && normalizeMedicineName(item.conceptKey()).contains("ast"))
                .findFirst()
                .orElse(null);
        if (latestAlt == null && latestAst == null) {
            return null;
        }
        return new MedicationSafetyEvaluationRequest.HepaticSnapshot(
                latestAlt == null ? null : latestAlt.valueText(),
                latestAlt == null ? null : latestAlt.observedOn(),
                latestAst == null ? null : latestAst.valueText(),
                latestAst == null ? null : latestAst.observedOn(),
                null,
                null,
                null,
                latestAlt != null && parseLocalDate(latestAlt.observedOn()) != null
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(parseLocalDate(latestAlt.observedOn()), LocalDate.now())
                        : latestAst != null && parseLocalDate(latestAst.observedOn()) != null
                        ? (int) java.time.temporal.ChronoUnit.DAYS.between(parseLocalDate(latestAst.observedOn()), LocalDate.now())
                        : null,
                sourceDocumentIds(latestAlt, latestAst)
        );
    }

    private String normalizeMedicineName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(mg|mcg|ml|g|iu|units|tablet|tab|capsule|cap|syrup|suspension|drop|drops|ointment|cream|injection|inj|sr|er|mr|xl|od|bd|bid|tid|qid)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeProductName(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeIngredientIdentity(String value) {
        return normalizeProductName(value);
    }

    private List<String> splitFreeText(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("[,;\\n•/]+"))
                .map(String::trim)
                .filter(this::hasText)
                .map(this::normalizeText)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private LocalDate parseLocalDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<String> sourceDocumentIds(Object... items) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (items != null) {
            for (Object item : items) {
                if (item instanceof com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse.LongitudinalConcept concept) {
                    if (concept.sourceDocumentId() != null) {
                        ids.add(concept.sourceDocumentId().toString());
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
