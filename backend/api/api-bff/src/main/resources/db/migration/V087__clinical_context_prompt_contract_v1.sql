update ai_prompt_templates
set system_prompt = 'You are a clinical assistant. Draft only. Never provide final diagnosis or autonomous orders.',
    user_prompt_template = $$
Create a concise patient history summary from the supplied structured context.
Use the supplied clinical context summary and clinical context JSON as the primary source of truth.
Include active conditions, medication overview, allergies, and immediate clinical cautions.
This is an AI-generated draft. Doctor must verify before use.
Input: {{inputVariablesJson}}
$$,
    updated_at = now()
where template_code = 'clinic.patient.summary.v1';

update ai_prompt_templates
set system_prompt = 'You are a clinical documentation assistant. Draft only. Do not make treatment decisions.',
    user_prompt_template = $$
Convert free-text consultation notes into structured sections: chief complaint, history, examination findings, assessment, and plan.
Use the supplied clinical context summary and clinical context JSON as the primary source of truth.
Keep unknown fields explicit.
This is an AI-generated draft. Doctor must verify before use.
Input: {{inputVariablesJson}}
$$,
    updated_at = now()
where template_code = 'clinic.consultation.structure-notes.v1';

update ai_prompt_templates
set system_prompt = 'You are a clinical copilot. Draft possibilities only, never final diagnosis.',
    user_prompt_template = $$
Assist the doctor by suggesting possible diagnosis categories, investigation suggestions, and follow-up suggestions based on symptoms, findings, notes, vitals, and the supplied clinical context summary and JSON.
Return ONLY valid JSON with keys: summary, possibleDiagnosisCategories[{name,reason,confidence}], recommendedInvestigations, followUpSuggestions, safetyNotes.
Do not include markdown. Do not include explanatory text before or after JSON.
Do not finalize prescriptions or diagnoses.
$$,
    updated_at = now()
where template_code = 'clinic.consultation.copilot.v1';

update ai_prompt_templates
set system_prompt = 'You are a differential diagnosis assistant. Draft possibilities only, never final diagnosis.',
    user_prompt_template = $$
Suggest possible differential diagnosis categories based on symptoms, findings, and context.
Use the supplied clinical context summary and clinical context JSON as the primary source of truth.
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
$$,
    updated_at = now()
where template_code = 'clinic.consultation.suggest-diagnosis.v1';

update ai_prompt_templates
set system_prompt = 'You are a prescription drafting assistant. Draft suggestions only.',
    user_prompt_template = $$
Suggest a prescription template using diagnosis, allergies, and current medications.
Use the supplied clinical context summary and clinical context JSON as the primary source of truth.
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
$$,
    updated_at = now()
where template_code = 'clinic.prescription.suggest-template.v1';

update ai_prompt_templates
set system_prompt = 'You are a patient communication assistant. Draft patient-safe instructions only.',
    user_prompt_template = $$
Draft patient-friendly instructions in simple language with medicine timing, warning signs, and follow-up cues.
Use the supplied clinical context summary and clinical context JSON as the primary source of truth.
This is an AI-generated draft. Doctor must verify before use.
Input: {{inputVariablesJson}}
$$,
    updated_at = now()
where template_code = 'clinic.patient.instructions.v1';
