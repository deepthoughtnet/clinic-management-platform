package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalMemoryRepairCorrectedValue;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.DeterministicLabFactParser;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptRepository;
import com.deepthoughtnet.clinic.api.clinicalmemory.mapping.ClinicalConceptMapper;
import com.deepthoughtnet.clinic.api.clinicalmemory.mapping.ClinicalConceptMapper.MappedConcept;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

@Service
public class PatientLongitudinalMemoryService {
    private static final Logger log = LoggerFactory.getLogger(PatientLongitudinalMemoryService.class);
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final java.util.Set<String> ALLOWED_CONCEPT_FAMILIES = java.util.Set.of(
            "CONDITION",
            "LAB_RESULT",
            "MEDICATION",
            "ALLERGY",
            "VITAL",
            "RISK_FLAG",
            "PROCEDURE",
            "VACCINATION",
            "FAMILY_HISTORY",
            "SOCIAL_HISTORY"
    );

    private final PatientLongitudinalConceptRepository repository;
    private final ClinicalConceptMapper mapper;
    private final DeterministicLabFactParser deterministicLabFactParser;
    private final ObjectMapper objectMapper;

    public PatientLongitudinalMemoryService(PatientLongitudinalConceptRepository repository,
                                            ClinicalConceptMapper mapper,
                                            ObjectMapper objectMapper) {
        this(repository, mapper, new DeterministicLabFactParser(), objectMapper);
    }

