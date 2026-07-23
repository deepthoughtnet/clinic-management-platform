package com.deepthoughtnet.clinic.api.notifications.operations;

import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsAnalyticsResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsAuditResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsDeliveryRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsPageResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsProviderRow;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsQuery;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryRequest;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSummaryResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-operations")
public class NotificationOperationsController {
    private final NotificationOperationsService notificationOperationsService;

    public NotificationOperationsController(NotificationOperationsService notificationOperationsService) {
        this.notificationOperationsService = notificationOperationsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsSummaryResponse summary(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.summary(tenantId, query);
    }

    @GetMapping("/deliveries")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsPageResponse deliveries(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.deliveries(tenantId, query);
    }

    @GetMapping("/deliveries/{logicalNotificationId}")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsDeliveryRow delivery(@PathVariable String logicalNotificationId, @ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.delivery(tenantId, logicalNotificationId, query);
    }

    @GetMapping("/failures")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsPageResponse failures(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.failures(tenantId, query);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @PreAuthorize("@permissionChecker.hasPermission('notification.retry') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsRetryResponse retrySingle(@PathVariable UUID deliveryId, @RequestParam(required = false) UUID tenantId) {
        UUID resolvedTenantId = resolveTenantId(tenantId);
        RequestContext context = RequestContextHolder.require();
        return notificationOperationsService.retry(resolvedTenantId, context.appUserId(), List.of(deliveryId));
    }

    @PostMapping("/retries")
    @PreAuthorize("@permissionChecker.hasPermission('notification.retry') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsRetryResponse retry(@RequestParam(required = false) UUID tenantId, @RequestBody NotificationOperationsRetryRequest request) {
        UUID resolvedTenantId = resolveTenantId(tenantId);
        RequestContext context = RequestContextHolder.require();
        return notificationOperationsService.retry(resolvedTenantId, context.appUserId(), request == null ? List.of() : request.ids());
    }

    @GetMapping("/providers")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<NotificationOperationsProviderRow> providers(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.providers(tenantId, query);
    }

    @GetMapping("/analytics")
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsAnalyticsResponse analytics(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.analytics(tenantId, query);
    }

    @GetMapping("/audit")
    @PreAuthorize("@permissionChecker.hasPermission('audit.read') or @permissionChecker.hasPermission('notification.read') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('TENANT_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationOperationsAuditResponse audit(@ModelAttribute NotificationOperationsQuery query) {
        UUID tenantId = resolveTenantId(query.tenantId());
        return notificationOperationsService.audit(tenantId, query);
    }

    private UUID resolveTenantId(UUID requestedTenantId) {
        RequestContext context = RequestContextHolder.require();
        UUID currentTenantId = context.tenantId() == null ? null : context.tenantId().value();
        boolean platformAdmin = context.tokenRoles() != null && context.tokenRoles().stream().anyMatch(role -> "PLATFORM_ADMIN".equalsIgnoreCase(role));
        if (requestedTenantId == null) {
            if (currentTenantId != null) {
                return currentTenantId;
            }
            throw new IllegalArgumentException("tenantId is required");
        }
        if (currentTenantId == null) {
            return requestedTenantId;
        }
        if (currentTenantId.equals(requestedTenantId) || platformAdmin) {
            return requestedTenantId;
        }
        throw new IllegalArgumentException("Tenant mismatch");
    }
}
