package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.CreateExecutionRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.DeliveryAttemptResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionCreateCommand;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CarePilot execution history APIs. */
@RestController
@RequestMapping("/api/carepilot/executions")
public class CarePilotExecutionController {
    private final CampaignExecutionService executionService;

    public CarePilotExecutionController(CampaignExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<ExecutionResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return executionService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/failed")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    /** Lists executions that are in terminal failure/dead-letter states. */
    public List<ExecutionResponse> listFailed() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return executionService.listFailed(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{executionId}/attempts")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    /** Lists per-attempt delivery audit history for one execution. */
    public List<DeliveryAttemptResponse> listAttempts(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return executionService.listAttempts(tenantId, executionId).stream()
                .map(a -> new DeliveryAttemptResponse(
                        a.id(), a.tenantId(), a.executionId(), a.attemptNumber(),
                        a.providerName(), a.channelType(), a.deliveryStatus(),
                        a.errorCode(), a.errorMessage(), a.attemptedAt()
                ))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse create(@RequestBody CreateExecutionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(executionService.create(tenantId, new CampaignExecutionCreateCommand(
                request.campaignId(), request.templateId(), request.channelType(), request.recipientPatientId(), request.scheduledAt()
        )));
    }

    @PatchMapping("/{executionId}/retry")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    /** Requeues a failed execution so scheduler can retry delivery. */
    public ExecutionResponse retry(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(executionService.retryExecution(tenantId, executionId));
    }

    @PatchMapping("/{executionId}/resend")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    /** Alias endpoint for retry to provide an explicit resend action. */
    public ExecutionResponse resend(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        // Resend aliases retry in v1 so admins have an explicit resend affordance.
        return toResponse(executionService.retryExecution(tenantId, executionId));
    }

    private ExecutionResponse toResponse(CampaignExecutionRecord record) {
        return new ExecutionResponse(
                record.id(), record.tenantId(), record.campaignId(), record.templateId(), record.channelType(),
                record.recipientPatientId(), record.scheduledAt(), record.status(), record.attemptCount(), record.lastError(),
                record.executedAt(), record.nextAttemptAt(), record.deliveryStatus(), record.providerName(),
                record.providerMessageId(), record.lastAttemptAt(), record.failureReason(), record.createdAt(), record.updatedAt()
        );
    }
}
