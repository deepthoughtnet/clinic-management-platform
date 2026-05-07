insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    '14f1d2ac-0671-41fa-8188-62fef05b5761',
    'CLINIC',
    null,
    'clinic.patient.summary.v1',
    'v1',
    'PATIENT_HISTORY_SUMMARY',
    'You are a clinical assistant. Draft only. Never provide final diagnosis or autonomous orders.',
    'Create a concise patient history summary from the supplied structured context. Include active conditions, medication overview, allergies, and immediate clinical cautions. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    '5a93c4ae-0cb4-433b-83f8-cad4288408b2',
    'CLINIC',
    null,
    'clinic.consultation.structure-notes.v1',
    'v1',
    'CONSULTATION_NOTE_STRUCTURING',
    'You are a clinical documentation assistant. Draft only. Do not make treatment decisions.',
    'Convert free-text consultation notes into structured sections: chief complaint, history, examination findings, assessment, and plan. Keep unknown fields explicit. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    '40ff8a31-fcca-411a-88f7-32ea74da08f2',
    'CLINIC',
    null,
    'clinic.consultation.suggest-diagnosis.v1',
    'v1',
    'SYMPTOMS_DIAGNOSIS_DRAFT',
    'You are a differential diagnosis assistant. Draft possibilities only, never final diagnosis.',
    'From the symptoms and findings, suggest possible differential diagnoses with brief reasoning and red-flag exclusions. Do not claim certainty. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    '47f0d5b4-3942-474d-baf9-53a2ba4bca17',
    'CLINIC',
    null,
    'clinic.prescription.suggest-template.v1',
    'v1',
    'PRESCRIPTION_TEMPLATE_SUGGESTION',
    'You are a prescription drafting assistant. Draft suggestions only.',
    'Suggest a prescription template using diagnosis, allergies, and active medications. Include checks for contraindications and interactions. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    'f3f79f22-adf2-40ad-b0c7-307cc9dacb5f',
    'CLINIC',
    null,
    'clinic.patient.instructions.v1',
    'v1',
    'PATIENT_INSTRUCTIONS_DRAFT',
    'You are a patient communication assistant. Draft patient-safe instructions only.',
    'Draft patient-friendly instructions in simple language with medicine timing, warning signs, and follow-up cues. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;

insert into ai_prompt_templates (id, product_code, tenant_id, template_code, version, task_type, system_prompt, user_prompt_template, status, created_at, updated_at)
values (
    'f786f383-01ec-476d-ba8a-e0cf4c590741',
    'CLINIC',
    null,
    'clinic.allergy.condition.warning.v1',
    'v1',
    'ALLERGY_CONDITION_WARNING',
    'You are a safety warning assistant. Draft warnings only.',
    'Identify possible allergy and condition-related warnings from the provided notes and medication context. This is an AI-generated draft. Doctor must verify before use. Input: {{inputVariablesJson}}',
    'ACTIVE',
    now(),
    now()
)
on conflict (id) do nothing;
