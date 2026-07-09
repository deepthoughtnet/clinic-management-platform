package com.deepthoughtnet.clinic.api.clinicalmemory.mapping;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClinicalConceptMapper {
    private static final Logger log = LoggerFactory.getLogger(ClinicalConceptMapper.class);
    private static final Set<String> CONDITION_KEYS = Set.of("chronicconditions", "conditions", "longtermdiseases", "diagnoses", "diagnosis", "knownconditions");
    private static final Set<String> MEDICATION_KEYS = Set.of("medications", "medicines", "medicine", "drug", "longtermmedications", "currentmedications");
    private static final Set<String> ALLERGY_KEYS = Set.of("allergies", "allergy", "drugallergies");
    private static final Set<String> PROCEDURE_KEYS = Set.of("procedures", "procedure", "surgery", "surgeries");
    private static final Set<String> NARRATIVE_ONLY_KEYS = Set.of(
            "answer",
            "summary",
            "summarytext",
            "aisummary",
            "clinicalsummary",
            "draft",
            "drafttext",
            "response",
            "responsetext",
            "suggestedactions",
            "recommendations",
            "advice",
            "followup",
            "follow_up",
            "followupplan",
            "patientinstructions",
            "patient_instructions",
            "instructions",
            "explanation",
            "assessment",
            "impression",
            "plan",
            "notes"
    );

    public List<MappedConcept> map(ClinicalDocumentEntity document, Map<String, Object> extractedData, String ocrText, BigDecimal confidence) {
        List<MappedConcept> concepts = new ArrayList<>();
        LocalDate observedOn = document.getReportDate();
        String sourceTitle = document.getTitle();
        String sourceType = document.getDocumentType() == null ? null : document.getDocumentType().name();

        if (hasFactualFindings(extractedData)) {
            collectFromFactualFindings(concepts, document, extractedData, observedOn, sourceTitle, sourceType, confidence);
        } else if (extractedData != null) {
            extractedData.forEach((key, value) -> collectFromEntry(concepts, key, value, document, observedOn, sourceTitle, sourceType, confidence));
            collectNonLabTextConcepts(concepts, ocrText, document, observedOn, sourceTitle, sourceType, confidence);
            collectRiskFlags(concepts, ocrText, document, observedOn, sourceTitle, sourceType, confidence);
        }
        collectRiskFlagsFromLabs(concepts, document, observedOn, sourceTitle, sourceType, confidence);

        List<MappedConcept> deduped = dedupe(concepts);
        traceMappedConcepts(document, deduped);
        return deduped;
    }

    @SuppressWarnings("unchecked")
    private void collectFromFactualFindings(List<MappedConcept> concepts,
                                            ClinicalDocumentEntity document,
                                            Map<String, Object> extractedData,
                                            LocalDate observedOn,
                                            String sourceTitle,
                                            String sourceType,
                                            BigDecimal confidence) {
        Object factual = extractedData.get("factualFindings");
        if (!(factual instanceof Map<?, ?> factualMap)) {
            return;
        }
        collectStructuredLabResults(concepts, document, (Object) factualMap.get("labResults"), observedOn, sourceTitle, sourceType, confidence);
        collectStructuredConditions(concepts, document, (Object) factualMap.get("conditions"), observedOn, sourceTitle, sourceType, confidence);
        collectStructuredRiskFlags(concepts, document, (Object) factualMap.get("riskFlags"), observedOn, sourceTitle, sourceType, confidence);
    }

    private void collectStructuredLabResults(List<MappedConcept> concepts,
                                             ClinicalDocumentEntity document,
                                             Object value,
                                             LocalDate observedOn,
                                             String sourceTitle,
                                             String sourceType,
                                             BigDecimal confidence) {
        if (!(value instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> fact)) {
                continue;
            }
            String canonicalKey = canonicalLabKey(normalizeText(firstNonNull(
                    fact.get("canonicalKey"),
                    fact.get("conceptKey"),
                    fact.get("key"),
                    fact.get("testName"),
                    fact.get("label")
            )));
            if (canonicalKey == null) {
                continue;
            }
            String evidenceText = normalizeText(firstNonNull(fact.get("evidenceText"), fact.get("evidence"), fact.get("sourceText")));
            String rawValue = normalizeText(firstNonNull(fact.get("value"), fact.get("result"), fact.get("valueText")));
            log.info("[AI-DOC-PIPELINE-TRACE] stage=MAPPER_INPUT documentId={} conceptKey={} sourcePath={} rawValue={} evidenceText={}",
                    document.getId(),
                    canonicalKey,
                    "factualFindings.labResults",
                    summarizeEvidence(rawValue),
                    summarizeEvidence(evidenceText));
            String valueText = normalizeLabValue(canonicalKey, rawValue, evidenceText);
            String unit = normalizeUnit(canonicalKey, normalizeText(firstNonNull(fact.get("unit"), fact.get("valueUnit"))), evidenceText);
            if (!hasText(valueText) || !isSafeLabEvidenceText(evidenceText, evidenceLabelsFor(canonicalKey)) || !isPlausibleStructuredLabValue(canonicalKey, valueText)) {
                log.info("[AI-DOC-PIPELINE-TRACE] documentId={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                        document.getId(), canonicalKey, valueText, unit, "factualFindings.labResults", summarizeEvidence(evidenceText), false, "INVALID_STRUCTURED_LAB_FACT");
                continue;
            }
            log.info("[AI-DOC-PIPELINE-TRACE] documentId={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                    document.getId(), canonicalKey, valueText, unit, "factualFindings.labResults", summarizeEvidence(evidenceText), true, null);
            concepts.add(concept(
                    document,
                    "LAB_RESULT",
                    canonicalKey,
                    displayLabelForLabKey(canonicalKey),
                    valueText,
                    unit,
                    observedOn,
                    confidence,
                    evidenceText,
                    sourceTitle,
                    sourceType
            ));
        }
    }

    private void collectStructuredConditions(List<MappedConcept> concepts,
                                             ClinicalDocumentEntity document,
                                             Object value,
                                             LocalDate observedOn,
                                             String sourceTitle,
                                             String sourceType,
                                             BigDecimal confidence) {
        if (!(value instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> fact) {
                String label = normalizeText(firstNonNull(fact.get("label"), fact.get("name"), fact.get("value")));
                String canonicalKey = normalizeText(firstNonNull(fact.get("canonicalKey"), fact.get("conceptKey"), fact.get("key")));
                String evidenceText = normalizeText(firstNonNull(fact.get("evidenceText"), fact.get("evidence"), fact.get("sourceText")));
                if (!isClinicalConditionLabel(label)) {
                    continue;
                }
                concepts.add(concept(document, "CONDITION", hasText(canonicalKey) ? normalizeKey(canonicalKey) : conceptKeyForCondition(label), displayLabel(label), label, null, observedOn, confidence, evidenceText, sourceTitle, sourceType));
            } else {
                String label = normalizeText(item);
                if (isClinicalConditionLabel(label)) {
                    concepts.add(concept(document, "CONDITION", conceptKeyForCondition(label), displayLabel(label), label, null, observedOn, confidence, label, sourceTitle, sourceType));
                }
            }
        }
    }

    private void collectStructuredRiskFlags(List<MappedConcept> concepts,
                                            ClinicalDocumentEntity document,
                                            Object value,
                                            LocalDate observedOn,
                                            String sourceTitle,
                                            String sourceType,
                                            BigDecimal confidence) {
        if (!(value instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> fact)) {
                continue;
            }
            String label = normalizeText(firstNonNull(fact.get("label"), fact.get("name"), fact.get("value")));
            String canonicalKey = normalizeText(firstNonNull(fact.get("canonicalKey"), fact.get("conceptKey"), fact.get("key")));
            String evidenceText = normalizeText(firstNonNull(fact.get("evidenceText"), fact.get("evidence"), fact.get("sourceText")));
            if (!hasText(label)) {
                continue;
            }
            String normalizedKey = normalizeRiskFlagKey(canonicalKey, label, evidenceText);
            String normalizedLabel = switch (normalizedKey) {
                case "diabetes_risk" -> "Diabetes";
                case "lipid_risk" -> "Dyslipidemia";
                default -> displayLabel(label);
            };
            concepts.add(concept(document, "RISK_FLAG", normalizedKey, normalizedLabel, normalizedLabel, null, observedOn, confidence, evidenceText, sourceTitle, sourceType));
        }
    }

    private void collectFromEntry(List<MappedConcept> concepts,
                                  String rawKey,
                                  Object value,
                                  ClinicalDocumentEntity document,
                                  LocalDate observedOn,
                                  String sourceTitle,
                                  String sourceType,
                                  BigDecimal confidence) {
        String key = normalizeKey(rawKey);
        if (value == null) {
            return;
        }
        if (isNarrativeOnlyKey(key)) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((nestedKey, nestedValue) -> collectFromEntry(concepts, String.valueOf(nestedKey), nestedValue, document, observedOn, sourceTitle, sourceType, confidence));
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectFromEntry(concepts, rawKey, item, document, observedOn, sourceTitle, sourceType, confidence);
            }
            return;
        }

        String text = normalizeText(value);
        if (text == null) {
            return;
        }

        if (matches(key, CONDITION_KEYS)) {
            if (!isClinicalConditionLabel(text)) {
                return;
            }
            concepts.add(concept(document, "CONDITION", conceptKeyForCondition(text), displayLabel(text), text, null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (matches(key, MEDICATION_KEYS) || containsAny(text, "metformin", "insulin", "amlodipine", "atorvastatin", "losartan", "telmisartan")) {
            concepts.add(concept(document, "MEDICATION", conceptKeyForMedication(text), displayLabel(text), text, null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (matches(key, ALLERGY_KEYS) || containsAny(text, "allergy", "penicillin", "sulfa", "aspirin")) {
            concepts.add(concept(document, "ALLERGY", conceptKeyForAllergy(text), displayLabel(text), text, null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (matches(key, PROCEDURE_KEYS) || containsAny(text, "procedure", "surgery", "appendectomy", "c-section")) {
            concepts.add(concept(document, "PROCEDURE", conceptKeyForProcedure(text), displayLabel(text), text, null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (containsAny(key, "riskflag", "riskflags", "risk", "flags")) {
            if (containsAny(text, "diabetes")) {
                concepts.add(concept(document, "RISK_FLAG", "diabetes_risk", "Diabetes", "Diabetes", null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            }
            if (containsAny(text, "dyslipidemia", "lipid", "cholesterol", "ldl", "hdl", "triglyceride")) {
                concepts.add(concept(document, "RISK_FLAG", "lipid_risk", "Dyslipidemia", "Dyslipidemia", null, observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            }
            return;
        }

        if (containsAny(key, "bloodpressure", "bp", "systolic", "diastolic")) {
            concepts.add(concept(document, "VITAL", "blood_pressure", "Blood Pressure", text, "mmHg", observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (containsAny(key, "bmi")) {
            concepts.add(concept(document, "VITAL", "bmi", "BMI", text, "kg/m2", observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (containsAny(key, "weight", "height", "pulse", "spo2", "temperature", "respiratoryrate")) {
            concepts.add(concept(document, "VITAL", normalizeKey(key), displayLabelFromKey(rawKey), text, inferUnit(text, null), observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
        }
    }

    private void collectNonLabTextConcepts(List<MappedConcept> concepts,
                                           String ocrText,
                                           ClinicalDocumentEntity document,
                                           LocalDate observedOn,
                                           String sourceTitle,
                                           String sourceType,
                                           BigDecimal confidence) {
        if (ocrText == null || ocrText.isBlank()) {
            return;
        }
        String text = ocrText.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "known diabetic", "diabetes mellitus", "type 2 diabetes", "type ii diabetes", "diabetic", "dm")) {
            concepts.add(concept(document, "CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, observedOn, confidence, findEvidenceLine(text, "diabetes"), sourceTitle, sourceType));
        }
        extractBloodPressure(concepts, document, observedOn, sourceTitle, sourceType, confidence, text);
        extractBmi(concepts, document, observedOn, sourceTitle, sourceType, confidence, text);
    }

    private void collectRiskFlags(List<MappedConcept> concepts,
                                  String ocrText,
                                  ClinicalDocumentEntity document,
                                  LocalDate observedOn,
                                  String sourceTitle,
                                  String sourceType,
                                  BigDecimal confidence) {
        if (ocrText == null) {
            return;
        }
        String text = ocrText.toLowerCase(Locale.ROOT);
        if (containsAny(text, "diabetes", "diabetic", "hyperglycemia", "high blood sugar")) {
            concepts.add(concept(document, "RISK_FLAG", "diabetes_risk", "Diabetes", "Diabetes", null, observedOn, confidence, findEvidenceLine(ocrText, "diabetes"), sourceTitle, sourceType));
        }
        if (containsAny(text, "high cholesterol", "high ldl", "high triglycerides", "low hdl")
                || exceedsThreshold(ocrText, "cholesterol", 200, Comparison.GREATER_THAN_OR_EQUAL)
                || exceedsThreshold(ocrText, "ldl", 130, Comparison.GREATER_THAN_OR_EQUAL)
                || exceedsThreshold(ocrText, "triglycerides", 150, Comparison.GREATER_THAN_OR_EQUAL)
                || exceedsThreshold(ocrText, "hdl", 40, Comparison.LESS_THAN)) {
            concepts.add(concept(document, "RISK_FLAG", "lipid_risk", "Dyslipidemia", "Dyslipidemia", null, observedOn, confidence, findEvidenceLine(ocrText, "cholesterol"), sourceTitle, sourceType));
        }
    }

    private void collectRiskFlagsFromLabs(List<MappedConcept> concepts,
                                          ClinicalDocumentEntity document,
                                          LocalDate observedOn,
                                          String sourceTitle,
                                          String sourceType,
                                          BigDecimal confidence) {
        boolean diabetesRisk = concepts.stream()
                .filter(concept -> "LAB_RESULT".equalsIgnoreCase(concept.family()))
                .filter(concept -> "hba1c".equalsIgnoreCase(concept.key()))
                .map(MappedConcept::valueText)
                .map(this::parseNumericValue)
                .filter(value -> value != null && value.compareTo(new BigDecimal("6.5")) >= 0)
                .findFirst()
                .isPresent();
        boolean lipidRisk = concepts.stream()
                .filter(concept -> "LAB_RESULT".equalsIgnoreCase(concept.family()))
                .anyMatch(concept -> {
                    BigDecimal numeric = parseNumericValue(concept.valueText());
                    if (numeric == null) {
                        return false;
                    }
                    return switch (normalizeKey(concept.key())) {
                        case "cholesterol" -> numeric.compareTo(new BigDecimal("200")) >= 0;
                        case "ldl" -> numeric.compareTo(new BigDecimal("100")) >= 0;
                        case "hdl" -> numeric.compareTo(new BigDecimal("40")) < 0;
                        case "triglycerides" -> numeric.compareTo(new BigDecimal("150")) >= 0;
                        default -> false;
                    };
                });
        if (diabetesRisk && concepts.stream().noneMatch(concept -> "RISK_FLAG".equalsIgnoreCase(concept.family()) && "diabetes_risk".equalsIgnoreCase(concept.key()))) {
            concepts.add(concept(document, "RISK_FLAG", "diabetes_risk", "Diabetes", "Diabetes", null, observedOn, confidence, "Derived from abnormal HbA1c", sourceTitle, sourceType));
        }
        if (lipidRisk && concepts.stream().noneMatch(concept -> "RISK_FLAG".equalsIgnoreCase(concept.family()) && "lipid_risk".equalsIgnoreCase(concept.key()))) {
            concepts.add(concept(document, "RISK_FLAG", "lipid_risk", "Dyslipidemia", "Dyslipidemia", null, observedOn, confidence, "Derived from abnormal lipid panel", sourceTitle, sourceType));
        }
    }

    private void extractMetric(List<MappedConcept> concepts,
                               ClinicalDocumentEntity document,
                               LocalDate observedOn,
                               String sourceTitle,
                               String sourceType,
                               BigDecimal confidence,
                               String text,
                               String key,
                               String label,
                               String unit,
                               String... needles) {
        String evidence = findEvidenceLine(text, needles);
        if (evidence != null) {
            String value = extractMetricValue(evidence, needles);
            if (value == null) {
                value = extractNumber(evidence);
            }
            if (value != null) {
                log.info("[AI-DOC-PIPELINE-TRACE] documentId={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                        document.getId(), key, value, unit, "OCR_TEXT", summarizeEvidence(evidence), true, null);
                concepts.add(concept(document, "LAB_RESULT", key, label, value, unit, observedOn, confidence, evidence, sourceTitle, sourceType));
            }
        }
    }

    private void extractBloodPressure(List<MappedConcept> concepts,
                                      ClinicalDocumentEntity document,
                                      LocalDate observedOn,
                                      String sourceTitle,
                                      String sourceType,
                                      BigDecimal confidence,
                                      String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{2,3})\\s*/\\s*(\\d{2,3})\\b").matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1) + "/" + matcher.group(2);
            concepts.add(concept(document, "VITAL", "blood_pressure", "Blood Pressure", value, "mmHg", observedOn, confidence, matcher.group(0), sourceTitle, sourceType));
        }
    }

    private void extractBmi(List<MappedConcept> concepts,
                            ClinicalDocumentEntity document,
                            LocalDate observedOn,
                            String sourceTitle,
                            String sourceType,
                            BigDecimal confidence,
                            String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)\\bbmi\\b[^\\d]{0,12}(\\d+(?:\\.\\d+)?)").matcher(text);
        if (matcher.find()) {
            concepts.add(concept(document, "VITAL", "bmi", "BMI", matcher.group(1), "kg/m2", observedOn, confidence, matcher.group(0), sourceTitle, sourceType));
        }
    }

    private MappedConcept concept(ClinicalDocumentEntity document,
                                  String family,
                                  String key,
                                  String label,
                                  String valueText,
                                  String valueUnit,
                                  LocalDate observedOn,
                                  BigDecimal confidence,
                                  String evidenceText,
                                  String sourceTitle,
                                  String sourceType) {
        return new MappedConcept(
                family,
                key,
                label,
                valueText,
                valueUnit,
                evidenceText,
                confidence,
                observedOn,
                null,
                sourceTitle,
                sourceType,
                document.getId()
        );
    }

    private void traceMappedConcepts(ClinicalDocumentEntity document, List<MappedConcept> concepts) {
        if (!log.isInfoEnabled() || concepts == null || concepts.isEmpty()) {
            return;
        }
        for (MappedConcept concept : concepts) {
            log.info(
                    "[JEEV-LONG-MEM-TRACE] mapped-concept tenantId={} patientId={} consultationId={} documentId={} conceptType={} conceptCode={} conceptName={} value={} unit={} observedDate={} verificationStatus={} sourceDocumentId={}",
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
                    "MAPPED",
                    document.getId()
            );
        }
    }

    private String summarizeEvidence(String evidenceText) {
        if (!hasText(evidenceText)) {
            return null;
        }
        String sanitized = evidenceText.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 220 ? sanitized : sanitized.substring(0, 220);
    }

    private String evidenceText(String key, String text) {
        return key + ": " + text;
    }

    private String findEvidenceLine(String text, String... needles) {
        for (String line : text.split("\\R")) {
            if (containsAny(line, needles)) {
                return line.trim();
            }
        }
        return null;
    }

    private String extractNumber(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractDecimalNumber(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+\\.\\d+)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizedHbA1cValue(String text) {
        String value = extractMetricValue(text, "hba1c", "a1c", "glycated hemoglobin");
        if (value != null && isSafeLabEvidenceText(text, "hba1c", "a1c", "glycated hemoglobin")) {
            return value;
        }
        return null;
    }

    private String extractMetricValue(String text, String... labels) {
        if (text == null || labels == null || labels.length == 0) {
            return null;
        }
        String labelPattern = java.util.Arrays.stream(labels)
                .filter(this::hasText)
                .map(label -> java.util.regex.Pattern.quote(label.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.joining("|"));
        if (labelPattern.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)\\b(?:" + labelPattern + ")\\b[^\\d\\n]{0,24}([<>]?\\s*\\d+(?:\\.\\d+)?)"
        );
        for (String line : text.split("\\R")) {
            if (!isSafeLabEvidenceText(line, labels)) {
                continue;
            }
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String value = matcher.group(1);
                return value == null ? null : value.replaceAll("[<>\\s]", "");
            }
        }
        return null;
    }

    private String normalizedMetricValue(String text, String... labels) {
        return extractMetricValue(text, labels);
    }

    private String structuredMetricLiteral(String text) {
        if (!hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)^[<>]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|mg/dl|mmhg|kg/m2|kg/m²)?\\s*$").matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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

    private boolean isPlausibleStructuredLabValue(String key, String value) {
        BigDecimal numeric = parseNumericValue(value);
        if (numeric == null) {
            return true;
        }
        String normalizedKey = normalizeKey(key);
        return switch (normalizedKey) {
            case "hba1c" -> numeric.compareTo(new BigDecimal("2")) >= 0 && numeric.compareTo(new BigDecimal("20")) <= 0;
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0;
            case "cholesterol", "ldl", "hdl", "triglycerides" -> numeric.compareTo(BigDecimal.ZERO) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0;
            default -> true;
        };
    }

    private String inferUnit(String text, String fallback) {
        if (text == null) {
            return fallback;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("%")) {
            return "%";
        }
        if (lower.contains("mg/dl")) {
            return "mg/dL";
        }
        if (lower.contains("mmhg")) {
            return "mmHg";
        }
        if (lower.contains("kg/m2") || lower.contains("kg/m²")) {
            return "kg/m2";
        }
        return fallback;
    }

    private String displayLabelFromKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "Concept";
        }
        String spaced = rawKey.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private String displayLabel(String text) {
        if (text == null || text.isBlank()) {
            return "Concept";
        }
        String value = text.trim();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String conceptKeyForCondition(String text) {
        String normalized = normalizeText(text);
        if (containsAny(normalized, "diabetes")) return "diabetes_mellitus";
        if (containsAny(normalized, "hypertension")) return "hypertension";
        if (containsAny(normalized, "asthma")) return "asthma";
        if (containsAny(normalized, "copd")) return "copd";
        if (containsAny(normalized, "thyroid")) return "thyroid_disorder";
        return normalizeKey(text);
    }

    private String conceptKeyForMedication(String text) {
        return normalizeKey(text);
    }

    private String conceptKeyForAllergy(String text) {
        return normalizeKey(text);
    }

    private String conceptKeyForProcedure(String text) {
        return normalizeKey(text);
    }

    private String inferLipidConceptKey(String rawKey, String text) {
        String normalizedKey = normalizeKey(rawKey);
        String normalizedText = normalizeText(text);
        if (containsAny(normalizedKey, "ldl") || containsAny(normalizedText, "ldl")) return "ldl";
        if (containsAny(normalizedKey, "hdl") || containsAny(normalizedText, "hdl")) return "hdl";
        if (containsAny(normalizedKey, "triglyceride") || containsAny(normalizedText, "triglyceride")) return "triglycerides";
        if (containsAny(normalizedKey, "cholesterol") || containsAny(normalizedText, "cholesterol")) return "cholesterol";
        return normalizedKey;
    }

    private String displayLabelForLipidKey(String key) {
        return switch (normalizeKey(key)) {
            case "cholesterol" -> "Total Cholesterol";
            case "ldl" -> "LDL Cholesterol";
            case "hdl" -> "HDL Cholesterol";
            case "triglycerides" -> "Triglycerides";
            default -> displayLabelFromKey(key);
        };
    }

    private String displayLabelForLabKey(String key) {
        return switch (normalizeKey(key)) {
            case "hba1c" -> "HbA1c";
            case "estimated_average_glucose" -> "Estimated Average Glucose";
            case "hemoglobin" -> "Hemoglobin";
            case "blood_sugar" -> "Blood Sugar";
            default -> displayLabelForLipidKey(key);
        };
    }

    private String canonicalLabKey(String rawKey) {
        String normalized = normalizeKey(rawKey);
        if (containsAny(normalized, "hba1c", "a1c", "glycated_hemoglobin")) return "hba1c";
        if (containsAny(normalized, "estimated_average_glucose", "eag")) return "estimated_average_glucose";
        if (containsAny(normalized, "blood_sugar", "random_blood_sugar", "glucose", "rbs")) return "blood_sugar";
        if (containsAny(normalized, "hemoglobin")) return "hemoglobin";
        if (containsAny(normalized, "ldl")) return "ldl";
        if (containsAny(normalized, "hdl")) return "hdl";
        if (containsAny(normalized, "triglycerides", "triglyceride")) return "triglycerides";
        if (containsAny(normalized, "total_cholesterol", "cholesterol")) return "cholesterol";
        return null;
    }

    private String[] evidenceLabelsFor(String canonicalKey) {
        return switch (normalizeKey(canonicalKey)) {
            case "hba1c" -> new String[]{"hba1c", "a1c", "glycated hemoglobin"};
            case "estimated_average_glucose" -> new String[]{"estimated average glucose", "average glucose", "eag"};
            case "blood_sugar" -> new String[]{"random blood sugar", "blood sugar", "glucose", "rbs"};
            case "cholesterol" -> new String[]{"total cholesterol", "cholesterol"};
            case "ldl" -> new String[]{"ldl cholesterol", "ldl"};
            case "hdl" -> new String[]{"hdl cholesterol", "hdl"};
            case "triglycerides" -> new String[]{"triglycerides", "triglyceride"};
            case "hemoglobin" -> new String[]{"hemoglobin"};
            default -> new String[]{canonicalKey};
        };
    }

    private String normalizeLabValue(String canonicalKey, String rawValue, String evidenceText) {
        if ("hba1c".equals(normalizeKey(canonicalKey))) {
            return firstNonBlank(normalizedHbA1cValue(evidenceText), structuredMetricLiteral(rawValue), rawValue);
        }
        if ("blood_sugar".equals(normalizeKey(canonicalKey))) {
            return firstNonBlank(normalizedMetricValue(evidenceText, evidenceLabelsFor(canonicalKey)), structuredMetricLiteral(rawValue), rawValue);
        }
        if (List.of("estimated_average_glucose", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin").contains(normalizeKey(canonicalKey))) {
            return firstNonBlank(normalizedMetricValue(evidenceText, evidenceLabelsFor(canonicalKey)), structuredMetricLiteral(rawValue), rawValue);
        }
        return rawValue;
    }

    private String normalizeUnit(String canonicalKey, String unit, String evidenceText) {
        if (hasText(unit)) {
            return inferUnit(unit, unit);
        }
        return switch (normalizeKey(canonicalKey)) {
            case "hba1c" -> inferUnit(evidenceText, "%");
            case "blood_sugar", "estimated_average_glucose", "cholesterol", "ldl", "hdl", "triglycerides" -> inferUnit(evidenceText, "mg/dL");
            case "hemoglobin" -> inferUnit(evidenceText, "g/dL");
            default -> inferUnit(evidenceText, null);
        };
    }

    private String normalizeRiskFlagKey(String canonicalKey, String label, String evidenceText) {
        if (containsAny(firstNonBlank(canonicalKey, ""), "diabetes")) {
            return "diabetes_risk";
        }
        if (containsAny(firstNonBlank(canonicalKey, ""), "lipid", "cholesterol", "ldl", "hdl", "triglyceride")) {
            return "lipid_risk";
        }
        String normalized = firstNonBlank(label, evidenceText);
        if (containsAny(normalized, "diabetes")) {
            return "diabetes_risk";
        }
        if (containsAny(normalized, "dyslipidemia", "lipid", "cholesterol", "ldl", "hdl", "triglyceride")) {
            return "lipid_risk";
        }
        return normalizeKey(label);
    }

    private boolean hasFactualFindings(Map<String, Object> extractedData) {
        return extractedData != null && extractedData.get("factualFindings") instanceof Map<?, ?>;
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

    private List<MappedConcept> dedupe(List<MappedConcept> concepts) {
        LinkedHashMap<String, MappedConcept> deduped = new LinkedHashMap<>();
        for (MappedConcept concept : concepts) {
            String key = String.join("|",
                    Objects.toString(concept.family(), ""),
                    Objects.toString(concept.key(), ""),
                    Objects.toString(concept.observedOn(), ""),
                    Objects.toString(concept.sourceDocumentId(), ""));
            deduped.merge(key, concept, this::choosePreferredConcept);
        }
        return new ArrayList<>(deduped.values());
    }

    private MappedConcept choosePreferredConcept(MappedConcept left, MappedConcept right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        int leftPlausibility = plausibilityScore(left);
        int rightPlausibility = plausibilityScore(right);
        if (leftPlausibility != rightPlausibility) {
            return rightPlausibility > leftPlausibility ? right : left;
        }
        BigDecimal leftConfidence = left.confidence();
        BigDecimal rightConfidence = right.confidence();
        if (leftConfidence == null && rightConfidence != null) {
            return right;
        }
        if (leftConfidence != null && rightConfidence == null) {
            return left;
        }
        if (leftConfidence != null && rightConfidence != null && leftConfidence.compareTo(rightConfidence) != 0) {
            return rightConfidence.compareTo(leftConfidence) > 0 ? right : left;
        }
        if (hasText(right.valueText()) && !hasText(left.valueText())) {
            return right;
        }
        return left;
    }

    private int plausibilityScore(MappedConcept concept) {
        if (concept == null || concept.family() == null || concept.key() == null) {
            return 0;
        }
        if (!"LAB_RESULT".equalsIgnoreCase(concept.family())) {
            return 10;
        }
        BigDecimal numeric = parseNumericValue(concept.valueText());
        if (numeric == null) {
            return 5;
        }
        String key = normalizeKey(concept.key());
        return switch (key) {
            case "hba1c" -> numeric.compareTo(new BigDecimal("2")) >= 0 && numeric.compareTo(new BigDecimal("20")) <= 0 ? 100 : 1;
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0 ? 100 : 1;
            case "cholesterol", "ldl", "hdl", "triglycerides" -> numeric.compareTo(BigDecimal.ZERO) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0 ? 100 : 1;
            default -> 50;
        };
    }

    private boolean matches(String key, Set<String> values) {
        return values.stream().anyMatch(value -> containsAny(key, value));
    }

    private boolean exceedsThreshold(String text, String needle, double threshold, Comparison comparison) {
        String evidence = findEvidenceLine(text, needle);
        if (evidence == null) {
            return false;
        }
        String numeric = extractNumber(evidence);
        if (numeric == null) {
            return false;
        }
        double value = Double.parseDouble(numeric);
        return switch (comparison) {
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN -> value < threshold;
        };
    }

    private enum Comparison {
        GREATER_THAN_OR_EQUAL,
        LESS_THAN
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isNarrativeOnlyKey(String key) {
        return containsAny(key, NARRATIVE_ONLY_KEYS.toArray(String[]::new));
    }

    private boolean isSafeLabEvidenceText(String text, String... labels) {
        if (!hasText(text) || labels == null || labels.length == 0) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "review", "discuss", "recommend", "consider", "monitor", "follow up", "follow-up", "adjust", "advice", "suggest", "summary", "answer", "suggestedactions", "patient instructions", "doctor advice")) {
            return false;
        }
        if (containsAny(normalized, "hemoglobin") && !containsAny(normalized, "hba1c", "a1c", "glycated hemoglobin")) {
            return false;
        }
        boolean matchesLabel = false;
        for (String label : labels) {
            if (label != null && normalized.contains(label.toLowerCase(Locale.ROOT))) {
                matchesLabel = true;
                break;
            }
        }
        return matchesLabel;
    }

    private boolean isClinicalConditionLabel(String text) {
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 80) {
            return false;
        }
        if (normalized.matches(".*\\b(review|consider|discuss|recommend|adjust|monitor|follow\\s*up|follow-up|start|stop|continue|take|please|advice|suggest)\\b.*")) {
            return false;
        }
        if (normalized.contains(".") || normalized.contains("!") || normalized.contains("?") || normalized.contains(":")) {
            return false;
        }
        return containsAny(normalized, "diabetes", "hypertension", "asthma", "copd", "kidney disease", "ckd", "thyroid", "hypothyroidism", "hyperthyroidism", "known diabetic", "dm");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT);
    }

    public record MappedConcept(
            String family,
            String key,
            String label,
            String valueText,
            String valueUnit,
            String evidenceText,
            BigDecimal confidence,
            LocalDate observedOn,
            String sourceSummary,
            String sourceDocumentTitle,
            String sourceDocumentType,
            UUID sourceDocumentId
    ) {
    }
}
