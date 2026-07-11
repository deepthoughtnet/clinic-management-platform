package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LongitudinalClinicalContextBuilder {
    private static final Map<String, AnalyteDefinition> ANALYTES = Map.ofEntries(
            Map.entry("hba1c", new AnalyteDefinition("hba1c", "HbA1c", Set.of("hba1c", "hb a1c", "a1c", "glycated hemoglobin"), "%", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("blood_sugar", new AnalyteDefinition("blood_sugar", "Blood Sugar", Set.of("blood sugar", "random blood sugar", "glucose", "rbs", "fbs"), "mg/dL", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("creatinine", new AnalyteDefinition("creatinine", "Creatinine", Set.of("creatinine", "serum creatinine"), "mg/dL", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("egfr", new AnalyteDefinition("egfr", "eGFR", Set.of("egfr", "e gfr", "estimated glomerular filtration rate", "estimated gfr"), "mL/min/1.73m2", TrendSemantics.WORSE_WHEN_DECREASING)),
            Map.entry("cholesterol", new AnalyteDefinition("cholesterol", "Total Cholesterol", Set.of("total cholesterol", "cholesterol"), "mg/dL", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("ldl", new AnalyteDefinition("ldl", "LDL Cholesterol", Set.of("ldl", "ldl cholesterol"), "mg/dL", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("hdl", new AnalyteDefinition("hdl", "HDL Cholesterol", Set.of("hdl", "hdl cholesterol"), "mg/dL", TrendSemantics.WORSE_WHEN_DECREASING)),
            Map.entry("triglycerides", new AnalyteDefinition("triglycerides", "Triglycerides", Set.of("triglycerides", "triglyceride"), "mg/dL", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("hemoglobin", new AnalyteDefinition("hemoglobin", "Hemoglobin", Set.of("hemoglobin"), "g/dL", TrendSemantics.INDETERMINATE)),
            Map.entry("crp", new AnalyteDefinition("crp", "CRP", Set.of("crp", "c reactive protein", "c-reactive protein"), "mg/L", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("alt", new AnalyteDefinition("alt", "ALT", Set.of("alt", "alanine aminotransferase", "sgpt"), "U/L", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("ast", new AnalyteDefinition("ast", "AST", Set.of("ast", "aspartate aminotransferase", "sgot"), "U/L", TrendSemantics.WORSE_WHEN_INCREASING)),
            Map.entry("weight", new AnalyteDefinition("weight", "Weight", Set.of("weight"), "kg", TrendSemantics.INDETERMINATE)),
            Map.entry("bmi", new AnalyteDefinition("bmi", "BMI", Set.of("bmi", "body mass index"), "kg/m2", TrendSemantics.INDETERMINATE))
    );
    private static final Set<String> IMAGING_ALIASES = Set.of("x ray", "xray", "x-ray", "cxr", "ct", "mri", "ultrasound", "usg", "mammography", "echocardiography");
    private static final Set<String> CHEST_ALIASES = Set.of("chest", "cxr", "lung", "pulmonary", "pneumonia", "bronchitic");
    private final ObjectMapper objectMapper;

    LongitudinalClinicalContextBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ClinicalContextResponse.LongitudinalClinicalContext build(PatientLongitudinalMemoryProfile profile,
                                                              List<ClinicalDocumentEntity> documents) {
        List<String> warnings = new ArrayList<>();
        CollectionResult observations = collectObservations(profile);
        if (observations.duplicateCount() > 0) {
            warnings.add("Multiple duplicate reprocessed observations were consolidated.");
        }

        List<ClinicalContextResponse.LabTrend> labTrends = buildLabTrends(observations.observations(), warnings);
        List<ClinicalContextResponse.ImagingHistoryItem> imagingHistory = buildImagingHistory(documents, warnings);
        ClinicalContextResponse.RenalContext renalContext = buildRenalContext(observations.observations(), documents, warnings);
        List<ClinicalContextResponse.HistoricalFinding> findings = buildHistoricalFindings(labTrends, imagingHistory, renalContext);

        return new ClinicalContextResponse.LongitudinalClinicalContext(
                labTrends,
                imagingHistory,
                renalContext,
                findings,
                warnings
        );
    }

    private CollectionResult collectObservations(PatientLongitudinalMemoryProfile profile) {
        if (profile == null || profile.history() == null || profile.history().isEmpty()) {
            return new CollectionResult(List.of(), 0);
        }
        List<Observation> raw = new ArrayList<>();
        for (LongitudinalConceptSnapshot snapshot : profile.history()) {
            Observation observation = toObservation(snapshot);
            if (observation != null) {
                raw.add(observation);
            }
        }
        LinkedHashMap<String, Observation> deduped = new LinkedHashMap<>();
        for (Observation observation : raw) {
            deduped.merge(dedupeKey(observation), observation, this::choosePreferredObservation);
        }
        return new CollectionResult(new ArrayList<>(deduped.values()), Math.max(0, raw.size() - deduped.size()));
    }

    private Observation toObservation(LongitudinalConceptSnapshot snapshot) {
        if (snapshot == null || snapshot.observedOn() == null) {
            return null;
        }
        String analyteKey = normalizeAnalyteKey(snapshot.conceptKey(), snapshot.label(), snapshot.evidenceText());
        if (!hasText(analyteKey)) {
            return null;
        }
        AnalyteDefinition definition = ANALYTES.get(analyteKey);
        String unit = canonicalUnit(analyteKey, snapshot.valueUnit(), snapshot.evidenceText());
        Double numericValue = parseNumericValue(snapshot.valueText());
        if (numericValue == null) {
            return null;
        }
        if (!isCompatibleUnit(definition, unit)) {
            return null;
        }
        return new Observation(
                analyteKey,
                definition.displayName(),
                numericValue,
                formatNumeric(numericValue),
                unit,
                snapshot.observedOn(),
                normalizeVerificationStatus(snapshot.verificationStatus()),
                snapshot.confidence(),
                snapshot.sourceDocumentId(),
                snapshot.sourceDocumentTitle(),
                snapshot.sourceDocumentType(),
                snapshot.evidenceText()
        );
    }

    private String dedupeKey(Observation observation) {
        return String.join("|",
                observation.analyteKey(),
                observation.observedOn().toString(),
                formatNumeric(observation.numericValue()),
                firstNonBlank(observation.unit(), ""),
                firstNonBlank(normalizeSourceIdentity(observation.sourceDocumentId(), observation.sourceDocumentTitle(), observation.sourceDocumentType()), ""));
    }

    private Observation choosePreferredObservation(Observation left, Observation right) {
        if (left == null) return right;
        if (right == null) return left;
        int leftVerification = verificationRank(left.verificationStatus());
        int rightVerification = verificationRank(right.verificationStatus());
        if (leftVerification != rightVerification) {
            return rightVerification > leftVerification ? right : left;
        }
        int leftConfidence = confidenceRank(left.confidence());
        int rightConfidence = confidenceRank(right.confidence());
        if (leftConfidence != rightConfidence) {
            return rightConfidence > leftConfidence ? right : left;
        }
        int leftTitleScore = canonicalTitleScore(left.sourceDocumentTitle());
        int rightTitleScore = canonicalTitleScore(right.sourceDocumentTitle());
        if (leftTitleScore != rightTitleScore) {
            return rightTitleScore > leftTitleScore ? right : left;
        }
        return left;
    }

    private List<ClinicalContextResponse.LabTrend> buildLabTrends(List<Observation> observations, List<String> warnings) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        Map<String, List<Observation>> grouped = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (observation == null || !isReliableObservation(observation)) {
                continue;
            }
            grouped.computeIfAbsent(observation.analyteKey(), ignored -> new ArrayList<>()).add(observation);
        }

        List<ClinicalContextResponse.LabTrend> trends = new ArrayList<>();
        for (AnalyteDefinition definition : ANALYTES.values().stream().sorted(Comparator.comparing(AnalyteDefinition::displayName)).toList()) {
            List<Observation> analyteObservations = grouped.getOrDefault(definition.key(), List.of()).stream()
                    .sorted(Comparator.comparing(Observation::observedOn))
                    .toList();
            if (analyteObservations.isEmpty()) {
                continue;
            }
            List<Observation> uniqueDatedObservations = collapseSameDateObservations(analyteObservations);
            if (uniqueDatedObservations.size() < 2) {
                if ("hba1c".equals(definition.key())) {
                    warnings.add("HbA1c trend could not be confirmed because only one reliable report date was available.");
                }
                continue;
            }
            Observation older = uniqueDatedObservations.get(uniqueDatedObservations.size() - 2);
            Observation newer = uniqueDatedObservations.get(uniqueDatedObservations.size() - 1);
            if (!newer.observedOn().isAfter(older.observedOn())) {
                if ("hba1c".equals(definition.key())) {
                    warnings.add("HbA1c trend not generated because the newer value predates the older value.");
                }
                continue;
            }

            double change = newer.numericValue() - older.numericValue();
            String direction = trendDirection(definition, change);
            String interpretation = clinicalInterpretation(definition, direction);
            trends.add(new ClinicalContextResponse.LabTrend(
                    definition.key(),
                    definition.displayName(),
                    older.valueText(),
                    older.unit(),
                    older.observedOn().toString(),
                    newer.valueText(),
                    newer.unit(),
                    newer.observedOn().toString(),
                    direction,
                    interpretation,
                    absoluteChange(definition.key(), change),
                    approximateInterval(older.observedOn(), newer.observedOn()),
                    sourceDocumentIds(older, newer),
                    mergeVerificationStatus(older.verificationStatus(), newer.verificationStatus())
            ));
        }
        return trends;
    }

    private List<Observation> collapseSameDateObservations(List<Observation> observations) {
        LinkedHashMap<LocalDate, Observation> deduped = new LinkedHashMap<>();
        for (Observation observation : observations) {
            deduped.merge(observation.observedOn(), observation, this::choosePreferredObservation);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<ClinicalContextResponse.ImagingHistoryItem> buildImagingHistory(List<ClinicalDocumentEntity> documents, List<String> warnings) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<ClinicalContextResponse.ImagingHistoryItem> items = new ArrayList<>();
        boolean sawChestImaging = false;
        for (ClinicalDocumentEntity document : documents.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(this::documentObservedOn, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList()) {
            if (!isImagingDocument(document)) {
                continue;
            }
            if (isChestImagingDocument(document)) {
                sawChestImaging = true;
            }
            String summary = extractDocumentSummary(document);
            if (!hasText(summary)) {
                continue;
            }
            items.add(new ClinicalContextResponse.ImagingHistoryItem(
                    detectImagingModality(document),
                    detectBodyPart(document, summary),
                    documentObservedOn(document) == null ? null : documentObservedOn(document).toString(),
                    summary,
                    extractNegativeFindings(summary),
                    normalizeDocumentVerificationStatus(document.getVerificationStatus(), document.getAiExtractionStatus()),
                    document.getId() == null ? null : document.getId().toString(),
                    compactText(firstNonBlank(document.getTitle(), document.getDescription(), detectImagingModality(document)), 120)
            ));
        }
        if (sawChestImaging && items.stream().noneMatch(item -> containsAny(item.bodyPart(), "chest") || containsAny(item.summary(), "bronchitic", "pneumonia", "consolidation"))) {
            warnings.add("Chest X-ray document exists but no usable report summary was available.");
        }
        return items.stream().distinct().limit(3).toList();
    }

    private ClinicalContextResponse.RenalContext buildRenalContext(List<Observation> observations,
                                                                   List<ClinicalDocumentEntity> documents,
                                                                   List<String> warnings) {
        List<Observation> reliableObservations = observations == null ? List.of() : observations.stream()
                .filter(this::isReliableObservation)
                .toList();
        Observation creatinine = latestObservation(reliableObservations, "creatinine");
        Observation egfr = latestObservation(reliableObservations, "egfr");
        if (creatinine == null && egfr == null) {
            if (hasRenalDocument(documents)) {
                warnings.add("Kidney-function report exists but creatinine/eGFR values were not extracted.");
            }
            return null;
        }
        LocalDate referenceDate = latestDate(creatinine == null ? null : creatinine.observedOn(), egfr == null ? null : egfr.observedOn());
        String interpretation = buildRenalInterpretation(creatinine, egfr);
        return new ClinicalContextResponse.RenalContext(
                creatinine == null ? null : appendUnit(creatinine.valueText(), creatinine.unit()),
                creatinine == null ? null : creatinine.observedOn().toString(),
                egfr == null ? null : appendUnit(egfr.valueText(), egfr.unit()),
                egfr == null ? null : egfr.observedOn().toString(),
                interpretation,
                referenceDate == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(referenceDate, LocalDate.now())),
                mergeVerificationStatus(creatinine == null ? null : creatinine.verificationStatus(), egfr == null ? null : egfr.verificationStatus()),
                sourceDocumentIds(creatinine, egfr)
        );
    }

    private List<ClinicalContextResponse.HistoricalFinding> buildHistoricalFindings(List<ClinicalContextResponse.LabTrend> labTrends,
                                                                                    List<ClinicalContextResponse.ImagingHistoryItem> imagingHistory,
                                                                                    ClinicalContextResponse.RenalContext renalContext) {
        List<ClinicalContextResponse.HistoricalFinding> findings = new ArrayList<>();
        if (labTrends != null) {
            for (ClinicalContextResponse.LabTrend trend : labTrends) {
                ClinicalContextResponse.HistoricalFinding finding = toTrendFinding(trend);
                if (finding != null) {
                    findings.add(finding);
                }
            }
        }
        if (imagingHistory != null && !imagingHistory.isEmpty()) {
            ClinicalContextResponse.ImagingHistoryItem item = imagingHistory.getFirst();
            findings.add(new ClinicalContextResponse.HistoricalFinding(
                    "IMAGING_HISTORY",
                    containsAny(item.bodyPart(), "chest") ? "Previous chest imaging" : "Previous imaging",
                    firstNonBlank(
                            joinSegments(
                                    detectImagingModality(item.sourceReference()) + " on " + formatHumanDate(parseLocalDate(item.reportDate())),
                                    "showed " + item.summary()
                            ),
                            item.summary()
                    ),
                    containsAny(item.bodyPart(), "chest")
                            ? "Repeat imaging should be considered only if clinically indicated by worsening symptoms, hypoxia, focal findings, or persistent fever."
                            : "Historical imaging should be interpreted with current symptoms and examination findings.",
                    item.reportDate(),
                    firstNonBlank(item.bodyPart(), item.modality()),
                    item.sourceReference(),
                    item.verificationStatus(),
                    "MEDIUM",
                    item.sourceDocumentId()
            ));
        }
        if (renalContext != null) {
            String referenceDate = firstNonBlank(renalContext.creatinineDate(), renalContext.egfrDate());
            findings.add(new ClinicalContextResponse.HistoricalFinding(
                    "RENAL_CONTEXT",
                    "Previous renal function",
                    joinSegments(
                            joinSegments(
                                    renalContext.creatinine() == null ? null : "Creatinine " + renalContext.creatinine(),
                                    renalContext.egfr() == null ? null : "eGFR " + renalContext.egfr()
                            ),
                            referenceDate == null ? null : "on " + formatHumanDate(parseLocalDate(referenceDate))
                    ),
                    renalContext.interpretation(),
                    referenceDate,
                    "LONGITUDINAL_MEMORY",
                    "Kidney function history",
                    renalContext.verificationStatus(),
                    "MEDIUM",
                    renalContext.sourceDocumentIds().isEmpty() ? null : renalContext.sourceDocumentIds().getFirst()
            ));
        }
        return findings;
    }

    private ClinicalContextResponse.HistoricalFinding toTrendFinding(ClinicalContextResponse.LabTrend trend) {
        if (trend == null) {
            return null;
        }
        if ("hba1c".equals(trend.analyteCode())) {
            String title = switch (trend.direction()) {
                case "IMPROVING" -> "Improving glycemic control";
                case "STABLE" -> "Stable glycemic control";
                default -> "Worsening glycemic control";
            };
            String summary = "HbA1c " + ("IMPROVING".equals(trend.direction()) ? "decreased" : "WORSENING".equals(trend.direction()) ? "increased" : "was stable")
                    + " from " + trend.olderValue() + "% on " + formatHumanDate(parseLocalDate(trend.olderDate()))
                    + " to " + trend.newerValue() + "% on " + formatHumanDate(parseLocalDate(trend.newerDate())) + ".";
            return new ClinicalContextResponse.HistoricalFinding(
                    "LAB_TREND",
                    title,
                    summary,
                    trend.clinicalInterpretation(),
                    trend.newerDate(),
                    "LONGITUDINAL_MEMORY",
                    "HbA1c trend",
                    trend.verificationStatus(),
                    "HIGH",
                    trend.sourceDocumentIds().isEmpty() ? null : trend.sourceDocumentIds().getFirst()
            );
        }
        return null;
    }

    private Observation latestObservation(List<Observation> observations, String analyteKey) {
        return observations.stream()
                .filter(observation -> analyteKey.equals(observation.analyteKey()))
                .sorted(Comparator.comparing(Observation::observedOn).reversed())
                .findFirst()
                .orElse(null);
    }

    private String buildRenalInterpretation(Observation creatinine, Observation egfr) {
        if (creatinine != null && egfr != null && creatinine.numericValue() <= 1.3d && egfr.numericValue() >= 60d) {
            return "Previous renal function was preserved at that time; current function should be rechecked if clinically indicated.";
        }
        if (creatinine != null || egfr != null) {
            return "Previous kidney-function results are available and should be interpreted with current clinical status.";
        }
        return null;
    }

    private String trendDirection(AnalyteDefinition definition, double change) {
        if (Math.abs(change) < 0.05d) {
            return "STABLE";
        }
        return switch (definition.semantics()) {
            case WORSE_WHEN_INCREASING -> change > 0 ? "WORSENING" : "IMPROVING";
            case WORSE_WHEN_DECREASING -> change < 0 ? "WORSENING" : "IMPROVING";
            case INDETERMINATE -> change > 0 ? "INCREASING" : "DECREASING";
        };
    }

    private String clinicalInterpretation(AnalyteDefinition definition, String direction) {
        return switch (definition.key()) {
            case "hba1c" -> switch (direction) {
                case "WORSENING" -> "Worsening glycemic control may increase susceptibility to infection and delay recovery.";
                case "IMPROVING" -> "Improved glycemic control supports better metabolic stability.";
                default -> "Glycemic control appears stable across the available interval.";
            };
            case "creatinine", "egfr" -> "Renal trends should be interpreted with current volume status, infection severity, and medication exposure.";
            case "crp" -> "Inflammatory-marker trends may help assess progression if correlated with symptoms and examination.";
            default -> null;
        };
    }

    private String absoluteChange(String analyteKey, double change) {
        if ("hba1c".equals(analyteKey)) {
            return String.format(Locale.ROOT, "%+.1f percentage points", change);
        }
        return String.format(Locale.ROOT, "%+.1f", change);
    }

    private boolean isReliableObservation(Observation observation) {
        if (observation == null) {
            return false;
        }
        String status = normalizeVerificationStatus(observation.verificationStatus());
        return "VERIFIED".equals(status);
    }

    private String normalizeAnalyteKey(String conceptKey, String label, String evidenceText) {
        String haystack = joinSegments(firstNonBlank(conceptKey, ""), firstNonBlank(label, ""), firstNonBlank(evidenceText, ""));
        if (!hasText(haystack)) {
            return null;
        }
        String normalized = haystack.toLowerCase(Locale.ROOT);
        for (AnalyteDefinition definition : ANALYTES.values()) {
            if (definition.aliases().stream().anyMatch(alias -> normalized.contains(alias.toLowerCase(Locale.ROOT)))) {
                return definition.key();
            }
        }
        return null;
    }

    private String canonicalUnit(String analyteKey, String unit, String evidenceText) {
        if (hasText(unit)) {
            return normalizeUnit(unit);
        }
        if (!hasText(analyteKey)) {
            return null;
        }
        AnalyteDefinition definition = ANALYTES.get(analyteKey);
        if (definition == null) {
            return null;
        }
        if ("hba1c".equals(analyteKey)) {
            return "%";
        }
        if ("egfr".equals(analyteKey)) {
            return "mL/min/1.73m2";
        }
        return hasText(evidenceText) ? inferUnitFromEvidence(evidenceText, definition.defaultUnit()) : definition.defaultUnit();
    }

    private boolean isCompatibleUnit(AnalyteDefinition definition, String unit) {
        if (definition == null || !hasText(definition.defaultUnit()) || !hasText(unit)) {
            return true;
        }
        String expected = normalizeUnit(definition.defaultUnit());
        String actual = normalizeUnit(unit);
        return expected.equalsIgnoreCase(actual);
    }

    private boolean isImagingDocument(ClinicalDocumentEntity document) {
        if (document == null) {
            return false;
        }
        if (document.getDocumentType() == ClinicalDocumentType.RADIOLOGY_REPORT
                || document.getDocumentType() == ClinicalDocumentType.X_RAY
                || document.getDocumentType() == ClinicalDocumentType.MRI_CT) {
            return true;
        }
        String haystack = joinSegments(firstNonBlank(document.getTitle(), ""), firstNonBlank(document.getDescription(), ""), firstNonBlank(document.getAiExtractionSummary(), ""));
        return IMAGING_ALIASES.stream().anyMatch(alias -> containsAny(haystack, alias));
    }

    private boolean isChestImagingDocument(ClinicalDocumentEntity document) {
        String haystack = joinSegments(
                firstNonBlank(document == null ? null : document.getTitle(), ""),
                firstNonBlank(document == null ? null : document.getDescription(), ""),
                firstNonBlank(document == null ? null : document.getAiExtractionSummary(), "")
        );
        return CHEST_ALIASES.stream().anyMatch(alias -> containsAny(haystack, alias));
    }

    private boolean hasRenalDocument(List<ClinicalDocumentEntity> documents) {
        if (documents == null) {
            return false;
        }
        return documents.stream().filter(Objects::nonNull).anyMatch(document -> containsAny(joinSegments(
                firstNonBlank(document.getTitle(), ""),
                firstNonBlank(document.getDescription(), ""),
                firstNonBlank(document.getAiExtractionSummary(), "")
        ), "kidney", "renal", "creatinine", "egfr", "e gfr", "microalbumin", "urea"));
    }

    private String extractDocumentSummary(ClinicalDocumentEntity document) {
        if (document == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        for (String raw : Arrays.asList(document.getAiExtractionAcceptedJson(), document.getAiExtractionStructuredJson())) {
            addJsonSummaryCandidates(candidates, raw);
        }
        if (hasText(document.getAiExtractionSummary())) {
            candidates.add(document.getAiExtractionSummary());
        }
        if (hasText(document.getDescription())) {
            candidates.add(document.getDescription());
        }
        return candidates.stream()
                .map(value -> compactText(cleanSummary(value), 260))
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private void addJsonSummaryCandidates(List<String> candidates, String rawJson) {
        if (!hasText(rawJson)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            for (String key : List.of("summary", "impression", "conclusion", "reportSummary", "findings")) {
                JsonNode node = findNode(root, key);
                if (node == null) {
                    continue;
                }
                if (node.isTextual()) {
                    candidates.add(node.asText());
                } else if (node.isArray()) {
                    List<String> values = new ArrayList<>();
                    node.forEach(item -> {
                        if (item.isTextual()) {
                            values.add(item.asText());
                        }
                    });
                    if (!values.isEmpty()) {
                        candidates.add(String.join(". ", values));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private JsonNode findNode(JsonNode node, String key) {
        if (node == null || key == null) {
            return null;
        }
        if (node.isObject()) {
            JsonNode direct = node.get(key);
            if (direct != null) {
                return direct;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode nested = findNode(entry.getValue(), key);
                if (nested != null) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode nested = findNode(item, key);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String cleanSummary(String value) {
        if (!hasText(value)) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)^summary\\s*[:\\-]\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^impression\\s*[:\\-]\\s*", "");
        return cleaned;
    }

    private List<String> extractNegativeFindings(String summary) {
        if (!hasText(summary)) {
            return List.of();
        }
        List<String> negatives = new ArrayList<>();
        for (String fragment : summary.split("[.;]")) {
            String candidate = fragment.trim();
            if (candidate.toLowerCase(Locale.ROOT).startsWith("no ")) {
                negatives.add(compactText(candidate, 140));
            }
        }
        return negatives.stream().limit(4).toList();
    }

    private String detectImagingModality(ClinicalDocumentEntity document) {
        if (document != null && document.getDocumentType() == ClinicalDocumentType.X_RAY) {
            return "X-ray";
        }
        String haystack = joinSegments(firstNonBlank(document == null ? null : document.getTitle(), ""), firstNonBlank(document == null ? null : document.getDescription(), ""));
        return detectImagingModality(haystack);
    }

    private String detectImagingModality(String value) {
        if (containsAny(value, "x ray", "xray", "x-ray", "cxr")) return "Chest X-ray";
        if (containsAny(value, "ct")) return "CT";
        if (containsAny(value, "mri")) return "MRI";
        if (containsAny(value, "ultrasound", "usg")) return "Ultrasound";
        if (containsAny(value, "echo", "echocardiography")) return "Echocardiography";
        return "Imaging";
    }

    private String detectBodyPart(ClinicalDocumentEntity document, String summary) {
        String haystack = joinSegments(
                firstNonBlank(document == null ? null : document.getTitle(), ""),
                firstNonBlank(document == null ? null : document.getDescription(), ""),
                firstNonBlank(summary, "")
        );
        if (CHEST_ALIASES.stream().anyMatch(alias -> containsAny(haystack, alias))) {
            return "Chest";
        }
        return null;
    }

    private String normalizeVerificationStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (Set.of("VERIFIED", "APPROVED", "ACCEPTED", "PUBLISHED", "AVAILABLE").contains(normalized)) {
            return "VERIFIED";
        }
        if (Set.of("PENDING_REVIEW", "PENDING_VERIFICATION", "REVIEW_REQUIRED", "UNVERIFIED", "NOT_REVIEWED", "AI_REVIEW_REQUIRED").contains(normalized)) {
            return "PENDING_VERIFICATION";
        }
        if ("REJECTED".equals(normalized)) {
            return "REJECTED";
        }
        return null;
    }

    private String normalizeDocumentVerificationStatus(String documentStatus, String aiStatus) {
        return firstNonBlank(normalizeVerificationStatus(documentStatus), normalizeVerificationStatus(aiStatus));
    }

    private String mergeVerificationStatus(String... statuses) {
        if (statuses == null || statuses.length == 0) {
            return null;
        }
        for (String status : statuses) {
            if ("VERIFIED".equals(normalizeVerificationStatus(status))) {
                return "VERIFIED";
            }
        }
        for (String status : statuses) {
            String normalized = normalizeVerificationStatus(status);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private int verificationRank(String status) {
        return switch (firstNonBlank(normalizeVerificationStatus(status), "")) {
            case "VERIFIED" -> 3;
            case "PENDING_VERIFICATION" -> 2;
            case "REJECTED" -> 0;
            default -> 1;
        };
    }

    private int confidenceRank(BigDecimal confidence) {
        if (confidence == null) {
            return 0;
        }
        return confidence.multiply(BigDecimal.valueOf(100)).intValue();
    }

    private int canonicalTitleScore(String title) {
        if (!hasText(title)) {
            return 0;
        }
        String normalized = title.toLowerCase(Locale.ROOT);
        if (normalized.contains("retest") || normalized.contains("reprocess")) {
            return 1;
        }
        return 2;
    }

    private String normalizeSourceIdentity(UUID documentId, String title, String type) {
        if (documentId != null) {
            return documentId.toString();
        }
        return normalizeText(joinSegments(firstNonBlank(title, ""), firstNonBlank(type, "")));
    }

    private LocalDate documentObservedOn(ClinicalDocumentEntity document) {
        if (document == null) {
            return null;
        }
        LocalDate reportDate = document.getReportDate();
        if (reportDate != null) {
            return reportDate;
        }
        OffsetDateTime createdAt = document.getCreatedAt();
        return createdAt == null ? null : createdAt.toLocalDate();
    }

    private LocalDate latestDate(LocalDate first, LocalDate second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private List<String> sourceDocumentIds(Observation... observations) {
        if (observations == null) {
            return List.of();
        }
        return Arrays.stream(observations)
                .filter(Objects::nonNull)
                .map(Observation::sourceDocumentId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();
    }

    private String approximateInterval(LocalDate older, LocalDate newer) {
        if (older == null || newer == null || !newer.isAfter(older)) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(older, newer);
        if (days < 45) {
            return days + " days";
        }
        long months = Math.max(1L, Math.round(days / 30.0d));
        return "approximately " + months + (months == 1 ? " month" : " months");
    }

    private String appendUnit(String value, String unit) {
        return hasText(unit) ? value + " " + unit : value;
    }

    private String formatHumanDate(LocalDate value) {
        return value == null ? null : value.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
    }

    private LocalDate parseLocalDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseNumericValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.valueOf(matcher.group(1));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String formatNumeric(Double value) {
        if (value == null) {
            return null;
        }
        return Math.abs(value - Math.rint(value)) < 0.001d
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private String normalizeUnit(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace("m²", "m2")
                .replace("ml/min/1.73m²", "ml/min/1.73m2");
        if (normalized.contains("mg/dl")) return "mg/dL";
        if (normalized.contains("g/dl")) return "g/dL";
        if (normalized.contains("ml/min/1.73m2")) return "mL/min/1.73m2";
        if (normalized.contains("kg/m2")) return "kg/m2";
        if (normalized.contains("%")) return "%";
        if (normalized.contains("mg/l")) return "mg/L";
        if (normalized.contains("u/l")) return "U/L";
        return value.trim();
    }

    private String inferUnitFromEvidence(String evidenceText, String fallback) {
        String normalized = evidenceText.toLowerCase(Locale.ROOT);
        if (normalized.contains("mg/dl")) return "mg/dL";
        if (normalized.contains("g/dl")) return "g/dL";
        if (normalized.contains("ml/min/1.73m2") || normalized.contains("ml/min/1.73m²")) return "mL/min/1.73m2";
        if (normalized.contains("mg/l")) return "mg/L";
        if (normalized.contains("u/l")) return "U/L";
        if (normalized.contains("%")) return "%";
        return fallback;
    }

    private boolean containsAny(String value, String... needles) {
        if (!hasText(value) || needles == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (hasText(needle) && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String joinSegments(String... values) {
        List<String> parts = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (hasText(value)) {
                    parts.add(value.trim());
                }
            }
        }
        return String.join(" ", parts).trim();
    }

    private String compactText(String value, int maxLength) {
        if (!hasText(value)) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String normalizeText(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT);
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

    private record Observation(
            String analyteKey,
            String analyteName,
            Double numericValue,
            String valueText,
            String unit,
            LocalDate observedOn,
            String verificationStatus,
            BigDecimal confidence,
            UUID sourceDocumentId,
            String sourceDocumentTitle,
            String sourceDocumentType,
            String evidenceText
    ) {}

    private record CollectionResult(List<Observation> observations, int duplicateCount) {}

    private record AnalyteDefinition(
            String key,
            String displayName,
            Set<String> aliases,
            String defaultUnit,
            TrendSemantics semantics
    ) {}

    private enum TrendSemantics {
        WORSE_WHEN_INCREASING,
        WORSE_WHEN_DECREASING,
        INDETERMINATE
    }
}
