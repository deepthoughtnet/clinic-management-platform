package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentExtraction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts provider-shaped extraction responses into one provider-independent model. */
public final class ClinicalDocumentExtractionResponseAdapter {
    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentExtractionResponseAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_WRAPPER_DEPTH = 4;

    public ClinicalDocumentExtraction adapt(Map<String, Object> raw, AiDraftResponse response) {
        Map<String, Object> source = raw == null ? Map.of() : raw;
        Resolution resolution = resolve(source);
        List<ClinicalDocumentExtraction.LabResult> labResults = canonicalLabResults(resolution.rows());
        List<ClinicalDocumentExtraction.LabResult> legacyRows = canonicalLabResults(classificationLabResults(source));
        if (!legacyRows.isEmpty()) {
            List<ClinicalDocumentExtraction.LabResult> combined = new ArrayList<>(labResults);
            combined.addAll(legacyRows);
            labResults = combined;
        }
        List<String> narrativeTexts = narrativeTexts(source, response, resolution.hasUsableStructuredRows());
        return new ClinicalDocumentExtraction(
                text(source.get("documentType")), text(source.get("reportDate")), map(source.get("patient")), labResults,
                listValue(firstNonNull(source.get("conditions"), nestedValue(source, List.of("factualFindings", "conditions")), source.get("knownConditions"), source.get("chronicConditions"), source.get("diagnosesMentioned"))),
                listValue(firstNonNull(source.get("riskFlags"), nestedValue(source, List.of("factualFindings", "riskFlags")), source.get("warnings"))),
                firstText(source.get("summary"), response == null ? null : response.draft()), text(source.get("impression")),
                strings(firstNonNull(source.get("recommendations"), source.get("suggestedActions"), source.get("followUpSuggestions")), response == null ? List.of() : response.suggestedActions()),
                strings(firstNonNull(source.get("limitations"), source.get("warnings")), response == null ? List.of() : response.warnings()),
                decimal(source.get("confidence"), response == null ? null : response.confidence()), confidenceLabel(source.get("confidence")),
                providerMetadata(response, resolution), narrativeTexts,
                firstText(source.get("parseStatus"), response == null ? null : response.parseStatus()), resolution.warnings()
        );
    }

    /** Compatibility entry point retained for diagnostics and adapter tests. */
    public Object resolveStructuredLabResults(Map<String, Object> raw) {
        return resolve(raw).rows();
    }

    private Resolution resolve(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return Resolution.empty();
        List<Candidate> candidates = new ArrayList<>();
        collectCandidates(raw, "", 0, 0, candidates);
        if (candidates.isEmpty()) return Resolution.empty();
        Candidate selected = candidates.stream()
                .max(Comparator.comparingInt(Candidate::rowCount)
                        .thenComparingInt(candidate -> completeness(candidate.rows()))
                        .thenComparingInt(candidate -> -candidate.priority()))
                .orElseThrow();
        List<String> warnings = conflictingCandidates(selected, candidates);
        log.info("[AI-DOC-ADAPTER] candidatePaths={} selectedPath={} candidateRowCounts={} selectionReason={}",
                candidates.stream().map(Candidate::path).toList(), selected.path(),
                candidates.stream().collect(java.util.stream.Collectors.toMap(Candidate::path, Candidate::rowCount, (left, right) -> right, LinkedHashMap::new)),
                candidates.size() == 1 ? "only_structurally_valid_candidate" : "highest_valid_row_count_then_completeness_then_preferred_path");
        return new Resolution(selected.rows(), true, warnings, selected.path());
    }

