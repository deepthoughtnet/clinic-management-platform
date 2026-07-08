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

    public List<MappedConcept> map(ClinicalDocumentEntity document, Map<String, Object> extractedData, String ocrText, BigDecimal confidence) {
        List<MappedConcept> concepts = new ArrayList<>();
        LocalDate observedOn = document.getReportDate();
        String sourceTitle = document.getTitle();
        String sourceType = document.getDocumentType() == null ? null : document.getDocumentType().name();

        if (extractedData != null) {
            extractedData.forEach((key, value) -> collectFromEntry(concepts, key, value, document, observedOn, sourceTitle, sourceType, confidence));
        }
        collectFromText(concepts, ocrText, document, observedOn, sourceTitle, sourceType, confidence);
        collectRiskFlags(concepts, ocrText, document, observedOn, sourceTitle, sourceType, confidence);

        List<MappedConcept> deduped = dedupe(concepts);
        traceMappedConcepts(document, deduped);
        return deduped;
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

        if (matches(key, CONDITION_KEYS) || containsAny(text, "diabetes", "hypertension", "asthma", "copd", "kidney disease", "ckd", "thyroid", "hypothyroidism", "hyperthyroidism")) {
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

        if (containsAny(key, "hba1c", "a1c")) {
            concepts.add(concept(document, "LAB_RESULT", "hba1c", "HbA1c", text, inferUnit(text, "%"), observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (containsAny(key, "glucose", "bloodsugar", "rbs", "randombloodsugar")) {
            concepts.add(concept(document, "LAB_RESULT", "blood_sugar", "Blood Sugar", text, inferUnit(text, "mg/dL"), observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
            return;
        }
        if (containsAny(key, "cholesterol", "hdl", "ldl", "triglyceride", "lipid")) {
            String lipidKey = inferLipidConceptKey(key, text);
            concepts.add(concept(document, "LAB_RESULT", lipidKey, displayLabelForLipidKey(lipidKey), text, inferUnit(text, null), observedOn, confidence, evidenceText(key, text), sourceTitle, sourceType));
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

    private void collectFromText(List<MappedConcept> concepts,
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
        if (containsAny(normalized, "known diabetic", "diabetes mellitus", "type 2 diabetes", "type ii diabetes", "dm")) {
            concepts.add(concept(document, "CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, observedOn, confidence, findEvidenceLine(text, "diabetes"), sourceTitle, sourceType));
        }
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "hba1c", "HbA1c", "HBA1C", "%");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "blood_sugar", "Blood Sugar", "random blood sugar", "mg/dL");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "blood_sugar", "Blood Sugar", "rbs", "mg/dL");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "cholesterol", "Total Cholesterol", "cholesterol", "mg/dL");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "ldl", "LDL Cholesterol", "ldl", "mg/dL");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "hdl", "HDL Cholesterol", "hdl", "mg/dL");
        extractMetric(concepts, document, observedOn, sourceTitle, sourceType, confidence, text, "triglycerides", "Triglycerides", "triglycerides", "mg/dL");
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

    private void extractMetric(List<MappedConcept> concepts,
                               ClinicalDocumentEntity document,
                               LocalDate observedOn,
                               String sourceTitle,
                               String sourceType,
                               BigDecimal confidence,
                               String text,
                               String key,
                               String label,
                               String needle,
                               String unit) {
        String evidence = findEvidenceLine(text, needle);
        if (evidence != null) {
            String value = extractNumber(evidence);
            if (value != null) {
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

    private String evidenceText(String key, String text) {
        return key + ": " + text;
    }

    private String findEvidenceLine(String text, String needle) {
        for (String line : text.split("\\R")) {
            if (line.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                return line.trim();
            }
        }
        return null;
    }

    private String extractNumber(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
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
        if (containsAny(normalizedKey, "cholesterol") || containsAny(normalizedText, "cholesterol")) return "cholesterol";
        if (containsAny(normalizedKey, "ldl") || containsAny(normalizedText, "ldl")) return "ldl";
        if (containsAny(normalizedKey, "hdl") || containsAny(normalizedText, "hdl")) return "hdl";
        if (containsAny(normalizedKey, "triglyceride") || containsAny(normalizedText, "triglyceride")) return "triglycerides";
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

    private List<MappedConcept> dedupe(List<MappedConcept> concepts) {
        LinkedHashMap<String, MappedConcept> deduped = new LinkedHashMap<>();
        for (MappedConcept concept : concepts) {
            String key = String.join("|",
                    Objects.toString(concept.family(), ""),
                    Objects.toString(concept.key(), ""),
                    Objects.toString(concept.label(), ""),
                    Objects.toString(concept.valueText(), ""));
            deduped.putIfAbsent(key, concept);
        }
        return new ArrayList<>(deduped.values());
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
