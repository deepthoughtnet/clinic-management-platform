package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeterministicLabFactParser {
    private static final Logger log = LoggerFactory.getLogger(DeterministicLabFactParser.class);

    public List<Map<String, Object>> parse(UUID documentId, String ocrText, List<String> detectedLabLines) {
        List<String> candidateLines = candidateLines(ocrText, detectedLabLines);
        LinkedHashMap<String, Map<String, Object>> results = new LinkedHashMap<>();

        addFact(results, parseLine(documentId, candidateLines, "hba1c", "HbA1c", "HbA1c", "Hb A1c", "Hb A1C", "A1c", "Glycated Hemoglobin", "Glycosylated Hemoglobin"));
        addFact(results, parseLine(documentId, candidateLines, "estimated_average_glucose", "Estimated Average Glucose", "Estimated Average Glucose", "Average Glucose", "EAG"));
        addFact(results, parseLine(documentId, candidateLines, "blood_sugar", "Random Blood Sugar", "Random Blood Sugar", "Blood Sugar", "RBS"));
        addFact(results, parseLine(documentId, candidateLines, "cholesterol", "Total Cholesterol", "Total Cholesterol", "Cholesterol"));
        addFact(results, parseLine(documentId, candidateLines, "ldl", "LDL Cholesterol", "LDL Cholesterol", "LDL"));
        addFact(results, parseLine(documentId, candidateLines, "hdl", "HDL Cholesterol", "HDL Cholesterol", "HDL"));
        addFact(results, parseLine(documentId, candidateLines, "triglycerides", "Triglycerides", "Triglycerides"));
        addFact(results, parseLine(documentId, candidateLines, "hemoglobin", "Hemoglobin", "Hemoglobin"));
        addFact(results, parseLine(documentId, candidateLines, "creatinine", "Creatinine", "Creatinine", "Serum Creatinine"));
        addFact(results, parseLine(documentId, candidateLines, "egfr", "eGFR", "eGFR", "Estimated Glomerular Filtration Rate", "Estimated GFR"));
        addFact(results, parseLine(documentId, candidateLines, "crp", "CRP", "CRP", "C-Reactive Protein", "C Reactive Protein"));
        addFact(results, parseLine(documentId, candidateLines, "alt", "ALT", "ALT", "SGPT", "Alanine Aminotransferase"));
        addFact(results, parseLine(documentId, candidateLines, "ast", "AST", "AST", "SGOT", "Aspartate Aminotransferase"));

        log.info("[AI-LAB-FACT-PARSER] documentId={} labLineCount={} parsedCount={} hba1c={} bloodSugar={} cholesterol={} ldl={} hdl={} triglycerides={}",
                documentId,
                candidateLines.size(),
                results.size(),
                summarize(results.get("hba1c")),
                summarize(results.get("blood_sugar")),
                summarize(results.get("cholesterol")),
                summarize(results.get("ldl")),
                summarize(results.get("hdl")),
                summarize(results.get("triglycerides")));

        return new ArrayList<>(results.values());
    }

    private void addFact(Map<String, Map<String, Object>> results, Map<String, Object> fact) {
        if (fact == null) {
            return;
        }
        String canonicalKey = stringValue(fact.get("canonicalKey"));
        if (hasText(canonicalKey)) {
            results.putIfAbsent(canonicalKey, fact);
        }
    }

    private Map<String, Object> parseLine(UUID documentId,
                                          List<String> candidateLines,
                                          String canonicalKey,
                                          String testName,
                                          String... labels) {
        String line = findMatchingLine(candidateLines, labels);
        if (!hasText(line)) {
            return null;
        }
        if (isNarrativeLine(line)) {
            return null;
        }
        String labelPattern = quotedAlternation(labels);
        Pattern pattern = Pattern.compile("(?i)\\b(?:" + labelPattern + ")\\b\\s*(?:[:=\\-]|\\|)?\\s*([<>]?\\s*\\d+(?:\\.\\d+)?)\\s*(%|mg/dL|g/dL|U/L|mg/L|mL/min/1\\.73m2)?\\s*(.*)$");
        Matcher matcher = pattern.matcher(line.trim());
        if (!matcher.find()) {
            return null;
        }
        String value = normalizeNumeric(matcher.group(1));
        String unit = normalizeUnit(matcher.group(2) == null ? defaultUnit(canonicalKey) : matcher.group(2));
        String tail = matcher.group(3) == null ? "" : matcher.group(3).trim();
        String flag = normalizeFlag(tail, canonicalKey, value);
        String referenceRange = stripFlag(tail, flag);

        if (!isPlausible(canonicalKey, value, line)) {
            log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=DETERMINISTIC_PARSER conceptKey={} proposedValue={} evidenceText={} accepted={} rejectionReason={}",
                    documentId, canonicalKey, value, summarize(line), false, "IMPLAUSIBLE_VALUE");
            return null;
        }

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("testName", testName);
        fact.put("canonicalKey", canonicalKey);
        fact.put("value", value);
        fact.put("unit", unit);
        fact.put("referenceRange", referenceRange);
        fact.put("flag", flag);
        fact.put("evidenceText", line.trim());
        fact.put("sourcePath", "ocr.labLines");
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=DETERMINISTIC_PARSER conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                documentId, canonicalKey, value, unit, "ocr.labLines", summarize(line), true, null);
        return fact;
    }

    private List<String> candidateLines(String ocrText, List<String> detectedLabLines) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        if (detectedLabLines != null) {
            detectedLabLines.stream().filter(this::hasText).map(String::trim).forEach(lines::add);
        }
        if (hasText(ocrText)) {
            for (String line : ocrText.split("\\R")) {
                if (!hasText(line)) {
                    continue;
                }
                String normalized = line.toLowerCase(Locale.ROOT);
                if (containsAny(normalized, "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin", "estimated average glucose", "random blood sugar", "blood sugar", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin", "creatinine", "egfr", "estimated gfr", "c-reactive protein", "crp", "alt", "ast", "sgpt", "sgot", "alanine aminotransferase", "aspartate aminotransferase")) {
                    lines.add(line.trim());
                }
            }
        }
        return new ArrayList<>(lines);
    }

    private String findMatchingLine(List<String> lines, String... labels) {
        if (lines == null || labels == null) {
            return null;
        }
        for (String label : labels) {
            if (!hasText(label)) {
                continue;
            }
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))) {
                    return line;
                }
            }
        }
        return null;
    }

    private boolean isNarrativeLine(String line) {
        if (!hasText(line)) {
            return false;
        }
        String normalized = line.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("review")
                || normalized.startsWith("discuss")
                || normalized.startsWith("recommend")
                || normalized.startsWith("consider")
                || normalized.startsWith("possible abnormal finding detected");
    }

    private boolean isPlausible(String canonicalKey, String value, String line) {
        BigDecimal numeric = parseNumber(value);
        if (numeric == null) {
            return false;
        }
        String normalizedLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        return switch (canonicalKey) {
            case "hba1c" -> normalizedLine.contains("hba1c") || normalizedLine.contains("hb a1c") || normalizedLine.contains("a1c") || normalizedLine.contains("glycated hemoglobin") || normalizedLine.contains("glycosylated hemoglobin");
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0;
            case "estimated_average_glucose", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin", "creatinine", "egfr", "crp", "alt", "ast" ->
                    numeric.compareTo(BigDecimal.ZERO) >= 0 && numeric.compareTo(new BigDecimal("2000")) <= 0;
            default -> true;
        };
    }

    private String normalizeFlag(String tail, String canonicalKey, String value) {
        String normalizedTail = hasText(tail) ? tail.toLowerCase(Locale.ROOT) : "";
        if (normalizedTail.contains(" high")) {
            return "HIGH";
        }
        if (normalizedTail.contains(" low")) {
            return "LOW";
        }
        if (normalizedTail.contains(" normal")) {
            return "NORMAL";
        }
        BigDecimal numeric = parseNumber(value);
        if (numeric == null) {
            return "UNKNOWN";
        }
        return switch (canonicalKey) {
            case "hba1c" -> numeric.compareTo(new BigDecimal("6.5")) >= 0 ? "HIGH" : "UNKNOWN";
            case "estimated_average_glucose" -> numeric.compareTo(new BigDecimal("154")) >= 0 ? "HIGH" : "UNKNOWN";
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("140")) > 0 ? "HIGH" : "UNKNOWN";
            case "cholesterol" -> numeric.compareTo(new BigDecimal("200")) >= 0 ? "HIGH" : "UNKNOWN";
            case "ldl" -> numeric.compareTo(new BigDecimal("100")) >= 0 ? "HIGH" : "UNKNOWN";
            case "hdl" -> numeric.compareTo(new BigDecimal("40")) < 0 ? "LOW" : "UNKNOWN";
            case "triglycerides" -> numeric.compareTo(new BigDecimal("150")) >= 0 ? "HIGH" : "UNKNOWN";
            case "creatinine" -> numeric.compareTo(new BigDecimal("1.3")) > 0 ? "HIGH" : "UNKNOWN";
            case "egfr" -> numeric.compareTo(new BigDecimal("60")) < 0 ? "LOW" : "UNKNOWN";
            case "crp", "alt", "ast" -> "UNKNOWN";
            default -> "UNKNOWN";
        };
    }

    private String stripFlag(String tail, String flag) {
        if (!hasText(tail)) {
            return null;
        }
        String trimmed = tail.trim();
        if (hasText(flag) && trimmed.toUpperCase(Locale.ROOT).endsWith(flag)) {
            trimmed = trimmed.substring(0, trimmed.length() - flag.length()).trim();
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private String defaultUnit(String canonicalKey) {
        return switch (canonicalKey) {
            case "hba1c" -> "%";
            case "hemoglobin" -> "g/dL";
            case "egfr" -> "mL/min/1.73m2";
            case "crp" -> "mg/L";
            case "alt", "ast" -> "U/L";
            default -> "mg/dL";
        };
    }

    private String quotedAlternation(String... labels) {
        List<String> quoted = new ArrayList<>();
        if (labels != null) {
            for (String label : labels) {
                if (hasText(label)) {
                    quoted.add(Pattern.quote(label));
                }
            }
        }
        return String.join("|", quoted);
    }

    private String normalizeNumeric(String value) {
        if (!hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeUnit(String unit) {
        if (!hasText(unit)) {
            return null;
        }
        String normalized = unit.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("mg/dl")) {
            return "mg/dL";
        }
        if (normalized.contains("g/dl")) {
            return "g/dL";
        }
        if (normalized.contains("%")) {
            return "%";
        }
        return unit.trim();
    }

    private BigDecimal parseNumber(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String summarize(Map<String, Object> fact) {
        if (fact == null) {
            return null;
        }
        return stringValue(fact.get("value"));
    }

    private String summarize(String text) {
        if (!hasText(text)) {
            return null;
        }
        String sanitized = text.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 220 ? sanitized : sanitized.substring(0, 220);
    }

    private boolean containsAny(String text, String... needles) {
        if (!hasText(text) || needles == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (hasText(needle) && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
