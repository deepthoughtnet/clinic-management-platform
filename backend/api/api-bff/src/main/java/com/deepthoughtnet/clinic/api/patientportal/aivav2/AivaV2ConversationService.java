package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.InteractiveActionRequest;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PatchMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AivaV2ConversationService {
    private static final String RUNTIME_VERSION = "aiva-v2-contract-1.0";
    private final AivaV2ConversationDecisionGateway decisionGateway;
    private final AivaV2TransactionalKernel kernel;
    private final AivaV2SessionStore sessionStore;
    private final PatientPortalService patientPortalService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final Clock clock;

    @Autowired
    AivaV2ConversationService(
            AivaV2ConversationDecisionGateway decisionGateway,
            AivaV2TransactionalKernel kernel,
            AivaV2SessionStore sessionStore,
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver
    ) {
        this(decisionGateway, kernel, sessionStore, patientPortalService, clinicTimeZoneResolver, Clock.systemUTC());
    }

    AivaV2ConversationService(
            AivaV2ConversationDecisionGateway decisionGateway,
            AivaV2TransactionalKernel kernel,
            AivaV2SessionStore sessionStore,
            PatientPortalService patientPortalService,
            Clock clock
    ) {
        this(decisionGateway, kernel, sessionStore, patientPortalService, null, clock);
    }

    AivaV2ConversationService(
            AivaV2ConversationDecisionGateway decisionGateway,
            AivaV2TransactionalKernel kernel,
            AivaV2SessionStore sessionStore,
            PatientPortalService patientPortalService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            Clock clock
    ) {
        this.decisionGateway = decisionGateway;
        this.kernel = kernel;
        this.sessionStore = sessionStore;
        this.patientPortalService = patientPortalService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.clock = clock;
    }

    @PostConstruct
    void logRuntimeVersion() {
        org.slf4j.LoggerFactory.getLogger(AivaV2ConversationService.class)
                .info("AIVA_V2_RUNTIME version={}", RUNTIME_VERSION);
    }

    MessageResponse message(MessageRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("Message is required.");
        }
        UUID patientId = patientPortalService.currentPatientId();
        String tenantId = RequestContextHolder.requireTenantId().toString();
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId().trim() : "aiva-v2-" + UUID.randomUUID();
        if (conversationId.length() > 100 || !conversationId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid conversation identifier.");
        }
        String key = tenantId + "|" + patientId + "|" + conversationId;
        String turnId = "turn-" + UUID.randomUUID();
        AtomicReference<MessageResponse> response = new AtomicReference<>();
        sessionStore.update(key, current -> {
            SessionProjection session = current == null ? new SessionProjection(
                    conversationId, patientId, tenantId, null, null, null, null, null,
                    1L, Instant.now(clock).plusSeconds(30 * 60)) : current;
            var decision = request.action() == null
                    ? decisionGateway.decide(request.message(), safeLanguage(request.language()), session)
                    : decisionForAction(request.action(), safeLanguage(request.language()));
            logDecision(conversationId, turnId, decision);
            ZoneId clinicZone = clinicTimeZoneResolver == null
                    ? ZoneId.of("Asia/Kolkata")
                    : clinicTimeZoneResolver.resolve(RequestContextHolder.requireTenantId());
            AivaV2Models.ValuePatch datePatch = decision.operation() == AivaV2Models.Operation.LOOKUP_APPOINTMENTS
                    || decision.operation() == AivaV2Models.Operation.CANCEL_APPOINTMENT
                    || decision.operation() == AivaV2Models.Operation.RESCHEDULE_APPOINTMENT
                    ? decision.lookupFilter().dateExpression() : decision.bookingPatch().dateExpression();
            AivaV2TemporalResolution temporal = new AivaV2TemporalNormalizer(clock, clinicZone)
                    .normalize(request.message(), datePatch,
                            cancellationDate(session, decision));
            decision = applyPendingCancellationRefinement(session, decision, temporal);
            decision = applyPendingRescheduleRefinement(session, decision, temporal);
            logTemporal(conversationId, turnId, decision, temporal);
            AivaV2TransactionalKernel.KernelResult result = kernel.handle(session, decision, temporal, clinicZone, turnId);
            response.set(result.response());
            return result.session();
        });
        return response.get();
    }

    private ConversationDecision decisionForAction(InteractiveActionRequest action, String language) {
        String type = action.type() == null ? "" : action.type().trim().toUpperCase(java.util.Locale.ROOT);
        Operation operation = switch (type) {
            case "SHOW_MORE_SLOTS" -> Operation.SHOW_MORE_SLOTS;
            case "CONFIRM_BOOKING" -> Operation.CONFIRM_BOOKING;
            case "CONFIRM_RESCHEDULE" -> Operation.CONFIRM_RESCHEDULE;
            case "CONFIRM_CANCELLATION" -> Operation.CONFIRM_CANCELLATION;
            case "DECLINE_CANCELLATION" -> Operation.CANCEL_APPOINTMENT;
            case "UPDATE_RESCHEDULE_DATE" -> Operation.UPDATE_RESCHEDULE;
            case "UPDATE_BOOKING_DATE" -> Operation.UPDATE_BOOKING;
            case "SELECT_SLOT" -> Operation.SELECT_SLOT;
            default -> Operation.UNKNOWN;
        };
        ConfirmationPolarity confirmation = switch (type) {
            case "CONFIRM_BOOKING", "CONFIRM_RESCHEDULE", "CONFIRM_CANCELLATION" -> ConfirmationPolarity.POSITIVE;
            case "DECLINE_CANCELLATION" -> ConfirmationPolarity.NEGATIVE;
            default -> ConfirmationPolarity.NONE;
        };
        BookingPatch patch = ("UPDATE_RESCHEDULE_DATE".equals(type) || "UPDATE_BOOKING_DATE".equals(type))
                ? new BookingPatch(null, null, ValuePatch.set(action.value()), null, null) : BookingPatch.empty();
        Selection selection = "SELECT_SLOT".equals(type)
                ? new Selection(null, null, action.slotReference(), action.value()) : null;
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                confirmation == ConfirmationPolarity.NONE ? AivaV2Models.DialogAct.SELECT_OPTION : AivaV2Models.DialogAct.CONFIRM,
                operation, patch, selection, confirmation, TopicAction.CONTINUE, language, 1.0d,
                "DETERMINISTIC_ACTION", false, 0L);
    }

    private java.time.LocalDate cancellationDate(SessionProjection session, ConversationDecision decision) {
        if (session.pendingReschedule() != null && session.pendingReschedule().targetDate() != null
                && (decision.operation() == Operation.UPDATE_BOOKING
                || decision.operation() == Operation.SELECT_SLOT
                || decision.operation() == Operation.UPDATE_RESCHEDULE
                || decision.operation() == Operation.GET_RESCHEDULE_AVAILABILITY
                || decision.operation() == Operation.SELECT_RESCHEDULE_SLOT)) {
            return session.pendingReschedule().targetDate();
        }
        if (session.pendingCancellationResolution() != null) {
            ValuePatch date = session.pendingCancellationResolution().criteria().dateExpression();
            if (date.mode() == PatchMode.SET) {
                try { return java.time.LocalDate.parse(date.value()); } catch (RuntimeException ignored) { }
            }
        }
        if (decision.operation() == Operation.LOOKUP_APPOINTMENTS
                || decision.operation() == Operation.CANCEL_APPOINTMENT
                || decision.operation() == Operation.RESCHEDULE_APPOINTMENT
                || decision.operation() == Operation.UPDATE_RESCHEDULE
                || decision.operation() == Operation.GET_RESCHEDULE_AVAILABILITY
                || decision.operation() == Operation.SELECT_RESCHEDULE_SLOT) return null;
        return session.activeDraft() == null ? null : session.activeDraft().preferredDate();
    }

    private ConversationDecision applyPendingCancellationRefinement(SessionProjection session,
                                                                     ConversationDecision decision,
                                                                     AivaV2TemporalResolution temporal) {
        var pending = session.pendingCancellationResolution();
        if (pending == null || pending.expiresAt() == null || pending.expiresAt().isBefore(Instant.now(clock))) {
            return decision;
        }
        if (decision.operation() == Operation.START_BOOKING
                || decision.operation() == Operation.LOOKUP_APPOINTMENTS
                || decision.operation() == Operation.GET_AVAILABILITY
                || decision.operation() == Operation.RESOLVE_PROVIDER) return decision;
        if (decision.operation() != Operation.UPDATE_BOOKING
                && decision.operation() != Operation.SELECT_SLOT
                && decision.operation() != Operation.UNKNOWN) return decision;
        var current = pending.criteria();
        ValuePatch doctor = decision.bookingPatch().doctorText().mode() == PatchMode.SET
                ? decision.bookingPatch().doctorText() : current.doctorText();
        ValuePatch date = current.dateExpression();
        if (temporal != null && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED) {
            date = ValuePatch.set(temporal.localDate().toString());
        } else if (decision.bookingPatch().dateExpression().mode() == PatchMode.SET) {
            date = decision.bookingPatch().dateExpression();
        }
        ValuePatch clinic = decision.bookingPatch().clinicText().mode() == PatchMode.SET
                ? decision.bookingPatch().clinicText() : current.clinicText();
        var merged = new AivaV2Models.AppointmentLookupFilter(doctor, date, current.status(), clinic, current.nextOnly());
            return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), Operation.CANCEL_APPOINTMENT,
                    BookingPatch.empty(), decision.selection(), ConfirmationPolarity.NONE, TopicAction.CONTINUE,
                    decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                    decision.latencyMs(), merged, decision.unresolvedExplicitQualifierPresent());
    }

    private ConversationDecision applyPendingRescheduleRefinement(SessionProjection session,
                                                                  ConversationDecision decision,
                                                                  AivaV2TemporalResolution temporal) {
        var pending = session.pendingReschedule();
        if (pending == null || pending.expiresAt() == null || pending.expiresAt().isBefore(Instant.now(clock))) {
            return decision;
        }
        if (decision.operation() == Operation.START_BOOKING
                || decision.operation() == Operation.LOOKUP_APPOINTMENTS
                || decision.operation() == Operation.CANCEL_APPOINTMENT
                || decision.operation() == Operation.GET_AVAILABILITY
                || decision.operation() == Operation.RESOLVE_PROVIDER
                || decision.operation() == Operation.RESCHEDULE_APPOINTMENT) return decision;
        if (pending.status() == AivaV2Models.RescheduleStatus.SOURCE_AMBIGUOUS) {
            ValuePatch doctor = decision.bookingPatch().doctorText().mode() == PatchMode.SET
                    ? decision.bookingPatch().doctorText() : pending.sourceCriteria().doctorText();
            ValuePatch date = pending.sourceCriteria().dateExpression();
            if (temporal != null && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                    && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED) {
                date = ValuePatch.set(temporal.localDate().toString());
            } else if (decision.bookingPatch().dateExpression().mode() == PatchMode.SET) {
                date = decision.bookingPatch().dateExpression();
            }
            ValuePatch clinic = decision.bookingPatch().clinicText().mode() == PatchMode.SET
                    ? decision.bookingPatch().clinicText() : pending.sourceCriteria().clinicText();
            if (doctor.mode() == PatchMode.UNCHANGED && date.mode() == PatchMode.UNCHANGED
                    && clinic.mode() == PatchMode.UNCHANGED) return decision;
            var filter = new AivaV2Models.AppointmentLookupFilter(doctor, date,
                    pending.sourceCriteria().status(), clinic, pending.sourceCriteria().nextOnly());
            return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), Operation.RESCHEDULE_APPOINTMENT,
                    BookingPatch.empty(), decision.selection(), ConfirmationPolarity.NONE, TopicAction.CONTINUE,
                    decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                    decision.latencyMs(), filter, decision.unresolvedExplicitQualifierPresent());
        }
        Operation operation = decision.operation();
        if (operation == Operation.SELECT_SLOT
                || (decision.selection() != null && (decision.selection().ordinal() != null
                || StringUtils.hasText(decision.selection().exactTime()) || StringUtils.hasText(decision.selection().slotRef())))) {
            operation = Operation.SELECT_RESCHEDULE_SLOT;
        } else if (operation == Operation.UPDATE_BOOKING || operation == Operation.UNKNOWN) {
            operation = Operation.UPDATE_RESCHEDULE;
        } else {
            return decision;
        }
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), operation,
                decision.bookingPatch(), decision.selection(), decision.confirmation(), TopicAction.CONTINUE,
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private void logDecision(String conversationId, String turnId,
                             AivaV2Models.ConversationDecision decision) {
        AivaV2Models.Selection selection = decision.selection();
        AivaV2Models.ValuePatch exactTime = decision.bookingPatch().exactTime();
        AivaV2Models.AvailabilityTimeConstraintPatch constraint = decision.bookingPatch().availabilityTimeConstraint();
        AivaV2Models.AvailabilityTimeConstraint value = constraint.value();
        org.slf4j.LoggerFactory.getLogger(AivaV2ConversationService.class).info(
                "AIVA_V2_DECISION_TRACE conversationId={} turnId={} operation={} dialogAct={} "
                        + "selectionOrdinal={} selectionExactTime={} bookingPatchExactTimeSet={} "
                        + "availabilityConstraintMode={} availabilityConstraintStart={} availabilityConstraintEnd={} "
                        + "confirmation={} topicAction={} decisionValidationResult={} provider={} fallbackUsed={} "
                        + "lookupNextOnly={} lookupDoctorFilterPresent={} lookupDoctorFilterValue={} "
                        + "lookupDateFilterPresent={} lookupDateFilterValue={} lookupStatusFilter={}",
                conversationId, turnId, decision.operation(), decision.dialogAct(),
                selection == null ? null : selection.ordinal(),
                selection == null ? null : selection.exactTime(),
                exactTime != null && exactTime.mode() == AivaV2Models.PatchMode.SET,
                value == null ? null : value.mode(), value == null ? null : value.startTime(),
                value == null ? null : value.endTime(),
                decision.confirmation(), decision.topicAction(),
                decision.operation() == AivaV2Models.Operation.UNKNOWN ? "INVALID_OR_UNKNOWN" : "VALID",
                decision.provider(), decision.fallbackUsed(), decision.lookupFilter().nextOnly(),
                decision.lookupFilter().doctorText().mode() == AivaV2Models.PatchMode.SET,
                decision.lookupFilter().doctorText().mode() == AivaV2Models.PatchMode.SET
                        ? decision.lookupFilter().doctorText().value() : null,
                decision.lookupFilter().dateExpression().mode() == AivaV2Models.PatchMode.SET,
                decision.lookupFilter().dateExpression().mode() == AivaV2Models.PatchMode.SET
                        ? decision.lookupFilter().dateExpression().value() : null,
                decision.lookupFilter().status().mode() == AivaV2Models.PatchMode.SET
                        ? decision.lookupFilter().status().value() : null);
    }

    private void logTemporal(String conversationId, String turnId,
                             AivaV2Models.ConversationDecision decision,
                             AivaV2TemporalResolution temporal) {
        org.slf4j.LoggerFactory.getLogger(AivaV2ConversationService.class).info(
                "AIVA_V2_TEMPORAL_TRACE conversationId={} turnId={} rawTemporalCandidate={} normalizedDate={} "
                        + "temporalSource={} temporalStatus={} modelDateCandidate={} modelVsDeterministicMismatch={}",
                conversationId, turnId, temporal.rawCandidate(), temporal.localDate(), temporal.source(),
                temporal.status(), temporal.modelCandidate(), temporal.modelVsDeterministicMismatch());
    }

    private String safeLanguage(String language) {
        return StringUtils.hasText(language) ? language.trim().toLowerCase() : "en";
    }
}
