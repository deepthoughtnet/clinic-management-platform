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
