package com.deepthoughtnet.clinic.api.ai;

import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.CreatePromptRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.CreatePromptVersionRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.GuardrailProfileResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.InvocationLogResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.PromptDefinitionDetailResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.PromptDefinitionResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.PromptVersionResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.ToolResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.UsageSummaryResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.WorkflowRunResponse;
import com.deepthoughtnet.clinic.api.ai.dto.AiOpsDtos.WorkflowStepResponse;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiInvocationLogService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiGuardrailProfileQueryService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiPromptRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiToolRegistryService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiUsageSummaryService;
import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiWorkflowLogService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration APIs for hardened AI orchestration platform visibility and prompt management.
 */
@RestController
@RequestMapping("/api/ai")
public class AiOpsController {
    private final AiPromptRegistryService promptRegistryService;
    private final AiInvocationLogService invocationLogService;
    private final AiUsageSummaryService usageSummaryService;
    private final AiToolRegistryService toolRegistryService;
    private final AiWorkflowLogService workflowLogService;
    private final AiGuardrailProfileQueryService guardrailProfileQueryService;

    public AiOpsController(AiPromptRegistryService promptRegistryService,
                           AiInvocationLogService invocationLogService,
                           AiUsageSummaryService usageSummaryService,
                           AiToolRegistryService toolRegistryService,
                           AiWorkflowLogService workflowLogService,
                           AiGuardrailProfileQueryService guardrailProfileQueryService) {
        this.promptRegistryService = promptRegistryService;
        this.invocationLogService = invocationLogService;
        this.usageSummaryService = usageSummaryService;
        this.toolRegistryService = toolRegistryService;
        this.workflowLogService = workflowLogService;
        this.guardrailProfileQueryService = guardrailProfileQueryService;
    }

