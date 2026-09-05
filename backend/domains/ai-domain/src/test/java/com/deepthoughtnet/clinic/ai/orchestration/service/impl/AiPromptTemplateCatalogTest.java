package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import org.junit.jupiter.api.Test;

class AiPromptTemplateCatalogTest {
    @Test
    void clinicalReasoningTemplateUsesCompactReasoningPromptOnly() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaultDefinition(AiTaskType.CLINICAL_REASONING, "clinic.clinical.reasoning.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).isEqualTo("{{input.reasoningPrompt}}");
        assertThat(userPromptTemplate).doesNotContain("inputVariablesJson");
        assertThat(userPromptTemplate).doesNotContain("evidenceSummary");
    }

    @Test
    void consultationAskTemplateUsesCompactCanonicalContextOnly() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaultDefinition(AiTaskType.GENERIC_COPILOT, "clinic.consultation.ask.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).contains("{{input.aiPromptContext}}");
        assertThat(userPromptTemplate).contains("{{input.prompt}}");
        assertThat(userPromptTemplate).contains("Be concise and clinically useful");
        assertThat(userPromptTemplate).contains("Prefer 3-5 key points");
        assertThat(userPromptTemplate).contains("Return plain text only");
        assertThat(userPromptTemplate).doesNotContain("Return ONLY valid JSON");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextJson");
        assertThat(userPromptTemplate).doesNotContain("inputVariablesJson");
    }

    @Test
    void consultationExplainDiagnosisTemplateUsesGroundedDiagnosisContext() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaultDefinition(AiTaskType.GENERIC_COPILOT, "clinic.consultation.explain-diagnosis.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).contains("{{input.prompt}}");
        assertThat(userPromptTemplate).contains("{{input.diagnosisExplanationContext}}");
        assertThat(userPromptTemplate).contains("{{input.clinicalContextSummary}}");
        assertThat(userPromptTemplate).contains("working diagnosis");
        assertThat(userPromptTemplate).contains("important alternative diagnoses");
        assertThat(userPromptTemplate).contains("If no diagnosis is recorded");
        assertThat(userPromptTemplate).doesNotContain("Return ONLY valid JSON");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextJson");
    }

    @Test
    void consultationHistoryGapTemplateUsesNarrowClinicalHistoryGapContext() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaultDefinition(AiTaskType.GENERIC_COPILOT, "clinic.consultation.history-gaps.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).contains("{{input.prompt}}");
        assertThat(userPromptTemplate).contains("{{input.historyGapContext}}");
        assertThat(userPromptTemplate).contains("{{input.clinicianReviewContext}}");
        assertThat(userPromptTemplate).contains("symptom duration");
        assertThat(userPromptTemplate).contains("respiratory red flags");
        assertThat(userPromptTemplate).contains("billing");
        assertThat(userPromptTemplate).contains("internal AI metadata");
        assertThat(userPromptTemplate).doesNotContain("{{input.aiPromptContext}}");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextJson");
        assertThat(userPromptTemplate).doesNotContain("Return ONLY valid JSON");
    }

    @Test
    void consultationSuggestTestsTemplateUsesGroundedInvestigationContextAndDuplicateAvoidanceRules() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaultDefinition(AiTaskType.GENERIC_COPILOT, "clinic.consultation.suggest-tests.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).contains("{{input.prompt}}");
        assertThat(userPromptTemplate).contains("{{input.investigationSuggestionContext}}");
        assertThat(userPromptTemplate).contains("already ordered, pending, or recently available");
        assertThat(userPromptTemplate).contains("Use the current symptoms, diagnosis, red flags, history, and missing data");
        assertThat(userPromptTemplate).contains("Prefer 2-5 focused suggestions");
        assertThat(userPromptTemplate).contains("Do not return generic filler such as \"consider investigations as needed\"");
        assertThat(userPromptTemplate).contains("Already ordered/pending");
        assertThat(userPromptTemplate).contains("Consider if indicated");
        assertThat(userPromptTemplate).contains("Not currently necessary / insufficient information");
        assertThat(userPromptTemplate).contains("brief rationale");
        assertThat(userPromptTemplate).contains("If evidence is insufficient to recommend a specific test");
        assertThat(userPromptTemplate).contains("Do not include billing, payment, lab-order logistics");
        assertThat(userPromptTemplate).contains("internal AI metadata");
        assertThat(userPromptTemplate).contains("Do not automatically create, select, or map a catalog test");
        assertThat(userPromptTemplate).doesNotContain("{{input.aiPromptContext}}");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextJson");
        assertThat(userPromptTemplate).doesNotContain("Return ONLY valid JSON");
    }

