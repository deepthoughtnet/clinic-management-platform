package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DoctorNameNormalizer;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiEntityRegistry;
import com.deepthoughtnet.clinic.api.patientportal.careai.SpecialtyResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * V2 Voice adapter. It owns only channel mapping and read-only tool selection;
 * business execution remains behind the supplied healthcare tool adapter.
 */
@Service
public class AivaV2VoiceConversationAdapter {
    private static final Logger log = LoggerFactory.getLogger(AivaV2VoiceConversationAdapter.class);
    private static final String BLOCKED_RESPONSE =
            "मैं अभी डॉक्टर और उपलब्ध समय बता सकती हूँ। अपॉइंटमेंट बुकिंग इस वॉइस चरण में अभी सक्षम नहीं है।";
    private static final String CLARIFICATION = "कृपया डॉक्टर, क्लिनिक, सेवा या उपलब्ध समय के बारे में बताइए।";
    private static final String FAILURE = "क्षमा कीजिए, अभी जानकारी प्राप्त नहीं हो पा रही है। कृपया दोबारा बताइए।";

    private final AivaV2SessionStore sessionStore;
    private final AivaV2ConversationDecisionGateway decisionGateway;
    private final LanguageAdapterRegistry languageAdapters;
    private final SpecialtyResolver specialtyResolver = new SpecialtyResolver(new PatientPortalCareAiEntityRegistry());

    @Autowired
    public AivaV2VoiceConversationAdapter(
            AivaV2SessionStore sessionStore,
            AivaV2ConversationDecisionGateway decisionGateway,
            LanguageAdapterRegistry languageAdapters
    ) {
        this.sessionStore = sessionStore;
        this.decisionGateway = decisionGateway;
        this.languageAdapters = languageAdapters;
    }

