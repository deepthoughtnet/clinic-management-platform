package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/**
 * Scheduler that materializes due operational reminder executions for CarePilot campaigns.
 */
@Component
@ConditionalOnProperty(prefix = "carepilot.reminders", name = "enabled", havingValue = "true")
public class CarePilotReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(CarePilotReminderScheduler.class);
    private static final String LOCK_KEY = "scheduler:carepilot-reminder";

    private final CarePilotReminderTriggerService triggerService;
    private final DistributedLockService lockService;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final Duration lockWaitTimeout;

    public CarePilotReminderScheduler(
            CarePilotReminderTriggerService triggerService,
            DistributedLockService lockService,
            SchedulerLockMonitor schedulerLockMonitor,
            @Value("${platform.locks.scheduler-wait-timeout:PT2S}") Duration lockWaitTimeout
    ) {
        this.triggerService = triggerService;
        this.lockService = lockService;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.lockWaitTimeout = lockWaitTimeout;
    }

    @Scheduled(fixedDelayString = "${carepilot.reminders.fixed-delay:PT15M}")
    public void queueDueReminders() {
        boolean ran = lockService.executeWithLock(LOCK_KEY, lockWaitTimeout, () -> {
            schedulerLockMonitor.markAcquired("carepilot-reminder-scheduler");
            int queued = triggerService.queueDueReminders();
            if (queued > 0) {
                log.info("Queued {} CarePilot reminder execution(s)", queued);
            }
            return null;
        });
        if (!ran) {
            schedulerLockMonitor.markSkipped("carepilot-reminder-scheduler");
            log.debug("Skipped CarePilot reminder scheduler run because lock is held.");
        }
    }
}