    @Test
    void prescriptionTemplateUsesCurrentEncounterOnlyAndConditionalSafetyRules() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        String userPromptTemplate = catalog.defaults().get("clinic.prescription.suggest-template.v1")
                .userPromptTemplate();

        assertThat(userPromptTemplate).contains("current consultation context below");
        assertThat(userPromptTemplate).contains("Do not use previous consultations");
        assertThat(userPromptTemplate).contains("current consultation summary");
        assertThat(userPromptTemplate).contains("Current consultation context:");
        assertThat(userPromptTemplate).contains("{{input.prescriptionSuggestionContext}}");
        assertThat(userPromptTemplate).contains("previous consultations, longitudinal memory, example text, cached output, or fallback clinical indications");
        assertThat(userPromptTemplate).contains("if allergy history is not recorded");
        assertThat(userPromptTemplate).contains("if current medicines are not recorded");
        assertThat(userPromptTemplate).contains("any patient-specific current medication assertion must be grounded");
        assertThat(userPromptTemplate).contains("do not introduce a new diagnosis or condition such as bacterial infection");
        assertThat(userPromptTemplate).contains("if a disease-specific indication is not supported by the current consultation evidence");
        assertThat(userPromptTemplate).contains("if the current context is insufficient");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextSummary");
        assertThat(userPromptTemplate).doesNotContain("clinicalContextJson");
    }

    @Test
    void consultationSoapTemplateRequiresMeaningfulSoapSectionsAndCanonicalContext() {
        AiPromptTemplateCatalog catalog = new AiPromptTemplateCatalog();

        var definition = catalog.defaultDefinition(AiTaskType.CONSULTATION_NOTE_STRUCTURING, "clinic.consultation.structure-notes.v1");
        assertThat(definition.systemPrompt()).contains("SOAP notes");
        assertThat(definition.userPromptTemplate()).contains("{{input.soapClinicalContext}}");
        assertThat(definition.userPromptTemplate()).contains("Chief complaint");
        assertThat(definition.userPromptTemplate()).contains("Diagnosis");
        assertThat(definition.userPromptTemplate()).contains("Advice / plan");
        assertThat(definition.userPromptTemplate()).contains("SOAP content style:");
        assertThat(definition.userPromptTemplate()).contains("Subjective: one coherent paragraph with concise history");
        assertThat(definition.userPromptTemplate()).contains("Objective: observable findings only");
        assertThat(definition.userPromptTemplate()).contains("Assessment: short, diagnosis-oriented clinical assessment");
        assertThat(definition.userPromptTemplate()).contains("Plan: action-oriented bullet points using short lines or bullet-style sentences");
        assertThat(definition.userPromptTemplate()).contains("do not add invented negatives or explanations");
        assertThat(definition.userPromptTemplate()).contains("do not write explanatory paragraphs");
        assertThat(definition.userPromptTemplate()).contains("Do not use \"-\" or placeholder-only content");
        assertThat(definition.userPromptTemplate()).contains("Do not include clinical reasoning narratives");
        assertThat(definition.userPromptTemplate()).contains("Return ONLY valid JSON");
        assertThat(definition.userPromptTemplate()).contains("\"subjective\"");
        assertThat(definition.userPromptTemplate()).contains("\"objective\"");
        assertThat(definition.userPromptTemplate()).contains("\"assessment\"");
        assertThat(definition.userPromptTemplate()).contains("\"plan\"");
        assertThat(definition.userPromptTemplate()).doesNotContain("Canonical clinical context JSON");
        assertThat(definition.userPromptTemplate()).doesNotContain("Canonical consultation context");
    }
}
