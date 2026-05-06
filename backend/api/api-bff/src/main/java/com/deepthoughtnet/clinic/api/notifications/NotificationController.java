package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.notification.service.NotificationHistoryFilter;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationHistoryService notificationHistoryService;

    public NotificationController(NotificationHistoryService notificationHistoryService) {
        this.notificationHistoryService = notificationHistoryService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('notification.read') or @permissionChecker.hasPermission('patient.read')")
    public List<NotificationHistoryRecord> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return notificationHistoryService.list(tenantId, new NotificationHistoryFilter(status, eventType, channel, patientId, from, to, 0, 100))
                .getContent();
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<NotificationHistoryRecord> listByPatient(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return notificationHistoryService.listByPatient(tenantId, patientId);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@permissionChecker.hasPermission('notification.retry') or @permissionChecker.hasPermission('settings.manage')")
    public NotificationHistoryRecord retry(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return notificationHistoryService.retry(tenantId, id, actorAppUserId);
    }
}
