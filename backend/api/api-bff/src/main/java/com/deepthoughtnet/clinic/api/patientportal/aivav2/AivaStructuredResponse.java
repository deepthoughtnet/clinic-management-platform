package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.InteractiveAction;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Language-neutral response facts produced by the AIVA V2 kernel. */
public record AivaStructuredResponse(ResponseType type, Payload payload, List<InteractiveAction> actions) {
    public AivaStructuredResponse {
        type = type == null ? ResponseType.CLARIFICATION : type;
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public enum ResponseType {
        NEED_PROVIDER, PROVIDER_CHOICES, PROVIDER_NOT_FOUND, NEED_DATE, AVAILABLE_SLOTS,
        NO_MORE_SLOTS, BOOKING_CONFIRMATION, BOOKING_SUCCESS, APPOINTMENTS_NONE,
        APPOINTMENTS_FOUND, LOOKUP_CLARIFICATION, CANCELLATION_CHOICES,
        CANCELLATION_CONFIRMATION, CANCELLATION_REJECTED, CANCELLATION_SUCCESS,
        RESCHEDULE_SOURCE_CHOICES, RESCHEDULE_NEED_DATE, RESCHEDULE_AVAILABLE_SLOTS,
        RESCHEDULE_NO_MORE_SLOTS, RESCHEDULE_CONFIRMATION, RESCHEDULE_REJECTED,
        RESCHEDULE_SUCCESS, CANCELLATION_NONE, RESCHEDULE_SOURCE_NONE,
        STALE_RESULT, FAILURE, CLARIFICATION, CALL_TO_BOOK, LEGACY
    }

    public sealed interface Payload permits NeedProviderPayload, ProviderChoicesPayload, NeedDatePayload,
            AvailabilityPayload, NoMoreSlotsPayload, BookingConfirmationPayload, BookingSuccessPayload,
            AppointmentListPayload, CancellationChoicesPayload, CancellationConfirmationPayload,
            CancellationSuccessPayload, RescheduleSourceChoicesPayload, RescheduleNeedDatePayload,
            RescheduleConfirmationPayload, RescheduleSuccessPayload, ClarificationPayload,
            FailurePayload, StaleResultPayload, CallToBookPayload, ProviderNotFoundPayload,
            NoMoreSlotsEmptyPayload, EmptyPayload { }

    public record NeedProviderPayload(String specialty, String context) implements Payload { }
    public record ProviderOption(String candidateReference, String doctorDisplayName,
                                 String specialty, String clinicDisplayName) { }
    public record ProviderChoicesPayload(List<ProviderOption> candidates) implements Payload {
        public ProviderChoicesPayload { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    }
    public record ProviderNotFoundPayload(String doctorQuery, String specialty) implements Payload { }
    public record NeedDatePayload(String providerDisplayName) implements Payload { }
    public record SlotFact(String slotReference, LocalTime startsAt, LocalTime endsAt, String displayTime) { }
    public record AvailabilityPayload(String providerDisplayName, LocalDate date, List<SlotFact> slots,
                                      boolean hasMore, String constraint) implements Payload {
        public AvailabilityPayload { slots = slots == null ? List.of() : List.copyOf(slots); }
    }
    public record NoMoreSlotsPayload(String providerDisplayName, LocalDate date, String constraint) implements Payload { }
    public record NoMoreSlotsEmptyPayload(String reasonCode) implements Payload { }
    public record BookingConfirmationPayload(String providerDisplayName, LocalDate date, LocalTime time) implements Payload { }
    public record BookingSuccessPayload(String appointmentReference, String providerDisplayName,
                                        LocalDate date, LocalTime time) implements Payload { }
    public record AppointmentFact(String appointmentReference, String providerDisplayName,
                                  LocalDate date, LocalTime time, String clinicDisplayName,
                                  String status) { }
    public record AppliedFilters(String doctor, LocalDate date, String status, String clinic, boolean nextOnly) { }
    public record AppointmentListPayload(List<AppointmentFact> appointments,
                                         AppliedFilters appliedFilters) implements Payload {
        public AppointmentListPayload { appointments = appointments == null ? List.of() : List.copyOf(appointments); }
    }
    public record CancellationChoicesPayload(List<AppointmentFact> candidates) implements Payload {
        public CancellationChoicesPayload { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    }
    public record CancellationConfirmationPayload(AppointmentFact appointment) implements Payload { }
    public record CancellationSuccessPayload(AppointmentFact appointment) implements Payload { }
    public record RescheduleSourceChoicesPayload(List<AppointmentFact> candidates) implements Payload {
        public RescheduleSourceChoicesPayload { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    }
    public record RescheduleNeedDatePayload(String providerDisplayName, AppointmentFact originalAppointment) implements Payload { }
    public record RescheduleConfirmationPayload(String providerDisplayName, AppointmentFact originalAppointment,
                                               LocalDate targetDate, LocalTime targetTime) implements Payload { }
    public record RescheduleSuccessPayload(String providerDisplayName, LocalDate oldDate, LocalTime oldTime,
                                           LocalDate newDate, LocalTime newTime) implements Payload { }
    public record ClarificationPayload(String reasonCode, List<String> missingFields) implements Payload {
        public ClarificationPayload { missingFields = missingFields == null ? List.of() : List.copyOf(missingFields); }
    }
    public record FailurePayload(String reasonCode) implements Payload { }
    public record StaleResultPayload(String reasonCode, String safeContext) implements Payload { }
    public record CallToBookPayload(String providerDisplayName, String clinicDisplayName,
                                    String authorizedContactDetails) implements Payload { }
    public record EmptyPayload(String reasonCode) implements Payload { }

    public static AivaStructuredResponse of(ResponseType type, Payload payload, List<InteractiveAction> actions) {
        return new AivaStructuredResponse(type, payload, actions);
    }
}