    private void collectCandidates(Object current, String path, int depth, int jsonStringDepth, List<Candidate> candidates) {
        if (depth > MAX_WRAPPER_DEPTH || !(current instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            String nextPath = path.isEmpty() ? key : path + "." + key;
            if ("labresults".equals(key.toLowerCase(Locale.ROOT))) {
                List<Map<?, ?>> rows = usableRows(value);
                if (!rows.isEmpty()) candidates.add(new Candidate(nextPath, rows, depth, pathPriority(nextPath)));
                continue;
            }
            if (depth >= MAX_WRAPPER_DEPTH) continue;
            if (value instanceof Map<?, ?>) {
                collectCandidates(value, nextPath, depth + 1, jsonStringDepth, candidates);
            } else if (value instanceof String json && jsonStringDepth == 0 && looksLikeJson(json)) {
                Object parsed = parseJson(json);
                if (parsed instanceof Map<?, ?>) collectCandidates(parsed, nextPath, depth + 1, 1, candidates);
            }
        }
    }

    private List<Map<?, ?>> usableRows(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<?, ?>> rows = new ArrayList<>();
        for (Object row : iterable) if (row instanceof Map<?, ?> map && hasName(map) && hasValue(map)) rows.add(map);
        return rows;
    }

    private boolean hasName(Map<?, ?> row) { return hasText(text(first(row, "testName", "test", "label", "name", "analyte", "parameter"))); }
    private boolean hasValue(Map<?, ?> row) { return hasText(text(first(row, "value", "result"))); }

    private int completeness(List<Map<?, ?>> rows) {
        return rows.stream().mapToInt(row -> countPresent(row, "unit", "referenceRange", "reference", "range", "flag", "status", "interpretation")).sum();
    }

    private int countPresent(Map<?, ?> row, String... keys) {
        int count = 0;
        for (String key : keys) if (hasText(text(first(row, key)))) count++;
        return count;
    }

    private int pathPriority(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.equals("labresults")) return 0;
        if (normalized.equals("factualfindings.labresults")) return 1;
        if (normalized.equals("answer.labresults")) return 2;
        return 3;
    }

