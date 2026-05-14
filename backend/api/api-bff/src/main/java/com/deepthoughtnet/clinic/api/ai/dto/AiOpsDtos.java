package com.deepthoughtnet.clinic.api.ai.dto;

import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiPromptVersionStatus;
import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowRunStatus;
import com.deepthoughtnet.clinic.ai.orchestration.platform.model.AiWorkflowStepStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs for AI Ops admin APIs. */
public final class AiOpsDtos {
    private AiOpsDtos() {}

    public record CreatePromptRequest(String promptKey, String name, String description, String domain,
                                      String useCase, Boolean systemPrompt) {}

    public record CreatePromptVersionRequest(String modelHint, BigDecimal temperature, Integer maxTokens,
                                             String systemPrompt, String userPromptTemplate,
                                             String variablesJson, String guardrailProfile) {}

    public record PromptDefinitionResponse(UUID id, UUID tenantId, String promptKey, String name,
                                           String description, String domain, String useCase,
                                           Integer activeVersion, boolean systemPrompt,
                                           OffsetDateTime updatedAt) {}

    public record PromptVersionResponse(UUID id, int version, AiPromptVersionStatus status,
                                        String modelHint, BigDecimal temperature, Integer maxTokens,
                                        String systemPrompt, String userPromptTemplate,
                                        String variablesJson, String guardrailProfile,
                                        OffsetDateTime createdAt, OffsetDateTime activatedAt) {}

    public record PromptDefinitionDetailResponse(PromptDefinitionResponse definition,
                                                 List<PromptVersionResponse> versions) {}

    public record InvocationLogResponse(UUID id, UUID requestId, String domain, String useCase,
                                        String promptKey, Integer promptVersion, String providerName,
                                        String modelName, String status, Long inputTokenCount,
                                        Long outputTokenCount, BigDecimal estimatedCost,
                                        Long latencyMs, String errorCode, String errorMessage,
                                        OffsetDateTime createdAt) {}

    public record UsageSummaryResponse(long totalCalls, long successfulCalls, long failedCalls,
                                       long inputTokens, long outputTokens, BigDecimal estimatedCost,
                                       long avgLatencyMs, Map<String, Long> callsByProvider,
                                       Map<String, Long> callsByUseCase,
                                       Map<String, Long> callsByStatus) {}

    public record ToolResponse(UUID id, UUID tenantId, String toolKey, String name, String description,
                               String category, boolean enabled, String riskLevel,
                               boolean requiresApproval, String inputSchemaJson,
                               String outputSchemaJson, OffsetDateTime updatedAt) {}

    public record GuardrailProfileResponse(UUID id, UUID tenantId, String profileKey, String name,
                                           String description, boolean enabled, String blockedTopicsJson,
                                           boolean piiRedactionEnabled, boolean humanApprovalRequired,
                                           Integer maxOutputTokens, OffsetDateTime updatedAt) {}

    public record WorkflowRunResponse(UUID id, String workflowKey, AiWorkflowRunStatus status,
                                      OffsetDateTime startedAt, OffsetDateTime completedAt,
                                      String failureReason, String inputSummary,
                                      String outputSummary) {}

    public record WorkflowStepResponse(UUID id, UUID workflowRunId, String stepName, String stepType,
                                       AiWorkflowStepStatus status, OffsetDateTime startedAt,
                                       OffsetDateTime completedAt, String providerName,
                                       String toolKey, String errorMessage) {}
}
