package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PatchMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DeterministicControl;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class AiAivaV2ConversationDecisionGateway implements AivaV2ConversationDecisionGateway {
    private static final Logger log = LoggerFactory.getLogger(AiAivaV2ConversationDecisionGateway.class);
    private static final double DEFAULT_CONFIDENCE = 1.0d;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Pattern ORDINAL = Pattern.compile("^(?:the\\s+)?(first|second|third|fourth|last|[1-9])(?:\\s+(?:one|slot|doctor))?[.!?]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK_TIME = Pattern.compile("^(?:at\\s+)?([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(am|pm)?[.!?]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL_ACCEPTANCE = Pattern.compile(
            "^(?:the\\s+)?(first|second|third|fourth|last|[1-9](?:st|nd|rd|th)?)(?:\\s+(?:one|slot))?\\s+(?:works(?:\\s+for\\s+me)?|is\\s+(?:fine|okay)|will\\s+do)[.!?]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_ACCEPTANCE = Pattern.compile(
            "^(?:at\\s+)?([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(am|pm)?\\s+(?:works(?:\\s+for\\s+me)?|is\\s+(?:fine|okay)|will\\s+do)[.!?]?$",
            Pattern.CASE_INSENSITIVE);
    private static final String TIME_TOKEN = "([0-9]{1,2}(?:[:.][0-9]{2})?\\s*(?:am|pm)?)";
    private static final Pattern BETWEEN_TIME_CONSTRAINT = Pattern.compile(
            "\\bbetween\\s+" + TIME_TOKEN + "\\s+and\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AFTER_BEFORE_TIME_CONSTRAINT = Pattern.compile(
            "\\bafter\\s+" + TIME_TOKEN + "\\s+and\\s+before\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARATIVE_BETWEEN_TIME_CONSTRAINT = Pattern.compile(
            "\\blater\\s+than\\s+" + TIME_TOKEN + "\\s+but\\s+earlier\\s+than\\s+" + TIME_TOKEN + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_TO_TIME_CONSTRAINT = Pattern.compile(
            "\\bfrom\\s+" + TIME_TOKEN + "\\s+to\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATER_THAN_TIME_CONSTRAINT = Pattern.compile(
            "\\blater\\s+than\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AFTER_TIME_CONSTRAINT = Pattern.compile(
            "\\bafter\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EARLIER_THAN_TIME_CONSTRAINT = Pattern.compile(
            "\\bearlier\\s+than\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEFORE_TIME_CONSTRAINT = Pattern.compile(
            "\\bbefore\\s+" + TIME_TOKEN + "\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_MORE_CONTROL = Pattern.compile(
            "^(?:show|provide)\\s+(?:me\\s+)?(?:more|next)(?:\\s+slots?)?[.!?]?$"
                    + "|^(?:show\\s+me\\s+)?(?:next|more)\\s+slots?[.!?]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYPART_CONTROL = Pattern.compile(
            "\\b(morning|afternoon|evening)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDER_ORDINAL = Pattern.compile(
            "^(?:the\\s+)?(first|second|third|fourth|[1-9])(?:\\s+(?:one|doctor|provider))?(?:\\s*\\..*)?$",
            Pattern.CASE_INSENSITIVE);

    private final AiOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final Clock clock;
    private final LanguageAdapterRegistry languageAdapterRegistry;

    @Override
    public NormalizedUserTurn presentationTurn(String text, String language) {
        return languageAdapterRegistry.resolve(language, text).normalize(text);
    }

    @Override
    public NormalizedUserTurn normalizeTurn(String text, String language, LocalDate referenceDate) {
        return languageAdapterRegistry.resolve(language, text).normalize(text, referenceDate);
    }

    @Autowired
    AiAivaV2ConversationDecisionGateway(AiOrchestrationService orchestrationService, ObjectMapper objectMapper,
                                        ClinicTimeZoneResolver clinicTimeZoneResolver,
                                        LanguageAdapterRegistry languageAdapterRegistry) {
        this(orchestrationService, objectMapper, clinicTimeZoneResolver, Clock.systemUTC(), languageAdapterRegistry);
    }

    AiAivaV2ConversationDecisionGateway(AiOrchestrationService orchestrationService, ObjectMapper objectMapper,
                                        ClinicTimeZoneResolver clinicTimeZoneResolver, Clock clock) {
        this(orchestrationService, objectMapper, clinicTimeZoneResolver, clock, LanguageAdapterRegistry.defaults());
    }

    AiAivaV2ConversationDecisionGateway(AiOrchestrationService orchestrationService, ObjectMapper objectMapper,
                                        ClinicTimeZoneResolver clinicTimeZoneResolver, Clock clock,
                                        LanguageAdapterRegistry languageAdapterRegistry) {
        this.orchestrationService = orchestrationService;
        this.objectMapper = objectMapper;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.clock = clock;
        this.languageAdapterRegistry = languageAdapterRegistry;
    }

    @Override
    public ConversationDecision decide(String text, String language, SessionProjection context) {
        ZoneId zone = clinicTimeZoneResolver.resolve(RequestContextHolder.requireTenantId());
        return decide(normalizeTurn(text, language, LocalDate.now(clock.withZone(zone))), context);
    }

    @Override
    public ConversationDecision decide(NormalizedUserTurn normalizedTurn, SessionProjection context) {
        String language = normalizedTurn.languageCode();
        long started = System.nanoTime();
        ConversationDecision fast = fastPath(normalizedTurn, language, context, started);
        if (fast != null) {
            fast = mergeCanonicalFacts(normalizedTurn, fast);
            log.info("AIVA_V2_CONTROL_TRACE rawControlLanguage={} semanticControl={} normalizedOperation={} "
                            + "normalizedTime={} normalizedDaypart={} confirmation={} providerCallRequired=false activeWorkflow={}",
                    normalizedTurn.languageCode(), semanticControl(fast), fast.operation(), normalizedTime(fast),
                    normalizedDaypart(fast), fast.confirmation(), activeWorkflow(context));
            return fast;
        }
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("message", normalizedTurn.canonicalSemanticText());
            variables.put("canonicalFacts", Map.of(
                    "doctor", normalizedTurn.doctorEntity() == null ? "" : normalizedTurn.doctorEntity().canonicalQuery(),
                    "specialty", normalizedTurn.specialtyEntity() == null ? "" : normalizedTurn.specialtyEntity().canonicalQuery(),
                    "date", normalizedTurn.temporal().localDate() == null ? "" : normalizedTurn.temporal().localDate().toString(),
                    "exactTime", normalizedTurn.exactTime() == null ? "" : normalizedTurn.exactTime().toString(),
                    "daypart", normalizedTurn.daypart() == null ? "" : normalizedTurn.daypart().name(),
                    "ordinal", normalizedTurn.ordinal() == null ? "" : normalizedTurn.ordinal().toString(),
                    "confirmation", normalizedTurn.confirmation().name(),
                    "pagination", normalizedTurn.pagination().name()));
            variables.put("boundedRawContext", normalizedTurn.rawText().substring(0, Math.min(256, normalizedTurn.rawText().length())));
            variables.put("language", language);
            variables.put("bookingContext", safeContext(context));
            ZoneId zone = clinicTimeZoneResolver.resolve(RequestContextHolder.requireTenantId());
            variables.put("referenceDate", LocalDate.now(clock.withZone(zone)).toString());
            variables.put("referenceTimezone", zone.getId());
            variables.put("instructions", schemaInstructions());
            AiOrchestrationResponse response = orchestrationService.complete(new AiOrchestrationRequest(
                    AiProductCode.GENERIC,
                    RequestContextHolder.requireTenantId(),
                    RequestContextHolder.require().appUserId(),
                    AiTaskType.GENERIC_EXTRACTION,
                    "generic.extraction.v1",
                    variables,
                    List.of(),
                    1400,
                    0.1d,
                    RequestContextHolder.require().correlationId(),
                    "patient-portal-aiva-v2-decision",
                    AivaV2ConversationDecisionSchema.definition()
            ));
            ConversationDecision decision = parse(response, language, elapsed(started));
            decision = recoverUnsolicitedConfirmationAsBooking(normalizedTurn, context, decision);
            decision = mergeCanonicalFacts(normalizedTurn, decision);
            log.info("AIVA_V2_DECISION_TRACE conversationId={} provider={} fallbackUsed={} responseEnvelope={} operation={} dialogAct={} confidence={} parseStatus={} normalization={} defaultsApplied={}",
                    context == null ? null : context.conversationId(), decision.provider(), decision.fallbackUsed(),
                    envelope(response), decision.operation(), decision.dialogAct(), decision.confidence(),
                    normalizationStatus(response, decision), normalizationStatus(response, decision), defaultsApplied(response));
            return validateDecision(decision);
        } catch (RuntimeException ex) {
            return ConversationDecision.unknown(language, "UNAVAILABLE", false, elapsed(started));
        }
    }

    /**
     * Applies trusted current-turn facts to the canonical fields consumed by the
     * selected operation. Appointment source resolution uses lookupFilter;
     * booking and availability refinement use bookingPatch.
     */
    private ConversationDecision mergeCanonicalFacts(NormalizedUserTurn turn, ConversationDecision decision) {
        if (decision == null) return null;
        Operation operation = decision.operation();
        if (isAppointmentSourceResolution(operation)) {
            return mergeAppointmentLookupFacts(turn, decision);
        }
        if (!isBookingCriteriaOperation(operation)) return decision;

        BookingPatch patch = decision.bookingPatch();
        String doctorQuery = canonicalDoctorQuery(turn);
        var doctor = StringUtils.hasText(doctorQuery) ? ValuePatch.set(doctorQuery) : patch.doctorText();
        var specialty = turn.specialtyEntity() == null || !StringUtils.hasText(turn.specialtyEntity().canonicalQuery())
                ? patch.specialtyText() : ValuePatch.set(turn.specialtyEntity().canonicalQuery());
        var date = turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? ValuePatch.set(turn.temporal().localDate().toString()) : patch.dateExpression();
        logCanonicalFactConflicts("BOOKING_PATCH", doctorQuery, patch.doctorText(),
                turn.temporal(), patch.dateExpression());
        boolean selectedSlot = decision.operation() == Operation.SELECT_SLOT
                || decision.operation() == Operation.SELECT_RESCHEDULE_SLOT;
        BookingPatch merged = new BookingPatch(doctor, specialty, date, patch.timeWindow(),
                turn.exactTime() == null || selectedSlot ? patch.exactTime() : ValuePatch.set(turn.exactTime().toString()),
                patch.clinicText(), patch.availabilityTimeConstraint());
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), decision.operation(), merged,
                decision.selection(), decision.confirmation(), decision.topicAction(), decision.responseLanguage(),
                decision.confidence(), decision.provider(), decision.fallbackUsed(), decision.latencyMs(),
                decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private ConversationDecision mergeAppointmentLookupFacts(NormalizedUserTurn turn,
                                                               ConversationDecision decision) {
        var filter = decision.lookupFilter();
        String doctorQuery = canonicalDoctorQuery(turn);
        var doctor = StringUtils.hasText(doctorQuery) ? ValuePatch.set(doctorQuery) : filter.doctorText();
        var date = turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? ValuePatch.set(turn.temporal().localDate().toString()) : filter.dateExpression();
        logCanonicalFactConflicts("LOOKUP_FILTER", doctorQuery, filter.doctorText(),
                turn.temporal(), filter.dateExpression());
        var mergedFilter = new AivaV2Models.AppointmentLookupFilter(doctor, date, filter.status(),
                filter.clinicText(), filter.nextOnly());
        boolean unresolvedQualifier = decision.unresolvedExplicitQualifierPresent();
        if (decision.operation() == Operation.LOOKUP_APPOINTMENTS
                && !StringUtils.hasText(doctorQuery) && turn.explicitLookupQualifierPresent()) {
            unresolvedQualifier = true;
        } else if (StringUtils.hasText(doctorQuery)) {
            unresolvedQualifier = false;
        }
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), decision.operation(),
                decision.bookingPatch(), decision.selection(), decision.confirmation(), decision.topicAction(),
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), mergedFilter, unresolvedQualifier);
    }

    private boolean isAppointmentSourceResolution(Operation operation) {
        return operation == Operation.LOOKUP_APPOINTMENTS
                || operation == Operation.CANCEL_APPOINTMENT
                || operation == Operation.RESCHEDULE_APPOINTMENT;
    }

    private boolean isBookingCriteriaOperation(Operation operation) {
        return operation == Operation.START_BOOKING
                || operation == Operation.UPDATE_BOOKING
                || operation == Operation.RESOLVE_PROVIDER
                || operation == Operation.GET_AVAILABILITY
                || operation == Operation.UPDATE_RESCHEDULE
                || operation == Operation.GET_RESCHEDULE_AVAILABILITY;
    }

    private String canonicalDoctorQuery(NormalizedUserTurn turn) {
        if (turn.doctorEntity() != null && StringUtils.hasText(turn.doctorEntity().canonicalQuery())) {
            return turn.doctorEntity().canonicalQuery();
        }
        return StringUtils.hasText(turn.explicitDoctorReference()) ? turn.explicitDoctorReference() : null;
    }

    private void logCanonicalFactConflicts(String target, String trustedDoctor, ValuePatch providerDoctor,
                                           NormalizedUserTurn.TemporalFact trustedTemporal,
                                           ValuePatch providerDate) {
        List<String> conflicts = new java.util.ArrayList<>();
        if (StringUtils.hasText(trustedDoctor) && isSet(providerDoctor)
                && !normalizeFactForComparison(trustedDoctor).equals(normalizeFactForComparison(providerDoctor.value()))) {
            conflicts.add("DOCTOR");
        }
        if (trustedTemporal != null && trustedTemporal.status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                && isSet(providerDate) && !trustedTemporal.localDate().toString().equals(providerDate.value())) {
            conflicts.add("DATE");
        }
        if (!conflicts.isEmpty()) {
            log.info("AIVA_V2_CANONICAL_FACT_CONFLICT target={} canonicalFactConflict=true conflictField={} providerFactOverridden=true",
                    target, String.join(",", conflicts));
        }
    }

    private boolean isSet(ValuePatch patch) {
        return patch != null && patch.mode() == PatchMode.SET && StringUtils.hasText(patch.value());
    }

    private String normalizeFactForComparison(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceFirst("^(doctor|dr|doc)\\s+", "");
    }

    /** A provider confirmation cannot start a transaction when no capability exists. */
    private ConversationDecision recoverUnsolicitedConfirmationAsBooking(NormalizedUserTurn turn,
                                                                         SessionProjection context,
                                                                         ConversationDecision decision) {
        if (decision == null || context != null && context.pendingConfirmation() != null
                || decision.operation() != Operation.CONFIRM_BOOKING
                || decision.confirmation() == ConfirmationPolarity.NEGATIVE
                || !isBookingIntent(turn.normalizedText())) return decision;
        BookingPatch patch = decision.bookingPatch();
        return new ConversationDecision(decision.schemaVersion(), DialogAct.START_REQUEST, Operation.START_BOOKING,
                patch, decision.selection(), ConfirmationPolarity.NONE, decision.topicAction(),
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private boolean isBookingIntent(String normalizedText) {
        String text = normalizedText == null ? "" : normalizedText;
        return text.matches(".*\\b(?:book|booking)\\b.*") && text.matches(".*\\bappointment\\b.*");
    }

    private ConversationDecision withUnresolvedQualifier(ConversationDecision decision) {
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), decision.operation(),
                decision.bookingPatch(), decision.selection(), decision.confirmation(), decision.topicAction(),
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), decision.lookupFilter(), true);
    }

    private ConversationDecision fastPath(NormalizedUserTurn turn, String language,
                                          SessionProjection context, long started) {
        String normalized = turn.normalizedText();
        String selectionText = turn.selectionText();
        boolean confirmationPending = context != null
                && (context.pendingConfirmation() != null || context.pendingCancellation() != null
                || context.pendingRescheduleConfirmation() != null);
        if (confirmationPending && turn.confirmation() != NormalizedUserTurn.Confirmation.NONE
                && turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE
                && turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
            return decision(DialogAct.REQUEST_ALTERNATIVE, Operation.SHOW_MORE_SLOTS, null, null,
                    ConfirmationPolarity.NEGATIVE, language, started);
        }
        if (confirmationPending && turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
            Operation operation = context.pendingRescheduleConfirmation() != null ? Operation.CONFIRM_RESCHEDULE
                    : context.pendingCancellation() != null ? Operation.CONFIRM_CANCELLATION : Operation.CONFIRM_BOOKING;
            return decision(DialogAct.CONFIRM, operation, null, null, ConfirmationPolarity.POSITIVE, language, started);
        }
        if (confirmationPending && turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
            Operation operation = context.pendingRescheduleConfirmation() != null ? Operation.CONFIRM_RESCHEDULE
                    : context.pendingCancellation() != null ? Operation.CANCEL_APPOINTMENT : Operation.UPDATE_BOOKING;
            return decision(DialogAct.REJECT, operation, null, null, ConfirmationPolarity.NEGATIVE, language, started);
        }
        if (turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE) {
            return decision(DialogAct.REQUEST_ALTERNATIVE, Operation.SHOW_MORE_SLOTS, null, null,
                    ConfirmationPolarity.NONE, language, started);
        }
        if (turn.daypart() != null && turn.exactTime() == null) {
            BookingPatch patch = new BookingPatch(null, null, null,
                    ValuePatch.set(turn.daypart().name().toLowerCase(Locale.ROOT)), null);
            Operation operation = context != null && context.pendingReschedule() != null
                    ? Operation.GET_RESCHEDULE_AVAILABILITY : Operation.GET_AVAILABILITY;
            return decision(DialogAct.PROVIDE_INFORMATION, operation, patch, null,
                    ConfirmationPolarity.NONE, language, started);
        }
        if (turn.exactTime() != null && context != null && currentAvailability(context) != null) {
            String exact = turn.exactTime().toString();
            if (matchesTime(context, exact)) return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                    new Selection(null, null, null, exact), ConfirmationPolarity.NONE, language, started);
        }
        if (turn.ordinal() != null && context != null && hasAvailability(context)
                && matchesOrdinal(context, turn.ordinal())) {
            return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                    new Selection(null, turn.ordinal(), null, null), ConfirmationPolarity.NONE, language, started);
        }
        if (turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                && turn.intent() != NormalizedUserTurn.Intent.LOOKUP
                && turn.intent() != NormalizedUserTurn.Intent.CANCELLATION
                && turn.intent() != NormalizedUserTurn.Intent.RESCHEDULE
                && !(turn.intent() == NormalizedUserTurn.Intent.BOOKING
                && (context == null || context.activeDraft() == null))) {
            BookingPatch patch = new BookingPatch(null, null, ValuePatch.set(turn.temporal().localDate().toString()), null, null);
            Operation operation = context != null && context.pendingReschedule() != null
                    ? Operation.UPDATE_RESCHEDULE : Operation.UPDATE_BOOKING;
            return decision(DialogAct.CHANGE_INFORMATION, operation, patch, null,
                    ConfirmationPolarity.NONE, language, started);
        }
        if (confirmationPending
                && turn.controls().contains(DeterministicControl.CONFIRMATION_NEGATIVE)
                && turn.controls().contains(DeterministicControl.SHOW_MORE_SLOTS)) {
            return decision(DialogAct.REQUEST_ALTERNATIVE, Operation.SHOW_MORE_SLOTS, null, null,
                    ConfirmationPolarity.NEGATIVE, language, started);
        }
        if (confirmationPending && isPositiveConfirmation(normalized)) {
            Operation operation = context.pendingRescheduleConfirmation() != null ? Operation.CONFIRM_RESCHEDULE
                    : context.pendingCancellation() != null ? Operation.CONFIRM_CANCELLATION : Operation.CONFIRM_BOOKING;
            return decision(DialogAct.CONFIRM, operation, null, null,
                    ConfirmationPolarity.POSITIVE, language, started);
        }
        if (confirmationPending && isNegativeConfirmation(normalized)) {
            Operation operation = context.pendingRescheduleConfirmation() != null ? Operation.CONFIRM_RESCHEDULE
                    : context.pendingCancellation() != null ? Operation.CANCEL_APPOINTMENT : Operation.UPDATE_BOOKING;
            return decision(DialogAct.REJECT, operation, null, null,
                    ConfirmationPolarity.NEGATIVE, language, started);
        }
        ConversationDecision providerSelection = providerCandidateFastPath(turn, context, language, started);
        if (providerSelection != null) return providerSelection;
        ConversationDecision timeConstraint = timeConstraintFastPath(normalized, language, started);
        if (timeConstraint != null && context != null && context.pendingReschedule() != null) {
            return decision(DialogAct.PROVIDE_INFORMATION, Operation.GET_RESCHEDULE_AVAILABILITY,
                    timeConstraint.bookingPatch(), null, ConfirmationPolarity.NONE, language, started);
        }
        if (timeConstraint != null) return timeConstraint;
        if (isShowMoreControl(normalized)) {
            return decision(DialogAct.REQUEST_ALTERNATIVE, Operation.SHOW_MORE_SLOTS, null, null,
                    ConfirmationPolarity.NONE, language, started);
        }
        var ordinal = ORDINAL.matcher(selectionText);
        if (ordinal.matches() && context != null && currentAvailability(context) != null) {
            return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                    new Selection(null, ordinalValue(ordinal.group(1)), null, null),
                    ConfirmationPolarity.NONE, language, started);
        }
        var acceptedOrdinal = ORDINAL_ACCEPTANCE.matcher(selectionText);
        if (acceptedOrdinal.matches() && context != null && hasAvailability(context)) {
            int value = ordinalValue(acceptedOrdinal.group(1));
            if (matchesOrdinal(context, value)) {
                return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                        new Selection(null, value, null, null), ConfirmationPolarity.NONE, language, started);
            }
        }
        var time = CLOCK_TIME.matcher(selectionText);
        if (time.matches() && context != null && currentAvailability(context) != null) {
            String exact = normalizeTime(time.group(1), time.group(2), time.group(3));
            return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                    new Selection(null, null, null, exact), ConfirmationPolarity.NONE, language, started);
        }
        var acceptedTime = TIME_ACCEPTANCE.matcher(selectionText);
        if (acceptedTime.matches() && context != null && hasAvailability(context)) {
            String exact = normalizeTime(acceptedTime.group(1), acceptedTime.group(2), acceptedTime.group(3));
            if (matchesTime(context, exact)) {
                return decision(DialogAct.SELECT_OPTION, Operation.SELECT_SLOT, null,
                        new Selection(null, null, null, exact), ConfirmationPolarity.NONE, language, started);
            }
        }
        Matcher daypart = DAYPART_CONTROL.matcher(normalized);
        if (daypart.find() && isAvailabilityRefinement(normalized)) {
            BookingPatch patch = new BookingPatch(null, null, null,
                    ValuePatch.set(daypart.group(1).toLowerCase(Locale.ROOT)), null);
            Operation operation = context != null && context.pendingReschedule() != null
                    ? Operation.GET_RESCHEDULE_AVAILABILITY : Operation.GET_AVAILABILITY;
            return decision(DialogAct.PROVIDE_INFORMATION, operation, patch, null,
                    ConfirmationPolarity.NONE, language, started);
        }
        if (isIsoDate(normalized)) {
            return decision(DialogAct.CHANGE_INFORMATION, Operation.UPDATE_BOOKING,
                    new BookingPatch(null, null, ValuePatch.set(normalized), null, null), null,
                    ConfirmationPolarity.NONE, language, started);
        }
        return null;
    }

    private ConversationDecision providerCandidateFastPath(NormalizedUserTurn turn, SessionProjection context,
                                                            String language, long started) {
        ProviderSearchResult result = context == null ? null : context.latestProviderResult();
        if (result == null || result.candidates().isEmpty() || result.status() != AivaV2Models.ResolutionStatus.AMBIGUOUS) {
            return null;
        }
        String selectionText = turn.selectionText();
        Matcher ordinal = PROVIDER_ORDINAL.matcher(selectionText);
        if (ordinal.matches()) {
            int value = ordinalValue(ordinal.group(1));
            return providerSelectionDecision(result, value, language, started);
        }
        String query = StringUtils.hasText(turn.explicitDoctorReference())
                ? turn.explicitDoctorReference() : selectionText;
        if (!isProviderSelectionText(query)) return null;
        var candidate = AivaV2ProviderCandidateResolver.byDisplayedText(result, query);
        return decision(DialogAct.SELECT_OPTION, Operation.RESOLVE_PROVIDER, null,
                candidate.map(value -> new Selection(value.candidateHandle(), null, null, null)).orElse(null),
                ConfirmationPolarity.NONE, language, started);
    }

    private ConversationDecision providerSelectionDecision(ProviderSearchResult result, int ordinal,
                                                            String language, long started) {
        var candidate = AivaV2ProviderCandidateResolver.byOrdinal(result, ordinal);
        return decision(DialogAct.SELECT_OPTION, Operation.RESOLVE_PROVIDER, null,
                candidate.map(value -> new Selection(value.candidateHandle(), null, null, null)).orElse(null),
                ConfirmationPolarity.NONE, language, started);
    }

    private boolean isProviderSelectionText(String text) {
        if (!StringUtils.hasText(text)) return false;
        return text.matches("(?i)^(?:book\\s+with\\s+|with\\s+)?(?:doctor|dr|doc)\\b.*");
    }

    private boolean isAvailabilityRefinement(String text) {
        return text.contains("slot") || text.contains("availability") || DAYPART_CONTROL.matcher(text).find();
    }

    private boolean isPositiveConfirmation(String text) {
        return List.of("yes", "yes please", "confirm", "go ahead")
                .contains(normalizeControlToken(text));
    }

    private boolean isNegativeConfirmation(String text) {
        return List.of("no", "no thanks", "don't", "never mind")
                .contains(normalizeControlToken(text));
    }

    private String normalizeControlToken(String text) {
        return (text == null ? "" : text).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
    }

    private boolean isShowMoreControl(String text) {
        if (SHOW_MORE_CONTROL.matcher(text).matches()) return true;
        boolean moreOrNext = text.matches(".*\\b(?:more|next)\\b.*");
        boolean slotsOrShow = text.matches(".*\\b(?:slots?|show)\\b.*");
        return moreOrNext && slotsOrShow;
    }

    private String semanticControl(ConversationDecision decision) {
        if (decision.confirmation() == ConfirmationPolarity.POSITIVE) return "CONFIRMATION_POSITIVE";
        if (decision.confirmation() == ConfirmationPolarity.NEGATIVE) return "CONFIRMATION_NEGATIVE";
        return switch (decision.operation()) {
            case SELECT_SLOT -> "SELECT_SLOT";
            case SHOW_MORE_SLOTS -> "SHOW_MORE";
            case GET_AVAILABILITY, GET_RESCHEDULE_AVAILABILITY ->
                    decision.bookingPatch().timeWindow().mode() == PatchMode.SET ? "DAYPART" : "TIME_CONSTRAINT";
            default -> decision.operation().name();
        };
    }

    private String normalizedTime(ConversationDecision decision) {
        if (decision.selection() != null && StringUtils.hasText(decision.selection().exactTime())) {
            return decision.selection().exactTime();
        }
        var constraint = decision.bookingPatch().availabilityTimeConstraint().value();
        if (constraint == null) return null;
        return constraint.startTime() != null ? constraint.startTime().toString()
                : constraint.endTime() == null ? null : constraint.endTime().toString();
    }

    private String normalizedDaypart(ConversationDecision decision) {
        return decision.bookingPatch().timeWindow().mode() == PatchMode.SET
                ? decision.bookingPatch().timeWindow().value() : null;
    }

    private String activeWorkflow(SessionProjection context) {
        if (context != null && context.pendingReschedule() != null) return "RESCHEDULE";
        if (context != null && (context.pendingConfirmation() != null || context.pendingCancellation() != null)) {
            return "BOOKING_OR_CANCELLATION";
        }
        return "NONE";
    }

    private ConversationDecision timeConstraintFastPath(String text, String language, long started) {
        Matcher matcher = AFTER_BEFORE_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BETWEEN,
                    matcher.group(1), matcher.group(2), language, started);
        }
        matcher = COMPARATIVE_BETWEEN_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BETWEEN,
                    matcher.group(1), matcher.group(2), language, started);
        }
        matcher = BETWEEN_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BETWEEN,
                    matcher.group(1), matcher.group(2), language, started);
        }
        matcher = FROM_TO_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BETWEEN,
                    matcher.group(1), matcher.group(2), language, started);
        }
        matcher = LATER_THAN_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.AFTER,
                    matcher.group(1), null, language, started);
        }
        matcher = AFTER_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.AFTER,
                    matcher.group(1), null, language, started);
        }
        matcher = EARLIER_THAN_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BEFORE,
                    null, matcher.group(1), language, started);
        }
        matcher = BEFORE_TIME_CONSTRAINT.matcher(text);
        if (matcher.find()) {
            return constraintDecision(AvailabilityTimeConstraintMode.BEFORE,
                    null, matcher.group(1), language, started);
        }
        return null;
    }

    private ConversationDecision constraintDecision(AvailabilityTimeConstraintMode mode, String startText,
                                                    String endText, String language, long started) {
        try {
            AvailabilityTimeConstraint constraint = new AvailabilityTimeConstraint(mode,
                    startText == null ? null : parseTime(startText),
                    endText == null ? null : parseTime(endText));
            BookingPatch patch = new BookingPatch(null, null, null, null, null, null,
                    new AvailabilityTimeConstraintPatch(PatchMode.SET, constraint));
            return decision(DialogAct.PROVIDE_INFORMATION, Operation.GET_AVAILABILITY, patch, null,
                    ConfirmationPolarity.NONE, language, started);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasAvailability(SessionProjection context) {
        var availability = currentAvailability(context);
        return availability != null && !availability.slots().isEmpty();
    }

    private boolean matchesOrdinal(SessionProjection context, int ordinal) {
        var availability = currentAvailability(context);
        return ordinal == -1 || (ordinal > 0
                && ordinal <= availability.slots().size());
    }

    private boolean matchesTime(SessionProjection context, String exact) {
        return currentAvailability(context).slots().stream()
                .anyMatch(slot -> exact.equals(slot.startsAt().toString()));
    }

    private AivaV2Models.AvailabilityResult currentAvailability(SessionProjection context) {
        if (context == null) return null;
        if (context.pendingReschedule() != null && context.pendingReschedule().latestAvailability() != null) {
            return context.pendingReschedule().latestAvailability();
        }
        return context.latestAvailabilityResult();
    }

    private ConversationDecision parse(AiOrchestrationResponse response, String language, long latencyMs) {
        try {
            String json = StringUtils.hasText(response.structuredJson()) ? response.structuredJson() : response.outputText();
            Map<String, Object> root = objectMapper.readValue(stripFence(json), MAP_TYPE);
            if (root.get("decision") instanceof Map<?, ?> decision) {
                root = castMap(decision);
            } else if (root.get("answer") instanceof Map<?, ?> answer) {
                root = castMap(answer);
            }
            String version = string(root.get("schemaVersion"));
            if (!AivaV2Models.DECISION_SCHEMA_VERSION.equals(version)) {
                return ConversationDecision.unknown(language, response.provider(), response.fallbackUsed(), latencyMs);
            }
            if (!validEnum(DialogAct.class, root.get("dialogAct"))
                    || !validEnum(Operation.class, root.get("operation"))
                    || !validOptionalEnum(ConfirmationPolarity.class, root.get("confirmation"))
                    || !validOptionalEnum(TopicAction.class, root.get("topicAction"))) {
                return ConversationDecision.unknown(language, response.provider(), response.fallbackUsed(), latencyMs);
            }
            double confidence = confidence(root);
            Operation operation = enumValue(Operation.class, root.get("operation"), Operation.UNKNOWN);
            if (confidence < 0.55d || confidence > 1.0d || operation == Operation.UNKNOWN) {
                return ConversationDecision.unknown(language, response.provider(), response.fallbackUsed(), latencyMs);
            }
            Map<String, Object> patch = map(root.get("bookingPatch"));
            Map<String, Object> selection = map(root.get("selection"));
            Map<String, Object> lookup = map(root.get("lookupFilter"));
            return validateDecision(new ConversationDecision(
                    version,
                    enumValue(DialogAct.class, root.get("dialogAct"), DialogAct.UNKNOWN),
                    operation,
                    new BookingPatch(patch(patch.get("doctorText")), patch(patch.get("specialtyText")),
                            patch(patch.get("dateExpression")), patch(patch.get("timeWindow")), patch(patch.get("exactTime")),
                            patch(patch.get("clinicText")),
                            constraintPatch(patch.get("availabilityTimeConstraint"))),
                    selection.isEmpty() ? null : new Selection(string(selection.get("candidateRef")), integer(selection.get("ordinal")),
                            string(selection.get("slotRef")), string(selection.get("exactTime"))),
                    enumValue(ConfirmationPolarity.class, root.get("confirmation"), ConfirmationPolarity.NONE),
                    enumValue(TopicAction.class, root.get("topicAction"), TopicAction.CONTINUE),
                    first(string(root.get("responseLanguage")), language, "en"),
                    confidence,
                    response.provider(),
                    response.fallbackUsed(),
                    latencyMs,
                    new AivaV2Models.AppointmentLookupFilter(
                            patch(lookup.get("doctorText")), patch(lookup.get("dateExpression")),
                            operation == Operation.LOOKUP_APPOINTMENTS ? ValuePatch.unchanged() : patch(lookup.get("status")),
                            patch(lookup.get("clinicText")),
                            Boolean.TRUE.equals(lookup.get("nextOnly")))
            ));
        } catch (Exception ex) {
            return ConversationDecision.unknown(language, response == null ? null : response.provider(),
                    response != null && response.fallbackUsed(), latencyMs);
        }
    }

    private ConversationDecision validateDecision(ConversationDecision decision) {
        if (decision == null) return ConversationDecision.unknown("en", "INVALID", false, 0);
        var patch = decision.bookingPatch();
        var constraintPatch = patch.availabilityTimeConstraint();
        var constraint = constraintPatch.value();
        boolean hasSelection = decision.selection() != null
                && (decision.selection().ordinal() != null
                || StringUtils.hasText(decision.selection().exactTime())
                || StringUtils.hasText(decision.selection().slotRef()));
        if (decision.operation() == Operation.SELECT_SLOT && !hasSelection) return invalid(decision);
        boolean criteriaMutation = patch.doctorText().mode() != PatchMode.UNCHANGED
                || patch.specialtyText().mode() != PatchMode.UNCHANGED
                || patch.dateExpression().mode() != PatchMode.UNCHANGED
                || patch.timeWindow().mode() != PatchMode.UNCHANGED
                || patch.exactTime().mode() != PatchMode.UNCHANGED
                || constraintPatch.mode() != PatchMode.UNCHANGED;
        if (decision.operation() == Operation.SHOW_MORE_SLOTS && criteriaMutation) return invalid(decision);
        if (decision.operation() == Operation.SELECT_SLOT && criteriaMutation) return invalid(decision);
        if (constraintPatch.mode() == PatchMode.SET && constraint == null) return invalid(decision);
        if (constraint != null && constraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                && (constraint.startTime() == null || constraint.endTime() == null)) return invalid(decision);
        if (decision.confirmation() == ConfirmationPolarity.POSITIVE && criteriaMutation) return invalid(decision);
        return decision;
    }

    private ConversationDecision invalid(ConversationDecision decision) {
        return ConversationDecision.unknown(decision.responseLanguage(), decision.provider(), decision.fallbackUsed(), decision.latencyMs());
    }

    private String envelope(AiOrchestrationResponse response) {
        try {
            String json = StringUtils.hasText(response.structuredJson()) ? response.structuredJson() : response.outputText();
            Map<String, Object> root = objectMapper.readValue(stripFence(json), MAP_TYPE);
            if (root.get("decision") instanceof Map<?, ?>) return "DECISION";
            if (root.get("answer") instanceof Map<?, ?>) return "ANSWER";
            if (root.containsKey("schemaVersion")) return "ROOT";
            return "INVALID";
        } catch (Exception ex) {
            return "INVALID";
        }
    }

    private Map<String, Object> safeContext(SessionProjection context) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("status", context == null || context.activeDraft() == null ? "NONE" : context.activeDraft().status());
        if (context != null && context.activeDraft() != null) {
            var draft = context.activeDraft();
            safe.put("providerName", draft.selectedProvider() == null ? null : draft.selectedProvider().displayName());
            safe.put("specialty", draft.specialtyFilter());
            safe.put("date", draft.preferredDate());
            safe.put("timeWindow", draft.preferredTimeWindow());
            safe.put("exactTime", draft.exactTime());
            safe.put("slotCount", context.latestAvailabilityResult() == null ? 0 : context.latestAvailabilityResult().slots().size());
        }
        safe.put("confirmationPending", context != null && context.pendingConfirmation() != null);
        safe.put("reschedulePending", context != null && context.pendingReschedule() != null);
        safe.put("rescheduleStatus", context == null || context.pendingReschedule() == null ? null : context.pendingReschedule().status());
        safe.put("rescheduleTargetDate", context == null || context.pendingReschedule() == null ? null : context.pendingReschedule().targetDate());
        safe.put("rescheduleSlotCount", context == null || context.pendingReschedule() == null
                || context.pendingReschedule().latestAvailability() == null ? 0
                : context.pendingReschedule().latestAvailability().slots().size());
        safe.put("rescheduleConfirmationPending", context != null && context.pendingRescheduleConfirmation() != null);
        return safe;
    }

    private String schemaInstructions() {
        return """
                Interpret exactly one booking conversation turn. Return JSON only.
                responseLanguage must contain exactly one BCP-47 language tag such as en, en-IN, or hi-IN; never return a list or comma-separated options.
                schemaVersion must be "1.0". dialogAct: START_REQUEST, PROVIDE_INFORMATION,
                CHANGE_INFORMATION, SELECT_OPTION, REQUEST_ALTERNATIVE, CONFIRM, REJECT,
                ASK_QUESTION, ABANDON, UNKNOWN. operation: START_BOOKING, UPDATE_BOOKING,
                RESOLVE_PROVIDER, GET_AVAILABILITY, SELECT_SLOT, SHOW_MORE_SLOTS,
                PREPARE_BOOKING, CONFIRM_BOOKING, ANSWER_CONTEXT, SUSPEND_BOOKING,
                RESUME_BOOKING, ABANDON_BOOKING, LOOKUP_APPOINTMENTS, CANCEL_APPOINTMENT,
                PREPARE_CANCELLATION, CONFIRM_CANCELLATION, RESCHEDULE_APPOINTMENT,
                UPDATE_RESCHEDULE, GET_RESCHEDULE_AVAILABILITY, SELECT_RESCHEDULE_SLOT,
                PREPARE_RESCHEDULE, CONFIRM_RESCHEDULE, UNKNOWN.
                bookingPatch fields doctorText, specialtyText, dateExpression, timeWindow,
                exactTime, clinicText are objects {mode: UNCHANGED|SET|CLEAR, value: string|null}.
                availabilityTimeConstraint is {mode: UNCHANGED|SET|CLEAR, value:
                {mode: EXACT|AFTER|BEFORE|BETWEEN, startTime: HH:mm|null, endTime: HH:mm|null}}.
                For search constraints, AFTER uses startTime only, BEFORE uses endTime only,
                EXACT uses startTime only, and BETWEEN uses both with startTime before endTime.
                Examples: "after 6 pm" is AFTER with startTime 18:00, "before 8 pm" is BEFORE
                with endTime 20:00, and "after 6 pm and before 8 pm" or "between 6 pm and 8 pm"
                is BETWEEN with startTime 18:00 and endTime 20:00.
                "later than 6 pm" means AFTER with startTime 18:00, "earlier than 8 pm" means BEFORE
                with endTime 20:00, and "later than 6 pm but earlier than 8 pm" or "from 6 pm to 8 pm"
                means BETWEEN with both boundaries.
                selection fields candidateRef, ordinal, slotRef, exactTime.
                lookupFilter fields doctorText, dateExpression, status, clinicText, and nextOnly are used only by
                LOOKUP_APPOINTMENTS. Use nextOnly for requests asking for the earliest upcoming visit.
                confirmation: POSITIVE, NEGATIVE, AMBIGUOUS, NONE. topicAction:
                CONTINUE, SUSPEND, RESUME, ABANDON. Include responseLanguage and confidence.
                Keep doctor text exactly as spoken. Dates should be ISO yyyy-MM-dd when unambiguous.
                Never return patient, tenant, doctor, clinic, slot, authorization, or booking truth.
                A positive confirmation means CONFIRM_BOOKING only when confirmationPending is true.
                Cancellation requests use CANCEL_APPOINTMENT with lookupFilter criteria; resolve the
                authorized appointment server-side. A positive confirmation for a prepared cancellation
                uses CONFIRM_CANCELLATION and must not perform another lookup.
                Reschedule requests use RESCHEDULE_APPOINTMENT with source appointment criteria in
                lookupFilter. After the source is bound, UPDATE_RESCHEDULE changes the target date/time
                using bookingPatch and GET_RESCHEDULE_AVAILABILITY retrieves slots for the same bound doctor
                and owning clinic. SELECT_RESCHEDULE_SLOT selects only from that current result, and
                CONFIRM_RESCHEDULE confirms only a server-held reschedule capability. Never return IDs.
                Context questions use ANSWER_CONTEXT. Requests to continue the current availability
                result use SHOW_MORE_SLOTS, including "Do you have more slots?", "Show me more",
                "Any other slots?", and "Show next slots". A request that adds or changes a date,
                time, or time window is a booking update instead, not SHOW_MORE_SLOTS.
                A choice from the current availability result uses SELECT_SLOT: put an ordinal
                in selection.ordinal, an accepted displayed time in selection.exactTime, or a
                stable candidate in selection.slotRef. Examples include "11:00 works", "17:30 is
                fine", and "the second one". bookingPatch.exactTime is only for searching for a
                time, such as "Do you have anything around 5 PM?"; never use it for an accepted
                current slot. Do not write response prose.
                """;
    }

    private ConversationDecision decision(DialogAct act, Operation operation, BookingPatch patch, Selection selection,
                                          ConfirmationPolarity confirmation, String language, long started) {
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION, act, operation, patch, selection,
                confirmation, TopicAction.CONTINUE, language, 1d, "DETERMINISTIC", false, elapsed(started));
    }

    private AvailabilityTimeConstraintPatch constraintPatch(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return AvailabilityTimeConstraintPatch.unchanged();
        PatchMode patchMode = enumValue(PatchMode.class, raw.get("mode"), PatchMode.UNCHANGED);
        if (patchMode != PatchMode.SET) return new AvailabilityTimeConstraintPatch(patchMode, null);
        Map<String, Object> constraint = map(raw.get("value"));
        AvailabilityTimeConstraintMode mode = enumValue(AvailabilityTimeConstraintMode.class,
                constraint.get("mode"), null);
        if (mode == null) throw new IllegalArgumentException("Unknown availability time constraint");
        LocalTime start = parseTime(string(constraint.get("startTime")));
        LocalTime end = parseTime(string(constraint.get("endTime")));
        if (mode == AvailabilityTimeConstraintMode.BEFORE && end == null) {
            end = start;
            start = null;
        }
        return new AvailabilityTimeConstraintPatch(patchMode, new AvailabilityTimeConstraint(mode, start, end));
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('.', ':');
        boolean pm = normalized.endsWith("pm");
        boolean am = normalized.endsWith("am");
        if (am || pm) normalized = normalized.substring(0, normalized.length() - 2).trim();
        String[] parts = normalized.split(":");
        int hour = Integer.parseInt(parts[0].trim());
        int minute = parts.length == 1 ? 0 : Integer.parseInt(parts[1].trim());
        if (am && hour == 12) hour = 0;
        if (pm && hour < 12) hour += 12;
        return LocalTime.of(hour, minute);
    }

    private ValuePatch patch(Object value) {
        if (value == null) return ValuePatch.unchanged();
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = castMap(raw);
            PatchMode mode = enumValue(PatchMode.class, map.get("mode"), null);
            if (mode == null) throw new IllegalArgumentException("Unknown patch mode");
            return new ValuePatch(mode, string(map.get("value")));
        }
        return ValuePatch.set(string(value));
    }

    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> raw ? castMap(raw) : Map.of(); }
    private Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
    private String stripFence(String value) {
        if (value == null) return "{}";
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }
    private <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback) {
        try { return value == null ? fallback : Enum.valueOf(type, String.valueOf(value).trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ex) { return fallback; }
    }
    private <E extends Enum<E>> boolean validEnum(Class<E> type, Object value) {
        if (value == null) return false;
        try { Enum.valueOf(type, String.valueOf(value).trim().toUpperCase(Locale.ROOT)); return true; }
        catch (RuntimeException ex) { return false; }
    }
    private <E extends Enum<E>> boolean validOptionalEnum(Class<E> type, Object value) {
        return value == null || validEnum(type, value);
    }
    private String string(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private Integer integer(Object value) { try { return value == null ? null : Integer.valueOf(String.valueOf(value)); } catch (RuntimeException ex) { return null; } }
    private double confidence(Map<String, Object> root) {
        if (!root.containsKey("confidence")) return DEFAULT_CONFIDENCE;
        Object value = root.get("confidence");
        if (value == null) return 0d;
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : 0d;
        } catch (RuntimeException ex) {
            return 0d;
        }
    }
    private String normalizationStatus(AiOrchestrationResponse response, ConversationDecision decision) {
        if (decision.operation() == Operation.UNKNOWN) return "INVALID_OR_UNKNOWN";
        return defaultsApplied(response).isEmpty() ? "VALID" : "VALID_WITH_DEFAULTS";
    }
    private List<String> defaultsApplied(AiOrchestrationResponse response) {
        if (response == null || !"GEMINI".equalsIgnoreCase(response.provider())) return List.of();
        try {
            String json = StringUtils.hasText(response.structuredJson()) ? response.structuredJson() : response.outputText();
            Map<String, Object> root = objectMapper.readValue(stripFence(json), MAP_TYPE);
            if (root.get("decision") instanceof Map<?, ?> decision) root = castMap(decision);
            else if (root.get("answer") instanceof Map<?, ?> answer) root = castMap(answer);
            List<String> defaults = new java.util.ArrayList<>();
            if (!root.containsKey("confidence")) defaults.add("CONFIDENCE_DEFAULT");
            if (!root.containsKey("confirmation")) defaults.add("CONFIRMATION_NONE");
            if (!root.containsKey("topicAction")) defaults.add("TOPIC_ACTION_CONTINUE");
            if (!root.containsKey("bookingPatch")) defaults.add("BOOKING_PATCH_ABSENT");
            if (!root.containsKey("selection")) defaults.add("SELECTION_ABSENT");
            if (!root.containsKey("lookupFilter")) defaults.add("LOOKUP_FILTER_ABSENT");
            if (!root.containsKey("responseLanguage")) defaults.add("RESPONSE_LANGUAGE_DEFAULT");
            return List.copyOf(defaults);
        } catch (Exception ex) {
            return List.of();
        }
    }
    private String first(String... values) { for (String value : values) if (StringUtils.hasText(value)) return value; return null; }
    private boolean isIsoDate(String value) { try { LocalDate.parse(value); return true; } catch (RuntimeException ex) { return false; } }
    private int ordinalValue(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "first", "1" -> 1;
            case "second", "2" -> 2;
            case "third", "3" -> 3;
            case "fourth", "4" -> 4;
            case "last" -> -1;
            default -> Integer.parseInt(normalized.replaceFirst("(?:st|nd|rd|th)$", ""));
        };
    }
    private String normalizeTime(String hourValue, String minuteValue, String meridiem) {
        int hour = Integer.parseInt(hourValue);
        int minute = minuteValue == null ? 0 : Integer.parseInt(minuteValue);
        if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
        if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
        return "%02d:%02d".formatted(hour, minute);
    }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000L; }
}
