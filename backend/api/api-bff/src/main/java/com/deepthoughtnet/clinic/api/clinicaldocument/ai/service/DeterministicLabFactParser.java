package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

        addFact(results, parseLine(documentId, candidateLines, "hba1c", "HbA1c", "HbA1c", "Hb A1c", "Hb A1C", "Hemoglobin A1c", "A1c", "Glycated Hemoglobin", "Glycosylated Hemoglobin"));
        addFact(results, parseLine(documentId, candidateLines, "estimated_average_glucose", "Estimated Average Glucose", "Estimated Average Glucose", "Average Glucose", "EAG"));
        addFact(results, parseLine(documentId, candidateLines, "blood_sugar", "Blood Sugar", "Random Blood Sugar", "Fasting Glucose", "Fasting Blood Sugar", "Blood Sugar", "Glucose", "FBS", "RBS"));
        addFact(results, parseLine(documentId, candidateLines, "cholesterol", "Total Cholesterol", "Total Cholesterol", "Cholesterol"));
        addFact(results, parseLine(documentId, candidateLines, "ldl", "LDL Cholesterol", "LDL Cholesterol", "LDL"));
        addFact(results, parseLine(documentId, candidateLines, "hdl", "HDL Cholesterol", "HDL Cholesterol", "HDL"));
        addFact(results, parseLine(documentId, candidateLines, "triglycerides", "Triglycerides", "Triglycerides"));
        addFact(results, parseLine(documentId, candidateLines, "hemoglobin", "Hemoglobin", "Hemoglobin"));
        addFact(results, parseLine(documentId, candidateLines, "rbc", "RBC", "Red Blood Cell Count", "Red Blood Cells Count", "RBC Count", "Red Blood Cells", "RBC"));
        addFact(results, parseLine(documentId, candidateLines, "wbc", "WBC", "White Blood Cell Count", "White Blood Cells Count", "Total WBC Count", "WBC Count", "White Blood Cells", "WBC"));
        addFact(results, parseLine(documentId, candidateLines, "platelets", "Platelets", "Platelets", "Platelet Count"));
        addFact(results, parseLine(documentId, candidateLines, "neutrophils", "Neutrophils", "Neutrophils", "Neutrophil"));
        addFact(results, parseLine(documentId, candidateLines, "lymphocytes", "Lymphocytes", "Lymphocytes", "Lymphocyte"));
        addFact(results, parseLine(documentId, candidateLines, "creatinine", "Creatinine", "Creatinine", "Serum Creatinine"));
        addFact(results, parseLine(documentId, candidateLines, "egfr", "eGFR", "eGFR", "Estimated Glomerular Filtration Rate", "Estimated GFR"));
        addFact(results, parseLine(documentId, candidateLines, "crp", "CRP", "CRP", "C-Reactive Protein", "C Reactive Protein"));
        addFact(results, parseLine(documentId, candidateLines, "alt", "ALT", "ALT", "SGPT", "Alanine Aminotransferase"));
        addFact(results, parseLine(documentId, candidateLines, "ast", "AST", "AST", "SGOT", "Aspartate Aminotransferase"));
        addGenericFacts(documentId, candidateLines, results);

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

    /** Returns stable identities for structurally valid source rows, including unknown analytes. */
    public List<String> detectedLabRowKeys(String ocrText, List<String> detectedLabLines) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String line : candidateLines(ocrText, detectedLabLines)) {
            if (!hasText(line) || isNarrativeLine(line) || looksLikeAdministrativeLabel(line)) {
                continue;
            }
            Matcher matcher = Pattern.compile("^\\s*(.*?)\\s+(?:[<>]?\\s*\\d+(?:\\.\\d+)?)\\b").matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String label = matcher.group(1).replaceAll("[|:=]+$", "").trim();
            if (!hasText(label)) {
                continue;
            }
            String canonicalKey = firstHasText(canonicalKeyForGenericLabel(label), "unmapped_" + slug(label));
            keys.add(canonicalKey);
        }
        return new ArrayList<>(keys);
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
                                          String defaultTestName,
                                          String... labels) {
        String line = findMatchingLine(candidateLines, labels);
        if (!hasText(line)) {
            return null;
        }
        if (isNarrativeLine(line)) {
            return null;
        }
        String matchedLabel = resolveMatchingLabel(line, labels);
        String labelPattern = quotedAlternation(matchedLabel);
        Pattern pattern = Pattern.compile("(?i)\\b(?:" + labelPattern + ")\\b\\s*(?:\\([^)]*\\))?\\s*(?:[:=\\-]|\\|)?\\s*([<>]?\\s*\\d+(?:\\.\\d+)?)\\s*([%A-Za-z0-9/\\.\\^µ²×\\-]+)?\\s*(.*)$");
        Matcher matcher = pattern.matcher(line.trim());
        if (!matcher.find()) {
            return null;
        }
        String value = normalizeNumeric(matcher.group(1));
        String unit = normalizeUnit(matcher.group(2) == null ? defaultUnit(canonicalKey) : matcher.group(2));
        String tail = matcher.group(3) == null ? "" : matcher.group(3).trim();
        String flag = normalizeFlag(tail, canonicalKey, value);
        String referenceRange = stripFlag(tail, flag);
        String testName = resolveDetectedTestName(line, defaultTestName, labels);
        if ("hemoglobin".equals(canonicalKey) && containsAny(line.toLowerCase(Locale.ROOT), "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin")) {
            return null;
        }

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

    private void addGenericFacts(UUID documentId, List<String> candidateLines, Map<String, Map<String, Object>> results) {
        if (candidateLines == null) {
            return;
        }
        for (String line : candidateLines) {
            Map<String, Object> fact = parseGenericLine(documentId, line);
            addFact(results, fact);
        }
    }

    private Map<String, Object> parseGenericLine(UUID documentId, String line) {
        if (!hasText(line) || isNarrativeLine(line) || !looksLikeGenericLabLine(line)) {
            return null;
        }
        String trimmedLine = line.trim();
        String[] tokens = trimmedLine.split("\\s+");
        int valueIndex = -1;
        for (int i = 1; i < tokens.length; i++) {
            if (looksLikeNumericResultToken(tokens[i])) {
                valueIndex = i;
                break;
            }
        }
        if (valueIndex <= 0) {
            return null;
        }
        String testName = String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, valueIndex)).trim();
        if (!hasText(testName) || looksLikeAdministrativeLabel(testName)) {
            return null;
        }
        String rawValueToken = tokens[valueIndex];
        String rawUnitToken = valueIndex + 1 < tokens.length && looksLikeUnitToken(tokens[valueIndex + 1]) ? tokens[valueIndex + 1] : null;
        int tailStartIndex = rawUnitToken == null ? valueIndex + 1 : valueIndex + 2;
        String tail = tailStartIndex >= tokens.length ? "" : String.join(" ", java.util.Arrays.copyOfRange(tokens, tailStartIndex, tokens.length)).trim();
        String canonicalKey = firstHasText(canonicalKeyForGenericLabel(testName), "unmapped_" + slug(testName));
        String value = normalizeNumeric(rawValueToken);
        String unit = normalizeUnit(rawUnitToken);
        String flag = normalizeFlag(tail, canonicalKey, value);
        String referenceRange = stripFlag(tail, flag);
        if ("hemoglobin".equals(canonicalKey) && containsAny(line.toLowerCase(Locale.ROOT), "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin", "hemoglobin a1c")) {
            return null;
        }
        if (!isPlausible(canonicalKey, value, line)) {
            log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=DETERMINISTIC_PARSER conceptKey={} proposedValue={} evidenceText={} accepted={} rejectionReason={}",
                    documentId, canonicalKey, value, summarize(line), false, "IMPLAUSIBLE_VALUE");
            return null;
        }
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("testName", testName);
        fact.put("canonicalKey", canonicalKey);
        fact.put("value", value);
        fact.put("unit", firstHasText(unit, defaultUnit(canonicalKey)));
        fact.put("referenceRange", referenceRange);
        fact.put("flag", flag);
        fact.put("evidenceText", line.trim());
        fact.put("sourcePath", "ocr.labLines");
        fact.put("mapped", !canonicalKey.startsWith("unmapped_"));
        return fact;
    }

    private boolean looksLikeNumericResultToken(String token) {
        return hasText(token) && token.trim().matches("[<>]?\\d+(?:\\.\\d+)?");
    }

    private boolean looksLikeUnitToken(String token) {
        return hasText(token) && token.trim().matches("[%A-Za-z][A-Za-z0-9/\\.\\^µ²×\\-]*");
    }

    private String canonicalKeyForGenericLabel(String label) {
        String canonicalKey = canonicalKeyForLabel(label);
        if ("hemoglobin".equals(canonicalKey) && !"hemoglobin".equals(slug(label))) {
            return null;
        }
        if ("platelets".equals(canonicalKey)
                && !List.of("platelet", "platelets", "platelet_count").contains(slug(label))) {
            return null;
        }
        return canonicalKey;
    }

    private String resolveDetectedTestName(String line, String defaultTestName, String... labels) {
        return firstHasText(resolveMatchingLabel(line, labels), defaultTestName);
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
                if (containsAny(normalized, "hba1c", "hb a1c", "a1c", "hemoglobin a1c", "glycated hemoglobin", "glycosylated hemoglobin", "estimated average glucose", "fasting glucose", "fasting blood sugar", "random blood sugar", "blood sugar", "glucose", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin", "rbc", "rbc count", "red blood cells", "red blood cell count", "wbc", "wbc count", "white blood cells", "white blood cell count", "platelets", "platelet count", "neutrophils", "lymphocytes", "creatinine", "egfr", "estimated gfr", "c-reactive protein", "crp", "alt", "ast", "sgpt", "sgot", "alanine aminotransferase", "aspartate aminotransferase")
                        || looksLikeGenericLabLine(line)) {
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
                if (line.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))
                        && hasNumericResultAfterLabel(line, label)) {
                    return line;
                }
            }
        }
        return null;
    }

    private boolean hasNumericResultAfterLabel(String line, String label) {
        if (!hasText(line) || !hasText(label)) {
            return false;
        }
        Pattern resultPattern = Pattern.compile(
                "(?i)\\b" + Pattern.quote(label.trim()) + "\\b\\s*(?:\\([^)]*\\))?\\s*(?:[:=|\\-])?\\s*[<>]?\\s*\\d+(?:\\.\\d+)?"
        );
        return resultPattern.matcher(line).find();
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

    private boolean looksLikeGenericLabLine(String line) {
        if (!hasText(line)) {
            return false;
        }
        String normalized = line.trim().toLowerCase(Locale.ROOT);
        if (looksLikeAdministrativeLabel(normalized)) {
            return false;
        }
        if (!normalized.matches(".*\\d+(?:\\.\\d+)? .*")) {
            return false;
        }
        return normalized.contains("%")
                || normalized.contains("mg/dl")
                || normalized.contains("g/dl")
                || normalized.contains("mg/l")
                || normalized.contains("u/l")
                || normalized.contains("10^")
                || normalized.contains("normal")
                || normalized.contains("high")
                || normalized.contains("low")
                || normalized.contains("<")
                || normalized.contains(">")
                || normalized.matches(".*\\d+(?:\\.\\d+)?\\s*-\\s*\\d+(?:\\.\\d+)? .*");
    }

    private boolean looksLikeAdministrativeLabel(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("report date")
                || normalized.startsWith("collected")
                || normalized.startsWith("sample")
                || normalized.startsWith("patient")
                || normalized.startsWith("name")
                || normalized.startsWith("age")
                || normalized.startsWith("sex")
                || normalized.startsWith("gender");
    }

    private boolean isPlausible(String canonicalKey, String value, String line) {
        BigDecimal numeric = parseNumber(value);
        if (numeric == null) {
            return false;
        }
        String normalizedLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        return switch (canonicalKey) {
            case "hba1c" -> normalizedLine.contains("hba1c") || normalizedLine.contains("hb a1c") || normalizedLine.contains("a1c") || normalizedLine.contains("hemoglobin a1c") || normalizedLine.contains("glycated hemoglobin") || normalizedLine.contains("glycosylated hemoglobin");
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("20")) >= 0 && numeric.compareTo(new BigDecimal("1000")) <= 0;
            case "estimated_average_glucose", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin", "rbc", "wbc", "platelets", "neutrophils", "lymphocytes", "creatinine", "egfr", "crp", "alt", "ast" ->
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
            case "neutrophils", "lymphocytes", "rbc", "wbc", "platelets" -> "UNKNOWN";
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
            case "rbc" -> "10^6/uL";
            case "wbc", "platelets" -> "10^3/uL";
            case "neutrophils", "lymphocytes" -> "%";
            case "egfr" -> "mL/min/1.73m2";
            case "crp" -> "mg/L";
            case "alt", "ast" -> "U/L";
            default -> null;
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

    private String resolveMatchingLabel(String line, String... labels) {
        if (!hasText(line) || labels == null) {
            return null;
        }
        String normalizedLine = line.toLowerCase(Locale.ROOT);
        String best = null;
        for (String label : labels) {
            if (!hasText(label)) {
                continue;
            }
            if (normalizedLine.contains(label.toLowerCase(Locale.ROOT))
                    && (best == null || label.trim().length() > best.length())) {
                best = label.trim();
            }
        }
        return best;
    }

    private String canonicalKeyForLabel(String label) {
        if (!hasText(label)) {
            return null;
        }
        String normalized = slug(label);
        if (matchesExact(normalized, "hba1c", "hb_a1c", "a1c", "hemoglobin_a1c", "glycated_hemoglobin", "glycosylated_hemoglobin")) return "hba1c";
        if (matchesExact(normalized, "estimated_average_glucose", "eag")) return "estimated_average_glucose";
        if (matchesExact(normalized, "random_blood_sugar", "fasting_blood_sugar", "fasting_glucose", "blood_sugar", "glucose", "fbs", "rbs")) return "blood_sugar";
        if (matchesExact(normalized, "hemoglobin", "hb")) return "hemoglobin";
        if (matchesExact(normalized, "red_blood_cell_count", "red_blood_cells_count", "red_blood_cell", "red_blood_cells", "rbc_count", "rbc")) return "rbc";
        if (matchesExact(normalized, "white_blood_cell_count", "white_blood_cells_count", "white_blood_cell", "white_blood_cells", "total_wbc_count", "wbc_count", "wbc")) return "wbc";
        if (matchesExact(normalized, "platelet", "platelets", "platelet_count")) return "platelets";
        if (matchesExact(normalized, "neutrophil", "neutrophils")) return "neutrophils";
        if (matchesExact(normalized, "lymphocyte", "lymphocytes")) return "lymphocytes";
        if (matchesExact(normalized, "creatinine", "serum_creatinine")) return "creatinine";
        if (matchesExact(normalized, "egfr", "estimated_gfr", "estimated_glomerular_filtration_rate")) return "egfr";
        if (matchesExact(normalized, "crp", "c_reactive_protein")) return "crp";
        if (matchesExact(normalized, "alt", "sgpt", "alanine_aminotransferase", "alt_sgpt")) return "alt";
        if (matchesExact(normalized, "ast", "sgot", "aspartate_aminotransferase", "ast_sgot")) return "ast";
        if (matchesExact(normalized, "ldl_cholesterol", "ldl")) return "ldl";
        if (matchesExact(normalized, "hdl_cholesterol", "hdl")) return "hdl";
        if (matchesExact(normalized, "triglycerides", "triglyceride")) return "triglycerides";
        if (matchesExact(normalized, "total_cholesterol", "cholesterol")) return "cholesterol";
        return null;
    }

    private boolean matchesExact(String value, String... aliases) {
        return value != null && aliases != null && Set.of(aliases).contains(value);
    }

    private String slug(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
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
        String normalized = unit.trim().toLowerCase(Locale.ROOT).replace("m²", "m2");
        if (normalized.contains("mg/dl")) {
            return "mg/dL";
        }
        if (normalized.contains("g/dl")) {
            return "g/dL";
        }
        if (normalized.contains("10^6/ul") || normalized.contains("10^6/µl") || normalized.contains("million/ul")) {
            return "10^6/uL";
        }
        if (normalized.contains("10^3/ul") || normalized.contains("10^3/µl") || normalized.contains("thousand/ul") || normalized.contains("lakh")) {
            return "10^3/uL";
        }
        if (normalized.contains("ml/min/1.73m2")) {
            return "mL/min/1.73m2";
        }
        if (normalized.contains("%")) {
            return "%";
        }
        if (normalized.contains("mg/l")) {
            return "mg/L";
        }
        if (normalized.contains("u/l")) {
            return "U/L";
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
