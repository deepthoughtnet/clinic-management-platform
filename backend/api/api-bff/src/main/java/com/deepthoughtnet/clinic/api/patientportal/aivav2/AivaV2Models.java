package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AivaV2Models {
    public static final String DECISION_SCHEMA_VERSION = "1.0";

    private AivaV2Models() {
    }

    public enum DialogAct {
        START_REQUEST, PROVIDE_INFORMATION, CHANGE_INFORMATION, SELECT_OPTION,
        REQUEST_ALTERNATIVE, CONFIRM, REJECT, ASK_QUESTION, ABANDON, UNKNOWN
    }

    public enum Operation {
        START_BOOKING, UPDATE_BOOKING, RESOLVE_PROVIDER, GET_AVAILABILITY,
        SELECT_SLOT, SHOW_MORE_SLOTS, PREPARE_BOOKING, CONFIRM_BOOKING,
        ANSWER_CONTEXT, SUSPEND_BOOKING, RESUME_BOOKING, ABANDON_BOOKING,
        LOOKUP_APPOINTMENTS, CANCEL_APPOINTMENT, PREPARE_CANCELLATION,
        CONFIRM_CANCELLATION, RESCHEDULE_APPOINTMENT, UPDATE_RESCHEDULE,
        GET_RESCHEDULE_AVAILABILITY, SELECT_RESCHEDULE_SLOT, PREPARE_RESCHEDULE,
        CONFIRM_RESCHEDULE, UNKNOWN
    }

    public enum ConfirmationPolarity { POSITIVE, NEGATIVE, AMBIGUOUS, NONE }

    public enum TopicAction { CONTINUE, SUSPEND, RESUME, ABANDON }

    public enum PatchMode { UNCHANGED, SET, CLEAR }

    public enum AvailabilityTimeConstraintMode { EXACT, AFTER, BEFORE, BETWEEN }

    public enum DraftStatus {
        COLLECTING, READY_FOR_CONFIRMATION, SUBMITTING, CONFIRMED, ABANDONED, EXPIRED
    }

    public enum ResolutionStatus { RESOLVED, AMBIGUOUS, NOT_FOUND, SUGGESTION }

    public enum ProviderSource { CARE_PRIVATE, DISCOVER_PUBLIC }

    public record ValuePatch(PatchMode mode, String value) {
        public static ValuePatch unchanged() { return new ValuePatch(PatchMode.UNCHANGED, null); }
        public static ValuePatch set(String value) { return new ValuePatch(PatchMode.SET, value); }
        public static ValuePatch clear() { return new ValuePatch(PatchMode.CLEAR, null); }
    }

    public record AvailabilityTimeConstraint(
            AvailabilityTimeConstraintMode mode,
            LocalTime startTime,
            LocalTime endTime
    ) {
        public AvailabilityTimeConstraint {
            if (mode == null) throw new IllegalArgumentException("Constraint mode is required");
            if (mode == AvailabilityTimeConstraintMode.BETWEEN && (startTime == null || endTime == null)) {
                throw new IllegalArgumentException("BETWEEN requires start and end times");
            }
            if (mode == AvailabilityTimeConstraintMode.BETWEEN && !startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("BETWEEN start must be before end");
            }
            if (mode == AvailabilityTimeConstraintMode.AFTER && startTime == null) {
                throw new IllegalArgumentException("AFTER requires a start time");
            }
            if (mode == AvailabilityTimeConstraintMode.BEFORE && endTime == null) {
                throw new IllegalArgumentException("BEFORE requires an end time");
            }
            if (mode == AvailabilityTimeConstraintMode.EXACT && startTime == null) {
                throw new IllegalArgumentException("EXACT requires a start time");
            }
        }
    }

    public record AvailabilityTimeConstraintPatch(PatchMode mode, AvailabilityTimeConstraint value) {
        public AvailabilityTimeConstraintPatch {
            mode = mode == null ? PatchMode.UNCHANGED : mode;
        }

        public static AvailabilityTimeConstraintPatch unchanged() {
            return new AvailabilityTimeConstraintPatch(PatchMode.UNCHANGED, null);
        }
    }

    public record BookingPatch(
            ValuePatch doctorText,
            ValuePatch specialtyText,
            ValuePatch dateExpression,
            ValuePatch timeWindow,
            ValuePatch exactTime,
            ValuePatch clinicText,
            AvailabilityTimeConstraintPatch availabilityTimeConstraint
    ) {
        public BookingPatch {
            doctorText = safe(doctorText);
            specialtyText = safe(specialtyText);
            dateExpression = safe(dateExpression);
            timeWindow = safe(timeWindow);
            exactTime = safe(exactTime);
            clinicText = safe(clinicText);
            availabilityTimeConstraint = availabilityTimeConstraint == null
                    ? AvailabilityTimeConstraintPatch.unchanged() : availabilityTimeConstraint;
        }

        public BookingPatch(ValuePatch doctorText, ValuePatch specialtyText, ValuePatch dateExpression,
                            ValuePatch timeWindow, ValuePatch exactTime) {
            this(doctorText, specialtyText, dateExpression, timeWindow, exactTime, null,
                    AvailabilityTimeConstraintPatch.unchanged());
        }

        public static BookingPatch empty() {
            return new BookingPatch(null, null, null, null, null, null, null);
        }

        private static ValuePatch safe(ValuePatch patch) {
            return patch == null ? ValuePatch.unchanged() : patch;
        }
    }

    public record Selection(String candidateRef, Integer ordinal, String slotRef, String exactTime) {
    }

    public record AppointmentLookupFilter(
            ValuePatch doctorText,
            ValuePatch dateExpression,
            ValuePatch status,
            ValuePatch clinicText,
            boolean nextOnly
    ) {
        public AppointmentLookupFilter {
            doctorText = doctorText == null ? ValuePatch.unchanged() : doctorText;
            dateExpression = dateExpression == null ? ValuePatch.unchanged() : dateExpression;
            status = status == null ? ValuePatch.unchanged() : status;
            clinicText = clinicText == null ? ValuePatch.unchanged() : clinicText;
        }

        public AppointmentLookupFilter(ValuePatch doctorText, ValuePatch dateExpression,
                                       ValuePatch status, boolean nextOnly) {
            this(doctorText, dateExpression, status, null, nextOnly);
        }

        public static AppointmentLookupFilter empty() {
            return new AppointmentLookupFilter(null, null, null, null, false);
        }
    }

    public enum AppointmentLookupStatus { FOUND, NONE, FAILED }

    public record AppointmentSummary(
            String appointmentReference,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate date,
            LocalTime time,
            String status,
            String visitReason,
            String doctorId,
            String clinicId,
            String tenantId,
            String clinicSlug
    ) {
        public AppointmentSummary(String appointmentReference, String doctorDisplayName, String clinicDisplayName,
                                  LocalDate date, LocalTime time, String status, String visitReason) {
            this(appointmentReference, doctorDisplayName, clinicDisplayName, date, time, status, visitReason,
                    null, null, null, null);
        }
    }

    public record AppointmentLookupResult(
            AppointmentLookupStatus status,
            List<AppointmentSummary> appointments,
            String safeReason
    ) {
        public AppointmentLookupResult {
            appointments = appointments == null ? List.of() : List.copyOf(appointments);
        }

        public static AppointmentLookupResult found(List<AppointmentSummary> appointments) {
            return new AppointmentLookupResult(appointments.isEmpty() ? AppointmentLookupStatus.NONE
                    : AppointmentLookupStatus.FOUND, appointments, null);
        }

        public static AppointmentLookupResult failed(String reason) {
            return new AppointmentLookupResult(AppointmentLookupStatus.FAILED, List.of(), reason);
        }
    }

    public record ConversationDecision(
            String schemaVersion,
            DialogAct dialogAct,
            Operation operation,
            BookingPatch bookingPatch,
            Selection selection,
            ConfirmationPolarity confirmation,
            TopicAction topicAction,
            String responseLanguage,
            double confidence,
            String provider,
            boolean fallbackUsed,
            long latencyMs,
            AppointmentLookupFilter lookupFilter,
            boolean unresolvedExplicitQualifierPresent
    ) {
        public ConversationDecision {
            schemaVersion = schemaVersion == null ? DECISION_SCHEMA_VERSION : schemaVersion;
            dialogAct = dialogAct == null ? DialogAct.UNKNOWN : dialogAct;
            operation = operation == null ? Operation.UNKNOWN : operation;
            bookingPatch = bookingPatch == null ? BookingPatch.empty() : bookingPatch;
            confirmation = confirmation == null ? ConfirmationPolarity.NONE : confirmation;
            topicAction = topicAction == null ? TopicAction.CONTINUE : topicAction;
            responseLanguage = responseLanguage == null ? "en" : responseLanguage;
            provider = provider == null ? "DETERMINISTIC" : provider;
            lookupFilter = lookupFilter == null ? AppointmentLookupFilter.empty() : lookupFilter;
        }

        public ConversationDecision(String schemaVersion, DialogAct dialogAct, Operation operation,
                                    BookingPatch bookingPatch, Selection selection,
                                    ConfirmationPolarity confirmation, TopicAction topicAction,
                                    String responseLanguage, double confidence, String provider,
                                    boolean fallbackUsed, long latencyMs) {
            this(schemaVersion, dialogAct, operation, bookingPatch, selection, confirmation, topicAction,
                    responseLanguage, confidence, provider, fallbackUsed, latencyMs, null, false);
        }

        public ConversationDecision(String schemaVersion, DialogAct dialogAct, Operation operation,
                                    BookingPatch bookingPatch, Selection selection,
                                    ConfirmationPolarity confirmation, TopicAction topicAction,
                                    String responseLanguage, double confidence, String provider,
                                    boolean fallbackUsed, long latencyMs, AppointmentLookupFilter lookupFilter) {
            this(schemaVersion, dialogAct, operation, bookingPatch, selection, confirmation, topicAction,
                    responseLanguage, confidence, provider, fallbackUsed, latencyMs, lookupFilter, false);
        }

        public static ConversationDecision unknown(String language, String provider, boolean fallbackUsed, long latencyMs) {
            return new ConversationDecision(DECISION_SCHEMA_VERSION, DialogAct.UNKNOWN, Operation.UNKNOWN,
                    BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE,
                    language, 0d, provider, fallbackUsed, latencyMs, null, false);
        }
    }

    public record ProviderCapabilities(
            boolean onlineBooking,
            boolean liveAvailability,
            boolean callToBook,
            boolean publicPhoneAvailable,
            boolean publicProfileAvailable,
            boolean careAuthorized,
            boolean publiclyPublished
    ) {
    }

    public record ProviderCandidate(
            String candidateHandle,
            String providerHandle,
            String doctorId,
            String clinicId,
            String tenantId,
            String clinicSlug,
            String bookingReference,
            String displayName,
            String specialty,
            String clinicDisplayName,
            ProviderSource source,
            ProviderCapabilities capabilities
    ) {
    }

    public record ProviderSearchResult(
            UUID resultId,
            ResolutionStatus status,
            String criteriaFingerprint,
            List<ProviderCandidate> candidates,
            ProviderCandidate resolvedProvider,
            Instant generatedAt,
            Instant expiresAt
    ) {
        public ProviderSearchResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record AvailabilitySlot(
            String slotReference,
            LocalTime startsAt,
            LocalTime endsAt,
            String displayTime
    ) {
    }

    public record AvailabilityResult(
            UUID requestId,
            UUID draftId,
            long draftRevision,
            String criteriaFingerprint,
            String providerHandle,
            String doctorId,
            String clinicId,
            LocalDate date,
            String timeWindow,
            AvailabilityTimeConstraint availabilityTimeConstraint,
            String timezone,
            List<AvailabilitySlot> slots,
            String cursor,
            boolean hasMore,
            Instant generatedAt,
            Instant expiresAt,
            int displayOffset,
            int pageSize
    ) {
        public AvailabilityResult {
            slots = slots == null ? List.of() : List.copyOf(slots);
            displayOffset = Math.max(0, displayOffset);
            pageSize = pageSize > 0 ? pageSize : 3;
        }

        public AvailabilityResult(
                UUID requestId,
                UUID draftId,
                long draftRevision,
                String criteriaFingerprint,
                String providerHandle,
                String doctorId,
                String clinicId,
                LocalDate date,
                String timeWindow,
                String timezone,
                List<AvailabilitySlot> slots,
                String cursor,
                boolean hasMore,
                Instant generatedAt,
                Instant expiresAt
        ) {
            this(requestId, draftId, draftRevision, criteriaFingerprint, providerHandle, doctorId, clinicId,
                    date, timeWindow, null, timezone, slots, cursor, hasMore, generatedAt, expiresAt, 0, 3);
        }
    }

    public record BookingConfirmation(
            String confirmationRef,
            UUID draftId,
            long draftRevision,
            String providerHandle,
            String doctorId,
            String clinicId,
            UUID availabilityRequestId,
            String slotReference,
            LocalDate date,
            LocalTime startsAt,
            Instant expiresAt,
            String idempotencyKey
    ) {
    }

    public record CancellationConfirmation(
            String confirmationRef,
            UUID appointmentId,
            String appointmentReference,
            String doctorDisplayName,
            String clinicDisplayName,
            LocalDate date,
            LocalTime startsAt,
            Instant expiresAt,
            String idempotencyKey
    ) {
    }

    public record CancellationResolution(
            AppointmentLookupFilter criteria,
            List<AppointmentSummary> candidates,
            Instant createdAt,
            Instant expiresAt
    ) {
        public CancellationResolution {
            criteria = criteria == null ? AppointmentLookupFilter.empty() : criteria;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public enum RescheduleStatus { SOURCE_RESOLVING, SOURCE_AMBIGUOUS, TARGET_COLLECTING,
        AVAILABILITY_READY, CONFIRMATION_PENDING, COMPLETED, ABANDONED }

    public record RescheduleResolution(
            String sourceAppointmentReference,
            AppointmentLookupFilter sourceCriteria,
            List<AppointmentSummary> sourceCandidates,
            String originalDoctorDisplayName,
            String providerHandle,
            String doctorId,
            String clinicId,
            String tenantId,
            String clinicSlug,
            LocalDate originalDate,
            LocalTime originalStartsAt,
            LocalDate targetDate,
            AvailabilityTimeConstraint availabilityConstraint,
            UUID latestAvailabilityRequestId,
            AvailabilityResult latestAvailability,
            String selectedSlotReference,
            LocalTime selectedStartsAt,
            RescheduleStatus status,
            long revision,
            Instant createdAt,
            Instant expiresAt
    ) {
        public RescheduleResolution {
            sourceCriteria = sourceCriteria == null ? AppointmentLookupFilter.empty() : sourceCriteria;
            sourceCandidates = sourceCandidates == null ? List.of() : List.copyOf(sourceCandidates);
            status = status == null ? RescheduleStatus.SOURCE_RESOLVING : status;
            revision = Math.max(1, revision);
        }

        public RescheduleResolution(String sourceAppointmentReference, String originalDoctorDisplayName,
                                    String providerHandle, String doctorId, String clinicId, String tenantId,
                                    String clinicSlug, LocalDate originalDate, LocalTime originalStartsAt,
                                    LocalDate targetDate, AvailabilityTimeConstraint availabilityConstraint,
                                    UUID latestAvailabilityRequestId, AvailabilityResult latestAvailability,
                                    String selectedSlotReference, LocalTime selectedStartsAt, RescheduleStatus status,
                                    long revision, Instant createdAt, Instant expiresAt) {
            this(sourceAppointmentReference, null, null, originalDoctorDisplayName, providerHandle, doctorId, clinicId,
                    tenantId, clinicSlug, originalDate, originalStartsAt, targetDate, availabilityConstraint,
                    latestAvailabilityRequestId, latestAvailability, selectedSlotReference, selectedStartsAt, status,
                    revision, createdAt, expiresAt);
        }
    }

    public record RescheduleConfirmation(
            String confirmationRef,
            String sourceAppointmentReference,
            String providerHandle,
            String doctorId,
            String clinicId,
            String tenantId,
            UUID targetAvailabilityRequestId,
            String targetSlotReference,
            LocalDate targetDate,
            LocalTime targetStartsAt,
            long stateRevision,
            Instant createdAt,
            Instant expiresAt,
            String commandId
    ) {
    }

    public record BookingDraft(
            UUID draftId,
            UUID patientSubjectId,
            String tenantScope,
            ProviderCandidate selectedProvider,
            String specialtyFilter,
            LocalDate preferredDate,
            String preferredTimeWindow,
            LocalTime exactTime,
            AvailabilityTimeConstraint availabilityTimeConstraint,
            UUID latestAvailabilityRequestId,
            String selectedSlotReference,
            DraftStatus status,
            long revision,
            Instant expiresAt,
            String confirmedAppointmentReference
    ) {
        public static BookingDraft create(UUID patientId, String tenantId, Instant now) {
            return new BookingDraft(UUID.randomUUID(), patientId, tenantId, null, null, null,
                    null, null, null, null, null, DraftStatus.COLLECTING, 1L,
                    now.plusSeconds(30 * 60), null);
        }

        public BookingDraft(UUID draftId, UUID patientSubjectId, String tenantScope,
                            ProviderCandidate selectedProvider, String specialtyFilter, LocalDate preferredDate,
                            String preferredTimeWindow, LocalTime exactTime, UUID latestAvailabilityRequestId,
                            String selectedSlotReference, DraftStatus status, long revision, Instant expiresAt,
                            String confirmedAppointmentReference) {
            this(draftId, patientSubjectId, tenantScope, selectedProvider, specialtyFilter, preferredDate,
                    preferredTimeWindow, exactTime, null, latestAvailabilityRequestId, selectedSlotReference,
                    status, revision, expiresAt, confirmedAppointmentReference);
        }
    }

    public record SessionProjection(
            String conversationId,
            UUID patientId,
            String tenantId,
            BookingDraft activeDraft,
            BookingDraft suspendedDraft,
            ProviderSearchResult latestProviderResult,
            AvailabilityResult latestAvailabilityResult,
            BookingConfirmation pendingConfirmation,
            CancellationConfirmation pendingCancellation,
            CancellationResolution pendingCancellationResolution,
            RescheduleResolution pendingReschedule,
            RescheduleConfirmation pendingRescheduleConfirmation,
            long version,
            Instant expiresAt
    ) {
        public SessionProjection(String conversationId, UUID patientId, String tenantId,
                                 BookingDraft activeDraft, BookingDraft suspendedDraft,
                                 ProviderSearchResult latestProviderResult, AvailabilityResult latestAvailabilityResult,
                                 BookingConfirmation pendingConfirmation, long version, Instant expiresAt) {
            this(conversationId, patientId, tenantId, activeDraft, suspendedDraft, latestProviderResult,
                    latestAvailabilityResult, pendingConfirmation, null, null, null, null, version, expiresAt);
        }

        public SessionProjection(String conversationId, UUID patientId, String tenantId,
                                 BookingDraft activeDraft, BookingDraft suspendedDraft,
                                 ProviderSearchResult latestProviderResult, AvailabilityResult latestAvailabilityResult,
                                 BookingConfirmation pendingConfirmation, CancellationConfirmation pendingCancellation,
                                 long version, Instant expiresAt) {
            this(conversationId, patientId, tenantId, activeDraft, suspendedDraft, latestProviderResult,
                    latestAvailabilityResult, pendingConfirmation, pendingCancellation, null, null, null, version, expiresAt);
        }
    }

    public record MessageRequest(String conversationId, String message, String language,
                                 InteractiveActionRequest action) {
        public MessageRequest(String conversationId, String message, String language) {
            this(conversationId, message, language, null);
        }
    }

    public record InteractiveActionRequest(String type, String value, String slotReference) {
    }

    public record InteractiveAction(String type, String label, String value, String slotReference) {
    }

    public record StateView(
            UUID draftId,
            DraftStatus status,
            long revision,
            String providerName,
            String specialty,
            LocalDate date,
            String timeWindow,
            LocalTime exactTime,
            String selectedSlotReference,
            boolean confirmationPending,
            String appointmentReference
    ) {
    }

    public record MessageResponse(
            String conversationId,
            String turnId,
            String assistantMessage,
            String responseCategory,
            StateView state,
            String interpretationProvider,
            boolean fallbackUsed,
            List<InteractiveAction> actions
    ) {
        public MessageResponse {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record ToolResult<T>(String category, T value, String safeReason) {
        public static <T> ToolResult<T> success(T value) { return new ToolResult<>("SUCCESS", value, null); }
        public static <T> ToolResult<T> failure(String category, String reason) { return new ToolResult<>(category, null, reason); }
    }

    public record BookingReceipt(String appointmentReference, String status, OffsetDateTime createdAt) {
    }
}
