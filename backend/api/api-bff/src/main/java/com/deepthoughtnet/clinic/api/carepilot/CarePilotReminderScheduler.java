package com.deepthoughtnet.clinic.api.carepilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler that materializes due operational reminder executions for CarePilot campaigns.
 */
@Component
@ConditionalOnProperty(prefix = "carepilot.reminders", name = "enabled", havingValue = "true")
public class CarePilotReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(CarePilotReminderScheduler.class);

    private final CarePilotReminderTriggerService triggerService;

    public CarePilotReminderScheduler(CarePilotReminderTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @Scheduled(fixedDelayString = "${carepilot.reminders.fixed-delay:PT15M}")
    public void queueDueReminders() {
        int queued = triggerService.queueDueReminders();
        if (queued > 0) {
            log.info("Queued {} CarePilot reminder execution(s)", queued);
        }
    }
}
