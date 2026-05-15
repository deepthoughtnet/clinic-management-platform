package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AlertActionRequest;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AlertRulesResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.DeadLetterResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.DeadLetterRow;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.OperationalAlertResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.OperationalAlertsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformHealthResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderMetricsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.ProviderSlosResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.QueueMetricsResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.RuntimeSummaryResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.SchedulerStatusResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.WebhookMetricsResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-native operational observability APIs across Clinic/CarePilot/AI surfaces. */
@RestController
@RequestMapping("/api/ops")
public class PlatformOpsController {
    private final PlatformOpsService platformOpsService;
    private final DeadLetterService deadLetterService;

    public PlatformOpsController(PlatformOpsService platformOpsService, DeadLetterService deadLetterService) {
        this.platformOpsService = platformOpsService;
        this.deadLetterService = deadLetterService;
    }

    @GetMapping("/platform-health")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public PlatformHealthResponse platformHealth() { return platformOpsService.platformHealth(RequestContextHolder.requireTenantId()); }

    @GetMapping("/schedulers")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public SchedulerStatusResponse schedulers() { return platformOpsService.schedulers(RequestContextHolder.requireTenantId()); }

    @GetMapping("/queues")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public QueueMetricsResponse queues() { return platformOpsService.queues(RequestContextHolder.requireTenantId()); }

    @GetMapping("/providers")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public ProviderMetricsResponse providers() { return platformOpsService.providers(RequestContextHolder.requireTenantId()); }

    @GetMapping("/provider-slos")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public ProviderSlosResponse providerSlos() { return platformOpsService.providerSlos(RequestContextHolder.requireTenantId()); }

    @GetMapping("/ai-metrics")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.AiMetricsResponse aiMetrics() { return platformOpsService.aiMetrics(RequestContextHolder.requireTenantId()); }

    @GetMapping({"/webhooks", "/webhook-metrics"})
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public WebhookMetricsResponse webhooks() { return platformOpsService.webhooks(RequestContextHolder.requireTenantId()); }

    @GetMapping("/alerts")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public OperationalAlertsResponse alerts() { return new OperationalAlertsResponse(platformOpsService.alerts(RequestContextHolder.requireTenantId())); }

    @GetMapping({"/alerts/rules", "/rules"})
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public AlertRulesResponse alertRules() { return platformOpsService.alertRules(RequestContextHolder.requireTenantId()); }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN')")
    public OperationalAlertResponse acknowledge(@PathVariable UUID id) {
        var ctx = RequestContextHolder.require();
        return platformOpsService.acknowledgeAlert(ctx.tenantId().value(), id, ctx.appUserId());
    }

    @PostMapping("/alerts/{id}/resolve")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN')")
    public OperationalAlertResponse resolve(@PathVariable UUID id, @RequestBody(required = false) AlertActionRequest request) {
        var ctx = RequestContextHolder.require();
        return platformOpsService.resolveAlert(ctx.tenantId().value(), id, ctx.appUserId(), request == null ? null : request.notes());
    }

    @PostMapping("/alerts/{id}/suppress")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN')")
    public OperationalAlertResponse suppress(@PathVariable UUID id) {
        var ctx = RequestContextHolder.require();
        return platformOpsService.suppressAlert(ctx.tenantId().value(), id);
    }

    @GetMapping("/runtime/summary")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public RuntimeSummaryResponse runtimeSummary() { return platformOpsService.runtimeSummary(RequestContextHolder.requireTenantId()); }

    @GetMapping("/runtime/errors")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public RuntimeSummaryResponse runtimeErrors() { return platformOpsService.runtimeErrors(RequestContextHolder.requireTenantId()); }

    @GetMapping("/runtime/failures")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public RuntimeSummaryResponse runtimeFailures() { return platformOpsService.runtimeFailures(RequestContextHolder.requireTenantId()); }

    @GetMapping("/dead-letter")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR')")
    public DeadLetterResponse deadLetter() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return new DeadLetterResponse(deadLetterService.list(tenantId).stream().map(row -> new DeadLetterRow(
                row.getId(), row.getTenantId(), row.getSourceType().name(), row.getSourceExecutionId(), row.getFailureReason(),
                row.getPayloadSummary(), row.getRetryCount(), row.getDeadLetteredAt(), row.getRecoveryStatus().name(), row.getLastRecoveryError()
        )).toList());
    }

    @PostMapping("/dead-letter/{id}/replay")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN')")
    public DeadLetterRow replayDeadLetter(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var row = deadLetterService.replay(tenantId, id);
        return new DeadLetterRow(row.getId(), row.getTenantId(), row.getSourceType().name(), row.getSourceExecutionId(), row.getFailureReason(), row.getPayloadSummary(), row.getRetryCount(), row.getDeadLetteredAt(), row.getRecoveryStatus().name(), row.getLastRecoveryError());
    }
}