    private List<String> conflictingCandidates(Candidate selected, List<Candidate> candidates) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate == selected) continue;
            for (Map<?, ?> other : candidate.rows()) {
                String otherName = semanticName(first(other, "testName", "test", "label", "name", "analyte", "parameter"));
                String otherValue = text(first(other, "value", "result"));
                for (Map<?, ?> chosen : selected.rows()) {
                    String chosenName = semanticName(first(chosen, "testName", "test", "label", "name", "analyte", "parameter"));
                    String chosenValue = text(first(chosen, "value", "result"));
                    if (hasText(otherName) && otherName.equals(chosenName) && hasText(otherValue) && hasText(chosenValue) && !otherValue.equalsIgnoreCase(chosenValue)) {
                        warnings.add("Conflicting structured lab candidates for " + otherName + ". Review required.");
                    }
                }
            }
        }
        return new ArrayList<>(warnings);
    }

    private String semanticName(Object value) {
        String normalized = text(value);
        if (normalized == null) return null;
        String slug = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (slug.contains("hba1c") || slug.contains("hemoglobina1c") || slug.contains("glycatedhemoglobin") || slug.contains("glycosylatedhemoglobin")) return "hba1c";
        return slug;
    }

    private Object parseJson(String value) {
        try { return OBJECT_MAPPER.readValue(value.trim(), Object.class); }
        catch (Exception ignored) { return null; }
    }

    private boolean looksLikeJson(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.startsWith("{") || normalized.startsWith("[");
    }

    private List<ClinicalDocumentExtraction.LabResult> canonicalLabResults(Object candidate) {
        List<ClinicalDocumentExtraction.LabResult> rows = new ArrayList<>();
        if (candidate instanceof Iterable<?> values) {
            for (Object value : values) if (value instanceof Map<?, ?> row && hasName(row) && hasValue(row)) rows.add(canonicalLabResult(row, null));
        } else if (candidate instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>(); row.put("value", entry.getValue());
                rows.add(canonicalLabResult(row, text(entry.getKey())));
            }
        }
        return rows;
    }

    private ClinicalDocumentExtraction.LabResult canonicalLabResult(Map<?, ?> row, String fallbackName) {
        return new ClinicalDocumentExtraction.LabResult(
                firstText(first(row, "testName", "test", "label", "name", "analyte", "parameter"), fallbackName),
                text(first(row, "result", "value", "valueText")), text(first(row, "unit", "valueUnit")),
                text(first(row, "referenceRange", "reference", "range")),
                firstText(first(row, "flag", "status", "abnormality", "interpretation"))
        );
    }

    private Object classificationLabResults(Map<String, Object> raw) {
        Object answer = raw.get("answer");
        if (!(answer instanceof Map<?, ?> answerMap) || !(answerMap.get("classification") instanceof Map<?, ?> classification)) return null;
        List<Object> rows = new ArrayList<>();
        for (Object value : classification.values()) if (value instanceof Map<?, ?> section && section.get("details") instanceof Iterable<?> details) details.forEach(rows::add);
        return rows;
    }

    private List<String> narrativeTexts(Map<String, Object> raw, AiDraftResponse response, boolean structuredRowsPresent) {
        if (structuredRowsPresent) return List.of();
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        addNarrative(texts, response == null ? null : response.draft()); addNarrative(texts, response == null ? null : response.rawText());
        addNarrative(texts, raw.get("summary")); addNarrative(texts, raw.get("rawText"));
        return new ArrayList<>(texts);
    }

    private void addNarrative(LinkedHashSet<String> target, Object value) {
        if (!(value instanceof String text) || text.trim().length() < 8) return;
        String normalized = text.trim();
        if (looksLikeJson(normalized) || normalized.contains("\"labResults\"") || normalized.contains("\"answer\"")) return;
        target.add(normalized);
    }

    private Map<String, Object> providerMetadata(AiDraftResponse response, Resolution resolution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response != null) {
            metadata.put("provider", response.provider() == null ? "" : response.provider()); metadata.put("model", response.model() == null ? "" : response.model());
            metadata.put("finishReason", response.normalizedFinishReason() == null ? "" : response.normalizedFinishReason()); metadata.put("parseStatus", response.parseStatus() == null ? "" : response.parseStatus());
        }
        if (resolution.selectedPath() != null) {
            metadata.put("selectedStructuredLabPath", resolution.selectedPath());
        }
        return metadata;
    }

    private Object nestedValue(Map<String, Object> source, List<String> path) {
        Object current = source;
        for (String segment : path) { if (!(current instanceof Map<?, ?> map)) return null; current = map.get(segment); }
        return current;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(); map.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue)); return result;
    }

    private List<Object> listValue(Object value) {
        if (value instanceof Iterable<?> iterable) return new ArrayList<>(java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList());
        return value == null ? List.of() : List.of(value);
    }

    private List<String> strings(Object value, List<String> fallback) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) iterable.forEach(item -> { if (text(item) != null) result.add(text(item)); });
        else if (text(value) != null) result.add(text(value));
        if (result.isEmpty() && fallback != null) result.addAll(fallback); return result;
    }

    private String firstText(Object... values) { for (Object value : values) if (text(value) != null) return text(value); return null; }

    private Object first(Map<?, ?> row, String... keys) {
        for (String key : keys) for (Map.Entry<?, ?> entry : row.entrySet()) if (key.equalsIgnoreCase(String.valueOf(entry.getKey())) && entry.getValue() != null) return entry.getValue();
        return null;
    }

    private Object firstNonNull(Object... values) { for (Object value : values) if (value != null) return value; return null; }
    private String text(Object value) { if (value == null) return null; String text = String.valueOf(value).trim(); return text.isEmpty() ? null : text; }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private BigDecimal decimal(Object value, BigDecimal fallback) { if (value instanceof BigDecimal decimal) return decimal; try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; } }
    private String confidenceLabel(Object value) { String text = text(value); if (text == null) return null; try { new BigDecimal(text); return null; } catch (NumberFormatException ignored) { return text.toUpperCase(Locale.ROOT); } }

    private record Candidate(String path, List<Map<?, ?>> rows, int depth, int priority) {
        int rowCount() { return rows.size(); }
    }

    private record Resolution(List<Map<?, ?>> rows, boolean hasUsableStructuredRows, List<String> warnings, String selectedPath) {
        static Resolution empty() { return new Resolution(List.of(), false, List.of(), null); }
    }
}
