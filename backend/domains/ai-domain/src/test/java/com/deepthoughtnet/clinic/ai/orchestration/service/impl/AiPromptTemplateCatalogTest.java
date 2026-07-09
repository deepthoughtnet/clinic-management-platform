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
}
