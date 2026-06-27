package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkflowInterruptManager {
    private static final Map<PatientPortalCareAiIntent, Integer> PRIORITY = new EnumMap<>(PatientPortalCareAiIntent.class);

    static {
        PRIORITY.put(PatientPortalCareAiIntent.RESET_CONVERSATION, 1000);
        PRIORITY.put(PatientPortalCareAiIntent.CANCEL_APPOINTMENT, 900);
        PRIORITY.put(PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT, 850);
        PRIORITY.put(PatientPortalCareAiIntent.CHECK_APPOINTMENT, 800);
        PRIORITY.put(PatientPortalCareAiIntent.APPOINTMENT_STATUS, 800);
        PRIORITY.put(PatientPortalCareAiIntent.BOOK_APPOINTMENT, 750);
        PRIORITY.put(PatientPortalCareAiIntent.FIND_DOCTOR, 300);
        PRIORITY.put(PatientPortalCareAiIntent.FIND_CLINIC, 300);
        PRIORITY.put(PatientPortalCareAiIntent.GREETING, 100);
        PRIORITY.put(PatientPortalCareAiIntent.SMALL_TALK, 50);
        PRIORITY.put(PatientPortalCareAiIntent.UNKNOWN, 0);
    }

    public WorkflowInterruptDecision evaluate(PatientPortalCareAiIntent currentIntent,
                                              PatientPortalCareAiIntent nextIntent,
                                              String userText) {
        PatientPortalCareAiIntent normalizedCurrent = PatientPortalCareAiIntent.normalize(currentIntent);
        PatientPortalCareAiIntent normalizedNext = PatientPortalCareAiIntent.normalize(nextIntent);
        boolean resetRequested = normalizedNext == PatientPortalCareAiIntent.RESET_CONVERSATION || isResetRequest(userText);
        boolean interruptOccurred = false;
        String reason = "preserve-current-workflow";
        PatientPortalCareAiIntent targetIntent = normalizedCurrent;

        if (resetRequested) {
            interruptOccurred = normalizedCurrent != null;
            reason = "explicit-reset";
            targetIntent = null;
        } else if (normalizedNext != null && normalizedNext.isWorkflowIntent()) {
            int currentPriority = priorityOf(normalizedCurrent);
            int nextPriority = priorityOf(normalizedNext);
            if (normalizedCurrent == null || normalizedCurrent != normalizedNext && nextPriority >= currentPriority) {
                interruptOccurred = normalizedCurrent != null && normalizedCurrent != normalizedNext;
                reason = normalizedCurrent == null ? "start-new-workflow" : "higher-priority-intent";
                targetIntent = normalizedNext;
            }
        }
        return new WorkflowInterruptDecision(normalizedCurrent, normalizedNext, targetIntent, interruptOccurred, resetRequested, reason);
    }

    public boolean isResetRequest(String userText) {
        if (!StringUtils.hasText(userText)) {
            return false;
        }
        String normalized = userText.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("start over")
                || normalized.equals("reset")
                || normalized.equals("new conversation")
                || normalized.equals("forget previous")
                || normalized.equals("start again")
                || normalized.equals("फिर से शुरू करें")
                || normalized.equals("नई बातचीत")
                || normalized.equals("रीसेट")
                || normalized.equals("शुरू से")
                || normalized.equals("सब भूल जाइए");
    }

    public int priorityOf(PatientPortalCareAiIntent intent) {
        return PRIORITY.getOrDefault(PatientPortalCareAiIntent.normalize(intent), 0);
    }

    public record WorkflowInterruptDecision(
            PatientPortalCareAiIntent currentIntent,
            PatientPortalCareAiIntent nextIntent,
            PatientPortalCareAiIntent targetIntent,
            boolean interruptOccurred,
            boolean resetRequested,
            String reason
    ) {
    }
}
