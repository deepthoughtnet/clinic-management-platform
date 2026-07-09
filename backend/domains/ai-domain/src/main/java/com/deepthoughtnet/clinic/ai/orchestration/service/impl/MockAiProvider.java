package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${clinic.ai.enabled:false}' == 'true' "
                + "&& '${clinic.ai.mock.enabled:true}' == 'true'"
)
public class MockAiProvider implements AiProvider {
    private static final String MODEL = "mock-clinic-ai";

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType != null;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        String output = request != null
                && request.request() != null
                && request.request().taskType() == AiTaskType.CLINICAL_REASONING
                ? """
                {
                  "confidence": "LOW",
                  "primaryDiagnosis": {
                    "name": "Viral Upper Respiratory Infection",
                    "confidence": 0.55,
                    "status": "SUGGESTED",
                    "whyConsidered": "Fever, cough, body ache and weakness fit a viral syndrome.",
                    "whyLessLikely": "This is a mock fallback and must be verified by a doctor.",
                    "supportingEvidence": [],
                    "contradictingEvidence": [],
                    "missingInformation": [],
                    "recommendedTests": [],
                    "redFlags": []
                  },
                  "differentialDiagnoses": [
                    {
                      "name": "Influenza",
                      "confidence": 0.35,
                      "whyConsidered": "Acute viral respiratory symptoms with fever.",
                      "whyLessLikely": "No specific influenza features provided.",
                      "recommendedTests": []
                    }
                  ],
                  "supportingEvidence": [],
                  "contradictingEvidence": [],
                  "missingInformation": [],
                  "redFlags": [],
                  "recommendedTests": [],
                  "reasoningSummary": "Mock clinical reasoning fallback. Doctor must verify.",
                  "safetyNotes": [
                    {
                      "message": "Mock fallback active. Clinical reasoning should be verified manually.",
                      "severity": "LOW"
                    }
                  ],
                  "followUpAdvice": [
                    "Review the consultation with a clinician."
                  ],
                  "patientExplanation": "Mock fallback response for local validation only.",
                  "sourceContextSummary": {
                    "chiefComplaint": "",
                    "symptoms": [],
                    "vitals": "",
                    "knownConditions": [],
                    "recentReports": [],
                    "currentMedicines": []
                  },
                  "metadata": {
                    "promptVersion": "clinic.clinical.reasoning.v1",
                    "contextVersion": "v1",
                    "provider": "MOCK",
                    "model": "mock-clinic-ai",
                    "tokens": {},
                    "parseStatus": "VALID"
                  }
                }
                """
                : """
                {
                  "summary": "Mock AI provider is active. No external model was called.",
                  "suggestions": [
                    "Configure Gemini for live AI output when ready.",
                    "AI suggestions are assistive only and must be clinically reviewed."
                  ],
                  "limitations": [
                    "Mock output is for local development only.",
                    "No autonomous diagnosis or prescription finalization was performed."
                  ]
                }
                """;
        return new AiProviderResponse(
                providerName(),
                MODEL,
                output,
                output,
                new BigDecimal("0.30"),
                null,
                "STOP",
                "COMPLETE",
                output.length(),
                output,
                "UNKNOWN"
        );
    }

    @Override
    public AiProviderStatus status() {
        return AiProviderStatus.AVAILABLE;
    }
}
