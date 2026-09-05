package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalDocumentFindingReviewService {
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private final PatientLongitudinalConceptRepository conceptRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    public ClinicalDocumentFindingReviewService(PatientLongitudinalConceptRepository conceptRepository,
                                                ClinicalDocumentRepository documentRepository,
                                                ObjectMapper objectMapper) {
        this.conceptRepository = conceptRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
    }

    public List<FindingReviewRecord> list(UUID tenantId, UUID documentId) {
        return list(document(tenantId, documentId));
    }

    public FindingReviewRecord decide(UUID tenantId, UUID documentId, UUID conceptId, String decision, String value,
                                      String unit, String referenceRange, String flag, String reviewNotes, UUID reviewerId) {
        return decide(document(tenantId, documentId), conceptId, decision, value, unit, referenceRange, flag, reviewNotes, reviewerId);
    }

    public ReviewCompletion complete(UUID tenantId, UUID documentId, UUID reviewerId) {
        return complete(document(tenantId, documentId), reviewerId);
    }

    @Transactional(readOnly = true)
    public List<FindingReviewRecord> list(ClinicalDocumentEntity document) {
        Map<String, Map<String, Object>> sourceFacts = sourceFacts(document.getAiExtractionStructuredJson());
        return concepts(document).stream().map(concept -> toRecord(concept, sourceFacts.get(concept.getConceptKey()))).toList();
    }

    @Transactional
    public FindingReviewRecord decide(ClinicalDocumentEntity document,
                                      UUID conceptId,
                                      String decision,
                                      String value,
                                      String unit,
                                      String referenceRange,
                                      String flag,
                                      String reviewNotes,
                                      UUID reviewerId) {
        PatientLongitudinalConceptEntity concept = findPending(document, conceptId);
        String normalizedDecision = normalizeDecision(decision);
        Map<String, Object> sourceFact = sourceFacts(document.getAiExtractionStructuredJson()).get(concept.getConceptKey());
        initializeMetadata(concept, sourceFact);
        if ("REJECTED".equals(normalizedDecision)) {
            concept.applyFindingReview(REJECTED, concept.getValueText(), concept.getValueUnit(), text(sourceFact, "referenceRange", "reference", "range"), text(sourceFact, "flag", "status", "interpretation"), reviewerId, reviewNotes);
        } else {
            String reviewedValue = StringUtils.hasText(value) ? value.trim() : concept.getValueText();
            String reviewedUnit = StringUtils.hasText(unit) ? unit.trim() : concept.getValueUnit();
            concept.applyFindingReview(normalizedDecision, reviewedValue, reviewedUnit, referenceRange, flag, reviewerId, reviewNotes);
        }
        conceptRepository.saveAndFlush(concept);
        return toRecord(concept, sourceFact);
    }

    @Transactional
    public ReviewCompletion complete(ClinicalDocumentEntity document, UUID reviewerId) {
        List<PatientLongitudinalConceptEntity> undecided = concepts(document).stream()
                .filter(concept -> PENDING_REVIEW.equals(concept.getVerificationStatus()))
                .filter(concept -> !StringUtils.hasText(concept.getReviewDecision()))
                .toList();
        if (!undecided.isEmpty()) {
            return new ReviewCompletion(false, undecided.size(), list(document));
        }
        List<PatientLongitudinalConceptEntity> reviewable = concepts(document).stream()
                .filter(concept -> PENDING_REVIEW.equals(concept.getVerificationStatus()))
                .filter(concept -> StringUtils.hasText(concept.getReviewDecision()))
                .toList();
        reviewable.forEach(concept -> concept.finalizeVerificationStatus("REJECTED".equals(normalizeDecision(concept.getReviewDecision())) ? REJECTED : ACCEPTED));
        if (!reviewable.isEmpty()) {
            conceptRepository.saveAllAndFlush(reviewable);
        }
        document.markAiExtractionReviewed(reviewerId, "Finding-level review completed", "APPROVED", document.getAiExtractionStructuredJson(), null);
        documentRepository.save(document);
        return new ReviewCompletion(true, 0, list(document));
    }

    private PatientLongitudinalConceptEntity findPending(ClinicalDocumentEntity document, UUID conceptId) {
        return concepts(document).stream()
                .filter(concept -> concept.getId().equals(conceptId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found for this document"));
    }

    private List<PatientLongitudinalConceptEntity> concepts(ClinicalDocumentEntity document) {
        return conceptRepository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(
                document.getTenantId(), document.getPatientId(), document.getId());
    }

    private ClinicalDocumentEntity document(UUID tenantId, UUID documentId) {
        return documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private FindingReviewRecord toRecord(PatientLongitudinalConceptEntity concept, Map<String, Object> sourceFact) {
        String originalValue = concept.getOriginalValueText() == null ? concept.getValueText() : concept.getOriginalValueText();
        String originalUnit = concept.getOriginalValueUnit() == null ? concept.getValueUnit() : concept.getOriginalValueUnit();
        String originalRange = concept.getOriginalReferenceRange() == null ? text(sourceFact, "referenceRange", "reference", "range") : concept.getOriginalReferenceRange();
        String originalFlag = concept.getOriginalFlag() == null ? text(sourceFact, "flag", "status", "interpretation") : concept.getOriginalFlag();
        String finalRange = concept.getReviewedReferenceRange() == null ? originalRange : concept.getReviewedReferenceRange();
        String finalFlag = concept.getReviewedFlag() == null ? originalFlag : concept.getReviewedFlag();
        return new FindingReviewRecord(
                concept.getId(), concept.getConceptKey(), concept.getConceptLabel(),
                originalValue, originalUnit, originalRange,
                concept.getValueText(), concept.getValueUnit(), finalRange, finalFlag,
                concept.getEvidenceText(), concept.getVerificationStatus(), concept.getReviewDecision(),
                concept.getReviewedByAppUserId(), concept.getReviewedAt());
    }

    private void initializeMetadata(PatientLongitudinalConceptEntity concept, Map<String, Object> sourceFact) {
        if (sourceFact != null) {
            concept.initializeReviewMetadata(
                    text(sourceFact, "referenceRange", "reference", "range"),
                    text(sourceFact, "flag", "status", "interpretation"));
        }
    }

    private String normalizeDecision(String decision) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if (!List.of("CONFIRMED", "EDITED", "REJECTED").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be CONFIRMED, EDITED, or REJECTED");
        }
        return value;
    }

    private Map<String, Map<String, Object>> sourceFacts(String json) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) return result;
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
            Object factual = root.get("factualFindings");
            if (!(factual instanceof Map<?, ?> map) || !(map.get("labResults") instanceof Iterable<?> rows)) return result;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> value)) continue;
                String key = text(value, "canonicalKey", "conceptKey", "key", "testName", "label");
                if (StringUtils.hasText(key)) result.put(normalizeKey(key), castMap(value));
            }
        } catch (Exception ignored) {
            // Review remains usable from persisted concept values if structured JSON is unavailable.
        }
        return result;
    }

    private String text(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value).trim();
        }
        return null;
    }

    private String normalizeKey(String value) {
        return value == null ? null : value.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) (Map<?, ?>) value;
    }

    public record FindingReviewRecord(UUID id, String conceptKey, String findingName,
                                      String originalValue, String originalUnit, String originalReferenceRange,
                                      String value, String unit, String referenceRange, String flag,
                                      String evidenceText, String verificationStatus, String decision,
                                      UUID reviewedBy, java.time.OffsetDateTime reviewedAt) {}

    public record ReviewCompletion(boolean completed, int pendingCount, List<FindingReviewRecord> findings) {}
}
