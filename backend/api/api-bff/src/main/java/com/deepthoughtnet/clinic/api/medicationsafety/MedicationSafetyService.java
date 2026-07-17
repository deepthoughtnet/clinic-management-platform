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
import org.slf4j.MDC;
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
    private final MedicationSafetySnapshotHasher medicationSafetySnapshotHasher;
    private final AuditEventPublisher auditEventPublisher;

    public MedicationSafetyService(ConsultationRepository consultationRepository,
                                   PrescriptionRepository prescriptionRepository,
                                   PrescriptionMedicineRepository prescriptionMedicineRepository,
                                   PatientRepository patientRepository,
                                   MedicineRepository medicineRepository,
                                   ClinicalContextService clinicalContextService,
                                   MedicationSafetyEngine medicationSafetyEngine,
                                   MedicationSafetySnapshotHasher medicationSafetySnapshotHasher,
                                   AuditEventPublisher auditEventPublisher) {
        this.consultationRepository = consultationRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionMedicineRepository = prescriptionMedicineRepository;
        this.patientRepository = patientRepository;
        this.medicineRepository = medicineRepository;
        this.clinicalContextService = clinicalContextService;
        this.medicationSafetyEngine = medicationSafetyEngine;
        this.medicationSafetySnapshotHasher = medicationSafetySnapshotHasher;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public MedicationSafetyEvaluationResult evaluateForConsultation(UUID tenantId, UUID consultationId, UUID actorAppUserId) {
        String traceId = traceId();
        boolean injected = !traceId.equals(MDC.get("medSafetyTraceId"));
        if (injected) {
            MDC.put("medSafetyTraceId", traceId);
        }
        try {
            log.info(
                    "MED-SAFETY-TRACE stage=START traceId={} tenantId={} consultationId={} patientId={} prescriptionId={} actorAppUserId={}",
                    traceId,
                    tenantId,
                    consultationId,
                    null,
                    null,
                    actorAppUserId
            );
            MedicationSafetyEvaluationContext context = buildEvaluationContext(tenantId, consultationId);
            MedicationSafetyEvaluationRequest request = context.request();
            MedicationSafetyEvaluationResult result = medicationSafetyEngine.evaluate(request);
            log.info(
                    "MED-SAFETY-TRACE stage=SERVICE_RESPONSE traceId={} tenantId={} patientId={} consultationId={} prescriptionId={} evaluationId={} rulesVersion={} findingCount={} severity={}",
                    traceId,
                    tenantId,
                    context.patient().getId(),
                    consultationId,
                    request.prescriptionId(),
                    result.evaluationId(),
                    result.rulesVersion(),
                    result.findings() == null ? 0 : result.findings().size(),
                    result.overallSeverity()
            );
            log.info(
                    "MED-SAFETY-TRACE stage=SERVICE_RENAL traceId={} tenantId={} consultationId={} patientId={} prescriptionId={} evaluationId={} renalContextPresent={} creatininePresent={} egfrPresent={} observedOn={} verificationStatus={} renalRulesEvaluated={} renalFindings={} renalSourceDocumentIds={}",
                    traceId,
                    tenantId,
                    consultationId,
                    context.patient().getId(),
                    request.prescriptionId(),
                    result.evaluationId(),
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
        } finally {
            if (injected) {
                MDC.remove("medSafetyTraceId");
            }
        }
    }

    MedicationSafetyEvaluationContext buildEvaluationContext(UUID tenantId, UUID consultationId) {
        String traceId = traceId();
        ConsultationEntity consultation = consultationRepository.findByTenantIdAndId(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, consultation.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        PrescriptionEntity prescription = prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)
                .orElse(null);
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, patient.getId(), consultationId);
        MedicationSafetyEvaluationRequest request = buildRequest(tenantId, consultation, patient, prescription, clinicalContext);
        String prescriptionHash = medicationSafetySnapshotHasher.prescriptionHash(request, prescription == null ? null : prescription.getVersionNumber());
        String patientContextHash = medicationSafetySnapshotHasher.patientContextHash(request);
        String snapshotHash = medicationSafetySnapshotHasher.evaluationHash(prescriptionHash, patientContextHash, medicationSafetyEngine.rulesVersion());
        log.info(
                "MED-SAFETY-TRACE stage=CONTEXT_ENRICHED traceId={} tenantId={} consultationId={} patientId={} prescriptionId={} clinicalContextPresent={} clinicalContextChars={} aiPromptContextChars={} latestVitalsPresent={} conditionCount={} labCount={} reportCount={} longitudinalFindingCount={} requestMedicationCount={} allergyCount={} renalContextPresent={} hepaticContextPresent={}",
                traceId,
                tenantId,
                consultationId,
                patient.getId(),
                prescription == null ? null : prescription.getId(),
                clinicalContext != null,
                clinicalContext == null ? 0 : safeStringLength(clinicalContext.clinicalContextJson()),
                clinicalContext == null ? 0 : safeStringLength(clinicalContext.aiPromptContext()),
                clinicalContext != null && clinicalContext.intakeSummary() != null && clinicalContext.intakeSummary().latestVitals() != null,
                clinicalContext == null || clinicalContext.longitudinalMemory() == null || clinicalContext.longitudinalMemory().knownConditions() == null ? 0 : clinicalContext.longitudinalMemory().knownConditions().size(),
                clinicalContext == null || clinicalContext.labIntelligence() == null ? 0 : countNonBlankStrings(
                        clinicalContext.labIntelligence().abnormalValues(),
                        clinicalContext.labIntelligence().previousTrends(),
                        clinicalContext.labIntelligence().pendingInvestigations()
                ),
                clinicalContext == null || clinicalContext.documentIntelligence() == null ? 0 : countNonBlankStrings(
                        clinicalContext.documentIntelligence().recentReports(),
                        clinicalContext.documentIntelligence().radiology(),
                        clinicalContext.documentIntelligence().referrals(),
                        clinicalContext.documentIntelligence().dischargeSummaries()
                ),
                clinicalContext == null || clinicalContext.longitudinalClinicalContext() == null || clinicalContext.longitudinalClinicalContext().importantHistoricalFindings() == null ? 0 : clinicalContext.longitudinalClinicalContext().importantHistoricalFindings().size(),
                request.proposedMedications() == null ? 0 : request.proposedMedications().size(),
                request.allergies() == null || request.allergies().terms() == null ? 0 : request.allergies().terms().size(),
                request.renalContext() != null,
                request.hepaticContext() != null
        );
        return new MedicationSafetyEvaluationContext(consultation, patient, prescription, clinicalContext, request, prescriptionHash, patientContextHash, snapshotHash);
    }

    void lockPrescriptionForSafety(UUID tenantId, UUID prescriptionId) {
        if (tenantId == null || prescriptionId == null) {
            return;
        }
        prescriptionRepository.findByTenantIdAndIdForUpdate(tenantId, prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
    }

    MedicationSafetyEvaluationRequest buildRequest(UUID tenantId,
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
                buildSourceVerificationMetadata(patient, consultation)
        );
    }

    private List<MedicationSafetyMedicationItem> loadProposedMedications(UUID tenantId, PrescriptionEntity prescription) {
        if (prescription == null) {
            return List.of();
        }
        List<MedicineEntity> medicines = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId);
        Map<String, MedicineEntity> medicinesByName = (medicines == null ? List.<MedicineEntity>of() : medicines).stream()
                .filter(medicine -> medicine != null && hasText(medicine.getMedicineName()))
                .collect(Collectors.toMap(medicine -> normalizeProductName(medicine.getMedicineName()), medicine -> medicine, (left, right) -> left, LinkedHashMap::new));
        List<PrescriptionMedicineEntity> lines = prescriptionMedicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(tenantId, prescription.getId());
        if (lines == null) {
            return List.of();
        }
        List<MedicationSafetyMedicationItem> proposed = new ArrayList<>();
        for (PrescriptionMedicineEntity line : lines) {
            String lookupKey = normalizeProductName(line.getMedicineName());
            MedicineEntity medicine = medicinesByName.get(lookupKey);
            String exactProductIdentity = exactProductIdentity(line, medicine);
            String ingredientIdentity = medicine == null ? null : normalizeIngredientIdentity(medicine.getGenericName());
            log.debug(
                    "[MED-SAFETY-IDENTITY-TRACE] prescriptionRowId={} medicineId={} medicineName={} genericName={} strength={} dosageForm={} exactIdentity={} ingredientIdentity={}",
                    line.getId(),
                    medicine == null ? null : medicine.getId(),
                    line.getMedicineName(),
                    medicine == null ? null : medicine.getGenericName(),
                    medicine == null ? line.getStrength() : firstNonBlank(line.getStrength(), medicine.getStrength()),
                    medicine == null ? null : medicine.getDosageForm(),
                    exactProductIdentity,
                    ingredientIdentity
            );
            proposed.add(toMedicationItem(line, medicine, exactProductIdentity, "PRESCRIPTION_DRAFT", prescription.getStatus() == null ? null : prescription.getStatus().name()));
        }
        return proposed;
    }

    private List<MedicationSafetyMedicationItem> loadCurrentMedications(ClinicalContextResponse clinicalContext, PatientEntity patient) {
        LinkedHashMap<String, MedicationSafetyMedicationItem> current = new LinkedHashMap<>();
        List<ClinicalContextResponse.LongitudinalConcept> longTermMedications = clinicalContext == null || clinicalContext.longitudinalMemory() == null
                ? null
                : clinicalContext.longitudinalMemory().longTermMedications();
        if (longTermMedications != null) {
            longTermMedications.stream()
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
        List<String> currentMedications = clinicalContext == null || clinicalContext.patientSummary() == null
                ? null
                : clinicalContext.patientSummary().currentMedications();
        if (currentMedications != null) {
            currentMedications.stream()
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

    private MedicationSafetyMedicationItem toMedicationItem(PrescriptionMedicineEntity line, MedicineEntity medicine, String exactProductIdentity, String source, String status) {
        List<String> ingredients = deriveActiveIngredients(line, medicine);
        return new MedicationSafetyMedicationItem(
                line.getId() == null ? null : line.getId().toString(),
                exactProductIdentity,
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

    private List<String> deriveActiveIngredients(PrescriptionMedicineEntity line, MedicineEntity medicine) {
        if (medicine != null && hasText(medicine.getGenericName())) {
            return List.of(medicine.getGenericName());
        }
        String fallback = normalizeMedicineName(line == null ? null : line.getMedicineName());
        if (!hasText(fallback) || !looksLikeStructuredMedicineName(line == null ? null : line.getMedicineName())) {
            return List.of();
        }
        return List.of(fallback);
    }

    private boolean looksLikeStructuredMedicineName(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.matches(".*\\d.*") || normalized.matches(".*\\b(mg|mcg|g|ml|tablet|tab|capsule|cap|syrup|suspension|drop|drops|ointment|cream|injection|inj)\\b.*");
    }

    private String exactProductIdentity(PrescriptionMedicineEntity line, MedicineEntity medicine) {
        if (medicine != null && hasText(medicine.getId() == null ? null : medicine.getId().toString())) {
            return medicine.getId().toString();
        }
        String sourceName = line == null ? null : line.getMedicineName();
        String strength = line == null ? null : line.getStrength();
        String dose = line == null ? null : line.getDosage();
        String frequency = line == null ? null : line.getFrequency();
        String duration = line == null ? null : line.getDuration();
        String timing = line == null || line.getTiming() == null ? null : line.getTiming().name();
        return String.join("|",
                "sig",
                normalizeProductName(sourceName),
                normalizeExactValue(strength),
                normalizeExactValue(dose),
                normalizeExactValue(frequency),
                normalizeExactValue(duration),
                normalizeExactValue(timing)
        );
    }

    String normalizeExactValue(String value) {
        return hasText(value) ? value.trim().replaceAll("\\s+", " ") : "";
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
        List<ClinicalContextResponse.LongitudinalConcept> knownConditions = clinicalContext == null || clinicalContext.longitudinalMemory() == null
                ? null
                : clinicalContext.longitudinalMemory().knownConditions();
        if (knownConditions != null) {
            knownConditions.stream()
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
        List<String> sourceDocumentIds = renalContext.sourceDocumentIds();
        return new MedicationSafetyEvaluationRequest.RenalSnapshot(
                renalContext.creatinine(),
                renalContext.creatinineDate(),
                renalContext.egfr(),
                renalContext.egfrDate(),
                renalContext.verificationStatus(),
                renalContext.stalenessDays(),
                sourceReferences(clinicalContext, sourceDocumentIds),
                sourceDocumentIds
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

    private List<String> sourceReferences(ClinicalContextResponse clinicalContext, List<String> sourceDocumentIds) {
        if (sourceDocumentIds == null || sourceDocumentIds.isEmpty()) {
            return List.of();
        }
        Map<String, ClinicalContextResponse.LongitudinalConcept> conceptBySourceId = new LinkedHashMap<>();
        if (clinicalContext != null
                && clinicalContext.longitudinalMemory() != null
                && clinicalContext.longitudinalMemory().history() != null) {
            for (ClinicalContextResponse.LongitudinalConcept concept : clinicalContext.longitudinalMemory().history()) {
                if (concept == null || concept.sourceDocumentId() == null || conceptBySourceId.containsKey(concept.sourceDocumentId().toString())) {
                    continue;
                }
                conceptBySourceId.put(concept.sourceDocumentId().toString(), concept);
            }
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (String sourceDocumentId : sourceDocumentIds) {
            if (!hasText(sourceDocumentId)) {
                continue;
            }
            ClinicalContextResponse.LongitudinalConcept concept = conceptBySourceId.get(sourceDocumentId);
            refs.add(firstNonBlank(
                    concept == null ? null : concept.sourceDocumentTitle(),
                    humanizeSourceType(concept == null ? null : concept.sourceDocumentType()),
                    "Longitudinal memory"
            ));
        }
        return new ArrayList<>(refs);
    }

    private String humanizeSourceType(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('_', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (char c : normalized.toCharArray()) {
            if (Character.isWhitespace(c)) {
                builder.append(c);
                capitalizeNext = true;
                continue;
            }
            builder.append(capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
            capitalizeNext = false;
        }
        return builder.toString().trim();
    }

    private Map<String, Object> buildSourceVerificationMetadata(PatientEntity patient, ConsultationEntity consultation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (patient != null && hasText(patient.getPatientNumber())) {
            metadata.put("patientNumber", patient.getPatientNumber());
        }
        if (consultation != null && consultation.getStatus() != null) {
            metadata.put("consultationStatus", consultation.getStatus().name());
        }
        return metadata;
    }

    private int safeStringLength(String value) {
        return value == null ? 0 : value.length();
    }

    @SafeVarargs
    private final int countNonBlankStrings(List<String>... groups) {
        if (groups == null || groups.length == 0) {
            return 0;
        }
        int count = 0;
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String value : group) {
                if (hasText(value)) {
                    count++;
                }
            }
        }
        return count;
    }

    private String traceId() {
        String traceId = MDC.get("medSafetyTraceId");
        return hasText(traceId) ? traceId : UUID.randomUUID().toString();
    }
}
