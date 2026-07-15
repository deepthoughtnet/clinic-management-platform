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
}
