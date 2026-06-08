package com.deepthoughtnet.clinic.api.careai;

import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskSlaStatus;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CareAiTaskSlaScheduler {
    private final PlatformTenantManagementService tenantManagementService;
    private final CareAiReceptionistTaskService taskService;
    private final CareAiTaskNotificationService notificationService;
    private final CareAiTaskSlaProperties properties;

    public CareAiTaskSlaScheduler(
            PlatformTenantManagementService tenantManagementService,
            CareAiReceptionistTaskService taskService,
            CareAiTaskNotificationService notificationService,
            CareAiTaskSlaProperties properties
    ) {
        this.tenantManagementService = tenantManagementService;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${careai.tasks.sla.fixed-delay:PT1M}")
    public void run() {
        if (!properties.isEnabled()) {
            return;
        }
        for (var tenant : tenantManagementService.list()) {
            taskService.evaluateSla(tenant.id(), properties.getDueSoonWindow()).forEach(task -> {
                if (CareAiReceptionistTaskSlaStatus.BREACHED.name().equals(task.getSlaStatus())) {
                    notificationService.notifySlaBreached(task);
                }
            });
        }
    }
}
