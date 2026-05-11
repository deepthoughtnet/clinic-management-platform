package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiPromptTemplateDefinition;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiPromptTemplateStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplateCatalog {
    private static final String SYSTEM_PROMPT = """
            You are a reusable AI orchestration engine.
            Provide explanations, classifications, summaries, and recommendations only.
            Never approve, reject, post, delete, send, or mutate business state.
            Deterministic application data is authoritative.
            Return concise business language and, when possible, structured JSON with keys:
            answer, suggestedActions, limitations, confidence.
            """;

    private static final String USER_PROMPT = """
            Product: {{productCode}}
            Task: {{taskType}}
            Use case: {{useCaseCode}}
            Prompt template: {{promptTemplateCode}}
            Tenant: {{tenantId}}
            Actor: {{actorUserId}}
            Correlation: {{correlationId}}
            Input variables JSON:
            {{inputVariablesJson}}
            Evidence:
            {{evidenceSummary}}

            Answer the user's business question. Be advisory only. Do not mutate workflow state.
            Return ONLY valid JSON. Do not include markdown. Do not include explanatory text before or after JSON.
            """;

    private final Map<String, AiPromptTemplateDefinition> defaults = Map.ofEntries(
            entry("clinic.clinic.extraction.v1", AiProductCode.CLINIC, AiTaskType.CLINIC_EXTRACTION,
                    "Extract clinic details from the supplied document context. Return structured JSON when possible.",
                    List.of("Validate clinic fields", "Surface extraction uncertainties", "Do not submit or approve anything"),
                    List.of("Extraction output is advisory until reviewed", "OCR and provider limits may affect accuracy")),
            entry("clinic.clinic.risk.v1", AiProductCode.CLINIC, AiTaskType.CLINIC_RISK_EXPLANATION,
                    "Explain clinic risk in plain business language using only the provided clinic context.",
                    List.of("Review missing fields and duplicate risk", "Confirm doctor and totals before approval", "Escalate unusual amounts"),
                    List.of("AI does not change clinic status", "Deterministic validation remains authoritative")),
            entry("clinic.clinic.duplicate.v1", AiProductCode.CLINIC, AiTaskType.DUPLICATE_EXPLANATION,
                    "Explain why the clinic may be a duplicate. Reference only the supplied facts.",
                    List.of("Compare clinic number, doctor, date, and amount", "Clear or confirm the duplicate flag only after review"),
                    List.of("AI cannot clear duplicates", "System of record remains authoritative")),
            entry("clinic.clinic.doctor-resubmission.v1", AiProductCode.CLINIC, AiTaskType.DOCTOR_RESUBMISSION_SUGGESTION,
                    "Draft a doctor resubmission note using only the supplied clinic facts.",
                    List.of("Ask the doctor to correct missing or inconsistent fields", "Keep the note brief and business-friendly"),
                    List.of("Reviewer must edit before sending", "AI does not send emails autonomously")),
            entry("clinic.reconciliation.exception.explain.v1", AiProductCode.CLINIC, AiTaskType.RECONCILIATION_EXCEPTION_EXPLANATION,
                    "Explain the reconciliation exception using statement facts, candidate facts, and the reason supplied.",
                    List.of("Review statement amount, clinic amount, and difference", "Use manual allocation or mark unapplied if appropriate", "Do not confirm without evidence"),
                    List.of("Statement line remains the source of truth", "AI cannot settle or post accounting entries")),
            entry("clinic.reconciliation.match.explain.v1", AiProductCode.CLINIC, AiTaskType.RECONCILIATION_MATCH_EXPLANATION,
                    "Explain the suggested reconciliation match using statement facts and candidate clinic facts.",
                    List.of("Review confidence, amount, and counterparty match", "Confirm only if the business evidence is strong"),
                    List.of("AI does not confirm matches automatically", "Application data remains authoritative")),
            entry("clinic.reconciliation.suggest.resolution.v1", AiProductCode.CLINIC, AiTaskType.GENERIC_RECOMMENDATION,
                    "Suggest a reconciliation resolution using the supplied statement, exception, match, and candidate context. Return structured JSON with suggestionType, confidence, reasoning, suggestedAllocations, and recommendedActions when possible.",
                    List.of("Open the manual match flow", "Mark the statement unapplied if no clinic fits", "Escalate wrong-direction or bank-charge cases"),
                    List.of("AI cannot confirm or post reconciliation automatically", "The bank statement line remains authoritative")),
            entry("clinic.collections.reminder.draft.v1", AiProductCode.CLINIC, AiTaskType.GENERIC_RECOMMENDATION,
                    "Draft a customer collections reminder using the supplied overdue clinic context. Return structured JSON with subject, body, suggestedActions, and limitations when possible.",
                    List.of("Review the reminder before sending", "Adjust tone if customer relationship risk is high", "Send only after authorized review"),
                    List.of("AI does not send emails autonomously", "Clinic users must confirm the final reminder")),
            entry("clinic.reconciliation.batch.summary.v1", AiProductCode.CLINIC, AiTaskType.SUMMARY,
                    "Summarize the reconciliation batch using the supplied statement, match, exception, and cash summary facts.",
                    List.of("Use the summary as a review aid", "Verify source data before acting"),
                    List.of("Summaries are advisory only")),
            entry("clinic.patient.summary.v1", AiProductCode.CLINIC, AiTaskType.PATIENT_HISTORY_SUMMARY,
                    "Summarize patient history with active conditions, medications, allergies, and follow-up risks.",
                    List.of("Verify the timeline against EHR records", "Prioritize allergy and chronic-condition interactions"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.clinical.document-extraction.v1", AiProductCode.CLINIC, AiTaskType.CLINICAL_DOCUMENT_EXTRACTION,
                    "Extract structured clinical findings from the supplied OCR text and document context. Return JSON with keys such as documentType, summary, diagnosesMentioned, medicines, allergies, chronicConditions, labValues, abnormalFindings, referralDoctor, referralHospital, followUpSuggestions, confidenceNotes, and reviewFlags. Do not diagnose; only extract and summarize what the source shows.",
                    List.of("Verify extracted facts against the source document", "Review any low-confidence or ambiguous fields before saving", "Keep the extracted data advisory until a clinician reviews it", "Highlight possible abnormal findings without claiming a diagnosis"),
                    List.of("Extraction output is advisory until reviewed", "OCR and provider limits may affect accuracy", "Do not auto-save critical clinical fields without clinician review")),
            entry("clinic.clinical.summary.v1", AiProductCode.CLINIC, AiTaskType.CLINICAL_SUMMARY,
                    "Summarize prior visits and chronic context in clinician-friendly language. Focus on previous visit summary, chronic history summary, recent consultation summary, medicine history, and uploaded report themes.",
                    List.of("Confirm the summary against the chart", "Use the summary as a review aid only", "Mention recurring conditions and follow-up gaps"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.structure-notes.v1", AiProductCode.CLINIC, AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    "Structure consultation notes into standardized medical sections.",
                    List.of("Review SOAP formatting and missing sections", "Confirm clinically relevant negatives"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.copilot.v1", AiProductCode.CLINIC, AiTaskType.CONSULTATION_COPILOT,
                    "Assist the doctor by suggesting possible diagnosis categories, investigation suggestions, and follow-up suggestions based on symptoms, findings, notes, and vitals. Return ONLY valid JSON with keys: summary, possibleDiagnosisCategories[{name,reason,confidence}], recommendedInvestigations, followUpSuggestions, safetyNotes. Do not include markdown. Do not include explanatory text before or after JSON. Do not finalize prescriptions or diagnoses.",
                    List.of("Review red flags and urgent exclusions", "Approve or reject each suggestion manually"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.suggest-diagnosis.v1", AiProductCode.CLINIC, AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                    """
                    Suggest possible differential diagnosis categories based on symptoms, findings, and context.
                    Return ONLY valid JSON. No markdown. No extra text.
                    Use exactly this shape:
                    {
                      "suggestions": [
                        {
                          "diagnosis": "Short name",
                          "reason": "One short sentence up to 140 chars",
                          "redFlags": ["short item 1", "short item 2"]
                        }
                      ],
                      "recommendedInvestigations": [],
                      "followUpSuggestions": [],
                      "safetyNote": "AI suggestions are assistive only."
                    }
                    Constraints:
                    - max 3 suggestions
                    - each suggestion reason <= 140 chars
                    - each suggestion redFlags max 3 items
                    - Do not return a top-level array
                    """,
                    List.of("Review red flags and urgent exclusions", "Use diagnostics to confirm before final diagnosis"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.prescription.suggest-template.v1", AiProductCode.CLINIC, AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                    "Suggest a prescription template using diagnosis, allergies, and current medications.",
                    List.of("Check contraindications and interactions", "Adjust dose and duration based on patient profile"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.patient.instructions.v1", AiProductCode.CLINIC, AiTaskType.PATIENT_INSTRUCTIONS_DRAFT,
                    "Draft patient-friendly instructions from diagnosis and prescription context.",
                    List.of("Confirm dosage schedule and warning signs", "Ensure language matches patient comprehension level"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.allergy.condition.warning.v1", AiProductCode.CLINIC, AiTaskType.ALLERGY_CONDITION_WARNING,
                    "Highlight possible allergy and pre-existing-condition warnings from supplied context.",
                    List.of("Review alerts against medication history", "Confirm severity and alternatives"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("generic.summary.v1", AiProductCode.GENERIC, AiTaskType.SUMMARY,
                    "Summarize the supplied business context.",
                    List.of("Use the summary as a decision aid", "Verify the underlying facts before action"),
                    List.of("AI summaries are advisory only")),
            entry("generic.classification.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_CLASSIFICATION,
                    "Classify the supplied business context into the most likely category.",
                    List.of("Use deterministic rules when available", "Review the classification before taking action"),
                    List.of("Classification is advisory only")),
            entry("generic.extraction.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_EXTRACTION,
                    "Extract structured information from the supplied business context.",
                    List.of("Validate extracted values", "Keep the output separate from the system of record"),
                    List.of("Extraction output must be reviewed")),
            entry("generic.recommendation.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_RECOMMENDATION,
                    "Provide a business recommendation from the supplied context.",
                    List.of("Use as a reviewer aid", "Confirm with deterministic application data"),
                    List.of("Recommendations are advisory only")),
            entry("generic.copilot.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                    "Act as a generic copilot for the supplied business context.",
                    List.of("Explain the situation clearly", "Offer concrete next steps", "Do not take irreversible action"),
                    List.of("AI output is advisory only"))
    );

    public AiPromptTemplateDefinition defaultDefinition(AiTaskType taskType, String templateCode) {
        AiPromptTemplateDefinition definition = templateCode == null ? null : defaults.get(templateCode);
        if (definition != null) {
            return definition;
        }
        return switch (taskType == null ? AiTaskType.GENERIC_COPILOT : taskType) {
            case CLINIC_EXTRACTION -> defaults.get("clinic.clinic.extraction.v1");
            case CLINIC_RISK_EXPLANATION -> defaults.get("clinic.clinic.risk.v1");
            case DUPLICATE_EXPLANATION -> defaults.get("clinic.clinic.duplicate.v1");
            case DOCTOR_RESUBMISSION_SUGGESTION -> defaults.get("clinic.clinic.doctor-resubmission.v1");
            case RECONCILIATION_EXCEPTION_EXPLANATION -> defaults.get("clinic.reconciliation.exception.explain.v1");
            case RECONCILIATION_MATCH_EXPLANATION -> defaults.get("clinic.reconciliation.match.explain.v1");
            case SUMMARY -> templateCode != null && templateCode.equals("clinic.reconciliation.batch.summary.v1")
                    ? defaults.get("clinic.reconciliation.batch.summary.v1")
                    : defaults.get("generic.summary.v1");
            case PATIENT_HISTORY_SUMMARY -> defaults.get("clinic.patient.summary.v1");
            case CLINICAL_DOCUMENT_EXTRACTION -> defaults.get("clinic.clinical.document-extraction.v1");
            case CLINICAL_SUMMARY -> defaults.get("clinic.clinical.summary.v1");
            case CONSULTATION_NOTE_STRUCTURING -> defaults.get("clinic.consultation.structure-notes.v1");
            case CONSULTATION_COPILOT -> defaults.get("clinic.consultation.copilot.v1");
            case SYMPTOMS_DIAGNOSIS_DRAFT -> defaults.get("clinic.consultation.suggest-diagnosis.v1");
            case PRESCRIPTION_TEMPLATE_SUGGESTION -> defaults.get("clinic.prescription.suggest-template.v1");
            case PATIENT_INSTRUCTIONS_DRAFT -> defaults.get("clinic.patient.instructions.v1");
            case ALLERGY_CONDITION_WARNING -> defaults.get("clinic.allergy.condition.warning.v1");
            case GENERIC_CLASSIFICATION -> defaults.get("generic.classification.v1");
            case GENERIC_EXTRACTION -> defaults.get("generic.extraction.v1");
            case GENERIC_RECOMMENDATION -> defaults.get("generic.recommendation.v1");
            case GENERIC_COPILOT -> defaults.get("generic.copilot.v1");
        };
    }

    private Map.Entry<String, AiPromptTemplateDefinition> entry(String templateCode, AiProductCode productCode,
                                                                AiTaskType taskType, String fallbackSummary,
                                                                List<String> fallbackSuggestedActions,
                                                                List<String> fallbackLimitations) {
        return Map.entry(templateCode, new AiPromptTemplateDefinition(
                templateCode,
                "v1",
                productCode,
                taskType,
                SYSTEM_PROMPT,
                USER_PROMPT,
                AiPromptTemplateStatus.ACTIVE,
                fallbackSummary,
                List.copyOf(fallbackSuggestedActions),
                List.copyOf(fallbackLimitations)
        ));
    }

    public Map<String, AiPromptTemplateDefinition> defaults() {
        return defaults;
    }
}
