package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import java.util.Map;

public record ClinicalReasoningResponse(
        ConsultationSummary consultation,
        ClinicalContextSummary clinicalContextSummary,
        ClinicalReasoningResult reasoningResult,
        ReasoningMetadata metadata,
        Map<String, Object> debug
) {
    public record ConsultationSummary(
            java.util.UUID consultationId,
            java.util.UUID patientId,
            String status,
            String chiefComplaint,
            String symptoms,
            String diagnosis,
            String advice,
            String clinicalNotes,
            String vitals,
            String vitalsSource,
            String vitalsSourceTitle
    ) {}

    public record ClinicalContextSummary(
            String patientName,
            Integer ageYears,
            String gender,
            String chiefComplaint,
            String vitals,
            String vitalsSource,
            String chronicConditions,
            String allergies,
            java.util.List<String> knownConditions,
            java.util.List<String> latestLabs,
            java.util.List<String> pendingInvestigations,
            java.util.List<String> recentReports,
            java.util.List<String> riskFlags,
            String lastVisitDiagnosis,
            String safetyContext
    ) {
        public static ClinicalContextSummary from(ClinicalContextResponse context, String vitals, String vitalsSource) {
            if (context == null) {
                return new ClinicalContextSummary(null, null, null, null, vitals, vitalsSource, null, null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null, null);
            }
            java.util.LinkedHashSet<String> latestLabs = new java.util.LinkedHashSet<>();
            if (context.labIntelligence() != null) {
                if (hasText(context.labIntelligence().lastHbA1c())) {
                    latestLabs.add(context.labIntelligence().lastHbA1c());
                }
                if (hasText(context.labIntelligence().latestBloodSugar())) {
                    latestLabs.add(context.labIntelligence().latestBloodSugar());
                }
                if (hasText(context.labIntelligence().latestLipidSummary())) {
                    latestLabs.add(context.labIntelligence().latestLipidSummary());
                }
                if (hasText(context.labIntelligence().latestLabReport())) {
                    latestLabs.add(context.labIntelligence().latestLabReport());
                }
            }
            return new ClinicalContextSummary(
                    context.patientSummary() == null ? null : context.patientSummary().patientName(),
                    context.patientSummary() == null ? null : context.patientSummary().ageYears(),
                    context.patientSummary() == null ? null : context.patientSummary().gender(),
                    context.intakeSummary() == null ? null : context.intakeSummary().chiefComplaint(),
                    vitals,
                    vitalsSource,
                    context.patientSummary() == null ? null : context.patientSummary().chronicConditions(),
                    context.patientSummary() == null ? null : context.patientSummary().allergies(),
                    context.longitudinalMemory() == null ? java.util.List.of() : context.longitudinalMemory().knownConditions().stream().map(ClinicalContextResponse.LongitudinalConcept::label).filter(ClinicalReasoningResponse::hasText).toList(),
                    new java.util.ArrayList<>(latestLabs),
                    context.labIntelligence() == null ? java.util.List.of() : context.labIntelligence().pendingInvestigations(),
                    context.documentIntelligence() == null ? java.util.List.of() : dedupeReports(context.documentIntelligence().recentReports()),
                    context.longitudinalMemory() == null ? java.util.List.of() : context.longitudinalMemory().riskFlags().stream().map(ClinicalContextResponse.LongitudinalConcept::label).filter(ClinicalReasoningResponse::hasText).toList(),
                    context.diagnosisHistory() == null ? null : context.diagnosisHistory().lastVisitDiagnosis(),
                    context.timelineSummary() == null ? null : context.timelineSummary().recentImportantEvents()
            );
        }

        private static java.util.List<String> dedupeReports(java.util.List<String> reports) {
            if (reports == null || reports.isEmpty()) {
                return java.util.List.of();
            }
            java.util.LinkedHashMap<String, String> deduped = new java.util.LinkedHashMap<>();
            for (String report : reports) {
                if (!hasText(report)) {
                    continue;
                }
                String normalized = report.toLowerCase(java.util.Locale.ROOT).replaceAll("retest\\s*\\d+", "retest").replaceAll("[^a-z0-9]+", " ").trim();
                deduped.putIfAbsent(normalized, report);
            }
            return new java.util.ArrayList<>(deduped.values());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
