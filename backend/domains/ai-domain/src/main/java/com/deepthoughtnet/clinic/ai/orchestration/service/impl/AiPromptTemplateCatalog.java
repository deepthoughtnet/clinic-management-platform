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
            Clinical context summary:
            {{input.clinicalContextSummary}}
            Clinical context JSON:
            {{input.clinicalContextJson}}
            Input variables JSON:
            {{inputVariablesJson}}
            Evidence:
            {{evidenceSummary}}

            Answer the user's business question. Be advisory only. Do not mutate workflow state.
            Return ONLY valid JSON. Do not include markdown. Do not include explanatory text before or after JSON.
            """;

    private static final String RESPONSE_COMPOSER_SYSTEM_PROMPT = """
            You rewrite patient portal CareAI responses for speech.
            You are a professional clinic assistant, warm but concise.
            Deterministic workflow output is authoritative.
            Preserve all supplied facts exactly.
            Never invent doctors, dates, times, slots, appointments, prices, or clinic names.
            Do not give medical advice.
            Return plain text only.
            """;

    private static final String RESPONSE_COMPOSER_USER_PROMPT = """
            Product: {{productCode}}
            Task: {{taskType}}
            Use case: {{useCaseCode}}
            Prompt template: {{promptTemplateCode}}
            Tenant: {{tenantId}}
            Actor: {{actorUserId}}
            Correlation: {{correlationId}}
            Language: {{input.language}}
            Response type: {{input.responseType}}
            Workflow: {{input.workflow}}
            Raw response text:
            {{input.rawResponseText}}

            Safe structured facts JSON:
            {{input.safeStructuredFactsJson}}

            Rewrite the raw response into a professional clinic assistant voice response.
            For hi-IN, use natural Hindi or Hinglish.
            Add a greeting only if the user greeted or this is clearly the first turn.
            Keep the response concise.
            Preserve all facts exactly.
            Do not invent or change slots, doctors, appointments, dates, times, clinic names, or prices.
            Keep confirmation prompts explicit.
            If the raw response is an error, keep it clear and polite.
            Return only the final spoken text.
            """;

    private static final String CONSULTATION_ASK_USER_PROMPT = """
            Product: {{productCode}}
            Task: {{taskType}}
            Use case: {{useCaseCode}}
            Prompt template: {{promptTemplateCode}}
            Tenant: {{tenantId}}
            Actor: {{actorUserId}}
            Correlation: {{correlationId}}

            User question:
            {{input.prompt}}

            Canonical consultation context:
            {{input.aiPromptContext}}

            Answer the doctor's exact question using only the canonical consultation context.
            Be concise and clinically useful.
            Prefer 3-5 key points when summarizing trends.
            Do not restate the full patient context or every report.
            Distinguish verified clinical data from pending-review AI extracted data.
            Do not invent missing information.
            Be advisory only. Do not mutate workflow state.
            Return plain text only. Do not include JSON, markdown, or code fences.
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
                    "Summarize patient history with active conditions, medications, allergies, and follow-up risks. Use the supplied clinical context summary and JSON as the primary source of truth.",
                    List.of("Verify the timeline against EHR records", "Prioritize allergy and chronic-condition interactions"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.clinical.document-extraction.v1", AiProductCode.CLINIC, AiTaskType.CLINICAL_DOCUMENT_EXTRACTION,
                    """
                    Extract structured clinical findings from the supplied OCR text and document context.
                    Return ONLY strict JSON. No markdown. No prose outside JSON.
                    Keep factual findings separate from summary or recommendations.
                    Do not return an answer wrapper.
                    Do not return a classification wrapper.
                    Use exactly this shape:
                    {
                      "documentType": "EXTERNAL_LAB_REPORT",
                      "reportDate": "2026-01-08",
                      "factualFindings": {
                        "labResults": [
                          {
                            "testName": "HbA1c",
                            "canonicalKey": "hba1c",
                            "value": "8.4",
                            "unit": "%",
                            "referenceRange": "< 5.7 normal; > 6.5 diabetic",
                            "flag": "HIGH",
                            "evidenceText": "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"
                          }
                        ],
                        "conditions": [
                          {
                            "canonicalKey": "diabetes_mellitus",
                            "label": "Diabetes Mellitus",
                            "evidenceText": "Known diabetic"
                          }
                        ],
                        "riskFlags": [
                          {
                            "canonicalKey": "lipid_risk",
                            "label": "Dyslipidemia",
                            "evidenceText": "Total Cholesterol 228 mg/dL < 200 High"
                          }
                        ]
                      },
                      "summary": "Brief factual summary only.",
                      "recommendations": [],
                      "limitations": [],
                      "confidence": "HIGH"
                    }
                    Map only direct factual findings from the source.
                    If the document contains lab values, populate factualFindings.labResults directly at the top level.
                    Do not nest lab values under answer.classification or any other wrapper.
                    Do not put recommendations, suggested actions, follow-up advice, or narrative plan text inside factualFindings.
                    For every lab result, ensure evidenceText contains the same test label and value.
                    Do not diagnose; only extract and summarize what the source shows.
                    """,
                    List.of("Verify extracted facts against the source document", "Review any low-confidence or ambiguous fields before saving", "Keep the extracted data advisory until a clinician reviews it", "Highlight possible abnormal findings without claiming a diagnosis"),
                    List.of("Extraction output is advisory until reviewed", "OCR and provider limits may affect accuracy", "Do not auto-save critical clinical fields without clinician review")),
            entry("clinic.clinical.summary.v1", AiProductCode.CLINIC, AiTaskType.CLINICAL_SUMMARY,
                    "Summarize prior visits and chronic context in clinician-friendly language. Use the supplied clinical context summary and JSON as the primary source of truth. Focus on previous visit summary, chronic history summary, recent consultation summary, medicine history, and uploaded report themes.",
                    List.of("Confirm the summary against the chart", "Use the summary as a review aid only", "Mention recurring conditions and follow-up gaps"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.clinical.reasoning.v1", AiProductCode.CLINIC, AiTaskType.CLINICAL_REASONING,
                    SYSTEM_PROMPT,
                    "{{input.reasoningPrompt}}",
                    "Brief clinician-friendly reasoning draft.",
                    List.of("Check for emergency red flags before suggesting urgent diagnoses", "Use longitudinal memory and reports as evidence", "Always mention uncertainty and missing information"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.structure-notes.v1", AiProductCode.CLINIC, AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                    """
                    You structure consultation notes into SOAP notes.
                    Return ONLY valid JSON. No markdown. No prose outside JSON.
                    Use the supplied canonical clinical context as the primary source of truth.
                    Prefer current visit evidence first, then longitudinal history, labs, and verified context.
                    Do not invent facts. Do not return placeholder-only sections.
                    If a section genuinely lacks evidence, use "Not documented" only when necessary.
                    Keep each section clinically meaningful, concise, and clinician-facing.
                    Write like an experienced physician documenting the visit.
                    Do not repeat the same complaint, symptom, diagnosis, or plan item multiple times.
                    Do not include conversational language, AI disclaimers, or internal reasoning.
                    """,
                    """
                    Product: {{productCode}}
                    Task: {{taskType}}
                    Use case: {{useCaseCode}}
                    Prompt template: {{promptTemplateCode}}
                    Tenant: {{tenantId}}
                    Actor: {{actorUserId}}
                    Correlation: {{correlationId}}

                    Chief complaint:
                    {{input.chiefComplaint}}

                    Symptoms:
                    {{input.symptoms}}

                    Diagnosis:
                    {{input.diagnosis}}

                    Advice / plan:
                    {{input.advice}}

                    Clinical notes / observations:
                    {{input.observations}}

                    Vitals:
                    {{input.vitals}}

                    Allergies:
                    {{input.allergies}}

                    Chronic conditions:
                    {{input.chronicConditions}}

                    Lab orders summary:
                    {{input.labOrdersSummary}}

                    Current prescription draft:
                    {{input.currentPrescriptionDraft}}

                    SOAP clinical context:
                    {{input.soapClinicalContext}}

                    Write SOAP sections with exactly these keys:
                    {"subjective":"...","objective":"...","assessment":"...","plan":"..."}

                    SOAP content style:
                    - Subjective: one coherent paragraph with concise history, no duplicated complaint sentences, no AI wording.
                    - Objective: observable findings only, especially vitals and exam/lab findings; do not add invented negatives or explanations.
                    - Assessment: short, diagnosis-oriented clinical assessment; do not write explanatory paragraphs.
                    - Plan: action-oriented bullet points using short lines or bullet-style sentences.
                    - Do not include clinical reasoning narratives, medication safety reasoning, duplicate-medication commentary, or investigation recommendation narratives.

                    Requirements:
                    - Return ONLY valid JSON. No markdown. No prose outside JSON.
                    - Subjective, Objective, Assessment, and Plan must be present.
                    - Each section should be meaningful when supporting information exists.
                    - Use current complaint, symptoms, vitals, diagnosis, notes, advice, history, labs, and relevant longitudinal findings.
                    - Do not use "-" or placeholder-only content.
                    - Do not invent treatment decisions or diagnosis details not supported by the context.
                    - Use "Not documented" only if a section truly has no evidence.
                    - Preserve the distinction between current visit evidence and historical context.
                    - Keep the output concise and ready for doctor review.
                    """,
                    "Structure consultation notes into SOAP sections using canonical context.",
                    List.of("Review SOAP formatting and missing sections", "Confirm clinically relevant negatives"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.copilot.v1", AiProductCode.CLINIC, AiTaskType.CONSULTATION_COPILOT,
                    "Assist the doctor by suggesting possible diagnosis categories, investigation suggestions, and follow-up suggestions based on symptoms, findings, notes, vitals, and the supplied clinical context summary and JSON. Return ONLY valid JSON with keys: summary, possibleDiagnosisCategories[{name,reason,confidence}], recommendedInvestigations, followUpSuggestions, safetyNotes. Do not include markdown. Do not include explanatory text before or after JSON. Do not finalize prescriptions or diagnoses.",
                    List.of("Review red flags and urgent exclusions", "Approve or reject each suggestion manually"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.consultation.suggest-diagnosis.v1", AiProductCode.CLINIC, AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT,
                    """
                    Suggest possible differential diagnosis categories based on symptoms, findings, and context.
                    Use the supplied clinical context summary and JSON as the primary source of truth.
                    Return ONLY valid JSON. No markdown. No extra text.
                    Use exactly this shape:
                    {
                      "suggestions": [
                        {
                          "diagnosis": "Short name",
                          "reason": "One short sentence",
                          "redFlags": ["short item 1", "short item 2"],
                          "recommendedInvestigations": ["short item 1"],
                          "followUpSuggestions": ["short item 1"]
                        }
                      ],
                      "safetyNote": "AI suggestions are assistive only."
                    }
                    Constraints:
                    - max 3 suggestions
                    - each suggestion redFlags max 3 items
                    - each suggestion recommendedInvestigations max 3 items
                    - each suggestion followUpSuggestions max 3 items
                    - Do not return a top-level array
                    """,
                    List.of("Review red flags and urgent exclusions", "Use diagnostics to confirm before final diagnosis"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.prescription.suggest-template.v1", AiProductCode.CLINIC, AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION,
                    """
                    Suggest a prescription template using diagnosis, allergies, and current medications.
                    Use the supplied clinical context summary and JSON as the primary source of truth.
                    Return ONLY valid JSON. No markdown. No extra text.
                    Use exactly this shape:
                    {
                      "suggestions": [
                        {
                          "medicine": "Medicine name",
                          "dose": "Dose",
                          "frequency": "Frequency",
                          "duration": "Duration",
                          "reason": "Short reason",
                          "safetyNote": "Doctor review required"
                        }
                      ],
                      "safetyNote": "AI suggestions are assistive only."
                    }
                    Constraints:
                    - max 5 suggestions
                    - highlight allergies or interaction concerns when relevant
                    - do not auto-prescribe or finalize anything
                    - do not return a top-level array
                    """,
                    List.of("Check contraindications and interactions", "Adjust dose and duration based on patient profile"),
                    List.of("This is an AI-generated draft. Doctor must verify before use.")),
            entry("clinic.patient.instructions.v1", AiProductCode.CLINIC, AiTaskType.PATIENT_INSTRUCTIONS_DRAFT,
                    "Draft patient-friendly instructions from diagnosis, prescription context, and the supplied clinical context summary and JSON.",
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
            entry("patient.portal.careai.response.composer.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                    RESPONSE_COMPOSER_SYSTEM_PROMPT,
                    RESPONSE_COMPOSER_USER_PROMPT,
                    "Rewrite patient portal CareAI responses for speech.",
                    List.of("Preserve all confirmed facts", "Keep confirmation prompts explicit", "Do not invent or mutate workflow outcomes"),
                    List.of("Speech rewrite is advisory only", "Deterministic CareAI state remains authoritative")),
            entry("generic.copilot.v1", AiProductCode.GENERIC, AiTaskType.GENERIC_COPILOT,
                    "Act as a generic copilot for the supplied business context.",
                    List.of("Explain the situation clearly", "Offer concrete next steps", "Do not take irreversible action"),
                    List.of("AI output is advisory only")),
            entry("clinic.consultation.ask.v1", AiProductCode.CLINIC, AiTaskType.GENERIC_COPILOT,
                    "Act as a consultation chat assistant using the supplied canonical consultation context.",
                    CONSULTATION_ASK_USER_PROMPT,
                    "Clinical chat answer using canonical consultation context.",
                    List.of("Explain the answer clearly", "Summarize relevant longitudinal trends", "Do not mutate workflow state"),
                    List.of("Consultation chat output is advisory only"))
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
            case CLINICAL_REASONING -> defaults.get("clinic.clinical.reasoning.v1");
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
        return entry(templateCode, productCode, taskType, SYSTEM_PROMPT, USER_PROMPT, fallbackSummary, fallbackSuggestedActions, fallbackLimitations);
    }

    private Map.Entry<String, AiPromptTemplateDefinition> entry(String templateCode, AiProductCode productCode,
                                                                AiTaskType taskType, String systemPrompt,
                                                                String userPromptTemplate, String fallbackSummary,
                                                                List<String> fallbackSuggestedActions,
                                                                List<String> fallbackLimitations) {
        return Map.entry(templateCode, new AiPromptTemplateDefinition(
                templateCode,
                "v1",
                productCode,
                taskType,
                systemPrompt,
                userPromptTemplate,
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
