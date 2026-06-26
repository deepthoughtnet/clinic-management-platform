package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record PatientPortalCareAiExtractedEntities(
        Map<PatientPortalCareAiEntityType, List<String>> values,
        Map<PatientPortalCareAiEntityType, Double> confidenceByType,
        String dateIssue
) {
    static PatientPortalCareAiExtractedEntities empty() {
        return new PatientPortalCareAiExtractedEntities(Map.of(), Map.of(), null);
    }

    String first(PatientPortalCareAiEntityType type) {
        List<String> items = values == null ? null : values.get(type);
        return items == null || items.isEmpty() ? null : items.getFirst();
    }

    boolean has(PatientPortalCareAiEntityType type) {
        return first(type) != null;
    }

    double confidence() {
        if (confidenceByType == null || confidenceByType.isEmpty()) {
            return 0.0d;
        }
        return confidenceByType.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0d);
    }

    boolean requiresDateClarification() {
        return "ambiguous".equalsIgnoreCase(dateIssue);
    }

    String doctor() {
        return first(PatientPortalCareAiEntityType.DOCTOR);
    }

    String clinic() {
        return first(PatientPortalCareAiEntityType.CLINIC);
    }

    String appointment() {
        return first(PatientPortalCareAiEntityType.APPOINTMENT);
    }

    String date() {
        return first(PatientPortalCareAiEntityType.DATE);
    }

    String time() {
        return first(PatientPortalCareAiEntityType.TIME);
    }

    String timeSlot() {
        return first(PatientPortalCareAiEntityType.TIME_SLOT);
    }

    String timeWindow() {
        return first(PatientPortalCareAiEntityType.TIME_WINDOW);
    }

    String speciality() {
        return first(PatientPortalCareAiEntityType.SPECIALITY);
    }

    String location() {
        return first(PatientPortalCareAiEntityType.LOCATION);
    }

    boolean confirmation() {
        return has(PatientPortalCareAiEntityType.CONFIRMATION);
    }

    boolean cancellation() {
        return has(PatientPortalCareAiEntityType.CANCELLATION);
    }

    boolean reset() {
        return has(PatientPortalCareAiEntityType.RESET);
    }

    Map<String, Object> traceView() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (values != null) {
            result.put("values", values);
        }
        if (confidenceByType != null) {
            result.put("confidenceByType", confidenceByType);
        }
        result.put("dateIssue", dateIssue);
        result.put("overallConfidence", confidence());
        return result;
    }

    Map<PatientPortalCareAiEntityType, List<String>> safeValues() {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(values);
    }

    Map<PatientPortalCareAiEntityType, Double> safeConfidenceByType() {
        if (confidenceByType == null || confidenceByType.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(confidenceByType);
    }
}
