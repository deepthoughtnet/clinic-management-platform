package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
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

    private final PatientLongitudinalConceptRepository repository;
    private final ClinicalConceptMapper mapper;
    private final ObjectMapper objectMapper;

    public PatientLongitudinalMemoryService(PatientLongitudinalConceptRepository repository,
                                            ClinicalConceptMapper mapper,
                                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ingestPendingConcepts(ClinicalDocumentEntity document, String structuredJson, String ocrText, BigDecimal confidence, String sourceSummary) {
        if (document == null || document.getTenantId() == null || document.getPatientId() == null) {
            return;
        }
        Map<String, Object> extracted = parseStructuredJson(structuredJson);
        List<MappedConcept> concepts = mapper.map(document, extracted, ocrText, confidence);
        tracePersistenceFieldLengths("persist-before-save", document, sourceSummary, concepts);
        tracePersistBatch("persist-before-save", document, PENDING_REVIEW, concepts);
        repository.deleteByDocumentAndStatus(document.getTenantId(), document.getPatientId(), document.getId(), PENDING_REVIEW);
        repository.saveAllAndFlush(concepts.stream().map(concept -> toEntity(document, concept, PENDING_REVIEW, null, null, null, sourceSummary)).toList());
        tracePersistedState(document);
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
                .toList();
        Map<String, List<PatientLongitudinalConceptEntity>> grouped = visibleConcepts.stream()
                .collect(Collectors.groupingBy(PatientLongitudinalConceptEntity::getConceptKey, LinkedHashMap::new, Collectors.toList()));

        List<LongitudinalConceptSnapshot> history = visibleConcepts.stream().map(this::toSnapshot).toList();
        List<LongitudinalConceptSnapshot> conditions = latestByFamily(grouped, "CONDITION");
        List<LongitudinalConceptSnapshot> medications = latestByFamily(grouped, "MEDICATION");
        LongitudinalConceptSnapshot hbA1c = latestByKey(grouped, "hba1c");
        LongitudinalConceptSnapshot bloodSugar = latestByKey(grouped, "blood_sugar");
        List<LongitudinalConceptSnapshot> lipids = latestKeys(grouped, List.of("cholesterol", "hdl", "ldl", "triglycerides"));
        LongitudinalConceptSnapshot bp = latestByKey(grouped, "blood_pressure");
        LongitudinalConceptSnapshot bmi = latestByKey(grouped, "bmi");
        List<LongitudinalConceptSnapshot> riskFlags = latestByFamily(grouped, "RISK_FLAG");
        String labSummary = buildLabSummary(hbA1c, bloodSugar, lipids);

        traceProfileSelection(tenantId, patientId, rawConcepts, visibleConcepts, conditions, hbA1c, bloodSugar, lipids, riskFlags);

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
        return repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId).stream()
                .filter(concept -> ACCEPTED.equals(concept.getVerificationStatus()) || PENDING_REVIEW.equals(concept.getVerificationStatus()))
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
        int leftRank = verificationRank(left.getVerificationStatus());
        int rightRank = verificationRank(right.getVerificationStatus());
        if (leftRank != rightRank) {
            return rightRank > leftRank ? right : left;
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
                                       List<LongitudinalConceptSnapshot> conditions,
                                       LongitudinalConceptSnapshot hbA1c,
                                       LongitudinalConceptSnapshot bloodSugar,
                                       List<LongitudinalConceptSnapshot> lipids,
                                       List<LongitudinalConceptSnapshot> riskFlags) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("[JEEV-LONG-MEM-TRACE] profile-build tenantId={} patientId={} conceptCount={} visibleConceptCount={} conditionGroupCount={} labGroupCount={} riskFlagCount={}",
                tenantId,
                patientId,
                rawConcepts == null ? 0 : rawConcepts.size(),
                visibleConcepts == null ? 0 : visibleConcepts.size(),
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
}
