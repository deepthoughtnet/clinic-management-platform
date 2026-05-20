update ai_prompt_templates
set system_prompt = 'You are a differential diagnosis assistant. Draft possibilities only, never final diagnosis.',
    user_prompt_template = $$
Suggest possible differential diagnosis categories based on symptoms, findings, and context.
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
