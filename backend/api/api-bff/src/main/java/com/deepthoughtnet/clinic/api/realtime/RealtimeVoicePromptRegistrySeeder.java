package com.deepthoughtnet.clinic.api.realtime;

import com.deepthoughtnet.clinic.ai.orchestration.platform.service.AiPromptRegistryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures baseline realtime voice prompt definitions exist in AI prompt registry.
 */
@Component
public class RealtimeVoicePromptRegistrySeeder {
    private static final Logger log = LoggerFactory.getLogger(RealtimeVoicePromptRegistrySeeder.class);

    private final AiPromptRegistryService promptRegistryService;

    public RealtimeVoicePromptRegistrySeeder(AiPromptRegistryService promptRegistryService) {
        this.promptRegistryService = promptRegistryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("realtime.voice.ai-receptionist.v1", "Realtime AI Receptionist");
        keys.put("realtime.voice.appointment-booking.v1", "Realtime Appointment Booking");
        keys.put("realtime.voice.clinic-faq.v1", "Realtime Clinic FAQ");
        keys.put("realtime.voice.lead-qualification.v1", "Realtime Lead Qualification");
        keys.put("realtime.voice.follow-up-reminder.v1", "Realtime Follow-up Reminder");
        keys.put("realtime.voice.escalation-detection.v1", "Realtime Escalation Detection");
        keys.put("AI_RECEPTIONIST_GREETING", "AI Receptionist Greeting");
        keys.put("AI_RECEPTIONIST_INTENT_DETECTION", "AI Receptionist Intent Detection");
        keys.put("AI_RECEPTIONIST_FAQ", "AI Receptionist FAQ");
        keys.put("AI_RECEPTIONIST_APPOINTMENT_COLLECTION", "AI Receptionist Appointment Collection");
        keys.put("AI_RECEPTIONIST_LEAD_CAPTURE", "AI Receptionist Lead Capture");
        keys.put("AI_RECEPTIONIST_SAFE_ESCALATION", "AI Receptionist Safe Escalation");
        keys.put("AI_RECEPTIONIST_SUMMARY", "AI Receptionist Session Summary");
        keys.put("AI_RECEPTIONIST_STRUCTURED_EXTRACTION", "AI Receptionist Structured Extraction");

        try {
            var existing = promptRegistryService.list(null);
            for (var entry : keys.entrySet()) {
                boolean found = existing.stream().anyMatch(p -> entry.getKey().equals(p.promptKey()));
                if (found) {
                    continue;
                }
                promptRegistryService.create(null,
                        new AiPromptRegistryService.PromptUpsertCommand(
                                entry.getKey(),
                                entry.getValue(),
                                "Realtime voice gateway prompt key foundation",
                                "REALTIME_VOICE",
                                entry.getKey(),
                                true
                        ),
                        null);
                log.info("Seeded realtime voice prompt key {}", entry.getKey());
            }
        } catch (Exception ex) {
            log.warn("Realtime voice prompt seeding skipped: {}", ex.toString());
        }
    }
}
