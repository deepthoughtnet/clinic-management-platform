package com.deepthoughtnet.clinic.api.clinicaldocument.ai.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Provider-independent extraction passed to the clinical validation pipeline. */
public record ClinicalDocumentExtraction(
        String documentType,
        String reportDate,
        Map<String, Object> patient,
        List<LabResult> labResults,
        List<Object> conditions,
        List<Object> riskFlags,
        String summary,
        String impression,
        List<String> recommendations,
        List<String> limitations,
        BigDecimal confidence,
        String confidenceLabel,
        Map<String, Object> providerMetadata,
        List<String> narrativeTexts,
        String parseStatus,
        List<String> qualityWarnings
) {
    public ClinicalDocumentExtraction {
        labResults = labResults == null ? List.of() : List.copyOf(labResults);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
        narrativeTexts = narrativeTexts == null ? List.of() : List.copyOf(narrativeTexts);
        qualityWarnings = qualityWarnings == null ? List.of() : List.copyOf(qualityWarnings);
    }

    /** Canonical map used only as input to the existing domain-neutral normalizers. */
    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentType", documentType);
        result.put("reportDate", reportDate);
        result.put("patient", patient == null ? Map.of() : patient);
        Map<String, Object> findings = new LinkedHashMap<>();
        findings.put("labResults", labResults.stream().map(LabResult::asMap).toList());
        findings.put("conditions", conditions);
        findings.put("riskFlags", riskFlags);
        result.put("factualFindings", findings);
        result.put("summary", summary);
        result.put("impression", impression);
        result.put("recommendations", recommendations);
        result.put("limitations", limitations);
        result.put("confidence", confidence);
        result.put("providerMetadata", providerMetadata);
        result.put("narrativeTexts", narrativeTexts);
        result.put("parseStatus", parseStatus);
        result.put("qualityWarnings", qualityWarnings);
        return result;
    }

    public record LabResult(
            String testName,
            String value,
            String unit,
            String referenceRange,
            String flag
    ) {
        public Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("testName", testName);
            result.put("value", value);
            result.put("unit", unit);
            result.put("referenceRange", referenceRange);
            result.put("flag", flag);
            return result;
        }
    }
}
