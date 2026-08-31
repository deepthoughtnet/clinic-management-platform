package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiConsultationAskService {
    private static final Logger log = LoggerFactory.getLogger(AiConsultationAskService.class);
    private static final String EXPLAIN_DIAGNOSIS_PROMPT = "Explain diagnosis";
    private static final String EXPLAIN_DIAGNOSIS_TEMPLATE_CODE = "clinic.consultation.explain-diagnosis.v1";
    private static final String EXPLAIN_DIAGNOSIS_USE_CASE = "consultation.explain-diagnosis";
    private static final String HISTORY_GAP_PROMPT = "What else should I ask?";
    private static final String HISTORY_GAP_TEMPLATE_CODE = "clinic.consultation.history-gaps.v1";
    private static final String HISTORY_GAP_USE_CASE = "consultation.history-gaps";
    private static final String SUGGEST_TESTS_PROMPT = "Suggest tests";
    private static final String SUGGEST_TESTS_TEMPLATE_CODE = "clinic.consultation.suggest-tests.v1";
    private static final String SUGGEST_TESTS_USE_CASE = "consultation.suggest-tests";
    private final AiDoctorCopilotService copilotService;
    private final ClinicalContextService clinicalContextService;

    public AiConsultationAskService(AiDoctorCopilotService copilotService, ClinicalContextService clinicalContextService) {
        this.copilotService = copilotService;
        this.clinicalContextService = clinicalContextService;
    }

    public AiDraftResponse ask(AiConsultationAskRequest request) {
        ClinicalContextResponse context = clinicalContextService.buildClinicalContext(
                RequestContextHolder.requireTenantId(),
                request.patientId(),
                request.consultationId()
        );
        boolean explainDiagnosis = isExplainDiagnosisPrompt(request.prompt());
        boolean historyGapPrompt = isHistoryGapPrompt(request.prompt());
        boolean suggestTestsPrompt = isSuggestTestsPrompt(request.prompt());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consultationId", request.consultationId());
        input.put("patientId", request.patientId());
        input.put("prompt", request.prompt());
        if (explainDiagnosis) {
            input.put("diagnosisExplanationContext", buildDiagnosisExplanationContext(request, context));
        } else if (historyGapPrompt) {
            input.put("historyGapContext", buildHistoryGapContext(request, context));
            input.put("clinicianReviewContext", buildClinicianReviewContext(context));
        } else if (suggestTestsPrompt) {
            input.put("investigationSuggestionContext", buildInvestigationSuggestionContext(request, context));
        } else {
            input.put("aiPromptContext", context.aiPromptContext());
            input.put("clinicalContextSummary", context.aiSummary());
        }
        log.info("[AI-CHAT-CONTEXT] consultationId={} patientId={} canonicalContextChars={} promptContextChars={} summaryChars={} questionChars={}",
                request.consultationId(),
                request.patientId(),
                context.clinicalContextJson() == null ? 0 : context.clinicalContextJson().length(),
                suggestTestsPrompt ? 0 : context.aiPromptContext() == null ? 0 : context.aiPromptContext().length(),
                suggestTestsPrompt ? 0 : context.aiSummary() == null ? 0 : context.aiSummary().length(),
                request.prompt() == null ? 0 : request.prompt().length());

        return copilotService.draft(
                AiTaskType.GENERIC_COPILOT,
                explainDiagnosis ? EXPLAIN_DIAGNOSIS_TEMPLATE_CODE : historyGapPrompt ? HISTORY_GAP_TEMPLATE_CODE : suggestTestsPrompt ? SUGGEST_TESTS_TEMPLATE_CODE : "clinic.consultation.ask.v1",
                explainDiagnosis ? EXPLAIN_DIAGNOSIS_USE_CASE : historyGapPrompt ? HISTORY_GAP_USE_CASE : suggestTestsPrompt ? SUGGEST_TESTS_USE_CASE : "consultation.ask",
                input,
                List.of()
        );
    }

    private boolean isExplainDiagnosisPrompt(String prompt) {
        return prompt != null && EXPLAIN_DIAGNOSIS_PROMPT.equalsIgnoreCase(prompt.trim());
    }

    private boolean isHistoryGapPrompt(String prompt) {
        return prompt != null && HISTORY_GAP_PROMPT.equalsIgnoreCase(prompt.trim());
    }

    private boolean isSuggestTestsPrompt(String prompt) {
        return prompt != null && SUGGEST_TESTS_PROMPT.equalsIgnoreCase(prompt.trim());
    }

    private String buildDiagnosisExplanationContext(AiConsultationAskRequest request, ClinicalContextResponse context) {
        List<String> parts = new java.util.ArrayList<>();
        String workingDiagnosis = normalize(request == null ? null : request.diagnosis());
        parts.add("Working diagnosis: " + (workingDiagnosis == null ? "Not recorded" : workingDiagnosis));
        addPart(parts, "Chief complaints", request == null ? null : request.chiefComplaints());
        addPart(parts, "Symptoms", request == null ? null : request.symptoms());
        addPart(parts, "Clinical notes", request == null ? null : request.clinicalNotes());
        addPart(parts, "Vitals", request == null ? null : request.vitals());
        addPart(parts, "Known conditions", request == null ? null : request.chronicConditions());
        addPart(parts, "Allergies", request == null ? null : request.allergies());
        addPart(parts, "Current medicines", context == null || context.patientSummary() == null ? null : joinStrings(context.patientSummary().currentMedications(), 5));
        addPart(parts, "Pending investigations", request == null ? null : request.labOrdersSummary());
        addPart(parts, "Current prescription draft", request == null ? null : request.currentPrescriptionDraft());
        addPart(parts, "Clinical summary", context == null ? null : context.aiSummary());
        return String.join(" | ", parts);
    }

    private String buildHistoryGapContext(AiConsultationAskRequest request, ClinicalContextResponse context) {
        List<String> sections = new java.util.ArrayList<>();
        String workingDiagnosis = normalize(request == null ? null : request.diagnosis());
        sections.add("Working diagnosis: " + (workingDiagnosis == null ? "Not recorded" : workingDiagnosis));
        sections.add("Goal: Identify the most important missing clinical history questions for the doctor to ask the patient.");

        List<String> currentHistory = new java.util.ArrayList<>();
        addPart(currentHistory, "Chief complaint", request == null ? null : request.chiefComplaints());
        addPart(currentHistory, "Symptoms", request == null ? null : request.symptoms());
        addPart(currentHistory, "Vitals", request == null ? null : request.vitals());
        addPart(currentHistory, "Allergies", request == null ? null : request.allergies());
        addPart(currentHistory, "Chronic conditions", request == null ? null : request.chronicConditions());
        addPart(currentHistory, "Current medicines", context == null || context.patientSummary() == null ? null : joinStrings(context.patientSummary().currentMedications(), 6));
        if (!currentHistory.isEmpty()) {
            sections.add("Known history: " + String.join(" | ", currentHistory));
        }

        List<String> missingQuestions = buildMissingHistoryQuestions(request, context);
        sections.add("Questions to ask the patient: " + (missingQuestions.isEmpty() ? "No major clinical history gaps are obvious from the record" : String.join(" | ", missingQuestions)));

        List<String> clinicianReview = buildClinicianReviewItems(context);
        if (!clinicianReview.isEmpty()) {
            sections.add("Clinician review: " + String.join(" | ", clinicianReview));
        }
        sections.add("Instructions: Ask the patient only. Prioritize missing history and red-flag assessment. Do not ask about payment, billing, internal AI metadata, prompt fragments, order implementation details, or truncated source text.");
        return String.join("\n", sections);
    }

    private List<String> buildMissingHistoryQuestions(AiConsultationAskRequest request, ClinicalContextResponse context) {
        List<String> questions = new java.util.ArrayList<>();
        if (isBlank(request == null ? null : request.symptoms())) {
            questions.add("Ask about symptom duration, progression, severity, and associated symptoms");
        } else {
            questions.add("Clarify symptom duration, progression, severity, and associated symptoms");
        }
        if (isBlank(request == null ? null : request.vitals())) {
            questions.add("Check vitals and focused examination findings");
        }
        if (isBlank(request == null ? null : request.allergies())) {
            questions.add("Ask about allergies and prior reactions");
        }
        if (isBlank(request == null ? null : request.chronicConditions())) {
            questions.add("Ask about significant chronic conditions");
        }
        if (isBlank(context == null || context.patientSummary() == null ? null : joinStrings(context.patientSummary().currentMedications(), 6))) {
            questions.add("Ask about current medicines and recent treatment");
        }
        if (isBlank(request == null ? null : request.chiefComplaints())) {
            questions.add("Ask about the main complaint and fever pattern if relevant");
        }
        questions.add("Ask about associated symptoms, including respiratory red flags, chest pain, confusion, dehydration, or worsening fever when clinically relevant");
        questions.add("Ask about relevant past history, travel, exposure, or sick contacts when infection is possible");
        return questions.stream().distinct().toList();
    }

    private List<String> buildClinicianReviewItems(ClinicalContextResponse context) {
        List<String> review = new java.util.ArrayList<>();
        if (context != null && context.labIntelligence() != null) {
            if (!context.labIntelligence().pendingInvestigations().isEmpty()) {
                review.add("Pending investigations: " + joinStrings(context.labIntelligence().pendingInvestigations(), 5));
            }
            if (isNonBlank(context.labIntelligence().latestLabReport()) || !context.labIntelligence().abnormalValues().isEmpty()) {
                List<String> labParts = new java.util.ArrayList<>();
                addPart(labParts, "Latest lab report", context.labIntelligence().latestLabReport());
                if (!context.labIntelligence().abnormalValues().isEmpty()) {
                    labParts.add("Abnormal values: " + joinStrings(context.labIntelligence().abnormalValues(), 5));
                }
                if (!labParts.isEmpty()) {
                    review.add(String.join(" | ", labParts));
                }
            }
        }
        return review;
    }

    private String buildClinicianReviewContext(ClinicalContextResponse context) {
        List<String> sections = new java.util.ArrayList<>();
        List<String> review = buildClinicianReviewItems(context);
        if (review.isEmpty()) {
            sections.add("Clinician review: No pending investigations or abnormal lab context is available.");
        } else {
            sections.add(String.join("\n", review));
        }
        sections.add("Instructions: If investigations are relevant, mention them only as clinician-review items. Do not turn them into patient questions.");
        return String.join("\n", sections);
    }

    private String buildInvestigationSuggestionContext(AiConsultationAskRequest request, ClinicalContextResponse context) {
        List<String> sections = new java.util.ArrayList<>();
        String workingDiagnosis = normalize(request == null ? null : request.diagnosis());
        sections.add("Working diagnosis: " + (workingDiagnosis == null ? "Not recorded" : workingDiagnosis));
        sections.add("Goal: Suggest clinically grounded investigations only when justified by the current consultation.");

        List<String> currentClinicalFacts = new java.util.ArrayList<>();
        addPart(currentClinicalFacts, "Patient age/gender", request == null ? null : request.patientAgeGender());
        addPart(currentClinicalFacts, "Chief complaint", firstNonBlank(request == null ? null : request.chiefComplaints(), context == null || context.intakeSummary() == null ? null : context.intakeSummary().chiefComplaint()));
        addPart(currentClinicalFacts, "Symptoms", request == null ? null : request.symptoms());
        addPart(currentClinicalFacts, "Vitals", request == null ? null : request.vitals());
        addPart(currentClinicalFacts, "Allergies", firstNonBlank(request == null ? null : request.allergies(), context == null || context.patientSummary() == null ? null : context.patientSummary().allergies()));
        addPart(currentClinicalFacts, "Chronic conditions", firstNonBlank(request == null ? null : request.chronicConditions(), context == null || context.patientSummary() == null ? null : context.patientSummary().chronicConditions()));
        addPart(currentClinicalFacts, "Current medicines", context == null || context.patientSummary() == null ? null : joinStrings(context.patientSummary().currentMedications(), 6));
        addPart(currentClinicalFacts, "Clinical notes", request == null ? null : request.clinicalNotes());
        if (!currentClinicalFacts.isEmpty()) {
            sections.add("Current clinical facts: " + String.join(" | ", currentClinicalFacts));
        }

        List<String> alreadyOrdered = new java.util.ArrayList<>();
        addPart(alreadyOrdered, "Current consultation lab orders", request == null ? null : request.labOrdersSummary());
        if (context != null && context.labIntelligence() != null) {
            addPart(alreadyOrdered, "Pending investigations", joinStrings(context.labIntelligence().pendingInvestigations(), 8));
        }
        if (!alreadyOrdered.isEmpty()) {
            sections.add("Already ordered/pending investigations: " + String.join(" | ", alreadyOrdered));
        } else {
            sections.add("Already ordered/pending investigations: None documented.");
        }

        List<String> availableEvidence = new java.util.ArrayList<>();
        if (context != null && context.labIntelligence() != null) {
            addPart(availableEvidence, "Latest lab report", context.labIntelligence().latestLabReport());
            if (!context.labIntelligence().abnormalValues().isEmpty()) {
                availableEvidence.add("Abnormal values: " + joinStrings(context.labIntelligence().abnormalValues(), 8));
            }
            addPart(availableEvidence, "Previous trends", joinStrings(context.labIntelligence().previousTrends(), 8));
            addPart(availableEvidence, "Last HbA1c", context.labIntelligence().lastHbA1c());
            addPart(availableEvidence, "Last CBC", context.labIntelligence().lastCbc());
            addPart(availableEvidence, "Last creatinine", context.labIntelligence().lastCreatinine());
            addPart(availableEvidence, "Latest blood sugar", context.labIntelligence().latestBloodSugar());
            addPart(availableEvidence, "Latest lipid summary", context.labIntelligence().latestLipidSummary());
            addPart(availableEvidence, "Latest blood pressure", context.labIntelligence().latestBloodPressure());
            addPart(availableEvidence, "Latest BMI", context.labIntelligence().latestBmi());
        }
        if (!availableEvidence.isEmpty()) {
            sections.add("Available investigation evidence: " + String.join(" | ", availableEvidence));
        } else {
            sections.add("Available investigation evidence: No recent investigation result is documented.");
        }

        sections.add("Instructions: First identify investigations already ordered, pending, or recently available. Do not recommend duplicates of already ordered, pending, or recently available tests unless there is a clear clinical reason and you explain it. Separate the response into exactly these sections: Already ordered/pending, Consider if indicated, and Not currently necessary / insufficient information. For every suggested test include a brief rationale. If evidence is insufficient to recommend a specific test, say so rather than inventing one. Do not include billing, payment, internal AI metadata, prompt fragments, action codes, or implementation details. Do not automatically create, select, or map a catalog test.");
        return String.join("\n", sections);
    }

    private void addPart(List<String> parts, String label, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            parts.add(label + ": " + normalized);
        }
    }

    private String joinStrings(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> filtered = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .limit(Math.max(1, limit))
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        return String.join(", ", filtered);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return normalize(value) == null;
    }

    private boolean isNonBlank(String value) {
        return !isBlank(value);
    }
}
