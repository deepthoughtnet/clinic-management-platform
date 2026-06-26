package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PatientPortalCareAiToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiToolRegistry.class);

    private final Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition> definitions;

    PatientPortalCareAiToolRegistry() {
        Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition> byType =
                new EnumMap<>(PatientPortalCareAiToolType.class);
        register(byType, PatientPortalCareAiToolType.FIND_DOCTOR,
                "Find public-bookable doctors by doctor name or speciality.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.SPECIALITY),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.LOCATION),
                false,
                true,
                "publicCatalogFacade.listDoctors");
        register(byType, PatientPortalCareAiToolType.FIND_CLINIC,
                "Find public-bookable clinics by name or location.",
                Set.of(PatientPortalCareAiEntityType.CLINIC),
                Set.of(PatientPortalCareAiEntityType.LOCATION),
                false,
                true,
                "publicCatalogFacade.listClinics");
        register(byType, PatientPortalCareAiToolType.FIND_SLOTS,
                "Find available appointment slots for a doctor on a date.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.DATE),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.TIME_WINDOW, PatientPortalCareAiEntityType.TIME),
                false,
                true,
                "businessLookupService.findSlots");
        register(byType, PatientPortalCareAiToolType.BOOK_APPOINTMENT,
                "Book a patient appointment using a selected doctor, date, and slot.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.DATE, PatientPortalCareAiEntityType.TIME_SLOT),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.TIME, PatientPortalCareAiEntityType.LOCATION, PatientPortalCareAiEntityType.SPECIALITY),
                true,
                true,
                "patientPortalService.bookAppointment");
        register(byType, PatientPortalCareAiToolType.FIND_APPOINTMENTS,
                "Find the authenticated patient's appointments across linked clinic tenants.",
                Set.of(),
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT),
                false,
                true,
                "businessLookupService.upcomingAppointments");
        register(byType, PatientPortalCareAiToolType.CANCEL_APPOINTMENT,
                "Cancel a selected appointment after confirmation.",
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT),
                Set.of(),
                true,
                true,
                "patientPortalService.cancelAppointment");
        register(byType, PatientPortalCareAiToolType.RESCHEDULE_APPOINTMENT,
                "Reschedule an existing appointment to a new date and slot.",
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT, PatientPortalCareAiEntityType.DATE, PatientPortalCareAiEntityType.TIME_SLOT),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.DOCTOR),
                true,
                true,
                "patientPortalService.rescheduleAppointment");
        register(byType, PatientPortalCareAiToolType.CHECK_APPOINTMENT,
                "Check upcoming appointments for the authenticated patient.",
                Set.of(),
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT),
                false,
                true,
                "businessLookupService.upcomingAppointments");
        this.definitions = Map.copyOf(byType);
    }

    PatientPortalCareAiToolDefinition definitionFor(PatientPortalCareAiToolType toolType) {
        PatientPortalCareAiToolDefinition definition = definitions.get(toolType);
        trace(definition);
        return definition;
    }

    private void register(Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition> definitions,
                          PatientPortalCareAiToolType toolType,
                          String description,
                          Set<PatientPortalCareAiEntityType> requiredEntities,
                          Set<PatientPortalCareAiEntityType> optionalEntities,
                          boolean confirmationRequired,
                          boolean auditRequired,
                          String mappedServiceName) {
        definitions.put(toolType, new PatientPortalCareAiToolDefinition(
                toolType,
                description,
                Set.copyOf(requiredEntities),
                Set.copyOf(optionalEntities),
                confirmationRequired,
                auditRequired,
                mappedServiceName
        ));
        trace(definitions.get(toolType));
    }

    private void trace(PatientPortalCareAiToolDefinition definition) {
        if (definition == null || !log.isInfoEnabled()) {
            return;
        }
        log.info(
                "CAREAI_TRACE_TOOL_REGISTRY toolType={} requiredEntities={} optionalEntities={} confirmationRequired={} auditRequired={} mappedServiceName={}",
                definition.toolType(),
                definition.requiredEntities(),
                definition.optionalEntities(),
                definition.confirmationRequired(),
                definition.auditRequired(),
                definition.mappedServiceName()
        );
    }
}