    @GetMapping("/prompts")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<PromptDefinitionResponse> prompts() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return promptRegistryService.list(tenantId).stream().map(this::toPromptDefinition).toList();
    }

    @GetMapping("/prompts/{id}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PromptDefinitionDetailResponse prompt(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toPromptDetail(promptRegistryService.get(tenantId, id));
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PromptDefinitionResponse createPrompt(@RequestBody CreatePromptRequest request) {
        var ctx = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toPromptDefinition(promptRegistryService.create(
                tenantId,
                new AiPromptRegistryService.PromptUpsertCommand(
                        request.promptKey(),
                        request.name(),
                        request.description(),
                        request.domain(),
                        request.useCase(),
                        Boolean.TRUE.equals(request.systemPrompt())
                ),
                ctx.appUserId()
        ));
    }

    @PostMapping("/prompts/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PromptVersionResponse createVersion(@PathVariable UUID id, @RequestBody CreatePromptVersionRequest request) {
        var ctx = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toPromptVersion(promptRegistryService.createVersion(
                tenantId,
                id,
                new AiPromptRegistryService.PromptVersionCreateCommand(
                        request.modelHint(),
                        request.temperature(),
                        request.maxTokens(),
                        request.systemPrompt(),
                        request.userPromptTemplate(),
                        request.variablesJson(),
                        request.guardrailProfile()
                ),
                ctx.appUserId()
        ));
    }

    @PostMapping("/prompts/{id}/versions/{versionId}/activate")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PromptDefinitionDetailResponse activateVersion(@PathVariable UUID id, @PathVariable UUID versionId) {
        var ctx = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toPromptDetail(promptRegistryService.activateVersion(tenantId, id, versionId, ctx.appUserId()));
    }

    @PostMapping("/prompts/{id}/versions/{versionId}/archive")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public PromptDefinitionDetailResponse archiveVersion(@PathVariable UUID id, @PathVariable UUID versionId) {
        var ctx = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toPromptDetail(promptRegistryService.archiveVersion(tenantId, id, versionId, ctx.appUserId()));
    }

    @GetMapping("/invocations")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<InvocationLogResponse> invocations() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return invocationLogService.recent(tenantId).stream().map(this::toInvocation).toList();
    }

    @GetMapping("/usage/summary")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public UsageSummaryResponse usage(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String useCase
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var summary = usageSummaryService.summarize(tenantId, parseDate(fromDate), parseDate(toDate), provider, useCase);
        return new UsageSummaryResponse(
                summary.totalCalls(),
                summary.successfulCalls(),
                summary.failedCalls(),
                summary.inputTokens(),
                summary.outputTokens(),
                summary.estimatedCost(),
                summary.avgLatencyMs(),
                summary.callsByProvider(),
                summary.callsByUseCase(),
                summary.callsByStatus()
        );
    }

    @GetMapping("/tools")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<ToolResponse> tools() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toolRegistryService.list(tenantId).stream().map(row -> new ToolResponse(
                row.id(), row.tenantId(), row.toolKey(), row.name(), row.description(), row.category(),
                row.enabled(), row.riskLevel(), row.requiresApproval(), row.inputSchemaJson(),
                row.outputSchemaJson(), row.updatedAt()
        )).toList();
    }

    @GetMapping("/guardrails")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<GuardrailProfileResponse> guardrails() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return guardrailProfileQueryService.list(tenantId).stream().map(row -> new GuardrailProfileResponse(
                row.id(), row.tenantId(), row.profileKey(), row.name(), row.description(), row.enabled(),
                row.blockedTopicsJson(), row.piiRedactionEnabled(), row.humanApprovalRequired(),
                row.maxOutputTokens(), row.updatedAt()
        )).toList();
    }

    @GetMapping("/workflows/runs")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<WorkflowRunResponse> workflowRuns() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return workflowLogService.listRuns(tenantId).stream().map(row -> new WorkflowRunResponse(
                row.id(), row.workflowKey(), row.status(), row.startedAt(), row.completedAt(),
                row.failureReason(), row.inputSummary(), row.outputSummary()
        )).toList();
    }

    @GetMapping("/workflows/runs/{runId}/steps")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<WorkflowStepResponse> workflowSteps(@PathVariable UUID runId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return workflowLogService.listSteps(tenantId, runId).stream().map(row -> new WorkflowStepResponse(
                row.id(), row.workflowRunId(), row.stepName(), row.stepType(), row.status(),
                row.startedAt(), row.completedAt(), row.providerName(), row.toolKey(), row.errorMessage()
        )).toList();
    }

    private PromptDefinitionResponse toPromptDefinition(AiPromptRegistryService.PromptDefinitionRecord row) {
        return new PromptDefinitionResponse(
                row.id(), row.tenantId(), row.promptKey(), row.name(), row.description(), row.domain(),
                row.useCase(), row.activeVersion(), row.systemPrompt(), row.updatedAt()
        );
    }

    private PromptVersionResponse toPromptVersion(AiPromptRegistryService.PromptVersionRecord row) {
        return new PromptVersionResponse(
                row.id(), row.version(), row.status(), row.modelHint(), row.temperature(), row.maxTokens(),
                row.systemPrompt(), row.userPromptTemplate(), row.variablesJson(), row.guardrailProfile(),
                row.createdAt(), row.activatedAt()
        );
    }

    private PromptDefinitionDetailResponse toPromptDetail(AiPromptRegistryService.PromptDefinitionDetail row) {
        return new PromptDefinitionDetailResponse(
                toPromptDefinition(row.definition()),
                row.versions().stream().map(this::toPromptVersion).toList()
        );
    }

    private InvocationLogResponse toInvocation(AiInvocationLogService.InvocationLogRecord row) {
        return new InvocationLogResponse(
                row.id(), row.requestId(), row.domain(), row.useCase(), row.promptKey(), row.promptVersion(),
                row.providerName(), row.modelName(), row.status(), row.inputTokenCount(), row.outputTokenCount(),
                row.estimatedCost(), row.latencyMs(), row.errorCode(), row.errorMessage(), row.createdAt()
        );
    }

    private OffsetDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }
}