    @Autowired
    public PatientLongitudinalMemoryService(PatientLongitudinalConceptRepository repository,
                                            ClinicalConceptMapper mapper,
                                            DeterministicLabFactParser deterministicLabFactParser,
                                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.deterministicLabFactParser = deterministicLabFactParser;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ingestPendingConcepts(ClinicalDocumentEntity document, String structuredJson, String ocrText, BigDecimal confidence, String sourceSummary) {
        if (document == null || document.getTenantId() == null || document.getPatientId() == null) {
            return;
        }
        persistConcepts(document, structuredJson, ocrText, confidence, sourceSummary, null, "persist-before-save");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClinicalMemoryRepairResult repairPendingConcepts(ClinicalDocumentEntity document, String structuredJson, String sourceText, BigDecimal confidence, String sourceSummary, UUID repairedByAppUserId) {
        if (document == null || document.getTenantId() == null || document.getPatientId() == null) {
            return new ClinicalMemoryRepairResult(null, "FAILED", OffsetDateTime.now(), repairedByAppUserId, 0, 0, 0, List.of(), 0, "Missing tenant or patient context");
        }
        return persistConcepts(document, structuredJson, sourceText, confidence, sourceSummary, repairedByAppUserId, "repair-before-save");
    }

    private ClinicalMemoryRepairResult persistConcepts(ClinicalDocumentEntity document,
                                                       String structuredJson,
                                                       String sourceText,
                                                       BigDecimal confidence,
                                                       String sourceSummary,
                                                       UUID repairedByAppUserId,
                                                       String traceLabel) {
        Map<String, Object> extracted = parseStructuredJson(structuredJson);
        List<Map<String, Object>> parsedRepairFacts = StringUtils.hasText(sourceText)
                ? deterministicLabFactParser.parse(document.getId(), sourceText, null)
                : List.of();
        extracted = ensureFactualLabResults(document, extracted, sourceText, parsedRepairFacts, traceLabel);
        if (repairedByAppUserId != null && !hasFactualLabResults(extracted)) {
            return new ClinicalMemoryRepairResult(
                    document.getId(),
                    "FAILED",
                    OffsetDateTime.now(),
                    repairedByAppUserId,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    "No factual lab rows available for memory repair."
            );
        }
        String repairSourceText = buildRepairSourceText(extracted, sourceText);
        List<MappedConcept> mappedConcepts = mapper.map(document, extracted, repairSourceText, confidence);
        if (log.isInfoEnabled()) {
            log.info("[LONG-MEM-REPAIR-TRACE] documentId={} traceLabel={} mappedConceptKeys={}",
                    document.getId(),
                    traceLabel,
                    summarizeMappedKeys(mappedConcepts));
        }
        List<MappedConcept> persistableConcepts = new ArrayList<>();
        int filteredPollutedCount = 0;
        for (MappedConcept concept : mappedConcepts) {
            if (isPersistableMappedConcept(concept)) {
                persistableConcepts.add(concept);
            } else {
                filteredPollutedCount++;
            }
        }
        if (repairedByAppUserId != null && persistableConcepts.stream().noneMatch(concept -> "LAB_RESULT".equalsIgnoreCase(concept.family()))) {
            return new ClinicalMemoryRepairResult(
                    document.getId(),
                    "FAILED",
                    OffsetDateTime.now(),
                    repairedByAppUserId,
                    0,
                    0,
                    0,
                    List.of(),
                    filteredPollutedCount,
                    "No factual lab rows available for memory repair."
            );
        }
        tracePersistenceFieldLengths(traceLabel, document, sourceSummary, persistableConcepts);
        tracePersistBatch(traceLabel, document, PENDING_REVIEW, persistableConcepts);

        List<PatientLongitudinalConceptEntity> existingConcepts = repository
                .findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(document.getTenantId(), document.getPatientId(), document.getId());
        Map<String, PatientLongitudinalConceptEntity> pendingByKey = new LinkedHashMap<>();
        Map<String, PatientLongitudinalConceptEntity> acceptedByKey = new LinkedHashMap<>();
        for (PatientLongitudinalConceptEntity concept : existingConcepts) {
            String key = conceptKey(concept.getConceptFamily(), concept.getConceptKey(), concept.getObservedAt() == null ? null : concept.getObservedAt().toLocalDate(), concept.getSourceDocumentId());
            if (PENDING_REVIEW.equals(concept.getVerificationStatus())) {
                pendingByKey.putIfAbsent(key, concept);
            } else if (ACCEPTED.equals(concept.getVerificationStatus())) {
                acceptedByKey.putIfAbsent(key, concept);
            }
        }

        int deletedPendingCount = repository.deleteByDocumentAndStatus(document.getTenantId(), document.getPatientId(), document.getId(), PENDING_REVIEW);
        int skippedAcceptedCount = 0;
        List<ClinicalMemoryRepairCorrectedValue> correctedValues = new ArrayList<>();
        List<PatientLongitudinalConceptEntity> toSave = new ArrayList<>();
        for (MappedConcept concept : persistableConcepts) {
            String key = conceptKey(concept.family(), concept.key(), concept.observedOn(), concept.sourceDocumentId());
            if (acceptedByKey.containsKey(key)) {
                skippedAcceptedCount++;
                continue;
            }
            PatientLongitudinalConceptEntity previous = pendingByKey.get(key);
            if (previous != null && !java.util.Objects.equals(previous.getValueText(), concept.valueText())) {
                correctedValues.add(new ClinicalMemoryRepairCorrectedValue(concept.key(), previous.getValueText(), concept.valueText(), concept.valueUnit()));
            }
            toSave.add(toEntity(document, concept, PENDING_REVIEW, repairedByAppUserId, null, null, sourceSummary));
        }
        log.info("[AI-DOC-PIPELINE-TRACE] tenantId={} patientId={} documentId={} deletePendingCount={} insertCount={} hba1cCandidate={} bloodSugarCandidate={} pollutedRejectedCount={}",
                document.getTenantId(),
                document.getPatientId(),
                document.getId(),
                deletedPendingCount,
                toSave.size(),
                summarizeMappedConcept(persistableConcepts, "hba1c"),
                summarizeMappedConcept(persistableConcepts, "blood_sugar"),
                filteredPollutedCount);
        if (log.isInfoEnabled()) {
            log.info("[LONG-MEM-REPAIR-TRACE] documentId={} traceLabel={} insertedConceptKeys={} skippedAcceptedCount={} filteredPollutedCount={}",
                    document.getId(),
                    traceLabel,
                    summarizeConceptKeys(toSave),
                    skippedAcceptedCount,
                    filteredPollutedCount);
        }
        if (!toSave.isEmpty()) {
            repository.saveAllAndFlush(toSave);
        }
        tracePersistedState(document);

        String message = buildRepairMessage(toSave.size(), filteredPollutedCount, correctedValues);
        return new ClinicalMemoryRepairResult(
                document.getId(),
                "SUCCESS",
                OffsetDateTime.now(),
                repairedByAppUserId,
                deletedPendingCount,
                toSave.size(),
                skippedAcceptedCount,
                correctedValues,
                filteredPollutedCount,
                message
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void verifyDocumentConcepts(ClinicalDocumentEntity document,
                                      boolean approved,
                                      UUID reviewerAppUserId,
                                      String acceptedStructuredJson,
                                      String reviewNotes,
                                      String overrideReason) {
        if (document == null || document.getTenantId() == null || document.getPatientId() == null) {
            return;
        }
        List<PatientLongitudinalConceptEntity> pending = repository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(
                document.getTenantId(), document.getPatientId(), document.getId()).stream()
                .filter(concept -> PENDING_REVIEW.equals(concept.getVerificationStatus()))
                .toList();

        if (pending.isEmpty() && approved) {
            Map<String, Object> extracted = parseStructuredJson(StringUtils.hasText(acceptedStructuredJson) ? acceptedStructuredJson : document.getAiExtractionStructuredJson());
            List<MappedConcept> concepts = mapper.map(document, extracted, null, document.getAiExtractionConfidence());
            tracePersistBatch("persist-before-save", document, ACCEPTED, concepts);
            repository.saveAllAndFlush(concepts.stream().map(concept -> toEntity(document, concept, ACCEPTED, reviewerAppUserId, reviewNotes, overrideReason, document.getAiExtractionSummary())).toList());
            tracePersistedState(document);
            return;
        }

        String status = approved ? ACCEPTED : REJECTED;
        pending.forEach(concept -> concept.markVerified(status, reviewerAppUserId, reviewNotes, overrideReason));
        repository.saveAllAndFlush(pending);
        tracePersistedState(document);
    }

    @Transactional(readOnly = true)
    public PatientLongitudinalMemoryProfile buildProfile(UUID tenantId, UUID patientId) {
        List<PatientLongitudinalConceptEntity> rawConcepts = repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId);
        List<PatientLongitudinalConceptEntity> visibleConcepts = rawConcepts.stream()
                .filter(concept -> !REJECTED.equals(concept.getVerificationStatus()))
                .filter(this::isClinicalFactConcept)
                .filter(this::isSelectableConcept)
                .toList();
        List<PatientLongitudinalConceptEntity> dedupedConcepts = dedupeVisibleConcepts(visibleConcepts);
        Map<String, List<PatientLongitudinalConceptEntity>> grouped = dedupedConcepts.stream()
                .collect(Collectors.groupingBy(PatientLongitudinalConceptEntity::getConceptKey, LinkedHashMap::new, Collectors.toList()));

        List<LongitudinalConceptSnapshot> history = dedupedConcepts.stream().map(this::toSnapshot).toList();
        List<LongitudinalConceptSnapshot> conditions = latestByFamily(grouped, "CONDITION");
        List<LongitudinalConceptSnapshot> medications = latestByFamily(grouped, "MEDICATION");
        LongitudinalConceptSnapshot hbA1c = latestByKey(grouped, "hba1c");
        LongitudinalConceptSnapshot bloodSugar = latestByKey(grouped, "blood_sugar");
        List<LongitudinalConceptSnapshot> lipids = latestKeys(grouped, List.of("cholesterol", "hdl", "ldl", "triglycerides"));
        LongitudinalConceptSnapshot bp = latestByKey(grouped, "blood_pressure");
        LongitudinalConceptSnapshot bmi = latestByKey(grouped, "bmi");
        List<LongitudinalConceptSnapshot> riskFlags = latestByFamily(grouped, "RISK_FLAG");
        String labSummary = buildLabSummary(hbA1c, bloodSugar, lipids);

        traceProfileSelection(tenantId, patientId, rawConcepts, visibleConcepts, dedupedConcepts, conditions, hbA1c, bloodSugar, lipids, riskFlags);

        return new PatientLongitudinalMemoryProfile(
                conditions,
                medications,
                hbA1c,
                bloodSugar,
                lipids,
                bp,
                bmi,
                riskFlags,
                history,
                labSummary
        );
    }

    @Transactional(readOnly = true)
    public List<ClinicalContextResponse.TimelineEvent> buildTimelineEvents(UUID tenantId, UUID patientId, int limit) {
        List<PatientLongitudinalConceptEntity> concepts = dedupeVisibleConcepts(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId).stream()
                .filter(concept -> (ACCEPTED.equals(concept.getVerificationStatus()) || PENDING_REVIEW.equals(concept.getVerificationStatus()))
                        && isClinicalFactConcept(concept))
                .toList());
        return concepts.stream()
                .sorted(Comparator.comparing(PatientLongitudinalConceptEntity::getObservedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(0, limit))
                .map(this::toTimelineEvent)
                .toList();
    }

    private LongitudinalConceptSnapshot toSnapshot(PatientLongitudinalConceptEntity concept) {
        return new LongitudinalConceptSnapshot(
                concept.getConceptFamily(),
                concept.getConceptKey(),
                concept.getConceptLabel(),
                concept.getValueText(),
                concept.getValueUnit(),
                concept.getSourceDocumentTitle(),
                concept.getSourceDocumentType(),
                concept.getSourceDocumentId(),
                concept.getSourceDocumentDate(),
                concept.getConfidence(),
                concept.getVerificationStatus(),
                concept.getEvidenceText()
        );
    }

    private ClinicalContextResponse.TimelineEvent toTimelineEvent(PatientLongitudinalConceptEntity concept) {
        String title;
        if (PENDING_REVIEW.equals(concept.getVerificationStatus())) {
            title = "AI extracted " + concept.getConceptLabel();
        } else if (REJECTED.equals(concept.getVerificationStatus())) {
            title = "Doctor rejected " + concept.getConceptLabel();
        } else {
            title = "Doctor verified " + concept.getConceptLabel();
        }
        String detail = buildDetail(concept);
        return new ClinicalContextResponse.TimelineEvent(
                concept.getObservedAt() == null ? null : concept.getObservedAt().toLocalDate().toString(),
                title,
                detail,
                "LONGITUDINAL_MEMORY"
        );
    }

    private String buildDetail(PatientLongitudinalConceptEntity concept) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(concept.getValueText())) {
            parts.add(concept.getValueText() + (StringUtils.hasText(concept.getValueUnit()) ? " " + concept.getValueUnit() : ""));
        }
        if (StringUtils.hasText(concept.getSourceDocumentTitle())) {
            parts.add("Source: " + concept.getSourceDocumentTitle());
        }
        if (concept.getConfidence() != null) {
            parts.add("Confidence: " + concept.getConfidence());
        }
        if (concept.getObservedAt() != null) {
            parts.add("Observed: " + concept.getObservedAt().toLocalDate());
        }
        return String.join(" • ", parts);
    }

    private List<LongitudinalConceptSnapshot> latestByFamily(Map<String, List<PatientLongitudinalConceptEntity>> grouped, String family) {
        return grouped.values().stream()
                .map(concepts -> concepts.stream()
                        .filter(concept -> family.equalsIgnoreCase(concept.getConceptFamily()))
                        .reduce(this::choosePreferredConcept)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toSnapshot)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                snapshot -> snapshot.conceptKey(),
                                snapshot -> snapshot,
                                (a, b) -> chooseLatest(a, b),
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));
    }

    private LongitudinalConceptSnapshot latestByKey(Map<String, List<PatientLongitudinalConceptEntity>> grouped, String key) {
        return grouped.getOrDefault(key, List.of()).stream()
                .reduce(this::choosePreferredConcept)
                .map(this::toSnapshot)
                .orElse(null);
    }

    private List<LongitudinalConceptSnapshot> latestKeys(Map<String, List<PatientLongitudinalConceptEntity>> grouped, List<String> keys) {
        LinkedHashMap<String, LongitudinalConceptSnapshot> snapshots = new LinkedHashMap<>();
        for (String key : keys) {
            LongitudinalConceptSnapshot snapshot = latestByKey(grouped, key);
            if (snapshot != null) {
                snapshots.putIfAbsent(snapshot.conceptKey(), snapshot);
            }
        }
        return new ArrayList<>(snapshots.values());
    }

    private LongitudinalConceptSnapshot chooseLatest(LongitudinalConceptSnapshot left, LongitudinalConceptSnapshot right) {
        if (left == null) return right;
        if (right == null) return left;
        LocalDate leftDate = left.observedOn();
        LocalDate rightDate = right.observedOn();
        if (leftDate == null && rightDate == null) {
            return left;
        }
        if (leftDate == null) {
            return right;
        }
        if (rightDate == null) {
            return left;
        }
        return rightDate.isAfter(leftDate) ? right : left;
    }

    private PatientLongitudinalConceptEntity choosePreferredConcept(PatientLongitudinalConceptEntity left,
                                                                    PatientLongitudinalConceptEntity right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        int leftRank = verificationRank(left.getVerificationStatus());
        int rightRank = verificationRank(right.getVerificationStatus());
        if (leftRank != rightRank) {
            return rightRank > leftRank ? right : left;
        }
        int leftReliability = reliabilityRank(left);
        int rightReliability = reliabilityRank(right);
        if (leftReliability != rightReliability) {
            return rightReliability > leftReliability ? right : left;
        }
        OffsetDateTime leftObserved = left.getObservedAt();
        OffsetDateTime rightObserved = right.getObservedAt();
        if (leftObserved == null && rightObserved != null) {
            return right;
        }
        if (leftObserved != null && rightObserved == null) {
            return left;
        }
        if (leftObserved != null && rightObserved != null && !leftObserved.isEqual(rightObserved)) {
            return rightObserved.isAfter(leftObserved) ? right : left;
        }
        int confidenceRank = compareConfidence(left.getConfidence(), right.getConfidence());
        if (confidenceRank != 0) {
            return confidenceRank > 0 ? left : right;
        }
        if (isQualitativeLipidValue(right) && !isQualitativeLipidValue(left)) {
            return right;
        }
        if (isQualitativeLipidValue(left) && !isQualitativeLipidValue(right)) {
            return left;
        }
        OffsetDateTime leftCreated = left.getCreatedAt();
        OffsetDateTime rightCreated = right.getCreatedAt();
        if (leftCreated == null) {
            return right;
        }
        if (rightCreated == null) {
            return left;
        }
        return rightCreated.isAfter(leftCreated) ? right : left;
    }

    private int compareConfidence(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int verificationRank(String status) {
        if (ACCEPTED.equals(status)) {
            return 2;
        }
        if (PENDING_REVIEW.equals(status)) {
            return 1;
        }
        return 0;
    }

    private boolean isQualitativeLipidValue(PatientLongitudinalConceptEntity concept) {
        if (concept == null || concept.getConceptKey() == null || concept.getValueText() == null) {
            return false;
        }
        if (!List.of("cholesterol", "ldl", "hdl", "triglycerides").contains(concept.getConceptKey())) {
            return false;
        }
        return !concept.getValueText().trim().matches("-?\\d+(?:\\.\\d+)?");
    }

    private List<PatientLongitudinalConceptEntity> dedupeVisibleConcepts(List<PatientLongitudinalConceptEntity> concepts) {
        LinkedHashMap<String, PatientLongitudinalConceptEntity> deduped = new LinkedHashMap<>();
        for (PatientLongitudinalConceptEntity concept : concepts) {
            String key = dedupeKey(concept);
            deduped.merge(key, concept, this::choosePreferredConcept);
        }
        return new ArrayList<>(deduped.values());
    }

    private String dedupeKey(PatientLongitudinalConceptEntity concept) {
        return String.join("|",
                String.valueOf(concept.getTenantId()),
                String.valueOf(concept.getPatientId()),
                String.valueOf(concept.getConceptFamily()),
                String.valueOf(concept.getConceptKey()),
                concept.getObservedAt() == null ? "" : concept.getObservedAt().toLocalDate().toString(),
                String.valueOf(concept.getSourceDocumentId()));
    }

    private boolean isClinicalFactConcept(PatientLongitudinalConceptEntity concept) {
        if (concept == null || !hasText(concept.getConceptFamily())) {
            return false;
        }
        if (!ALLOWED_CONCEPT_FAMILIES.contains(concept.getConceptFamily().toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        if ("CONDITION".equalsIgnoreCase(concept.getConceptFamily())) {
            return isConditionLabel(concept.getConceptLabel(), concept.getValueText());
        }
        return true;
    }

    private boolean isSelectableConcept(PatientLongitudinalConceptEntity concept) {
        if (concept == null) {
            return false;
        }
        if (ACCEPTED.equals(concept.getVerificationStatus())) {
            return true;
        }
        if ("LAB_RESULT".equalsIgnoreCase(concept.getConceptFamily())) {
            return isReliableLabEntity(concept);
        }
        if ("RISK_FLAG".equalsIgnoreCase(concept.getConceptFamily())) {
            return !isNarrativeEvidence(concept.getEvidenceText());
        }
        return true;
    }

    private boolean isConditionLabel(String label, String valueText) {
        String text = firstHasText(label, valueText);
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 80) {
            return false;
        }
        if (normalized.matches(".*\\b(review|consider|discuss|recommend|adjust|monitor|follow\\s*up|follow-up|start|stop|continue|take|please|advice|suggest)\\b.*")) {
            return false;
        }
        if (normalized.contains(".") || normalized.contains("!") || normalized.contains("?") || normalized.contains(":")) {
            return false;
        }
        return normalized.matches(".*\\b(diabetes|hypertension|asthma|copd|kidney disease|ckd|thyroid|hypothyroidism|hyperthyroidism|known diabetic|dm)\\b.*");
    }

    private String firstHasText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String buildLabSummary(LongitudinalConceptSnapshot hbA1c,
                                   LongitudinalConceptSnapshot bloodSugar,
                                   List<LongitudinalConceptSnapshot> lipids) {
        List<String> parts = new ArrayList<>();
        if (hbA1c != null) {
            parts.add("HbA1c " + hbA1c.valueText());
        }
        if (bloodSugar != null) {
            parts.add("Blood Sugar " + bloodSugar.valueText());
        }
        if (!lipids.isEmpty()) {
            parts.add("Lipid profile " + lipids.stream().map(LongitudinalConceptSnapshot::label).distinct().collect(Collectors.joining(", ")));
        }
        return parts.isEmpty() ? null : String.join(" • ", parts);
    }

    private void tracePersistBatch(String label, ClinicalDocumentEntity document, String status, List<MappedConcept> concepts) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("[JEEV-LONG-MEM-TRACE] {} tenantId={} patientId={} consultationId={} documentId={} conceptCount={}",
                label,
                document.getTenantId(),
                document.getPatientId(),
                document.getConsultationId(),
                document.getId(),
                concepts == null ? 0 : concepts.size());
        if (concepts != null) {
            for (MappedConcept concept : concepts) {
                log.info(
                        "[JEEV-LONG-MEM-TRACE] {} tenantId={} patientId={} consultationId={} documentId={} conceptType={} conceptCode={} conceptName={} value={} unit={} observedDate={} verificationStatus={} sourceDocumentId={}",
                        label,
                        document.getTenantId(),
                        document.getPatientId(),
                        document.getConsultationId(),
                        document.getId(),
                        concept.family(),
                        concept.key(),
                        concept.label(),
                        concept.valueText(),
                        concept.valueUnit(),
                        concept.observedOn(),
                        status,
                        document.getId()
                );
            }
        }
    }

    private void tracePersistenceFieldLengths(String label, ClinicalDocumentEntity document, String sourceSummary, List<MappedConcept> concepts) {
        if (!log.isInfoEnabled() || concepts == null || concepts.isEmpty()) {
            return;
        }
        log.info("[JEEV-LONG-MEM-TRACE] {} tenantId={} patientId={} consultationId={} documentId={} sourceDocumentTitleLength={} sourceSummaryLength={} conceptCount={}",
                label,
                document.getTenantId(),
                document.getPatientId(),
                document.getConsultationId(),
                document.getId(),
                lengthOf(document.getTitle()),
                lengthOf(sourceSummary),
                concepts.size());
        for (MappedConcept concept : concepts) {
            log.info(
                    "[JEEV-LONG-MEM-TRACE] {} tenantId={} patientId={} consultationId={} documentId={} sourceDocumentId={} conceptType={} conceptCode={} conceptNameLength={} valueLength={} unitLength={} evidenceLength={} sourceSummaryLength={} verificationStatusLength={}",
                    label,
                    document.getTenantId(),
                    document.getPatientId(),
                    document.getConsultationId(),
                    document.getId(),
                    document.getId(),
                    concept.family(),
                    concept.key(),
                    lengthOf(concept.label()),
                    lengthOf(concept.valueText()),
                    lengthOf(concept.valueUnit()),
                    lengthOf(concept.evidenceText()),
                    lengthOf(concept.sourceSummary()),
                    lengthOf(PENDING_REVIEW)
            );
        }
    }

    private void tracePersistedState(ClinicalDocumentEntity document) {
        if (!log.isInfoEnabled() || document.getTenantId() == null || document.getPatientId() == null || document.getId() == null) {
            return;
        }
        List<PatientLongitudinalConceptEntity> persisted = repository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(
                document.getTenantId(), document.getPatientId(), document.getId());
        log.info("[JEEV-LONG-MEM-TRACE] persist-after-save tenantId={} patientId={} consultationId={} documentId={} conceptCount={}",
                document.getTenantId(),
                document.getPatientId(),
                document.getConsultationId(),
                document.getId(),
                persisted.size());
        log.info("[AI-DOC-PIPELINE-TRACE] tenantId={} patientId={} documentId={} persistedConceptCount={} persistedHba1c={} persistedBloodSugar={}",
                document.getTenantId(),
                document.getPatientId(),
                document.getId(),
                persisted.size(),
                summarizePersistedConcept(persisted, "hba1c"),
                summarizePersistedConcept(persisted, "blood_sugar"));
        for (PatientLongitudinalConceptEntity concept : persisted) {
            log.info(
                    "[JEEV-LONG-MEM-TRACE] persist-after-save tenantId={} patientId={} consultationId={} documentId={} conceptType={} conceptCode={} conceptName={} value={} unit={} observedDate={} verificationStatus={} sourceDocumentId={}",
                    concept.getTenantId(),
                    concept.getPatientId(),
                    document.getConsultationId(),
                    document.getId(),
                    concept.getConceptFamily(),
                    concept.getConceptKey(),
                    concept.getConceptLabel(),
                    concept.getValueText(),
                    concept.getValueUnit(),
                    concept.getObservedAt() == null ? null : concept.getObservedAt().toLocalDate(),
                    concept.getVerificationStatus(),
                    concept.getSourceDocumentId()
            );
        }
    }

    private void traceProfileSelection(UUID tenantId,
                                       UUID patientId,
                                       List<PatientLongitudinalConceptEntity> rawConcepts,
                                       List<PatientLongitudinalConceptEntity> visibleConcepts,
                                       List<PatientLongitudinalConceptEntity> dedupedConcepts,
                                       List<LongitudinalConceptSnapshot> conditions,
                                       LongitudinalConceptSnapshot hbA1c,
                                       LongitudinalConceptSnapshot bloodSugar,
                                       List<LongitudinalConceptSnapshot> lipids,
                                       List<LongitudinalConceptSnapshot> riskFlags) {
        if (!log.isInfoEnabled()) {
            return;
        }
        int filteredRecommendationCount = Math.max(0, (rawConcepts == null ? 0 : rawConcepts.size()) - (visibleConcepts == null ? 0 : visibleConcepts.size()));
        log.info("[JEEV-LONG-MEM-TRACE] profile-build tenantId={} patientId={} conceptCount={} visibleConceptCount={} dedupedConceptCount={} filteredRecommendationCount={} conditionGroupCount={} labGroupCount={} riskFlagCount={}",
                tenantId,
                patientId,
                rawConcepts == null ? 0 : rawConcepts.size(),
                visibleConcepts == null ? 0 : visibleConcepts.size(),
                dedupedConcepts == null ? 0 : dedupedConcepts.size(),
                filteredRecommendationCount,
                conditions == null ? 0 : conditions.size(),
                lipids == null ? 0 : lipids.size(),
                riskFlags == null ? 0 : riskFlags.size());
        log.info("[JEEV-LONG-MEM-TRACE] profile-selected tenantId={} patientId={} latestHbA1c={} latestBloodSugar={} latestLipidSummary={} riskFlags={}",
                tenantId,
                patientId,
                summarizeConcept(hbA1c),
                summarizeConcept(bloodSugar),
                lipids == null ? List.of() : lipids.stream().map(this::summarizeConcept).toList(),
                riskFlags == null ? List.of() : riskFlags.stream().map(this::summarizeConcept).toList());
    }

    private String summarizeConcept(LongitudinalConceptSnapshot concept) {
        if (concept == null) {
            return null;
        }
        return String.format("%s|%s|%s|%s|%s|%s|%s",
                concept.conceptFamily(),
                concept.conceptKey(),
                concept.label(),
                concept.valueText(),
                concept.valueUnit(),
                concept.observedOn(),
                concept.verificationStatus());
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private String buildRepairSourceText(Map<String, Object> extracted, String fallbackText) {
        List<String> evidenceLines = new ArrayList<>();
        Object factualFindings = extracted == null ? null : extracted.get("factualFindings");
        collectRepairEvidenceText(factualFindings, evidenceLines, null);
        if (extracted != null) {
            collectRepairEvidenceText(extracted.get("possibleAbnormalFindings"), evidenceLines, "possibleAbnormalFindings");
            collectRepairEvidenceText(extracted.get("possibleAbnormalities"), evidenceLines, "possibleAbnormalities");
            collectRepairEvidenceText(extracted.get("labResults"), evidenceLines, "labResults");
            collectRepairEvidenceText(extracted.get("labs"), evidenceLines, "labs");
            collectRepairEvidenceText(extracted.get("results"), evidenceLines, "results");
        }
        if (evidenceLines.isEmpty() && StringUtils.hasText(fallbackText)) {
            evidenceLines.add(fallbackText);
        }
        return String.join("\n", new LinkedHashSet<>(evidenceLines));
    }

    @SuppressWarnings("unused")
    private Map<String, Object> ensureFactualLabResults(ClinicalDocumentEntity document,
                                                       Map<String, Object> extracted,
                                                       String sourceText,
                                                       List<Map<String, Object>> parsedRepairFacts,
                                                       String traceLabel) {
        Map<String, Object> normalized = extracted == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extracted);
        Map<String, Object> factualFindings;
        Object existing = normalized.get("factualFindings");
        if (existing instanceof Map<?, ?> map) {
            factualFindings = new LinkedHashMap<>((Map<String, Object>) map);
        } else {
            factualFindings = new LinkedHashMap<>();
        }
        LinkedHashMap<String, Map<String, Object>> mergedLabResults = new LinkedHashMap<>();
        List<String> existingLabKeys = new ArrayList<>();
        Object existingLabResults = factualFindings.get("labResults");
        if (existingLabResults instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> row) {
                    Map<String, Object> normalizedRow = normalizeRepairLabRow(row, "factualFindings.labResults");
                    String key = canonicalLabResultKey(normalizedRow);
                    if (hasText(key)) {
                        existingLabKeys.add(key);
                        mergedLabResults.putIfAbsent(key, normalizedRow);
                    }
                }
            }
        }
        List<String> parsedLabKeys = new ArrayList<>();
        if (parsedRepairFacts != null) {
            for (Map<String, Object> fact : parsedRepairFacts) {
                Map<String, Object> normalizedFact = normalizeRepairLabRow(fact, "deterministic.labResults");
                String key = canonicalLabResultKey(normalizedFact);
                if (hasText(key)) {
                    parsedLabKeys.add(key);
                    mergedLabResults.putIfAbsent(key, normalizedFact);
                }
            }
        }
        if (log.isInfoEnabled()) {
            log.info("[LONG-MEM-REPAIR-TRACE] documentId={} traceLabel={} existingLabKeys={} parsedLabKeys={} mergedLabKeys={}",
                    document == null ? null : document.getId(),
                    traceLabel,
                    existingLabKeys,
                    parsedLabKeys,
                    new ArrayList<>(mergedLabResults.keySet()));
        }
        if (mergedLabResults.isEmpty()) {
            if (existingLabResults != null) {
                factualFindings.put("labResults", existingLabResults);
            }
            normalized.put("factualFindings", factualFindings);
            return normalized;
        }
        factualFindings.put("labResults", new ArrayList<>(mergedLabResults.values()));
        Object existingConditions = factualFindings.get("conditions");
        if (!(existingConditions instanceof List<?>) && StringUtils.hasText(sourceText) && sourceText.toLowerCase(java.util.Locale.ROOT).contains("diabet")) {
            factualFindings.put("conditions", List.of(Map.of(
                    "canonicalKey", "diabetes_mellitus",
                    "label", "Diabetes Mellitus",
                    "evidenceText", "Known diabetic"
            )));
        }
        normalized.put("factualFindings", factualFindings);
        return normalized;
    }

    private void collectRepairEvidenceText(Object value, List<String> target, String key) {
        if (value == null || target == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = entry.getKey() == null ? null : entry.getKey().toString();
                if (isEvidenceKey(childKey)) {
                    collectRepairEvidenceText(entry.getValue(), target, childKey);
                } else if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof Iterable<?>) {
                    collectRepairEvidenceText(entry.getValue(), target, childKey);
                }
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectRepairEvidenceText(item, target, key);
            }
            return;
        }
        String text = normalizeRepairEvidenceText(value.toString());
        if (text.isBlank()) {
            return;
        }
        if (hasText(key) && isEvidenceKey(key)) {
            target.add(text);
            return;
        }
        if (hasText(text) && text.length() > 8 && text.length() < 400) {
            target.add(text);
        }
    }

    private String normalizeRepairEvidenceText(String text) {
        if (!hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        String prefix = "Possible abnormal finding detected:";
        if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return normalized.substring(prefix.length()).trim();
        }
        return normalized;
    }

    private boolean isEvidenceKey(String key) {
        if (!hasText(key)) {
            return false;
        }
        String normalized = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("evidence")
                || normalized.contains("sourcetext")
                || normalized.contains("ocrtext")
                || normalized.contains("rawtext")
                || normalized.contains("finding")
                || normalized.contains("abnormalfinding");
    }

    private boolean isPersistableMappedConcept(MappedConcept concept) {
        if (concept == null || !hasText(concept.family()) || !hasText(concept.key())) {
            return false;
        }
        if (!ALLOWED_CONCEPT_FAMILIES.contains(concept.family().toUpperCase(java.util.Locale.ROOT))) {
            return false;
        }
        if ("CONDITION".equalsIgnoreCase(concept.family())) {
            return isConditionLabel(concept.label(), concept.valueText());
        }
        if ("LAB_RESULT".equalsIgnoreCase(concept.family()) && !isValidLabConcept(concept)) {
            return false;
        }
        return true;
    }

    private boolean hasFactualLabResults(Map<String, Object> extracted) {
        if (extracted == null) {
            return false;
        }
        Object factualFindings = extracted.get("factualFindings");
        if (!(factualFindings instanceof Map<?, ?> factualMap)) {
            return false;
        }
        Object labResults = factualMap.get("labResults");
        if (!(labResults instanceof Iterable<?> iterable)) {
            return false;
        }
        for (Object ignored : iterable) {
            return true;
        }
        return false;
    }

    private List<String> summarizeMappedKeys(List<MappedConcept> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return List.of();
        }
        return concepts.stream()
                .map(MappedConcept::key)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private List<String> summarizeConceptKeys(List<PatientLongitudinalConceptEntity> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return List.of();
        }
        return concepts.stream()
                .map(PatientLongitudinalConceptEntity::getConceptKey)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private boolean isValidLabConcept(MappedConcept concept) {
        BigDecimal numeric = parseNumericValue(concept.valueText());
        String key = concept.key() == null ? "" : concept.key().toLowerCase(java.util.Locale.ROOT);
        String evidence = concept.evidenceText() == null ? "" : concept.evidenceText().toLowerCase(java.util.Locale.ROOT);
        if ("hba1c".equals(key)) {
            if (evidence.contains("hemoglobin") && !evidence.contains("hba1c") && !evidence.contains("a1c") && !evidence.contains("glycated hemoglobin")) {
                return false;
            }
            return numeric == null || (numeric.compareTo(new BigDecimal("2")) >= 0 && numeric.compareTo(new BigDecimal("20")) <= 0);
        }
        if ("blood_sugar".equals(key)) {
            return numeric == null || (numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0);
        }
        if ("cholesterol".equals(key) || "ldl".equals(key) || "hdl".equals(key) || "triglycerides".equals(key)) {
            return numeric == null || (numeric.compareTo(BigDecimal.ZERO) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0);
        }
        return true;
    }

    private boolean isReliableLabEntity(PatientLongitudinalConceptEntity concept) {
        if (concept == null) {
            return false;
        }
        BigDecimal numeric = parseNumericValue(concept.getValueText());
        String key = concept.getConceptKey() == null ? "" : concept.getConceptKey().toLowerCase(java.util.Locale.ROOT);
        String evidence = concept.getEvidenceText() == null ? "" : concept.getEvidenceText().toLowerCase(java.util.Locale.ROOT);
        if (isNarrativeEvidence(evidence)) {
            return false;
        }
        if ("hba1c".equals(key)) {
            if (evidence.contains("hemoglobin") && !evidence.contains("hba1c") && !evidence.contains("a1c") && !evidence.contains("glycated hemoglobin")) {
                return false;
            }
            return numeric != null && numeric.compareTo(new BigDecimal("2")) >= 0 && numeric.compareTo(new BigDecimal("20")) <= 0;
        }
        if ("blood_sugar".equals(key)) {
            return numeric != null && numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0
                    && (evidence.contains("blood sugar") || evidence.contains("glucose") || evidence.contains("rbs"));
        }
        if (java.util.Set.of("cholesterol", "ldl", "hdl", "triglycerides", "estimated_average_glucose", "hemoglobin").contains(key)) {
            return numeric != null && numeric.compareTo(BigDecimal.ZERO) >= 0 && numeric.compareTo(new BigDecimal("2000")) <= 0;
        }
        return true;
    }

    private boolean isNarrativeEvidence(String evidence) {
        if (!hasText(evidence)) {
            return false;
        }
        String normalized = evidence.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("review")
                || normalized.startsWith("discuss")
                || normalized.startsWith("recommend")
                || normalized.startsWith("consider")
                || normalized.startsWith("possible abnormal finding detected");
    }

    private int reliabilityRank(PatientLongitudinalConceptEntity concept) {
        if (concept == null) {
            return 0;
        }
        if ("LAB_RESULT".equalsIgnoreCase(concept.getConceptFamily())) {
            return isReliableLabEntity(concept) ? 2 : 0;
        }
        if ("RISK_FLAG".equalsIgnoreCase(concept.getConceptFamily())) {
            return isNarrativeEvidence(concept.getEvidenceText()) ? 0 : 1;
        }
        return 1;
    }

    private String conceptKey(String family, String key, LocalDate observedOn, UUID sourceDocumentId) {
        return String.join("|",
                String.valueOf(family),
                String.valueOf(key),
                observedOn == null ? "" : observedOn.toString(),
                String.valueOf(sourceDocumentId));
    }

    private String buildRepairMessage(int insertedConceptCount, int filteredPollutedCount, List<ClinicalMemoryRepairCorrectedValue> correctedValues) {
        List<String> parts = new ArrayList<>();
        parts.add("Memory repaired");
        parts.add(insertedConceptCount + " concepts inserted");
        if (filteredPollutedCount > 0) {
            parts.add(filteredPollutedCount + " polluted concepts filtered");
        }
        if (correctedValues != null && !correctedValues.isEmpty()) {
            ClinicalMemoryRepairCorrectedValue corrected = correctedValues.getFirst();
            parts.add("%s corrected %s -> %s".formatted(corrected.conceptKey(), corrected.oldValue(), corrected.newValue()));
        }
        return String.join(", ", parts);
    }

    private String summarizeMappedConcept(List<MappedConcept> concepts, String key) {
        if (concepts == null || key == null) {
            return null;
        }
        return concepts.stream()
                .filter(concept -> key.equalsIgnoreCase(concept.key()))
                .findFirst()
                .map(concept -> "%s|%s|%s".formatted(concept.key(), concept.valueText(), concept.valueUnit()))
                .orElse(null);
    }

    private String summarizePersistedConcept(List<PatientLongitudinalConceptEntity> concepts, String key) {
        if (concepts == null || key == null) {
            return null;
        }
        return concepts.stream()
                .filter(concept -> key.equalsIgnoreCase(concept.getConceptKey()))
                .findFirst()
                .map(concept -> "%s|%s|%s".formatted(concept.getConceptKey(), concept.getValueText(), concept.getValueUnit()))
                .orElse(null);
    }

    private PatientLongitudinalConceptEntity toEntity(ClinicalDocumentEntity document,
                                                      MappedConcept concept,
                                                      String verificationStatus,
                                                      UUID reviewerAppUserId,
                                                      String reviewNotes,
                                                      String overrideReason,
                                                      String sourceSummary) {
        PatientLongitudinalConceptEntity entity = PatientLongitudinalConceptEntity.create(
                document.getTenantId(),
                document.getPatientId(),
                document.getId(),
                concept.sourceDocumentType(),
                concept.sourceDocumentTitle(),
                concept.observedOn(),
                concept.family(),
                concept.key(),
                concept.label(),
                concept.valueText(),
                concept.valueUnit(),
                concept.evidenceText(),
                sourceSummary,
                verificationStatus,
                concept.confidence(),
                concept.observedOn() == null ? null : concept.observedOn().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
        );
        if (!PENDING_REVIEW.equals(verificationStatus)) {
            entity.markVerified(verificationStatus, reviewerAppUserId, reviewNotes, overrideReason);
        }
        return entity;
    }

    private Map<String, Object> parseStructuredJson(String structuredJson) {
        if (!StringUtils.hasText(structuredJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(structuredJson, new TypeReference<>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException ex) {
            return Map.of("raw", structuredJson);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeRepairLabRow(Map<?, ?> row, String sourcePath) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (row == null) {
            return normalized;
        }
        Object rawKey = firstNonNull(row.get("canonicalKey"), row.get("conceptKey"), row.get("key"), row.get("testName"), row.get("label"));
        String canonicalKey = canonicalLabResultKey(rawKey);
        normalized.put("canonicalKey", canonicalKey);
        String testName = firstHasText(
                stringValue(row.get("testName")),
                stringValue(row.get("label")),
                displayRepairLabName(canonicalKey)
        );
        normalized.put("testName", testName);
        normalized.put("value", stringValue(firstNonNull(row.get("value"), row.get("result"), row.get("valueText"))));
        normalized.put("unit", canonicalRepairUnit(stringValue(firstNonNull(row.get("unit"), row.get("valueUnit"))), canonicalKey));
        normalized.put("referenceRange", firstHasText(stringValue(row.get("referenceRange")), stringValue(row.get("range"))));
        normalized.put("flag", firstHasText(stringValue(row.get("flag")), stringValue(row.get("status"))));
        normalized.put("sourcePath", firstHasText(stringValue(row.get("sourcePath")), sourcePath));
        String evidenceText = firstHasText(
                stringValue(row.get("evidenceText")),
                stringValue(row.get("evidence")),
                stringValue(row.get("sourceText")),
                buildRepairLabEvidenceText(testName, stringValue(firstNonNull(row.get("value"), row.get("result"), row.get("valueText"))), stringValue(firstNonNull(row.get("unit"), row.get("valueUnit"))), canonicalKey)
        );
        normalized.put("evidenceText", evidenceText);
        return normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String buildRepairLabEvidenceText(String testName, String value, String unit, String canonicalKey) {
        List<String> parts = new ArrayList<>();
        if (hasText(testName)) {
            parts.add(testName.trim());
        } else {
            parts.add(displayRepairLabName(canonicalKey));
        }
        if (hasText(value)) {
            parts.add(value.trim());
        }
        if (hasText(unit)) {
            parts.add(canonicalRepairUnit(unit, canonicalKey));
        }
        return String.join(" ", parts).trim();
    }

    private String displayRepairLabName(String canonicalKey) {
        if (!hasText(canonicalKey)) {
            return "Lab Result";
        }
        return switch (canonicalKey) {
            case "hba1c" -> "HbA1c";
            case "estimated_average_glucose" -> "Estimated Average Glucose";
            case "blood_sugar" -> "Random Blood Sugar";
            case "cholesterol" -> "Total Cholesterol";
            case "ldl" -> "LDL Cholesterol";
            case "hdl" -> "HDL Cholesterol";
            case "triglycerides" -> "Triglycerides";
            case "hemoglobin" -> "Hemoglobin";
            case "creatinine" -> "Creatinine";
            case "egfr" -> "eGFR";
            case "crp" -> "CRP";
            case "alt" -> "ALT";
            case "ast" -> "AST";
            default -> canonicalKey;
        };
    }

    private String canonicalRepairUnit(String unit, String canonicalKey) {
        if (!hasText(unit)) {
            return switch (canonicalKey) {
                case "hba1c" -> "%";
                case "hemoglobin" -> "g/dL";
                case "egfr" -> "mL/min/1.73m2";
                case "crp" -> "mg/L";
                case "alt", "ast" -> "U/L";
                default -> "mg/dL";
            };
        }
        String normalized = unit.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("m²", "m2")
                .replace("ml/min/1.73m²", "ml/min/1.73m2");
        if (normalized.contains("mg/dl")) return "mg/dL";
        if (normalized.contains("g/dl")) return "g/dL";
        if (normalized.contains("ml/min/1.73m2")) return "mL/min/1.73m2";
        if (normalized.contains("%")) return "%";
        if (normalized.contains("mg/l")) return "mg/L";
        if (normalized.contains("u/l")) return "U/L";
        return unit.trim();
    }

    private String canonicalLabResultKey(Object rawKey) {
        if (rawKey == null) {
            return null;
        }
        String key = rawKey.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return key.isBlank() ? null : key;
    }

    private BigDecimal parseNumericValue(String text) {
        if (!hasText(text)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
