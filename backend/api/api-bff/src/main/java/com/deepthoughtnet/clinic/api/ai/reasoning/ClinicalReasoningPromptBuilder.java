package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClinicalReasoningPromptBuilder {
    public static final String REASONING_ENGINE_VERSION = "clinic.clinical.reasoning.engine.v1";
    public static final String PROMPT_VERSION = "clinic.clinical.reasoning.v1";
    public static final String CONTEXT_VERSION = "v1";
    public static final String SCHEMA_VERSION = "v1";

    public Map<String, Object> buildInput(UUID tenantId,
                                          ConsultationEntity consultation,
                                          ClinicalContextResponse context,
                                          ClinicalReasoningRequest request,
                                          boolean repairMode,
                                          String repairReason) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tenantId", tenantId);
        input.put("consultationId", consultation.getId());
        input.put("patientId", consultation.getPatientId());
        input.put("patientAgeGender", buildPatientAgeGender(context));
        input.put("chiefComplaint", firstNonBlank(request == null ? null : request.chiefComplaint(), consultation.getChiefComplaints()));
        input.put("symptoms", firstNonBlank(request == null ? null : request.symptoms(), consultation.getSymptoms()));
        input.put("findings", firstNonBlank(request == null ? null : request.findings(), consultation.getClinicalNotes()));
        input.put("diagnosis", firstNonBlank(request == null ? null : request.diagnosis(), consultation.getDiagnosis()));
        input.put("advice", firstNonBlank(request == null ? null : request.advice(), consultation.getAdvice()));
        input.put("notes", firstNonBlank(request == null ? null : request.notes(), consultation.getClinicalNotes()));
        input.put("vitals", firstNonBlank(request == null ? null : request.vitals(), buildVitals(context, consultation)));
        input.put("currentPrescriptionDraft", request == null ? null : request.currentPrescriptionDraft());
        input.put("labOrdersSummary", request == null ? null : request.labOrdersSummary());
        input.put("knownConditions", listFromContext(context));
        input.put("recentReports", recentReports(context));
        input.put("currentMedicines", currentMedicines(context));
        input.put("sourceContextSummary", buildSourceContextSummary(context, consultation, request));
        input.put("reasoningPrompt", buildReasoningPrompt(context, consultation, request, repairMode, repairReason));
        input.put("promptVersion", PROMPT_VERSION);
        input.put("contextVersion", CONTEXT_VERSION);
        input.put("repairMode", repairMode);
        input.put("repairReason", repairReason);
        input.put("strictJsonOnly", true);
        return input;
    }

    private String buildReasoningPrompt(ClinicalContextResponse context,
                                        ConsultationEntity consultation,
                                        ClinicalReasoningRequest request,
                                        boolean repairMode,
                                        String repairReason) {
        if (repairMode) {
            return buildCompactRepairPrompt(context, consultation, request, repairReason);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Generate concise structured clinical reasoning for a doctor.\n");
        builder.append("Return strict JSON only. No markdown. No prose outside JSON.\n");
        builder.append("Do not duplicate the same clinical context across multiple fields.\n");
        builder.append("Use concise phrases rather than paragraphs.\n");
        builder.append("Never present AI as final diagnosis. Doctor must verify.\n");
        builder.append("No diagnosis without clinical justification.\n");
        builder.append("No emergency diagnosis unless red flags support it.\n");
        builder.append("Mention uncertainty and missing information.\n");
        builder.append("Always populate supportingEvidence, missingInformation, redFlags, recommendedTests, and safetyNotes from available context when clinically relevant.\n");
        builder.append("If evidence arrays would otherwise be empty, derive short evidence items from chief complaint, symptoms, vitals, known conditions, and labs.\n");
        builder.append("For fever with diabetes, include safety notes about glucose monitoring, hydration, worsening fever, breathlessness, SpO2 below 94, and confusion.\n");
        builder.append("Only recommend repeat tests when clinically justified. If HbA1c is already available, prefer reviewing the existing result. If a pending lab order exists, prefer completing it instead of duplicating the order.\n");
        builder.append("Use short strings only.\n");
        builder.append("If the patient has fever, cough, body ache, weakness, and no chest pain, dyspnea, hypoxia, or severe pain, avoid unsupported ACS, PE, or aortic dissection unless strong supporting red flags exist.\n");
        if (repairMode) {
            builder.append("RETRY MODE: previous response was truncated or invalid. Return compact corrected JSON only.\n");
            if (repairReason != null && !repairReason.isBlank()) {
                builder.append("Repair reason: ").append(repairReason.trim()).append('\n');
            }
        }
        builder.append("Required JSON shape:\n");
        builder.append("{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"...\",\"confidence\":0.0,\"status\":\"SUGGESTED\",\"whyConsidered\":\"...\",\"whyLessLikely\":\"...\",\"supportingEvidence\":[],\"contradictingEvidence\":[],\"missingInformation\":[],\"recommendedTests\":[],\"redFlags\":[]},\"differentialDiagnoses\":[{\"name\":\"...\",\"confidence\":0.0,\"whyConsidered\":\"...\",\"whyLessLikely\":\"...\",\"recommendedTests\":[]}],\"supportingEvidence\":[],\"contradictingEvidence\":[],\"missingInformation\":[],\"redFlags\":[],\"recommendedTests\":[],\"reasoningSummary\":\"...\",\"safetyNotes\":[],\"followUpAdvice\":[],\"patientExplanation\":\"...\",\"sourceContextSummary\":{\"chiefComplaint\":\"...\",\"symptoms\":[],\"vitals\":\"...\",\"knownConditions\":[],\"recentReports\":[],\"currentMedicines\":[]}}\n");
        builder.append("Constraints:\n");
        builder.append("- max 1 primary diagnosis\n");
        builder.append("- max 3 differential diagnoses\n");
        builder.append("- max 6 supportingEvidence items\n");
        builder.append("- max 4 contradictingEvidence items\n");
        builder.append("- max 5 missingInformation items\n");
        builder.append("- max 5 recommendedTests items\n");
        builder.append("- max 5 redFlags items\n");
        builder.append("- max 5 safetyNotes items\n");
        builder.append("- max 5 followUpAdvice items\n");
        builder.append("- primary diagnosis whyConsidered max 160 chars\n");
        builder.append("- primary diagnosis whyLessLikely max 120 chars\n");
        builder.append("- differential diagnosis whyConsidered max 160 chars\n");
        builder.append("- differential diagnosis whyLessLikely max 120 chars\n");
        builder.append("- evidence item max 100 chars\n");
        builder.append("- missingInformation reason max 100 chars\n");
        builder.append("- redFlag explanation max 100 chars\n");
        builder.append("- recommendedTest reason max 120 chars\n");
        builder.append("- safetyNote max 120 chars\n");
        builder.append("- reasoningSummary max 300 chars\n");
        builder.append("Patient context:\n");
        builder.append("- Chief complaint: ").append(firstNonBlank(request == null ? null : request.chiefComplaint(), consultation.getChiefComplaints())).append('\n');
        builder.append("- Symptoms: ").append(firstNonBlank(request == null ? null : request.symptoms(), consultation.getSymptoms())).append('\n');
        builder.append("- Findings/notes: ").append(firstNonBlank(request == null ? null : request.findings(), consultation.getClinicalNotes())).append('\n');
        builder.append("- Diagnosis field: ").append(firstNonBlank(request == null ? null : request.diagnosis(), consultation.getDiagnosis())).append('\n');
        builder.append("- Advice field: ").append(firstNonBlank(request == null ? null : request.advice(), consultation.getAdvice())).append('\n');
        builder.append("- Vitals: ").append(firstNonBlank(request == null ? null : request.vitals(), buildVitals(context, consultation))).append('\n');
        builder.append("- Pending lab orders: ").append(String.join(", ", pendingInvestigations(context))).append('\n');
        builder.append("- Available labs: ").append(String.join(", ", availableLabs(context))).append('\n');
        builder.append("- Known conditions: ").append(String.join(", ", listFromContext(context))).append('\n');
        builder.append("- Recent reports: ").append(String.join(", ", recentReports(context))).append('\n');
        builder.append("- Current medicines: ").append(String.join(", ", currentMedicines(context))).append('\n');
        builder.append("- Patient snapshot: ").append(context == null || context.patientSummary() == null ? "" : context.patientSummary().patientName()).append('\n');
        builder.append("- Longitudinal memory summary: ").append(context == null || context.aiSummary() == null ? "" : context.aiSummary()).append('\n');
        return builder.toString();
    }

    private String buildCompactRepairPrompt(ClinicalContextResponse context,
                                            ConsultationEntity consultation,
                                            ClinicalReasoningRequest request,
                                            String repairReason) {
        StringBuilder builder = new StringBuilder();
        builder.append("Return valid JSON only. No markdown. No explanation.\n");
        builder.append("Do not repeat the same context in multiple arrays.\n");
        builder.append("Use very short phrases only.\n");
        builder.append("Use compact schema:\n");
        builder.append("{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"...\",\"confidence\":0.0,\"status\":\"SUGGESTED\",\"whyConsidered\":\"...\",\"whyLessLikely\":\"...\",\"supportingEvidence\":[],\"contradictingEvidence\":[],\"missingInformation\":[],\"recommendedTests\":[],\"redFlags\":[]},\"differentialDiagnoses\":[{\"name\":\"...\",\"confidence\":0.0,\"whyConsidered\":\"...\",\"whyLessLikely\":\"...\",\"recommendedTests\":[]}],\"supportingEvidence\":[],\"contradictingEvidence\":[],\"missingInformation\":[],\"redFlags\":[],\"recommendedTests\":[],\"reasoningSummary\":\"...\",\"safetyNotes\":[],\"followUpAdvice\":[],\"patientExplanation\":\"...\"}\n");
        builder.append("Doctor must verify.\n");
        builder.append("Always populate supportingEvidence, missingInformation, redFlags, recommendedTests, and safetyNotes when possible.\n");
        builder.append("For fever with diabetes, include glucose monitoring, hydration, worsening fever, breathlessness, SpO2 below 94, and confusion.\n");
        builder.append("If HbA1c or CBC already exists or is pending, do not duplicate it without justification.\n");
        builder.append("Constraints:\n");
        builder.append("- max 1 primary diagnosis\n");
        builder.append("- max 2 differential diagnoses\n");
        builder.append("- max 4 supportingEvidence items\n");
        builder.append("- max 3 contradictingEvidence items\n");
        builder.append("- max 4 missingInformation items\n");
        builder.append("- max 4 redFlags items\n");
        builder.append("- max 4 recommendedTests items\n");
        builder.append("- max 4 safetyNotes items\n");
        builder.append("- max 4 followUpAdvice items\n");
        builder.append("- diagnosis reasons max 140 chars\n");
        builder.append("- evidence and missingInformation text max 90 chars\n");
        builder.append("- recommendedTest reasons max 100 chars\n");
        builder.append("- safetyNote max 100 chars\n");
        builder.append("- reasoningSummary max 220 chars\n");
        if (repairReason != null && !repairReason.isBlank()) {
            builder.append("Reason: ").append(repairReason.trim()).append('\n');
        }
        builder.append("Chief complaint: ").append(firstNonBlank(request == null ? null : request.chiefComplaint(), consultation.getChiefComplaints())).append('\n');
        builder.append("Symptoms: ").append(firstNonBlank(request == null ? null : request.symptoms(), consultation.getSymptoms())).append('\n');
        builder.append("Vitals: ").append(firstNonBlank(request == null ? null : request.vitals(), buildVitals(context, consultation))).append('\n');
        builder.append("Available labs: ").append(String.join(", ", availableLabs(context))).append('\n');
        builder.append("Pending lab orders: ").append(String.join(", ", pendingInvestigations(context))).append('\n');
        builder.append("Known conditions: ").append(String.join(", ", listFromContext(context))).append('\n');
        builder.append("Recent reports: ").append(String.join(", ", recentReports(context))).append('\n');
        return builder.toString();
    }

    private Map<String, Object> buildSourceContextSummary(ClinicalContextResponse context,
                                                          ConsultationEntity consultation,
                                                          ClinicalReasoningRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chiefComplaint", firstNonBlank(request == null ? null : request.chiefComplaint(), consultation.getChiefComplaints()));
        summary.put("symptoms", splitToList(firstNonBlank(request == null ? null : request.symptoms(), consultation.getSymptoms())));
        summary.put("vitals", firstNonBlank(request == null ? null : request.vitals(), buildVitals(context, consultation)));
        summary.put("vitalsSource", determineVitalsSource(context, consultation));
        summary.put("knownConditions", listFromContext(context));
        summary.put("recentReports", recentReports(context));
        summary.put("currentMedicines", currentMedicines(context));
        summary.put("pendingInvestigations", pendingInvestigations(context));
        summary.put("availableLabs", availableLabs(context));
        return summary;
    }

    private String buildPatientAgeGender(ClinicalContextResponse context) {
        if (context == null || context.patientSummary() == null) {
            return null;
        }
        Integer age = context.patientSummary().ageYears();
        String gender = context.patientSummary().gender();
        if (age == null && (gender == null || gender.isBlank())) {
            return null;
        }
        return (age == null ? "Age -" : age + "y") + " / " + (gender == null || gender.isBlank() ? "-" : gender);
    }

    private String buildVitals(ClinicalContextResponse context, ConsultationEntity consultation) {
        if (consultation != null) {
            String consultationVitals = buildConsultationVitals(consultation);
            if (consultationVitals != null) {
                return consultationVitals;
            }
        }
        if (context == null || context.intakeSummary() == null || context.intakeSummary().latestVitals() == null) {
            return null;
        }
        ClinicalContextResponse.VitalsSnapshot vitals = context.intakeSummary().latestVitals();
        List<String> parts = new ArrayList<>();
        if (vitals.bloodPressureSystolic() != null && vitals.bloodPressureDiastolic() != null) {
            parts.add("BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic());
        }
        if (vitals.pulseRate() != null) {
            parts.add("Pulse " + vitals.pulseRate());
        }
        if (vitals.temperature() != null) {
            parts.add("Temp " + vitals.temperature() + (vitals.temperatureUnit() == null || vitals.temperatureUnit().isBlank() ? "" : " " + vitals.temperatureUnit()));
        }
        if (vitals.spo2() != null) {
            parts.add("SpO2 " + vitals.spo2());
        }
        if (vitals.randomBloodSugar() != null) {
            parts.add("RBS " + vitals.randomBloodSugar());
        }
        return parts.isEmpty() ? null : "INTAKE " + String.join(", ", parts);
    }

    private String buildConsultationVitals(ConsultationEntity consultation) {
        if (consultation == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (consultation.getBloodPressureSystolic() != null && consultation.getBloodPressureDiastolic() != null) {
            parts.add("BP " + consultation.getBloodPressureSystolic() + "/" + consultation.getBloodPressureDiastolic());
        }
        if (consultation.getPulseRate() != null) {
            parts.add("Pulse " + consultation.getPulseRate());
        }
        if (consultation.getTemperature() != null) {
            parts.add("Temp " + consultation.getTemperature() + (consultation.getTemperatureUnit() == null || consultation.getTemperatureUnit().name().isBlank() ? "" : " " + consultation.getTemperatureUnit().name()));
        }
        if (consultation.getSpo2() != null) {
            parts.add("SpO2 " + consultation.getSpo2());
        }
        if (consultation.getRespiratoryRate() != null) {
            parts.add("RR " + consultation.getRespiratoryRate());
        }
        if (consultation.getWeightKg() != null) {
            parts.add("Weight " + consultation.getWeightKg());
        }
        if (consultation.getHeightCm() != null) {
            parts.add("Height " + consultation.getHeightCm());
        }
        return parts.isEmpty() ? null : "CONSULTATION " + String.join(", ", parts);
    }

    private String determineVitalsSource(ClinicalContextResponse context, ConsultationEntity consultation) {
        boolean consultationVitalsPresent = consultation != null
                && (consultation.getBloodPressureSystolic() != null
                || consultation.getBloodPressureDiastolic() != null
                || consultation.getPulseRate() != null
                || consultation.getTemperature() != null
                || consultation.getSpo2() != null
                || consultation.getRespiratoryRate() != null
                || consultation.getWeightKg() != null
                || consultation.getHeightCm() != null);
        if (consultationVitalsPresent) {
            return "CONSULTATION";
        }
        return context != null && context.intakeSummary() != null && context.intakeSummary().latestVitals() != null ? "INTAKE" : null;
    }

    private List<String> listFromContext(ClinicalContextResponse context) {
        if (context == null || context.longitudinalMemory() == null) {
            return List.of();
        }
        return context.longitudinalMemory().knownConditions().stream()
                .map(concept -> concept.label() == null || concept.label().isBlank() ? concept.valueText() : concept.label())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> recentReports(ClinicalContextResponse context) {
        if (context == null || context.documentIntelligence() == null) {
            return List.of();
        }
        return dedupeRecentReports(context.documentIntelligence().recentReports()).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private List<String> pendingInvestigations(ClinicalContextResponse context) {
        if (context == null || context.labIntelligence() == null || context.labIntelligence().pendingInvestigations() == null) {
            return List.of();
        }
        return context.labIntelligence().pendingInvestigations().stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> availableLabs(ClinicalContextResponse context) {
        List<String> labs = new ArrayList<>();
        if (context != null && context.labIntelligence() != null) {
            if (context.labIntelligence().lastHbA1c() != null) {
                labs.add(context.labIntelligence().lastHbA1c());
            }
            if (context.labIntelligence().latestBloodSugar() != null) {
                labs.add(context.labIntelligence().latestBloodSugar());
            }
            if (context.labIntelligence().latestLipidSummary() != null && !context.labIntelligence().latestLipidSummary().isBlank()) {
                labs.add(context.labIntelligence().latestLipidSummary());
            }
        }
        return labs;
    }

    private List<String> dedupeRecentReports(List<String> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, String> deduped = new java.util.LinkedHashMap<>();
        for (String report : reports) {
            if (report == null || report.isBlank()) {
                continue;
            }
            String normalized = report.toLowerCase(java.util.Locale.ROOT).replaceAll("retest\\s*\\d+", "retest").replaceAll("[^a-z0-9]+", " ").trim();
            deduped.putIfAbsent(normalized, report);
        }
        return new ArrayList<>(deduped.values());
    }

    private List<String> currentMedicines(ClinicalContextResponse context) {
        if (context == null || context.longitudinalMemory() == null) {
            return List.of();
        }
        return context.longitudinalMemory().longTermMedications().stream()
                .map(concept -> concept.label() == null || concept.label().isBlank() ? concept.valueText() : concept.label())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> splitToList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary.trim();
        }
        return null;
    }
}