    public TurnResult continueTurn(AivaExecutionContext context, String transcript,
                                   AivaV2VoiceToolExecutor executor) {
        if (context == null || !StringUtils.hasText(transcript) || executor == null) {
            return TurnResult.failure(FAILURE);
        }
        String key = "VOICE|" + context.tenantId() + "|" + context.sessionId();
        AtomicReference<TurnResult> result = new AtomicReference<>();
        sessionStore.updateTurn(key, null, null, current -> {
            SessionProjection session = current == null
                    ? new SessionProjection(context.sessionId(), context.patientId(), context.tenantId().toString(),
                    null, null, null, null, null, 1L, Instant.now().plusSeconds(30 * 60))
                    : current;
            NormalizedUserTurn turn = languageAdapters.resolve(context.language(), transcript)
                    .normalize(transcript, LocalDate.now(context.timezone()));
            String diagnosticSpeciality = specialty(turn);
            String doctorQuery = doctorQuery(turn, diagnosticSpeciality);
            AvailabilityResult previousAvailability = session.latestAvailabilityResult();
            String cursor = previousAvailability == null ? null : previousAvailability.cursor();
            boolean pendingDaypartChoice = "VOICE_PENDING_DAYPART".equals(cursor);
            boolean pendingDate = "VOICE_PENDING_DATE".equals(cursor);
            boolean pendingAppointmentIntent = "VOICE_PENDING_APPOINTMENT_INTENT".equals(cursor);
            boolean pendingSingleDaypart = cursor != null && cursor.startsWith("VOICE_CONFIRM_DAYPART:");
            boolean pendingScheduleOffer = cursor != null && (cursor.startsWith("VOICE_CONFIRM_SCHEDULE")
                    || cursor.startsWith("VOICE_CONFIRM_SCHEDULE_DAYPART:"));
            QueryType queryType = queryType(turn);
            boolean activeAvailabilityFlow = isAvailabilityContinuation(cursor);
            boolean explicitAvailabilityDate = activeAvailabilityFlow && hasDateExpression(turn);
            if (queryType == null && pendingDate && hasDateExpression(turn)) {
                queryType = QueryType.SLOT_AVAILABILITY;
            }
            if (queryType == null && explicitAvailabilityDate) {
                queryType = QueryType.SLOT_AVAILABILITY;
            }
            if (queryType == null && turn.daypart() != null && pendingDaypartChoice) {
                queryType = QueryType.SLOT_AVAILABILITY;
            }
            if (queryType == null && isShowMore(turn) && previousAvailability != null) {
                queryType = QueryType.SLOT_AVAILABILITY;
            }
            if (queryType == null && turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE
                    && (pendingSingleDaypart || pendingScheduleOffer)) queryType = QueryType.SLOT_AVAILABILITY;
            DoctorResolution doctorResolution = resolveDoctor(session, turn, doctorQuery);
            if ("AMBIGUOUS".equals(doctorResolution.source())) {
                session = session.withActiveVoiceDoctor(null);
            } else if (doctorResolution.doctor() != null) {
                session = session.withActiveVoiceDoctor(doctorResolution.doctor());
            }
            logContinuationTrace(context, cursor, turn, previousAvailability, doctorResolution);

            if (isClosing(turn)) {
                logSemanticInput(context, turn, diagnosticSpeciality, "END_CONVERSATION", null,
                        "CONVERSATION_CLOSE");
                logContextTrace(context, turn, doctorQuery, doctorResolution, "END_CONVERSATION", null,
                        "CONVERSATION_CLOSED");
                result.set(new TurnResult("धन्यवाद। स्वस्थ रहिए।", "END_CONVERSATION", null,
                        false, false, false, null, 0, null, true, turn));
                return execution(session.close(), result.get());
            }

            if (isIncompleteAppointmentReference(turn)) {
                String doctorName = doctorResolution.doctor() != null
                        ? doctorResolution.doctor().displayName() : doctorQuery;
                result.set(new TurnResult(appointmentIntentClarification(doctorName, turn),
                        "CLARIFY_APPOINTMENT_INTENT", null, true, false, false,
                        diagnosticSpeciality, 0, "APPOINTMENT_INTENT_REQUIRED", false, turn));
                AvailabilityResult pendingIntent = appointmentIntentContext(doctorResolution, doctorQuery,
                        resolvedDate(turn, context), turn, context);
                return execution(pendingIntent == null ? session
                        : session.withVoiceAvailabilityResult(pendingIntent), result.get());
            }

            if (pendingDaypartChoice && isShowMore(turn)) {
                result.set(new TurnResult(daypartChoiceClarification(previousAvailability, turn),
                        "CLARIFY_DAYPART", null, true, false, false, null, 0,
                        "DAYPART_CHOICE_REQUIRED", false, turn));
                return execution(session, result.get());
            }

            if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                    && (pendingSingleDaypart || pendingDaypartChoice || pendingScheduleOffer)
                    && !explicitAvailabilityDate && turn.daypart() == null) {
                String response = continuationDeclined(turn);
                SessionProjection stable = session.withVoiceAvailabilityResult(withCursor(previousAvailability, null));
                result.set(new TurnResult(response, "CONTINUE_CONVERSATION", null,
                        true, false, false, null, 0, null, false, turn));
                return execution(stable, result.get());
            }

            if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE && pendingScheduleOffer
                    && queryType == QueryType.SLOT_AVAILABILITY && previousAvailability != null) {
                if (previousAvailability.date() == null) {
                    result.set(new TurnResult(dateClarification(turn), "CLARIFY_DATE", null,
                            true, false, false, null, 0, null, false, turn));
                    return execution(session.withVoiceAvailabilityResult(withCursor(previousAvailability,
                            "VOICE_PENDING_DATE")), result.get());
                }
            }

            if (isBlockedMutation(turn)) {
                logSemanticInput(context, turn, diagnosticSpeciality, intentName(turn), intentName(turn),
                        "READ_ONLY_MUTATION_GUARD");
                String mutationResponse = mutationResponse(doctorResolution, turn);
                logContextTrace(context, turn, doctorQuery, doctorResolution, intentName(turn), intentName(turn),
                        "BLOCKED_READ_ONLY");
                result.set(new TurnResult(mutationResponse, intentName(turn), intentName(turn),
                        true, true, false, null, 0, "BLOCKED_READ_ONLY", false, turn));
                AvailabilityResult retainedDoctor = retainMutationContext(doctorResolution, doctorQuery,
                        previousAvailability, context);
                return execution(session.withVoiceAvailabilityResult(retainedDoctor), result.get());
            }

            if (queryType != null && "AMBIGUOUS".equals(doctorResolution.source())) {
                String clarification = ambiguousDoctorResponse(doctorResolution.candidates(), turn);
                logSemanticInput(context, turn, diagnosticSpeciality, "CLARIFY_DOCTOR", null,
                        "AMBIGUOUS_PREVIOUS_DOCTOR_CONTEXT");
                result.set(new TurnResult(clarification, "CLARIFY_DOCTOR", null, true,
                        false, false, null, 0, "AMBIGUOUS_DOCTOR", false, turn));
                return execution(session, result.get());
            }

            String tool = queryType == QueryType.RECURRING_SCHEDULE ? "FIND_DOCTOR_SCHEDULE"
                    : queryType == QueryType.EFFECTIVE_DATE_SCHEDULE ? "FIND_DOCTOR_SCHEDULE"
                    : queryType != null ? "FIND_SLOTS"
                    : selectTool(turn, session, diagnosticSpeciality, doctorQuery);
            if (tool == null) {
                logSemanticInput(context, turn, diagnosticSpeciality, "UNKNOWN", null,
                        "NO_SUPPORTED_TOOL_SIGNAL_AFTER_NORMALIZATION");
                logContextTrace(context, turn, doctorQuery, doctorResolution, "UNKNOWN", null, "NO_TOOL");
                result.set(new TurnResult(CLARIFICATION, "UNKNOWN", null, true,
                        false, false, null, 0, null, false, turn));
                return execution(session, result.get());
            }
            logSemanticInput(context, turn, diagnosticSpeciality, tool, tool, "TOOL_SELECTED");
            logContextTrace(context, turn, doctorQuery, doctorResolution, tool, tool, "ALLOWED_READ_ONLY");
            String speciality = diagnosticSpeciality;
            String doctorId = doctorResolution.doctor() == null ? null : doctorResolution.doctor().doctorId();
            LocalDate date = resolvedDate(turn, context);
            boolean dateOverrideApplied = explicitAvailabilityDate && date != null
                    && previousAvailability.date() != null && !date.equals(previousAvailability.date());
            boolean explicitMore = isShowMore(turn) && previousAvailability != null && !dateOverrideApplied;
            boolean acceptsSingleDaypart = turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE
                    && previousAvailability != null && previousAvailability.cursor() != null
                    && (previousAvailability.cursor().startsWith("VOICE_CONFIRM_DAYPART:")
                    || previousAvailability.cursor().startsWith("VOICE_CONFIRM_SCHEDULE"));
            boolean followsPendingDate = pendingDate && previousAvailability != null;
            boolean followsPendingDaypart = (pendingDaypartChoice || pendingSingleDaypart || pendingScheduleOffer)
                    && previousAvailability != null;
            boolean followsStoredDoctorContext = previousAvailability != null
                    && ("VOICE_DOCTOR_CONTEXT".equals(previousAvailability.cursor())
                    || pendingAppointmentIntent)
                    && ("FIND_SLOTS".equals(tool) || queryType != null);
            boolean followsActiveAvailability = activeAvailabilityFlow && previousAvailability != null
                    && "FIND_SLOTS".equals(tool);
            if (explicitMore || acceptsSingleDaypart || followsPendingDate || followsPendingDaypart
                    || followsStoredDoctorContext || followsActiveAvailability) {
                if (date == null) date = previousAvailability.date();
                if (doctorId == null) doctorId = previousAvailability.doctorId();
                if (!StringUtils.hasText(doctorId) && !StringUtils.hasText(doctorQuery)
                        && previousAvailability.doctorId() == null && previousAvailability.providerHandle() != null) {
                    doctorQuery = previousAvailability.providerHandle();
                }
            }
            NormalizedUserTurn.Daypart requestedDaypart = turn.daypart();
            if (acceptsSingleDaypart && requestedDaypart == null) {
                requestedDaypart = daypartFromCursor(previousAvailability.cursor());
                if (requestedDaypart == null) requestedDaypart = daypartFromScheduleCursor(previousAvailability.cursor());
                queryType = QueryType.SLOT_AVAILABILITY;
                tool = "FIND_SLOTS";
            }
            if (followsPendingDate && requestedDaypart == null && !dateOverrideApplied) {
                requestedDaypart = daypartFromWindow(previousAvailability.timeWindow());
            }
            if (pendingScheduleOffer && turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE
                    && requestedDaypart == null) {
                requestedDaypart = daypartFromScheduleCursor(previousAvailability.cursor());
            }
            if (explicitMore && requestedDaypart == null) {
                requestedDaypart = daypartFromWindow(previousAvailability.timeWindow());
                queryType = QueryType.SLOT_AVAILABILITY;
                tool = "FIND_SLOTS";
            }
            int pageOffset = explicitMore && previousAvailability != null
                    ? previousAvailability.displayOffset() + previousAvailability.pageSize() : 0;
            if (queryType == null) queryType = QueryType.SLOT_AVAILABILITY;
            if ("FIND_SLOTS".equals(tool) && date == null) {
                if (!StringUtils.hasText(doctorId) && !StringUtils.hasText(doctorQuery)) {
                    result.set(new TurnResult(missingDoctorClarification(turn), "CLARIFY_DOCTOR", null,
                            true, false, false, speciality, 0, null, false, turn));
                    return execution(session, result.get());
                }
                AvailabilityResult pending = new AvailabilityResult(UUID.randomUUID(), null, 0,
                        "voice-date-clarification", StringUtils.hasText(doctorId) ? doctorId : doctorQuery,
                        doctorId, null, null, requestedDaypart == null ? null : requestedDaypart.name(), null,
                        context.timezone().getId(), List.of(), "VOICE_PENDING_DATE", false, Instant.now(),
                        Instant.now().plusSeconds(5 * 60), 0, 3);
                result.set(new TurnResult(dateClarification(turn), "CLARIFY_DATE", null,
                        true, false, false, speciality, 0, null, false, turn));
                return execution(session.withVoiceAvailabilityResult(pending), result.get());
            }
            boolean currentNameCanResolve = StringUtils.hasText(doctorQuery);
            if (("FIND_SLOTS".equals(tool) || "FIND_DOCTOR_SCHEDULE".equals(tool))
                    && !StringUtils.hasText(doctorId) && !currentNameCanResolve) {
                result.set(new TurnResult(missingDoctorClarification(turn), "CLARIFY_DOCTOR", null,
                        true, false, false, speciality, 0, null, false, turn));
                return execution(session, result.get());
            }
            if ("FIND_DOCTOR".equals(tool)) {
                log.info("VOICE_FIND_DOCTOR_INPUT providerCallId={} rawText=\"{}\" normalizedText=\"{}\" "
                                + "explicitDoctorReference=\"{}\" doctorQuery=\"{}\" specialty=\"{}\"",
                        context.sessionId(), turn.rawText(), turn.normalizedText(), turn.explicitDoctorReference(),
                        doctorQuery, speciality);
            }
            boolean staleStateCleared = dateOverrideApplied;
            log.info("VOICE_CONTEXT_TRACE providerCallId={} previousDoctor={} currentExplicitDoctor={} resolvedDoctor={} "
                            + "resolutionSource={} previousDate={} currentExplicitDate={} resolvedDate={} temporalSource={} "
                            + "previousDaypart={} requestedDaypart={} pendingContinuationType={} intent={} tool={} "
                            + "staleStateCleared={} dateOverrideApplied={}",
                    context.sessionId(), doctorResolution.previousDoctorName(), turn.explicitDoctorReference(),
                    doctorResolution.doctor() == null ? null : doctorResolution.doctor().displayName(),
                    doctorResolution.source(), previousAvailability == null ? null : previousAvailability.date(),
                    hasDateExpression(turn) ? resolvedDate(turn, context) : null, date, turn.temporal().rawSpan(),
                    previousAvailability == null ? null : previousAvailability.timeWindow(), requestedDaypart,
                    cursor, tool, tool, staleStateCleared, dateOverrideApplied);
            AivaV2VoiceToolExecutor.ToolResult toolResult = executor.execute(tool, context,
                    new AivaV2VoiceToolExecutor.ToolInput(
                            doctorQuery, speciality, null, null, doctorId, date, queryType, requestedDaypart,
                            context.timezone(), pageOffset));
            SessionProjection updated = session;
            if ("FIND_DOCTOR".equals(tool)) updated = updated.withVoiceAvailabilityResult(null);
            updated = rememberReadOnlyProviderResult(updated, tool, toolResult, doctorQuery, speciality);
            if (toolResult.availabilityResult() != null) {
                updated = updated.withVoiceAvailabilityResult(toolResult.availabilityResult());
            } else if ("FIND_DOCTOR_SCHEDULE".equals(tool) || "FIND_SLOTS".equals(tool)) {
                updated = updated.withVoiceAvailabilityResult(null);
            }
            String response = toolResult.success() ? "" : FAILURE;
            result.set(new TurnResult(response, tool, tool, true, !toolResult.allowed(),
                    toolResult.success(), speciality, toolResult.candidateCount(),
                    toolResult.diagnostics(), false, turn, toolResult));
            return execution(updated, result.get());
        });
        return result.get() == null ? TurnResult.failure(FAILURE) : result.get();
    }

    private void logSemanticInput(AivaExecutionContext context, NormalizedUserTurn turn,
                                  String diagnosticSpeciality, String selectedIntent, String selectedTool,
                                  String toolSelectionReason) {
        String extractedCandidate = turn.specialtyEntity() == null ? null : turn.specialtyEntity().rawSpan();
        String resolverInput = turn.specialtyEntity() == null
                ? turn.rawText() : turn.specialtyEntity().canonicalQuery();
        String resolverOutput = specialtyResolver.canonicalize(resolverInput);
        boolean resolverResolved = StringUtils.hasText(resolverOutput)
                && !resolverOutput.equalsIgnoreCase(resolverInput);
        log.info("VOICE_SEMANTIC_TRACE providerCallId={} rawTranscript=\"{}\" normalizedTranscript=\"{}\" "
                        + "normalizedLanguage={} extractedSpecialtyCandidate=\"{}\" "
                        + "specialtyResolverInput=\"{}\" specialtyResolverResult=\"{}\" "
                        + "specialtyResolverResolved={} selectedSpecialty=\"{}\" "
                        + "explicitDoctorReference=\"{}\" selectedIntent={} selectedTool={} "
                        + "toolSelectionReason={}",
                context.sessionId(), turn.rawText(), turn.normalizedText(), turn.languageCode(), extractedCandidate,
                resolverInput, resolverOutput, resolverResolved, diagnosticSpeciality,
                turn.explicitDoctorReference(), selectedIntent, selectedTool, toolSelectionReason);
    }

    public void endSession(AivaExecutionContext context) {
        if (context != null) endSession(context.tenantId(), context.sessionId());
    }

    public void endSession(UUID tenantId, String providerCallId) {
        if (tenantId == null || !StringUtils.hasText(providerCallId)) return;
        sessionStore.remove("VOICE|" + tenantId + "|" + providerCallId);
    }

    private SessionProjection rememberReadOnlyProviderResult(SessionProjection session, String tool,
                                                              AivaV2VoiceToolExecutor.ToolResult result,
                                                              String doctorQuery, String specialty) {
        if (result == null || !("FIND_DOCTOR".equals(tool) || "FIND_DOCTOR_SCHEDULE".equals(tool)
                || "FIND_SLOTS".equals(tool))) {
            return session;
        }
        // The durable active doctor is independent of workflow projections. Any
        // single-provider result may refresh it; empty schedule/slot projections
        // never erase it. A genuinely ambiguous provider search clears selection.
        List<ProviderCandidate> candidates = result.providers().stream().map(provider -> new ProviderCandidate(
                provider.doctorId(), provider.doctorId(), provider.doctorId(), provider.clinicId(), provider.tenantId(),
                provider.clinicSlug(), null, provider.displayName(), provider.specialty(), provider.clinicName(),
                ProviderSource.CARE_PRIVATE, new ProviderCapabilities(false, true, false, false, false, false, true)
        )).toList();
        if (candidates.size() == 1) {
            ProviderCandidate candidate = candidates.getFirst();
            ProviderCandidate active = session.activeVoiceDoctor();
            boolean explicitSwitch = StringUtils.hasText(doctorQuery) || StringUtils.hasText(specialty);
            if (active == null || active.doctorId().equals(candidate.doctorId()) || explicitSwitch) {
                session = session.withActiveVoiceDoctor(candidate);
            }
        } else if ("FIND_DOCTOR".equals(tool) && candidates.size() > 1) {
            session = session.withActiveVoiceDoctor(null);
        }
        // Only provider discovery owns/replaces the candidate set. Schedule and
        // slot tools may omit provider projections; an empty list from those
        // tools must not erase a previously resolved doctor or ambiguity set.
        if (!"FIND_DOCTOR".equals(tool) && result.providers().isEmpty()) return session;
        Instant now = Instant.now();
        ProviderCandidate resolved = candidates.size() == 1 ? candidates.getFirst() : null;
        ProviderSearchResult providers = new ProviderSearchResult(UUID.randomUUID(),
                candidates.isEmpty() ? ResolutionStatus.NOT_FOUND
                        : resolved == null ? ResolutionStatus.AMBIGUOUS : ResolutionStatus.RESOLVED,
                "voice-read-only", candidates, resolved, now, now.plusSeconds(5 * 60));
        return session.withVoiceProviderResult(providers);
    }

    private QueryType queryType(NormalizedUserTurn turn) {
        if (isRecurringScheduleQuestion(turn)) {
            return hasExplicitScheduleDate(turn) ? QueryType.EFFECTIVE_DATE_SCHEDULE
                    : QueryType.RECURRING_SCHEDULE;
        }
        if (isExplicitSlotRequest(turn) || isAppointmentSlotRequest(turn)) return QueryType.SLOT_AVAILABILITY;
        if (hasDateExpression(turn) && hasAvailabilityLanguage(turn)) {
            return QueryType.EFFECTIVE_DATE_AVAILABILITY;
        }
        if (turn.daypart() != null && hasAvailabilityLanguage(turn)) {
            return refersToTomorrow(turn) ? QueryType.EFFECTIVE_DATE_AVAILABILITY : QueryType.RECURRING_SCHEDULE;
        }
        if (isShowMore(turn)) return QueryType.SLOT_AVAILABILITY;
        return null;
    }

    private boolean isAvailabilityContinuation(String cursor) {
        return cursor != null && (cursor.equals("VOICE_PENDING_DATE")
                || cursor.equals("VOICE_PENDING_DAYPART")
                || cursor.equals("VOICE_PENDING_APPOINTMENT_INTENT")
                || cursor.startsWith("VOICE_CONFIRM_DAYPART:")
                || cursor.startsWith("VOICE_CONFIRM_SCHEDULE")
                || cursor.startsWith("VOICE_DAYPART:")
                || cursor.equals("VOICE_SHOW_MORE"));
    }

    private boolean isRecurringScheduleQuestion(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:बैठते|बैठेंगे|बैठें|बैठने का समय|बैठने का टाइम|सिटिंग\\s+टाइम|"
                + "sitting\\s*time|consultation\\s+hours|consult\\s+hours|consultation\\s+time|"
                + "doctor\\s+schedule|doctor's\\s+schedule|when\\s+does.*consult|does.*consult.*(?:morning|afternoon|evening)|"
                + "when.*consultation\\s+hours|kab\\s+baith(?:te|enge)|kis\\s+time.*baith(?:te|enge)|"
                + "किस समय.*बैठते|कब.*बैठ(?:ते|ेंगे)|doctor\\s+kab\\s+available|कब.*available.*होते|"
                + "kab.*available.*hote|doctor.*(?:evening|morning|afternoon)\\s+timing|doctor\\s+ka.*timing|"
                + "डॉक्टर.*(?:समय|टाइमिंग)|consult.*(?:करते|karte)).*" );
    }

    private boolean hasExplicitScheduleDate(NormalizedUserTurn turn) {
        return hasDateExpression(turn);
    }

    private boolean isExplicitSlotRequest(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:\\bslots?\\b|स्लॉट|available\\s+slots|appointment\\s+slots|अपॉइंटमेंट स्लॉट).*" );
    }

    private boolean isAppointmentSlotRequest(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*" )
                && isAppointmentAvailabilityRequest(turn);
    }

    private boolean hasAvailabilityLanguage(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:available|अवेलेबल|availability|उपलब्ध|मिलेगा|मिलेंगे|मिल सकता|मिल सकती|मिल सकते|"
                + "mil sakta|mil sakti|mil sakte|can i get|can get|openings?|समय|time|slot).*" );
    }

    private boolean isAppointmentAvailabilityRequest(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:available|availability|उपलब्ध|मिलेगा|मिलेंगे|मिल सकता|मिल सकती|मिल सकते|"
                + "mil sakta|mil sakti|mil sakte|can i get|can get|free slot|slot मिल|slot mil|खाली स्लॉट|"
                + "appointment.*(?:possible|available|openings?|मिल|उपलब्ध)|(?:मिल|उपलब्ध).*appointment).*" );
    }

    private boolean isIncompleteAppointmentReference(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean appointmentMention = value.matches("(?s).*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*" );
        return appointmentMention && StringUtils.hasText(turn.explicitDoctorReference())
                && turn.intent() == NormalizedUserTurn.Intent.UNKNOWN
                && !isAppointmentAvailabilityRequest(turn) && !isExplicitSlotRequest(turn)
                && !isBookingRequest(turn);
    }

    private String appointmentIntentClarification(String doctorName, NormalizedUserTurn turn) {
        String name = StringUtils.hasText(doctorName) ? "Dr. " + doctorName : "the doctor";
        if (isEnglish(turn)) return "Would you like to check available slots with " + name
                + ", request booking, or ask about an existing appointment?";
        if (isHinglish(turn)) return name + " ke available slots dekhne hain, appointment book karna hai, "
                + "ya existing appointment ki jankari chahiye?";
        return "आप " + name + " के available slots देखना चाहते हैं, appointment book करना चाहते हैं, "
                + "या existing appointment की जानकारी चाहते हैं?";
    }

    private boolean refersToTomorrow(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(?:\\btomorrow\\b|\\bkal\\b|कल).*" );
    }

    private boolean hasDateExpression(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean lexicalDate = value.matches(
                "(?s).*(?:\\btoday\\b|\\baaj\\b|आज|\\btomorrow\\b|\\bkal\\b|कल|"
                        + "day after tomorrow|\\bparson\\b|परसों|परसो|"
                        + "\\b(?:next\\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b|"
                        + "सोमवार|मंगलवार|बुधवार|गुरुवार|शुक्रवार|शनिवार|रविवार|"
                        + "\\b[0-3]?\\d(?:st|nd|rd|th)?\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\b|"
                        + "\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+[0-3]?\\d\\b).*" );
        if (lexicalDate) return true;
        // The temporal normalizer can recognize the Hindi short weekday "रवि"
        // inside a Devanagari-transcribed doctor name. Only trust non-lexical
        // parsed dates when the span is not itself the current doctor reference.
        String span = turn.temporal().rawSpan();
        String doctor = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().rawSpan() + " " + turn.doctorEntity().canonicalQuery();
        // "रवि" is also a short weekday alias in the temporal normalizer, but
        // in a doctor/schedule utterance it is commonly the start of Ravi's name.
        // Full "रविवार" remains recognized by the lexical-date branch above.
        if ("रवि".equals(span) && (doctor != null || hasExplicitSlotEvidence(turn)
                || isRecurringScheduleQuestion(turn))) return false;
        return span != null && turn.temporal().localDate() != null
                && !(doctor != null && doctor.toLowerCase(Locale.ROOT).contains(span.toLowerCase(Locale.ROOT)));
    }

    private LocalDate resolvedDate(NormalizedUserTurn turn, AivaExecutionContext context) {
        if (!hasDateExpression(turn)) return null;
        if (turn.temporal().localDate() != null) return turn.temporal().localDate();
        String raw = (turn.temporal().rawSpan() == null ? turn.rawText() : turn.temporal().rawSpan())
                .toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(context.timezone());
        if (raw.matches("(?s).*(?:day after tomorrow|\\bparson\\b|परसों|परसो).*")) return today.plusDays(2);
        if (raw.matches("(?s).*(?:\\btomorrow\\b|\\bkal\\b|कल).*")) return today.plusDays(1);
        if (raw.matches("(?s).*(?:\\btoday\\b|\\baaj\\b|आज).*")) return today;
        return null;
    }

    private boolean isShowMore(NormalizedUserTurn turn) {
        String raw = (turn.rawText() == null ? "" : turn.rawText()).toLowerCase(Locale.ROOT);
        return turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE
                || raw.matches("(?s).*(?:show\\s+(?:me\\s+)?more|more\\s+slots?|next(?:\\s+slots?)?|other\\s+times|"
                + "और\\s*(?:समय|स्लॉट|बताइए)|दूसरे\\s+slots?|अगला\\s+बताइए|"
                + "aur\\s*(?:slots?|batao|samay)|next\\s+slots?\\s+dikhao|doosre\\s+(?:time|slots?)).*" );
    }

    private NormalizedUserTurn.Daypart daypartFromCursor(String cursor) {
        if (cursor == null || !cursor.startsWith("VOICE_CONFIRM_DAYPART:")) return null;
        try { return NormalizedUserTurn.Daypart.valueOf(cursor.substring("VOICE_CONFIRM_DAYPART:".length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private NormalizedUserTurn.Daypart daypartFromWindow(String window) {
        if (window == null) return null;
        try { return NormalizedUserTurn.Daypart.valueOf(window.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private NormalizedUserTurn.Daypart daypartFromScheduleCursor(String cursor) {
        if (cursor == null || !cursor.startsWith("VOICE_CONFIRM_SCHEDULE_DAYPART:")) return null;
        try { return NormalizedUserTurn.Daypart.valueOf(cursor.substring("VOICE_CONFIRM_SCHEDULE_DAYPART:".length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private AvailabilityResult withCursor(AvailabilityResult previous, String cursor) {
        if (previous == null) return null;
        return new AvailabilityResult(previous.requestId(), previous.draftId(), previous.draftRevision(),
                previous.criteriaFingerprint(), previous.providerHandle(), previous.doctorId(), previous.clinicId(),
                previous.date(), previous.timeWindow(), previous.availabilityTimeConstraint(), previous.timezone(),
                previous.slots(), cursor, previous.hasMore(), previous.generatedAt(), previous.expiresAt(),
                previous.displayOffset(), previous.pageSize());
    }

    private AvailabilityResult doctorContext(DoctorResolution resolution, String doctorQuery,
                                             AivaExecutionContext context) {
        ProviderCandidate doctor = resolution.doctor();
        String name = doctor == null ? doctorQuery : doctor.displayName();
        String doctorId = doctor == null ? null : doctor.doctorId();
        if (!StringUtils.hasText(name)) return null;
        Instant now = Instant.now();
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-doctor-context",
                name, doctorId, doctor == null ? null : doctor.clinicId(), null, null, null,
                context.timezone().getId(), List.of(), "VOICE_DOCTOR_CONTEXT", false,
                now, now.plusSeconds(5 * 60), 0, 3);
    }

    private AvailabilityResult appointmentIntentContext(DoctorResolution resolution, String doctorQuery,
                                                         LocalDate date, NormalizedUserTurn turn,
                                                         AivaExecutionContext context) {
        AvailabilityResult doctor = doctorContext(resolution, doctorQuery, context);
        if (doctor == null) return null;
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-appointment-intent-clarification",
                doctor.providerHandle(), doctor.doctorId(), doctor.clinicId(), date,
                turn.daypart() == null ? null : turn.daypart().name(), null, context.timezone().getId(),
                List.of(), "VOICE_PENDING_APPOINTMENT_INTENT", false, Instant.now(),
                Instant.now().plusSeconds(5 * 60), 0, 3);
    }

    private AvailabilityResult retainMutationContext(DoctorResolution resolution, String doctorQuery,
                                                     AvailabilityResult previous, AivaExecutionContext context) {
        AvailabilityResult currentDoctor = doctorContext(resolution, doctorQuery, context);
        if (previous == null) return currentDoctor;
        String providerHandle = currentDoctor != null && StringUtils.hasText(currentDoctor.providerHandle())
                ? currentDoctor.providerHandle() : previous.providerHandle();
        String doctorId = currentDoctor != null && StringUtils.hasText(currentDoctor.doctorId())
                ? currentDoctor.doctorId() : previous.doctorId();
        if (!StringUtils.hasText(providerHandle) && !StringUtils.hasText(doctorId)) return currentDoctor;
        String clinicId = currentDoctor != null && StringUtils.hasText(currentDoctor.clinicId())
                ? currentDoctor.clinicId() : previous.clinicId();
        return new AvailabilityResult(UUID.randomUUID(), null, 0, "voice-doctor-context",
                providerHandle, doctorId, clinicId, previous.date(), null, null,
                context.timezone().getId(), List.of(), "VOICE_DOCTOR_CONTEXT", false,
                Instant.now(), Instant.now().plusSeconds(5 * 60), 0, 3);
    }

    private String dateClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Which day would you like to check — today, tomorrow, or another date?"
                : isHinglish(turn) ? "Aap kis din ke slots dekhna chahenge — aaj, kal, ya koi aur date?"
                : "आप किस दिन के slots देखना चाहेंगे — आज, कल, या कोई और तारीख?";
    }

    private String missingDoctorClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Which doctor's slots would you like to check?"
                : isHinglish(turn) ? "Aap kis doctor ke slots dekhna chahenge?"
                : "आप किस डॉक्टर के slots देखना चाहेंगे?";
    }

    private String continuationDeclined(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Okay. Let me know if you would like to check another time."
                : isHinglish(turn) ? "Theek hai. Kisi aur time ke slots chahiye hon to bataiye."
                : "ठीक है। किसी और समय के slots चाहिए हों तो बताइए।";
    }

    private String daypartChoiceClarification(AvailabilityResult availability, NormalizedUserTurn turn) {
        java.util.Set<NormalizedUserTurn.Daypart> available = availability.slots().stream()
                .filter(slot -> slot.startsAt() != null)
                .map(slot -> slot.startsAt().isBefore(java.time.LocalTime.NOON)
                        ? NormalizedUserTurn.Daypart.MORNING
                        : slot.startsAt().isBefore(java.time.LocalTime.of(17, 0))
                        ? NormalizedUserTurn.Daypart.AFTERNOON : NormalizedUserTurn.Daypart.EVENING)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (isEnglish(turn)) {
            List<String> labels = available.stream().map(part -> switch (part) {
                case MORNING -> "morning";
                case AFTERNOON -> "afternoon";
                case EVENING -> "evening";
            }).toList();
            return "Please choose one of the available times: " + String.join(" or ", labels) + ".";
        }
        if (isHinglish(turn)) {
            List<String> labels = available.stream().map(part -> part.name().toLowerCase(Locale.ROOT)).toList();
            return "Available dayparts mein se ek choose kijiye: " + String.join(" ya ", labels) + ".";
        }
        List<String> labels = available.stream().map(part -> switch (part) {
            case MORNING -> "सुबह";
            case AFTERNOON -> "दोपहर";
            case EVENING -> "शाम";
        }).toList();
        return (labels.isEmpty() ? "उपलब्ध समय" : String.join(" या ", labels)) + " में से कौन सा समय देखना चाहेंगे?";
    }

    private boolean isEnglish(NormalizedUserTurn turn) {
        String language = turn.responseLanguage() == null ? turn.languageCode() : turn.responseLanguage();
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en");
    }

    private boolean isHinglish(NormalizedUserTurn turn) {
        return turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
    }

    private DoctorResolution resolveDoctor(SessionProjection session, NormalizedUserTurn turn, String doctorQuery) {
        var previous = session.latestProviderResult();
        if (StringUtils.hasText(doctorQuery)) {
            if (previous != null) {
                List<ProviderCandidate> explicitMatches = previous.candidates().stream()
                        .filter(candidate -> DoctorNameNormalizer.matches(candidate.displayName(), doctorQuery)).toList();
                if (explicitMatches.size() == 1) {
                    return new DoctorResolution(explicitMatches.getFirst(), "CURRENT_EXPLICIT", List.of());
                }
                if (explicitMatches.size() > 1) {
                    return new DoctorResolution(null, "AMBIGUOUS", explicitMatches);
                }
            }
            ProviderCandidate activeDoctor = session.activeVoiceDoctor();
            if (activeDoctor != null && DoctorNameNormalizer.matches(activeDoctor.displayName(), doctorQuery)) {
                return new DoctorResolution(activeDoctor, "CURRENT_EXPLICIT", List.of());
            }
            return new DoctorResolution(null, "CURRENT_EXPLICIT", List.of());
        }
        // A blocked booking request is still about the active doctor when the
        // caller omits a pronoun/name. Keep that durable call-scoped context
        // available to the read-only response and subsequent availability turns.
        if (isBookingRequest(turn) && session.activeVoiceDoctor() != null) {
            return new DoctorResolution(session.activeVoiceDoctor(), "PREVIOUS_SINGLE_RESULT", List.of());
        }
        if (!referencesDoctorContext(turn)) {
            return new DoctorResolution(null, "NONE", List.of());
        }
        if (previous == null) {
            return session.activeVoiceDoctor() == null
                    ? new DoctorResolution(null, "NONE", List.of())
                    : new DoctorResolution(session.activeVoiceDoctor(), "PREVIOUS_SINGLE_RESULT", List.of());
        }
        List<ProviderCandidate> contextualMatches = previous.candidates().stream()
                .filter(candidate -> hasNameToken(turn.rawText(), candidate.displayName())).toList();
        if (contextualMatches.size() == 1) {
            return new DoctorResolution(contextualMatches.getFirst(), "CURRENT_CONTEXT_REFERENCE", List.of());
        }
        if (contextualMatches.size() > 1) {
            return new DoctorResolution(null, "AMBIGUOUS", contextualMatches);
        }
        if (previous.candidates().size() > 1) {
            return new DoctorResolution(null, "AMBIGUOUS", previous.candidates());
        }
        if (session.activeVoiceDoctor() != null) {
            return new DoctorResolution(session.activeVoiceDoctor(), "PREVIOUS_SINGLE_RESULT", List.of());
        }
        if (previous.resolvedProvider() != null && previous.candidates().size() == 1) {
            return new DoctorResolution(previous.resolvedProvider(), "PREVIOUS_SINGLE_RESULT", List.of());
        }
        return new DoctorResolution(null, "NONE", List.of());
    }

    private boolean hasNameToken(String utterance, String displayName) {
        if (!StringUtils.hasText(utterance) || !StringUtils.hasText(displayName)) return false;
        List<String> utteranceTokens = java.util.Arrays.stream(utterance.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim().split("\\s+"))
                .filter(StringUtils::hasText).map(DoctorNameNormalizer::normalized).toList();
        return java.util.Arrays.stream(displayName.split("\\s+"))
                .map(DoctorNameNormalizer::normalized).filter(token -> token.length() >= 3)
                .anyMatch(utteranceTokens::contains);
    }

    private boolean referencesDoctorContext(NormalizedUserTurn turn) {
        String raw = turn.rawText() == null ? "" : turn.rawText().toLowerCase(Locale.ROOT);
        return raw.matches(".*(?:उनका|उनकी|उनसे|उनके|उसी\\s+डॉक्टर|वही\\s+डॉक्टर|same\\s+doctor|that\\s+doctor|their|his|her|unka|unke|unki|wahi\\s+doctor|same\\s+doctor).*" )
                || isSlotFollowUp(turn) || isRecurringScheduleQuestion(turn)
                || hasDateExpression(turn) && hasAvailabilityLanguage(turn);
    }

    private boolean isSlotFollowUp(NormalizedUserTurn turn) {
        return hasExplicitSlotEvidence(turn);
    }

    private String mutationResponse(DoctorResolution resolution, NormalizedUserTurn turn) {
        String intent = intentName(turn);
        if ("CANCEL_APPOINTMENT".equals(intent)) {
            return isEnglish(turn) ? "Appointment cancellation is not enabled in this voice phase."
                    : isHinglish(turn) ? "Appointment cancel karna is voice phase mein abhi enabled nahi hai."
                    : "इस वॉइस चरण में अपॉइंटमेंट कैंसिल करना अभी सक्षम नहीं है।";
        }
        if ("RESCHEDULE_APPOINTMENT".equals(intent)) {
            return isEnglish(turn) ? "Appointment rescheduling is not enabled in this voice phase. I can help find doctors and available times."
                    : isHinglish(turn) ? "Appointment reschedule karna is voice phase mein abhi enabled nahi hai. Main doctor aur available timings bata sakti hoon."
                    : "इस वॉइस चरण में अपॉइंटमेंट का समय बदलना अभी सक्षम नहीं है। मैं डॉक्टर और उपलब्ध समय बता सकती हूँ।";
        }
        if ("FIND_APPOINTMENTS".equals(intent)) {
            return isEnglish(turn) ? "Appointment lookup is not available in this voice phase."
                    : isHinglish(turn) ? "Appointment details dekhna is voice phase mein abhi available nahi hai."
                    : "इस वॉइस चरण में अपॉइंटमेंट की जानकारी देखना अभी उपलब्ध नहीं है।";
        }
        if ("BOOK_APPOINTMENT".equals(intent) && resolution.doctor() != null) {
            String doctorName = resolution.doctor().displayName();
            return isEnglish(turn)
                    ? "Booking an appointment with Dr. " + doctorName
                            + " is not enabled in this voice phase. I can help with available times."
                    : isHinglish(turn)
                    ? "Dr. " + doctorName + " ke saath appointment book karna is voice phase mein abhi enabled nahi hai."
                            + " Main unke available timings bata sakti hoon."
                    : "डॉ. " + doctorName + " के साथ अपॉइंटमेंट बुक करना इस वॉइस चरण में अभी सक्षम नहीं है।"
                            + " मैं उनके उपलब्ध समय बता सकती हूँ।";
        }
        if ("AMBIGUOUS".equals(resolution.source())) {
            return ambiguousDoctorResponse(resolution.candidates(), turn);
        }
        if ("NONE".equals(resolution.source()) && resolution.referenceWasRequested()) {
            return isEnglish(turn) ? "Which doctor would you like an appointment with?"
                    : isHinglish(turn) ? "Aap kis doctor ke saath appointment chahte hain?"
                    : "कृपया बताइए, आप किस डॉक्टर के साथ अपॉइंटमेंट चाहते हैं?";
        }
        return BLOCKED_RESPONSE;
    }

    private String ambiguousDoctorResponse(List<ProviderCandidate> candidates, NormalizedUserTurn turn) {
        String names = candidates.stream().limit(2).map(ProviderCandidate::displayName)
                .filter(StringUtils::hasText).reduce((left, right) -> left + " या " + right).orElse(null);
        if (isEnglish(turn)) return StringUtils.hasText(names)
                ? "Which doctor do you mean — Dr. " + names.replace(" या ", " or Dr. ") + "?"
                : "Which doctor do you mean?";
        if (isHinglish(turn)) return StringUtils.hasText(names)
                ? "Aap Dr. " + names.replace(" या ", " ya Dr. ") + " mein se kis doctor ki baat kar rahe hain?"
                : "Aap kis doctor ki baat kar rahe hain?";
        return StringUtils.hasText(names) ? "कृपया बताइए, आप डॉ. " + names + " में से किस डॉक्टर की बात कर रहे हैं?"
                : "कृपया बताइए, आप किस डॉक्टर की बात कर रहे हैं?";
    }

    private void logContextTrace(AivaExecutionContext context, NormalizedUserTurn turn, String doctorQuery,
                                 DoctorResolution resolution, String intent, String tool, String policy) {
        log.info("VOICE_CONTEXT_TRACE providerCallId={} currentTurnExplicitDoctor=\"{}\" "
                        + "previousSelectedDoctor=\"{}\" resolvedDoctor=\"{}\" resolutionSource={} "
                        + "intent={} selectedTool={} policyResult={} doctorQuery=\"{}\"",
                context.sessionId(), turn.explicitDoctorReference(), resolution.previousDoctorName(),
                resolution.doctor() == null ? null : resolution.doctor().displayName(), resolution.source(),
                intent, tool, policy, doctorQuery);
    }

    private void logContinuationTrace(AivaExecutionContext context, String cursor, NormalizedUserTurn turn,
                                      AvailabilityResult previous, DoctorResolution resolution) {
        String pending = cursor == null ? "NONE" : cursor.startsWith("VOICE_CONFIRM_DAYPART:")
                || cursor.startsWith("VOICE_CONFIRM_SCHEDULE") ? "OFFER_SLOTS"
                : cursor.startsWith("VOICE_PENDING_DAYPART") ? "OFFER_DAYPART"
                : cursor.startsWith("VOICE_PENDING_DATE") ? "CLARIFY_DATE"
                : turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE || isShowMore(turn) ? "SHOW_MORE" : "NONE";
        log.info("VOICE_CONTINUATION_TRACE providerCallId={} pendingContinuationType={} yesNoResolution={} "
                        + "preservedDoctor={} preservedDate={} preservedDaypart={}",
                context.sessionId(), pending, turn.confirmation(),
                resolution.doctor() == null ? previous == null ? null : previous.providerHandle()
                        : resolution.doctor().displayName(),
                previous == null ? null : previous.date(), previous == null ? null : previous.timeWindow());
    }

    private MessageResponse messageResponse(SessionProjection session, TurnResult result) {
        return new MessageResponse(session.conversationId(), "voice-turn-" + UUID.randomUUID(),
                result.response(), result.closing() ? "CONVERSATION_CLOSED" : "VOICE_READ_ONLY",
                null, "AIVA_V2", false, List.of());
    }

    private String selectTool(NormalizedUserTurn turn, SessionProjection session,
                              String speciality, String doctorQuery) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        if (value.contains("clinic") || value.contains("क्लिनिक")) return "FIND_CLINIC";
        if (value.contains("service") || value.contains("सेवा")) return "FIND_SERVICE";
        boolean slots = hasExplicitSlotEvidence(turn);
        boolean doctor = value.contains("doctor") || value.contains("डॉक्टर")
                || value.contains("physician") || value.contains("फिजिशियन")
                || StringUtils.hasText(speciality) || StringUtils.hasText(doctorQuery)
                || turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference());
        // Explicit slot/time/date evidence wins over the previous selected doctor,
        // but generic provider availability remains FIND_DOCTOR discovery.
        if (slots) return "FIND_SLOTS";
        if (doctor) return "FIND_DOCTOR";
        return null;
    }

    private boolean hasExplicitSlotEvidence(NormalizedUserTurn turn) {
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean slotOrTiming = value.matches(".*(?:\\bslots?\\b|स्लॉट|\\btim(?:e|ing|ings)\\b|समय|टाइम).*" );
        boolean dayOrDate = turn.temporal().localDate() != null
                || value.matches(".*(?:\\btomorrow\\b|\\btoday\\b|\\btonight\\b|\\bmorning\\b|\\bafternoon\\b|\\bevening\\b|\\bnight\\b|कल|आज|सुबह|दोपहर|शाम|रात).*" );
        boolean availabilityWithTime = value.matches(".*(?:availability|उपलब्धता|उपलब्ध).*") && dayOrDate;
        return slotOrTiming || availabilityWithTime;
    }

    private String doctorQuery(NormalizedUserTurn turn, String speciality) {
        if (StringUtils.hasText(speciality)) return null;
        String reference = turn.doctorEntity() == null
                ? turn.explicitDoctorReference() : turn.doctorEntity().canonicalQuery();
        if (!StringUtils.hasText(reference)) return null;
        // The language adapter can expose the post-name tail of a slot request
        // (for example, "ke slots dikhao") as an explicit reference. It is not
        // a doctor name; let the existing selected-provider context resolve it.
        String referenceText = reference.toLowerCase(Locale.ROOT);
        if (hasExplicitSlotEvidence(turn)
                && referenceText.matches(".*(?:slot|slots|स्लॉट|time|timing|समय|availability|उपलब्धता|उपलब्ध).*")) {
            return null;
        }
        String cleaned = reference
                .replaceFirst("(?i)^(?:show|tell|find|give)\\s+me\\s+", "")
                .replaceFirst("(?i)^me\\s+", "")
                .replaceFirst("(?i)[’']s$", "")
                .replaceFirst("(?i)^(?:dr\\.?|doctor|doc)\\s+", "")
                .replaceFirst("(?i)\\s+(?:ka|ki|ke)$", "")
                .replaceFirst("\\s+(?:का|की|के)$", "")
                .trim();
        if (turn.daypart() != null && hasDateExpression(turn) && onlyDateAndDaypartTokens(cleaned, turn)) return null;
        return cleaned;
    }

    private boolean onlyDateAndDaypartTokens(String reference, NormalizedUserTurn turn) {
        java.util.Set<String> nonName = new java.util.HashSet<>(java.util.List.of(
                "dr", "doctor", "actually", "available", "availability", "slots", "slot", "ke", "ka", "ki",
                "today", "aaj", "आज", "tomorrow", "kal", "कल", "day", "after", "parson", "parason",
                "परसों", "परसो", "morning", "subah", "सुबह", "afternoon", "dopahar", "दोपहर",
                "evening", "shaam", "शाम", "रात", "night"));
        String temporal = turn.temporal().rawSpan();
        if (temporal != null) {
            nonName.add(temporal.toLowerCase(Locale.ROOT));
            switch (turn.temporal().relativeKind()) {
                case TODAY -> nonName.addAll(java.util.List.of("today", "aaj", "आज"));
                case TOMORROW -> nonName.addAll(java.util.List.of("tomorrow", "kal", "कल"));
                case DAY_AFTER_TOMORROW -> nonName.addAll(java.util.List.of("day", "after", "tomorrow", "parson", "parason", "परसों", "परसो"));
                case THIS_WEEKDAY, NEXT_WEEKDAY -> nonName.addAll(java.util.List.of(
                        "next", "this", "agale", "is", "monday", "tuesday", "wednesday", "thursday",
                        "friday", "saturday", "sunday", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार",
                        "शुक्रवार", "शनिवार", "रविवार"));
                default -> { }
            }
        }
        switch (turn.daypart()) {
            case MORNING -> nonName.addAll(java.util.List.of("morning", "subah", "सुबह"));
            case AFTERNOON -> nonName.addAll(java.util.List.of("afternoon", "dopahar", "दोपहर"));
            case EVENING -> nonName.addAll(java.util.List.of("evening", "shaam", "शाम"));
        }
        List<String> meaningful = java.util.Arrays.stream(reference.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split("\\s+"))
                .filter(StringUtils::hasText).filter(token -> !nonName.contains(token)).toList();
        return meaningful.isEmpty();
    }

    private String specialty(NormalizedUserTurn turn) {
        String raw = turn.specialtyEntity() == null ? turn.rawText() : turn.specialtyEntity().canonicalQuery();
        String canonical = specialtyResolver.canonicalize(raw);
        return StringUtils.hasText(canonical) && !canonical.equalsIgnoreCase(turn.rawText()) ? canonical : null;
    }

    private String resolvedDoctorId(SessionProjection session) {
        if (session.activeVoiceDoctor() != null) return session.activeVoiceDoctor().doctorId();
        return session.latestProviderResult() == null || session.latestProviderResult().resolvedProvider() == null
                ? null : session.latestProviderResult().resolvedProvider().doctorId();
    }

    private boolean isClosing(NormalizedUserTurn turn) {
        String raw = turn.rawText().trim().toLowerCase(Locale.ROOT);
        return raw.matches(".*(?:धन्यवाद|शुक्रिया|thanks?|thank you|bye|goodbye|अलविदा|बस इतना ही).*" );
    }

    private boolean isBlockedMutation(NormalizedUserTurn turn) {
        return isBookingRequest(turn)
                || turn.intent() == NormalizedUserTurn.Intent.CANCELLATION
                || turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE
                || turn.intent() == NormalizedUserTurn.Intent.LOOKUP
                && !isAppointmentAvailabilityRequest(turn);
    }

    private String intentName(NormalizedUserTurn turn) {
        if (isBookingRequest(turn)) return "BOOK_APPOINTMENT";
        return switch (turn.intent()) {
            case BOOKING -> "BOOK_APPOINTMENT";
            case CANCELLATION -> "CANCEL_APPOINTMENT";
            case RESCHEDULE -> "RESCHEDULE_APPOINTMENT";
            case LOOKUP -> "FIND_APPOINTMENTS";
            default -> "UNKNOWN";
        };
    }

    private boolean isBookingRequest(NormalizedUserTurn turn) {
        if (turn.intent() == NormalizedUserTurn.Intent.CANCELLATION
                || turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean appointment = text.matches(".*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*");
        return text.matches(".*(?:\\bbook(?:ing)?\\b|\\bappointment\\s+book\\b|बुक(?:िंग)?|अपॉइंटमेंट\\s+बुक).*" )
                || appointment && text.matches(".*(?:want|need|chahiye|चाहिए|लेना|लेनी|चाहता|चाहती|with|के\\s+साथ|उनका|उनकी|उनके|same\\s+doctor|उसी\\s+डॉक्टर).*" );
    }

    private record DoctorResolution(ProviderCandidate doctor, String source, List<ProviderCandidate> candidates) {
        private DoctorResolution {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        private String previousDoctorName() {
            return "PREVIOUS_SINGLE_RESULT".equals(source) && doctor != null ? doctor.displayName() : null;
        }

        private boolean referenceWasRequested() {
            return "NONE".equals(source);
        }
    }

    private AivaV2SessionStore.TurnExecution execution(SessionProjection session, TurnResult result) {
        MessageResponse response = messageResponse(session, result);
        return new AivaV2SessionStore.TurnExecution(session, response);
    }

    public record TurnResult(String response, String intent, String tool, boolean aivaUsed,
                             boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                             int toolResultCount, String toolResultSummary, boolean closing,
                             NormalizedUserTurn normalizedTurn, AivaV2VoiceToolExecutor.ToolResult toolResult) {
        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary, boolean closing,
                          NormalizedUserTurn normalizedTurn) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted, toolInputSpeciality,
                    toolResultCount, toolResultSummary, closing, normalizedTurn, null);
        }

        static TurnResult failure(String response) {
            return new TurnResult(response, "UNKNOWN", null, false, false, false,
                    null, 0, null, false, null, null);
        }

        public String policyDecision() {
            return mutationBlocked ? "BLOCKED_READ_ONLY" : "ALLOWED_READ_ONLY";
        }
    }
}
