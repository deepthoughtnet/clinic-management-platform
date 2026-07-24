package com.deepthoughtnet.clinic.api.notificationcenter;

import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterItem;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterPage;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterPreview;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterQuery;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterSummary;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterDtos.NotificationCenterUnreadCount;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterInboxService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-center")
public class NotificationCenterController {
    private final NotificationCenterInboxService inboxService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    public NotificationCenterController(NotificationCenterInboxService inboxService, ClinicTimeZoneResolver clinicTimeZoneResolver) {
        this.inboxService = inboxService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    @GetMapping("/unread-count")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterUnreadCount unreadCount() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.unreadCount(tenantId, appUserId);
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterSummary summary() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.summary(tenantId, appUserId, clinicTimeZoneResolver.resolve(tenantId));
    }

    @GetMapping("/preview")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterPreview preview(@RequestParam(required = false, defaultValue = "10") int size) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.preview(tenantId, appUserId, size);
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterPage list(@ModelAttribute NotificationCenterQuery query) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.list(tenantId, appUserId, query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterItem get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.get(tenantId, appUserId, id);
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterItem read(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.markRead(tenantId, appUserId, id);
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterItem unread(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        return inboxService.markUnread(tenantId, appUserId, id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("@permissionChecker.hasPermission('notification.center.read')")
    public NotificationCenterUnreadCount readAll() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = RequestContextHolder.require().appUserId();
        long updated = inboxService.markAllRead(tenantId, appUserId);
        return new NotificationCenterUnreadCount(updated);
    }
}
