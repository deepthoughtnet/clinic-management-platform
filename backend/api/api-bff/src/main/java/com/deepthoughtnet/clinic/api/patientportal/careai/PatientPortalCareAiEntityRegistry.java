package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PatientPortalCareAiEntityRegistry {
    private final Map<PatientPortalCareAiEntityType, PatientPortalCareAiEntityDefinition> definitions;

    PatientPortalCareAiEntityRegistry() {
        Map<PatientPortalCareAiEntityType, PatientPortalCareAiEntityDefinition> byType =
                new EnumMap<>(PatientPortalCareAiEntityType.class);
        register(byType, PatientPortalCareAiEntityType.DOCTOR, "Doctor name or identifier",
                List.of("Dr Vikas", "Vikas", "Dr Ashish"), Set.of("dr", "doctor"), 0.95);
        register(byType, PatientPortalCareAiEntityType.CLINIC, "Clinic name or public clinic identifier",
                List.of("Sunrise Clinic", "CuraPilot Demo Clinic"), Set.of("clinic", "hospital", "centre", "center", "branch"), 0.92);
        register(byType, PatientPortalCareAiEntityType.APPOINTMENT, "Appointment reference or appointment mention",
                List.of("my appointment", "upcoming appointment", "cancel appointment"), Set.of("appointment", "booking", "visit"), 0.85);
        register(byType, PatientPortalCareAiEntityType.DATE, "Date or relative date expression",
                List.of("tomorrow", "next Friday", "26 June 2026", "26/06/2026"), Set.of(), 0.95);
        register(byType, PatientPortalCareAiEntityType.TIME, "Explicit clock time",
                List.of("10 AM", "5:30 PM"), Set.of(), 0.95);
        register(byType, PatientPortalCareAiEntityType.TIME_SLOT, "Slot number or slot selection",
                List.of("slot 3", "book 3", "number 2"), Set.of("slot", "number"), 0.9);
        register(byType, PatientPortalCareAiEntityType.TIME_WINDOW, "Loose time window",
                List.of("morning", "afternoon", "evening", "night"), Set.of("morning", "afternoon", "evening", "night"), 0.9);
        register(byType, PatientPortalCareAiEntityType.SPECIALITY, "Medical speciality or department",
                List.of("cardiology", "dermatology"), Set.of("speciality", "specialty", "department"), 0.8);
        register(byType, PatientPortalCareAiEntityType.LOCATION, "Location or area reference",
                List.of("Andheri", "Mumbai"), Set.of("location", "area", "city", "near"), 0.7);
        register(byType, PatientPortalCareAiEntityType.CONFIRMATION, "Positive confirmation",
                List.of("yes", "confirm", "book it", "cancel it", "हाँ", "हां", "ठीक है"), Set.of("yes", "confirm", "book it", "go ahead", "okay", "ok", "हाँ", "हां", "ठीक है", "कन्फर्म", "बुक कर दीजिए"), 0.99);
        register(byType, PatientPortalCareAiEntityType.CANCELLATION, "Cancellation acknowledgement",
                List.of("cancel it", "cancel this", "stop it", "नहीं", "रद्द"), Set.of("cancel", "cancel it", "stop", "नहीं", "रद्द", "कैंसल"), 0.98);
        register(byType, PatientPortalCareAiEntityType.RESET, "Conversation reset",
                List.of("start over", "forget it", "switch conversation", "टॉपिक चेंज", "बातचीत बदलो", "नया शुरू करो"), Set.of("start over", "forget it", "switch conversation", "reset", "clear chat", "टॉपिक चेंज", "बातचीत बदलो", "नया शुरू करो", "शुरू से"), 0.99);
        this.definitions = Map.copyOf(byType);
    }

    PatientPortalCareAiEntityDefinition definitionFor(PatientPortalCareAiEntityType type) {
        return definitions.get(type);
    }

    Set<String> aliasesFor(PatientPortalCareAiEntityType type) {
        PatientPortalCareAiEntityDefinition definition = definitions.get(type);
        return definition == null ? Set.of() : definition.aliases();
    }

    List<String> examplesFor(PatientPortalCareAiEntityType type) {
        PatientPortalCareAiEntityDefinition definition = definitions.get(type);
        return definition == null ? List.of() : definition.exampleUtterances();
    }

    private void register(Map<PatientPortalCareAiEntityType, PatientPortalCareAiEntityDefinition> definitions,
                          PatientPortalCareAiEntityType entityType,
                          String description,
                          List<String> exampleUtterances,
                          Set<String> aliases,
                          double defaultConfidence) {
        definitions.put(entityType, new PatientPortalCareAiEntityDefinition(
                entityType,
                description,
                List.copyOf(exampleUtterances),
                Set.copyOf(aliases),
                defaultConfidence
        ));
    }
}
