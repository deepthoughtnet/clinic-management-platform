package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class PatientPortalCareAiToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiToolRegistry.class);

    private final Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition<?, ?>> definitions;

    PatientPortalCareAiToolRegistry() {
        this(null, null, null, null);
    }

    PatientPortalCareAiToolRegistry(
            PatientPortalCareAiBusinessLookupService businessLookupService,
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade,
            DiscoverReferenceDataService discoverReferenceDataService
    ) {
        Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition<?, ?>> byType =
                new EnumMap<>(PatientPortalCareAiToolType.class);

        register(byType, doctorFindDefinition(businessLookupService));
        register(byType, clinicFindDefinition(businessLookupService));
        register(byType, serviceFindDefinition(businessLookupService));
        register(byType, availabilityCheckDefinition(businessLookupService));
        register(byType, appointmentCheckDefinition(businessLookupService, PatientPortalCareAiToolType.FIND_APPOINTMENTS));
        register(byType, appointmentCheckDefinition(businessLookupService, PatientPortalCareAiToolType.CHECK_APPOINTMENT));
        register(byType, appointmentBookDefinition(patientPortalService));
        register(byType, appointmentCancelDefinition(patientPortalService));
        register(byType, appointmentRescheduleDefinition(patientPortalService));
        this.definitions = Map.copyOf(byType);
    }

    PatientPortalCareAiToolDefinition<?, ?> definitionFor(PatientPortalCareAiToolType toolType) {
        PatientPortalCareAiToolDefinition<?, ?> definition = definitions.get(toolType);
        trace(definition);
        return definition;
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiDoctorFindSkillInput, PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>>> doctorFind() {
        return definition(PatientPortalCareAiToolType.FIND_DOCTOR);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiClinicFindSkillInput, PatientPortalCareAiSkillResult<List<PublicClinicSummaryResponse>>> clinicFind() {
        return definition(PatientPortalCareAiToolType.FIND_CLINIC);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiServiceFindSkillInput, PatientPortalCareAiSkillResult<List<DiscoverReferenceOptionRecord>>> serviceFind() {
        return definition(PatientPortalCareAiToolType.FIND_SERVICE);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiAvailabilityCheckSkillInput, PatientPortalCareAiSkillResult<List<PatientPortalDoctorSlotResponse>>> availabilityCheck() {
        return definition(PatientPortalCareAiToolType.FIND_SLOTS);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentCheckSkillInput, PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>>> appointmentCheck() {
        return definition(PatientPortalCareAiToolType.CHECK_APPOINTMENT);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentBookSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentBook() {
        return definition(PatientPortalCareAiToolType.BOOK_APPOINTMENT);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentCancelSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentCancel() {
        return definition(PatientPortalCareAiToolType.CANCEL_APPOINTMENT);
    }

    PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentRescheduleSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentReschedule() {
        return definition(PatientPortalCareAiToolType.RESCHEDULE_APPOINTMENT);
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiDoctorFindSkillInput, PatientPortalCareAiSkillResult<List<PublicDoctorSummaryResponse>>> doctorFindDefinition(
            PatientPortalCareAiBusinessLookupService businessLookupService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.FIND_DOCTOR,
                "doctor.find",
                Set.of(PatientPortalCareAiIntent.FIND_DOCTOR),
                "Find authorized doctors by doctor name, speciality, clinic, or location across Care and public discovery sources.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.SPECIALITY),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.LOCATION),
                false,
                true,
                "publicCatalogFacade.listDoctors",
                PatientPortalCareAiSkillConfirmationPolicy.NOT_REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_CONTEXT_AND_PUBLIC_DISCOVERY,
                PatientPortalCareAiDoctorFindSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    List<PublicDoctorSummaryResponse> doctors = businessLookupService.findDoctors(input.doctorQuery(), input.specialityQuery(), input.clinicSlug());
                    boolean locationFilterApplied = StringUtils.hasText(input.locationQuery());
                    if (locationFilterApplied) {
                        doctors = doctors.stream()
                                .filter(doctor -> containsText(doctor.doctorDisplayName(), input.locationQuery())
                                        || containsText(doctor.speciality(), input.locationQuery())
                                        || containsText(doctor.clinicDisplayName(), input.locationQuery())
                                        || containsText(doctor.city(), input.locationQuery())
                                        || containsText(doctor.area(), input.locationQuery()))
                                .toList();
                    }
                    if (doctors.isEmpty()) {
                        return PatientPortalCareAiSkillResult.noMatch(locationFilterApplied
                                ? "No doctors matched the requested location."
                                : "No doctors matched the requested criteria.");
                    }
                    if (doctors.size() > 1) {
                        return PatientPortalCareAiSkillResult.multipleMatches(doctors, "Multiple doctors matched the requested criteria.", doctors.size());
                    }
                    return PatientPortalCareAiSkillResult.success(doctors, "One doctor matched the requested criteria.", doctors.size());
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiClinicFindSkillInput, PatientPortalCareAiSkillResult<List<PublicClinicSummaryResponse>>> clinicFindDefinition(
            PatientPortalCareAiBusinessLookupService businessLookupService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.FIND_CLINIC,
                "clinic.find",
                Set.of(PatientPortalCareAiIntent.FIND_CLINIC),
                "Find public-bookable clinics by clinic name or location.",
                Set.of(PatientPortalCareAiEntityType.CLINIC),
                Set.of(PatientPortalCareAiEntityType.LOCATION, PatientPortalCareAiEntityType.SPECIALITY),
                false,
                true,
                "publicCatalogFacade.listClinics",
                PatientPortalCareAiSkillConfirmationPolicy.NOT_REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_CONTEXT_AND_PUBLIC_DISCOVERY,
                PatientPortalCareAiClinicFindSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    String query = firstText(input.clinicQuery(), input.specialityQuery());
                    List<PublicClinicSummaryResponse> clinics = businessLookupService.findClinics(query);
                    if (StringUtils.hasText(input.locationQuery())) {
                        clinics = clinics.stream()
                                .filter(clinic -> containsText(clinic.clinicDisplayName(), input.locationQuery())
                                        || containsText(clinic.area(), input.locationQuery())
                                        || containsText(clinic.city(), input.locationQuery()))
                                .toList();
                    }
                    if (clinics.isEmpty()) {
                        return PatientPortalCareAiSkillResult.noMatch("No clinics matched the requested criteria.");
                    }
                    if (clinics.size() > 1) {
                        return PatientPortalCareAiSkillResult.multipleMatches(clinics, "Multiple clinics matched the requested criteria.", clinics.size());
                    }
                    return PatientPortalCareAiSkillResult.success(clinics, "One clinic matched the requested criteria.", clinics.size());
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiServiceFindSkillInput, PatientPortalCareAiSkillResult<List<DiscoverReferenceOptionRecord>>> serviceFindDefinition(
            PatientPortalCareAiBusinessLookupService businessLookupService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.FIND_SERVICE,
                "service.find",
                Set.of(),
                "Find supported discover services by canonical service text.",
                Set.of(PatientPortalCareAiEntityType.SERVICE),
                Set.of(PatientPortalCareAiEntityType.LOCATION, PatientPortalCareAiEntityType.CLINIC),
                false,
                true,
                "discoverReferenceDataService.listServices",
                PatientPortalCareAiSkillConfirmationPolicy.NOT_REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_CONTEXT_AND_PUBLIC_DISCOVERY,
                PatientPortalCareAiServiceFindSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    List<DiscoverReferenceOptionRecord> services = businessLookupService.findServices(input.serviceQuery(), input.locationQuery());
                    if (services.isEmpty()) {
                        return PatientPortalCareAiSkillResult.noMatch("No services matched the requested criteria.");
                    }
                    if (services.size() > 1) {
                        return PatientPortalCareAiSkillResult.multipleMatches(services, "Multiple services matched the requested criteria.", services.size());
                    }
                    return PatientPortalCareAiSkillResult.success(services, "One service matched the requested criteria.", services.size());
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiAvailabilityCheckSkillInput, PatientPortalCareAiSkillResult<List<PatientPortalDoctorSlotResponse>>> availabilityCheckDefinition(
            PatientPortalCareAiBusinessLookupService businessLookupService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.FIND_SLOTS,
                "availability.check",
                Set.of(PatientPortalCareAiIntent.BOOK_APPOINTMENT, PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT),
                "Find available appointment slots for a selected doctor on a date.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.DATE),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.TIME_WINDOW, PatientPortalCareAiEntityType.TIME),
                false,
                true,
                "businessLookupService.findSlots",
                PatientPortalCareAiSkillConfirmationPolicy.NOT_REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_CONTEXT_AND_BOOKABLE_PROVIDER,
                PatientPortalCareAiAvailabilityCheckSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    List<PatientPortalDoctorSlotResponse> slots = businessLookupService.findSlots(
                            input.bookingReference(),
                            input.publicDoctorId(),
                            input.clinicSlug(),
                            input.tenantId(),
                            input.clinicId(),
                            input.date()
                    );
                    if (slots.isEmpty()) {
                        return PatientPortalCareAiSkillResult.noMatch("No slots were available for the requested doctor and date.");
                    }
                    if (slots.size() > 1) {
                        return PatientPortalCareAiSkillResult.multipleMatches(slots, "Multiple slots were available for the requested doctor and date.", slots.size());
                    }
                    return PatientPortalCareAiSkillResult.success(slots, "One slot was available for the requested doctor and date.", slots.size());
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentCheckSkillInput, PatientPortalCareAiSkillResult<List<PatientPortalCareAiAppointmentOption>>> appointmentCheckDefinition(
            PatientPortalCareAiBusinessLookupService businessLookupService,
            PatientPortalCareAiToolType toolType
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                toolType,
                "appointment.check",
                Set.of(PatientPortalCareAiIntent.CHECK_APPOINTMENT, PatientPortalCareAiIntent.APPOINTMENT_STATUS),
                "Check upcoming appointments for the authenticated patient.",
                Set.of(),
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT),
                false,
                true,
                "businessLookupService.upcomingAppointments",
                PatientPortalCareAiSkillConfirmationPolicy.NOT_REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_OWNED_ONLY,
                PatientPortalCareAiAppointmentCheckSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    List<PatientPortalCareAiAppointmentOption> appointments = businessLookupService.upcomingAppointments();
                    if (appointments.isEmpty()) {
                        return PatientPortalCareAiSkillResult.noMatch("No upcoming appointments were found for the authenticated patient.");
                    }
                    if (appointments.size() > 1) {
                        return PatientPortalCareAiSkillResult.multipleMatches(appointments, "Multiple upcoming appointments were found.", appointments.size());
                    }
                    return PatientPortalCareAiSkillResult.success(appointments, "An upcoming appointment was found.", appointments.size());
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentBookSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentBookDefinition(
            PatientPortalService patientPortalService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.BOOK_APPOINTMENT,
                "appointment.book",
                Set.of(PatientPortalCareAiIntent.BOOK_APPOINTMENT),
                "Book a patient appointment using a selected doctor, date, and slot.",
                Set.of(PatientPortalCareAiEntityType.DOCTOR, PatientPortalCareAiEntityType.DATE, PatientPortalCareAiEntityType.TIME_SLOT),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.TIME, PatientPortalCareAiEntityType.LOCATION, PatientPortalCareAiEntityType.SPECIALITY),
                true,
                true,
                "patientPortalService.bookAppointment",
                PatientPortalCareAiSkillConfirmationPolicy.REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_CONTEXT_AND_BOOKABLE_PROVIDER,
                PatientPortalCareAiAppointmentBookSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    if (!input.confirmed()) {
                        return PatientPortalCareAiSkillResult.needsConfirmation(null, "Booking requires explicit confirmation.");
                    }
                    if (StringUtils.hasText(input.bookingMode()) && "CALL_TO_BOOK".equalsIgnoreCase(input.bookingMode())) {
                        return PatientPortalCareAiSkillResult.notBookable("This doctor requires clinic booking instead of direct booking.");
                    }
                    PatientPortalAppointmentConfirmationResponse confirmation = patientPortalService.bookAppointment(new PatientPortalAppointmentBookingRequest(
                            input.doctorId(),
                            input.clinicSlug(),
                            input.tenantId(),
                            input.clinicId(),
                            input.bookingReference(),
                            input.date(),
                            input.time(),
                            input.reason()
                    ).withIdempotencyKey(input.idempotencyKey()));
                    return PatientPortalCareAiSkillResult.success(confirmation, confirmation.message(), 1);
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentCancelSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentCancelDefinition(
            PatientPortalService patientPortalService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.CANCEL_APPOINTMENT,
                "appointment.cancel",
                Set.of(PatientPortalCareAiIntent.CANCEL_APPOINTMENT),
                "Cancel a selected appointment after confirmation.",
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT),
                Set.of(),
                true,
                true,
                "patientPortalService.cancelAppointment",
                PatientPortalCareAiSkillConfirmationPolicy.REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_OWNED_ONLY,
                PatientPortalCareAiAppointmentCancelSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    if (!input.confirmed()) {
                        return PatientPortalCareAiSkillResult.needsConfirmation(null, "Cancellation requires explicit confirmation.");
                    }
                    PatientPortalAppointmentConfirmationResponse confirmation = patientPortalService.cancelAppointment(
                            input.appointmentId(), "Cancelled via AIVA", input.idempotencyKey());
                    return PatientPortalCareAiSkillResult.success(confirmation, confirmation.message(), 1);
                }
        );
    }

    private PatientPortalCareAiToolDefinition<PatientPortalCareAiAppointmentRescheduleSkillInput, PatientPortalCareAiSkillResult<PatientPortalAppointmentConfirmationResponse>> appointmentRescheduleDefinition(
            PatientPortalService patientPortalService
    ) {
        return new PatientPortalCareAiToolDefinition<>(
                PatientPortalCareAiToolType.RESCHEDULE_APPOINTMENT,
                "appointment.reschedule",
                Set.of(PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT),
                "Reschedule an existing appointment to a new date and slot.",
                Set.of(PatientPortalCareAiEntityType.APPOINTMENT, PatientPortalCareAiEntityType.DATE, PatientPortalCareAiEntityType.TIME_SLOT),
                Set.of(PatientPortalCareAiEntityType.CLINIC, PatientPortalCareAiEntityType.DOCTOR),
                true,
                true,
                "patientPortalService.rescheduleAppointment",
                PatientPortalCareAiSkillConfirmationPolicy.REQUIRED,
                PatientPortalCareAiSkillAuthorizationPolicy.PATIENT_OWNED_ONLY,
                PatientPortalCareAiAppointmentRescheduleSkillInput.class,
                (Class) PatientPortalCareAiSkillResult.class,
                input -> {
                    if (!input.confirmed()) {
                        return PatientPortalCareAiSkillResult.needsConfirmation(null, "Rescheduling requires explicit confirmation.");
                    }
                    PatientPortalAppointmentConfirmationResponse confirmation = patientPortalService.rescheduleAppointment(
                            input.appointmentId(),
                            input.date(),
                            input.time(),
                            input.reason(),
                            input.idempotencyKey()
                    );
                    return PatientPortalCareAiSkillResult.success(confirmation, confirmation.message(), 1);
                }
        );
    }

    private <I, O> PatientPortalCareAiToolDefinition<I, O> definition(PatientPortalCareAiToolType toolType) {
        @SuppressWarnings("unchecked")
        PatientPortalCareAiToolDefinition<I, O> definition = (PatientPortalCareAiToolDefinition<I, O>) definitions.get(toolType);
        trace(definition);
        return definition;
    }

    private <T> void register(Map<PatientPortalCareAiToolType, PatientPortalCareAiToolDefinition<?, ?>> definitions,
                              PatientPortalCareAiToolDefinition<?, ?> definition) {
        definitions.put(definition.toolType(), definition);
        trace(definition);
    }

    private boolean containsText(String value, String query) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(query)) {
            return false;
        }
        String normalizedValue = normalize(value);
        String normalizedQuery = normalize(query);
        return normalizedValue.contains(normalizedQuery) || normalizedQuery.contains(normalizedValue);
    }

    private String firstText(String left, String right) {
        return StringUtils.hasText(left) ? left : right;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private void trace(PatientPortalCareAiToolDefinition<?, ?> definition) {
        if (definition == null || !log.isInfoEnabled()) {
            return;
        }
        log.info(
                "CAREAI_TRACE_TOOL_REGISTRY toolType={} skillId={} intents={} requiredEntities={} optionalEntities={} confirmationRequired={} auditRequired={} mappedServiceName={} confirmationPolicy={} authorizationPolicy={} inputType={} outputType={}",
                definition.toolType(),
                definition.skillId(),
                definition.intents(),
                definition.requiredEntities(),
                definition.optionalEntities(),
                definition.confirmationRequired(),
                definition.auditRequired(),
                definition.mappedServiceName(),
                definition.confirmationPolicy(),
                definition.authorizationPolicy(),
                definition.inputType() == null ? null : definition.inputType().getSimpleName(),
                definition.outputType() == null ? null : definition.outputType().getSimpleName()
        );
    }
}
