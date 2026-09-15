package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CancellationResolution;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.InteractiveAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PatchMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleResolution;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.StateView;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import static com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaStructuredResponse.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AivaV2TransactionalKernel {
    private static final Logger log = LoggerFactory.getLogger(AivaV2TransactionalKernel.class);
    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

    private final AivaV2BookingTools tools;
    private final AivaV2AppointmentLookupTool appointmentLookupTool;
    private final AivaV2CancellationTool cancellationTool;
    private final AivaV2RescheduleTools rescheduleTools;
    private final Clock clock;

    @Autowired
    AivaV2TransactionalKernel(AivaV2BookingTools tools, AivaV2AppointmentLookupTool appointmentLookupTool,
                              AivaV2CancellationTool cancellationTool, AivaV2RescheduleTools rescheduleTools) {
        this(tools, appointmentLookupTool, cancellationTool, rescheduleTools, Clock.systemUTC());
    }

    AivaV2TransactionalKernel(AivaV2BookingTools tools, Clock clock) {
        this(tools, null, null, null, clock);
    }

    AivaV2TransactionalKernel(AivaV2BookingTools tools, AivaV2AppointmentLookupTool appointmentLookupTool, Clock clock) {
        this(tools, appointmentLookupTool, null, null, clock);
    }

    AivaV2TransactionalKernel(AivaV2BookingTools tools, AivaV2AppointmentLookupTool appointmentLookupTool,
                              AivaV2CancellationTool cancellationTool, Clock clock) {
        this(tools, appointmentLookupTool, cancellationTool, null, clock);
    }

    AivaV2TransactionalKernel(AivaV2BookingTools tools, AivaV2AppointmentLookupTool appointmentLookupTool,
                              AivaV2CancellationTool cancellationTool, AivaV2RescheduleTools rescheduleTools,
                              Clock clock) {
        this.tools = tools;
        this.appointmentLookupTool = appointmentLookupTool;
        this.cancellationTool = cancellationTool;
        this.rescheduleTools = rescheduleTools;
        this.clock = clock;
    }

    KernelResult handle(SessionProjection original, ConversationDecision decision, String turnId) {
        AivaV2TemporalResolution temporal = AivaV2TemporalResolution.unchanged(
                original.activeDraft() == null ? null : original.activeDraft().preferredDate(),
                decision.bookingPatch().dateExpression().value());
        if (decision.bookingPatch().dateExpression().mode() == PatchMode.SET) {
            try {
                temporal = new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED,
                        LocalDate.parse(decision.bookingPatch().dateExpression().value()),
                        AivaV2TemporalResolution.Source.MODEL_CANONICAL,
                        null, decision.bookingPatch().dateExpression().value(), false);
            } catch (DateTimeParseException ex) {
                temporal = new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.INVALID, null,
                        AivaV2TemporalResolution.Source.MODEL_CANONICAL, null,
                        decision.bookingPatch().dateExpression().value(), false);
            }
        }
        return handle(original, decision, temporal, turnId);
    }

    KernelResult handle(SessionProjection original, ConversationDecision decision,
                        AivaV2TemporalResolution temporal, String turnId) {
        return handle(original, decision, temporal, ZoneId.of("Asia/Kolkata"), turnId);
    }

    KernelResult handle(SessionProjection original, ConversationDecision decision,
                        AivaV2TemporalResolution temporal, ZoneId clinicZone, String turnId) {
        Instant now = Instant.now(clock);
        SessionProjection session = original;
        if (session.pendingCancellationResolution() != null
                && session.pendingCancellationResolution().expiresAt() != null
                && !session.pendingCancellationResolution().expiresAt().isAfter(now)) {
            session = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), session.latestAvailabilityResult(),
                    session.pendingConfirmation(), session.pendingCancellation(), null);
        }
        Operation incomingOperation = decision.operation();
        boolean explicitNewWorkflow = isExplicitNewWorkflow(decision);
        boolean rescheduleOwnsAvailability = session.pendingReschedule() != null
                && decision.operation() == Operation.GET_AVAILABILITY;
        boolean targetRefinementDetected = session.pendingReschedule() != null
                && StringUtils.hasText(session.pendingReschedule().sourceAppointmentReference())
                && decision.operation() == Operation.RESCHEDULE_APPOINTMENT
                && hasBoundRescheduleTargetRefinement(decision, temporal);
        if (targetRefinementDetected) {
            decision = withOperation(decision, Operation.UPDATE_RESCHEDULE);
        }
        log.info("AIVA_V2_RESCHEDULE_REFINEMENT_TRACE conversationId={} turnId={} incomingOperation={} "
                        + "normalizedOperation={} activeWorkflow={} pendingReschedulePresent={} "
                        + "targetRefinementDetected={} targetRefinementSource={} explicitNewWorkflow={} "
                        + "sourceAppointmentBound={} targetDate={}",
                session.conversationId(), turnId, incomingOperation,
                decision.operation(), session.pendingReschedule() == null ? "NONE" : "RESCHEDULE",
                session.pendingReschedule() != null, targetRefinementDetected,
                targetRefinementSource(decision, temporal),
                explicitNewWorkflow,
                session.pendingReschedule() != null
                        && StringUtils.hasText(session.pendingReschedule().sourceAppointmentReference()),
                temporal == null ? null : temporal.localDate());
        if ((session.pendingCancellationResolution() != null || session.pendingReschedule() != null)
                && isExplicitNewWorkflow(decision) && !rescheduleOwnsAvailability
                && decision.operation() != Operation.LOOKUP_APPOINTMENTS) {
            session = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), session.latestAvailabilityResult(),
                    session.pendingConfirmation(), null, null, null, null);
        }
        if (session.pendingReschedule() != null
                && decision.topicAction() == AivaV2Models.TopicAction.ABANDON) {
            AivaStructuredResponse rejected = AivaStructuredResponse.of(ResponseType.RESCHEDULE_REJECTED,
                    rescheduleConfirmationPayload(session), List.of());
            SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), session.latestAvailabilityResult(), session.pendingConfirmation(),
                    session.pendingCancellation());
            return result(updated, decision, turnId, "RESCHEDULE_ABANDONED",
                    "I will leave your original appointment unchanged.", rejected);
        }
        if (decision.operation() == Operation.RESCHEDULE_APPOINTMENT
                || decision.operation() == Operation.UPDATE_RESCHEDULE
                || decision.operation() == Operation.GET_RESCHEDULE_AVAILABILITY
                || decision.operation() == Operation.SELECT_RESCHEDULE_SLOT
                || (decision.operation() == Operation.GET_AVAILABILITY && session.pendingReschedule() != null)
                || (decision.operation() == Operation.SHOW_MORE_SLOTS && session.pendingReschedule() != null)) {
            return handleReschedule(session, decision, temporal, turnId);
        }
        if (decision.operation() == Operation.CONFIRM_RESCHEDULE
                && decision.confirmation() == ConfirmationPolarity.POSITIVE) {
            return confirmReschedule(session, decision, turnId);
        }
        if (decision.confirmation() == ConfirmationPolarity.NEGATIVE && session.pendingRescheduleConfirmation() != null) {
            RescheduleResolution state = session.pendingReschedule();
            RescheduleResolution rejected = rescheduleState(state, state.targetDate(), state.availabilityConstraint(),
                    state.latestAvailability(), null, state.latestAvailability() == null
                            ? RescheduleStatus.TARGET_COLLECTING : RescheduleStatus.AVAILABILITY_READY);
            SessionProjection updated = replaceReschedule(session, rejected, null);
            return result(updated, decision, turnId, "RESCHEDULE_CONFIRMATION_REJECTED",
                    "I will leave your original appointment unchanged. You can choose another slot or date.",
                    AivaStructuredResponse.of(ResponseType.RESCHEDULE_REJECTED,
                            new RescheduleConfirmationPayload(state.originalDoctorDisplayName(), appointmentFact(state),
                                    state.targetDate(), state.selectedStartsAt()), List.of()));
        }
        if (decision.operation() == Operation.LOOKUP_APPOINTMENTS) {
            return lookupAppointments(session, decision, temporal, turnId);
        }
        if ((decision.operation() == Operation.CONFIRM_CANCELLATION
                || decision.operation() == Operation.CANCEL_APPOINTMENT)
                && decision.confirmation() == ConfirmationPolarity.NEGATIVE
                && session.pendingCancellation() != null) {
            AppointmentFact rejectedAppointment = cancellationFact(session.pendingCancellation());
            SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), session.latestAvailabilityResult(), session.pendingConfirmation(), null);
            return result(updated, decision, turnId, "CANCEL_CONFIRMATION_REJECTED",
                    "I’ll leave that appointment unchanged.", AivaStructuredResponse.of(ResponseType.CANCELLATION_REJECTED,
                            new CancellationConfirmationPayload(rejectedAppointment), List.of()));
        }
        if (decision.operation() == Operation.CANCEL_APPOINTMENT) {
            return prepareCancellation(session, decision, temporal, turnId);
        }
        if (decision.operation() == Operation.CONFIRM_CANCELLATION
                && decision.confirmation() == ConfirmationPolarity.POSITIVE) {
            return confirmCancellation(session, decision, turnId);
        }
        if (decision.operation() == Operation.UNKNOWN) {
            return result(session, decision, turnId, "CLARIFICATION", unknownClarification(session));
        }
        if (session.activeDraft() == null && requiresDraft(decision)) {
            session = withActiveDraft(session, BookingDraft.create(session.patientId(), session.tenantId(), now));
        }

        if (decision.topicAction() == AivaV2Models.TopicAction.SUSPEND || decision.operation() == Operation.SUSPEND_BOOKING) {
            SessionProjection updated = new SessionProjection(session.conversationId(), session.patientId(), session.tenantId(),
                    null, session.activeDraft(), null, null, null, session.version() + 1, session.expiresAt());
            return result(updated, decision, turnId, "BOOKING_SUSPENDED", "Your booking is paused. You can continue it later.");
        }
        if (decision.topicAction() == AivaV2Models.TopicAction.RESUME || decision.operation() == Operation.RESUME_BOOKING) {
            if (session.suspendedDraft() == null || session.suspendedDraft().expiresAt().isBefore(now)) {
                return result(session, decision, turnId, "NO_SUSPENDED_BOOKING", "There is no active booking to resume.");
            }
            SessionProjection updated = new SessionProjection(session.conversationId(), session.patientId(), session.tenantId(),
                    session.suspendedDraft(), null, null, null, null, session.version() + 1, session.expiresAt());
            return continueFromDraft(updated, decision, turnId);
        }
        if (decision.topicAction() == AivaV2Models.TopicAction.ABANDON || decision.operation() == Operation.ABANDON_BOOKING) {
            BookingDraft abandoned = tools.abandonBooking(session.activeDraft());
            SessionProjection updated = replace(session, abandoned, null, null, null, null);
            return result(updated, decision, turnId, "BOOKING_ABANDONED", "I have abandoned this booking draft.");
        }

        BookingDraft draft = session.activeDraft();
        if (draft != null && draft.status() == DraftStatus.CONFIRMED
                && decision.confirmation() == ConfirmationPolarity.POSITIVE) {
            return result(session, decision, turnId, "BOOKING_CONFIRMED",
                    "This appointment is already booked. Reference: " + draft.confirmedAppointmentReference() + ".");
        }
        if (decision.confirmation() == ConfirmationPolarity.POSITIVE) {
            return confirm(session, decision, turnId);
        }

        if (decision.operation() == Operation.ANSWER_CONTEXT) {
            return answerContext(session, decision, turnId);
        }

        if (decision.confirmation() == ConfirmationPolarity.NEGATIVE && session.pendingConfirmation() != null) {
            BookingDraft rejected = copyDraft(session.activeDraft(), session.activeDraft().selectedProvider(),
                    session.activeDraft().specialtyFilter(), session.activeDraft().preferredDate(),
                    session.activeDraft().preferredTimeWindow(), null,
                    session.activeDraft().latestAvailabilityRequestId(), null,
                    DraftStatus.COLLECTING, session.activeDraft().revision(), null);
            session = replace(session, rejected, null, session.latestProviderResult(),
                    session.latestAvailabilityResult(), null);
        }

        // A current slot choice is not a booking-criteria patch. Resolve it before
        // applying model fields so selection.exactTime cannot invalidate the result.
        if (decision.operation() == Operation.SELECT_SLOT && decision.selection() != null) {
            KernelResult selection = selectCurrentCandidate(session, decision, turnId);
            if (selection != null) return selection;
        }
        if (decision.operation() == Operation.RESOLVE_PROVIDER && session.latestProviderResult() != null) {
            return resolveProviderCandidate(session, decision, turnId);
        }

        PatchResult patchResult = applyPatch(session, decision, temporal, clinicZone);
        session = patchResult.session();
        draft = session.activeDraft();
        if (patchResult.invalidDate()) {
            return result(session, decision, turnId, "CLARIFICATION", "Please provide an unambiguous appointment date.");
        }
        if (patchResult.providerResult() != null) {
            ProviderSearchResult providerResult = patchResult.providerResult();
            if (providerResult.status() == ResolutionStatus.SUGGESTION) {
                return result(session, decision, turnId, "PROVIDER_SUGGESTION",
                        "I did not find that exact doctor. Did you mean " + providerResult.candidates().get(0).displayName() + "?");
            }
            if (providerResult.status() == ResolutionStatus.NOT_FOUND) {
                return result(session, decision, turnId, "PROVIDER_NOT_FOUND", "I could not find a matching doctor.");
            }
            if (providerResult.status() == ResolutionStatus.AMBIGUOUS) {
                return result(session, decision, turnId, "PROVIDER_CHOICES", providerChoices(providerResult));
            }
        }
        if (patchResult.pastDate()) {
            return result(session, decision, turnId, "PAST_DATE", pastDateMessage(temporal.localDate()));
        }

        if (decision.operation() == Operation.SHOW_MORE_SLOTS) {
            AvailabilityResult current = session.latestAvailabilityResult();
            if (!availabilityIsCurrent(session.activeDraft(), current)) {
                return result(session, decision, turnId, "STALE", "Those slots are no longer current.");
            }
            int nextOffset = current.displayOffset() + current.pageSize();
            if (nextOffset >= current.slots().size()) {
                return result(session, decision, turnId, "NO_MORE_SLOTS", noMoreSlotsMessage(session.activeDraft()));
            }
            AvailabilityResult continued = withDisplayOffset(current, nextOffset);
            SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), continued, session.pendingConfirmation());
            return result(updated, decision, turnId, "SLOT_CHOICES", slotChoices(continued));
        }

        return continueFromDraft(session, decision, turnId);
    }

    private PatchResult applyPatch(SessionProjection session, ConversationDecision decision,
                                   AivaV2TemporalResolution temporal, ZoneId clinicZone) {
        BookingDraft draft = session.activeDraft();
        if (draft == null) return new PatchResult(session, null, false, false);
        var patch = decision.bookingPatch();
        boolean criteriaChanged = false;
        boolean providerTargeted = patch.doctorText().mode() != PatchMode.UNCHANGED
                || patch.specialtyText().mode() != PatchMode.UNCHANGED
                || patch.clinicText().mode() != PatchMode.UNCHANGED;
        ProviderCandidate provider = draft.selectedProvider();
        String specialty = draft.specialtyFilter();
        LocalDate date = draft.preferredDate();
        String window = draft.preferredTimeWindow();
        LocalTime exact = draft.exactTime();
        AvailabilityTimeConstraint constraint = draft.availabilityTimeConstraint();

        if (patch.specialtyText().mode() == PatchMode.CLEAR) { specialty = null; criteriaChanged = true; }
        if (patch.specialtyText().mode() == PatchMode.SET) { specialty = patch.specialtyText().value(); criteriaChanged = true; }
        boolean keepCurrentProvider = patch.doctorText().mode() == PatchMode.SET
                && provider != null && sameKnownProvider(patch.doctorText().value(), provider.displayName());
        if (patch.doctorText().mode() != PatchMode.UNCHANGED && !keepCurrentProvider) {
            provider = null;
            criteriaChanged = true;
        }
        if (keepCurrentProvider) providerTargeted = false;
        if (patch.dateExpression().mode() == PatchMode.CLEAR) { date = null; criteriaChanged = true; }
        if (patch.timeWindow().mode() == PatchMode.CLEAR) { window = null; criteriaChanged = true; }
        if (patch.exactTime().mode() == PatchMode.CLEAR) { exact = null; criteriaChanged = true; }
        AvailabilityTimeConstraintPatch constraintPatch = patch.availabilityTimeConstraint();
        if (constraintPatch.mode() == PatchMode.CLEAR) { constraint = null; criteriaChanged = true; }
        if (constraintPatch.mode() == PatchMode.SET) {
            constraint = constraintPatch.value();
            exact = null;
            criteriaChanged = true;
        }
        boolean currentTurnDate = temporal != null
                && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT;
        boolean dateRequested = patch.dateExpression().mode() == PatchMode.SET || currentTurnDate;
        boolean invalidDate = dateRequested
                && (temporal == null || temporal.status() != AivaV2TemporalResolution.Status.RESOLVED);
        boolean pastDate = dateRequested && !invalidDate && temporal.localDate() != null
                && temporal.localDate().isBefore(LocalDate.now(clock.withZone(clinicZone)));
        if (dateRequested && !invalidDate && !pastDate && temporal.localDate() != null) {
            date = temporal.localDate();
            criteriaChanged = true;
        }
        if (patch.timeWindow().mode() == PatchMode.SET) { window = patch.timeWindow().value(); criteriaChanged = true; }
        if (patch.exactTime().mode() == PatchMode.SET) {
            try { exact = LocalTime.parse(patch.exactTime().value()); constraint = null; criteriaChanged = true; }
            catch (DateTimeParseException ignored) { }
        }
        long revision = criteriaChanged ? draft.revision() + 1 : draft.revision();
        BookingDraft changed = copyDraft(draft, provider, specialty, date, window, exact, constraint,
                criteriaChanged ? null : draft.latestAvailabilityRequestId(),
                criteriaChanged ? null : draft.selectedSlotReference(),
                criteriaChanged ? DraftStatus.COLLECTING : draft.status(), revision,
                criteriaChanged ? null : draft.confirmedAppointmentReference());
        session = criteriaChanged ? replace(session, changed, null, null, null, null) : withActiveDraft(session, changed);

        ProviderSearchResult providerResult = null;
        if (providerTargeted) {
            String doctorText = patch.doctorText().mode() == PatchMode.SET ? patch.doctorText().value() : null;
            String clinicText = patch.clinicText().mode() == PatchMode.SET ? patch.clinicText().value() : null;
            providerResult = tools.resolveBookingProvider(doctorText, specialty, clinicText);
            session = replace(session, session.activeDraft(), null, providerResult, null, null);
            if (providerResult.status() == ResolutionStatus.RESOLVED) {
                BookingDraft resolved = copyDraft(session.activeDraft(), providerResult.resolvedProvider(), specialty, date,
                        window, exact, constraint, null, null, DraftStatus.COLLECTING,
                        session.activeDraft().revision(), null);
                session = withActiveDraft(session, resolved);
            }
        }
        return new PatchResult(session, providerResult, invalidDate, pastDate);
    }

    private KernelResult continueFromDraft(SessionProjection session, ConversationDecision decision, String turnId) {
        BookingDraft draft = session.activeDraft();
        if (draft == null) return result(session, decision, turnId, "CLARIFICATION", "How can I help with an appointment?");
        if (draft.selectedProvider() == null) {
            return result(session, decision, turnId, "NEED_PROVIDER", "Which doctor or specialty would you like?");
        }
        if (draft.selectedProvider().capabilities().callToBook()) {
            return result(session, decision, turnId, "CALL_TO_BOOK", "Online scheduling is not connected for "
                    + draft.selectedProvider().displayName() + ". Please contact the clinic directly.");
        }
        if (draft.preferredDate() == null) {
            return result(session, decision, turnId, "NEED_DATE", "What date would you prefer?");
        }
        if (draft.selectedSlotReference() != null && session.pendingConfirmation() != null) {
            return result(session, decision, turnId, "CONFIRMATION_REQUIRED", confirmationPrompt(draft, session.pendingConfirmation()));
        }
        ToolResult<AvailabilityResult> availability = tools.getBookingAvailability(draft);
        if (!"SUCCESS".equals(availability.category())) {
            return result(session, decision, turnId, availability.category(), safe(availability.safeReason(), "Availability could not be checked."));
        }
        AvailabilityResult value = availability.value();
        BookingDraft updatedDraft = copyDraft(draft, draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(),
                draft.preferredTimeWindow(), draft.exactTime(), value.requestId(), null,
                DraftStatus.COLLECTING, draft.revision(), null);
        SessionProjection updated = replace(session, updatedDraft, null, session.latestProviderResult(), value, null);
        if (value.slots().isEmpty()) {
            return result(updated, decision, turnId, "NO_AVAILABILITY", noAvailabilityMessage(updatedDraft));
        }
        return result(updated, decision, turnId, "SLOT_CHOICES", slotChoices(value));
    }

    private KernelResult lookupAppointments(SessionProjection session, ConversationDecision decision,
                                            AivaV2TemporalResolution temporal, String turnId) {
        if (appointmentLookupTool == null) {
            return result(session, decision, turnId, "FAILED", "Appointment information could not be retrieved.");
        }
        if (decision.unresolvedExplicitQualifierPresent()) {
            return result(session, decision, turnId, "LOOKUP_CLARIFICATION",
                    "Are you asking about an appointment with a specific doctor or specialty?");
        }
        AivaV2Models.AppointmentLookupFilter filter = decision.lookupFilter();
        if (filter.dateExpression().mode() == PatchMode.SET
                && (temporal == null || temporal.status() != AivaV2TemporalResolution.Status.RESOLVED)) {
            return result(session, decision, turnId, "CLARIFICATION", "Please provide a valid appointment date.");
        }
        LocalDate date = filter.dateExpression().mode() == PatchMode.SET ? temporal.localDate() : null;
        var lookup = appointmentLookupTool.lookup(filter, date, session.conversationId(), turnId);
        if (lookup.status() == AivaV2Models.AppointmentLookupStatus.FAILED) {
            log.info("AIVA_V2_LOOKUP_RESPONSE_TRACE conversationId={} turnId={} operation={} resultStatus={} resultCount={} responseCategory={}",
                    session.conversationId(), turnId, decision.operation(), lookup.status(), lookup.appointments().size(), "FAILED");
            return result(session, decision, turnId, "FAILED", lookup.safeReason());
        }
        if (lookup.status() == AivaV2Models.AppointmentLookupStatus.NONE) {
            log.info("AIVA_V2_LOOKUP_RESPONSE_TRACE conversationId={} turnId={} operation={} resultStatus={} resultCount={} responseCategory={}",
                    session.conversationId(), turnId, decision.operation(), lookup.status(), lookup.appointments().size(), "APPOINTMENTS_NONE");
            return result(session, decision, turnId, "APPOINTMENTS_NONE", appointmentLookupNoneMessage(filter, date),
                    AivaStructuredResponse.of(ResponseType.APPOINTMENTS_NONE,
                            new AppointmentListPayload(List.of(), appliedFilters(filter, date)), List.of()));
        }
        log.info("AIVA_V2_LOOKUP_RESPONSE_TRACE conversationId={} turnId={} operation={} resultStatus={} resultCount={} responseCategory={}",
                session.conversationId(), turnId, decision.operation(), lookup.status(), lookup.appointments().size(), "APPOINTMENTS_FOUND");
        return result(session, decision, turnId, "APPOINTMENTS_FOUND", appointmentMessage(lookup.appointments()),
                AivaStructuredResponse.of(ResponseType.APPOINTMENTS_FOUND,
                        new AppointmentListPayload(appointmentFacts(lookup.appointments()), appliedFilters(filter, date)), List.of()));
    }

    private String appointmentMessage(List<AivaV2Models.AppointmentSummary> appointments) {
        if (appointments.size() == 1) return appointmentLine(appointments.get(0));
        StringBuilder message = new StringBuilder("You have ").append(appointments.size())
                .append(" upcoming appointments: ");
        for (int i = 0; i < appointments.size(); i++) {
            if (i > 0) message.append("; ");
            message.append(i + 1).append(". ").append(appointmentLine(appointments.get(i)));
        }
        return message.toString();
    }

    private KernelResult handleReschedule(SessionProjection session, ConversationDecision decision,
                                          AivaV2TemporalResolution temporal, String turnId) {
        RescheduleResolution traceState = session.pendingReschedule();
        org.slf4j.LoggerFactory.getLogger(AivaV2TransactionalKernel.class).info(
                "AIVA_V2_RESCHEDULE_TRACE operation={} sourceCandidateCount={} sourceResolutionStatus={} "
                        + "sourceClinicPresent={} sourceTenantPresent={} targetDatePresent={} "
                        + "availabilityResultPresent={} slotSelected={} confirmationPending={} "
                        + "confirmationResult={} staleReason={} idempotentReplay={} activeWorkflow=RESCHEDULE "
                        + "rescheduleProviderBound={} rescheduleTargetDate={} availabilityControl={} "
                        + "providerCallRequired={} sourceLookupInvoked={} sourceAppointmentBound={} stateOwner=RESCHEDULE",
                decision.operation(), traceState == null ? 0 : traceState.sourceCandidates().size(),
                traceState == null ? "NONE" : traceState.status(),
                traceState != null && StringUtils.hasText(traceState.clinicId()),
                traceState != null && StringUtils.hasText(traceState.tenantId()),
                traceState != null && traceState.targetDate() != null,
                traceState != null && traceState.latestAvailability() != null,
                traceState != null && StringUtils.hasText(traceState.selectedSlotReference()),
                session.pendingRescheduleConfirmation() != null,
                "NONE", "NONE", false,
                traceState != null && StringUtils.hasText(traceState.providerHandle()),
                traceState == null ? null : traceState.targetDate(),
                decision.operation() == Operation.SHOW_MORE_SLOTS ? "SHOW_MORE"
                        : decision.bookingPatch().timeWindow().mode() == PatchMode.SET
                        ? decision.bookingPatch().timeWindow().value()
                        : decision.bookingPatch().availabilityTimeConstraint().mode() == PatchMode.SET
                        && decision.bookingPatch().availabilityTimeConstraint().value() != null
                        ? decision.bookingPatch().availabilityTimeConstraint().value().mode().name() : null,
                decision.operation() != Operation.SHOW_MORE_SLOTS,
                traceState == null || decision.operation() == Operation.RESCHEDULE_APPOINTMENT,
                traceState != null && StringUtils.hasText(traceState.sourceAppointmentReference()));
        if (rescheduleTools == null || appointmentLookupTool == null) {
            return result(session, decision, turnId, "FAILED", "Rescheduling is temporarily unavailable.");
        }
        RescheduleResolution state = session.pendingReschedule();
        if (state == null || decision.operation() == Operation.RESCHEDULE_APPOINTMENT) {
            var filter = decision.lookupFilter();
            LocalDate date = temporal != null && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED
                    && (filter.dateExpression().mode() == PatchMode.SET
                    || temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT)
                    ? temporal.localDate() : null;
            if (date != null && filter.dateExpression().mode() != PatchMode.SET) {
                filter = new AivaV2Models.AppointmentLookupFilter(filter.doctorText(),
                        ValuePatch.set(date.toString()), filter.status(), filter.clinicText(), filter.nextOnly());
            }
            var lookup = appointmentLookupTool.lookup(filter, date, session.conversationId(), turnId);
            if (lookup.appointments().isEmpty()) {
                return result(session, decision, turnId, "RESCHEDULE_NONE", "I couldn't find an upcoming appointment to reschedule.");
            }
            AppointmentSummary source = selectCancellationCandidate(lookup.appointments(), decision.selection());
            if (lookup.appointments().size() > 1 && source == null) {
                RescheduleResolution pending = new RescheduleResolution(null, filter, lookup.appointments(),
                        null, null, null, null, null, null, null, null, null, null, null, null, null,
                        null, RescheduleStatus.SOURCE_AMBIGUOUS, 1,
                        Instant.now(clock), Instant.now(clock).plusSeconds(5 * 60));
                return result(storeReschedule(session, pending), decision, turnId, "RESCHEDULE_SOURCE_CHOICES",
                        "I found more than one matching appointment. Which one would you like to reschedule?");
            }
            source = source == null ? lookup.appointments().get(0) : source;
            ProviderCandidate provider = rescheduleTools.providerFor(source);
            if (provider == null) return result(session, decision, turnId, "RESCHEDULE_UNAVAILABLE", "I couldn't bind the appointment safely.");
            RescheduleResolution bound = new RescheduleResolution(source.appointmentReference(), source.doctorDisplayName(),
                    provider.providerHandle(), source.doctorId(), source.clinicId(), source.tenantId(), source.clinicSlug(),
                    source.date(), source.time(), null, null, null, null, null, null, RescheduleStatus.TARGET_COLLECTING, 1,
                    Instant.now(clock), Instant.now(clock).plusSeconds(5 * 60));
            return result(storeReschedule(session, bound), decision, turnId, "RESCHEDULE_SOURCE_RESOLVED",
                    "You have an appointment with " + source.doctorDisplayName() + " on "
                            + source.date().format(HUMAN_DATE) + " at " + source.time() + ". What date would you like to move it to?");
        }
        if (state.expiresAt() != null && !state.expiresAt().isAfter(Instant.now(clock))) {
            return result(session, decision, turnId, "STALE", "The reschedule request has expired. Please start again.");
        }
        if (decision.operation() == Operation.SELECT_RESCHEDULE_SLOT) {
            ToolResult<AvailabilitySlot> selected = rescheduleTools.select(state.latestAvailability(), decision.selection());
            if (!"SUCCESS".equals(selected.category())) return result(session, decision, turnId, selected.category(), selected.safeReason());
            RescheduleResolution chosen = rescheduleState(state, state.targetDate(), state.availabilityConstraint(),
                    state.latestAvailability(), selected.value().slotReference(), RescheduleStatus.CONFIRMATION_PENDING);
            ToolResult<RescheduleConfirmation> prepared = rescheduleTools.prepare(chosen, chosen.latestAvailability(), selected.value());
            if (!"SUCCESS".equals(prepared.category())) return result(session, decision, turnId, prepared.category(), prepared.safeReason());
            SessionProjection updated = replaceReschedule(session, chosen, prepared.value());
            return result(updated, decision, turnId, "RESCHEDULE_CONFIRMATION",
                    "Move your appointment with " + chosen.originalDoctorDisplayName() + " from "
                            + chosen.originalDate().format(HUMAN_DATE) + " at " + chosen.originalStartsAt()
                            + " to " + chosen.targetDate().format(HUMAN_DATE) + " at " + chosen.selectedStartsAt() + "? Shall I reschedule it?");
        }
        if (decision.operation() == Operation.SHOW_MORE_SLOTS) {
            AvailabilityResult current = state.latestAvailability();
            if (current == null) return result(session, decision, turnId, "STALE", "Those reschedule slots are no longer current.");
            int nextOffset = current.displayOffset() + current.pageSize();
            if (nextOffset >= current.slots().size()) return result(session, decision, turnId, "NO_MORE_SLOTS", "There are no more matching reschedule slots.");
            AvailabilityResult continued = withDisplayOffset(current, nextOffset);
            RescheduleResolution continuedState = rescheduleState(state, state.targetDate(), state.availabilityConstraint(), continued,
                    null, RescheduleStatus.AVAILABILITY_READY);
            return result(storeReschedule(session, continuedState), decision, turnId, "RESCHEDULE_SLOT_CHOICES", rescheduleSlotChoices(continued));
        }
        LocalDate targetDate = state.targetDate();
        if (temporal != null && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED) targetDate = temporal.localDate();
        AvailabilityTimeConstraint constraint = state.availabilityConstraint();
        var constraintPatch = decision.bookingPatch().availabilityTimeConstraint();
        if (constraintPatch.mode() == PatchMode.SET) constraint = constraintPatch.value();
        if (constraintPatch.mode() == PatchMode.CLEAR) constraint = null;
        if (constraintPatch.mode() != PatchMode.SET && constraintPatch.mode() != PatchMode.CLEAR
                && decision.bookingPatch().timeWindow().mode() == PatchMode.SET) {
            constraint = daypartConstraint(decision.bookingPatch().timeWindow().value());
        }
        if (decision.bookingPatch().exactTime().mode() == PatchMode.SET) {
            try {
                constraint = new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.EXACT,
                        LocalTime.parse(decision.bookingPatch().exactTime().value()), null);
            } catch (RuntimeException ignored) {
                return result(session, decision, turnId, "INVALID", "I couldn't use that time.");
            }
        }
        RescheduleResolution updatedState = rescheduleState(state, targetDate, constraint, null, null,
                RescheduleStatus.TARGET_COLLECTING);
        if (targetDate == null) return result(storeReschedule(session, updatedState), decision, turnId,
                "RESCHEDULE_NEED_DATE", "What date would you like to move it to?");
        ToolResult<AvailabilityResult> availability = rescheduleTools.availability(updatedState);
        if (!"SUCCESS".equals(availability.category())) return result(storeReschedule(session, updatedState), decision,
                turnId, availability.category(), availability.safeReason());
        RescheduleResolution ready = rescheduleState(updatedState, targetDate, constraint, availability.value(), null,
                RescheduleStatus.AVAILABILITY_READY);
        SessionProjection updated = storeReschedule(session, ready);
        return result(updated, decision, turnId, availability.value().slots().isEmpty() ? "NO_AVAILABILITY" : "RESCHEDULE_SLOT_CHOICES",
                availability.value().slots().isEmpty() ? "There are no available slots for " + targetDate.format(HUMAN_DATE) + "."
                        : rescheduleSlotChoices(availability.value()));
    }

    private AvailabilityTimeConstraint daypartConstraint(String timeWindow) {
        if (!StringUtils.hasText(timeWindow)) return null;
        String value = timeWindow.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("morning")) return new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.BEFORE, null, LocalTime.NOON);
        if (value.contains("afternoon")) return new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.BETWEEN,
                LocalTime.NOON, LocalTime.of(17, 0));
        if (value.contains("evening")) return new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.AFTER,
                LocalTime.of(17, 0), null);
        return null;
    }

    private KernelResult confirmReschedule(SessionProjection session, ConversationDecision decision, String turnId) {
        RescheduleConfirmation confirmation = session.pendingRescheduleConfirmation();
        RescheduleResolution state = session.pendingReschedule();
        if (confirmation == null || state == null) return result(session, decision, turnId, "STALE", "There is no current reschedule to confirm.");
        if (state.status() == RescheduleStatus.COMPLETED) return result(session, decision, turnId, "RESCHEDULE_CONFIRMED",
                "Your appointment has been rescheduled to " + confirmation.targetDate().format(HUMAN_DATE) + " at " + confirmation.targetStartsAt() + ".");
        ToolResult<String> confirmed = rescheduleTools.confirm(confirmation, session.conversationId(), turnId);
        if (!"SUCCESS".equals(confirmed.category())) return result(session, decision, turnId, confirmed.category(), confirmed.safeReason());
        SessionProjection updated = replaceReschedule(session, rescheduleState(state, state.targetDate(),
                state.availabilityConstraint(), state.latestAvailability(), state.selectedSlotReference(),
                RescheduleStatus.COMPLETED), confirmation);
        return result(updated, decision, turnId, "RESCHEDULE_CONFIRMED", "Your appointment has been rescheduled to "
                        + confirmation.targetDate().format(HUMAN_DATE) + " at " + confirmation.targetStartsAt() + ".",
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_SUCCESS,
                        new RescheduleSuccessPayload(state.originalDoctorDisplayName(), state.originalDate(),
                                state.originalStartsAt(), confirmation.targetDate(), confirmation.targetStartsAt()), List.of()));
    }

    private RescheduleResolution rescheduleState(RescheduleResolution state, LocalDate targetDate,
                                                 AvailabilityTimeConstraint constraint, AvailabilityResult availability,
                                                 String selectedSlot, RescheduleStatus status) {
        return new RescheduleResolution(state.sourceAppointmentReference(), state.sourceCriteria(), state.sourceCandidates(),
                state.originalDoctorDisplayName(), state.providerHandle(),
                state.doctorId(), state.clinicId(), state.tenantId(), state.clinicSlug(), state.originalDate(), state.originalStartsAt(),
                targetDate, constraint, availability == null ? null : availability.requestId(),
                availability, selectedSlot,
                selectedSlot == null ? null : availability == null ? state.selectedStartsAt() : availability.slots().stream()
                        .filter(slot -> selectedSlot.equals(slot.slotReference())).map(AvailabilitySlot::startsAt).findFirst().orElse(null),
                status, state.revision() + 1, state.createdAt(), state.expiresAt());
    }

    private boolean hasBoundRescheduleTargetRefinement(ConversationDecision decision,
                                                       AivaV2TemporalResolution temporal) {
        BookingPatch patch = decision.bookingPatch();
        if (patch.dateExpression().mode() == PatchMode.SET
                || patch.timeWindow().mode() == PatchMode.SET
                || patch.exactTime().mode() == PatchMode.SET
                || patch.availabilityTimeConstraint().mode() != PatchMode.UNCHANGED) {
            return true;
        }
        return temporal != null
                && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED
                && decision.dialogAct() == AivaV2Models.DialogAct.CHANGE_INFORMATION
                && decision.lookupFilter().doctorText().mode() != PatchMode.SET
                && decision.lookupFilter().clinicText().mode() != PatchMode.SET;
    }

    private String targetRefinementSource(ConversationDecision decision,
                                          AivaV2TemporalResolution temporal) {
        BookingPatch patch = decision.bookingPatch();
        if (patch.dateExpression().mode() == PatchMode.SET
                || patch.timeWindow().mode() == PatchMode.SET
                || patch.exactTime().mode() == PatchMode.SET
                || patch.availabilityTimeConstraint().mode() != PatchMode.UNCHANGED) {
            return "PATCH";
        }
        if (temporal != null
                && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED) {
            return "TEMPORAL";
        }
        return "NONE";
    }

    private ConversationDecision withOperation(ConversationDecision decision, Operation operation) {
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), operation,
                decision.bookingPatch(), decision.selection(), decision.confirmation(), decision.topicAction(),
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private SessionProjection storeReschedule(SessionProjection session, RescheduleResolution state) {
        return replace(session, session.activeDraft(), session.suspendedDraft(), session.latestProviderResult(),
                session.latestAvailabilityResult(), session.pendingConfirmation(), session.pendingCancellation(),
                session.pendingCancellationResolution(), state, null);
    }

    private SessionProjection replaceReschedule(SessionProjection session, RescheduleResolution state,
                                                RescheduleConfirmation confirmation) {
        return replace(session, session.activeDraft(), session.suspendedDraft(), session.latestProviderResult(),
                session.latestAvailabilityResult(), session.pendingConfirmation(), session.pendingCancellation(),
                session.pendingCancellationResolution(), state, confirmation);
    }

    private String rescheduleSlotChoices(AvailabilityResult availability) {
        StringBuilder message = new StringBuilder("Available slots for ").append(availability.date().format(HUMAN_DATE)).append(": ");
        int max = Math.min(availability.slots().size(), availability.displayOffset() + availability.pageSize());
        for (int i = availability.displayOffset(); i < max; i++) {
            if (i > availability.displayOffset()) message.append("; ");
            message.append(i - availability.displayOffset() + 1).append(". ").append(availability.slots().get(i).displayTime());
        }
        return message.append(". Which one works?").toString();
    }

    private KernelResult prepareCancellation(SessionProjection session, ConversationDecision decision,
                                             AivaV2TemporalResolution temporal, String turnId) {
        if (appointmentLookupTool == null || cancellationTool == null) {
            return result(session, decision, turnId, "FAILED", "Cancellation is temporarily unavailable.");
        }
        var requestedFilter = decision.lookupFilter();
        // Cancellation targets upcoming appointments; persistence status is domain-owned.
        var filter = new AivaV2Models.AppointmentLookupFilter(
                requestedFilter.doctorText(), requestedFilter.dateExpression(), new ValuePatch(PatchMode.UNCHANGED, null),
                requestedFilter.clinicText(), requestedFilter.nextOnly());
        LocalDate date = temporal != null && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED
                && (filter.dateExpression().mode() == PatchMode.SET
                || temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT)
                ? temporal.localDate() : null;
        if (date != null && filter.dateExpression().mode() != PatchMode.SET) {
            filter = new AivaV2Models.AppointmentLookupFilter(filter.doctorText(), ValuePatch.set(date.toString()),
                    filter.status(), filter.clinicText(), filter.nextOnly());
        }
        var lookup = appointmentLookupTool.lookup(filter, date, session.conversationId(), turnId);
        if (lookup.appointments().isEmpty()) {
            log.info("AIVA_V2_CANCELLATION_TRACE operation={} pendingCancellationPresent={} doctorFilterPresent={} "
                            + "dateFilterPresent={} clinicFilterPresent={} candidateCount=0 resolutionStatus=NOT_FOUND activeWorkflow=CANCELLATION",
                    decision.operation(), session.pendingCancellationResolution() != null,
                    StringUtils.hasText(lookupValue(filter.doctorText())), date != null,
                    StringUtils.hasText(lookupValue(filter.clinicText())));
            return result(session, decision, turnId, "CANCELLATION_NONE",
                    "I couldn't find an upcoming appointment to cancel.");
        }
        AppointmentSummary selected = selectCancellationCandidate(lookup.appointments(), decision.selection());
        if (selected != null) {
            return prepareCancellationConfirmation(session, decision, turnId, selected);
        }
        if (lookup.appointments().size() > 1) {
            CancellationResolution pending = new CancellationResolution(filter, lookup.appointments(),
                    Instant.now(clock), Instant.now(clock).plusSeconds(5 * 60));
            SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                    session.latestProviderResult(), session.latestAvailabilityResult(), session.pendingConfirmation(),
                    null, pending);
            log.info("AIVA_V2_CANCELLATION_TRACE operation={} pendingCancellationPresent=true doctorFilterPresent={} "
                            + "dateFilterPresent={} clinicFilterPresent={} candidateCount={} resolutionStatus=AMBIGUOUS activeWorkflow=CANCELLATION",
                    decision.operation(), StringUtils.hasText(lookupValue(filter.doctorText())),
                    date != null, StringUtils.hasText(lookupValue(filter.clinicText())), lookup.appointments().size());
            return result(updated, decision, turnId, "CANCELLATION_CHOICES",
                    "I found more than one matching appointment. Which one would you like to cancel?");
        }
        AppointmentSummary appointment = lookup.appointments().get(0);
        return prepareCancellationConfirmation(session, decision, turnId, appointment);
    }

    private KernelResult prepareCancellationConfirmation(SessionProjection session, ConversationDecision decision,
                                                         String turnId, AppointmentSummary appointment) {
        var prepared = cancellationTool.prepare(appointment, Instant.now(clock));
        if (!"SUCCESS".equals(prepared.category())) {
            return result(session, decision, turnId, prepared.category(), prepared.safeReason());
        }
        SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                session.latestProviderResult(), session.latestAvailabilityResult(), session.pendingConfirmation(),
                prepared.value(), null);
        log.info("AIVA_V2_CANCELLATION_TRACE operation={} pendingCancellationPresent=true doctorFilterPresent={} "
                        + "dateFilterPresent={} clinicFilterPresent={} candidateCount=1 resolutionStatus=RESOLVED "
                        + "owningClinicPresent={} owningTenantPresent={} confirmationPending=true activeWorkflow=CANCELLATION",
                decision.operation(), StringUtils.hasText(lookupValue(decision.lookupFilter().doctorText())),
                appointment.date() != null, StringUtils.hasText(lookupValue(decision.lookupFilter().clinicText())),
                StringUtils.hasText(appointment.clinicDisplayName()), true);
        return result(updated, decision, turnId, "CANCELLATION_CONFIRMATION", cancellationPrompt(appointment));
    }

    private AppointmentSummary selectCancellationCandidate(List<AppointmentSummary> candidates, Selection selection) {
        if (selection == null) return null;
        if (StringUtils.hasText(selection.candidateRef())) {
            return candidates.stream()
                    .filter(candidate -> selection.candidateRef().equals(candidate.appointmentReference()))
                    .findFirst().orElse(null);
        }
        if (selection.ordinal() == null || selection.ordinal() < 1) return null;
        int index = selection.ordinal() - 1;
        return index < candidates.size() ? candidates.get(index) : null;
    }

    private KernelResult confirmCancellation(SessionProjection session, ConversationDecision decision, String turnId) {
        CancellationConfirmation confirmation = session.pendingCancellation();
        if (confirmation == null) {
            return result(session, decision, turnId, "STALE", "There is no current cancellation to confirm.");
        }
        var cancelled = cancellationTool.cancel(confirmation);
        if (!"SUCCESS".equals(cancelled.category())) {
            return result(session, decision, turnId, cancelled.category(), cancelled.safeReason());
        }
        SessionProjection updated = replace(session, session.activeDraft(), session.suspendedDraft(),
                session.latestProviderResult(), session.latestAvailabilityResult(), session.pendingConfirmation(), null);
        log.info("AIVA_V2_CANCELLATION_TRACE operation={} candidateCount=1 resolutionStatus=RESOLVED "
                        + "owningClinicPresent=true owningTenantPresent=true confirmationPending=false "
                        + "confirmationResult=SUCCESS", decision.operation());
        return result(updated, decision, turnId, "CANCELLATION_CONFIRMED",
                "Your appointment with " + confirmation.doctorDisplayName() + " on " + confirmation.date()
                        + " at " + confirmation.startsAt() + " has been cancelled.",
                AivaStructuredResponse.of(ResponseType.CANCELLATION_SUCCESS,
                        new CancellationSuccessPayload(cancellationFact(confirmation)), List.of()));
    }

    private String cancellationPrompt(AppointmentSummary appointment) {
        return "You have an appointment with " + appointment.doctorDisplayName() + " on "
                + appointment.date() + " at " + appointment.time() + ". Shall I cancel it?";
    }

    private String unknownClarification(SessionProjection session) {
        if (session.pendingCancellation() != null || session.pendingCancellationResolution() != null) {
            return "I couldn't understand which appointment you want to cancel.";
        }
        if (session.latestAvailabilityResult() != null) {
            return "I couldn't understand that time range. What time would you like?";
        }
        return session.activeDraft() == null
                ? "Sorry, I couldn't understand that request."
                : "I couldn't understand that booking request.";
    }

    private String appointmentLine(AivaV2Models.AppointmentSummary appointment) {
        String doctor = StringUtils.hasText(appointment.doctorDisplayName())
                ? appointment.doctorDisplayName() : "your doctor";
        String date = appointment.date() == null ? "the scheduled date" : appointment.date().format(HUMAN_DATE);
        String time = appointment.time() == null ? "the scheduled time" : appointment.time().toString();
        return "You have an appointment with " + doctor + " on " + date + " at " + time + ".";
    }

    private String appointmentLookupNoneMessage(AivaV2Models.AppointmentLookupFilter filter, LocalDate date) {
        String doctor = lookupValue(filter.doctorText());
        if (StringUtils.hasText(doctor)) {
            return "You do not have any upcoming appointments with " + doctor + ".";
        }
        if (date != null) {
            return "You do not have any upcoming appointments on " + date.format(HUMAN_DATE) + ".";
        }
        String clinic = lookupValue(filter.clinicText());
        if (StringUtils.hasText(clinic)) {
            return "You do not have any upcoming appointments at " + clinic + ".";
        }
        return "You do not have any upcoming appointments.";
    }

    private String lookupValue(ValuePatch patch) {
        return patch != null && patch.mode() == PatchMode.SET ? patch.value() : null;
    }

    private KernelResult selectCurrentCandidate(SessionProjection session, ConversationDecision decision, String turnId) {
        Selection selection = decision.selection();
        AvailabilityResult availability = session.latestAvailabilityResult();
        BookingDraft draft = session.activeDraft();
        if (availability == null && session.latestProviderResult() != null) {
            ProviderCandidate selected = selectProvider(session.latestProviderResult(), selection);
            if (selected == null) return result(session, decision, turnId, "CLARIFICATION", "Please select one of the current doctors.");
            BookingDraft changed = copyDraft(draft, selected, draft.specialtyFilter(), draft.preferredDate(),
                    draft.preferredTimeWindow(), draft.exactTime(), null, null, DraftStatus.COLLECTING,
                    draft.revision() + 1, null);
            return continueFromDraft(replace(session, changed, null, session.latestProviderResult(), null, null), decision, turnId);
        }
        if (!availabilityIsCurrent(draft, availability)) {
            return result(session, decision, turnId, "STALE", "Those slots are no longer current. I will check again.");
        }
        ToolResult<AvailabilitySlot> slotResult = tools.selectBookingSlot(draft, availability, selection);
        if (!"SUCCESS".equals(slotResult.category())) {
            return result(session, decision, turnId, slotResult.category(),
                    safe(slotResult.safeReason(), "Please select one of the current slots."));
        }
        AvailabilitySlot slot = slotResult.value();
        BookingDraft selected = copyDraft(draft, draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(),
                draft.preferredTimeWindow(), slot.startsAt(), availability.requestId(), slot.slotReference(),
                DraftStatus.COLLECTING, draft.revision(), null);
        ToolResult<BookingConfirmation> prepared = tools.prepareBooking(selected, availability);
        if (!"SUCCESS".equals(prepared.category())) {
            return result(replace(session, selected, null, session.latestProviderResult(), availability, null), decision,
                    turnId, prepared.category(), safe(prepared.safeReason(), "That slot is no longer available."));
        }
        BookingDraft ready = copyDraft(selected, selected.selectedProvider(), selected.specialtyFilter(), selected.preferredDate(),
                selected.preferredTimeWindow(), selected.exactTime(), selected.latestAvailabilityRequestId(),
                selected.selectedSlotReference(), DraftStatus.READY_FOR_CONFIRMATION, selected.revision(), null);
        SessionProjection updated = replace(session, ready, null, session.latestProviderResult(), availability, prepared.value());
        return result(updated, decision, turnId, "CONFIRMATION_REQUIRED", confirmationPrompt(ready, prepared.value()));
    }

    private KernelResult confirm(SessionProjection session, ConversationDecision decision, String turnId) {
        BookingDraft draft = session.activeDraft();
        BookingConfirmation confirmation = session.pendingConfirmation();
        if (draft == null || confirmation == null || draft.status() != DraftStatus.READY_FOR_CONFIRMATION) {
            return result(session, decision, turnId, "CLARIFICATION", "There is no current booking ready to confirm.");
        }
        ToolResult<AivaV2Models.BookingReceipt> booked = tools.confirmBooking(draft, confirmation);
        if (!"SUCCESS".equals(booked.category())) {
            return result(session, decision, turnId, booked.category(), safe(booked.safeReason(), "I could not confirm the booking."));
        }
        BookingDraft confirmed = copyDraft(draft, draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(),
                draft.preferredTimeWindow(), draft.exactTime(), draft.latestAvailabilityRequestId(),
                draft.selectedSlotReference(), DraftStatus.CONFIRMED, draft.revision(), booked.value().appointmentReference());
        SessionProjection updated = replace(session, confirmed, null, session.latestProviderResult(), session.latestAvailabilityResult(), null);
        return result(updated, decision, turnId, "BOOKING_CONFIRMED",
                "Your appointment is booked. Reference: " + booked.value().appointmentReference() + ".");
    }

    private KernelResult answerContext(SessionProjection session, ConversationDecision decision, String turnId) {
        AvailabilityResult availability = session.latestAvailabilityResult();
        if (availability != null) {
            return result(session, decision, turnId, "CONTEXT_ANSWER", "These slots are for " + availability.date() + ".");
        }
        BookingDraft draft = session.activeDraft();
        if (draft != null && draft.selectedProvider() != null) {
            return result(session, decision, turnId, "CONTEXT_ANSWER", "The selected doctor is " + draft.selectedProvider().displayName() + ".");
        }
        return result(session, decision, turnId, "CLARIFICATION", "There is no current booking context yet.");
    }

    private ProviderCandidate selectProvider(ProviderSearchResult result, Selection selection) {
        int index = selection.ordinal() == null ? -1 : selection.ordinal() == -1 ? result.candidates().size() - 1 : selection.ordinal() - 1;
        if (StringUtils.hasText(selection.candidateRef())) {
            return result.candidates().stream().filter(candidate -> selection.candidateRef().equals(candidate.candidateHandle())).findFirst().orElse(null);
        }
        return index >= 0 && index < result.candidates().size() ? result.candidates().get(index) : null;
    }

    private KernelResult resolveProviderCandidate(SessionProjection session, ConversationDecision decision,
                                                  String turnId) {
        BookingDraft draft = session.activeDraft();
        ProviderSearchResult result = session.latestProviderResult();
        ProviderCandidate selected = decision.selection() == null ? null : selectProvider(result, decision.selection());
        if (selected == null) {
            return result(session, decision, turnId, "PROVIDER_CHOICES", providerChoices(result));
        }
        BookingDraft changed = copyDraft(draft, selected, draft.specialtyFilter(), draft.preferredDate(),
                draft.preferredTimeWindow(), draft.exactTime(), draft.availabilityTimeConstraint(), null, null,
                DraftStatus.COLLECTING, draft.revision() + 1, null);
        SessionProjection updated = replace(session, changed, null, null, null, null);
        return continueFromDraft(updated, decision, turnId);
    }

    private boolean availabilityIsCurrent(BookingDraft draft, AvailabilityResult result) {
        return draft != null && result != null && result.draftId().equals(draft.draftId())
                && result.draftRevision() == draft.revision()
                && result.requestId().equals(draft.latestAvailabilityRequestId())
                && result.criteriaFingerprint().equals(tools.criteriaFingerprint(draft));
    }

    private boolean requiresDraft(ConversationDecision decision) {
        return decision.operation() != Operation.UNKNOWN && decision.operation() != Operation.RESUME_BOOKING;
    }

    private String providerChoices(ProviderSearchResult result) {
        StringBuilder message = new StringBuilder("I found these doctors: ");
        for (int i = 0; i < Math.min(result.candidates().size(), 3); i++) {
            if (i > 0) message.append("; ");
            message.append(i + 1).append(". ").append(result.candidates().get(i).displayName());
        }
        return message.append(". Which one would you like?").toString();
    }

    private AvailabilityResult withDisplayOffset(AvailabilityResult result, int offset) {
        return new AvailabilityResult(result.requestId(), result.draftId(), result.draftRevision(),
                result.criteriaFingerprint(), result.providerHandle(), result.doctorId(), result.clinicId(),
                result.date(), result.timeWindow(), result.availabilityTimeConstraint(), result.timezone(), result.slots(), result.cursor(),
                offset + result.pageSize() < result.slots().size(), result.generatedAt(), result.expiresAt(),
                offset, result.pageSize());
    }

    private String slotChoices(AvailabilityResult result) {
        int offset = result.displayOffset();
        int end = Math.min(result.slots().size(), offset + result.pageSize());
        StringBuilder message = new StringBuilder("Available slots for ").append(result.date()).append(": ");
        for (int i = offset; i < end; i++) {
            if (i > offset) message.append("; ");
            message.append(i + 1).append(". ").append(result.slots().get(i).displayTime());
        }
        return message.append(". Which one works?").toString();
    }

    private String noMoreSlotsMessage(BookingDraft draft) {
        return draft.selectedProvider().displayName() + " has no more available slots on "
                + draft.preferredDate().format(HUMAN_DATE) + ". Would you like to check another date or time?";
    }

    private String confirmationPrompt(BookingDraft draft, BookingConfirmation confirmation) {
        return draft.selectedProvider().displayName() + " on " + confirmation.date() + " at "
                + confirmation.startsAt() + ". Shall I book it?";
    }

    private String noAvailabilityMessage(BookingDraft draft) {
        return draft.selectedProvider().displayName() + " has no available slots on "
                + draft.preferredDate().format(HUMAN_DATE)
                + timeConstraintMessage(draft.availabilityTimeConstraint())
                + ". Would you like me to check another date?";
    }

    private String timeConstraintMessage(AvailabilityTimeConstraint constraint) {
        if (constraint == null) return "";
        return switch (constraint.mode()) {
            case EXACT -> " at " + constraint.startTime();
            case AFTER -> " after " + constraint.startTime();
            case BEFORE -> " before " + constraint.endTime();
            case BETWEEN -> " between " + constraint.startTime() + " and " + constraint.endTime();
        };
    }

    private String pastDateMessage(LocalDate date) {
        return date.format(HUMAN_DATE) + " is in the past. Please choose today or a future date.";
    }

    private boolean sameKnownProvider(String candidate, String selectedName) {
        if (!StringUtils.hasText(candidate) || !StringUtils.hasText(selectedName)) return false;
        String left = candidate.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceFirst("^(doctor|dr|doc)\\s+", "");
        String right = selectedName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceFirst("^(doctor|dr|doc)\\s+", "");
        return right.equals(left) || right.contains(left);
    }

    private KernelResult result(SessionProjection session, ConversationDecision decision, String turnId,
                                String category, String message) {
        List<InteractiveAction> actions = interactiveActions(session, category);
        return result(session, decision, turnId, category, message,
                structuredResponse(session, category, actions));
    }

    private KernelResult result(SessionProjection session, ConversationDecision decision, String turnId,
                                String category, String message, AivaStructuredResponse structured) {
        log.info("AIVA_V2_TRACE conversationId={} turnId={} provider={} fallbackUsed={} dialogAct={} operation={} category={} "
                        + "draftRevision={} draftStatus={} providerResolved={} datePresent={} slotSelected={} confirmationPending={}",
                session.conversationId(), turnId, decision.provider(), decision.fallbackUsed(), decision.dialogAct(), decision.operation(), category,
                session.activeDraft() == null ? null : session.activeDraft().revision(),
                session.activeDraft() == null ? null : session.activeDraft().status(),
                session.activeDraft() != null && session.activeDraft().selectedProvider() != null,
                session.activeDraft() != null && session.activeDraft().preferredDate() != null,
                session.activeDraft() != null && session.activeDraft().selectedSlotReference() != null,
                session.pendingConfirmation() != null);
        List<InteractiveAction> actions = interactiveActions(session, category);
        MessageResponse response = new MessageResponse(session.conversationId(), turnId, message, category,
                view(session.activeDraft(), session.pendingConfirmation()), decision.provider(), decision.fallbackUsed(),
                actions, structured == null ? structuredResponse(session, category, actions) : structured);
        return new KernelResult(session, response);
    }

    private AivaStructuredResponse structuredResponse(SessionProjection session, String category,
                                                       List<InteractiveAction> actions) {
        BookingDraft draft = session.activeDraft();
        ProviderCandidate provider = draft == null ? null : draft.selectedProvider();
        RescheduleResolution reschedule = session.pendingReschedule();
        AvailabilityResult availability = reschedule == null
                ? session.latestAvailabilityResult() : reschedule.latestAvailability();
        ResponseType type;
        Payload payload;
        switch (category) {
            case "NEED_PROVIDER" -> {
                type = ResponseType.NEED_PROVIDER;
                payload = new NeedProviderPayload(draft == null ? null : draft.specialtyFilter(), null);
            }
            case "PROVIDER_CHOICES", "PROVIDER_SUGGESTION" -> {
                type = ResponseType.PROVIDER_CHOICES;
                ProviderSearchResult result = session.latestProviderResult();
                List<ProviderOption> candidates = result == null ? List.of() : result.candidates().stream()
                        .limit(3).map(candidate -> new ProviderOption(candidate.candidateHandle(),
                                candidate.displayName(), candidate.specialty(), candidate.clinicDisplayName())).toList();
                payload = new ProviderChoicesPayload(candidates);
            }
            case "PROVIDER_NOT_FOUND" -> {
                type = ResponseType.PROVIDER_NOT_FOUND;
                payload = new ProviderNotFoundPayload(null, draft == null ? null : draft.specialtyFilter());
            }
            case "NEED_DATE", "RESCHEDULE_NEED_DATE", "RESCHEDULE_SOURCE_RESOLVED" -> {
                if (category.startsWith("RESCHEDULE")) {
                    type = ResponseType.RESCHEDULE_NEED_DATE;
                    payload = new RescheduleNeedDatePayload(reschedule == null ? null : reschedule.originalDoctorDisplayName(),
                            reschedule == null ? null : appointmentFact(reschedule));
                } else {
                    type = ResponseType.NEED_DATE;
                    payload = new NeedDatePayload(provider == null ? null : provider.displayName());
                }
            }
            case "SLOT_CHOICES", "RESCHEDULE_SLOT_CHOICES", "NO_AVAILABILITY" -> {
                boolean isReschedule = reschedule != null || "RESCHEDULE_SLOT_CHOICES".equals(category);
                type = isReschedule ? ResponseType.RESCHEDULE_AVAILABLE_SLOTS : ResponseType.AVAILABLE_SLOTS;
                payload = availability == null ? new AvailabilityPayload(providerName(session),
                        draft == null ? null : draft.preferredDate(), List.of(), false, null)
                        : new AvailabilityPayload(providerName(session), availability.date(),
                        slotFacts(availability), availability.hasMore(), constraint(availability));
            }
            case "NO_MORE_SLOTS" -> {
                type = reschedule == null ? ResponseType.NO_MORE_SLOTS : ResponseType.RESCHEDULE_NO_MORE_SLOTS;
                payload = availability == null
                        ? new NoMoreSlotsEmptyPayload("NO_MORE_MATCHING_SLOTS")
                        : new NoMoreSlotsPayload(providerName(session), availability.date(), constraint(availability));
            }
            case "CONFIRMATION_REQUIRED" -> {
                type = ResponseType.BOOKING_CONFIRMATION;
                var confirmation = session.pendingConfirmation();
                payload = new BookingConfirmationPayload(providerName(session),
                        confirmation == null ? draft == null ? null : draft.preferredDate() : confirmation.date(),
                        confirmation == null ? draft == null ? null : draft.exactTime() : confirmation.startsAt());
            }
            case "BOOKING_CONFIRMED" -> {
                type = ResponseType.BOOKING_SUCCESS;
                payload = new BookingSuccessPayload(draft == null ? null : draft.confirmedAppointmentReference(),
                        providerName(session), draft == null ? null : draft.preferredDate(),
                        draft == null ? null : draft.exactTime());
            }
            case "APPOINTMENTS_NONE" -> {
                type = ResponseType.APPOINTMENTS_NONE;
                payload = new AppointmentListPayload(List.of(), null);
            }
            case "APPOINTMENTS_FOUND" -> {
                type = ResponseType.APPOINTMENTS_FOUND;
                payload = new AppointmentListPayload(List.of(), null);
            }
            case "LOOKUP_CLARIFICATION" -> {
                type = ResponseType.LOOKUP_CLARIFICATION;
                payload = new ClarificationPayload("LOOKUP_FILTER_AMBIGUOUS", List.of());
            }
            case "CANCELLATION_NONE" -> {
                type = ResponseType.CANCELLATION_NONE;
                payload = new CancellationChoicesPayload(List.of());
            }
            case "CANCELLATION_CHOICES" -> {
                type = ResponseType.CANCELLATION_CHOICES;
                var pending = session.pendingCancellationResolution();
                payload = new CancellationChoicesPayload(pending == null ? List.of() : appointmentFacts(pending.candidates()));
            }
            case "CANCELLATION_CONFIRMATION" -> {
                type = ResponseType.CANCELLATION_CONFIRMATION;
                payload = new CancellationConfirmationPayload(cancellationFact(session.pendingCancellation()));
            }
            case "CANCELLATION_CONFIRMED" -> {
                type = ResponseType.CANCELLATION_SUCCESS;
                payload = new CancellationSuccessPayload(null);
            }
            case "CANCEL_CONFIRMATION_REJECTED" -> {
                type = ResponseType.CANCELLATION_REJECTED;
                payload = new CancellationConfirmationPayload(null);
            }
            case "RESCHEDULE_SOURCE_CHOICES" -> {
                type = ResponseType.RESCHEDULE_SOURCE_CHOICES;
                payload = new RescheduleSourceChoicesPayload(reschedule == null ? List.of()
                        : appointmentFacts(reschedule.sourceCandidates()));
            }
            case "RESCHEDULE_NONE" -> {
                type = ResponseType.RESCHEDULE_SOURCE_NONE;
                payload = new RescheduleSourceChoicesPayload(List.of());
            }
            case "RESCHEDULE_CONFIRMATION" -> {
                type = ResponseType.RESCHEDULE_CONFIRMATION;
                payload = rescheduleConfirmationPayload(session);
            }
            case "RESCHEDULE_CONFIRMED" -> {
                type = ResponseType.RESCHEDULE_SUCCESS;
                payload = rescheduleSuccessPayload(session);
            }
            case "RESCHEDULE_CONFIRMATION_REJECTED" -> {
                type = ResponseType.RESCHEDULE_REJECTED;
                payload = rescheduleConfirmationPayload(session);
            }
            case "CALL_TO_BOOK" -> {
                type = ResponseType.CALL_TO_BOOK;
                payload = new CallToBookPayload(providerName(session), provider == null ? null : provider.clinicDisplayName(), null);
            }
            case "STALE", "EXPIRED" -> {
                type = ResponseType.STALE_RESULT;
                payload = new StaleResultPayload(category, null);
            }
            case "FAILED", "INVALID", "NOT_BOOKABLE", "NEEDS_INPUT", "RESCHEDULE_UNAVAILABLE" -> {
                type = ResponseType.FAILURE;
                payload = new FailurePayload(category);
            }
            case "CLARIFICATION", "PAST_DATE" -> {
                type = ResponseType.CLARIFICATION;
                payload = new ClarificationPayload(category, List.of());
            }
            default -> {
                type = ResponseType.LEGACY;
                payload = new EmptyPayload(category);
            }
        }
        return AivaStructuredResponse.of(type, payload, actions);
    }

    private AivaStructuredResponse structuredLookup(List<AppointmentSummary> appointments,
                                                    AivaV2Models.AppointmentLookupFilter filter, LocalDate date) {
        return AivaStructuredResponse.of(ResponseType.APPOINTMENTS_FOUND,
                new AppointmentListPayload(appointmentFacts(appointments), appliedFilters(filter, date)), List.of());
    }

    private List<AppointmentFact> appointmentFacts(List<AppointmentSummary> appointments) {
        if (appointments == null) return List.of();
        return appointments.stream().map(appointment -> new AppointmentFact(appointment.appointmentReference(),
                appointment.doctorDisplayName(), appointment.date(), appointment.time(),
                appointment.clinicDisplayName(), appointment.status())).toList();
    }

    private AppointmentFact appointmentFact(RescheduleResolution state) {
        return state == null ? null : new AppointmentFact(state.sourceAppointmentReference(),
                state.originalDoctorDisplayName(), state.originalDate(), state.originalStartsAt(), null, null);
    }

    private AppointmentFact cancellationFact(CancellationConfirmation confirmation) {
        return confirmation == null ? null : new AppointmentFact(confirmation.appointmentReference(),
                confirmation.doctorDisplayName(), confirmation.date(), confirmation.startsAt(),
                confirmation.clinicDisplayName(), null);
    }

    private AppliedFilters appliedFilters(AivaV2Models.AppointmentLookupFilter filter, LocalDate resolvedDate) {
        if (filter == null) return null;
        LocalDate date = resolvedDate;
        if (date == null && filter.dateExpression().mode() == PatchMode.SET) {
            try { date = LocalDate.parse(filter.dateExpression().value()); } catch (RuntimeException ignored) { }
        }
        return new AppliedFilters(filter.doctorText().mode() == PatchMode.SET ? filter.doctorText().value() : null,
                date, filter.status().mode() == PatchMode.SET ? filter.status().value() : null,
                filter.clinicText().mode() == PatchMode.SET ? filter.clinicText().value() : null, filter.nextOnly());
    }

    private List<SlotFact> slotFacts(AvailabilityResult result) {
        int start = result.displayOffset();
        int end = Math.min(result.slots().size(), start + result.pageSize());
        return result.slots().subList(start, end).stream().map(slot ->
                new SlotFact(slot.slotReference(), slot.startsAt(), slot.endsAt(), slot.displayTime())).toList();
    }

    private String constraint(AvailabilityResult result) {
        if (result.timeWindow() != null) return result.timeWindow();
        if (result.availabilityTimeConstraint() == null) return null;
        return result.availabilityTimeConstraint().mode().name();
    }

    private String providerName(SessionProjection session) {
        if (session.pendingReschedule() != null && session.pendingReschedule().originalDoctorDisplayName() != null) {
            return session.pendingReschedule().originalDoctorDisplayName();
        }
        return session.activeDraft() == null || session.activeDraft().selectedProvider() == null ? null
                : session.activeDraft().selectedProvider().displayName();
    }

    private RescheduleConfirmationPayload rescheduleConfirmationPayload(SessionProjection session) {
        RescheduleResolution state = session.pendingReschedule();
        RescheduleConfirmation confirmation = session.pendingRescheduleConfirmation();
        return new RescheduleConfirmationPayload(state == null ? null : state.originalDoctorDisplayName(),
                appointmentFact(state), confirmation == null ? state == null ? null : state.targetDate() : confirmation.targetDate(),
                confirmation == null ? state == null ? null : state.selectedStartsAt() : confirmation.targetStartsAt());
    }

    private RescheduleSuccessPayload rescheduleSuccessPayload(SessionProjection session) {
        RescheduleResolution state = session.pendingReschedule();
        RescheduleConfirmation confirmation = session.pendingRescheduleConfirmation();
        return new RescheduleSuccessPayload(state == null ? null : state.originalDoctorDisplayName(),
                state == null ? null : state.originalDate(), state == null ? null : state.originalStartsAt(),
                confirmation == null ? state == null ? null : state.targetDate() : confirmation.targetDate(),
                confirmation == null ? state == null ? null : state.selectedStartsAt() : confirmation.targetStartsAt());
    }

    private List<InteractiveAction> interactiveActions(SessionProjection session, String category) {
        if ("NEED_DATE".equals(category) || "RESCHEDULE_NEED_DATE".equals(category)) {
            String type = "RESCHEDULE_NEED_DATE".equals(category) ? "UPDATE_RESCHEDULE_DATE" : "UPDATE_BOOKING_DATE";
            return List.of(new InteractiveAction(type, "Choose a date", null, null));
        }
        if ("SLOT_CHOICES".equals(category) || "RESCHEDULE_SLOT_CHOICES".equals(category)) {
            AvailabilityResult availability = session.pendingReschedule() == null
                    ? session.latestAvailabilityResult() : session.pendingReschedule().latestAvailability();
            if (availability == null) return List.of();
            int end = Math.min(availability.slots().size(), availability.displayOffset() + availability.pageSize());
            List<InteractiveAction> actions = new java.util.ArrayList<>();
            for (int i = availability.displayOffset(); i < end; i++) {
                AvailabilitySlot slot = availability.slots().get(i);
                actions.add(new InteractiveAction("SELECT_SLOT", slot.displayTime(), null, slot.slotReference()));
            }
            if (availability.hasMore()) actions.add(new InteractiveAction("SHOW_MORE_SLOTS", "Show more slots", null, null));
            return actions;
        }
        if ("CONFIRMATION_REQUIRED".equals(category)) {
            return List.of(new InteractiveAction("CONFIRM_BOOKING", "Confirm", null, null),
                    new InteractiveAction("CHOOSE_ANOTHER_SLOT", "Choose another slot", null, null));
        }
        if ("RESCHEDULE_CONFIRMATION".equals(category)) {
            return List.of(new InteractiveAction("CONFIRM_RESCHEDULE", "Confirm", null, null),
                    new InteractiveAction("CHOOSE_ANOTHER_SLOT", "Choose another slot", null, null));
        }
        if ("CANCELLATION_CONFIRMATION".equals(category)) {
            return List.of(new InteractiveAction("CONFIRM_CANCELLATION", "Cancel appointment", null, null),
                    new InteractiveAction("DECLINE_CANCELLATION", "Keep appointment", null, null));
        }
        return List.of();
    }

    private StateView view(BookingDraft draft, BookingConfirmation confirmation) {
        if (draft == null) return null;
        return new StateView(draft.draftId(), draft.status(), draft.revision(),
                draft.selectedProvider() == null ? null : draft.selectedProvider().displayName(), draft.specialtyFilter(),
                draft.preferredDate(), draft.preferredTimeWindow(), draft.exactTime(), draft.selectedSlotReference(),
                confirmation != null, draft.confirmedAppointmentReference());
    }

    private SessionProjection withActiveDraft(SessionProjection session, BookingDraft draft) {
        return replace(session, draft, session.suspendedDraft(), session.latestProviderResult(),
                session.latestAvailabilityResult(), session.pendingConfirmation());
    }

    private SessionProjection replace(SessionProjection session, BookingDraft active, BookingDraft suspended,
                                      ProviderSearchResult providerResult, AvailabilityResult availability,
                                      BookingConfirmation confirmation) {
        return replace(session, active, suspended, providerResult, availability, confirmation, null);
    }

    private SessionProjection replace(SessionProjection session, BookingDraft active, BookingDraft suspended,
                                      ProviderSearchResult providerResult, AvailabilityResult availability,
                                      BookingConfirmation confirmation, CancellationConfirmation cancellation) {
        return replace(session, active, suspended, providerResult, availability, confirmation, cancellation, null);
    }

    private SessionProjection replace(SessionProjection session, BookingDraft active, BookingDraft suspended,
                                      ProviderSearchResult providerResult, AvailabilityResult availability,
                                      BookingConfirmation confirmation, CancellationConfirmation cancellation,
                                      CancellationResolution resolution) {
        return replace(session, active, suspended, providerResult, availability, confirmation, cancellation, resolution, null, null);
    }

    private SessionProjection replace(SessionProjection session, BookingDraft active, BookingDraft suspended,
                                      ProviderSearchResult providerResult, AvailabilityResult availability,
                                      BookingConfirmation confirmation, CancellationConfirmation cancellation,
                                      CancellationResolution resolution, RescheduleResolution reschedule,
                                      RescheduleConfirmation rescheduleConfirmation) {
        return new SessionProjection(session.conversationId(), session.patientId(), session.tenantId(), active, suspended,
                providerResult, availability, confirmation, cancellation, resolution,
                reschedule, rescheduleConfirmation, session.version() + 1, session.expiresAt());
    }

    private boolean isExplicitNewWorkflow(ConversationDecision decision) {
        return decision.operation() == Operation.START_BOOKING
                || decision.operation() == Operation.LOOKUP_APPOINTMENTS
                || decision.operation() == Operation.GET_AVAILABILITY
                || decision.operation() == Operation.RESOLVE_PROVIDER
                || decision.operation() == Operation.CANCEL_APPOINTMENT
                || decision.operation() == Operation.RESCHEDULE_APPOINTMENT;
    }

    private BookingDraft copyDraft(BookingDraft draft, ProviderCandidate provider, String specialty, LocalDate date,
                                   String window, LocalTime exact, UUID availabilityId, String slot,
                                   DraftStatus status, long revision, String appointmentReference) {
        return copyDraft(draft, provider, specialty, date, window, exact, draft.availabilityTimeConstraint(), availabilityId,
                slot, status, revision, appointmentReference);
    }

    private BookingDraft copyDraft(BookingDraft draft, ProviderCandidate provider, String specialty, LocalDate date,
                                   String window, LocalTime exact, AvailabilityTimeConstraint constraint,
                                   UUID availabilityId, String slot, DraftStatus status, long revision,
                                   String appointmentReference) {
        return new BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(), provider, specialty,
                date, window, exact, constraint, availabilityId, slot, status, revision, draft.expiresAt(), appointmentReference);
    }

    private String safe(String value, String fallback) { return StringUtils.hasText(value) ? value : fallback; }

    record KernelResult(SessionProjection session, MessageResponse response) { }
    private record PatchResult(SessionProjection session, ProviderSearchResult providerResult,
                               boolean invalidDate, boolean pastDate) { }
}
