package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraint;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityTimeConstraintMode;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.PendingProviderSwitch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSearchResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ResolutionStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.CompletedBookingOutcome;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceClarificationKind;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceClarificationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceProviderIdentityCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceUnresolvedReason;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceAuthenticationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.VoiceGuestRegistrationState;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DeterministicControl;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.DoctorNameNormalizer;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.ProviderOption;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiEntityRegistry;
import com.deepthoughtnet.clinic.api.patientportal.careai.SpecialtyResolver;
import com.deepthoughtnet.clinic.api.patientportal.booking.BookingExecutionContext;
import com.deepthoughtnet.clinic.api.patientportal.booking.VoiceBookingExecutionService;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * V2 Voice adapter. It maps read-only tools and the explicitly authorized
 * book-only flow to existing canonical application services.
 */
@Service
public class AivaV2VoiceConversationAdapter {
    private static final Logger log = LoggerFactory.getLogger(AivaV2VoiceConversationAdapter.class);
    private static final String BLOCKED_RESPONSE =
            "मैं अभी डॉक्टर और उपलब्ध समय बता सकती हूँ। अपॉइंटमेंट बुकिंग इस वॉइस चरण में अभी सक्षम नहीं है।";
    private static final String CLARIFICATION = "कृपया डॉक्टर, क्लिनिक, सेवा या उपलब्ध समय के बारे में बताइए।";
    private static final String FAILURE = "क्षमा कीजिए, अभी जानकारी प्राप्त नहीं हो पा रही है। कृपया दोबारा बताइए।";
    private static final String SLOT_PAGE_DIRECTION = "(?:more|next|aur|agla|agli|agle|dusre|doosre|और|दूसरे|दूसरी|अगला|अगली|अगले|नेक्स्ट)";
    private static final String SLOT_PAGE_NOUN = "(?:slots?|plots?|स्लॉट्स?|स्लॉट|प्लॉट्स?|प्लॉट)";
    private static final String SLOT_PAGE_ACTION = "(?:show|tell|list|batao|bataiye|dikhao|dikhaiye|बताओ|बताइए|दिखाओ|दिखाइए)";
    private static final Pattern SLOT_PAGE_CONTROL = Pattern.compile("(?iu)^\\s*(?:please\\s+|कृपया\\s+)?(?:"
            + SLOT_PAGE_ACTION + "(?:\\s+me)?\\s+" + SLOT_PAGE_DIRECTION + "(?:\\s+" + SLOT_PAGE_NOUN + ")?"
            + "|" + SLOT_PAGE_DIRECTION + "\\s+" + SLOT_PAGE_NOUN + "(?:\\s+" + SLOT_PAGE_ACTION + ")?"
            + ")(?:\\s+please)?\\s*[.!?।]*\\s*$");
    private final AivaV2SessionStore sessionStore;
    private final AivaV2ConversationDecisionGateway decisionGateway;
    private final LanguageAdapterRegistry languageAdapters;
    private final AivaTransactionCapabilityGate transactionCapabilityGate;
    private final AivaV2TransactionalExecutionBoundary transactionalBoundary;
    private final VoiceBookingExecutionService voiceBookingExecutionService;
    private final AivaV2VoiceResponseRenderer responseRenderer = new AivaV2VoiceResponseRenderer();
    private final AivaV2DisplayedSlotSelectionResolver displayedSlotSelectionResolver =
            new AivaV2DisplayedSlotSelectionResolver();
    private final SpecialtyResolver specialtyResolver = new SpecialtyResolver(new PatientPortalCareAiEntityRegistry());

    public VoiceAuthenticationState voiceAuthenticationState(AivaExecutionContext context) {
        if (context == null) return null;
        return sessionStore.find(sessionKey(context)).map(SessionProjection::voiceAuthenticationState).orElse(null);
    }

    public void updateVoiceAuthenticationState(AivaExecutionContext context, VoiceAuthenticationState state) {
        if (context == null) return;
        sessionStore.update(sessionKey(context), current -> {
            SessionProjection session = current == null
                    ? new SessionProjection(context.sessionId(), context.identifiedPatientId(), context.tenantId().toString(),
                    null, null, null, null, null, Instant.now().plusSeconds(30 * 60)) : current;
            return session.withVoiceAuthenticationState(state);
        });
    }

    public VoiceGuestRegistrationState voiceGuestRegistrationState(AivaExecutionContext context) {
        if (context == null) return null;
        return sessionStore.find(sessionKey(context)).map(SessionProjection::voiceGuestRegistrationState).orElse(null);
    }

    public void updateVoiceGuestRegistrationState(AivaExecutionContext context, VoiceGuestRegistrationState state) {
        if (context == null) return;
        sessionStore.update(sessionKey(context), current -> {
            SessionProjection session = current == null
                    ? new SessionProjection(context.sessionId(), context.identifiedPatientId(), context.tenantId().toString(),
                    null, null, null, null, null, Instant.now().plusSeconds(30 * 60)) : current;
            return session.withVoiceGuestRegistrationState(state);
        });
    }

    public AivaTransactionCapabilityGate.Decision authorizeGuestPreparation(
            AivaExecutionContext context, AivaV2Models.Operation operation) {
        return transactionCapabilityGate.authorizeGuestBookingPreparation(
                operation, voiceGuestRegistrationState(context), context);
    }

    public AivaTransactionAuthorization currentVoiceAuthorization(AivaExecutionContext context) {
        VoiceAuthenticationState state = voiceAuthenticationState(context);
        if (state == null || state.authorization() == null || state.authorization().expiresAt() == null
                || !state.authorization().expiresAt().isAfter(Instant.now())
                || !context.tenantId().equals(state.tenantId())
                || !java.util.Objects.equals(context.identifiedPatientId(), state.identifiedPatientId())
                || !context.providerCallId().equals(state.providerCallId())
                || !java.util.Objects.equals(context.interactionId(), state.interactionId())) {
            return AivaTransactionAuthorization.unauthenticated(context.tenantId(), context.providerCallId(),
                    context.identifiedPatientId());
        }
        return state.authorization();
    }

    private String sessionKey(AivaExecutionContext context) {
        return "VOICE|" + context.tenantId() + "|" + context.sessionId();
    }

    private PresentationTurn applyPresentationMode(SessionProjection session, NormalizedUserTurn turn) {
        if (session == null || turn == null) return new PresentationTurn(session, turn);
        AivaV2Models.VoicePresentationMode before = session.presentationMode();
        AivaV2Models.VoicePresentationMode requested = explicitPresentationSwitch(turn.rawText());
        AivaV2Models.VoicePresentationMode detected = before == null && requested == null
                && StringUtils.hasText(turn.rawText()) && !isClosing(turn)
                ? inferPresentationMode(turn) : null;
        AivaV2Models.VoicePresentationMode mode = requested;
        if (mode == null) mode = session.presentationMode();
        if (mode == null) mode = detected;
        SessionProjection updated = mode != null && mode != session.presentationMode()
                ? session.withPresentationMode(mode) : session;
        String reason = requested != null ? "EXPLICIT_LANGUAGE_REQUEST"
                : before == null && mode != null ? "INITIAL_UTTERANCE_DETECTION"
                : before != null && before == mode ? "STICKY_SESSION_MODE"
                : before != null && mode != before ? "DETECTED_LANGUAGE_CHANGE" : "NO_CHANGE";
        log.info("VOICE_PRESENTATION_MODE presentationModeBefore={} detectedUtteranceMode={} "
                        + "presentationModeAfter={} presentationModeChangeReason={}",
                before, detected, mode == null ? before : mode, reason);
        if (mode == null) return new PresentationTurn(updated, turn);
        boolean hindi = mode != AivaV2Models.VoicePresentationMode.ENGLISH;
        var style = mode == AivaV2Models.VoicePresentationMode.HINGLISH
                ? com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH
                : com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.STANDARD;
        String language = hindi ? "hi" : "en";
        NormalizedUserTurn rendered = new NormalizedUserTurn(turn.rawText(), turn.normalizedText(),
                turn.selectionText(), turn.languageCode(), turn.explicitDoctorReference(),
                turn.explicitLookupQualifierPresent(), turn.controls(), language, style,
                turn.canonicalSemanticText(), turn.doctorEntity(), turn.specialtyEntity(), turn.temporal(),
                turn.exactTime(), turn.daypart(), turn.ordinal(), turn.confirmation(), turn.pagination(), turn.intent());
        return new PresentationTurn(updated, rendered);
    }

    private AivaV2Models.VoicePresentationMode explicitPresentationSwitch(String text) {
        if (text == null) return null;
        String normalized = text.toLowerCase(Locale.ROOT).replace('’', '\'')
                .replaceAll("[,;.!?।]+", " ").replaceAll("\\s+", " ").trim();
        if (normalized.matches(".*(?:speak|talk|reply|respond)\\s+(?:to me\\s+)?in\\s+english.*")
                || normalized.matches(".*(?:please\\s+)?(?:speak|talk|reply|respond)\\s+english.*")
                || normalized.matches(".*\\benglish\\s+please\\b.*")
                || normalized.matches(".*\\benglish\\s+में\\s+(?:बात\\s+(?:करें|करो|कीजिए)|बोल(?:ें|िए|ो)).*"))
            return AivaV2Models.VoicePresentationMode.ENGLISH;
        if (normalized.matches(".*(?:english\\s+mein\\s+(?:bolo|boliye|baat)|अंग्रेज़ी\\s+में\\s+(?:बोल(?:ें|िए|ो)|बात\\s+(?:करें|करो|कीजिए))).*"))
            return AivaV2Models.VoicePresentationMode.ENGLISH;
        if (normalized.matches(".*(?:speak|talk|reply|respond)\\s+hindi.*")
                || normalized.matches(".*\\bhindi\\s+please\\b.*")
                || normalized.matches(".*hindi\\s+mein\\s+(?:bolo|boliye|baat\\s+(?:karo|karein)).*" )
                || normalized.matches(".*हिंदी\\s+में\\s+(?:बोल(?:ें|िए|ो)|बात\\s+(?:करें|करो|कीजिए)).*"))
            return AivaV2Models.VoicePresentationMode.HINDI;
        if (normalized.matches(".*(?:speak|talk|reply|respond)\\s+hinglish.*")
                || normalized.matches(".*\\bhinglish\\s+please\\b.*")
                || normalized.matches(".*hinglish\\s+में\\s+(?:बोलो|बोलिए|बात\\s+(?:करो|करें)).*" )
                || normalized.matches(".*hinglish\\s+mein\\s+(?:bolo|baat\\s+(?:karo|karein)).*"))
            return AivaV2Models.VoicePresentationMode.HINGLISH;
        return null;
    }

    private AivaV2Models.VoicePresentationMode inferPresentationMode(NormalizedUserTurn turn) {
        String rawText = turn.rawText();
        if (rawText.matches(".*[\\u0900-\\u097F].*")) {
            boolean mixedIndianSpeech = rawText.matches("(?iu).*(?:\\b(?:kal|parso|aaj|aur|batao|dikhao|karo|doctor|pediatric|slot|slots|morning|evening|subah|shaam|baje|appointment|ka naam)\\b).*" );
            return mixedIndianSpeech ? AivaV2Models.VoicePresentationMode.HINGLISH
                    : AivaV2Models.VoicePresentationMode.HINDI;
        }
        boolean hasLetter = false;
        for (int offset = 0; offset < rawText.length();) {
            int codePoint = rawText.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetter(codePoint)) continue;
            hasLetter = true;
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script != Character.UnicodeScript.LATIN) return null;
        }
        if (!hasLetter) return null;
        if (rawText.matches("(?iu).*(?:^|[^\\p{L}\\p{N}])(?:kal|parso|aaj|aur|wala|waala|wali|wale|batao|dikhao|karo|ka|ke|ki|hain|hai|mein|subah|shaam|saat|tees)(?:$|[^\\p{L}\\p{N}]).*"))
            return AivaV2Models.VoicePresentationMode.HINGLISH;
        return AivaV2Models.VoicePresentationMode.ENGLISH;
    }

    private record PresentationTurn(SessionProjection session, NormalizedUserTurn turn) { }

    public AivaV2VoiceConversationAdapter(
            AivaV2SessionStore sessionStore,
            AivaV2ConversationDecisionGateway decisionGateway,
            LanguageAdapterRegistry languageAdapters
    ) {
        this(sessionStore, decisionGateway, languageAdapters, new AivaTransactionCapabilityGate(), null);
    }

    public AivaV2VoiceConversationAdapter(
            AivaV2SessionStore sessionStore,
            AivaV2ConversationDecisionGateway decisionGateway,
            LanguageAdapterRegistry languageAdapters,
            AivaTransactionCapabilityGate transactionCapabilityGate
    ) {
        this(sessionStore, decisionGateway, languageAdapters, transactionCapabilityGate, null);
    }

    public AivaV2VoiceConversationAdapter(
            AivaV2SessionStore sessionStore,
            AivaV2ConversationDecisionGateway decisionGateway,
            LanguageAdapterRegistry languageAdapters,
            AivaTransactionCapabilityGate transactionCapabilityGate,
            AivaV2TransactionalExecutionBoundary transactionalBoundary
    ) {
        this(sessionStore, decisionGateway, languageAdapters, transactionCapabilityGate, transactionalBoundary, null);
    }

    @Autowired
    public AivaV2VoiceConversationAdapter(AivaV2SessionStore sessionStore,
            AivaV2ConversationDecisionGateway decisionGateway, LanguageAdapterRegistry languageAdapters,
            AivaTransactionCapabilityGate transactionCapabilityGate,
            AivaV2TransactionalExecutionBoundary transactionalBoundary,
            VoiceBookingExecutionService voiceBookingExecutionService) {
        this.sessionStore = sessionStore;
        this.decisionGateway = decisionGateway;
        this.languageAdapters = languageAdapters;
        this.transactionCapabilityGate = transactionCapabilityGate;
        this.transactionalBoundary = transactionalBoundary;
        this.voiceBookingExecutionService = voiceBookingExecutionService;
    }

    public boolean hasVoiceBookingWorkflow(AivaExecutionContext context) {
        return context != null && sessionStore.find(sessionKey(context))
                .map(session -> session.activeDraft() != null || session.pendingConfirmation() != null
                        || session.voiceClarificationState() != null)
                .orElse(false);
    }

    public AivaV2Models.VoicePresentationMode presentationMode(AivaExecutionContext context) {
        if (context != null) {
            AivaV2Models.VoicePresentationMode stored = sessionStore.find(sessionKey(context))
                    .map(SessionProjection::presentationMode).orElse(null);
            if (stored != null) return stored;
        }
        return context != null && context.language() != null
                && context.language().toLowerCase(Locale.ROOT).startsWith("en")
                ? AivaV2Models.VoicePresentationMode.ENGLISH
                : AivaV2Models.VoicePresentationMode.HINDI;
    }

    /** Single entry-routing decision for the authorized Voice booking path. */
    public boolean shouldRouteVoiceBookingTurn(AivaExecutionContext context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) return false;
        SessionProjection session = sessionStore.find(sessionKey(context)).orElse(null);
        if (session == null || session.closed()) return false;
        NormalizedUserTurn turn = normalizeVoiceTurn(context, session, transcript);
        // Courtesy controls take precedence over every active booking continuation.
        if (isClosing(turn)) return false;
        if (isPreparedBookingCancellation(session, turn)) return true;
        // Explicit discovery and context inspection stay on the read-only Voice path,
        // even while a transactional draft or clarification is active.
        if ((isExplicitReadOnlyDiscovery(turn) && !isExplicitProviderSwitchRequest(session, turn))
                || isProviderContextInspection(turn) || isDateContextInspection(turn)
                || isSelectedSlotInspection(turn)) return false;
        if (hasRetainedReadOnlySlotPage(session) && parsedTimeRange(turn) != null) return false;
        if (isRestartBookingRequest(turn)) return session.activeDraft() != null
                || session.pendingConfirmation() != null || session.voiceClarificationState() != null;
        if (isRepeatRequest(turn)) return session.activeDraft() != null
                || session.pendingConfirmation() != null || session.voiceClarificationState() != null;
        if (isContextChangeCommand(turn)) return session.activeDraft() != null
                || session.pendingConfirmation() != null || session.voiceClarificationState() != null
                || hasRetainedReadOnlySlotPage(session);
        if (hasRetainedReadOnlySlotPage(session) && !isShowMore(turn) && parsedTimeRange(turn) == null
                && retainedSlotContextMatches(session.latestAvailabilityResult(), turn)
                && !hasConflictingProviderEvidence(session, turn)) {
            AivaV2DisplayedSlotSelectionResolver.Resolution selection =
                    displayedSlotSelectionResolver.resolve(turn, session.latestAvailabilityResult());
            if (selectedDisplayedSlot(session.latestAvailabilityResult(), selection.selection()) != null) return true;
        }
        if (turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                && session.activeDraft() == null
                && !(isBookingRequest(turn) && (session.activeVoiceDoctor() != null
                || turn.doctorEntity() != null))) return false;
        if (session.voiceClarificationState() != null
                && isConsultationClarification(session.voiceClarificationState())) return false;
        AvailabilityResult retainedAvailability = session.latestAvailabilityResult();
        if (retainedAvailability != null && "VOICE_PENDING_DAYPART".equals(retainedAvailability.cursor())
                && !hasExplicitBookingVerb(turn)) {
            // This is a read-only FIND_SLOTS continuation. A weak booking label
            // from STT must not move it into the transactional adapter path.
            return false;
        }
        boolean activeWorkflow = session.activeDraft() != null || session.pendingConfirmation() != null
                || session.voiceClarificationState() != null;
        if (!activeWorkflow) {
            return (session.activeVoiceDoctor() != null || turn.doctorEntity() != null
                    || StringUtils.hasText(turn.explicitDoctorReference())) && isBookingRequest(turn);
        }
        return isRelevantNormalizedVoiceContinuation(session, transcript, turn);
    }

    /** Returns whether a transcript is a deterministic courtesy close in this Voice session. */
    public boolean isCourtesyClose(AivaExecutionContext context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) return false;
        SessionProjection session = sessionStore.find(sessionKey(context)).orElse(null);
        return isClosing(normalizeVoiceTurn(context, session, transcript));
    }

    /** Rejects protected Voice actions at the channel boundary before any business tool can run. */
    public VoiceScopeBoundaryResult unsupportedVoiceScopeRequest(AivaExecutionContext context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) return null;
        SessionProjection session = sessionStore.find(sessionKey(context)).orElse(null);
        NormalizedUserTurn turn = normalizeVoiceTurn(context, session, transcript);
        if (session != null && isPreparedBookingCancellation(session, turn)) return null;
        if (isProviderContextInspection(turn) || isDateContextInspection(turn)
                || isSelectedSlotInspection(turn) || isRepeatRequest(turn)
                || session != null && session.activeDraft() != null
                && (isContextChangeCommand(turn) || isRestartBookingRequest(turn)
                || isDeclineCurrentSlotOptions(session, turn))) return null;
        String category = switch (turn.intent()) {
            case RESCHEDULE -> "RESCHEDULE_NOT_SUPPORTED";
            case CANCELLATION -> "CANCEL_NOT_SUPPORTED";
            default -> null;
        };
        if (category == null && ((turn.intent() == NormalizedUserTurn.Intent.LOOKUP
                && !isDatedAppointmentAvailability(turn) && !hasExplicitBookingVerb(turn))
                || isPatientOwnedAppointmentLookup(turn) || requestsSensitivePatientData(turn))) {
            category = "PATIENT_DATA_NOT_SUPPORTED";
        }
        if (category == null) return null;
        String response = unsupportedScopeResponse(turn, category);
        return new VoiceScopeBoundaryResult(category, response);
    }

    private boolean requestsSensitivePatientData(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\breports?\\b|\\bprescriptions?\\b|\\blab(?:oratory)?\\s+(?:results?|reports?|tests?)\\b|"
                + "\\bmedical\\s+records?\\b|\\bclinical\\s+history\\b|\\bmedical\\s+history\\b|"
                + "\\bprevious\\s+consultations?\\b|\\bconsultation\\s+history\\b|\\bcare\\s+timeline\\b|"
                + "रिपोर्ट|प्रिस्क्रिप्शन|पर्ची|लैब\\s*(?:रिजल्ट|रिपोर्ट|टेस्ट)|जांच\\s*(?:रिपोर्ट|नतीजे)|"
                + "मेडिकल\\s*(?:हिस्ट्री|इतिहास|रिकॉर्ड)|क्लिनिकल\\s*(?:हिस्ट्री|इतिहास)|"
                + "पुरानी\\s*(?:कंसल्टेशन|consultations?|परामर्श|रिपोर्ट)|meri\\s+(?:reports?|prescription|history)|"
                + "purani\\s+consultation|हेल्थ\\s*रिकॉर्ड|केयर\\s*टाइमलाइन).*" );
    }

    private String unsupportedScopeResponse(NormalizedUserTurn turn, String category) {
        boolean english = isEnglish(turn);
        boolean hinglish = isHinglish(turn);
        if ("PATIENT_DATA_NOT_SUPPORTED".equals(category)) {
            return english ? "Please use Care to check existing appointments or health information."
                    : hinglish ? "Existing appointment ya health information ke liye please Care use kijiye."
                    : "पुरानी appointment या स्वास्थ्य जानकारी के लिए कृपया Care का उपयोग करें।";
        }
        if (english) return "Appointment rescheduling and cancellation are not available through AIVA Voice right now. Please use Care.";
        if (hinglish) return "Reschedule aur cancellation abhi AIVA Voice par available nahi hai. Please Care use kijiye.";
        return "AIVA Voice पर अपॉइंटमेंट बदलना या रद्द करना अभी उपलब्ध नहीं है। कृपया Care का उपयोग करें।";
    }

    private String localizedNewBookingOnlyResponse(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "I can help with new appointments, doctor information, and availability."
                : isHinglish(turn) ? "Main new appointment, doctor information, aur availability mein help kar sakti hoon."
                : "मैं नई अपॉइंटमेंट, डॉक्टर की जानकारी और उपलब्ध समय में मदद कर सकती हूँ।";
    }

    public record VoiceScopeBoundaryResult(String category, String response) { }

    /** Handles deterministic repeats/status/confirmation after success without reopening mutation. */
    public GuestPreparationResult completedBookingFollowUp(AivaExecutionContext context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript)) return null;
        SessionProjection session = sessionStore.find(sessionKey(context)).orElse(null);
        CompletedBookingOutcome completed = session == null ? null : session.completedBookingOutcome();
        if (session == null || session.closed() || completed == null || session.activeDraft() != null
                || session.pendingConfirmation() != null
                || !java.util.Objects.equals(session.tenantId(), context.tenantId().toString())
                || !java.util.Objects.equals(completed.tenantId(), context.tenantId())
                || !java.util.Objects.equals(completed.patientId(), session.patientId())
                || !java.util.Objects.equals(completed.providerCallId(), context.providerCallId())
                || context.identifiedPatientId() != null
                && !context.identifiedPatientId().equals(completed.patientId())) return null;
        NormalizedUserTurn turn = normalizeVoiceTurn(context, session, transcript);
        PresentationTurn presentation = applyPresentationMode(session, turn);
        turn = presentation.turn();
        AivaV2Models.VoicePresentationMode mode = presentation.session().presentationMode();
        if (mode != null && mode != session.presentationMode()) {
            sessionStore.update(sessionKey(context), current -> current == null ? null : current.withPresentationMode(mode));
        }
        if (isExplicitAdditionalBookingRequest(turn)) return null;
        if (!isCompletedBookingFollowUp(turn)) return null;
        log.info("VOICE_CONVERSATION_DECISION context=COMPLETED_BOOKING action=REPEAT source=DETERMINISTIC_CONTEXT "
                        + "operation=COMPLETED_BOOKING_REPEAT aiInvoked=false result=COMPLETED_BOOKING_REPEAT");
        AivaV2VoiceResponseRenderer.Intent intent = new AivaV2VoiceResponseRenderer.Intent(
                AivaV2VoiceResponseRenderer.Type.COMPLETED_BOOKING_REPEAT,
                mode, completed.providerDisplayName(), null, List.of(),
                completed.date(), completed.time(), List.of(), List.of(), completed.bookingReference(), null);
        return new GuestPreparationResult(responseRenderer.render(intent), "COMPLETED_BOOKING_REPEAT",
                Map.of("mutationAllowed", false, "executionDecision", "ALREADY_CONFIRMED",
                        "responseIntent", intent.type().name(), "presentationMode", intent.presentationMode().name(),
                        "ttsLanguage", ttsLanguage(intent.presentationMode()),
                        "ttsTextLanguageProfile", intent.presentationMode().name(),
                        "appointmentId", completed.appointmentId().toString(), "source", "COMPLETED_VOICE_OUTCOME"));
    }

    private boolean isExplicitAdditionalBookingRequest(NormalizedUserTurn turn) {
        String text = turn.rawText().toLowerCase(Locale.ROOT);
        boolean additional = text.matches("(?s).*(?:\\bek aur\\b|\\banother\\b|\\bone more\\b|एक और|एक अतिरिक्त).*" );
        return additional && isBookingRequest(turn);
    }

    private boolean isCompletedBookingFollowUp(NormalizedUserTurn turn) {
        if (turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE)
                || AivaV2VoiceConfirmationContext.explicitAnswer(turn)
                == NormalizedUserTurn.Confirmation.POSITIVE) return true;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:booking|appointment).*(?:book(?:ed)?|ho\\s+gayi|hui|hua|status|details).*" )
                || text.matches("(?s).*(?:book(?:ing)?\\s+(?:hua|hui|ho\\s+gayi)|appointment\\s+(?:booked|confirmed)|"
                + "बुकिंग\\s+(?:हो\\s+गई|हुई)|अपॉइंटमेंट\\s+(?:बुक\\s+हुई|बुक\\s+हो\\s+गया)).*");
    }

    /** Separates known global/read-only turns before the contextual decision; does not choose a booking operation. */
    private boolean isRelevantNormalizedVoiceContinuation(SessionProjection session, String transcript,
            NormalizedUserTurn turn) {
        if ((isExplicitReadOnlyDiscovery(turn) && !isExplicitProviderSwitchRequest(session, turn))
                || isProviderContextInspection(turn)) return false;
        if (session.voiceClarificationState() != null || session.pendingConfirmation() != null) return true;
        if (isClosing(turn) || isClearlyUnrelatedRequest(transcript)
                || isContextualSlotDoctorQuestion(turn) || isRecurringScheduleQuestion(turn)) return false;

        // An active draft is the authoritative workflow boundary. Do not require a
        // second, weaker lexical classifier to recognize its next collection answer.
        // That classifier is useful only before a draft exists, when we must decide
        // whether an utterance belongs to booking or to read-only discovery.
        if (session.activeDraft() != null) return true;

        boolean currentTurnEvidence = turn.intent() == NormalizedUserTurn.Intent.BOOKING
                || turn.temporal().localDate() != null || hasDateExpression(turn)
                || turn.exactTime() != null || turn.daypart() != null || turn.doctorEntity() != null
                || StringUtils.hasText(turn.explicitDoctorReference()) || turn.specialtyEntity() != null
                || turn.ordinal() != null || turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE
                || turn.confirmation() != NormalizedUserTurn.Confirmation.NONE || isShowMore(turn)
                || turn.controls().contains(DeterministicControl.HELP)
                || turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE)
                || turn.controls().contains(DeterministicControl.ABANDON_WORKFLOW)
                || AivaV2VoiceSlotContext.explicitlySearchesNextDate(transcript)
                || hasExplicitSlotEvidence(turn);
        if (!currentTurnEvidence) return false;

        // Once a logical collection or displayed-slot context owns the conversation, ambiguous replies
        // stay in that context. Do not run the generic read-only tool selector as a second router.
        if (AivaV2VoiceCollectionContext.state(session, Instant.now())
                != AivaV2VoiceCollectionContext.State.NONE || hasBookingSlotPage(session)) return true;
        return currentTurnEvidence;
    }

    private boolean isClearlyUnrelatedRequest(String text) {
        if (!StringUtils.hasText(text)) return false;
        String normalized = text.strip().toLowerCase(Locale.ROOT).replaceAll("[?!।]+$", "").trim();
        return (normalized.contains("আপনি") && normalized.contains("কেমন আছেন"))
                || (normalized.contains("आप") && normalized.contains("हैं"))
                || (normalized.contains("कैसे") && (normalized.contains("आप") || normalized.contains("तुम")))
                || normalized.contains("क्या हाल") || normalized.matches(".*\\bhow are you(?: doing)?\\b.*")
                || normalized.matches(".*\\binsurance\\s+claim\\b.*");
    }

    private boolean isExplicitReadOnlyDiscovery(NormalizedUserTurn turn) {
        if (turn == null) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        if (hasDateExpression(turn) && isAppointmentAvailabilityRequest(turn)
                && !isPatientOwnedAppointmentLookup(turn)) return true;
        if (hasDateExpression(turn) && hasAvailabilityLanguage(turn)
                && isGenericDoctorAvailabilityRequest(turn, specialty(turn))) return true;
        return text.matches("(?s).*(?:\\bshow\\s+(?:me\\s+)?doctors?\\b|\\blist\\s+(?:the\\s+)?doctors?\\b|"
                + "\\bwhich\\s+doctors?\\s+(?:are\\s+)?available\\b|\\bdoctors?\\s+available\\b|"
                + "कौन-कौन\\s+से\\s+डॉक्टर|कौन\\s+से\\s+डॉक्टर(?:\\s+उपलब्ध)?|"
                + "डॉक्टर\\s+दिखाइए|डॉक्टरों\\s+की\\s+सूची).*" );
    }

    private boolean isProviderContextInspection(NormalizedUserTurn turn) {
        if (turn == null) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bwhich\\s+doctor\\s+is\\s+this\\s+for\\b|"
                + "\\bwhich\\s+doctor\\s+(?:did\\s+i\\s+)?select(?:ed)?\\b|"
                + "\\bwhat\\s+doctor\\s+is\\s+selected\\b|\\bwhich\\s+doctor\\s+did\\s+i\\s+choose\\b|"
                + "कौन\\s+सा\\s+डॉक्टर\\s+selected|किस\\s+डॉक्टर\\s+का\\s+(?:slot|स्लॉट)|"
                + "ये\\s+किस\\s+डॉक्टर\\s+का\\s+appointment|ये\\s+किस\\s+डॉक्टर\\s+के\\s+(?:slots?|स्लाइस|स्लॉट्स?)"
                + "(?:\\s+हैं|\\s+है)?|किस\\s+doctor\\s+के\\s+slots|"
                + "कौन\\s+सा\\s+time\\s+select\\s+किया|"
                + "कौन\\s+से\\s+डॉक्टर\\s+चुने|कौन\\s+सा\\s+डॉक्टर\\s+चुना).*" );
    }

    private boolean isDateContextInspection(NormalizedUserTurn turn) {
        if (turn == null || !StringUtils.hasText(turn.rawText())) return false;
        String text = turn.rawText().toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bwhich\\s+date\\b|\\bwhat\\s+date\\b|कौन\\s+सी\\s+तारीख|किस\\s+तारीख).*" );
    }

    private boolean isSelectedSlotInspection(NormalizedUserTurn turn) {
        if (turn == null || !StringUtils.hasText(turn.rawText())) return false;
        String text = turn.rawText().toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bwhich\\s+time\\s+(?:did\\s+i\\s+)?select(?:ed)?\\b|"
                + "\\bwhat\\s+time\\s+is\\s+selected\\b|कौन\\s+सा\\s+time\\s+select\\s+किया|"
                + "कौन\\s+सा\\s+समय\\s+चुना).*" );
    }

    private boolean isRepeatRequest(NormalizedUserTurn turn) {
        if (turn == null) return false;
        if (turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE)) return true;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replaceAll("[,;.!?।]+", " ").replaceAll("\\s+", " ").trim();
        return text.matches("(?iu).*(?:repeat(?:\\s+(?:that|the\\s+slots?|those\\s+times?|the\\s+page))?|"
                + "say\\s+(?:those\\s+times|that)\\s+again|एक\\s+बार\\s+और\\s+बताइए|"
                + "स्लॉट\\s+दोबारा\\s+बताइए|फिर\\s+से\\s+स्लॉट\\s+बताइए|समय\\s+फिर\\s+से\\s+बताइए|"
                + "जो\\s+slots?\\s+बताए\\s+थे\\s+repeat\\s+करें|फिर\\s+से\\s+बताइए).*" );
    }

    private boolean isPreparedBookingCancellation(SessionProjection session, NormalizedUserTurn turn) {
        if (session == null || turn == null || session.activeDraft() == null
                && session.pendingConfirmation() == null) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replaceAll("[,;.!?।]+", " ").replaceAll("\\s+", " ").trim();
        boolean existingAppointment = text.matches("(?s).*(?:\\bmy\\s+(?:existing\\s+)?appointment\\b|"
                + "\\bappointment\\s+status\\b|मेरी\\s+(?:पुरानी\\s+)?अपॉइंटमेंट|मेरी\\s+booking).*" );
        if (existingAppointment) return false;
        return text.matches("(?s).*(?:appointment\\s+(?:नहीं|nahi)\\s+चाहिए|booking\\s+(?:नहीं|nahi)\\s+करनी|"
                + "रहने\\s+दीजिए|rehne\\s+dijiye|cancel\\s+(?:this\\s+)?booking|"
                + "(?:नहीं|nahi)\\s+बुक\\s+करना|"
                + "do\\s+not\\s+book\\s+(?:this\\s+)?appointment|don't\\s+book\\s+(?:this\\s+)?appointment).*" );
    }

    private boolean isDeclineCurrentSlotOptions(SessionProjection session, NormalizedUserTurn turn) {
        if (session == null || turn == null || !hasDisplayedSlotPage(session.latestAvailabilityResult())) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:नहीं\\s+चुनना(?:\\s+चाहता|\\s+चाहती)?|इनमें\\s+से\\s+(?:कोई\\s+नहीं|नहीं)|\\bnot\\s+these\\b|"
                + "don't\\s+want\\s+to\\s+choose|do\\s+not\\s+want\\s+to\\s+choose).*" );
    }

    private boolean hasDisplayedSlotPage(AvailabilityResult availability) {
        return availability != null && availability.slots() != null
                && availability.slots().size() > availability.displayOffset()
                && availability.pageSize() > 0
                && (availability.expiresAt() == null || availability.expiresAt().isAfter(Instant.now()));
    }

    private String repeatTarget(SessionProjection session) {
        if (session == null) return "NONE";
        if (session.pendingConfirmation() != null) return "FINAL_CONFIRMATION";
        VoiceClarificationState pending = session.voiceClarificationState();
        if (pending != null && pending.pendingProviderSwitch() != null) return "PROVIDER_SWITCH";
        if (pending != null && ("CHANGE_PROVIDER".equals(pending.pendingAction())
                || "PROVIDER_IDENTITY".equals(pending.pendingAction())
                || "CONSULTATION_PROVIDER".equals(pending.pendingAction())
                || "CONSULTATION_SPECIALTY".equals(pending.pendingAction()))) return "PROVIDER";
        if (pending != null && "CHANGE_DATE".equals(pending.pendingAction())) return "DATE";
        if (pending != null && "CHANGE_DAYPART".equals(pending.pendingAction())) return "DAYPART";
        if (pending != null && pending.pendingAction() != null
                && !"SHOW_MORE".equals(pending.pendingAction())) return pending.pendingAction();
        if (hasDisplayedSlotPage(session.latestAvailabilityResult())
                && (AivaV2VoiceSlotContext.isAwaitSlot(session, Instant.now())
                || hasRetainedReadOnlySlotPage(session))) return "SLOT_PAGE";
        AivaV2VoiceCollectionContext.State state = AivaV2VoiceCollectionContext.state(session, Instant.now());
        return switch (state) {
            case AWAIT_DATE -> "DATE";
            case AWAIT_DAYPART -> "DAYPART";
            case AWAIT_PROVIDER -> "PROVIDER";
            case NONE -> pending == null ? "LAST_RESPONSE" : pending.pendingAction() == null
                    ? "CLARIFICATION" : pending.pendingAction();
        };
    }

    private String repeatCurrentPrompt(SessionProjection session, NormalizedUserTurn turn) {
        if (session == null) return null;
        String target = repeatTarget(session);
        if ("SLOT_PAGE".equals(target) && hasDisplayedSlotPage(session.latestAvailabilityResult())) {
            return retainedSlotPageResponse(session.latestAvailabilityResult(), turn);
        }
        if ("FINAL_CONFIRMATION".equals(target) && session.activeDraft() != null) {
            return bookingConfirmationPrompt(session.activeDraft(), turn);
        }
        VoiceClarificationState pending = session.voiceClarificationState();
        if (pending != null && pending.pendingProviderSwitch() != null) {
            PendingProviderSwitch change = pending.pendingProviderSwitch();
            ProviderCandidate active = session.activeDraft() == null ? session.activeVoiceDoctor()
                    : session.activeDraft().selectedProvider();
            return active == null ? changeProviderPrompt(turn)
                    : providerSwitchWarning(active, change.targetDisplayName(), turn);
        }
        if (pending != null && "CHANGE_PROVIDER".equals(pending.pendingAction())) return changeProviderPrompt(turn);
        if (pending != null && "CHANGE_DATE".equals(pending.pendingAction())) return changeDatePrompt(turn);
        if (pending != null && "CHANGE_DAYPART".equals(pending.pendingAction())) return changeDaypartPrompt(turn);
        AivaV2VoiceCollectionContext.State state = AivaV2VoiceCollectionContext.state(session, Instant.now());
        if (state != AivaV2VoiceCollectionContext.State.NONE) return collectionPrompt(state, turn, session);
        return null;
    }

    private String contextInspectionResponse(SessionProjection session, NormalizedUserTurn turn) {
        ProviderCandidate active = session == null ? null : session.activeDraft() != null
                && session.activeDraft().selectedProvider() != null
                ? session.activeDraft().selectedProvider() : session.activeVoiceDoctor();
        if (isProviderContextInspection(turn)) {
            if (session != null && hasDisplayedSlotPage(session.latestAvailabilityResult())) {
                ProviderCandidate pageProvider = providerForAvailability(session.latestAvailabilityResult(), session);
                if (pageProvider != null) active = pageProvider;
            }
            return active == null ? missingDoctorClarification(turn)
                    : doctorOwnsSlotsResponse(active.displayName(), turn);
        }
        AivaV2Models.BookingDraft draft = session == null ? null : session.activeDraft();
        AvailabilityResult availability = session == null ? null : session.latestAvailabilityResult();
        if (isDateContextInspection(turn)) {
            LocalDate date = hasDisplayedSlotPage(availability) ? availability.date()
                    : draft != null && draft.preferredDate() != null ? draft.preferredDate()
                    : availability == null ? null : availability.date();
            if (date == null) return isEnglish(turn) ? "No date has been selected yet."
                    : isHinglish(turn) ? "Abhi koi date select nahi hui hai."
                    : "अभी कोई तारीख नहीं चुनी गई है।";
            return isEnglish(turn) ? "The selected date is " + AivaSpokenDateFormatter.format(date,
                    turn.responseLanguage(), turn.responseStyle()) + "."
                    : isHinglish(turn) ? "Selected date " + AivaSpokenDateFormatter.format(date,
                    turn.responseLanguage(), turn.responseStyle()) + " hai."
                    : "चुनी हुई तारीख " + AivaSpokenDateFormatter.format(date,
                    turn.responseLanguage(), turn.responseStyle()) + " है।";
        }
        LocalTime selected = draft == null ? null : draft.exactTime();
        if (selected == null && draft != null && availability != null
                && StringUtils.hasText(draft.selectedSlotReference())) {
            selected = availability.slots().stream().filter(slot -> slot.slotReference().equals(draft.selectedSlotReference()))
                    .map(AvailabilitySlot::startsAt).findFirst().orElse(null);
        }
        if (selected == null) return isEnglish(turn) ? "No appointment time has been selected yet."
                : isHinglish(turn) ? "Abhi koi appointment time select nahi hua hai."
                : "अभी कोई appointment समय नहीं चुना गया है।";
        String time = spokenTime(selected, !isEnglish(turn), isHinglish(turn));
        return isEnglish(turn) ? "The selected time is " + time + "."
                : isHinglish(turn) ? "Selected time " + time + " hai."
                : "चुना हुआ समय " + time + " है।";
    }

    private ProviderCandidate providerForAvailability(AvailabilityResult availability, SessionProjection session) {
        if (availability == null || session == null) return null;
        ProviderCandidate active = session.activeDraft() != null && session.activeDraft().selectedProvider() != null
                ? session.activeDraft().selectedProvider() : session.activeVoiceDoctor();
        if (active != null && availability.doctorId() != null
                && availability.doctorId().equals(active.doctorId())) return active;
        ProviderSearchResult providers = session.latestProviderResult();
        if (providers == null) return null;
        if (providers.resolvedProvider() != null && availability.doctorId() != null
                && availability.doctorId().equals(providers.resolvedProvider().doctorId())) {
            return providers.resolvedProvider();
        }
        return providers.candidates().stream().filter(candidate -> availability.doctorId() != null
                && availability.doctorId().equals(candidate.doctorId())).findFirst().orElse(null);
    }

    private boolean isExplicitProviderSwitchRequest(SessionProjection session, NormalizedUserTurn turn) {
        if (session == null || turn == null) return false;
        String requestedReference = StringUtils.hasText(turn.explicitDoctorReference())
                ? turn.explicitDoctorReference()
                : turn.doctorEntity() == null ? null : turn.doctorEntity().canonicalQuery();
        if (!StringUtils.hasText(requestedReference)) return false;
        ProviderCandidate active = session.activeDraft() != null && session.activeDraft().selectedProvider() != null
                ? session.activeDraft().selectedProvider() : session.activeVoiceDoctor();
        if (active == null || !StringUtils.hasText(active.displayName())) return false;
        String requested = normalizeProviderName(requestedReference);
        String current = normalizeProviderName(active.displayName());
        return StringUtils.hasText(requested) && !requested.equals(current);
    }

    private boolean looksLikeProviderChange(NormalizedUserTurn turn) {
        String text = turn == null ? "" : (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\b(?:doctor|appointment|consult|specialty)\\b|डॉक्टर|अपॉइंटमेंट|"
                + "बच्चे|बच्चों|बाल रोग|का appointment|के slots).*" );
    }

    private boolean sameCanonicalLabel(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.replaceAll("[^\\p{L}\\p{N}]", "").equalsIgnoreCase(
                right.replaceAll("[^\\p{L}\\p{N}]", ""));
    }

    private String normalizeProviderName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^dr\\.?\\s*", "").replaceAll("[^\\p{L}\\p{N}]", "").trim();
    }

    private boolean isContextChangeCommand(NormalizedUserTurn turn) {
        if (turn == null) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return isChangeProviderCommand(turn) || text.matches("(?s).*(?:\\bchange\\s+(?:the\\s+)?doctor\\b|\\bswitch\\s+doctor\\b|"
                + "\\bdoctor\\s+change\\s+(?:karo|करो|please)\\b|\\bi\\s+want\\s+to\\s+change\\s+doctor\\b|"
                + "दूसरे\\s+डॉक्टर\\s+के\\s+slots\\s+दिखाओ|मुझे\\s+डॉक्टर\\s+बदलना\\s+है|"
                + "\\bchange\\s+(?:the\\s+)?date\\b|\\bchange\\s+(?:the\\s+)?time\\b|"
                + "doctor\\s+(?:बदलना\\s+है|badalna\\s+hai)|डॉक्टर\\s+बदलना\\s+है|तारीख\\s+बदलो|तारीख\\s+बदलनी\\s+है|"
                + "समय\\s+बदलो|समय\\s+बदलना\\s+है|daypart\\s+बदलना\\s+है).*" );
    }

    private boolean isChangeProviderCommand(NormalizedUserTurn turn) {
        if (turn == null) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean explicit = text.matches("(?s).*(?:\\bchange\\s+(?:the\\s+)?doctor\\b|\\bswitch\\s+doctor\\b|"
                + "\\bdoctor\\s+change\\s+(?:karo|करो|please)\\b|\\bi\\s+want\\s+to\\s+change\\s+doctor\\b|"
                + "दूसरे\\s+डॉक्टर\\s+के\\s+slots\\s+दिखाओ|मुझे\\s+डॉक्टर\\s+बदलना\\s+है|"
                + "(?:doctor|डॉक्टर)\\s+(?:बदलना\\s+है|badalna\\s+hai)|डॉक्टर\\s+बदल).*" );
        return explicit || text.matches("(?s).*(?:doctor|डॉक्टर).{0,24}(?:change|badal|बदल).*" );
    }

    private boolean isProviderChangeRequest(NormalizedUserTurn turn) {
        if (isChangeProviderCommand(turn)) return true;
        if (turn == null) return false;
        String raw = turn.rawText() == null ? "" : turn.rawText().toLowerCase(Locale.ROOT);
        return (raw.contains("doctor") || raw.contains("डॉक्टर"))
                && (raw.contains("बदल") || raw.contains("badal") || raw.contains("change"));
    }

    private boolean isGenericOrCurrentProviderEvidence(String evidence, ProviderCandidate activeProvider) {
        if (!StringUtils.hasText(evidence) || evidence.matches("(?iu)\\s*(?:doctor|डॉक्टर)(?:\\s+(?:बदलना(?:\\s+है)?|badalna(?:\\s+hai)?|change(?:\\s+doctor)?))?\\s*")) return true;
        return activeProvider != null && DoctorNameNormalizer.matches(activeProvider.displayName(), evidence);
    }

    private boolean isProviderChangeWithoutTarget(NormalizedUserTurn turn) {
        return turn != null && isRawProviderChangeWithoutTarget(turn.rawText());
    }

    private boolean isRawProviderChangeWithoutTarget(String transcript) {
        if (!StringUtils.hasText(transcript)) return false;
        String raw = transcript.toLowerCase(Locale.ROOT);
        return raw.matches("(?s).*\\bchange\\s+(?:the\\s+)?doctor\\s*[.!?।]*")
                || raw.matches("(?s).*\\bswitch\\s+doctor\\s*[.!?।]*")
                || raw.matches("(?s).*\\bi\\s+want\\s+to\\s+change\\s+doctor\\s*[.!?।]*")
                || raw.matches("(?s).*(?:doctor|डॉक्टर)\\s+(?:बदलना\\s+है|badalna\\s+hai|change\\s+(?:karo|करो))\\s*[.!?।]*")
                || raw.matches("(?s).*दूसरे\\s+डॉक्टर\\s+के\\s+slots\\s+दिखाओ\\s*[.!?।]*")
                || raw.matches("(?s).*मुझे\\s+डॉक्टर\\s+बदलना\\s+है\\s*[.!?।]*")
                || raw.matches("(?s).*डॉक्टर\\s+बदलना\\s+है\\s*[.!?।]*");
    }

    private boolean isRestartBookingRequest(NormalizedUserTurn turn) {
        String text = turn == null ? "" : (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bstart\\s+again\\b|\\bstart\\s+over\\b|booking\\s+फिर\\s+से\\s+शुरू\\s+करो|"
                + "बुकिंग\\s+फिर\\s+से\\s+शुरू\\s+करो|दोबारा\\s+शुरू\\s+करो).*" );
    }

    /** Prepares a Voice booking or consumes its dedicated final confirmation, never the Portal kernel mutation. */
    public GuestPreparationResult prepareAuthorizedVoiceBookingTurn(AivaExecutionContext context, String transcript,
            BookingExecutionContext bookingContext, VoiceTransactionAuthPolicy policy) {
        if (context == null || bookingContext == null || policy == null || transactionalBoundary == null
                || voiceBookingExecutionService == null || !StringUtils.hasText(transcript)
                || !"VOICE".equalsIgnoreCase(context.channel())
                || !java.util.Objects.equals(context.providerCallId(), bookingContext.providerCallId())
                || !java.util.Objects.equals(context.sessionId(), bookingContext.sessionId())
                || !java.util.Objects.equals(context.interactionId(), bookingContext.interactionId())) {
            return GuestPreparationResult.denied("BOOKING_BOUNDARY_UNAVAILABLE");
        }
        String key = sessionKey(context);
        String turnId = AivaV2VoiceFlowTrace.turnId();
        AtomicReference<GuestPreparationResult> outcome = new AtomicReference<>();
        try {
        sessionStore.update(key, current -> {
            if (current == null || current.closed() || !context.tenantId().toString().equals(current.tenantId())) {
                outcome.set(GuestPreparationResult.denied("SESSION_BINDING_MISMATCH"));
                return current;
            }
            if (current.patientId() == null || !current.patientId().equals(bookingContext.patientId())) {
                if (current.patientId() != null || current.activeDraft() != null) {
                    outcome.set(GuestPreparationResult.denied("BOOKING_PATIENT_BINDING_MISMATCH"));
                    return current;
                }
                current = current.withPatientId(bookingContext.patientId());
            }
            if (current.activeDraft() != null
                    && !bookingContext.patientId().equals(current.activeDraft().patientSubjectId())) {
                outcome.set(GuestPreparationResult.denied("BOOKING_DRAFT_PATIENT_MISMATCH"));
                return current;
            }
            NormalizedUserTurn turn = normalizeVoiceTurn(context, current, transcript);
            AvailabilityResult pendingAvailability = current.latestAvailabilityResult();
            if (pendingAvailability != null && "VOICE_PENDING_DAYPART".equals(pendingAvailability.cursor())
                    && transcript.trim().matches("(?iu)shyam[.!?।]*")) {
                turn = turn.withDaypart(NormalizedUserTurn.Daypart.EVENING).withoutDoctorEvidence();
            }
            boolean pendingShowMoreResolved = false;
            boolean awaitSlotContext = AivaV2VoiceSlotContext.isAwaitSlot(current, Instant.now());
            ConversationDecision slotContextDecision = null;
            PresentationTurn presentation = applyPresentationMode(current, turn);
            current = presentation.session();
            turn = presentation.turn();
            if (isClosing(turn)) {
                outcome.set(new GuestPreparationResult(closingResponse(turn), "END_CONVERSATION",
                        Map.of("mutationAllowed", false, "toolsInvoked", false, "conversationClosed", true,
                                "draftCancelled", isPreparedBookingCancellation(current, turn))));
                return current.close();
            }
            if (isPreparedBookingCancellation(current, turn)) {
                SessionProjection cancelled = current.clearTransientWorkflow();
                String response = isEnglish(turn) ? "Okay, I have stopped this booking request."
                        : isHinglish(turn) ? "Theek hai, maine yeh booking request rok di hai."
                        : "ठीक है, मैंने यह booking request रोक दी है।";
                outcome.set(new GuestPreparationResult(response, "CURRENT_BOOKING_CANCELLED",
                        Map.of("mutationAllowed", false, "toolsInvoked", false,
                                "draftCancelled", true, "contextAction", "CANCEL_CURRENT_BOOKING")));
                return cancelled;
            }
            if (isRepeatRequest(turn) && current.pendingConfirmation() == null) {
                String repeated = repeatCurrentPrompt(current, turn);
                if (repeated != null) {
                    AvailabilityResult page = current.latestAvailabilityResult();
                    int offset = page == null ? -1 : page.displayOffset();
                    outcome.set(new GuestPreparationResult(repeated,
                            hasDisplayedSlotPage(page) && AivaV2VoiceSlotContext.isAwaitSlot(current, Instant.now())
                                    ? "REPEAT_SLOT_PAGE" : "REPEAT_CURRENT_PROMPT",
                            Map.of("mutationAllowed", false, "toolsInvoked", false,
                                    "decisionAction", hasDisplayedSlotPage(page) ? "REPEAT_SLOT_PAGE" : "REPEAT_CURRENT_PROMPT",
                                    "repeatTarget", repeatTarget(current), "slotPageBefore", offset,
                                    "slotPageAfter", offset, "contextPreserved", true)));
                    return current;
                }
            }
            if (isProviderContextInspection(turn) || isDateContextInspection(turn)
                    || isSelectedSlotInspection(turn)) {
                String response = contextInspectionResponse(current, turn);
                outcome.set(new GuestPreparationResult(response, "READ_ONLY_CONTEXT_INSPECTION",
                        Map.of("mutationAllowed", false, "toolsInvoked", false,
                                "contextAction", isProviderContextInspection(turn) ? "INSPECT_PROVIDER"
                                        : isDateContextInspection(turn) ? "INSPECT_DATE" : "INSPECT_SELECTED_SLOT",
                                "contextPreserved", true)));
                return current;
            }
            if (isDeclineCurrentSlotOptions(current, turn)) {
                String response = isEnglish(turn)
                        ? "No problem. You can ask for more slots, change the date or doctor, or stop this booking."
                        : isHinglish(turn)
                        ? "Theek hai. Aap aur slots, date ya doctor change kar sakte hain, ya booking rok sakte hain."
                        : "ठीक है। आप और समय पूछ सकते हैं, तारीख या डॉक्टर बदल सकते हैं, या booking रोक सकते हैं।";
                outcome.set(new GuestPreparationResult(response, "DECLINE_CURRENT_SLOT_OPTIONS",
                        Map.of("mutationAllowed", false, "toolsInvoked", false,
                                "contextPreserved", true, "decisionAction", "DECLINE_CURRENT_SLOT_OPTIONS")));
                return current;
            }
            AvailabilityResult readOnlyAvailability = current.latestAvailabilityResult();
            AvailabilitySlot readOnlySelectedSlot = null;
            if (awaitSlotContext && parsedTimeRange(turn) != null) {
                NormalizedUserTurn rangeTurn = turn.daypart() == null && readOnlyAvailability != null
                        ? turn.withDaypart(daypartFromWindow(readOnlyAvailability.timeWindow())) : turn;
                String rangeValue = parsedTimeRange(rangeTurn);
                AvailabilityTimeConstraint range = timeConstraint(rangeTurn, rangeValue, readOnlyAvailability);
                if (range != null) {
                    AivaV2Models.BookingPatch rangePatch = new AivaV2Models.BookingPatch(
                            AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                            AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                            AivaV2Models.ValuePatch.clear(), AivaV2Models.ValuePatch.unchanged(),
                            new AivaV2Models.AvailabilityTimeConstraintPatch(AivaV2Models.PatchMode.SET, range));
                    slotContextDecision = collectionDecision(rangeTurn, rangePatch);
                    log.info("VOICE_SLOT_CONTEXT_ACTION state=AWAIT_SLOT action=REFINE_TIME_RANGE "
                                    + "rangeStart={} rangeEnd={} contextPreserved=true slotInvalidatedReason=TIME_RANGE_REFINED",
                            range.startTime(), range.endTime());
                }
            }
            if (current.activeDraft() == null && hasRetainedReadOnlySlotPage(current)
                    && !isContextChangeCommand(turn) && !isShowMore(turn)
                    && parsedTimeRange(turn) == null
                    && retainedSlotContextMatches(readOnlyAvailability, turn)
                    && !hasConflictingProviderEvidence(current, turn)) {
                AivaV2DisplayedSlotSelectionResolver.Resolution displayedSelection =
                        displayedSlotSelectionResolver.resolve(turn, readOnlyAvailability);
                if (displayedSelection.recognized()) {
                    AvailabilitySlot selected = selectedDisplayedSlot(readOnlyAvailability,
                            displayedSelection.selection());
                    if (selected == null) {
                        outcome.set(new GuestPreparationResult(slotContinuationClarification(turn),
                                "CLARIFY_DISPLAYED_SLOT_TIME", Map.of("mutationAllowed", false,
                                        "contextPreserved", true, "availabilityReloaded", false,
                                        "state", "AWAIT_SLOT")));
                        return current;
                    }
                    readOnlySelectedSlot = selected;
                    logSlotSelection(readOnlyAvailability, selected, displayedSelection.selection());
                    turn = turn.withExactTime(selected.startsAt());
                    String providerArtifact = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                            : turn.doctorEntity().canonicalQuery();
                    if (isClockWordProviderArtifact(providerArtifact)) {
                        // Hindi time words such as "nau" can also be misread by the
                        // lexical parser as a doctor name. The retained canonical slot
                        // is stronger contextual evidence for this turn.
                        turn = turn.withoutDoctorEvidence();
                    }
                    turn = turn.withTemporal(new NormalizedUserTurn.TemporalFact(null,
                            NormalizedUserTurn.TemporalStatus.RESOLVED, readOnlyAvailability.date(),
                            NormalizedUserTurn.RelativeKind.NONE, false));
                    NormalizedUserTurn.Daypart retainedDaypart = daypartFromWindow(readOnlyAvailability.timeWindow());
                    if (retainedDaypart != null) turn = turn.withDaypart(retainedDaypart);
                }
            }
            VoiceClarificationState pendingClarification = current.voiceClarificationState();
            AivaV2Models.BookingDraft activeBooking = current.activeDraft();
            ProviderCandidate activeBookingProvider = activeBooking != null && activeBooking.selectedProvider() != null
                    ? activeBooking.selectedProvider() : current.activeVoiceDoctor();
            boolean pendingProviderIdentityResolved = false;
            ConversationDecision providerSwitchDecision = null;
            String providerEvidence = turn.doctorEntity() == null
                    ? turn.explicitDoctorReference() : turn.doctorEntity().canonicalQuery();
            boolean upgradingReadOnlyAmbiguity = pendingClarification != null
                    && pendingClarification.kind() == VoiceClarificationKind.DOCTOR
                    && pendingClarification.unresolvedReason() == VoiceUnresolvedReason.AMBIGUOUS_DOCTOR
                    && pendingClarification.pendingAction() == null
                    && pendingClarification.canonicalProviderCandidates().isEmpty();
            PendingProviderSwitch pendingSwitch = pendingClarification == null ? null
                    : pendingClarification.pendingProviderSwitch();
            boolean globalContextControlResolved = false;
            if (isRestartBookingRequest(turn)) {
                SessionProjection reset = current.clearTransientWorkflow();
                String response = isEnglish(turn) ? "Okay. We can start a new appointment request."
                        : isHinglish(turn) ? "Theek hai. Hum nayi appointment request shuru kar sakte hain."
                        : "ठीक है। हम नई appointment request शुरू कर सकते हैं।";
                outcome.set(new GuestPreparationResult(response, "BOOKING_CONTEXT_RESET",
                        Map.of("mutationAllowed", false, "contextAction", "RESET_BOOKING",
                                "conversationControl", "START_OVER", "contextPreserved", false)));
                return reset;
            }
            if (isProviderChangeRequest(turn)) {
                if (isProviderChangeWithoutTarget(turn)
                        || isGenericOrCurrentProviderEvidence(providerEvidence, activeBookingProvider)) {
                    providerEvidence = null;
                    turn = turn.withoutDoctorEvidence();
                    current = clearSlotForProviderChange(current, context)
                            .withVoiceClarificationState(contextChangeClarification("CHANGE_PROVIDER",
                                    current.activeWorkflow()));
                    outcome.set(new GuestPreparationResult(changeProviderPrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                            Map.of("mutationAllowed", false, "contextAction", "CHANGE_PROVIDER",
                                    "pendingClarificationType", "CHANGE_PROVIDER", "slotInvalidatedReason",
                                    "PROVIDER_CHANGE_REQUESTED", "contextPreserved", true)));
                    return current;
                }
            } else if (isContextChangeCommand(turn)) {
                String rawControl = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
                if (rawControl.matches("(?s).*(?:change\\s+(?:the\\s+)?date|तारीख\\s+(?:बदलो|बदलनी)).*")) {
                    if (turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                            && turn.temporal().localDate() != null) {
                        current = clearSelectedSlotForContextChange(current).withVoiceClarificationState(null);
                        pendingClarification = null;
                        pendingSwitch = null;
                        slotContextDecision = slotDateDecision(turn, turn.temporal().localDate());
                        globalContextControlResolved = true;
                    } else {
                        current = clearSelectedSlotForContextChange(current).withVoiceClarificationState(
                                contextChangeClarification("CHANGE_DATE", current.activeWorkflow()));
                        outcome.set(new GuestPreparationResult(changeDatePrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextAction", "CHANGE_DATE",
                                        "pendingClarificationType", "CHANGE_DATE", "slotInvalidatedReason",
                                        "DATE_CHANGE_REQUESTED", "contextPreserved", true)));
                        return current;
                    }
                } else if (rawControl.matches("(?s).*(?:change\\s+(?:the\\s+)?time|समय\\s+(?:बदलो|बदलना)).*")) {
                    if (turn.daypart() != null) {
                        current = clearSelectedSlotForContextChange(current).withVoiceClarificationState(null);
                        pendingClarification = null;
                        pendingSwitch = null;
                        slotContextDecision = slotDaypartDecision(turn, turn.daypart());
                        globalContextControlResolved = true;
                    } else {
                        current = clearSelectedSlotForContextChange(current).withVoiceClarificationState(
                                contextChangeClarification("CHANGE_DAYPART", current.activeWorkflow()));
                        outcome.set(new GuestPreparationResult(changeDaypartPrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextAction", "CHANGE_DAYPART",
                                        "pendingClarificationType", "CHANGE_DAYPART", "slotInvalidatedReason",
                                        "DAYPART_CHANGE_REQUESTED", "contextPreserved", true)));
                        return current;
                    }
                }
            }
            String requestedSpecialty = specialty(turn);
            if (awaitSlotContext && pendingSwitch == null && activeBookingProvider != null
                    && StringUtils.hasText(requestedSpecialty)
                    && !sameCanonicalLabel(requestedSpecialty, activeBookingProvider.specialty())
                    && looksLikeProviderChange(turn)) {
                ProviderSearchResult specialtyResolution = transactionalBoundary.resolveVoiceProviderSpecialty(
                        context.tenantId(), requestedSpecialty);
                if (specialtyResolution != null && specialtyResolution.status() == ResolutionStatus.AMBIGUOUS
                        && !specialtyResolution.candidates().isEmpty()) {
                    VoiceClarificationState identity = providerIdentityState(specialtyResolution.candidates(),
                            context.tenantId(), current.activeWorkflow());
                    current = current.withVoiceClarificationState(identity);
                    outcome.set(new GuestPreparationResult(providerIdentityClarification(identity, turn),
                            "PROVIDER_IDENTITY_AMBIGUOUS", providerIdentityDiagnostics(identity)));
                    return current;
                }
                if (specialtyResolution != null && specialtyResolution.status() == ResolutionStatus.RESOLVED
                        && specialtyResolution.resolvedProvider() != null) {
                    ProviderCandidate target = transactionalBoundary.resolveVoiceBookingProvider(context.tenantId(),
                            specialtyResolution.resolvedProvider());
                    if (target != null && !sameProvider(activeBookingProvider, target)) {
                        current = beginProviderSwitch(current, activeBookingProvider, target, turn, context);
                        outcome.set(new GuestPreparationResult(providerSwitchWarning(activeBookingProvider,
                                target, turn), "PROVIDER_SWITCH_CONFIRMATION_REQUIRED",
                                providerSwitchDiagnostics(current)));
                        return current;
                    }
                }
            }
            boolean sameProviderReaffirmed = pendingSwitch == null && activeBookingProvider != null
                    && isSameProviderReaffirmation(activeBookingProvider, providerEvidence, turn);
            if (sameProviderReaffirmed && !hasExplicitTemporalOrDaypartEvidence(turn)
                    && !isBookingRequest(turn)) {
                outcome.set(new GuestPreparationResult(providerSwitchDeclined(turn),
                        "SAME_PROVIDER_REAFFIRMED", Map.of("mutationAllowed", false,
                                "providerId", activeBookingProvider.doctorId(), "contextPreserved", true)));
                return current;
            }
            if (sameProviderReaffirmed) {
                // Keep the already-bound canonical provider and consume temporal or
                // booking details from this turn without re-resolving the same name.
                turn = turn.withoutDoctorEvidence();
                providerEvidence = null;
            }
            if (pendingSwitch != null) {
                if (!AivaV2VoiceContextTransitions.providerSwitchStillBound(current, pendingSwitch)) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(providerIdentityExpired(turn),
                            "PROVIDER_SWITCH_STALE", Map.of("mutationAllowed", false,
                            "contextPreserved", true, "switchCommitted", false)));
                    return current;
                }
                // A canonical name is an answer to this exact provider-switch question.
                // It must not be sent back through generic booking intent inference.
                if (StringUtils.hasText(providerEvidence)
                        && DoctorNameNormalizer.isPlausibleReference(providerEvidence)) {
                    if (turn.confirmation() != NormalizedUserTurn.Confirmation.NEGATIVE
                            && matchesCanonicalProviderReference(pendingSwitch.targetDisplayName(), providerEvidence)) {
                        turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.POSITIVE);
                    } else if (matchesCanonicalProviderReference(activeBookingProvider.displayName(), providerEvidence)) {
                        turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.NEGATIVE);
                    }
                }
                boolean currentProviderNamed = StringUtils.hasText(providerEvidence)
                        && matchesCanonicalProviderReference(activeBookingProvider.displayName(), providerEvidence);
                boolean requestedProviderNamed = StringUtils.hasText(providerEvidence)
                        && matchesCanonicalProviderReference(pendingSwitch.targetDisplayName(), providerEvidence);
                if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                        && StringUtils.hasText(providerEvidence) && !currentProviderNamed && !requestedProviderNamed
                        && DoctorNameNormalizer.isPlausibleReference(providerEvidence)) {
                    ProviderSearchResult declinedMention = transactionalBoundary.resolveVoiceProviderEvidence(
                            context.tenantId(), providerEvidence);
                    if (declinedMention != null && declinedMention.status() == ResolutionStatus.RESOLVED
                            && declinedMention.resolvedProvider() != null) {
                        ProviderCandidate canonicalMention = transactionalBoundary.resolveVoiceBookingProvider(
                                context.tenantId(), declinedMention.resolvedProvider());
                        currentProviderNamed = canonicalMention != null
                                && canonicalMention.doctorId().equals(pendingSwitch.currentProviderId());
                        requestedProviderNamed = canonicalMention != null
                                && canonicalMention.doctorId().equals(pendingSwitch.targetProviderId());
                    }
                }
                boolean thirdProviderWithDecline = turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                        && StringUtils.hasText(providerEvidence)
                        && DoctorNameNormalizer.isPlausibleReference(providerEvidence)
                        && !requestedProviderNamed && !currentProviderNamed;
                if (thirdProviderWithDecline) {
                    // A third provider in a decline utterance is resolved as a replacement target.
                    turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.NONE);
                }
                if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                        && !thirdProviderWithDecline) {
                    LocalDate dateBefore = current.activeDraft() == null
                            ? null : current.activeDraft().preferredDate();
                    String daypartBefore = current.activeDraft() == null
                            ? null : current.activeDraft().preferredTimeWindow();
                    boolean hasAdditionalContext = hasExplicitTemporalOrDaypartEvidence(turn);
                    current = retainProviderSwitchContextOnDecline(current, pendingSwitch, turn, context);
                    log.info("AIVA_V2_PROVIDER_SWITCH event=PROVIDER_SWITCH_DECLINED currentProviderId={} targetProviderId={} sourceContext={}",
                            pendingSwitch.currentProviderId(), pendingSwitch.targetProviderId(), pendingSwitch.originatingContext());
                    if (hasAdditionalContext) {
                        pendingClarification = null;
                        pendingSwitch = null;
                        activeBooking = current.activeDraft();
                        activeBookingProvider = activeBooking != null && activeBooking.selectedProvider() != null
                                ? activeBooking.selectedProvider() : current.activeVoiceDoctor();
                        turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.NONE).withoutDoctorEvidence();
                        if (turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                                && turn.temporal().localDate() != null) {
                            slotContextDecision = slotDateDecision(turn, turn.temporal().localDate());
                        } else if (turn.daypart() != null) {
                            slotContextDecision = slotDaypartDecision(turn, turn.daypart());
                        } else if (turn.exactTime() != null) {
                            slotContextDecision = slotExactTimeDecision(turn, turn.exactTime());
                        }
                    } else {
                    outcome.set(new GuestPreparationResult(providerSwitchDeclined(turn),
                            "PROVIDER_SWITCH_DECLINED", Map.of("mutationAllowed", false,
                            "providerId", pendingSwitch.currentProviderId(), "contextPreserved", true,
                            "availabilityReloaded", false,
                            "dateBefore", String.valueOf(dateBefore),
                            "dateAfter", String.valueOf(current.activeDraft() == null
                                    ? null : current.activeDraft().preferredDate()),
                            "daypartBefore", String.valueOf(daypartBefore),
                            "daypartAfter", String.valueOf(current.activeDraft() == null
                                    ? null : current.activeDraft().preferredTimeWindow()))));
                    return current;
                    }
                }
                if (pendingSwitch != null && turn.confirmation() != NormalizedUserTurn.Confirmation.POSITIVE) {
                    if (StringUtils.hasText(providerEvidence)
                            && !matchesCanonicalProviderReference(pendingSwitch.targetDisplayName(), providerEvidence)
                            && !matchesCanonicalProviderReference(activeBookingProvider.displayName(), providerEvidence)
                            && DoctorNameNormalizer.isPlausibleReference(providerEvidence)) {
                        ProviderSearchResult revised = transactionalBoundary.resolveVoiceProviderEvidence(
                                context.tenantId(), providerEvidence);
                        if (revised != null && revised.status() == ResolutionStatus.AMBIGUOUS) {
                            VoiceClarificationState identity = providerIdentityState(revised.candidates(),
                                    context.tenantId(), pendingSwitch.originatingContext());
                            if (!identity.canonicalProviderCandidates().isEmpty()) {
                                current = current.withVoiceClarificationState(identity);
                                outcome.set(new GuestPreparationResult(providerIdentityClarification(identity, turn),
                                        "PROVIDER_IDENTITY_AMBIGUOUS", providerIdentityDiagnostics(identity)));
                                return current;
                            }
                        }
                        if (revised != null && revised.status() == ResolutionStatus.RESOLVED
                                && revised.resolvedProvider() != null) {
                            ProviderCandidate nextTarget = transactionalBoundary.resolveVoiceBookingProvider(
                                    context.tenantId(), revised.resolvedProvider());
                            if (nextTarget != null && !nextTarget.doctorId().equals(pendingSwitch.currentProviderId())) {
                                current = beginProviderSwitch(current, activeBookingProvider, nextTarget, turn, context);
                                outcome.set(new GuestPreparationResult(providerSwitchWarning(activeBookingProvider,
                                        nextTarget, turn), "PROVIDER_SWITCH_CONFIRMATION_REQUIRED",
                                        providerSwitchDiagnostics(current)));
                                return current;
                            }
                        }
                    }
                    outcome.set(new GuestPreparationResult(providerSwitchWarning(activeBookingProvider,
                            pendingSwitch.targetDisplayName(), turn), "PROVIDER_SWITCH_CONFIRMATION_PENDING",
                            Map.of("mutationAllowed", false, "pendingAction", "PROVIDER_SWITCH_CONFIRMATION",
                                    "contextPreserved", true, "availabilityReloaded", false)));
                    return current;
                }
                ProviderCandidate canonicalTarget = revalidateProviderSwitchTarget(context, pendingSwitch);
                if (canonicalTarget == null) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(providerIdentityExpired(turn),
                            "PROVIDER_SWITCH_TARGET_STALE", Map.of("mutationAllowed", false,
                            "contextPreserved", true, "switchCommitted", false)));
                    return current;
                }
                current = AivaV2VoiceContextTransitions.invalidateForProviderSwitch(current,
                        bookingContext.patientId(), pendingSwitch, UUID.randomUUID(), Instant.now());
                turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.NONE)
                        .withDoctorEvidence(canonicalTarget.displayName());
                providerEvidence = canonicalTarget.displayName();
                activeBooking = current.activeDraft();
                activeBookingProvider = null;
                pendingClarification = null;
                pendingProviderIdentityResolved = true;
                providerSwitchDecision = providerSwitchCommitDecision(turn, canonicalTarget.displayName());
                log.info("AIVA_V2_PROVIDER_SWITCH event=PROVIDER_SWITCH_CONFIRMED oldProviderId={} newProviderId={} preservedDate={} sourceContext={} reason={}",
                        pendingSwitch.currentProviderId(), canonicalTarget.doctorId(),
                        pendingSwitch.requestedDate() != null, pendingSwitch.originatingContext(), pendingSwitch.reason());
            }
            if (pendingClarification != null && ("CHANGE_DATE".equals(pendingClarification.pendingAction())
                    || "CHANGE_DAYPART".equals(pendingClarification.pendingAction()))) {
                if ("CHANGE_DATE".equals(pendingClarification.pendingAction())
                        && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                        && turn.temporal().localDate() != null) {
                    current = current.withVoiceClarificationState(null);
                    slotContextDecision = slotDateDecision(turn, turn.temporal().localDate());
                    logContextAction("CHANGE_DATE", activeBookingProvider, activeBookingProvider,
                            activeBooking == null ? null : activeBooking.preferredDate(),
                            turn.temporal().localDate(), activeBooking == null ? null : activeBooking.preferredTimeWindow(),
                            turn.daypart() == null ? null : turn.daypart().name(), "DATE_CHANGE");
                    pendingClarification = null;
                } else if ("CHANGE_DAYPART".equals(pendingClarification.pendingAction()) && turn.daypart() != null) {
                    current = current.withVoiceClarificationState(null);
                    slotContextDecision = slotDaypartDecision(turn, turn.daypart());
                    logContextAction("CHANGE_DAYPART", activeBookingProvider, activeBookingProvider,
                            activeBooking == null ? null : activeBooking.preferredDate(),
                            activeBooking == null ? null : activeBooking.preferredDate(),
                            activeBooking == null ? null : activeBooking.preferredTimeWindow(),
                            turn.daypart().name(), "DAYPART_CHANGE");
                    pendingClarification = null;
                } else {
                    String response = "CHANGE_DATE".equals(pendingClarification.pendingAction())
                            ? changeDatePrompt(turn) : changeDaypartPrompt(turn);
                    outcome.set(new GuestPreparationResult(response, "CONTEXT_CHANGE_CLARIFICATION",
                            Map.of("mutationAllowed", false, "contextAction", pendingClarification.pendingAction(),
                                    "pendingClarificationType", pendingClarification.pendingAction(),
                                    "contextPreserved", true)));
                    return current;
                }
            }
            if (pendingClarification != null && "CHANGE_PROVIDER".equals(pendingClarification.pendingAction())) {
                if (StringUtils.hasText(providerEvidence)) {
                    current = current.withVoiceClarificationState(null);
                    pendingClarification = null;
                } else {
                    String response = changeProviderPrompt(turn);
                    outcome.set(new GuestPreparationResult(response, "CONTEXT_CHANGE_CLARIFICATION",
                            Map.of("mutationAllowed", false, "contextAction", "CHANGE_PROVIDER",
                                    "pendingClarificationType", "CHANGE_PROVIDER", "contextPreserved", true)));
                    return current;
                }
            }
            if (!globalContextControlResolved && isContextChangeCommand(turn)) {
                String raw = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
                if (isChangeProviderCommand(turn)) {
                    if (isGenericOrCurrentProviderEvidence(providerEvidence, activeBookingProvider)) {
                        providerEvidence = null;
                        turn = turn.withoutDoctorEvidence();
                    }
                    if (!StringUtils.hasText(providerEvidence)) {
                        VoiceClarificationState changeProvider = contextChangeClarification("CHANGE_PROVIDER",
                                current.activeWorkflow());
                        current = clearSlotForProviderChange(current, context).withVoiceClarificationState(changeProvider);
                        String response = changeProviderPrompt(turn);
                        outcome.set(new GuestPreparationResult(response, "CONTEXT_CHANGE_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextAction", "CHANGE_PROVIDER",
                                        "pendingClarificationType", "CHANGE_PROVIDER", "slotInvalidatedReason",
                                        "PROVIDER_CHANGE_REQUESTED", "contextPreserved", true)));
                        return current;
                    }
                } else if (raw.matches("(?s).*(?:change\\s+(?:the\\s+)?date|तारीख\\s+(?:बदलो|बदलनी)).*")) {
                    if (turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                            && turn.temporal().localDate() != null) {
                        current = clearSelectedSlotForContextChange(current);
                        slotContextDecision = slotDateDecision(turn, turn.temporal().localDate());
                        logContextAction("CHANGE_DATE", activeBookingProvider, activeBookingProvider,
                                activeBooking == null ? null : activeBooking.preferredDate(),
                                turn.temporal().localDate(), activeBooking == null ? null : activeBooking.preferredTimeWindow(),
                                turn.daypart() == null ? null : turn.daypart().name(), "DATE_CHANGE");
                    } else {
                        current = clearSelectedSlotForContextChange(current);
                        VoiceClarificationState changeDate = contextChangeClarification("CHANGE_DATE",
                                current.activeWorkflow());
                        current = current.withVoiceClarificationState(changeDate);
                        outcome.set(new GuestPreparationResult(changeDatePrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextAction", "CHANGE_DATE",
                                        "pendingClarificationType", "CHANGE_DATE", "slotInvalidatedReason",
                                        "DATE_CHANGE_REQUESTED", "contextPreserved", true)));
                        return current;
                    }
                } else if (raw.matches("(?s).*(?:change\\s+(?:the\\s+)?time|समय\\s+(?:बदलो|बदलना)).*")) {
                    if (turn.daypart() != null) {
                        current = clearSelectedSlotForContextChange(current);
                        slotContextDecision = slotDaypartDecision(turn, turn.daypart());
                        logContextAction("CHANGE_DAYPART", activeBookingProvider, activeBookingProvider,
                                activeBooking == null ? null : activeBooking.preferredDate(),
                                activeBooking == null ? null : activeBooking.preferredDate(),
                                activeBooking == null ? null : activeBooking.preferredTimeWindow(),
                                turn.daypart().name(), "DAYPART_CHANGE");
                    } else {
                        current = clearSelectedSlotForContextChange(current);
                        VoiceClarificationState changeTime = contextChangeClarification("CHANGE_DAYPART",
                                current.activeWorkflow());
                        current = current.withVoiceClarificationState(changeTime);
                        outcome.set(new GuestPreparationResult(changeDaypartPrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextAction", "CHANGE_DAYPART",
                                        "pendingClarificationType", "CHANGE_DAYPART", "slotInvalidatedReason",
                                        "DAYPART_CHANGE_REQUESTED", "contextPreserved", true)));
                        return current;
                    }
                }
            }
            if (pendingClarification != null && "SHOW_MORE".equals(pendingClarification.pendingAction())) {
                VoiceRecognitionResult pendingAnswer = recognizePendingClarification(pendingClarification, turn);
                if (pendingAnswer.action() == VoiceRecognitionResult.Action.SHOW_MORE) {
                    turn = turn.withPagination(NormalizedUserTurn.Pagination.SHOW_MORE)
                            .withConfirmation(NormalizedUserTurn.Confirmation.NONE).withoutDoctorEvidence();
                    pendingShowMoreResolved = true;
                    current = current.withVoiceClarificationState(null);
                    pendingClarification = null;
                } else if (pendingAnswer.action() == VoiceRecognitionResult.Action.NEGATE) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(continuationDeclined(turn),
                            "CLARIFICATION_DECLINED", Map.of("mutationAllowed", false,
                            "pendingAction", "SHOW_MORE", "contextPreserved", true)));
                    return current;
                } else {
                    int attempts = pendingClarification.attemptCount();
                    boolean boundedFallback = attempts >= 2;
                    VoiceClarificationState repeated = new VoiceClarificationState(
                            VoiceClarificationKind.MISSING_INFO,
                            boundedFallback ? 2 : attempts + 1,
                            boundedFallback ? VoiceUnresolvedReason.REPEATED_CLARIFICATION_FAILURE
                                    : VoiceUnresolvedReason.MISSING_REQUIRED_INFO,
                            pendingClarification.candidateOptions(), false, "SHOW_MORE",
                            pendingClarification.resumeContext());
                    current = current.withVoiceClarificationState(repeated);
                    String recovery = boundedFallback ? slotContinuationFallback(turn)
                            : slotContinuationRetryClarification(turn);
                    outcome.set(new GuestPreparationResult(recovery, "CLARIFICATION_PENDING",
                            Map.of("mutationAllowed", false, "pendingAction", "SHOW_MORE",
                                    "attemptCount", repeated.attemptCount(), "contextPreserved", true)));
                    return current;
                }
            }
            if (pendingClarification != null && "SEARCH_NEXT_DATE".equals(pendingClarification.pendingAction())) {
                if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
                    current = current.withVoiceClarificationState(null);
                    turn = turn.withConfirmation(NormalizedUserTurn.Confirmation.NONE).withoutDoctorEvidence();
                    slotContextDecision = slotDecision(turn, AivaV2Models.Operation.FIND_NEXT_AVAILABLE_SLOT, null);
                } else if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(continuationDeclined(turn),
                            "NEXT_DATE_SEARCH_DECLINED", Map.of("mutationAllowed", false,
                            "contextPreserved", true, "availabilityReloaded", false)));
                    return current;
                } else {
                    outcome.set(new GuestPreparationResult(slotExhaustedPrompt(turn), "CLARIFICATION_PENDING",
                            Map.of("mutationAllowed", false, "pendingAction", "SEARCH_NEXT_DATE",
                                    "contextPreserved", true)));
                    return current;
                }
            }
            if (pendingClarification == null && !awaitSlotContext && current.pendingConfirmation() == null
                    && current.activeDraft() != null
                    && turn.rawText().toLowerCase(Locale.ROOT).matches("(?s).*(?:समझ नहीं आया|समझ नहीं आई|not sure|didn't understand|did not understand|unclear).*" )
                    && providerSwitchDecision == null) {
                VoiceRecognitionResult pageRecognition = recognizeContextBoundControls(current, turn);
                if (pageRecognition.quality() == VoiceRecognitionResult.Quality.NO_MATCH
                        && !AivaV2VoiceSlotContext.explicitlySearchesNextDate(turn.rawText())
                        && !hasSlotContextChangeEvidence(turn)) {
                    VoiceClarificationState clarification = new VoiceClarificationState(
                            VoiceClarificationKind.MISSING_INFO, 1, VoiceUnresolvedReason.UNSUPPORTED_REQUEST,
                            List.of("choose_displayed_slot", "show_more"), false, "SHOW_MORE",
                            current.activeWorkflow());
                    current = current.withVoiceClarificationState(clarification);
                    String prompt = slotContinuationClarification(turn);
                    outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_CONTINUATION",
                            Map.of("mutationAllowed", false, "pendingAction", "SHOW_MORE",
                                    "contextPreserved", true, "state", "AWAIT_SLOT")));
                    return current;
                }
            }
            if (pendingClarification == null && awaitSlotContext && providerSwitchDecision == null
                    && slotContextDecision == null) {
                VoiceRecognitionResult slotRecognition = recognizeContextBoundControls(current, turn);
                AivaV2DisplayedSlotSelectionResolver.Resolution currentPageSelection =
                        displayedSlotSelectionResolver.resolve(turn, current.latestAvailabilityResult());
                if (currentPageSelection.recognized() && currentPageSelection.selection() != null
                        && currentPageSelection.selection().exactTime() == null
                        && currentPageSelection.selection().ordinal() == null) {
                    String prompt = slotContinuationClarification(turn);
                    outcome.set(new GuestPreparationResult(prompt, "CLARIFY_DISPLAYED_SLOT_TIME",
                            Map.of("mutationAllowed", false, "contextPreserved", true,
                                    "availabilityReloaded", false, "state", "AWAIT_SLOT")));
                    return current;
                }
                boolean explicitNextDate = AivaV2VoiceSlotContext.explicitlySearchesNextDate(turn.rawText());
                boolean explicitProviderChange = activeBookingProvider != null
                        && (turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference()))
                        && !currentPageSelection.recognized()
                        && isExplicitDifferentProvider(turn, activeBookingProvider.displayName());
                if (explicitProviderChange) {
                    // A clear named provider change is an allowed Phase 3 handoff, even when the utterance
                    // also contains a slot-continuation phrase.
                } else if (explicitNextDate) {
                    slotContextDecision = slotDecision(turn, AivaV2Models.Operation.FIND_NEXT_AVAILABLE_SLOT, null);
                } else if (slotRecognition.action() == VoiceRecognitionResult.Action.SHOW_MORE) {
                    turn = turn.withPagination(NormalizedUserTurn.Pagination.SHOW_MORE).withoutDoctorEvidence();
                    slotContextDecision = slotDecision(turn, AivaV2Models.Operation.SHOW_MORE_SLOTS, null);
                } else {
                    if (currentPageSelection.recognized()) {
                        slotContextDecision = slotDecision(turn, AivaV2Models.Operation.SELECT_SLOT,
                                currentPageSelection.selection());
                        turn = turn.withoutDoctorEvidence();
                    }
                }
                if (slotContextDecision == null && turn.temporal() != null
                        && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                        && turn.temporal().localDate() != null) {
                    slotContextDecision = slotDateDecision(turn, turn.temporal().localDate());
                } else if (slotContextDecision == null && turn.daypart() != null) {
                    slotContextDecision = slotDaypartDecision(turn, turn.daypart());
                }
                if (slotContextDecision == null
                        && (slotRecognition.action() == VoiceRecognitionResult.Action.CONFIRM
                        || slotRecognition.action() == VoiceRecognitionResult.Action.NEGATE)) {
                    String prompt = slotContinuationClarification(turn);
                    outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_CONTEXT_ACTION",
                            Map.of("mutationAllowed", false, "contextPreserved", true,
                                    "state", "AWAIT_SLOT", "confirmationAccepted", false)));
                    return current;
                }
                if (slotContextDecision == null && !explicitNextDate
                        && slotRecognition.quality() == VoiceRecognitionResult.Quality.NO_MATCH
                        && !hasSlotContextChangeEvidence(turn)) {
                    VoiceClarificationState clarification = new VoiceClarificationState(
                            VoiceClarificationKind.MISSING_INFO, 1, VoiceUnresolvedReason.UNSUPPORTED_REQUEST,
                            List.of("choose_displayed_slot", "show_more"), false, "SHOW_MORE",
                            current.activeWorkflow());
                    current = current.withVoiceClarificationState(clarification);
                    String prompt = slotContinuationClarification(turn);
                    outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_CONTINUATION",
                            Map.of("mutationAllowed", false, "pendingAction", "SHOW_MORE",
                                    "contextPreserved", true, "state", "AWAIT_SLOT")));
                    return current;
                }
            }
            if (pendingClarification != null && "PROVIDER_IDENTITY".equals(pendingClarification.pendingAction())) {
                VoiceRecognitionResult pendingProviderRecognition = recognizePendingClarification(
                        pendingClarification, turn);
                PendingProviderCandidateResolution resolution = pendingProviderRecognition != null
                        && (pendingProviderRecognition.quality() == VoiceRecognitionResult.Quality.EXACT
                        || pendingProviderRecognition.quality() == VoiceRecognitionResult.Quality.STRONG)
                        ? resolvePendingProviderCandidate(context, pendingClarification, turn)
                        : new PendingProviderCandidateResolution(null, false);
                ProviderCandidate selected = resolution.selected();
                if (resolution.stale()) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(providerIdentityExpired(turn),
                            "PROVIDER_IDENTITY_CANDIDATE_STALE", Map.of("mutationAllowed", false,
                            "contextPreserved", true)));
                    return current;
                }
                if (selected == null) {
                    if (pendingClarification.confirmSingleProviderCandidate()
                            && turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
                        current = current.withVoiceClarificationState(null);
                        outcome.set(new GuestPreparationResult(providerIdentityDeclined(turn),
                                "PROVIDER_IDENTITY_DECLINED", Map.of("mutationAllowed", false,
                                "contextPreserved", true, "clarificationCleared", true)));
                        return current;
                    }
                    VoiceClarificationState retry = new VoiceClarificationState(
                            VoiceClarificationKind.PROVIDER_IDENTITY,
                            Math.min(2, pendingClarification.attemptCount() + 1),
                            pendingClarification.unresolvedReason(), pendingClarification.candidateOptions(), false,
                            "PROVIDER_IDENTITY", pendingClarification.resumeContext(),
                            pendingClarification.canonicalProviderCandidates(),
                            pendingClarification.confirmSingleProviderCandidate());
                    current = current.withVoiceClarificationState(retry);
                    outcome.set(new GuestPreparationResult(providerIdentityClarification(retry, turn),
                            "PROVIDER_IDENTITY_CLARIFICATION_PENDING", providerIdentityDiagnostics(retry,
                                    pendingProviderRecognition == null ? VoiceRecognitionResult.Quality.NO_MATCH
                                            : pendingProviderRecognition.quality())));
                    return current;
                }
                ProviderCandidate revalidated = selected;
                if (activeBookingProvider != null && !sameProvider(activeBookingProvider, revalidated)) {
                    current = beginProviderSwitch(current, activeBookingProvider, revalidated, turn, context);
                    outcome.set(new GuestPreparationResult(providerSwitchWarning(activeBookingProvider, revalidated, turn),
                            "PROVIDER_SWITCH_CONFIRMATION_REQUIRED", providerSwitchDiagnostics(current)));
                    return current;
                }
                current = current.withVoiceClarificationState(null);
                turn = turn.withDoctorEvidence(revalidated.displayName());
                providerEvidence = revalidated.displayName();
                activeBookingProvider = revalidated;
                pendingProviderIdentityResolved = true;
            }
            if (slotContextDecision == null && providerSwitchDecision == null && !pendingProviderIdentityResolved
                    && (current.pendingConfirmation() == null || activeBookingProvider != null
                    && isExplicitDifferentProvider(turn, activeBookingProvider.displayName()))
                    && (current.pendingConfirmation() == null
                    || !turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE))
                    && (pendingClarification == null || upgradingReadOnlyAmbiguity) && StringUtils.hasText(providerEvidence)
                    && DoctorNameNormalizer.isPlausibleReference(providerEvidence)
                    && (activeBookingProvider == null
                    || !DoctorNameNormalizer.matches(activeBookingProvider.displayName(), providerEvidence))) {
                AivaV2Models.ProviderSearchResult providerResult = transactionalBoundary.resolveVoiceProviderEvidence(
                        context.tenantId(), providerEvidence);
                if (providerResult != null && providerResult.status() == AivaV2Models.ResolutionStatus.AMBIGUOUS) {
                    VoiceClarificationState providerClarification = providerIdentityState(providerResult.candidates(),
                            context.tenantId(), current.activeWorkflow());
                    if (!providerClarification.canonicalProviderCandidates().isEmpty()) {
                        current = current.withVoiceClarificationState(providerClarification);
                        outcome.set(new GuestPreparationResult(providerIdentityClarification(providerClarification, turn),
                                "PROVIDER_IDENTITY_AMBIGUOUS", providerIdentityDiagnostics(providerClarification)));
                        return current;
                    }
                }
                if (upgradingReadOnlyAmbiguity
                        && (providerResult == null || providerResult.status() == AivaV2Models.ResolutionStatus.NOT_FOUND)) {
                    current = current.withVoiceClarificationState(null);
                    outcome.set(new GuestPreparationResult(providerIdentityExpired(turn),
                            "PROVIDER_IDENTITY_NO_MATCH", Map.of("mutationAllowed", false,
                            "contextPreserved", true, "recognitionQuality", "NO_MATCH")));
                    return current;
                }
                if (awaitSlotContext && providerResult != null
                        && providerResult.status() == AivaV2Models.ResolutionStatus.NOT_FOUND) {
                    String prompt = slotContinuationClarification(turn);
                    outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_PROVIDER_EVIDENCE",
                            Map.of("mutationAllowed", false, "contextPreserved", true,
                                    "state", "AWAIT_SLOT", "providerDiscoveryRepeated", false)));
                    return current;
                }
                if (providerResult != null && providerResult.status() == AivaV2Models.ResolutionStatus.RESOLVED
                        && providerResult.resolvedProvider() != null) {
                    ProviderCandidate canonicalTarget = transactionalBoundary.resolveVoiceBookingProvider(
                            context.tenantId(), providerResult.resolvedProvider());
                    if (canonicalTarget != null && activeBookingProvider != null
                            && !sameProvider(activeBookingProvider, canonicalTarget)) {
                        current = beginProviderSwitch(current, activeBookingProvider, canonicalTarget, turn, context);
                        outcome.set(new GuestPreparationResult(providerSwitchWarning(activeBookingProvider,
                                canonicalTarget, turn), "PROVIDER_SWITCH_CONFIRMATION_REQUIRED",
                                providerSwitchDiagnostics(current)));
                        return current;
                    } else if (canonicalTarget != null && activeBookingProvider != null) {
                        turn = turn.withDoctorEvidence(activeBookingProvider.displayName());
                        providerEvidence = activeBookingProvider.displayName();
                    }
                }
            }
            if (awaitSlotContext && slotContextDecision == null && providerSwitchDecision == null
                    && (turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference()))) {
                String prompt = slotContinuationClarification(turn);
                outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_PROVIDER_EVIDENCE",
                        Map.of("mutationAllowed", false, "contextPreserved", true,
                                "state", "AWAIT_SLOT", "semanticAiInvoked", false)));
                return current;
            }
            AivaV2VoiceCollectionContext.State collectionState =
                    AivaV2VoiceCollectionContext.state(current, Instant.now());
            ConversationDecision collectionContextDecision = null;
            ConversationDecision confirmationContextDecision = null;
            if (readOnlySelectedSlot != null && readOnlyAvailability.date() != null) {
                // The read-only page has no transactional slot reference. Carry its
                // canonical time/date as constraints so the booking kernel fetches
                // and revalidates the slot before presenting final confirmation.
                slotContextDecision = null;
                AivaV2Models.BookingPatch selectedSlotPatch = new AivaV2Models.BookingPatch(
                        AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.set(readOnlyAvailability.date().toString()),
                        AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.set(readOnlySelectedSlot.startsAt().toString()),
                        AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
                collectionContextDecision = collectionDecision(turn, selectedSlotPatch);
            }
            if (slotContextDecision == null && providerSwitchDecision == null
                    && !awaitSlotContext && !isCollectionUtilityControl(turn)
                    && collectionState != AivaV2VoiceCollectionContext.State.NONE) {
                turn = bookingContinuationTurn(turn, current.activeDraft(), context.timezone());
                CollectionTurn collection = collectionTurn(collectionState, turn, current);
                if (collection.prompt() != null) {
                    outcome.set(new GuestPreparationResult(collection.prompt(), collection.result(),
                            Map.of("mutationAllowed", false, "contextPreserved", true,
                                    "state", collectionState.name(), "semanticAiInvoked", false,
                                    "availabilityRequested", false)));
                    return current;
                }
                collectionContextDecision = collection.decision();
                if (collectionContextDecision == null
                        && "COLLECTION_TIME_CONSTRAINT".equals(collection.result())) {
                    ConversationDecision timeConstraint = decisionGateway
                            .deterministicCollectionTimeConstraint(turn, current);
                    if (isAllowedCollectionTimeConstraint(timeConstraint)) {
                        collectionContextDecision = timeConstraint;
                    } else {
                        String prompt = collectionPrompt(collectionState, turn, current);
                        outcome.set(new GuestPreparationResult(prompt, "COLLECTION_DAYPART_CLARIFICATION",
                                Map.of("mutationAllowed", false, "contextPreserved", true,
                                        "state", collectionState.name(), "semanticAiInvoked", false,
                                        "availabilityRequested", false)));
                        return current;
                    }
                }
            }
            if (current.pendingConfirmation() != null) {
                if (!AivaV2VoiceConfirmationContext.isAwaitConfirmation(current, bookingContext,
                        voiceBookingExecutionService, Instant.now())) {
                    current = clearConfirmationForSlotDecision(current);
                    current = refreshAvailabilityAfterStaleConfirmation(context, current, turn, bookingContext, policy);
                    outcome.set(new GuestPreparationResult(localizedStaleResponse(turn),
                            "BOOKING_CONFIRMATION_STALE", Map.of("mutationAllowed", false,
                                    "state", "AWAIT_CONFIRMATION", "bindingValid", false,
                                    "availabilityRefreshed", true, "aiInvoked", false)));
                    log.info("AIVA_V2_CONFIRMATION_TRACE state=AWAIT_CONFIRMATION action=STALE bindingValid=false "
                            + "authorizationValid={} executionAttempt=false", bookingContext.authorizationValidated());
                    return current;
                }
                AivaV2Models.BookingConfirmation pending = current.pendingConfirmation();
                Optional<AppointmentRecord> durableBooking;
                try {
                    durableBooking = voiceBookingExecutionService.reconcile(bookingContext, current.activeDraft(),
                            current.latestAvailabilityResult(), pending.idempotencyKey(), pending.confirmationRef());
                } catch (RuntimeException reconciliationFailure) {
                    log.warn("VOICE_BOOKING_RECONCILIATION_UNAVAILABLE callId={} executionIdPresent={} reason={}",
                            context.providerCallId(), StringUtils.hasText(pending.idempotencyKey()),
                            reconciliationFailure.getClass().getSimpleName());
                    outcome.set(new GuestPreparationResult(localizedBookingStatusChecking(turn),
                            "BOOKING_RECONCILIATION_UNAVAILABLE", Map.of("mutationAllowed", false,
                                    "state", "AWAIT_CONFIRMATION", "executionDecision", "STATUS_UNKNOWN",
                                    "automaticRetry", false)));
                    return current;
                }
                if (durableBooking != null && durableBooking.isPresent()) {
                    GuestPreparationResult reconciled = bookingConfirmedResult(durableBooking.get(),
                            current.activeDraft(), pending, bookingContext, turn, true);
                    outcome.set(reconciled);
                    log.info("VOICE_BOOKING_RECONCILED source=IDEMPOTENCY_RECEIPT result=CONFIRMED "
                                    + "executionIdPresent=true appointmentCorrelation={}",
                            reconciled.completedBookingOutcome().appointmentId());
                    return completedBookingSession(current, reconciled.completedBookingOutcome(),
                            current.activeDraft().selectedProvider());
                }
                ConfirmationTurnResolution confirmationTurn = resolveConfirmationTurn(current, turn);
                if (confirmationTurn.decision() != null) {
                    current = current.withPendingBookingConfirmation(null, preparedDraft(current.activeDraft()));
                    // Any explicit confirmation-time refinement invalidates the old slot binding
                    // before the existing kernel applies the new slot/date/time decision.
                    current = clearConfirmationForSlotDecision(current);
                    turn = confirmationTurn.turn().withConfirmation(NormalizedUserTurn.Confirmation.NONE);
                    confirmationContextDecision = confirmationTurn.decision();
                } else if (confirmationTurn.action() == ConfirmationTurnAction.CONFIRM
                        || confirmationTurn.action() == ConfirmationTurnAction.DECLINE) {
                    turn = turn.withConfirmation(confirmationTurn.action() == ConfirmationTurnAction.CONFIRM
                            ? NormalizedUserTurn.Confirmation.POSITIVE : NormalizedUserTurn.Confirmation.NEGATIVE);
                    log.info("AIVA_V2_CONFIRMATION_TRACE state=AWAIT_CONFIRMATION action={} bindingValid=true "
                                    + "authorizationValid={} executionAttempt={}", confirmationTurn.action(),
                            bookingContext.authorizationValidated(),
                            confirmationTurn.action() == ConfirmationTurnAction.CONFIRM);
                    GuestPreparationResult confirmationResult = consumeVoiceBookingConfirmation(
                            current, turn, bookingContext, policy);
                    outcome.set(confirmationResult);
                    if ("BOOKING_CONFIRMED".equals(confirmationResult.result())) {
                        return completedBookingSession(current, confirmationResult.completedBookingOutcome(),
                                current.activeDraft().selectedProvider());
                    }
                    if ("BOOKING_CONFIRMATION_DECLINED".equals(confirmationResult.result())) {
                        return clearConfirmationForSlotDecision(current);
                    }
                    if ("STALE_SLOT".equals(confirmationResult.result())
                            || "BOOKING_CONFIRMATION_STALE".equals(confirmationResult.result())) {
                        SessionProjection safe = clearConfirmationForSlotDecision(current);
                        return refreshAvailabilityAfterStaleConfirmation(context, safe, turn, bookingContext, policy);
                    }
                    if ("BOOKING_DISABLED".equals(confirmationResult.result())) {
                        return clearConfirmationForSlotDecision(current);
                    }
                    return current;
                } else {
                    String response = switch (confirmationTurn.action()) {
                        case REPEAT, CLARIFY -> bookingConfirmationPrompt(current.activeDraft(), turn);
                        case HELP -> confirmationHelp(turn);
                        case END -> confirmationEndResponse(turn);
                        case ABANDON -> confirmationEndResponse(turn);
                        default -> bookingConfirmationPrompt(current.activeDraft(), turn);
                    };
                    if (confirmationTurn.action() == ConfirmationTurnAction.ABANDON) {
                        outcome.set(new GuestPreparationResult("Okay. No appointment was booked.",
                                "BOOKING_ABANDONED", Map.of("mutationAllowed", false,
                                        "state", "ABANDONED", "aiInvoked", false)));
                        return current.clearTransientWorkflow();
                    }
                    outcome.set(new GuestPreparationResult(response, "BOOKING_CONFIRMATION_PENDING",
                            Map.of("mutationAllowed", false, "state", "AWAIT_CONFIRMATION",
                                    "action", confirmationTurn.action().name(), "bindingValid", true,
                                    "authorizationValid", bookingContext.authorizationValidated(),
                                    "aiInvoked", false)));
                    log.info("AIVA_V2_CONFIRMATION_TRACE state=AWAIT_CONFIRMATION action={} bindingValid=true "
                            + "authorizationValid={} executionAttempt=false", confirmationTurn.action(),
                            bookingContext.authorizationValidated());
                    return current;
                }
            }
            log.info("AIVA_V2_VOICE_FLOW stage=SESSION_BEFORE_DECISION tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                            + "activeWorkflow={} activeVoiceDoctorId={} activeVoiceDoctorName={} bookingProviderId={} bookingProviderName={} "
                            + "draftRevision={} draftStatus={} temporalSource={}", context.tenantId(), context.providerCallId(),
                    AivaV2VoiceFlowTrace.conversationHash(current.conversationId()), AivaV2VoiceFlowTrace.turnId(),
                    AivaV2VoiceFlowTrace.turnNumber(), current.activeWorkflow(),
                    current.activeVoiceDoctor() == null ? null : current.activeVoiceDoctor().doctorId(),
                    current.activeVoiceDoctor() == null ? null : current.activeVoiceDoctor().displayName(),
                    current.activeDraft() == null || current.activeDraft().selectedProvider() == null ? null
                            : current.activeDraft().selectedProvider().doctorId(),
                    current.activeDraft() == null || current.activeDraft().selectedProvider() == null ? null
                            : current.activeDraft().selectedProvider().displayName(),
                    current.activeDraft() == null ? null : current.activeDraft().revision(),
                    current.activeDraft() == null ? null : current.activeDraft().status(),
                    current.activeDraft() == null || current.activeDraft().preferredDate() == null ? "NONE" : "SESSION_CONTEXT");
            log.info("AIVA_V2_VOICE_FLOW stage=ADAPTER_AFTER_SEMANTIC_PARSE tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                            + "normalizedLanguage={} explicitDoctorReference={} resolvedDoctorId={} resolvedDoctorName={} intent={} "
                            + "currentTurnDate={} temporalStatus={} daypart={} exactTime={} activeWorkflow={}",
                    context.tenantId(), context.providerCallId(), AivaV2VoiceFlowTrace.conversationHash(current.conversationId()),
                    AivaV2VoiceFlowTrace.turnId(), AivaV2VoiceFlowTrace.turnNumber(), turn.languageCode(),
                    turn.explicitDoctorReference(), current.activeVoiceDoctor() == null ? null : current.activeVoiceDoctor().doctorId(),
                    current.activeVoiceDoctor() == null ? null : current.activeVoiceDoctor().displayName(), turn.intent(),
                    turn.temporal().localDate(), turn.temporal().status(), turn.daypart(), turn.exactTime(), current.activeWorkflow());
            SessionProjection decisionContext = current;
            if (decisionContext.activeDraft() == null) {
                AivaV2Models.BookingDraft fresh = AivaV2Models.BookingDraft.create(
                        bookingContext.patientId(), decisionContext.tenantId(), Instant.now());
                decisionContext = decisionContext.withBookingDraft(seedDraftFromReadOnlyContext(
                        fresh, decisionContext, turn));
            }
            turn = bookingContinuationTurn(turn, decisionContext.activeDraft(), context.timezone());
            decisionContext = adoptActiveVoiceDoctor(decisionContext, turn, context);
            logBookingFlow("DECISION_INPUT", context, decisionContext, turn, null,
                    providerStateSource(decisionContext));
            ConversationDecision decision = providerSwitchDecision != null ? providerSwitchDecision
                    : confirmationContextDecision != null ? confirmationContextDecision
                    : slotContextDecision != null ? slotContextDecision
                    : collectionContextDecision != null ? collectionContextDecision
                    : deterministicBookingContinuation(turn, decisionContext, pendingShowMoreResolved);
            if (decision == null && pendingProviderIdentityResolved) {
                AivaV2Models.BookingPatch providerPatch = new AivaV2Models.BookingPatch(
                        AivaV2Models.ValuePatch.set(providerEvidence), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
                decision = new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                        AivaV2Models.DialogAct.PROVIDE_INFORMATION,
                        activeBooking == null ? AivaV2Models.Operation.START_BOOKING : AivaV2Models.Operation.UPDATE_BOOKING,
                        providerPatch, null, AivaV2Models.ConfirmationPolarity.NONE,
                        AivaV2Models.TopicAction.CONTINUE, turn.responseLanguage(), 1d,
                        "DETERMINISTIC_CANONICAL_PROVIDER_CLARIFICATION", false, 0L, null, false);
            }
            boolean gatewayConsulted = decision == null;
            if (gatewayConsulted) decision = decisionGateway.decide(turn, decisionContext, context);
            if (awaitSlotContext && !isAllowedAwaitSlotDecision(decision, turn,
                    slotContextDecision != null || providerSwitchDecision != null)) {
                String prompt = slotContinuationClarification(turn);
                outcome.set(new GuestPreparationResult(prompt, "CLARIFY_SLOT_CONTEXT_ACTION",
                        Map.of("mutationAllowed", false, "contextPreserved", true,
                                "rejectedOperation", decision == null ? "NONE" : decision.operation().name())));
                return current;
            }
            if (pendingShowMoreResolved && decision.operation() == AivaV2Models.Operation.SHOW_MORE_SLOTS) {
                decisionContext = decisionContext.withVoiceClarificationState(null);
            }
            logBookingFlow("DECISION_OUTPUT", context, decisionContext, turn, decision,
                    providerStateSource(decisionContext));
            ActiveProviderBinding providerBinding = bindActiveProvider(decisionContext, turn, decision, context);
            decisionContext = providerBinding.session();
            decision = providerBinding.decision();
            if (decision.operation() == AivaV2Models.Operation.UNKNOWN
                    && turn.intent() == NormalizedUserTurn.Intent.BOOKING) {
                decision = withOperation(decision, AivaV2Models.Operation.START_BOOKING);
            } else if (decision.operation() == AivaV2Models.Operation.UNKNOWN
                    && decisionContext.activeDraft() != null
                    && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                    && turn.temporal().localDate() != null) {
                decision = withOperation(decision, AivaV2Models.Operation.UPDATE_BOOKING);
            }
            if (decision.operation() == AivaV2Models.Operation.END_CONVERSATION
                    || decision.operation() == AivaV2Models.Operation.LOOKUP_APPOINTMENTS
                    || decision.operation() == AivaV2Models.Operation.NEXT_UPCOMING_APPOINTMENT
                    || decision.operation() == AivaV2Models.Operation.CANCEL_APPOINTMENT
                    || decision.operation() == AivaV2Models.Operation.RESCHEDULE_APPOINTMENT) {
                String response = switch (decision.operation()) {
                    case LOOKUP_APPOINTMENTS, NEXT_UPCOMING_APPOINTMENT ->
                            unsupportedScopeResponse(turn, "PATIENT_DATA_NOT_SUPPORTED");
                    case CANCEL_APPOINTMENT -> unsupportedScopeResponse(turn, "CANCEL_NOT_SUPPORTED");
                    case RESCHEDULE_APPOINTMENT -> unsupportedScopeResponse(turn, "RESCHEDULE_NOT_SUPPORTED");
                    default -> localizedNewBookingOnlyResponse(turn);
                };
                outcome.set(new GuestPreparationResult(response,
                        "OPERATION_NOT_BOOK", Map.of("mutationAllowed", false)));
                return current;
            }
            AivaV2TemporalResolution temporal = isPendingDaypartAnswer(turn, decisionContext)
                    ? AivaV2TemporalResolution.unchanged(decisionContext.activeDraft().preferredDate(), null)
                    : voiceTemporal(turn, decisionContext.activeDraft(), context.timezone());
            if (decision.operation() == AivaV2Models.Operation.UPDATE_BOOKING
                    && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED
                    && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                    && temporal.localDate() != null && decision.bookingPatch().dateExpression().mode()
                    == AivaV2Models.PatchMode.UNCHANGED) {
                decision = withBookingDate(decision, temporal.localDate().toString());
            }
            String logicalContext = current.voiceClarificationState() != null ? "PENDING_CLARIFICATION"
                    : pendingSwitch != null ? "PENDING_PROVIDER_SWITCH"
                    : awaitSlotContext ? "AWAIT_SLOT"
                    : collectionState != AivaV2VoiceCollectionContext.State.NONE
                    ? collectionState.name()
                    : decisionContext.activeDraft() == null ? "AWAIT_PROVIDER" : "BOOKING_CONTEXT";
            boolean semanticAiInvoked = gatewayConsulted && decision.fallbackUsed();
            String decisionSource = !gatewayConsulted ? "DETERMINISTIC_CONTEXT"
                    : semanticAiInvoked ? "BOUNDED_AI_PROPOSAL" : "GATEWAY_DETERMINISTIC";
            if (awaitSlotContext) {
                AvailabilityResult availability = decisionContext.latestAvailabilityResult();
                AivaV2Models.BookingDraft draft = decisionContext.activeDraft();
                int nextOffset = decision.operation() == AivaV2Models.Operation.SHOW_MORE_SLOTS
                        && availability != null
                        ? Math.min(availability.slots().size(), availability.displayOffset() + availability.pageSize())
                        : availability == null ? 0 : availability.displayOffset();
                log.info("AIVA_V2_SLOT_CONTEXT state=AWAIT_SLOT action={} source={} availabilityId={} "
                                + "displayOffset={}->{} providerId={} date={} aiInvoked=false",
                        decision.operation(), decision.provider(), availability == null ? null : availability.requestId(),
                        availability == null ? 0 : availability.displayOffset(), nextOffset,
                        draft == null || draft.selectedProvider() == null ? null : draft.selectedProvider().doctorId(),
                        draft == null ? null : draft.preferredDate());
            }
            logBookingFlow("KERNEL_INPUT", context, decisionContext, turn, decision,
                    providerStateSource(decisionContext));
            AvailabilityResult retainedDisplayedAvailability = decision.operation() == AivaV2Models.Operation.SELECT_SLOT
                    ? decisionContext.latestAvailabilityResult() : null;
            AivaV2TransactionalExecutionBoundary.Result prepared = transactionalBoundary.prepareAuthorizedVoiceBookingOnly(
                    context, decisionContext, decision, temporal, context.timezone(), turnId,
                    false, policy, bookingContext);
            if (prepared.kernelResult() == null) {
                outcome.set(GuestPreparationResult.denied(prepared.authorization().denialReason()));
                return current;
            }
            AivaV2TransactionalKernel.KernelResult kernelResult = prepared.kernelResult();
            SessionProjection updated = kernelResult.session();
            updated = retainDisplayedChoicesForPreparedSelection(updated, retainedDisplayedAvailability);
            if ("NO_MORE_SLOTS".equals(kernelResult.response().responseCategory())
                    && decision.operation() == AivaV2Models.Operation.SHOW_MORE_SLOTS) {
                VoiceClarificationState searchDate = new VoiceClarificationState(
                        VoiceClarificationKind.MISSING_INFO, 1, VoiceUnresolvedReason.MISSING_REQUIRED_INFO,
                        List.of("search_next_date", "keep_current_date"), false,
                        "SEARCH_NEXT_DATE", updated.activeWorkflow());
                updated = updated.withVoiceClarificationState(searchDate);
                outcome.set(new GuestPreparationResult(slotExhaustedPrompt(turn), "NO_MORE_SLOTS",
                        Map.of("mutationAllowed", false, "pendingAction", "SEARCH_NEXT_DATE",
                                "contextPreserved", true, "availabilityReloaded", false)));
                return updated;
            }
            logBookingFlow("KERNEL_OUTPUT", context, updated, turn, decision,
                    providerStateSource(updated));
            AivaV2Models.BookingDraft draft = updated.activeDraft();
            if (draft != null && draft.selectedProvider() != null) {
                updated = updated.withActiveVoiceDoctor(draft.selectedProvider());
            }
            String response = guestPreparationResponse(kernelResult, decision, turn);
            String result = kernelResult.response().responseCategory();
            if ("DAYPART_SELECTION".equals(result) && updated.latestAvailabilityResult() != null) {
                response = daypartChoiceClarification(updated.latestAvailabilityResult(), turn);
            }
            if (draft != null && draft.status() == AivaV2Models.DraftStatus.PREPARED
                    && StringUtils.hasText(draft.selectedSlotReference())) {
                AivaV2Models.AvailabilityResult availability = updated.latestAvailabilityResult();
                String binding = voiceBookingExecutionService.confirmationBinding(bookingContext, draft, availability);
                Instant expiry = Instant.now().plusSeconds(120);
                AivaV2Models.BookingConfirmation confirmation = new AivaV2Models.BookingConfirmation(binding,
                        draft.draftId(), draft.revision(), draft.selectedProvider().providerHandle(),
                        draft.selectedProvider().doctorId(), draft.selectedProvider().clinicId(), availability.requestId(),
                        draft.selectedSlotReference(), draft.preferredDate(), draft.exactTime(), expiry, binding);
                updated = updated.withPendingBookingConfirmation(confirmation, draft);
                response = bookingConfirmationPrompt(draft, turn);
                result = "BOOKING_CONFIRMATION_PENDING";
            }
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("bookingEnabled", policy.bookingEnabled());
            diagnostics.put("providerResolved", draft != null && draft.selectedProvider() != null);
            diagnostics.put("effectiveDate", draft == null ? null : draft.preferredDate());
            diagnostics.put("requestedTimeConstraint", draft == null ? null : draft.availabilityTimeConstraint());
            diagnostics.put("canonicalSlotSelected", draft != null && StringUtils.hasText(draft.selectedSlotReference()));
            diagnostics.put("draftPrepared", draft != null && draft.status() == AivaV2Models.DraftStatus.PREPARED);
            diagnostics.put("finalConfirmationPending", updated.pendingConfirmation() != null);
            diagnostics.put("mutationAllowed", false);
            diagnostics.put("executionDecision", "AWAITING_FINAL_CONFIRMATION");
            diagnostics.put("decisionContext", logicalContext);
            diagnostics.put("decisionAction", decision.operation().name());
            diagnostics.put("decisionSource", decisionSource);
            diagnostics.put("semanticAiInvoked", semanticAiInvoked);
            diagnostics.entrySet().removeIf(entry -> entry.getValue() == null);
            outcome.set(new GuestPreparationResult(response, result, Map.copyOf(diagnostics)));
            return updated;
        });
        } catch (RuntimeException sessionFailure) {
            GuestPreparationResult completed = outcome.get();
            if (completed != null && "BOOKING_CONFIRMED".equals(completed.result())
                    && completed.completedBookingOutcome() != null) {
                log.error("VOICE_BOOKING_SESSION_FINALIZATION_FAILED executionIdPresent={} appointmentCorrelationPresent={}",
                        StringUtils.hasText(completed.completedBookingOutcome().executionReference()),
                        completed.completedBookingOutcome().appointmentId() != null);
            }
            throw sessionFailure;
        }
        GuestPreparationResult resolved = outcome.get() == null
                ? GuestPreparationResult.denied("BOOKING_PREPARATION_FAILED") : outcome.get();
        return withAuthoritativeDecisionTrace(context, resolved);
    }

    private GuestPreparationResult withAuthoritativeDecisionTrace(AivaExecutionContext context,
            GuestPreparationResult result) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(result.diagnostics() == null
                ? Map.of() : result.diagnostics());
        diagnostics.putIfAbsent("acceptedDecisionCount", 1);
        diagnostics.putIfAbsent("decisionOwner", "AIVA_V2_VOICE_ADAPTER");
        String resultName = result.result() == null ? "UNKNOWN" : result.result();
        String decisionContext = String.valueOf(diagnostics.getOrDefault("decisionContext",
                diagnostics.getOrDefault("state", resultName.startsWith("PROVIDER_SWITCH")
                        ? "PENDING_PROVIDER_SWITCH" : resultName.startsWith("PROVIDER_IDENTITY")
                        ? "PENDING_CLARIFICATION" : resultName.contains("SLOT") ? "AWAIT_SLOT" : "BOOKING_CONTEXT")));
        String action = String.valueOf(diagnostics.getOrDefault("decisionAction",
                diagnostics.getOrDefault("pendingAction", resultName)));
        String source = String.valueOf(diagnostics.getOrDefault("decisionSource", "DETERMINISTIC_CONTEXT"));
        boolean aiInvoked = Boolean.TRUE.equals(diagnostics.get("semanticAiInvoked"));
        diagnostics.putIfAbsent("decisionContext", decisionContext);
        diagnostics.putIfAbsent("decisionAction", action);
        diagnostics.putIfAbsent("decisionSource", source);
        diagnostics.putIfAbsent("semanticAiInvoked", aiInvoked);
        log.info("VOICE_CONVERSATION_DECISION context={} action={} source={} operation={} aiInvoked={} result={}",
                decisionContext, action, source, action, aiInvoked, resultName);
        SessionProjection session = context == null ? null : sessionStore.find(sessionKey(context)).orElse(null);
        AivaV2VoiceResponseRenderer.Intent intent = voiceResponseIntent(session, result);
        String rendered = intent.type() == AivaV2VoiceResponseRenderer.Type.COMPATIBILITY
                ? localizedCompatibilityResponse(result.response(), intent.presentationMode())
                : responseRenderer.render(intent);
        diagnostics.put("responseIntent", intent.type().name());
        diagnostics.put("presentationMode", intent.presentationMode().name());
        diagnostics.put("ttsLanguage", ttsLanguage(intent.presentationMode()));
        diagnostics.put("ttsTextLanguageProfile", intent.presentationMode().name());
        diagnostics.put("responseLength", rendered == null ? 0 : rendered.length());
        diagnostics.put("providerPresent", StringUtils.hasText(intent.providerName()));
        diagnostics.put("datePresent", intent.date() != null);
        diagnostics.put("slotPresent", intent.time() != null || !intent.slots().isEmpty());
        String errorCategory = switch (intent.type()) {
            case PROVIDER_CLARIFICATION -> "VOICE_PROVIDER_AMBIGUOUS";
            case BOOKING_STALE -> "VOICE_BOOKING_STALE";
            case BOOKING_FAILED -> "VOICE_BOOKING_FAILED";
            case BOOKING_STATUS_UNKNOWN -> "VOICE_BOOKING_STATUS_UNKNOWN";
            case BOOKING_SUCCESS, COMPLETED_BOOKING_REPEAT -> "VOICE_RECONCILIATION_SUCCESS";
            default -> null;
        };
        if (resultName.contains("CONFIRMATION_STALE")) errorCategory = "VOICE_CONFIRMATION_BINDING_INVALID";
        else if (resultName.startsWith("CLARIFY")) errorCategory = "VOICE_RECOGNITION_AMBIGUOUS";
        if (diagnostics.containsKey("rejectedOperation")) errorCategory = "VOICE_CONTEXT_PROPOSAL_REJECTED";
        if (errorCategory != null) diagnostics.put("errorCategory", errorCategory);
        return new GuestPreparationResult(rendered, result.result(), Map.copyOf(diagnostics),
                result.completedBookingOutcome());
    }

    private String localizedCompatibilityResponse(String response, AivaV2Models.VoicePresentationMode mode) {
        if (response == null || mode == null || mode == AivaV2Models.VoicePresentationMode.ENGLISH) return response;
        if (response.equalsIgnoreCase("Select one of the current slots.")
                || response.equalsIgnoreCase("Please select one of the current slots.")) {
            return mode == AivaV2Models.VoicePresentationMode.HINGLISH
                    ? "Dikhaaye gaye slots mein se ek choose kijiye."
                    : "दिखाए गए समय में से कोई एक चुनिए।";
        }
        return response;
    }

    private AivaV2VoiceResponseRenderer.Intent voiceResponseIntent(SessionProjection session,
            GuestPreparationResult result) {
        AivaV2Models.VoicePresentationMode mode = session == null || session.presentationMode() == null
                ? AivaV2Models.VoicePresentationMode.HINDI : session.presentationMode();
        AivaV2Models.BookingDraft draft = session == null ? null : session.activeDraft();
        AivaV2Models.AvailabilityResult availability = session == null ? null : session.latestAvailabilityResult();
        String status = result.result() == null ? "" : result.result();
        String provider = draft != null && draft.selectedProvider() != null
                ? draft.selectedProvider().displayName()
                : session != null && session.activeVoiceDoctor() != null ? session.activeVoiceDoctor().displayName() : null;
        LocalDate date = draft != null && draft.preferredDate() != null ? draft.preferredDate()
                : availability == null ? null : availability.date();
        LocalTime time = draft == null ? null : draft.exactTime();
        if (time == null && draft != null && availability != null && StringUtils.hasText(draft.selectedSlotReference())) {
            time = availability.slots().stream().filter(slot -> draft.selectedSlotReference().equals(slot.slotReference()))
                    .map(AivaV2Models.AvailabilitySlot::startsAt).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        List<LocalTime> slots = List.of();
        List<String> dayparts = List.of();
        if (availability != null) {
            int start = Math.min(availability.displayOffset(), availability.slots().size());
            int end = Math.min(availability.slots().size(), start + availability.pageSize());
            slots = availability.slots().subList(start, end).stream()
                    .map(AivaV2Models.AvailabilitySlot::startsAt).filter(java.util.Objects::nonNull).toList();
            dayparts = availability.slots().stream().map(AivaV2Models.AvailabilitySlot::startsAt)
                    .filter(java.util.Objects::nonNull).map(slot -> {
                        String english = slot.isBefore(LocalTime.NOON) ? "morning"
                                : slot.isBefore(LocalTime.of(17, 0)) ? "afternoon" : "evening";
                        if (mode != AivaV2Models.VoicePresentationMode.HINDI) return english;
                        return switch (english) {
                            case "morning" -> "सुबह";
                            case "afternoon" -> "दोपहर";
                            default -> "शाम";
                        };
                    }).distinct().limit(3).toList();
        }
        AivaV2VoiceResponseRenderer.Type type = responseType(status, session);
        List<String> candidates = List.of();
        String target = null;
        if (session != null && session.voiceClarificationState() != null) {
            VoiceClarificationState clarification = session.voiceClarificationState();
            candidates = clarification.canonicalProviderCandidates().stream()
                    .map(VoiceProviderIdentityCandidate::displayName).toList();
            if (clarification.pendingProviderSwitch() != null) {
                target = clarification.pendingProviderSwitch().targetDisplayName();
            }
        }
        CompletedBookingOutcome completed = result.completedBookingOutcome() != null
                ? result.completedBookingOutcome() : session == null ? null : session.completedBookingOutcome();
        String bookingReference = completed == null ? null : completed.bookingReference();
        if (completed != null && (type == AivaV2VoiceResponseRenderer.Type.BOOKING_SUCCESS
                || type == AivaV2VoiceResponseRenderer.Type.COMPLETED_BOOKING_REPEAT)) {
            provider = completed.providerDisplayName();
            date = completed.date();
            time = completed.time();
        }
        return new AivaV2VoiceResponseRenderer.Intent(type, mode, provider, target, candidates, date, time,
                slots, dayparts, bookingReference, result.response());
    }

    private AivaV2VoiceResponseRenderer.Type responseType(String result, SessionProjection session) {
        if (result.startsWith("PROVIDER_SWITCH_CONFIRMATION")) return AivaV2VoiceResponseRenderer.Type.PROVIDER_SWITCH_CONFIRMATION;
        if (result.startsWith("PROVIDER_IDENTITY_AMBIGUOUS") || result.startsWith("PROVIDER_IDENTITY_CLARIFICATION"))
            return AivaV2VoiceResponseRenderer.Type.PROVIDER_CLARIFICATION;
        if (result.equals("BOOKING_CONFIRMATION_PENDING")) return AivaV2VoiceResponseRenderer.Type.BOOKING_CONFIRMATION;
        if (result.equals("BOOKING_CONFIRMED")) return AivaV2VoiceResponseRenderer.Type.BOOKING_SUCCESS;
        if (result.equals("COMPLETED_BOOKING_REPEAT")) return AivaV2VoiceResponseRenderer.Type.COMPLETED_BOOKING_REPEAT;
        if (result.contains("STALE_SLOT") || result.contains("CONFIRMATION_STALE")) return AivaV2VoiceResponseRenderer.Type.BOOKING_STALE;
        if (result.contains("STATUS_CHECK") || result.contains("STATUS_UNKNOWN")) return AivaV2VoiceResponseRenderer.Type.BOOKING_STATUS_UNKNOWN;
        if (result.equals("BOOKING_FAILED")) return AivaV2VoiceResponseRenderer.Type.BOOKING_FAILED;
        if (result.equals("BOOKING_CONFIRMATION_DECLINED")) return AivaV2VoiceResponseRenderer.Type.BOOKING_DECLINED;
        if (result.equals("NO_MORE_SLOTS")) return AivaV2VoiceResponseRenderer.Type.SLOT_EXHAUSTED;
        if (result.equals("SLOT_CHOICES")) return AivaV2VoiceResponseRenderer.Type.SLOT_CHOICES;
        if (result.equals("DAYPART_SELECTION") || result.equals("COLLECTION_DAYPART_CLARIFICATION"))
            return AivaV2VoiceResponseRenderer.Type.DAYPART_CLARIFICATION;
        if (result.equals("NEED_DATE") || result.startsWith("COLLECTION_DATE") || result.startsWith("DATE_CLARIFICATION"))
            return AivaV2VoiceResponseRenderer.Type.DATE_CLARIFICATION;
        if (result.startsWith("CLARIFY_SLOT") || result.equals("CLARIFICATION_PENDING"))
            return AivaV2VoiceResponseRenderer.Type.CONTEXTUAL_CLARIFICATION;
        if (result.startsWith("CLARIFY") && session != null) {
            if (session.voiceClarificationState() != null
                    && !session.voiceClarificationState().canonicalProviderCandidates().isEmpty())
                return AivaV2VoiceResponseRenderer.Type.PROVIDER_CLARIFICATION;
            if (session.activeDraft() != null && session.activeDraft().preferredDate() == null)
                return AivaV2VoiceResponseRenderer.Type.DATE_CLARIFICATION;
            if (session.latestAvailabilityResult() != null && !session.latestAvailabilityResult().slots().isEmpty())
                return AivaV2VoiceResponseRenderer.Type.SLOT_CHOICES;
            return AivaV2VoiceResponseRenderer.Type.CONTEXTUAL_CLARIFICATION;
        }
        if ((result.equals("MISSING_REQUIRED_INFO") || result.equals("NEED_DATE"))
                && session != null && session.activeDraft() != null
                && session.activeDraft().preferredDate() == null)
            return AivaV2VoiceResponseRenderer.Type.DATE_CLARIFICATION;
        return AivaV2VoiceResponseRenderer.Type.COMPATIBILITY;
    }

    private String ttsLanguage(AivaV2Models.VoicePresentationMode mode) {
        return mode == AivaV2Models.VoicePresentationMode.ENGLISH ? "en-IN" : "hi-IN";
    }

    private SessionProjection retainDisplayedChoicesForPreparedSelection(SessionProjection session,
            AvailabilityResult retainedAvailability) {
        AivaV2Models.BookingDraft draft = session == null ? null : session.activeDraft();
        if (draft == null || draft.status() != AivaV2Models.DraftStatus.PREPARED
                || !StringUtils.hasText(draft.selectedSlotReference()) || retainedAvailability == null
                || retainedAvailability.expiresAt() == null || !retainedAvailability.expiresAt().isAfter(Instant.now())
                || !java.util.Objects.equals(draft.draftId(), retainedAvailability.draftId())
                || draft.revision() != retainedAvailability.draftRevision()
                || !java.util.Objects.equals(draft.preferredDate(), retainedAvailability.date())
                || draft.selectedProvider() == null
                || !java.util.Objects.equals(draft.selectedProvider().doctorId(), retainedAvailability.doctorId())
                || !java.util.Objects.equals(draft.selectedProvider().clinicId(), retainedAvailability.clinicId())
                || !java.util.Objects.equals(draft.selectedProvider().providerHandle(), retainedAvailability.providerHandle())
                || retainedAvailability.slots().stream().noneMatch(slot -> draft.selectedSlotReference()
                        .equals(slot.slotReference()))) return session;
        AivaV2Models.BookingDraft retained = new AivaV2Models.BookingDraft(draft.draftId(), draft.patientSubjectId(),
                draft.tenantScope(), draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(),
                draft.preferredTimeWindow(), draft.exactTime(), draft.availabilityTimeConstraint(),
                retainedAvailability.requestId(), draft.selectedSlotReference(), draft.status(), draft.revision(),
                draft.expiresAt(), draft.confirmedAppointmentReference());
        log.info("AIVA_V2_CONFIRMATION_TRACE state=AWAIT_CONFIRMATION action=SELECT_SLOT "+
                "retainedAvailabilityId={} selectedSlotPresent=true canonicalRevalidationPassed=true",
                retainedAvailability.requestId());
        return session.withBookingDraft(retained).withVoiceAvailabilityResult(retainedAvailability);
    }

    private SessionProjection completedBookingSession(SessionProjection session, CompletedBookingOutcome completed,
            ProviderCandidate provider) {
        if (completed == null) return session.clearTransientWorkflow();
        return session.withCompletedBookingOutcome(completed).clearTransientWorkflow().withActiveVoiceDoctor(provider);
    }

    private GuestPreparationResult bookingConfirmedResult(AppointmentRecord booked,
            AivaV2Models.BookingDraft draft, AivaV2Models.BookingConfirmation confirmation,
            BookingExecutionContext bookingContext, NormalizedUserTurn turn, boolean reconciled) {
        String spokenDate = AivaSpokenDateFormatter.format(draft.preferredDate(), turn.responseLanguage(), turn.responseStyle());
        boolean hindi = turn.responseLanguage() != null
                && turn.responseLanguage().toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        String spokenTime = spokenTime(draft.exactTime(), hindi, hinglish);
        String providerName = draft.selectedProvider().displayName();
        String confirmationResponse = hindi && !hinglish
                ? "डॉ. " + providerName + " के साथ " + spokenDate + " को " + spokenTime
                    + " का appointment बुक हो गया है।"
                : hinglish ? "Dr. " + providerName + " ke saath " + spokenDate
                    + " ko " + spokenTime + " appointment book ho gaya hai."
                : "Your appointment with Dr. " + providerName + " on " + spokenDate
                    + " at " + spokenTime + " is booked.";
        if (StringUtils.hasText(booked.displayReference())) confirmationResponse += " Reference: " + booked.displayReference() + ".";
        CompletedBookingOutcome completed = new CompletedBookingOutcome(booked.id(), booked.displayReference(),
                providerName, draft.preferredDate(), draft.exactTime(), Instant.now(), bookingContext.tenantId(),
                bookingContext.patientId(), bookingContext.providerCallId(), confirmation.idempotencyKey(),
                "VOICE", "CONFIRMED");
        if (!reconciled) {
            log.info("VOICE_BOOKING_DURABLE_SUCCESS executionIdPresent=true appointmentCorrelation={}", booked.id());
        }
        return new GuestPreparationResult(confirmationResponse, "BOOKING_CONFIRMED",
                Map.of("mutationAllowed", !reconciled, "executionDecision", reconciled ? "RECONCILED" : "BOOKED",
                        "appointmentId", booked.id().toString(), "source", reconciled ? "IDEMPOTENCY_RECEIPT" : "VOICE_EXECUTION"),
                completed);
    }

    private GuestPreparationResult consumeVoiceBookingConfirmation(SessionProjection session, NormalizedUserTurn turn,
            BookingExecutionContext bookingContext, VoiceTransactionAuthPolicy policy) {
        AivaV2Models.BookingConfirmation confirmation = session.pendingConfirmation();
        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (!AivaV2VoiceConfirmationContext.isAwaitConfirmation(session, bookingContext,
                voiceBookingExecutionService, Instant.now())) {
            return new GuestPreparationResult(localizedStaleResponse(turn),
                    "BOOKING_CONFIRMATION_STALE", Map.of("mutationAllowed", false));
        }
        String binding;
        try { binding = voiceBookingExecutionService.confirmationBinding(bookingContext, draft, session.latestAvailabilityResult()); }
        catch (RuntimeException invalid) {
            return new GuestPreparationResult(localizedStaleResponse(turn),
                    "BOOKING_CONFIRMATION_STALE", Map.of("mutationAllowed", false));
        }
        if (!binding.equals(confirmation.confirmationRef())) {
            return new GuestPreparationResult(localizedStaleResponse(turn),
                    "BOOKING_CONFIRMATION_STALE", Map.of("mutationAllowed", false));
        }
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
            return new GuestPreparationResult(localizedDeclinedResponse(turn),
                    "BOOKING_CONFIRMATION_DECLINED", Map.of("mutationAllowed", false));
        }
        if (turn.confirmation() != NormalizedUserTurn.Confirmation.POSITIVE) {
            return new GuestPreparationResult(bookingConfirmationPrompt(draft, turn),
                    "BOOKING_CONFIRMATION_PENDING", Map.of("mutationAllowed", false));
        }
        if (!policy.bookingExecutionAllowed()) {
            return new GuestPreparationResult(localizedBookingDisabledResponse(turn),
                    "BOOKING_DISABLED", Map.of("mutationAllowed", false));
        }
        log.info("VOICE_BOOKING_EXECUTION stage=FINAL_CONFIRMATION_RECEIVED tenantId={} providerCallId={} "
                        + "interactionId={} draftId={} draftRevision={} providerPresent={} datePresent={} "
                        + "canonicalSlotReferencePresent={} draftPrepared={} authorizationSource={}",
                bookingContext.tenantId(), bookingContext.providerCallId(), bookingContext.interactionId(),
                draft.draftId(), draft.revision(), draft.selectedProvider() != null, draft.preferredDate() != null,
                StringUtils.hasText(draft.selectedSlotReference()), draft.status() == AivaV2Models.DraftStatus.PREPARED,
                bookingContext.authorizationSource());
        try {
            AppointmentRecord booked = voiceBookingExecutionService.book(bookingContext, draft,
                    session.latestAvailabilityResult(), confirmation.idempotencyKey(), binding);
            return bookingConfirmedResult(booked, draft, confirmation, bookingContext, turn, false);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 409) {
                return new GuestPreparationResult(localizedStaleResponse(turn),
                        "STALE_SLOT", Map.of("mutationAllowed", false, "executionDecision", "STALE_SLOT"));
            }
            return new GuestPreparationResult(localizedBookingFailedResponse(turn),
                    "BOOKING_FAILED", Map.of("mutationAllowed", false, "executionDecision", "FAILED"));
        } catch (RuntimeException ex) {
            log.warn("VOICE_BOOKING_EXECUTION_FAILED callId={} reason={}", bookingContext.providerCallId(),
                    safeFailureReason(ex));
            return new GuestPreparationResult(localizedBookingFailedResponse(turn),
                    "BOOKING_FAILED", Map.of("mutationAllowed", false, "executionDecision", "FAILED"));
        }
    }

    private String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) return exception.getClass().getSimpleName();
        return switch (message) {
            case "Canonical booking target is missing", "Canonical provider identity is invalid",
                    "Selected slot is not in the current canonical availability result",
                    "Prepared canonical booking state is incomplete",
                    "Booking execution context does not authorize this patient and tenant",
                    "Restricted unverified Voice booking actor scope is invalid",
                    "This time is no longer available. Please choose another slot." -> message;
            default -> exception.getClass().getSimpleName();
        };
    }

    private String localizedStaleResponse(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish ? "Ye slot ab available nahi hai. Please koi doosra time choose kijiye."
                : "यह स्लॉट अब उपलब्ध नहीं है। कृपया कोई दूसरा समय चुनें।"
                : "That slot is no longer available. Please choose another time.";
    }

    private String localizedBookingStatusChecking(NormalizedUserTurn turn) {
        if (isEnglish(turn)) return "I’m checking the booking result safely. Please try again in a moment; I will not submit another booking while its status is unknown.";
        if (isHinglish(turn)) return "Main booking ka status safely check kar raha hoon. Thodi der baad phir batayein; status clear hone tak main dobara booking submit nahi karunga.";
        return "मैं booking का परिणाम सुरक्षित रूप से जाँच रहा हूँ। कृपया थोड़ी देर बाद फिर बताइए; स्थिति स्पष्ट होने तक मैं दोबारा booking submit नहीं करूँगा।";
    }

    private String localizedDeclinedResponse(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish
                ? "Theek hai. Appointment book nahi hui. Aap diye gaye slots mein se doosra time chun sakte hain."
                : "ठीक है। कोई appointment book नहीं हुई। आप दिए गए slots में से दूसरा समय चुन सकते हैं।"
                : "Understood. No appointment was booked. You can choose another displayed slot.";
    }

    private String localizedBookingFailedResponse(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish
                ? "Booking abhi confirm nahi ho saki. Main ise dobara submit nahi karunga jab tak status verify na ho."
                : "बुकिंग की पुष्टि अभी नहीं हो सकी। स्थिति जाँचे बिना इसे दोबारा submit नहीं किया जाएगा।"
                : "I could not confirm the booking. I will not resubmit until its status is checked.";
    }

    private String localizedBookingDisabledResponse(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        return hindi ? turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH
                ? "Voice booking abhi enabled nahi hai. Appointment book nahi hui."
                : "Voice में appointment booking अभी सक्षम नहीं है। कोई appointment book नहीं हुई।"
                : "Voice booking is currently disabled. No appointment was booked.";
    }

    private AivaV2Models.BookingDraft preparedDraft(AivaV2Models.BookingDraft draft) {
        if (draft == null) return null;
        return new AivaV2Models.BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(),
                draft.selectedProvider(), draft.specialtyFilter(), draft.preferredDate(), draft.preferredTimeWindow(),
                draft.exactTime(), draft.availabilityTimeConstraint(), draft.latestAvailabilityRequestId(),
                draft.selectedSlotReference(), AivaV2Models.DraftStatus.PREPARED, draft.revision(), draft.expiresAt(), null);
    }

    private String bookingConfirmationPrompt(AivaV2Models.BookingDraft draft, NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        String date = AivaSpokenDateFormatter.format(draft.preferredDate(), turn.responseLanguage(), turn.responseStyle());
        String time = spokenTime(draft.exactTime(), hindi, hinglish);
        String doctor = draft.selectedProvider().displayName();
        if (hindi && !hinglish) return "डॉ. " + doctor + ", " + date + " को " + time
                + "। क्या मैं यह appointment book कर दूँ?";
        if (hinglish) return "Dr. " + doctor + ", " + date + " ko " + time
                + ". Kya main ye appointment book kar doon?";
        return "Dr. " + doctor + ", " + date + " at " + time + ". Would you like me to book this appointment?";
    }

    private ConfirmationTurnResolution resolveConfirmationTurn(SessionProjection session, NormalizedUserTurn turn) {
        if (isClosing(turn)) return new ConfirmationTurnResolution(ConfirmationTurnAction.END, turn, null);
        if (turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE)) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.REPEAT, turn, null);
        }
        if (turn.controls().contains(DeterministicControl.HELP)) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.HELP, turn, null);
        }
        if (turn.controls().contains(DeterministicControl.ABANDON_WORKFLOW)) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.ABANDON, turn, null);
        }

        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (turn.temporal() != null && (turn.temporal().status() == NormalizedUserTurn.TemporalStatus.AMBIGUOUS
                || turn.temporal().status() == NormalizedUserTurn.TemporalStatus.INVALID)) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CLARIFY, turn, null);
        }
        if (turn.temporal() != null && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                && turn.temporal().localDate() != null && !turn.temporal().localDate().equals(draft.preferredDate())) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_DATE, turn,
                    slotDateDecision(turn, turn.temporal().localDate()));
        }
        if (AivaV2VoiceSlotContext.explicitlySearchesNextDate(turn.rawText())) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_DATE, turn,
                    slotDecision(turn, AivaV2Models.Operation.FIND_NEXT_AVAILABLE_SLOT, null));
        }
        if (turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_SLOT, turn,
                    slotDecision(turn, AivaV2Models.Operation.SHOW_MORE_SLOTS, null));
        }

        AivaV2DisplayedSlotSelectionResolver.Resolution displayedSelection = displayedSlotSelectionResolver
                .resolve(turn, session.latestAvailabilityResult());
        if (displayedSelection.recognized()) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_SLOT, turn,
                    slotDecision(turn, AivaV2Models.Operation.SELECT_SLOT, displayedSelection.selection()));
        }
        if (turn.daypart() != null) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_TIME, turn,
                    slotDaypartDecision(turn, turn.daypart()));
        }
        if (hasTimeRangeEvidence(turn)) {
            ConversationDecision constraint = decisionGateway.deterministicCollectionTimeConstraint(turn, session);
            if (isAllowedCollectionTimeConstraint(constraint)) {
                return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_TIME, turn, constraint);
            }
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CLARIFY, turn, null);
        }
        if (turn.exactTime() != null) {
            AivaV2Models.BookingPatch patch = collectionPatch(turn, null, false, true, null);
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CHANGE_TIME, turn,
                    collectionDecision(turn, patch));
        }

        NormalizedUserTurn.Confirmation answer = AivaV2VoiceConfirmationContext.explicitAnswer(turn);
        if (answer == NormalizedUserTurn.Confirmation.POSITIVE) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.CONFIRM, turn, null);
        }
        if (answer == NormalizedUserTurn.Confirmation.NEGATIVE) {
            return new ConfirmationTurnResolution(ConfirmationTurnAction.DECLINE, turn, null);
        }
        return new ConfirmationTurnResolution(ConfirmationTurnAction.CLARIFY, turn, null);
    }

    private SessionProjection clearConfirmationForSlotDecision(SessionProjection session) {
        if (session == null || session.activeDraft() == null) return session;
        AivaV2Models.BookingDraft draft = session.activeDraft();
        AivaV2Models.BookingDraft collecting = new AivaV2Models.BookingDraft(draft.draftId(),
                draft.patientSubjectId(), draft.tenantScope(), draft.selectedProvider(), draft.specialtyFilter(),
                draft.preferredDate(), draft.preferredTimeWindow(), null, draft.availabilityTimeConstraint(),
                draft.latestAvailabilityRequestId(), null, AivaV2Models.DraftStatus.COLLECTING,
                draft.revision(), draft.expiresAt(), null);
        return session.withPendingBookingConfirmation(null, collecting);
    }

    private SessionProjection refreshAvailabilityAfterStaleConfirmation(AivaExecutionContext context,
            SessionProjection session, NormalizedUserTurn turn, BookingExecutionContext bookingContext,
            VoiceTransactionAuthPolicy policy) {
        if (session == null || session.activeDraft() == null || session.activeDraft().selectedProvider() == null
                || session.activeDraft().preferredDate() == null) return session;
        ConversationDecision refresh = new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.PROVIDE_INFORMATION, AivaV2Models.Operation.GET_AVAILABILITY,
                AivaV2Models.BookingPatch.empty(), null, AivaV2Models.ConfirmationPolarity.NONE,
                AivaV2Models.TopicAction.CONTINUE, turn.responseLanguage(), 1d,
                "STALE_CONFIRMATION_AVAILABILITY_REFRESH", false, 0L, null, false);
        AivaV2TemporalResolution temporal = AivaV2TemporalResolution.unchanged(
                session.activeDraft().preferredDate(), null);
        AivaV2TransactionalExecutionBoundary.Result refreshed = transactionalBoundary
                .prepareAuthorizedVoiceBookingOnly(context, session, refresh, temporal, context.timezone(),
                        AivaV2VoiceFlowTrace.turnId(), false, policy, bookingContext);
        return refreshed.kernelResult() == null ? session : refreshed.kernelResult().session();
    }

    private String confirmationHelp(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish
                ? "Aap 'haan book kar do' kehkar confirm kar sakte hain, ya date, time, doctor badal sakte hain."
                : "आप 'हाँ, बुक कर दो' कहकर पुष्टि कर सकते हैं, या तारीख, समय अथवा डॉक्टर बदल सकते हैं।"
                : "You can confirm, choose another displayed slot, or change the date, time, or doctor.";
    }

    private String confirmationEndResponse(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish ? "Abhi appointment book nahi hui. Aap baad mein continue kar sakte hain."
                : "अभी appointment book नहीं हुई है। आप बाद में यहीं से जारी रख सकते हैं।"
                : "The appointment has not been booked. You can continue from this confirmation later.";
    }

    /** Applies one guest Voice turn to the shared V2 booking draft without opening confirmation execution. */
    public GuestPreparationResult prepareGuestBookingTurn(AivaExecutionContext context, String transcript) {
        if (context == null || !StringUtils.hasText(transcript) || transactionalBoundary == null) {
            return GuestPreparationResult.denied("PREPARATION_BOUNDARY_UNAVAILABLE");
        }
        String key = sessionKey(context);
        String turnId = AivaV2VoiceFlowTrace.turnId();
        AtomicReference<GuestPreparationResult> outcome = new AtomicReference<>();
        sessionStore.update(key, current -> {
            if (current == null || current.closed() || current.voiceGuestRegistrationState() == null) {
                outcome.set(GuestPreparationResult.denied("GUEST_CAPABILITY_MISSING"));
                return current;
            }
            VoiceGuestRegistrationState guest = current.voiceGuestRegistrationState();
            NormalizedUserTurn turn = normalizeVoiceTurn(context, current, transcript);
            PresentationTurn presentation = applyPresentationMode(current, turn);
            current = presentation.session();
            turn = presentation.turn();
            SessionProjection decisionContext = current.patientId() == null
                    ? current.withPatientId(guest.patientId()) : current;
            if (decisionContext.activeDraft() == null) {
                decisionContext = decisionContext.withBookingDraft(AivaV2Models.BookingDraft.create(
                        guest.patientId(), decisionContext.tenantId(), Instant.now()));
            }
            turn = bookingContinuationTurn(turn, decisionContext.activeDraft(), context.timezone());
            decisionContext = adoptActiveVoiceDoctor(decisionContext, turn, context);
            logBookingFlow("DECISION_INPUT", context, decisionContext, turn, null,
                    providerStateSource(decisionContext));
            ConversationDecision decision = deterministicBookingContinuation(turn, decisionContext);
            if (decision == null) decision = decisionGateway.decide(turn, decisionContext, context);
            logBookingFlow("DECISION_OUTPUT", context, decisionContext, turn, decision,
                    providerStateSource(decisionContext));
            ActiveProviderBinding binding = bindActiveProvider(decisionContext, turn, decision, context);
            decisionContext = binding.session();
            decision = binding.decision();
            if (decision.operation() == AivaV2Models.Operation.UNKNOWN
                    && turn.intent() == NormalizedUserTurn.Intent.BOOKING) {
                decision = withOperation(decision, AivaV2Models.Operation.START_BOOKING);
            } else if (decision.operation() == AivaV2Models.Operation.UNKNOWN
                    && decisionContext.activeDraft() != null
                    && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                    && turn.temporal().localDate() != null) {
                decision = withOperation(decision, AivaV2Models.Operation.UPDATE_BOOKING);
            }
            AivaV2TemporalResolution temporal = isPendingDaypartAnswer(turn, decisionContext)
                    ? AivaV2TemporalResolution.unchanged(decisionContext.activeDraft().preferredDate(), null)
                    : voiceTemporal(turn, decisionContext.activeDraft(), context.timezone());
            if (decision.operation() == AivaV2Models.Operation.UPDATE_BOOKING
                    && temporal.status() == AivaV2TemporalResolution.Status.RESOLVED
                    && temporal.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT
                    && temporal.localDate() != null && decision.bookingPatch().dateExpression().mode()
                    == AivaV2Models.PatchMode.UNCHANGED) {
                decision = withBookingDate(decision, temporal.localDate().toString());
            }
            logBookingFlow("KERNEL_INPUT", context, decisionContext, turn, decision,
                    providerStateSource(decisionContext));
            AivaV2TransactionalExecutionBoundary.Result prepared = transactionalBoundary.prepareGuestBookingOnly(
                    context, decisionContext, guest, decision, temporal, context.timezone(), turnId, false);
            if (prepared.kernelResult() == null) {
                outcome.set(GuestPreparationResult.denied(prepared.authorization().denialReason()));
                return current;
            }
            AivaV2TransactionalKernel.KernelResult kernelResult = prepared.kernelResult();
            SessionProjection preparedSession = kernelResult.session();
            logBookingFlow("KERNEL_OUTPUT", context, preparedSession, turn, decision,
                    providerStateSource(preparedSession));
            AivaV2Models.BookingDraft draft = preparedSession.activeDraft();
            if (draft != null && draft.selectedProvider() != null) {
                preparedSession = preparedSession.withActiveVoiceDoctor(draft.selectedProvider());
            }
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("patientContextType", "GUEST_REGISTERED");
            diagnostics.put("providerResolved", draft != null && draft.selectedProvider() != null);
            diagnostics.put("effectiveDate", draft == null ? null : draft.preferredDate());
            diagnostics.put("requestedTimeConstraint", draft == null ? null : draft.availabilityTimeConstraint());
            diagnostics.put("availabilityRequestId", kernelResult.session().latestAvailabilityResult() == null
                    ? null : kernelResult.session().latestAvailabilityResult().requestId());
            diagnostics.put("availabilityFingerprint", kernelResult.session().latestAvailabilityResult() == null
                    ? null : kernelResult.session().latestAvailabilityResult().criteriaFingerprint());
            diagnostics.put("canonicalSlotSelected", draft != null && StringUtils.hasText(draft.selectedSlotReference()));
            diagnostics.put("draftPrepared", draft != null && draft.status() == AivaV2Models.DraftStatus.PREPARED);
            diagnostics.put("mutationAllowed", false);
            diagnostics.put("executionDecision", "BLOCKED");
            diagnostics.put("authorizationDecision", prepared.authorization().outcome().name());
            String response = "DAYPART_SELECTION".equals(kernelResult.response().responseCategory())
                    && preparedSession.latestAvailabilityResult() != null
                    ? daypartChoiceClarification(preparedSession.latestAvailabilityResult(), turn)
                    : guestPreparationResponse(kernelResult, decision, turn);
            outcome.set(new GuestPreparationResult(response,
                    kernelResult.response().responseCategory(), diagnostics));
            return preparedSession;
        });
        return outcome.get() == null ? GuestPreparationResult.denied("PREPARATION_FAILED") : outcome.get();
    }

    private ActiveProviderBinding bindActiveProvider(SessionProjection session, NormalizedUserTurn turn,
                                                       ConversationDecision decision, AivaExecutionContext context) {
        String explicitDoctor = turn.doctorEntity() != null ? turn.doctorEntity().canonicalQuery()
                : turn.explicitDoctorReference();
        if (StringUtils.hasText(explicitDoctor)
                && (isBookingProviderOperation(decision.operation())
                || turn.intent() == NormalizedUserTurn.Intent.BOOKING)) {
            AivaV2Models.BookingPatch patch = decision.bookingPatch();
            AivaV2Models.BookingPatch explicitProviderPatch = new AivaV2Models.BookingPatch(
                    AivaV2Models.ValuePatch.set(explicitDoctor), patch.specialtyText(), patch.dateExpression(),
                    patch.timeWindow(), patch.exactTime(), patch.clinicText(), patch.availabilityTimeConstraint());
            decision = new ConversationDecision(decision.schemaVersion(), decision.dialogAct(),
                    decision.operation(), explicitProviderPatch, decision.selection(), decision.confirmation(),
                    decision.topicAction(), decision.responseLanguage(), decision.confidence(), decision.provider(),
                    decision.fallbackUsed(), decision.latencyMs(), decision.lookupFilter(),
                    decision.unresolvedExplicitQualifierPresent());
        }
        ProviderCandidate active = session.activeVoiceDoctor();
        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (active == null || draft == null
                || (decision.confirmation() == AivaV2Models.ConfirmationPolarity.POSITIVE
                && session.latestProviderResult() != null
                && session.latestProviderResult().status() == AivaV2Models.ResolutionStatus.SUGGESTION)) {
            return new ActiveProviderBinding(session, decision);
        }
        if (isExplicitDifferentProvider(turn, active.displayName())) {
            return new ActiveProviderBinding(session, decision);
        }
        SessionProjection boundSession = draft.selectedProvider() == null
                || !draft.selectedProvider().doctorId().equals(active.doctorId())
                || draft.selectedProvider().source() != ProviderSource.CARE_PRIVATE
                ? adoptCanonicalBookingProvider(session, draft, active, context, "ACTIVE_DOCTOR") : session;
        // Keep current-turn provider evidence on the decision. START_BOOKING can
        // replace the existing draft in the kernel; stripping even an explicit
        // same-doctor patch would then lose the provider along with that draft.
        if (StringUtils.hasText(explicitDoctor)) {
            return new ActiveProviderBinding(boundSession, decision);
        }
        AivaV2Models.BookingPatch patch = decision.bookingPatch();
        AivaV2Models.BookingPatch withoutModelProvider = new AivaV2Models.BookingPatch(
                AivaV2Models.ValuePatch.unchanged(), patch.specialtyText(), patch.dateExpression(),
                patch.timeWindow(), patch.exactTime(), patch.clinicText(), patch.availabilityTimeConstraint());
        ConversationDecision boundDecision = new ConversationDecision(decision.schemaVersion(), decision.dialogAct(),
                decision.operation(), withoutModelProvider, decision.selection(), decision.confirmation(),
                decision.topicAction(), decision.responseLanguage(), decision.confidence(), decision.provider(),
                decision.fallbackUsed(), decision.latencyMs(), decision.lookupFilter(),
                decision.unresolvedExplicitQualifierPresent());
        return new ActiveProviderBinding(boundSession, boundDecision);
    }

    private boolean isBookingProviderOperation(AivaV2Models.Operation operation) {
        return operation == AivaV2Models.Operation.START_BOOKING
                || operation == AivaV2Models.Operation.UPDATE_BOOKING
                || operation == AivaV2Models.Operation.GET_AVAILABILITY
                || operation == AivaV2Models.Operation.SELECT_SLOT;
    }

    private record ActiveProviderBinding(SessionProjection session, ConversationDecision decision) { }

    private SessionProjection adoptActiveVoiceDoctor(SessionProjection session, NormalizedUserTurn turn,
                                                     AivaExecutionContext context) {
        ProviderCandidate active = session.activeVoiceDoctor();
        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (active == null || draft == null
                || (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE
                && session.latestProviderResult() != null
                && session.latestProviderResult().status() == ResolutionStatus.SUGGESTION)) return session;
        if (draft.selectedProvider() != null && draft.selectedProvider().source() == ProviderSource.CARE_PRIVATE
                && draft.selectedProvider().doctorId().equals(active.doctorId())
                && context != null && context.tenantId() != null
                && context.tenantId().toString().equals(draft.selectedProvider().tenantId())) return session;
        String explicit = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().canonicalQuery();
        if (isExplicitDifferentProvider(turn, active.displayName())) return session;
        ProviderCandidate incoming = active;
        session = adoptCanonicalBookingProvider(session, draft, incoming, context, "ACTIVE_DOCTOR");
        if (session.activeDraft() == null || session.activeDraft().selectedProvider() == null) return session;
        active = session.activeDraft().selectedProvider();
        session = session.withActiveVoiceDoctor(active);
        AvailabilityResult priorAvailability = session.latestAvailabilityResult();
        if (draft.preferredDate() == null && priorAvailability != null
                && active.doctorId().equals(priorAvailability.doctorId())
                && priorAvailability.date() != null
                && (priorAvailability.expiresAt() == null || priorAvailability.expiresAt().isAfter(Instant.now()))) {
            draft = new AivaV2Models.BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(),
                    draft.selectedProvider(), draft.specialtyFilter(), priorAvailability.date(),
                    draft.preferredTimeWindow(), draft.exactTime(), draft.availabilityTimeConstraint(),
                    null, null, AivaV2Models.DraftStatus.COLLECTING, draft.revision() + 1,
                    draft.expiresAt(), null);
            session = session.withBookingDraft(draft);
        }
        return session;
    }

    private AivaV2Models.BookingDraft seedDraftFromReadOnlyContext(AivaV2Models.BookingDraft draft,
            SessionProjection session, NormalizedUserTurn turn) {
        AvailabilityResult context = session == null ? null : session.latestAvailabilityResult();
        if (context == null || context.date() == null
                || context.expiresAt() != null && !context.expiresAt().isAfter(Instant.now())
                || !isExplicitUserDateContext(context.cursor())) return draft;
        ProviderCandidate active = session.activeVoiceDoctor();
        if (active != null && StringUtils.hasText(context.doctorId())
                && !active.doctorId().equals(context.doctorId())) return draft;
        LocalDate date = turn != null && turn.temporal() != null
                && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? turn.temporal().localDate() : null;
        if (date == null) date = context.date();
        String timeWindow = turn.daypart() == null ? context.timeWindow()
                : turn.daypart().name().toLowerCase(Locale.ROOT);
        log.info("VOICE_DISCOVERY_CONTEXT_SEEDED source=USER_SELECTED_DATE datePresent=true daypartPresent={} "
                        + "providerBindingPresent={}", StringUtils.hasText(timeWindow),
                StringUtils.hasText(context.doctorId()));
        return new AivaV2Models.BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(),
                draft.selectedProvider(), draft.specialtyFilter(), date, timeWindow, null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, draft.revision(), draft.expiresAt(), null);
    }

    private boolean isExplicitUserDateContext(String cursor) {
        return "VOICE_PROVIDER_AVAILABILITY_DATE".equals(cursor)
                || "VOICE_CONFIRM_SCHEDULE".equals(cursor)
                || cursor != null && (cursor.startsWith("VOICE_CONFIRM_SCHEDULE_DAYPART:")
                || cursor.startsWith("VOICE_PENDING_CONSULTATION_"));
    }

    private ProviderCandidate resolveAuthoritativeBookingProvider(AivaExecutionContext context,
                                                                   ProviderCandidate discovered) {
        if (discovered == null || transactionalBoundary == null || context == null || context.tenantId() == null) return null;
        if (discovered.source() == ProviderSource.CARE_PRIVATE
                && context.tenantId().toString().equals(discovered.tenantId())) return discovered;
        ProviderCandidate resolved = transactionalBoundary.resolveVoiceBookingProvider(context.tenantId(), discovered);
        return resolved != null && discovered.doctorId().equals(resolved.doctorId())
                && context.tenantId().toString().equals(resolved.tenantId()) ? resolved : null;
    }

    /** Recognize page controls before the original transcript can become provider evidence. */
    private NormalizedUserTurn normalizeVoiceTurn(AivaExecutionContext context, SessionProjection session,
                                                  String transcript) {
        var adapter = languageAdapters.resolve(context.language(), transcript);
        LocalDate referenceDate = LocalDate.now(context.timezone());
        // A no-target provider-change command is a control, not provider evidence.
        // Normalize a fixed control phrase first so Hindi morphology such as
        // "बदलना" can never become a spurious canonical doctor reference.
        if (isRawProviderChangeWithoutTarget(transcript)) {
            NormalizedUserTurn command = adapter.normalize("change doctor", referenceDate);
            return copyWithRawText(command.withoutDoctorEvidence(), transcript);
        }
        if (!hasBookingSlotPage(session) || !SLOT_PAGE_CONTROL.matcher(transcript).matches()) {
            return contextualRelativeDate(adapter.normalize(transcript, referenceDate), session);
        }
        NormalizedUserTurn control = adapter.normalize("show more slots", referenceDate);
        return new NormalizedUserTurn(transcript, "show more slots", "show more slots",
                control.languageCode(), null, false, List.of(DeterministicControl.SHOW_MORE_SLOTS),
                control.responseLanguage(), control.responseStyle());
    }

    private NormalizedUserTurn copyWithRawText(NormalizedUserTurn turn, String rawText) {
        return new NormalizedUserTurn(rawText, turn.normalizedText(), turn.selectionText(), turn.languageCode(),
                turn.explicitDoctorReference(), turn.explicitLookupQualifierPresent(), turn.controls(),
                turn.responseLanguage(), turn.responseStyle(), turn.canonicalSemanticText(), turn.doctorEntity(),
                turn.specialtyEntity(), turn.temporal(), turn.exactTime(), turn.daypart(), turn.ordinal(),
                turn.confirmation(), turn.pagination(), turn.intent());
    }

    private NormalizedUserTurn contextualRelativeDate(NormalizedUserTurn turn, SessionProjection session) {
        if (turn == null || session == null || !isRelativeDateChain(turn.rawText())) return turn;
        LocalDate base = session.activeDraft() != null && session.activeDraft().preferredDate() != null
                ? session.activeDraft().preferredDate()
                : session.latestAvailabilityResult() == null ? null : session.latestAvailabilityResult().date();
        if (base == null) return turn.withoutDoctorEvidence();
        return turn.withoutDoctorEvidence().withTemporal(new NormalizedUserTurn.TemporalFact(turn.rawText(),
                NormalizedUserTurn.TemporalStatus.RESOLVED, base.plusDays(1),
                NormalizedUserTurn.RelativeKind.TOMORROW, false));
    }

    private boolean isRelativeDateChain(String rawText) {
        return StringUtils.hasText(rawText) && rawText.matches(
                "(?iu).*?(?:उसके\\s+अगले\\s+दिन|एक\\s+दिन\\s+बाद|\\bnext\\s+day\\b).*" );
    }

    private boolean hasBookingSlotPage(SessionProjection session) {
        return AivaV2VoiceSlotContext.isAwaitSlot(session, Instant.now());
    }

    private String slotExhaustedPrompt(NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null
                && turn.responseLanguage().toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        return hindi ? hinglish
                ? "Is date ke liye aur slots available nahi hain. Kya aap doosri date dekhna chahenge?"
                : "इस तारीख के लिए और स्लॉट उपलब्ध नहीं हैं। क्या आप दूसरी तारीख देखना चाहेंगे?"
                : "There are no more slots for this date. Would you like to see another date?";
    }

    private boolean hasSlotContextChangeEvidence(NormalizedUserTurn turn) {
        return turn != null && (turn.temporal() != null
                && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                || AivaV2VoiceSlotContext.explicitlySearchesNextDate(turn.rawText())
                || turn.daypart() != null || turn.exactTime() != null
                || turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference())
                || isClosing(turn) || turn.controls().contains(DeterministicControl.ABANDON_WORKFLOW)
                || turn.controls().contains(DeterministicControl.HELP)
                || turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE));
    }

    private ConversationDecision slotDecision(NormalizedUserTurn turn, AivaV2Models.Operation operation,
                                               AivaV2Models.Selection selection) {
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                operation == AivaV2Models.Operation.SHOW_MORE_SLOTS
                        ? AivaV2Models.DialogAct.REQUEST_ALTERNATIVE : AivaV2Models.DialogAct.SELECT_OPTION,
                operation, AivaV2Models.BookingPatch.empty(), selection,
                AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "VOICE_AWAIT_SLOT_CONTEXT", false, 0L, null, false);
    }

    private ConversationDecision slotDateDecision(NormalizedUserTurn turn, LocalDate date) {
        AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.set(date.toString()), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.CHANGE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "VOICE_AWAIT_SLOT_CONTEXT", false, 0L, null, false);
    }

    private ConversationDecision slotDaypartDecision(NormalizedUserTurn turn, NormalizedUserTurn.Daypart daypart) {
        AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.set(daypart.name().toLowerCase(Locale.ROOT)),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.CHANGE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "VOICE_AWAIT_SLOT_CONTEXT", false, 0L, null, false);
    }

    private ConversationDecision slotExactTimeDecision(NormalizedUserTurn turn, LocalTime exactTime) {
        AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.set(exactTime.toString()), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.CHANGE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "VOICE_AWAIT_SLOT_CONTEXT", false, 0L, null, false);
    }

    private boolean hasExplicitTemporalOrDaypartEvidence(NormalizedUserTurn turn) {
        return turn != null && (turn.temporal() != null
                && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                && turn.temporal().localDate() != null || turn.daypart() != null || turn.exactTime() != null);
    }

    private boolean isAllowedAwaitSlotDecision(ConversationDecision decision, NormalizedUserTurn turn,
                                               boolean explicitContextDecision) {
        if (decision == null) return false;
        return switch (decision.operation()) {
            case SELECT_SLOT, SHOW_MORE_SLOTS, UPDATE_BOOKING, GET_AVAILABILITY, ABANDON_BOOKING,
                    INTRO, PRESENCE_CHECK, REPEAT_LAST_RESPONSE, HOLD, CONTINUE_CONVERSATION,
                    END_CONVERSATION, REQUEST_HUMAN_ASSISTANCE, ANSWER_CONTEXT, ANSWER_PROVIDER_CONTEXT -> true;
            case FIND_NEXT_AVAILABLE_SLOT -> explicitContextDecision
                    || AivaV2VoiceSlotContext.explicitlySearchesNextDate(turn.rawText());
            default -> false;
        };
    }

    private boolean isBoundedDisplayedSlotReply(NormalizedUserTurn turn) {
        String raw = turn == null || turn.rawText() == null ? "" : turn.rawText().trim();
        return raw.matches("(?iu)^(?:(?:please\\s+)?(?:book|select|choose|take|pick|use)\\s+)?"
                + "(?:the\\s+)?(?:ten|nine\\s+thirty|[01]?\\d(?::[0-5]\\d)?\\s*(?:am|pm)?)"
                + "(?:\\s+(?:works(?:\\s+for\\s+me)?|is\\s+(?:fine|okay)|will\\s+do))?[.!?]*$")
                || raw.matches("(?iu)^(?:(?:please\\s+)?(?:book|select|choose|take|pick|use)\\s+)?"
                + "(?:the\\s+)?(?:first|second|third|[1-3](?:st|nd|rd)?)"
                + "(?:\\s+(?:one|slot|option))?[.!?]*$");
    }

    /** Small state-based router for slot filling; ambiguity still falls through to the configured gateway. */
    private ConversationDecision deterministicBookingContinuation(NormalizedUserTurn turn, SessionProjection session) {
        return deterministicBookingContinuation(turn, session, false);
    }

    private ConversationDecision deterministicBookingContinuation(NormalizedUserTurn turn, SessionProjection session,
                                                                    boolean pendingShowMoreResolved) {
        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (draft == null || draft.status() != AivaV2Models.DraftStatus.COLLECTING) return null;
        if (hasBookingSlotPage(session) && turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE
                && (pendingShowMoreResolved || SLOT_PAGE_CONTROL.matcher(turn.rawText()).matches())) {
            return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                    AivaV2Models.DialogAct.REQUEST_ALTERNATIVE, AivaV2Models.Operation.SHOW_MORE_SLOTS,
                    AivaV2Models.BookingPatch.empty(), null, AivaV2Models.ConfirmationPolarity.NONE,
                    AivaV2Models.TopicAction.CONTINUE, turn.responseLanguage(), 1d,
                    "DETERMINISTIC_SESSION_ROUTER", false, 0L, null, false);
        }
        AvailabilityResult availability = session.latestAvailabilityResult();
        if (hasBookingSlotPage(session) && parsedTimeRange(turn) != null) {
            NormalizedUserTurn rangeTurn = turn.daypart() == null && availability != null
                    ? turn.withDaypart(daypartFromWindow(availability.timeWindow())) : turn;
            AvailabilityTimeConstraint range = timeConstraint(rangeTurn, parsedTimeRange(rangeTurn), availability);
            if (range != null) {
                AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                        AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                        AivaV2Models.ValuePatch.clear(), AivaV2Models.ValuePatch.unchanged(),
                        new AivaV2Models.AvailabilityTimeConstraintPatch(AivaV2Models.PatchMode.SET, range));
                return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                        AivaV2Models.DialogAct.PROVIDE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                        patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                        turn.responseLanguage(), 1d, "DETERMINISTIC_SLOT_RANGE_REFINEMENT", false, 0L, null, false);
            }
        }
        if (availability != null && "AIVA_BOOKING_PENDING_DAYPART".equals(availability.cursor())
                && turn.daypart() != null && turn.exactTime() == null && !hasTimeRangeEvidence(turn)) {
            AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                    AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                    AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.set(
                    turn.daypart().name().toLowerCase(Locale.ROOT)),
                    AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                    AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
            return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                    AivaV2Models.DialogAct.PROVIDE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                    patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                    turn.responseLanguage(), 1d, "DETERMINISTIC_SESSION_ROUTER", false, 0L, null, false);
        }
        if (draft.selectedProvider() != null && isBookingRequest(turn)
                && !isExplicitDifferentProvider(turn, draft.selectedProvider().displayName())) {
            return withOperation(ConversationDecision.unknown(turn.responseLanguage(),
                    "DETERMINISTIC_SESSION_ROUTER", false, 0L), AivaV2Models.Operation.UPDATE_BOOKING);
        }
        if (draft.selectedProvider() != null && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                && turn.temporal().localDate() != null && turn.exactTime() == null && turn.daypart() == null
                && !hasTimeRangeEvidence(turn)
                && !isExplicitDifferentProvider(turn, draft.selectedProvider().displayName())) {
            ConversationDecision update = withOperation(ConversationDecision.unknown(turn.responseLanguage(),
                    "DETERMINISTIC_SESSION_ROUTER", false, 0L), AivaV2Models.Operation.UPDATE_BOOKING);
            return withBookingDate(update, turn.temporal().localDate().toString());
        }
        return null;
    }

    private CollectionTurn collectionTurn(AivaV2VoiceCollectionContext.State state, NormalizedUserTurn turn,
            SessionProjection session) {
        String providerEvidence = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().canonicalQuery();
        String specialtyEvidence = turn.specialtyEntity() == null ? null
                : turn.specialtyEntity().canonicalQuery();
        LocalDate date = turn.temporal() != null
                && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? turn.temporal().localDate() : null;
        String range = parsedTimeRange(turn);
        AvailabilityTimeConstraint requestedTimeConstraint =
                StringUtils.hasText(range) || turn.exactTime() != null
                        ? timeConstraint(turn, range, null) : null;
        boolean hasTimeEvidence = turn.daypart() != null || turn.exactTime() != null
                || requestedTimeConstraint != null;

        if (state == AivaV2VoiceCollectionContext.State.AWAIT_PROVIDER) {
            if (!StringUtils.hasText(specialtyEvidence)) {
                String canonicalSpecialty = specialty(turn);
                if (StringUtils.hasText(canonicalSpecialty)) specialtyEvidence = canonicalSpecialty;
            }
            boolean providerEvidencePresent = StringUtils.hasText(providerEvidence)
                    && DoctorNameNormalizer.isPlausibleReference(providerEvidence);
            boolean specialtyEvidencePresent = StringUtils.hasText(specialtyEvidence);
            if (providerEvidencePresent || specialtyEvidencePresent) {
                AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                        providerEvidencePresent ? AivaV2Models.ValuePatch.set(providerEvidence)
                                : AivaV2Models.ValuePatch.unchanged(),
                        specialtyEvidencePresent ? AivaV2Models.ValuePatch.set(specialtyEvidence)
                                : AivaV2Models.ValuePatch.unchanged(),
                        date == null ? AivaV2Models.ValuePatch.unchanged()
                                : AivaV2Models.ValuePatch.set(date.toString()),
                        turn.daypart() == null || turn.exactTime() != null || requestedTimeConstraint != null
                                ? AivaV2Models.ValuePatch.unchanged()
                                : AivaV2Models.ValuePatch.set(turn.daypart().name().toLowerCase(Locale.ROOT)),
                        turn.exactTime() == null ? AivaV2Models.ValuePatch.unchanged()
                                : AivaV2Models.ValuePatch.set(turn.exactTime().toString()),
                        null,
                        requestedTimeConstraint == null ? AivaV2Models.AvailabilityTimeConstraintPatch.unchanged()
                                : new AivaV2Models.AvailabilityTimeConstraintPatch(
                                        AivaV2Models.PatchMode.SET, requestedTimeConstraint));
                return new CollectionTurn(collectionDecision(turn, patch), null, null);
            }
            return new CollectionTurn(null, collectionPrompt(state, turn, session),
                    "COLLECTION_PROVIDER_CLARIFICATION");
        }

        if (state == AivaV2VoiceCollectionContext.State.AWAIT_DATE) {
            if (date == null) {
                return new CollectionTurn(null, collectionPrompt(state, turn, session),
                        turn.temporal() != null
                                && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.AMBIGUOUS
                                ? "COLLECTION_DATE_AMBIGUOUS" : "COLLECTION_DATE_CLARIFICATION");
            }
            AivaV2Models.BookingPatch patch = collectionPatch(turn, date, true, hasTimeEvidence,
                    requestedTimeConstraint);
            return new CollectionTurn(collectionDecision(turn, patch), null, null);
        }

        if (state == AivaV2VoiceCollectionContext.State.AWAIT_DAYPART) {
            if (date != null) {
                return new CollectionTurn(collectionDecision(turn,
                        collectionPatch(turn, date, true, hasTimeEvidence, requestedTimeConstraint)), null, null);
            }
            if (hasTimeEvidence) {
                return new CollectionTurn(collectionDecision(turn,
                        collectionPatch(turn, null, false, true, requestedTimeConstraint)), null, null);
            }
            if (hasTimeRangeEvidence(turn)) {
                return new CollectionTurn(null, null, "COLLECTION_TIME_CONSTRAINT");
            }
            return new CollectionTurn(null, collectionPrompt(state, turn, session),
                    "COLLECTION_DAYPART_CLARIFICATION");
        }
        return new CollectionTurn(null, null, null);
    }

    private boolean isCollectionUtilityControl(NormalizedUserTurn turn) {
        return turn != null && (isClosing(turn)
                || turn.controls().contains(DeterministicControl.HELP)
                || turn.controls().contains(DeterministicControl.REPEAT_LAST_RESPONSE)
                || turn.controls().contains(DeterministicControl.ABANDON_WORKFLOW));
    }

    private AivaV2Models.BookingPatch collectionPatch(NormalizedUserTurn turn, LocalDate date,
            boolean includeDate, boolean includeTime, AvailabilityTimeConstraint requestedTimeConstraint) {
        boolean range = requestedTimeConstraint != null
                && requestedTimeConstraint.mode() != AvailabilityTimeConstraintMode.EXACT;
        AivaV2Models.ValuePatch timeWindow = includeTime && !range && turn.exactTime() == null
                && turn.daypart() != null
                ? AivaV2Models.ValuePatch.set(turn.daypart().name().toLowerCase(Locale.ROOT))
                : AivaV2Models.ValuePatch.unchanged();
        AivaV2Models.ValuePatch exact = includeTime && turn.exactTime() != null
                ? AivaV2Models.ValuePatch.set(turn.exactTime().toString())
                : AivaV2Models.ValuePatch.unchanged();
        return new AivaV2Models.BookingPatch(AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(),
                includeDate && date != null ? AivaV2Models.ValuePatch.set(date.toString())
                        : AivaV2Models.ValuePatch.unchanged(),
                timeWindow, exact, null,
                includeTime && requestedTimeConstraint != null
                        ? new AivaV2Models.AvailabilityTimeConstraintPatch(
                                AivaV2Models.PatchMode.SET, requestedTimeConstraint)
                        : AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
    }

    private ConversationDecision collectionDecision(NormalizedUserTurn turn, AivaV2Models.BookingPatch patch) {
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.PROVIDE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "DETERMINISTIC_COLLECTION_CONTEXT", false, 0L, null, false);
    }

    private boolean isAllowedCollectionTimeConstraint(ConversationDecision decision) {
        if (decision == null || decision.operation() != AivaV2Models.Operation.GET_AVAILABILITY
                || decision.bookingPatch() == null || decision.selection() != null
                || decision.confirmation() != AivaV2Models.ConfirmationPolarity.NONE) return false;
        AivaV2Models.BookingPatch patch = decision.bookingPatch();
        return patch.doctorText().mode() == AivaV2Models.PatchMode.UNCHANGED
                && patch.specialtyText().mode() == AivaV2Models.PatchMode.UNCHANGED
                && patch.dateExpression().mode() == AivaV2Models.PatchMode.UNCHANGED
                && patch.exactTime().mode() == AivaV2Models.PatchMode.UNCHANGED
                && patch.availabilityTimeConstraint().mode() == AivaV2Models.PatchMode.SET;
    }

    private String collectionPrompt(AivaV2VoiceCollectionContext.State state, NormalizedUserTurn turn,
            SessionProjection session) {
        boolean hindi = turn.responseLanguage() != null
                && turn.responseLanguage().toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle()
                == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        if (state == AivaV2VoiceCollectionContext.State.AWAIT_PROVIDER) {
            return hindi ? hinglish ? "Kis doctor ya specialty ke saath appointment chahiye?"
                    : "किस डॉक्टर या specialty के साथ appointment चाहिए?"
                    : "Which doctor or specialty would you like?";
        }
        if (state == AivaV2VoiceCollectionContext.State.AWAIT_DATE) {
            return hindi ? hinglish ? "Appointment kis date ka chahiye — aaj, kal ya parso?"
                    : "किस तारीख का appointment चाहिए — आज, कल या परसों?"
                    : "What date would you like — today, tomorrow, or the day after tomorrow?";
        }
        List<NormalizedUserTurn.Daypart> choices = AivaV2VoiceCollectionContext.availableDayparts(
                session, session.activeDraft(), Instant.now());
        List<String> labels = choices.stream().map(part -> switch (part) {
            case MORNING -> hindi ? hinglish ? "subah" : "सुबह" : "morning";
            case AFTERNOON -> hindi ? hinglish ? "dopahar" : "दोपहर" : "afternoon";
            case EVENING -> hindi ? hinglish ? "shaam" : "शाम" : "evening";
        }).toList();
        String options = labels.size() == 2 ? labels.get(0) + (hindi ? hinglish ? " ya " : " या " : " or ") + labels.get(1)
                : String.join(hindi ? hinglish ? ", " : ", " : ", ", labels);
        return hindi ? hinglish ? "Kaun-sa time dekhein — " + options + "?"
                : "कौन-सा समय देखें — " + options + "?"
                : "Which time should I check — " + options + "?";
    }

    private record CollectionTurn(ConversationDecision decision, String prompt, String result) { }

    private enum ConfirmationTurnAction {
        CONFIRM, DECLINE, CHANGE_SLOT, CHANGE_DATE, CHANGE_TIME, ABANDON, HELP, REPEAT, END, CLARIFY
    }

    private record ConfirmationTurnResolution(ConfirmationTurnAction action,
            NormalizedUserTurn turn, ConversationDecision decision) { }

    private boolean hasTimeRangeEvidence(NormalizedUserTurn turn) {
        String text = (turn.rawText() == null ? "" : turn.rawText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bbetween\\b|\\bafter\\b|\\bbefore\\b|\\bfrom\\b|\\bto\\b|से|के बाद|से पहले|के बीच|[-–]).*");
    }

    private boolean isExplicitDifferentProvider(NormalizedUserTurn turn, String activeProviderName) {
        String reference = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().canonicalQuery();
        if (!StringUtils.hasText(reference) || DoctorNameNormalizer.matches(activeProviderName, reference)) return false;
        String candidate = reference.toLowerCase(Locale.ROOT);
        return !candidate.matches("(?s).*(?:appointment|booking|book|available|availability|slot|time|today|tomorrow|"
                + "कल|आज|अपॉइंटमेंट|बुक|बुकिंग|दे दीजिए|कर दीजिए|चाहिए|चाहता|चाहती|करना|का|के|की).*" );
    }

    private boolean isPendingDaypartAnswer(NormalizedUserTurn turn, SessionProjection session) {
        return session.latestAvailabilityResult() != null
                && "AIVA_BOOKING_PENDING_DAYPART".equals(session.latestAvailabilityResult().cursor())
                && turn.daypart() != null && turn.exactTime() == null && !hasTimeRangeEvidence(turn);
    }

    private NormalizedUserTurn bookingContinuationTurn(NormalizedUserTurn turn,
            AivaV2Models.BookingDraft draft, java.time.ZoneId timezone) {
        if (draft == null || draft.status() != AivaV2Models.DraftStatus.COLLECTING
                || draft.selectedProvider() == null || draft.preferredDate() != null
                || turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED) return turn;
        java.time.ZoneId trustedZone = timezone == null ? java.time.ZoneId.of("Asia/Kolkata") : timezone;
        AivaV2TemporalNormalizer normalizer = new AivaV2TemporalNormalizer(
                java.time.Clock.system(trustedZone), trustedZone);
        AivaV2TemporalResolution contextual = normalizer.normalize(turn.rawText(),
                AivaV2Models.ValuePatch.unchanged(), null);
        if (contextual.status() != AivaV2TemporalResolution.Status.RESOLVED
                || contextual.localDate() == null) {
            var localized = new com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LocaleTemporalNormalizer()
                    .normalize(turn.rawText(), java.time.LocalDate.now(java.time.Clock.system(trustedZone)));
            if (localized.status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                    && localized.localDate() != null) {
                contextual = new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED,
                        localized.localDate(), AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT,
                        localized.rawSpan(), null, false, localized.candidateDates());
            }
        }
        if (contextual.status() != AivaV2TemporalResolution.Status.RESOLVED
                || contextual.localDate() == null) {
            return isDateOnlyContinuation(turn) ? withoutProviderReference(turn) : turn;
        }
        NormalizedUserTurn.RelativeKind relative = contextual.localDate().equals(
                java.time.LocalDate.now(java.time.Clock.system(trustedZone)).plusDays(1))
                ? NormalizedUserTurn.RelativeKind.TOMORROW : turn.temporal().relativeKind();
        NormalizedUserTurn resolved = turn.withTemporal(new NormalizedUserTurn.TemporalFact(
                contextual.rawCandidate() == null ? turn.temporal().rawSpan() : contextual.rawCandidate(),
                NormalizedUserTurn.TemporalStatus.RESOLVED, contextual.localDate(), relative, false));
        return isDateOnlyContinuation(turn) ? withoutProviderReference(resolved) : resolved;
    }

    private boolean isDateOnlyContinuation(NormalizedUserTurn turn) {
        String raw = turn.rawText() == null ? "" : turn.rawText().toLowerCase(Locale.ROOT)
                .replaceAll("[,.!?।]", " ").replaceAll("\\s+", " ").trim();
        String dateSpan = turn.temporal().rawSpan();
        if (StringUtils.hasText(dateSpan)) raw = raw.replaceFirst(
                "(?iu)" + java.util.regex.Pattern.quote(dateSpan.toLowerCase(Locale.ROOT)), " ");
        raw = raw.replaceAll("(?iu)(?:का|की|के|लिए|को|please|for|the|date|appointment)", " ")
                .replaceAll("\\s+", " ").trim();
        return raw.isEmpty();
    }

    private NormalizedUserTurn withoutProviderReference(NormalizedUserTurn turn) {
        return new NormalizedUserTurn(turn.rawText(), turn.normalizedText(), turn.selectionText(), turn.languageCode(),
                null, turn.explicitLookupQualifierPresent(), turn.controls(), turn.responseLanguage(), turn.responseStyle(),
                turn.canonicalSemanticText(), null, turn.specialtyEntity(), turn.temporal(), turn.exactTime(),
                turn.daypart(), turn.ordinal(), turn.confirmation(), turn.pagination(), turn.intent());
    }

    private ConversationDecision withBookingDate(ConversationDecision decision, String date) {
        AivaV2Models.BookingPatch patch = decision.bookingPatch();
        AivaV2Models.BookingPatch dated = new AivaV2Models.BookingPatch(patch.doctorText(), patch.specialtyText(),
                AivaV2Models.ValuePatch.set(date), patch.timeWindow(), patch.exactTime(), patch.clinicText(),
                patch.availabilityTimeConstraint());
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), decision.operation(), dated,
                decision.selection(), decision.confirmation(), decision.topicAction(), decision.responseLanguage(),
                decision.confidence(), decision.provider(), decision.fallbackUsed(), decision.latencyMs(),
                decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private String providerStateSource(SessionProjection session) {
        return session.activeDraft() != null && session.activeDraft().selectedProvider() != null
                ? "BOOKING_DRAFT" : session.activeVoiceDoctor() != null ? "VOICE_ACTIVE_DOCTOR" : "NONE";
    }

    private void logBookingFlow(String stage, AivaExecutionContext context, SessionProjection session,
            NormalizedUserTurn turn, ConversationDecision decision, String providerSource) {
        AivaV2Models.BookingDraft draft = session.activeDraft();
        ProviderCandidate active = session.activeVoiceDoctor();
        ProviderCandidate provider = draft == null ? null : draft.selectedProvider();
        AivaTransactionAuthorization authorization = context.transactionAuthorization();
        String temporalSource = turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? "CURRENT_TURN" : draft != null && draft.preferredDate() != null ? "SESSION_CONTEXT" : "NONE";
        Boolean restrictedBookingAllowed = null;
        if (authorization != null) {
            if ("VOICE_RESTRICTED_NEW_BOOKING".equals(authorization.authorizationSource())) {
                restrictedBookingAllowed = Boolean.TRUE;
            } else if (authorization.authenticated()) {
                restrictedBookingAllowed = Boolean.FALSE;
            }
        }
        boolean personalDataAllowed = authorization != null && authorization.authenticated()
                && authorization.grantedCapabilities().contains(AivaTransactionAuthorization.Capability.VIEW_APPOINTMENTS);
        String pending = session.pendingConfirmation() != null ? "BOOKING_CONFIRMATION"
                : draft != null && draft.status() == AivaV2Models.DraftStatus.COLLECTING
                && draft.selectedProvider() != null && draft.preferredDate() == null ? "BOOKING_DATE"
                : "NONE";
        log.info("AIVA_V2_VOICE_FLOW stage={} tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                        + "rawOperation={} normalizedOperation={} dialogAct={} intent={} tool={} activeWorkflow={} pendingContinuationType={} "
                        + "activeVoiceDoctorId={} activeVoiceDoctorName={} bookingProviderId={} bookingProviderName={} providerResolved={} "
                        + "currentTurnDate={} effectiveDate={} draftDate={} datePresent={} temporalSource={} daypart={} exactTime={} "
                        + "rangeStart={} rangeEnd={} slotSelected={} selectedSlotRef={} draftRevision={} draftStatus={} "
                        + "transactionAuthorizationSource={} restrictedBookingAllowed={} personalDataAllowed={} decisionCategory={} "
                        + "executionDecision={} stateSourceForProvider={} stateSourceForDate={} stateSourceForWorkflow={}",
                stage, context.tenantId(), context.providerCallId(), AivaV2VoiceFlowTrace.conversationHash(session.conversationId()),
                AivaV2VoiceFlowTrace.turnId(), AivaV2VoiceFlowTrace.turnNumber(),
                decision == null ? null : decision.operation(), decision == null ? null : decision.operation(),
                decision == null ? null : decision.dialogAct(), turn.intent(), decision == null ? null : decision.operation(),
                session.activeWorkflow(), pending, active == null ? null : active.doctorId(),
                active == null ? null : active.displayName(), provider == null ? null : provider.doctorId(),
                provider == null ? null : provider.displayName(), provider != null,
                turn.temporal().localDate(), turn.temporal().localDate() != null ? turn.temporal().localDate()
                        : draft == null ? null : draft.preferredDate(), draft == null ? null : draft.preferredDate(),
                draft != null && draft.preferredDate() != null,
                temporalSource, turn.daypart(), turn.exactTime(),
                draft == null || draft.availabilityTimeConstraint() == null ? null
                        : draft.availabilityTimeConstraint().startTime(),
                draft == null || draft.availabilityTimeConstraint() == null ? null
                        : draft.availabilityTimeConstraint().endTime(),
                draft != null && StringUtils.hasText(draft.selectedSlotReference()),
                draft == null ? null : draft.selectedSlotReference(), draft == null ? null : draft.revision(),
                draft == null ? null : draft.status(), authorization == null ? null : authorization.authorizationSource(),
                restrictedBookingAllowed, personalDataAllowed,
                decision == null ? null : decision.operation(), decision == null ? "NOT_EXECUTED" : "ROUTED",
                providerSource, temporalSource, session.activeWorkflow());
    }

    private ConversationDecision withOperation(ConversationDecision decision, AivaV2Models.Operation operation) {
        return new ConversationDecision(decision.schemaVersion(), decision.dialogAct(), operation,
                decision.bookingPatch(), decision.selection(), decision.confirmation(), decision.topicAction(),
                decision.responseLanguage(), decision.confidence(), decision.provider(), decision.fallbackUsed(),
                decision.latencyMs(), decision.lookupFilter(), decision.unresolvedExplicitQualifierPresent());
    }

    private AivaV2TemporalResolution voiceTemporal(NormalizedUserTurn turn, AivaV2Models.BookingDraft draft,
                                                    java.time.ZoneId timezone) {
        var fact = turn.temporal();
        if (fact != null && fact.status() == NormalizedUserTurn.TemporalStatus.RESOLVED && fact.localDate() != null) {
            return new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, fact.localDate(),
                    AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, fact.rawSpan(), null, false);
        }
        java.time.ZoneId trustedZone = timezone == null ? java.time.ZoneId.of("Asia/Kolkata") : timezone;
        AivaV2TemporalResolution canonical = new AivaV2TemporalNormalizer(
                java.time.Clock.system(trustedZone), trustedZone).normalize(turn.rawText(),
                AivaV2Models.ValuePatch.unchanged(), draft == null ? null : draft.preferredDate());
        if (canonical.status() == AivaV2TemporalResolution.Status.RESOLVED
                && canonical.source() == AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT) return canonical;
        return AivaV2TemporalResolution.unchanged(draft == null ? null : draft.preferredDate(), null);
    }

    private String guestPreparationResponse(AivaV2TransactionalKernel.KernelResult result,
                                            ConversationDecision decision, NormalizedUserTurn turn) {
        boolean hindi = turn.responseLanguage() != null && turn.responseLanguage().toLowerCase(Locale.ROOT).startsWith("hi");
        boolean hinglish = hindi && turn.responseStyle() == com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle.HINGLISH;
        String category = result.response().responseCategory();
        AivaV2Models.BookingDraft draft = result.session().activeDraft();
        String doctor = draft == null || draft.selectedProvider() == null
                ? null : draft.selectedProvider().displayName();
        if ("TRANSACTION_EXECUTION_DISABLED".equals(category)) {
            if (hindi && !hinglish) return "अपॉइंटमेंट की पुष्टि Voice में अभी सक्षम नहीं है। कोई appointment book नहीं हुई है।";
            if (hinglish) return "Voice par booking confirmation abhi enabled nahi hai. Appointment book nahi hui hai.";
            return "Booking confirmation is not enabled for Voice. No appointment was booked.";
        }
        if ("BOOKING_DRAFT_PREPARED".equals(category) && draft != null) {
            String date = AivaSpokenDateFormatter.format(draft.preferredDate(), turn.responseLanguage(), turn.responseStyle());
            String time = spokenTime(draft.exactTime(), hindi, hinglish);
            if (hindi && !hinglish) return "डॉ. " + doctor + " के लिए " + date + " " + time
                    + " का समय उपलब्ध है। अभी appointment book नहीं हुई है।";
            if (hinglish) return "Dr. " + doctor + " ke liye " + date + " " + time
                    + " available hai. Abhi appointment book nahi hui hai.";
            return "Dr. " + doctor + " is available on " + date + " at " + time + ". No appointment has been booked.";
        }
        if ("NEED_PROVIDER".equals(category)) return hindi
                ? hinglish ? "Kis doctor ya specialty ke liye appointment chahiye?" : "किस डॉक्टर या विशेषज्ञता के लिए अपॉइंटमेंट चाहिए?"
                : "Which doctor or specialty would you like?";
        if ("NEED_DATE".equals(category)) return hindi
                ? hinglish ? "Aap kis date ka appointment chahte hain?" : "आप किस तारीख का अपॉइंटमेंट चाहते हैं?"
                : "What date would you prefer?";
        if ("PROVIDER_NOT_FOUND".equals(category)) return hindi
                ? hinglish ? "Is doctor ka match nahi mila. Kya aap naam dobara bata sakte hain?" : "इस डॉक्टर का मिलान नहीं मिला। कृपया नाम फिर से बताइए।"
                : "I could not find a matching doctor. Please check the name.";
        if ("NO_AVAILABILITY".equals(category) || "NO_SLOTS".equals(category)) return hindi
                ? hinglish ? "Is date par koi slot available nahi hai. Doosri date try karein?" : "इस तारीख को कोई स्लॉट उपलब्ध नहीं है। क्या आप दूसरी तारीख देखना चाहेंगे?"
                : "There are no available slots on that date. Would you like another date?";
        if ("NO_MORE_SLOTS".equals(category)) return slotExhaustedPrompt(turn);
        if ("DAYPART_SELECTION".equals(category)) return hindi
                ? hinglish ? "Morning, afternoon, ya evening mein appointment chahiye?" : "सुबह, दोपहर या शाम में अपॉइंटमेंट चाहिए?"
                : "Would you prefer morning, afternoon, or evening?";
        if ("BOOKING_CONFIRMATION_PENDING".equals(category) && draft != null)
            return bookingConfirmationPrompt(draft, turn);
        if ("STALE_SLOT".equals(category) || "BOOKING_CONFIRMATION_STALE".equals(category))
            return localizedStaleResponse(turn);
        if ("BOOKING_FAILED".equals(category)) return hindi
                ? hinglish ? "Booking confirm nahi ho saki. Appointment book hone ki pushti nahi hai."
                : "बुकिंग की पुष्टि नहीं हो सकी। अपॉइंटमेंट बुक होने की पुष्टि नहीं है।"
                : "I could not confirm the booking. No appointment was confirmed.";
        if ("SLOT_CHOICES".equals(category)) {
            AvailabilityResult availability = result.session().latestAvailabilityResult();
            int start = availability == null ? 0 : Math.min(availability.displayOffset(), availability.slots().size());
            int end = availability == null ? 0 : Math.min(availability.slots().size(), start + availability.pageSize());
            List<AivaV2Models.AvailabilitySlot> slots = availability == null
                    ? List.of() : availability.slots().subList(start, end);
            String times = java.util.stream.IntStream.range(0, slots.size())
                    .mapToObj(index -> (index + 1) + ". " + spokenTime(slots.get(index).startsAt(), hindi, hinglish))
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (!times.isBlank()) return hindi
                    ? hinglish ? "Available slots hain:\n" + times + "\nKaunsa time theek rahega?"
                    : "उपलब्ध समय:\n" + times + "\nकौन-सा समय ठीक रहेगा?"
                    : "Available slots:\n" + times + "\nWhich time works for you?";
        }
        String fallback = result.response().assistantMessage();
        if ("This request is outside the Voice booking-preparation flow.".equals(fallback)) {
            return localizedNewBookingOnlyResponse(turn);
        }
        return fallback;
    }

    private String spokenTime(LocalTime time, boolean hindi, boolean hinglish) {
        AivaV2Models.VoicePresentationMode mode = !hindi ? AivaV2Models.VoicePresentationMode.ENGLISH
                : hinglish ? AivaV2Models.VoicePresentationMode.HINGLISH : AivaV2Models.VoicePresentationMode.HINDI;
        return new AivaV2VoiceTimePresentationFormatter().format(time, mode);
    }

    public record GuestPreparationResult(String response, String result, Map<String, Object> diagnostics,
                                         CompletedBookingOutcome completedBookingOutcome) {
        public GuestPreparationResult(String response, String result, Map<String, Object> diagnostics) {
            this(response, result, diagnostics, null);
        }

        static GuestPreparationResult denied(String reason) {
            return new GuestPreparationResult("I could not prepare this booking request. No appointment was created.",
                    reason, Map.of("mutationAllowed", false, "executionDecision", "BLOCKED"));
        }
    }

    public TurnResult continueTurn(AivaExecutionContext context, String transcript,
                                   AivaV2VoiceToolExecutor executor) {
        if (context == null || !StringUtils.hasText(transcript) || executor == null) {
            return TurnResult.failure(FAILURE);
        }
        String key = "VOICE|" + context.tenantId() + "|" + context.sessionId();
        AtomicReference<TurnResult> result = new AtomicReference<>();
        AtomicReference<String> activeDoctorBefore = new AtomicReference<>();
        sessionStore.updateTurn(key, null, null, current -> {
            UUID authenticatedPatientId = context.transactionAuthorization() != null
                    && context.transactionAuthorization().authenticated()
                    ? context.transactionAuthorization().authenticatedPatientId() : null;
            UUID resolvedPatientId = context.identifiedPatientId();
            UUID sessionPatientId = authenticatedPatientId == null ? resolvedPatientId : authenticatedPatientId;
            SessionProjection session = current == null
                    ? new SessionProjection(context.sessionId(), sessionPatientId, context.tenantId().toString(),
                    null, null, null, null, null, 1L, Instant.now().plusSeconds(30 * 60))
                    : current;
            if (resolvedPatientId != null && session.patientId() != null
                    && !resolvedPatientId.equals(session.patientId())) {
                log.warn("VOICE_SESSION_PATIENT_BINDING result=REJECTED reason=CALL_PATIENT_MISMATCH");
                TurnResult mismatch = new TurnResult("I could not continue this call safely.",
                        "PATIENT_BINDING_MISMATCH", null, false, true, false,
                        null, 0, "PATIENT_BINDING_MISMATCH", false, null);
                result.set(mismatch);
                return execution(session, mismatch);
            }
            if (resolvedPatientId != null && session.patientId() == null) {
                session = session.withPatientId(resolvedPatientId);
                log.info("VOICE_SESSION_PATIENT_BINDING result=RETAINED source=TENANT_SCOPED_INBOUND_MATCH");
            }
            String activeBefore = session.activeVoiceDoctor() == null
                    ? null : session.activeVoiceDoctor().displayName();
            activeDoctorBefore.set(activeBefore);
            NormalizedUserTurn turn = normalizeVoiceTurn(context, session, transcript);
            AvailabilityResult initialAvailability = session.latestAvailabilityResult();
            if (initialAvailability != null && "VOICE_PENDING_DAYPART".equals(initialAvailability.cursor())
                    && transcript.trim().matches("(?iu)shyam[.!?।]*")) {
                turn = turn.withDaypart(NormalizedUserTurn.Daypart.EVENING).withoutDoctorEvidence();
            }
            PresentationTurn presentation = applyPresentationMode(session, turn);
            session = presentation.session();
            turn = presentation.turn();
            if (isPreparedBookingCancellation(session, turn) && isClosing(turn)) {
                TurnResult closed = new TurnResult(closingResponse(turn), "END_CONVERSATION", null,
                        false, false, false, null, 0, "CANCEL_CURRENT_BOOKING_AND_CLOSE", true, turn,
                        null, Map.of("mutationAllowed", false, "draftCancelled", true,
                                "contextAction", "CANCEL_CURRENT_BOOKING", "conversationControl", "END_CONVERSATION"));
                result.set(closed);
                return execution(session.close(), closed);
            }
            if (isClosing(turn)) {
                logSemanticInput(context, turn, specialty(turn), "END_CONVERSATION", null, "COURTESY_CLOSE");
                logContextTrace(context, turn, null, new DoctorResolution(null, "NOT_REQUIRED", List.of()),
                        "END_CONVERSATION", null, "CONVERSATION_CLOSED");
                result.set(new TurnResult(closingResponse(turn), "END_CONVERSATION", null,
                        false, false, false, null, 0, null, true, turn));
                return execution(session.close(), result.get());
            }
            if (isChangeProviderCommand(turn) && (session.activeDraft() != null
                    || session.latestAvailabilityResult() != null)) {
                SessionProjection changed = clearSlotForProviderChange(session, context)
                        .withVoiceClarificationState(contextChangeClarification("CHANGE_PROVIDER",
                                session.activeWorkflow()));
                TurnResult change = new TurnResult(changeProviderPrompt(turn), "CONTEXT_CHANGE_CLARIFICATION",
                        null, false, false, false, null, 0, "CHANGE_PROVIDER", false, turn, null,
                        Map.of("mutationAllowed", false, "contextAction", "CHANGE_PROVIDER",
                                "pendingClarificationType", "CHANGE_PROVIDER", "slotInvalidatedReason",
                                "PROVIDER_CHANGE_REQUESTED", "contextPreserved", true));
                result.set(change);
                return execution(changed, change);
            }
            if (isRepeatRequest(turn)) {
                String repeated = repeatCurrentPrompt(session, turn);
                if (repeated != null) {
                    AvailabilityResult page = session.latestAvailabilityResult();
                    int offset = page == null ? -1 : page.displayOffset();
                    String target = repeatTarget(session);
                    String action = "SLOT_PAGE".equals(target) ? "REPEAT_SLOT_PAGE" : "REPEAT_CURRENT_PROMPT";
                    Map<String, Object> diagnostics = Map.of("mutationAllowed", false,
                            "contextAction", action, "repeatTarget", target,
                            "slotPageBefore", offset, "slotPageAfter", offset, "contextPreserved", true);
                    result.set(new TurnResult(repeated, action, null, false, false, false,
                            null, 0, action, false, turn, null, diagnostics));
                    log.info("VOICE_CONVERSATION_CONTROL conversationControl=REPEAT repeatTarget={} "
                            + "slotPageBefore={} slotPageAfter={} contextPreserved=true", target, offset, offset);
                    return execution(session, result.get());
                }
            }
            if (isProviderContextInspection(turn) || isDateContextInspection(turn)
                    || isSelectedSlotInspection(turn)) {
                ProviderCandidate active = session.activeDraft() != null
                        && session.activeDraft().selectedProvider() != null
                        ? session.activeDraft().selectedProvider() : session.activeVoiceDoctor();
                String response = contextInspectionResponse(session, turn);
                String contextAction = isProviderContextInspection(turn) ? "INSPECT_PROVIDER"
                        : isDateContextInspection(turn) ? "INSPECT_DATE" : "INSPECT_SELECTED_SLOT";
                Map<String, Object> diagnostics = Map.of("mutationAllowed", false,
                        "contextAction", contextAction, "providerPresent", active != null);
                String inspectionIntent = switch (contextAction) {
                    case "INSPECT_DATE" -> "ANSWER_DATE_CONTEXT";
                    case "INSPECT_SELECTED_SLOT" -> "ANSWER_SELECTED_SLOT_CONTEXT";
                    default -> active == null ? "CLARIFY_DOCTOR" : "ANSWER_PROVIDER_CONTEXT";
                };
                TurnResult inspection = new TurnResult(response, inspectionIntent, null, false, false, false, null, 0,
                        "READ_ONLY_CONTEXT_INSPECTION", false, turn, null, diagnostics);
                result.set(inspection);
                return execution(session, inspection);
            }
            if (isDeclineCurrentSlotOptions(session, turn)) {
                String response = isEnglish(turn)
                        ? "No problem. You can ask for more slots, change the date or doctor, or stop this booking."
                        : isHinglish(turn)
                        ? "Theek hai. Aap aur slots, date ya doctor change kar sakte hain, ya booking rok sakte hain."
                        : "ठीक है। आप और समय पूछ सकते हैं, तारीख या डॉक्टर बदल सकते हैं, या booking रोक सकते हैं।";
                TurnResult declined = new TurnResult(response, "DECLINE_CURRENT_SLOT_OPTIONS", null,
                        false, false, false, null, 0, "DECLINE_CURRENT_SLOT_OPTIONS", false, turn,
                        null, Map.of("mutationAllowed", false, "contextPreserved", true,
                                "conversationControl", "DECLINE_CURRENT_SLOT_OPTIONS"));
                result.set(declined);
                return execution(session, declined);
            }
            VoiceRecognitionResult recognition = recognizePendingClarification(session.voiceClarificationState(), turn);
            if (recognition == null) recognition = recognizeContextBoundControls(session, turn);
            if (recognition.action() == VoiceRecognitionResult.Action.SHOW_MORE
                    && recognition.quality() == VoiceRecognitionResult.Quality.STRONG) {
                turn = turn.withPagination(NormalizedUserTurn.Pagination.SHOW_MORE).withoutDoctorEvidence();
            }
            AvailabilityResult retainedReadOnlyAvailability = session.latestAvailabilityResult();
            if (session.voiceClarificationState() == null && hasRetainedReadOnlySlotPage(session)
                    && retainedSlotContextMatches(retainedReadOnlyAvailability, turn)
                    && !hasConflictingProviderEvidence(session, turn)) {
                if (isShowMore(turn)) {
                    int oldOffset = retainedReadOnlyAvailability.displayOffset();
                    int nextOffset = Math.min(retainedReadOnlyAvailability.slots().size(),
                            oldOffset + retainedReadOnlyAvailability.pageSize());
                    if (nextOffset == oldOffset) {
                        String exhausted = slotExhaustedPrompt(turn);
                        log.info("VOICE_SLOT_CONTEXT_ACTION state=AWAIT_SLOT action=SHOW_MORE_EXHAUSTED "
                                        + "displayedPageOffset={} displayedCandidateCount={} contextPreserved=true",
                                oldOffset, displayedSlots(retainedReadOnlyAvailability).size());
                        TurnResult exhaustedResult = new TurnResult(exhausted, "NO_MORE_SLOTS", null,
                                false, false, false, null, 0, "NO_MORE_SLOTS", false, turn);
                        result.set(exhaustedResult);
                        return execution(session, exhaustedResult);
                    }
                    AvailabilityResult advanced = withDisplayOffset(retainedReadOnlyAvailability, nextOffset);
                    session = session.withVoiceAvailabilityResult(advanced);
                    String response = retainedSlotPageResponse(advanced, turn);
                    log.info("VOICE_SLOT_CONTEXT_ACTION state=AWAIT_SLOT action=SHOW_MORE "
                                    + "selectionSource=NONE displayedPageOffset={}->{} displayedCandidateCount={} "
                                    + "contextPreserved=true availabilityReloaded=false",
                            oldOffset, nextOffset, displayedSlots(advanced).size());
                    TurnResult pageResult = new TurnResult(response, "SHOW_MORE_SLOTS", null,
                            false, false, false, null, displayedSlots(advanced).size(),
                            "RETAINED_AVAILABILITY_PAGE", false, turn, null,
                            Map.of("mutationAllowed", false, "contextPreserved", true,
                                    "availabilityReloaded", false, "displayOffset", nextOffset));
                    result.set(pageResult);
                    return execution(session, pageResult);
                }
                AivaV2DisplayedSlotSelectionResolver.Resolution selection =
                        displayedSlotSelectionResolver.resolve(turn, retainedReadOnlyAvailability);
                if (selection.recognized()) {
                    AvailabilitySlot selected = selectedDisplayedSlot(retainedReadOnlyAvailability,
                            selection.selection());
                    if (selected == null) {
                        String prompt = slotContinuationClarification(turn);
                        TurnResult clarification = new TurnResult(prompt, "CLARIFY_DISPLAYED_SLOT_TIME", null,
                                false, false, false, null, 0, "DISPLAYED_SLOT_NOT_UNIQUE", false, turn,
                                null, Map.of("mutationAllowed", false, "contextPreserved", true,
                                        "availabilityReloaded", false, "state", "AWAIT_SLOT"));
                        result.set(clarification);
                        return execution(session, clarification);
                    }
                    logSlotSelection(retainedReadOnlyAvailability, selected, selection.selection());
                    turn = turn.withExactTime(selected.startsAt());
                    turn = turn.withTemporal(new NormalizedUserTurn.TemporalFact(null,
                            NormalizedUserTurn.TemporalStatus.RESOLVED, retainedReadOnlyAvailability.date(),
                            NormalizedUserTurn.RelativeKind.NONE, false));
                    NormalizedUserTurn.Daypart retainedDaypart = daypartFromWindow(
                            retainedReadOnlyAvailability.timeWindow());
                    if (retainedDaypart != null) turn = turn.withDaypart(retainedDaypart);
                    TurnResult selectedResult = new TurnResult(
                            slotSelectedForBookingResponse(retainedReadOnlyAvailability, selected, turn),
                            "BOOK_APPOINTMENT", null, true, true, false, null, 1,
                            "DISPLAYED_SLOT_SELECTED_PENDING_CANONICAL_REVALIDATION", false, turn,
                            null, Map.of("mutationAllowed", false, "canonicalSlotSelected", true,
                                    "canonicalSlotTime", selected.startsAt().toString(),
                                    "providerRetained", retainedReadOnlyAvailability.doctorId() != null,
                                    "dateRetained", retainedReadOnlyAvailability.date() != null,
                                    "daypartRetained", retainedReadOnlyAvailability.timeWindow() != null));
                    result.set(selectedResult);
                    return execution(session, selectedResult);
                }
            }
            VoiceClarificationState pendingClarification = session.voiceClarificationState();
            boolean consultationContinuation = pendingClarification != null
                    && isConsultationClarification(pendingClarification);
            if (consultationContinuation) {
                String action = pendingClarification.pendingAction();
                List<String> options = pendingClarification.candidateOptions();
                String selected = consultationOption(turn, options);
                String resolvedSpecialty = specialty(turn);
                if (selected == null && "CONSULTATION_PROVIDER".equals(action)
                        && StringUtils.hasText(resolvedSpecialty)) {
                    turn = turn.withSpecialtyEvidence(NormalizedUserTurn.entity(turn.rawText(), resolvedSpecialty))
                            .withIntent(NormalizedUserTurn.Intent.CONSULTATION_REQUEST);
                    session = session.withVoiceClarificationState(null);
                    pendingClarification = null;
                    selected = resolvedSpecialty;
                    action = "CONSULTATION_SPECIALTY_SELECTED";
                }
                if (selected == null) {
                    String prompt = "CONSULTATION_SPECIALTY".equals(action)
                            ? consultationSpecialtyPrompt(options, turn)
                            : consultationProviderPrompt(options, turn);
                    return clarify(result, session, context, turn, pendingClarification.kind(),
                            pendingClarification.unresolvedReason(), options, prompt, prompt,
                            "CONSULTATION_SPECIALTY".equals(action)
                                    ? "CONSULTATION_SPECIALTY_CLARIFICATION" : "CONSULTATION_PROVIDER_CLARIFICATION",
                            null, action);
                }
                if ("CONSULTATION_SPECIALTY".equals(action)
                        || "CONSULTATION_SPECIALTY_SELECTED".equals(action)) {
                    turn = turn.withSpecialtyEvidence(NormalizedUserTurn.entity(turn.rawText(), selected))
                            .withIntent(NormalizedUserTurn.Intent.CONSULTATION_REQUEST);
                } else {
                    turn = turn.withDoctorEvidence(selected).withIntent(NormalizedUserTurn.Intent.CONSULTATION_REQUEST);
                }
                session = session.withVoiceClarificationState(null);
                pendingClarification = null;
            }
            if (pendingClarification != null
                    && "PROVIDER_IDENTITY".equals(pendingClarification.pendingAction())) {
                TurnResult clarificationTurn = new TurnResult(providerIdentityClarification(pendingClarification, turn),
                        "BOOK_APPOINTMENT", null, true, false, false, null, 0,
                        "PROVIDER_IDENTITY_CLARIFICATION_PENDING", false, turn, null,
                        providerIdentityDiagnostics(pendingClarification));
                result.set(clarificationTurn);
                return execution(session, clarificationTurn);
            }
            if (pendingClarification != null && "SHOW_MORE".equals(pendingClarification.pendingAction())) {
                if (recognition.action() == VoiceRecognitionResult.Action.SHOW_MORE) {
                    turn = turn.withPagination(NormalizedUserTurn.Pagination.SHOW_MORE)
                            .withConfirmation(NormalizedUserTurn.Confirmation.NONE).withoutDoctorEvidence();
                    session = session.withVoiceClarificationState(null);
                } else if (recognition.action() == VoiceRecognitionResult.Action.NEGATE) {
                    turn = turn.withoutDoctorEvidence();
                    String response = continuationDeclined(turn);
                    result.set(new TurnResult(response, "CONTINUE_CONVERSATION", null,
                            false, false, false, null, 0, null, false, turn));
                    return execution(session.withVoiceClarificationState(null), result.get());
                }
            }
            logReadOnlyFlow("ADAPTER_AFTER_SEMANTIC_PARSE", context, session, turn, null, null);
            String diagnosticSpeciality = specialty(turn);
            boolean genericDoctorAvailability = isGenericDoctorAvailabilityRequest(turn, diagnosticSpeciality);
            String doctorQuery = genericDoctorAvailability ? null : doctorQuery(turn, diagnosticSpeciality);
            AvailabilityResult previousAvailability = session.latestAvailabilityResult();
            boolean noDeterministicEvidence = turn.intent() == NormalizedUserTurn.Intent.UNKNOWN
                    && turn.doctorEntity() == null && !StringUtils.hasText(turn.explicitDoctorReference())
                    && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.NONE
                    && turn.daypart() == null && turn.ordinal() == null
                    && turn.pagination() == NormalizedUserTurn.Pagination.NONE
                    && turn.confirmation() == NormalizedUserTurn.Confirmation.NONE;
            if (recognition.quality() == VoiceRecognitionResult.Quality.NO_MATCH
                    && noDeterministicEvidence && hasBookingSlotPage(session)) {
                String prompt = slotContinuationClarification(turn);
                return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                        VoiceUnresolvedReason.UNSUPPORTED_REQUEST, List.of("choose_displayed_slot", "show_more"),
                        prompt, prompt, "CLARIFY_SLOT_CONTINUATION", null, "SHOW_MORE");
            }
            String cursor = previousAvailability == null ? null : previousAvailability.cursor();
            boolean pendingDaypartChoice = "VOICE_PENDING_DAYPART".equals(cursor);
            boolean pendingDate = "VOICE_PENDING_DATE".equals(cursor);
            boolean pendingAppointmentIntent = "VOICE_PENDING_APPOINTMENT_INTENT".equals(cursor);
            boolean pendingConsultation = cursor != null && cursor.startsWith("VOICE_PENDING_CONSULTATION");
            boolean pendingSingleDaypart = cursor != null && cursor.startsWith("VOICE_CONFIRM_DAYPART:");
            boolean pendingScheduleOffer = cursor != null && (cursor.startsWith("VOICE_CONFIRM_SCHEDULE")
                    || cursor.startsWith("VOICE_CONFIRM_SCHEDULE_DAYPART:"));
            QueryType queryType = genericDoctorAvailability ? null : queryType(turn);
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
            DoctorResolution doctorResolution = genericDoctorAvailability
                    ? new DoctorResolution(null, "GENERIC_AVAILABILITY_DISCOVERY", List.of())
                    : resolveDoctor(session, turn, doctorQuery);
            ProviderCandidate currentProvider = session.activeDraft() != null
                    ? session.activeDraft().selectedProvider() : session.activeVoiceDoctor();
            if ("AMBIGUOUS".equals(doctorResolution.source())) {
                recognition = new VoiceRecognitionResult(VoiceRecognitionResult.Action.CLARIFY,
                        VoiceRecognitionResult.Quality.AMBIGUOUS,
                        VoiceRecognitionResult.Source.CANONICAL_PROVIDER,
                        "multiple canonical provider candidates", doctorResolution.candidates().stream()
                        .map(ProviderCandidate::displayName).limit(3).toList(), true);
            } else if (doctorResolution.doctor() != null) {
                recognition = new VoiceRecognitionResult(currentProvider != null
                        && !sameProvider(currentProvider, doctorResolution.doctor())
                        ? VoiceRecognitionResult.Action.PROVIDER_CHANGE
                        : VoiceRecognitionResult.Action.PROVIDER_EVIDENCE,
                        VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.CANONICAL_PROVIDER,
                        null, List.of(doctorResolution.doctor().displayName()), false);
            } else if (turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference())) {
                recognition = new VoiceRecognitionResult(VoiceRecognitionResult.Action.NONE,
                        VoiceRecognitionResult.Quality.NO_MATCH, VoiceRecognitionResult.Source.CANONICAL_PROVIDER,
                        "provider evidence did not resolve canonically", List.of(), true);
            }
            if (currentProvider != null && doctorResolution.doctor() != null
                    && !sameProvider(currentProvider, doctorResolution.doctor())) {
                SessionProjection pendingSwitchSession = beginProviderSwitch(session, currentProvider,
                        doctorResolution.doctor(), turn, context);
                String response = providerSwitchClarification(currentProvider, turn);
                result.set(new TurnResult(response, "PROVIDER_SWITCH_CONFIRMATION_REQUIRED", null,
                        false, false, false, null, 0, "PROVIDER_SWITCH_CONFIRMATION_PENDING", false, turn,
                        null, providerSwitchDiagnostics(pendingSwitchSession)));
                return execution(pendingSwitchSession, result.get());
            } else if (doctorResolution.doctor() != null) {
                session = session.withActiveVoiceDoctor(doctorResolution.doctor());
            }
            if (hasBookingSlotPage(session) && currentProvider != null && turn.doctorEntity() != null
                    && doctorResolution.doctor() == null
                    && !"AMBIGUOUS".equals(doctorResolution.source())) {
                recognition = new VoiceRecognitionResult(VoiceRecognitionResult.Action.CLARIFY,
                        VoiceRecognitionResult.Quality.NO_MATCH, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                        "provider evidence is not canonical in the active slot context", List.of(), true);
                String prompt = slotContinuationClarification(turn);
                return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                        VoiceUnresolvedReason.UNSUPPORTED_REQUEST, List.of("choose_displayed_slot", "show_more"),
                        prompt, prompt, "CLARIFY_SLOT_CONTINUATION", null, "SHOW_MORE");
            }
            logContinuationTrace(context, cursor, turn, previousAvailability, doctorResolution);

            if (isClosing(turn)) {
                logSemanticInput(context, turn, diagnosticSpeciality, "END_CONVERSATION", null,
                        "CONVERSATION_CLOSE");
                logContextTrace(context, turn, doctorQuery, doctorResolution, "END_CONVERSATION", null,
                        "CONVERSATION_CLOSED");
                result.set(new TurnResult(closingResponse(turn), "END_CONVERSATION", null,
                        false, false, false, null, 0, null, true, turn));
                return execution(session.close(), result.get());
            }

            if (turn.controls().contains(DeterministicControl.HUMAN_ASSISTANCE)) {
                VoiceClarificationState humanRequest = new VoiceClarificationState(
                        VoiceClarificationKind.INTENT, 0, VoiceUnresolvedReason.CALLER_REQUESTED_HUMAN,
                        List.of(), true);
                session = session.withVoiceClarificationState(humanRequest);
                logClarificationTrace(context, humanRequest);
                result.set(new TurnResult(humanAssistanceResponse(turn), "ESCALATION_DECISION", null,
                        true, false, false, null, 0, "CALLER_REQUESTED_HUMAN", false, turn));
                return execution(session, result.get());
            }

            if (isContextualSlotDoctorQuestion(turn)) {
                ProviderCandidate active = session.activeVoiceDoctor();
                String response = active == null ? missingDoctorClarification(turn)
                        : doctorOwnsSlotsResponse(active.displayName(), turn);
                result.set(new TurnResult(response, active == null ? "CLARIFY_DOCTOR" : "ANSWER_PROVIDER_CONTEXT",
                        null, false, false, false, null, 0, null, false, turn));
                return execution(session, result.get());
            }

            if (isIncompleteAppointmentReference(turn)) {
                String doctorName = doctorResolution.doctor() != null
                        ? doctorResolution.doctor().displayName() : doctorQuery;
                AvailabilityResult pendingIntent = appointmentIntentContext(doctorResolution, doctorQuery,
                        resolvedDate(turn, context), turn, context);
                SessionProjection pendingSession = pendingIntent == null ? session
                        : session.withVoiceAvailabilityResult(pendingIntent);
                return clarify(result, pendingSession, context, turn, VoiceClarificationKind.INTENT,
                        VoiceUnresolvedReason.AMBIGUOUS_INTENT, List.of(),
                        appointmentIntentClarification(doctorName, turn),
                        appointmentIntentClarification(doctorName, turn), "CLARIFY_APPOINTMENT_INTENT",
                        diagnosticSpeciality);
            }

            if (turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                    && isBookingRequest(turn) && currentProvider == null
                    && doctorResolution.doctor() == null
                    && !StringUtils.hasText(doctorQuery)
                    && !StringUtils.hasText(turn.explicitDoctorReference())
                    && refersToMissingProviderContext(turn)) {
                List<ProviderCandidate> priorCandidates = session.latestProviderResult() == null
                        ? List.of() : session.latestProviderResult().candidates();
                if (priorCandidates.size() > 1) {
                    List<ProviderCandidate> bounded = priorCandidates.stream().limit(3).toList();
                    return clarify(result, session, context, turn, VoiceClarificationKind.DOCTOR,
                            VoiceUnresolvedReason.AMBIGUOUS_DOCTOR,
                            bounded.stream().map(ProviderCandidate::displayName).toList(),
                            ambiguousDoctorResponse(bounded, turn), narrowedDoctorClarification(bounded, turn),
                            "CLARIFY_DOCTOR", diagnosticSpeciality);
                }
                String prompt = missingDoctorClarification(turn);
                return clarify(result, session, context, turn, VoiceClarificationKind.DOCTOR,
                        VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(), prompt, prompt,
                        "CLARIFY_DOCTOR", diagnosticSpeciality);
            }

            if (pendingDaypartChoice && isShowMore(turn)) {
                return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                        VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(),
                        daypartChoiceClarification(previousAvailability, turn),
                        daypartChoiceClarification(previousAvailability, turn), "CLARIFY_DAYPART", null);
            }

            if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE
                    && (pendingSingleDaypart || pendingDaypartChoice || pendingScheduleOffer)
                    && !explicitAvailabilityDate && turn.daypart() == null) {
                String response = continuationDeclined(turn);
                SessionProjection stable = session.withVoiceAvailabilityResult(withCursor(previousAvailability, null))
                        .withVoiceClarificationState(null);
                result.set(new TurnResult(response, "CONTINUE_CONVERSATION", null,
                        true, false, false, null, 0, null, false, turn));
                return execution(stable, result.get());
            }

            if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE && pendingScheduleOffer
                    && queryType == QueryType.SLOT_AVAILABILITY && previousAvailability != null) {
                if (previousAvailability.date() == null) {
                    SessionProjection pendingDateSession = session.withVoiceAvailabilityResult(withCursor(
                            previousAvailability, "VOICE_PENDING_DATE"));
                    return clarify(result, pendingDateSession, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(), dateClarification(turn),
                            dateClarification(turn), "CLARIFY_DATE", null);
                }
            }

            String effectiveIntent = intentName(turn);
            if (turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                    && isBookingRequest(turn)
                    && (currentProvider != null || doctorResolution.doctor() != null
                    || turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference()))) {
                effectiveIntent = "BOOK_APPOINTMENT";
            }
            AivaTransactionCapabilityGate.Decision transactionAuthorization =
                    transactionCapabilityGate.authorizeVoiceIntent(effectiveIntent, context);
            if (transactionAuthorization.requestedCapability() != null) {
                ProviderCandidate activeBeforeMutation = session.activeVoiceDoctor();
                boolean explicitSwitchFromActive = activeBeforeMutation != null
                        && !DoctorNameNormalizer.matches(activeBeforeMutation.displayName(), doctorQuery);
                if (turn.doctorEntity() != null && StringUtils.hasText(doctorQuery) && explicitSwitchFromActive
                        && doctorResolution.doctor() == null
                        && !"AMBIGUOUS".equals(doctorResolution.source())) {
                    DoctorResolution lookedUp = resolveExplicitDoctorForReadOnlyMutation(
                            context, executor, doctorQuery, diagnosticSpeciality);
                    if (lookedUp.doctor() != null) {
                        doctorResolution = lookedUp;
                        session = session.withActiveVoiceDoctor(lookedUp.doctor());
                    }
                }
                logSemanticInput(context, turn, diagnosticSpeciality, intentName(turn), intentName(turn),
                        "READ_ONLY_MUTATION_GUARD");
                String mutationResponse = mutationResponse(doctorResolution, turn);
                logContextTrace(context, turn, doctorQuery, doctorResolution, intentName(turn), intentName(turn),
                        "BLOCKED_READ_ONLY");
                Map<String, Object> blockedDiagnostics = new LinkedHashMap<>();
                blockedDiagnostics.put("activeVoiceDoctorBefore", activeBefore);
                blockedDiagnostics.put("currentExplicitDoctor", doctorQuery);
                blockedDiagnostics.put("resolvedDoctorId", doctorResolution.doctor() == null
                        ? null : doctorResolution.doctor().doctorId());
                blockedDiagnostics.put("resolvedDoctorName", doctorResolution.doctor() == null
                        ? null : doctorResolution.doctor().displayName());
                blockedDiagnostics.put("resolutionSource", doctorResolution.source());
                blockedDiagnostics.put("readOnlyDoctorResolution", StringUtils.hasText(doctorQuery)
                        ? "FIND_DOCTOR" : "NOT_REQUIRED");
                blockedDiagnostics.put("transactionAuthorization",
                        transactionAuthorization.diagnostics(context.transactionAuthorization()));
                blockedDiagnostics.put("transactionExecutionEnabled", false);
                result.set(new TurnResult(mutationResponse, intentName(turn), intentName(turn),
                        true, true, false, null, 0, "BLOCKED_READ_ONLY", false, turn, null, blockedDiagnostics));
                AvailabilityResult retainedDoctor = retainMutationContext(doctorResolution, doctorQuery,
                        previousAvailability, context);
                return execution(session.withVoiceAvailabilityResult(retainedDoctor)
                        .withVoiceClarificationState(null), result.get());
            }

            if ((queryType != null || turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                    && isBookingRequest(turn)) && "AMBIGUOUS".equals(doctorResolution.source())) {
                logSemanticInput(context, turn, diagnosticSpeciality, "CLARIFY_DOCTOR", null,
                        "AMBIGUOUS_PREVIOUS_DOCTOR_CONTEXT");
                List<ProviderCandidate> boundedCandidates = doctorResolution.candidates().stream().limit(3).toList();
                return clarify(result, session, context, turn, VoiceClarificationKind.DOCTOR,
                        VoiceUnresolvedReason.AMBIGUOUS_DOCTOR,
                        boundedCandidates.stream().map(ProviderCandidate::displayName).toList(),
                        ambiguousDoctorResponse(boundedCandidates, turn),
                        narrowedDoctorClarification(boundedCandidates, turn), "CLARIFY_DOCTOR", null);
            }

            String tool = queryType == QueryType.RECURRING_SCHEDULE ? "FIND_DOCTOR_SCHEDULE"
                    : queryType == QueryType.EFFECTIVE_DATE_SCHEDULE ? "FIND_DOCTOR_SCHEDULE"
                    : queryType != null ? "FIND_SLOTS"
                    : selectTool(turn, session, diagnosticSpeciality, doctorQuery);
            boolean consultationRequest = turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                    || consultationContinuation;
            if ("FIND_DOCTOR".equals(tool) && consultationRequest && doctorResolution.doctor() != null
                    && previousAvailability != null && previousAvailability.date() != null) {
                tool = "FIND_SLOTS";
                queryType = QueryType.SLOT_AVAILABILITY;
            }
            if ("FIND_SLOTS".equals(tool) && session.activeDraft() != null
                    && (turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference()))
                    && explicitProviderDiffers(session, doctorQuery)) {
                DoctorResolution switched = resolveExplicitDoctorForReadOnlyMutation(
                        context, executor, doctorQuery, diagnosticSpeciality);
                if (switched.doctor() != null) {
                    recognition = new VoiceRecognitionResult(VoiceRecognitionResult.Action.PROVIDER_CHANGE,
                            VoiceRecognitionResult.Quality.EXACT,
                            VoiceRecognitionResult.Source.CANONICAL_PROVIDER, null,
                            List.of(switched.doctor().displayName()), true);
                    SessionProjection pendingSwitchSession = beginProviderSwitch(session, currentProvider,
                            switched.doctor(), turn, context);
                    result.set(new TurnResult(providerSwitchClarification(currentProvider, turn),
                            "PROVIDER_SWITCH_CONFIRMATION_REQUIRED", null, false, false, false,
                            null, 0, "PROVIDER_SWITCH_CONFIRMATION_PENDING", false, turn,
                            null, providerSwitchDiagnostics(pendingSwitchSession)));
                    return execution(pendingSwitchSession, result.get());
                } else if ("AMBIGUOUS".equals(switched.source())) {
                    List<ProviderCandidate> bounded = switched.candidates().stream().limit(3).toList();
                    return clarify(result, session, context, turn, VoiceClarificationKind.DOCTOR,
                            VoiceUnresolvedReason.AMBIGUOUS_DOCTOR,
                            bounded.stream().map(ProviderCandidate::displayName).toList(),
                            ambiguousDoctorResponse(bounded, turn), narrowedDoctorClarification(bounded, turn),
                            "CLARIFY_DOCTOR", diagnosticSpeciality);
                }
            }
            if (tool == null) {
                logSemanticInput(context, turn, diagnosticSpeciality, "UNKNOWN", null,
                        "NO_SUPPORTED_TOOL_SIGNAL_AFTER_NORMALIZATION");
                logContextTrace(context, turn, doctorQuery, doctorResolution, "UNKNOWN", null, "NO_TOOL");
                boolean pendingDateAnswer = pendingDate;
                boolean pendingDaypartAnswer = pendingDaypartChoice || pendingSingleDaypart || pendingScheduleOffer;
                if (pendingDateAnswer) {
                    return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(), dateClarification(turn),
                            dateClarification(turn), "CLARIFY_DATE", null);
                }
                if (pendingDaypartAnswer) {
                    return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(),
                            daypartChoiceClarification(previousAvailability, turn),
                            daypartChoiceClarification(previousAvailability, turn), "CLARIFY_DAYPART", null);
                }
                boolean doctorUnresolved = StringUtils.hasText(doctorQuery)
                        || StringUtils.hasText(turn.explicitDoctorReference());
                if (!doctorUnresolved && previousAvailability != null && !previousAvailability.slots().isEmpty()) {
                    String prompt = slotContinuationClarification(turn);
                    return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of("choose_displayed_slot", "show_more"),
                            prompt, prompt, "CLARIFY_SLOT_CONTINUATION", null, "SHOW_MORE");
                }
                VoiceClarificationKind kind = doctorUnresolved
                        ? VoiceClarificationKind.DOCTOR : VoiceClarificationKind.UNSUPPORTED;
                VoiceUnresolvedReason reason = doctorUnresolved
                        ? VoiceUnresolvedReason.MISSING_REQUIRED_INFO : VoiceUnresolvedReason.UNSUPPORTED_REQUEST;
                String firstPrompt = doctorUnresolved ? missingDoctorClarification(turn) : unknownIntentClarification(turn);
                String retryPrompt = doctorUnresolved ? narrowerMissingDoctorClarification(turn)
                        : narrowedUnknownIntentClarification(turn);
                return clarify(result, session, context, turn, kind, reason, List.of(), firstPrompt, retryPrompt,
                        "UNKNOWN", null);
            }
            logSemanticInput(context, turn, diagnosticSpeciality, tool, tool, "TOOL_SELECTED");
            logReadOnlyFlow("ADAPTER_AFTER_DECISION", context, session, turn, intentName(turn), tool);
            logContextTrace(context, turn, doctorQuery, doctorResolution, tool, tool, "ALLOWED_READ_ONLY");
            String speciality = diagnosticSpeciality;
            String doctorId = doctorResolution.doctor() == null ? null : doctorResolution.doctor().doctorId();
            LocalDate date = resolvedDate(turn, context);
            if (date == null && previousAvailability != null
                    && ("VOICE_PROVIDER_AVAILABILITY_DATE".equals(previousAvailability.cursor())
                    || pendingConsultation || pendingScheduleOffer)) {
                date = previousAvailability.date();
            }
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
                    || pendingAppointmentIntent || pendingConsultation || pendingScheduleOffer)
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
            if (requestedDaypart == null && pendingConsultation && previousAvailability != null) {
                requestedDaypart = daypartFromWindow(previousAvailability.timeWindow());
            }
            if (requestedDaypart == null && pendingScheduleOffer && previousAvailability != null) {
                requestedDaypart = daypartFromWindow(previousAvailability.timeWindow());
            }
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
            if (turn.daypart() == null && previousAvailability != null) {
                NormalizedUserTurn.Daypart retainedDaypart = daypartFromWindow(previousAvailability.timeWindow());
                if (retainedDaypart != null && parsedTimeRange(turn) != null) turn = turn.withDaypart(retainedDaypart);
            }
            String requestedRange = parsedTimeRange(turn);
            AvailabilityTimeConstraint requestedTimeConstraint = timeConstraint(turn, requestedRange,
                    explicitMore || pendingDate ? previousAvailability : null);
            boolean currentNameCanResolve = StringUtils.hasText(doctorQuery);
            if ("FIND_SLOTS".equals(tool) && requestedTimeConstraint != null) {
                if (!StringUtils.hasText(doctorId) && !currentNameCanResolve) {
                    return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(),
                            missingDoctorClarification(turn), narrowerMissingDoctorClarification(turn),
                            "CLARIFY_DOCTOR", speciality);
                }
                // Exact/range requests still use the canonical date-specific lookup below,
                // then the Voice boundary filters the returned slots.
            }
            if ("FIND_SLOTS".equals(tool) && date == null) {
                if (!StringUtils.hasText(doctorId) && !StringUtils.hasText(doctorQuery)) {
                    return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                            VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(),
                            missingDoctorClarification(turn), narrowerMissingDoctorClarification(turn),
                            "CLARIFY_DOCTOR", speciality);
                }
                AvailabilityResult pending = new AvailabilityResult(UUID.randomUUID(), null, 0,
                        "voice-date-clarification", StringUtils.hasText(doctorId) ? doctorId : doctorQuery,
                        doctorId, null, null, requestedDaypart == null ? null : requestedDaypart.name(),
                        requestedTimeConstraint,
                        context.timezone().getId(), List.of(), "VOICE_PENDING_DATE", false, Instant.now(),
                        Instant.now().plusSeconds(5 * 60), 0, 3);
                return clarify(result, session.withVoiceAvailabilityResult(pending), context, turn,
                        VoiceClarificationKind.MISSING_INFO, VoiceUnresolvedReason.MISSING_REQUIRED_INFO,
                        List.of(), dateClarification(turn), dateClarification(turn), "CLARIFY_DATE", speciality);
            }
            if (("FIND_SLOTS".equals(tool) || "FIND_DOCTOR_SCHEDULE".equals(tool))
                    && !StringUtils.hasText(doctorId) && !currentNameCanResolve) {
                return clarify(result, session, context, turn, VoiceClarificationKind.MISSING_INFO,
                        VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(),
                        missingDoctorClarification(turn), narrowerMissingDoctorClarification(turn),
                        "CLARIFY_DOCTOR", speciality);
            }
            if ("FIND_DOCTOR".equals(tool)) {
                log.info("VOICE_FIND_DOCTOR_INPUT providerCallId={} explicitDoctorReference=\"{}\" "
                                + "doctorQueryPresent={} specialty=\"{}\"",
                        context.sessionId(), turn.explicitDoctorReference(), StringUtils.hasText(doctorQuery), speciality);
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
            AivaV2VoiceToolExecutor.ToolResult toolResult;
            Map<String, Object> toolDiagnostics = toolDiagnostics(turn, doctorResolution, session, tool,
                    speciality, doctorQuery, doctorId, date, requestedDaypart, queryType, pageOffset,
                    activeBefore, requestedTimeConstraint);
            try {
                toolResult = executor.execute(tool, context, new AivaV2VoiceToolExecutor.ToolInput(
                        doctorQuery, speciality, null, null, doctorId, date, queryType, requestedDaypart,
                        context.timezone(), pageOffset, requestedTimeConstraint));
            } catch (RuntimeException ex) {
                log.warn("VOICE_CLARIFICATION_TOOL_FAILURE providerCallId={} tool={} exception={}",
                        context.sessionId(), tool, ex.getClass().getSimpleName());
                return clarify(result, session, context, turn, VoiceClarificationKind.UNSUPPORTED,
                        VoiceUnresolvedReason.SYSTEM_ERROR, List.of(),
                        systemFailureClarification(turn), systemFailureClarification(turn),
                        "CLARIFY_SYSTEM_ERROR", speciality);
            }
            SessionProjection updated = session;
            boolean consultationClarificationRetained = false;
            if ("FIND_DOCTOR".equals(tool) && session.activeDraft() == null
                    && session.pendingConfirmation() == null) updated = updated.withVoiceAvailabilityResult(null);
            updated = rememberReadOnlyProviderResult(updated, tool, toolResult, doctorQuery, speciality, context);
            if (consultationRequest && "FIND_DOCTOR".equals(tool) && toolResult.success()) {
                List<ProviderOption> discovered = toolResult.providers();
                List<String> foundSpecialties = discovered.stream().map(ProviderOption::specialty)
                        .filter(StringUtils::hasText).map(String::trim).distinct().limit(3).toList();
                if (!StringUtils.hasText(speciality) && doctorResolution.doctor() == null
                        && foundSpecialties.size() > 1) {
                    updated = updated.withVoiceAvailabilityResult(consultationContext(date, requestedDaypart,
                            "VOICE_PENDING_CONSULTATION_SPECIALTY", null, context));
                    VoiceClarificationState state = new VoiceClarificationState(VoiceClarificationKind.INTENT,
                            1, VoiceUnresolvedReason.MISSING_REQUIRED_INFO, foundSpecialties, false,
                            "CONSULTATION_SPECIALTY", "consultation");
                    updated = updated.withVoiceClarificationState(state);
                    consultationClarificationRetained = true;
                    logClarificationTrace(context, state);
                } else if (discovered.size() > 1) {
                    List<String> names = discovered.stream().map(ProviderOption::displayName)
                            .filter(StringUtils::hasText).distinct().limit(3).toList();
                    updated = updated.withVoiceAvailabilityResult(consultationContext(date, requestedDaypart,
                            "VOICE_PENDING_CONSULTATION_PROVIDER", null, context));
                    VoiceClarificationState state = new VoiceClarificationState(VoiceClarificationKind.DOCTOR,
                            1, VoiceUnresolvedReason.AMBIGUOUS_DOCTOR, names, false,
                            "CONSULTATION_PROVIDER", "consultation");
                    updated = updated.withVoiceClarificationState(state);
                    consultationClarificationRetained = true;
                    logClarificationTrace(context, state);
                } else if (discovered.size() == 1) {
                    ProviderOption provider = discovered.getFirst();
                    String consultationCursor = "VOICE_CONFIRM_SCHEDULE";
                    updated = updated.withVoiceAvailabilityResult(consultationContext(date, requestedDaypart,
                            consultationCursor, provider.doctorId(), context));
                }
            }
            if ("FIND_DOCTOR".equals(tool) && date != null
                    && (genericDoctorAvailability || previousAvailability != null
                    && "VOICE_PROVIDER_AVAILABILITY_DATE".equals(previousAvailability.cursor()))
                    && !consultationRequest) {
                updated = updated.withVoiceAvailabilityResult(providerAvailabilityDateContext(date,
                        requestedDaypart, context));
            }
            if (toolResult.availabilityResult() != null) {
                updated = updated.withVoiceAvailabilityResult(toolResult.availabilityResult());
            } else if ("FIND_DOCTOR_SCHEDULE".equals(tool) || "FIND_SLOTS".equals(tool)) {
                updated = updated.withVoiceAvailabilityResult(null);
            }
            boolean retainProviderSwitch = session.voiceClarificationState() != null
                    && session.voiceClarificationState().pendingProviderSwitch() != null;
            if (toolResult.success() && !consultationClarificationRetained && !retainProviderSwitch) {
                updated = updated.withVoiceClarificationState(null);
                logClarificationTrace(context, null);
            }
            String response = toolResult.success() ? "" : FAILURE;
            result.set(new TurnResult(response, consultationRequest ? "CONSULT_DOCTOR" : tool, tool,
                    true, !toolResult.allowed(),
                    toolResult.success(), speciality, toolResult.candidateCount(),
                    toolResult.diagnostics(), false, turn, toolResult, toolDiagnostics));
            return execution(updated, result.get());
        });
        if (result.get() != null) {
            SessionProjection finalSession = sessionStore.find(key).orElse(null);
            if (finalSession != null) {
                Map<String, Object> diagnostics = new LinkedHashMap<>(result.get().runtimeDiagnostics());
                if (activeDoctorBefore.get() != null) diagnostics.putIfAbsent("activeVoiceDoctorBefore", activeDoctorBefore.get());
                result.set(withSessionDiagnostics(finalSession, result.get().withRuntimeDiagnostics(diagnostics)));
            }
        }
        return result.get() == null ? TurnResult.failure(FAILURE) : result.get();
    }

    private void logSemanticInput(AivaExecutionContext context, NormalizedUserTurn turn,
                                  String diagnosticSpeciality, String selectedIntent, String selectedTool,
                                  String toolSelectionReason) {
        String extractedCandidate = turn.specialtyEntity() == null ? null : turn.specialtyEntity().canonicalQuery();
        String resolverInput = turn.specialtyEntity() == null ? "NO_SPECIALTY_CANDIDATE"
                : turn.specialtyEntity().canonicalQuery();
        String resolverOutput = specialtyResolver.canonicalize(resolverInput);
        boolean resolverResolved = StringUtils.hasText(resolverOutput)
                && !resolverOutput.equalsIgnoreCase(resolverInput);
        log.info("VOICE_SEMANTIC_TRACE providerCallId={} transcriptPresent={} transcriptChars={} "
                        + "normalizedLanguage={} extractedSpecialtyCandidate=\"{}\" "
                        + "specialtyResolverInput=\"{}\" specialtyResolverResult=\"{}\" "
                        + "specialtyResolverResolved={} selectedSpecialty=\"{}\" "
                        + "explicitDoctorReference=\"{}\" selectedIntent={} selectedTool={} "
                        + "toolSelectionReason={}",
                context.sessionId(), StringUtils.hasText(turn.rawText()), turn.rawText().length(), turn.languageCode(), extractedCandidate,
                resolverInput, resolverOutput, resolverResolved, diagnosticSpeciality,
                turn.explicitDoctorReference(), selectedIntent, selectedTool, toolSelectionReason);
    }

    private void logReadOnlyFlow(String stage, AivaExecutionContext context, SessionProjection session,
            NormalizedUserTurn turn, String intent, String tool) {
        ProviderCandidate active = session.activeVoiceDoctor();
        var draft = session.activeDraft();
        ProviderCandidate bookingProvider = draft == null ? null : draft.selectedProvider();
        String temporalSource = turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? "CURRENT_TURN" : session.latestAvailabilityResult() != null
                && session.latestAvailabilityResult().date() != null ? "SESSION_CONTEXT" : "NONE";
        log.info("AIVA_V2_VOICE_FLOW stage={} tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                        + "normalizedLanguage={} explicitDoctorReference={} activeVoiceDoctorId={} activeVoiceDoctorName={} "
                        + "bookingProviderId={} bookingProviderName={} providerResolved={} intent={} tool={} activeWorkflow={} "
                        + "currentTurnDate={} effectiveDate={} draftDate={} datePresent={} temporalSource={} daypart={} exactTime={} "
                        + "rangeStart={} rangeEnd={} slotSelected={} selectedSlotRef={} draftRevision={} draftStatus={} "
                        + "transactionAuthorizationSource={} decisionCategory={} executionDecision={} stateSourceForProvider={} "
                        + "stateSourceForDate={} stateSourceForWorkflow={}",
                stage, context.tenantId(), context.providerCallId(), AivaV2VoiceFlowTrace.conversationHash(session.conversationId()),
                AivaV2VoiceFlowTrace.turnId(), AivaV2VoiceFlowTrace.turnNumber(), turn.languageCode(),
                turn.explicitDoctorReference(), active == null ? null : active.doctorId(),
                active == null ? null : active.displayName(), bookingProvider == null ? null : bookingProvider.doctorId(),
                bookingProvider == null ? null : bookingProvider.displayName(), bookingProvider != null,
                intent, tool, session.activeWorkflow(), turn.temporal().localDate(),
                turn.temporal().localDate() != null ? turn.temporal().localDate()
                        : session.latestAvailabilityResult() == null ? null : session.latestAvailabilityResult().date(),
                draft == null ? null : draft.preferredDate(), draft != null && draft.preferredDate() != null,
                temporalSource, turn.daypart(), turn.exactTime(),
                draft == null || draft.availabilityTimeConstraint() == null ? null
                        : draft.availabilityTimeConstraint().startTime(),
                draft == null || draft.availabilityTimeConstraint() == null ? null
                        : draft.availabilityTimeConstraint().endTime(),
                draft != null && StringUtils.hasText(draft.selectedSlotReference()),
                draft == null ? null : draft.selectedSlotReference(), draft == null ? null : draft.revision(),
                draft == null ? null : draft.status(), context.transactionAuthorization() == null ? null
                        : context.transactionAuthorization().authorizationSource(),
                intent, tool == null ? "NOT_EXECUTED" : "ROUTED",
                bookingProvider != null ? "BOOKING_DRAFT" : active != null ? "VOICE_ACTIVE_DOCTOR" : "NONE",
                temporalSource, session.activeWorkflow());
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
                                                              String doctorQuery, String specialty,
                                                              AivaExecutionContext context) {
        if (result == null || !("FIND_DOCTOR".equals(tool) || "FIND_DOCTOR_SCHEDULE".equals(tool)
                || "FIND_SLOTS".equals(tool))) {
            return session;
        }
        // The durable active doctor is independent of workflow projections. Any
        // single-provider result may refresh it; empty schedule/slot projections
        // never erase it. A genuinely ambiguous provider search clears selection.
        List<ProviderCandidate> candidates = result.providers().stream().map(this::providerCandidate).toList();
        if (candidates.size() == 1 && session.activeDraft() == null) {
            ProviderCandidate candidate = candidates.getFirst();
            ProviderCandidate active = session.activeVoiceDoctor();
            boolean explicitSwitch = StringUtils.hasText(doctorQuery) || StringUtils.hasText(specialty);
            if (active == null || active.doctorId().equals(candidate.doctorId()) || explicitSwitch) {
                session = session.withActiveVoiceDoctor(candidate);
            }
            AivaV2Models.BookingDraft draft = session.activeDraft();
            if (draft != null && (draft.selectedProvider() == null
                    || draft.selectedProvider().source() != ProviderSource.CARE_PRIVATE
                    || (StringUtils.hasText(doctorQuery)
                    && !draft.selectedProvider().doctorId().equals(candidate.doctorId())))) {
                session = adoptCanonicalBookingProvider(session, draft, candidate, context,
                        "FIND_DOCTOR_RESULT".equals(tool) ? "FIND_DOCTOR_RESULT" : "FIND_SLOTS_RESULT");
            }
        } else if ("FIND_DOCTOR".equals(tool) && candidates.size() > 1 && session.activeDraft() == null) {
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

    private SessionProjection adoptCanonicalBookingProvider(SessionProjection session, AivaV2Models.BookingDraft draft,
            ProviderCandidate provider, AivaExecutionContext context, String adoptionSource) {
        ProviderCandidate canonical = resolveAuthoritativeBookingProvider(context, provider);
        log.info("AIVA_V2_PROVIDER_ADOPTION tenantId={} doctorId={} incomingProviderSource={} canonicalProviderSource={} "
                        + "incomingBookingReferencePresent={} canonicalBookingReferencePresent={} adoptionSource={} "
                        + "canonicalResolutionApplied={} draftId={} draftRevision={}",
                context == null ? null : context.tenantId(), provider == null ? null : provider.doctorId(),
                provider == null ? null : provider.source(), canonical == null ? null : canonical.source(),
                provider != null && provider.bookingReference() != null,
                canonical != null && canonical.bookingReference() != null, adoptionSource,
                canonical != null && context != null && context.tenantId() != null
                        && context.tenantId().toString().equals(canonical.tenantId()),
                draft == null ? null : draft.draftId(), draft == null ? null : draft.revision());
        if (session == null || draft == null || canonical == null) return session;
        if (canonical.equals(draft.selectedProvider())) return session;
        AivaV2Models.BookingDraft changed = new AivaV2Models.BookingDraft(draft.draftId(),
                draft.patientSubjectId(), draft.tenantScope(), canonical, draft.specialtyFilter(),
                draft.preferredDate(), draft.preferredTimeWindow(), draft.exactTime(),
                draft.availabilityTimeConstraint(), null, null, AivaV2Models.DraftStatus.COLLECTING,
                draft.revision() + 1, draft.expiresAt(), null);
        return session.withBookingDraft(changed).withVoiceAvailabilityResult(null);
    }

    private boolean explicitProviderDiffers(SessionProjection session, String explicitDoctor) {
        if (!StringUtils.hasText(explicitDoctor)) return false;
        ProviderCandidate active = session.activeVoiceDoctor();
        ProviderCandidate booking = session.activeDraft() == null ? null : session.activeDraft().selectedProvider();
        return (active != null && !DoctorNameNormalizer.matches(active.displayName(), explicitDoctor))
                || (booking != null && !DoctorNameNormalizer.matches(booking.displayName(), explicitDoctor));
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
                || cursor.startsWith("VOICE_PENDING_CONSULTATION")
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
        if (parsedTimeRange(turn) != null) return true;
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

    private boolean isContextualSlotDoctorQuestion(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:ये|यह|इन|किस|कौन).*?(?:डॉक्टर|doctor).*?(?:स्लॉट|slot).*|"
                + ".*(?:which|what)\\s+doctor.*(?:slots?).*|"
                + ".*\\bwhich\\s+doctor\\s+is\\s+this\\s+for\\b.*|"
                + ".*\\bwhat\\s+doctor\\s+is\\s+selected\\b.*|"
                + ".*कौन\\s+सा\\s+डॉक्टर\\s+selected.*|.*किस\\s+डॉक्टर\\s+का\\s+(?:slot|स्लॉट).*");
    }

    private String doctorOwnsSlotsResponse(String doctorName, NormalizedUserTurn turn) {
        if (isEnglish(turn)) return "These slots are for Dr. " + doctorName + ".";
        if (isHinglish(turn)) return "Ye slots Dr. " + doctorName + " ke hain.";
        return "ये डॉ. " + doctorName + " के slots हैं।";
    }

    private String exactTimeUnavailableResponse(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "I can currently filter appointment availability by morning, afternoon, or evening, but not by an exact time or time range."
                : isHinglish(turn) ? "Abhi main slots ko morning, afternoon, ya evening ke hisaab se filter kar sakti hoon, exact time ya range ke hisaab se nahi."
                : "अभी मैं slots को सुबह, दोपहर या शाम के अनुसार देख सकती हूँ; किसी खास समय या समय-सीमा के अनुसार filter उपलब्ध नहीं है।";
    }

    private DoctorResolution resolveExplicitDoctorForReadOnlyMutation(
            AivaExecutionContext context, AivaV2VoiceToolExecutor executor, String query, String specialty) {
        try {
            AivaV2VoiceToolExecutor.ToolResult result = executor.execute("FIND_DOCTOR", context,
                    new AivaV2VoiceToolExecutor.ToolInput(query, specialty, null, null, null, null,
                            QueryType.SLOT_AVAILABILITY, null, context.timezone(), 0));
            if (result == null || !result.success()) return new DoctorResolution(null, "CURRENT_EXPLICIT", List.of());
            List<ProviderCandidate> matches = result.providers().stream()
                    .filter(provider -> DoctorNameNormalizer.matches(provider.displayName(), query))
                    .map(this::providerCandidate)
                    .toList();
            if (matches.size() == 1) return new DoctorResolution(matches.getFirst(), "CURRENT_EXPLICIT", List.of());
            if (matches.size() > 1) return new DoctorResolution(null, "AMBIGUOUS", matches);
        } catch (RuntimeException ex) {
            log.info("VOICE_EXPLICIT_DOCTOR_RESOLUTION_FAILED exception={}", ex.getClass().getSimpleName());
        }
        return new DoctorResolution(null, "CURRENT_EXPLICIT", List.of());
    }

    private ProviderCandidate providerCandidate(AivaV2VoiceToolExecutor.ProviderOption option) {
        return new ProviderCandidate(option.doctorId(), option.doctorId(), option.doctorId(), option.clinicId(),
                option.tenantId(), option.clinicSlug(), option.bookingReference(), option.displayName(),
                option.specialty(), option.clinicName(),
                option.source() == null ? ProviderSource.CARE_PRIVATE : option.source(), option.capabilities());
    }

    private Map<String, Object> toolDiagnostics(NormalizedUserTurn turn, DoctorResolution resolution,
            SessionProjection session, String tool, String specialty, String doctorQuery, String doctorId,
            LocalDate date, NormalizedUserTurn.Daypart daypart, QueryType queryType, int offset,
            String activeDoctorBefore, AvailabilityTimeConstraint timeConstraint) {
        Map<String, Object> values = new LinkedHashMap<>();
        String explicitDate = explicitDate(turn);
        AvailabilityResult prior = session.latestAvailabilityResult();
        values.put("language", turn.responseLanguage());
        values.put("responseStyle", turn.responseStyle().name());
        values.put("explicitDoctorReference", turn.explicitDoctorReference());
        values.put("doctorFilter", doctorQuery);
        values.put("activeVoiceDoctorBefore", activeDoctorBefore);
        values.put("resolvedDoctorId", doctorId);
        values.put("resolvedDoctorName", resolution.doctor() == null ? null : resolution.doctor().displayName());
        values.put("activeVoiceDoctorAfter", session.activeVoiceDoctor() == null
                ? null : session.activeVoiceDoctor().displayName());
        values.put("resolutionSource", resolution.source());
        values.put("explicitDate", explicitDate);
        values.put("effectiveDate", date == null ? null : date.toString());
        values.put("temporalSource", explicitDate != null ? "CURRENT_TURN"
                : prior != null && prior.date() != null && date != null ? "SESSION_CONTEXT" : "NONE");
        values.put("dateOverrideApplied", explicitDate != null && prior != null && prior.date() != null
                && date != null && !prior.date().equals(date));
        values.put("parsedDaypart", turn.daypart() == null ? null : turn.daypart().name());
        values.put("effectiveDaypart", daypart == null ? null : daypart.name());
        values.put("parsedExactTime", turn.exactTime() == null ? null : turn.exactTime().toString());
        values.put("parsedTimeRange", parsedTimeRange(turn));
        values.put("requestedExactTime", timeConstraint != null
                && timeConstraint.mode() == AvailabilityTimeConstraintMode.EXACT
                ? timeConstraint.startTime().toString() : null);
        values.put("requestedRangeStart", timeConstraint != null
                && timeConstraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                ? timeConstraint.startTime().toString() : null);
        values.put("requestedRangeEnd", timeConstraint != null
                && timeConstraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                ? timeConstraint.endTime().toString() : null);
        values.put("slotSelectionSource", timeConstraint != null
                ? timeConstraint.mode() == AvailabilityTimeConstraintMode.EXACT ? "EXACT_TIME" : "TIME_RANGE"
                : daypart != null ? "DAYPART" : "UNFILTERED");
        values.put("recognizedSpecialty", specialty);
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        values.put("providerEvidence", turn.doctorEntity() != null || turn.specialtyEntity() != null
                || text.matches("(?s).*(?:doctor|physician|डॉक्टर|चिकित्सक).*"));
        values.put("slotAvailabilityEvidence", text.matches("(?s).*(?:slot|availability|available|स्लॉट|अवेलेबल|उपलब्ध).*"));
        values.put("selectedQueryType", queryType == null ? null : queryType.name());
        values.put("routingReason", queryType == null ? "DETERMINISTIC_TOOL_SELECTION" : "QUERY_TYPE_" + queryType.name());
        values.put("toolInput", Map.ofEntries(
                Map.entry("tool", tool), Map.entry("specialty", specialty == null ? "" : specialty),
                Map.entry("doctorId", doctorId == null ? "" : doctorId),
                Map.entry("doctorReference", doctorQuery == null ? "" : doctorQuery),
                Map.entry("date", date == null ? "" : date.toString()),
                Map.entry("daypart", daypart == null ? "" : daypart.name()),
                Map.entry("exactTime", timeConstraint != null
                        && timeConstraint.mode() == AvailabilityTimeConstraintMode.EXACT
                        ? timeConstraint.startTime().toString() : ""),
                Map.entry("timeRange", timeConstraint != null
                        && timeConstraint.mode() == AvailabilityTimeConstraintMode.BETWEEN
                        ? timeConstraint.startTime() + "-" + timeConstraint.endTime() : ""),
                Map.entry("constraintHandling", timeConstraint == null ? "NONE"
                        : "CANONICAL_SLOTS_THEN_VOICE_FILTER"),
                Map.entry("pageOffset", offset), Map.entry("pageSize", 3)));
        values.put("responseSource", "TOOL_RESULT_RENDERER");
        values.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(values);
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
                || raw.matches("(?iu)^\\s*(?:और\\s*(?:समय|स्लॉट)(?:\\s*(?:बताइए|बताओ|दिखाइए|दिखाओ))?|"
                + "अगले\\s+स्लॉट|दूसरे\\s+समय\\s*(?:बताइए|बताओ)|"
                + "aur\\s+(?:slots?|time)(?:\\s+(?:batao|dikhao))?|"
                + "more\\s+slots?|show\\s+more\\s+slots?|other\\s+slots?|next\\s+slots?)[.!?।]*\\s*$")
                || raw.matches("(?iu)^\\s*(?:or\\s+slots?(?:\\s*,?\\s*tell\\s+me)?|other\\s+slots?(?:\\s*,?\\s*tell\\s+me)?|aur\\s+slots?|और\\s+स्लॉट(?:\\s+बताओ)?|और\\s+समय|अगले\\s+स्लॉट)[.!?।]*\\s*$")
                || raw.matches("(?s).*(?:show\\s+(?:me\\s+)?more|more\\s+slots?|next(?:\\s+slots?)?|other\\s+times|"
                + "और\\s*(?:समय|स्लॉट|बताइए|दिखाओ)|दूसरे\\s+slots?|अगला\\s*(?:स्लॉट\\s*)?(?:बताओ|बताइए|दिखाओ)|"
                + "नेक्स्ट\\s+स्लॉट\\s*(?:बताओ|बताइए|दिखाओ)|"
                + "aur\\s*(?:slots?|batao|samay|dikhao)|next\\s+slots?\\s+(?:dikhao|batao)|doosre\\s+(?:time|slots?)).*" );
    }

    private boolean hasRetainedReadOnlySlotPage(SessionProjection session) {
        if (session == null || session.closed() || session.activeDraft() != null) return false;
        AvailabilityResult availability = session.latestAvailabilityResult();
        return availability != null && availability.draftId() == null
                && availability.date() != null && StringUtils.hasText(availability.doctorId())
                && availability.slots().size() > availability.displayOffset()
                && availability.pageSize() > 0
                && (availability.expiresAt() == null || availability.expiresAt().isAfter(Instant.now()))
                && !"VOICE_PENDING_DAYPART".equals(availability.cursor());
    }

    private boolean retainedSlotContextMatches(AvailabilityResult availability, NormalizedUserTurn turn) {
        if (availability == null || turn == null) return false;
        LocalDate requestedDate = turn.temporal() == null ? null : turn.temporal().localDate();
        if (requestedDate != null && !requestedDate.equals(availability.date()) && hasExplicitDateLanguage(turn.rawText())) {
            return false;
        }
        NormalizedUserTurn.Daypart requestedDaypart = turn.daypart();
        NormalizedUserTurn.Daypart retainedDaypart = daypartFromWindow(availability.timeWindow());
        boolean explicitDaypart = turn.rawText() != null && turn.rawText().matches(
                "(?iu).*(?:\\b(?:morning|evening|night|subah|shaam|raat)\\b|सुबह|शाम|रात).*" );
        return !explicitDaypart || requestedDaypart == null || retainedDaypart == null
                || requestedDaypart == retainedDaypart;
    }

    private boolean hasConflictingProviderEvidence(SessionProjection session, NormalizedUserTurn turn) {
        String evidence = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().canonicalQuery();
        if (!StringUtils.hasText(evidence)) return false;
        AivaV2DisplayedSlotSelectionResolver.Resolution selection = displayedSlotSelectionResolver.resolve(
                turn, session.latestAvailabilityResult());
        if (selectedDisplayedSlot(session.latestAvailabilityResult(), selection.selection()) != null
                && isClockWordProviderArtifact(evidence)) return false;
        ProviderCandidate current = session.activeVoiceDoctor();
        return current == null || !DoctorNameNormalizer.matches(current.displayName(), evidence);
    }

    private boolean isClockWordProviderArtifact(String evidence) {
        String normalized = evidence.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:dr\\.?|doctor)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normalized.matches("(?iu)(?:[1-12]|one|two|three|four|five|six|seven|eight|nine|ten|"
                + "eleven|twelve|ek|do|teen|chaar|char|paanch|panch|chhah|cheh|saat|aath|nau|das|"
                + "gyarah|baarah|एक|दो|तीन|चार|पाँच|पांच|छह|छः|सात|आठ|नौ|दस|ग्यारह|बारह)(?:\\s+(?:"
                + "thirty|tees|तीस|baje|बजे))?" );
    }

    private boolean hasExplicitDateLanguage(String raw) {
        return raw != null && raw.matches("(?iu).*(?:\\b(?:today|tomorrow|day after tomorrow|aaj|kal|parso|parson)\\b|"
                + "आज|कल|परसों|परसो|\\b\\d{1,2}\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|"
                + "may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b).*" );
    }

    private List<AvailabilitySlot> displayedSlots(AvailabilityResult availability) {
        if (availability == null || availability.slots().isEmpty()) return List.of();
        int start = Math.min(availability.displayOffset(), availability.slots().size());
        int end = Math.min(availability.slots().size(), start + availability.pageSize());
        return availability.slots().subList(start, end);
    }

    private AvailabilitySlot selectedDisplayedSlot(AvailabilityResult availability,
            AivaV2Models.Selection selection) {
        if (selection == null) return null;
        List<AvailabilitySlot> page = displayedSlots(availability);
        if (StringUtils.hasText(selection.exactTime())) {
            try {
                LocalTime requested = LocalTime.parse(selection.exactTime());
                List<AvailabilitySlot> matches = page.stream()
                        .filter(candidate -> requested.equals(candidate.startsAt())).toList();
                return matches.size() == 1 ? matches.getFirst() : null;
            } catch (RuntimeException invalidTime) {
                return null;
            }
        }
        if (selection.ordinal() == null) return null;
        int index = selection.ordinal() == -1 ? page.size() - 1 : selection.ordinal() - 1;
        return index >= 0 && index < page.size() ? page.get(index) : null;
    }

    private AvailabilityResult withDisplayOffset(AvailabilityResult source, int offset) {
        return new AvailabilityResult(source.requestId(), source.draftId(), source.draftRevision(),
                source.criteriaFingerprint(), source.providerHandle(), source.doctorId(), source.clinicId(),
                source.date(), source.timeWindow(), source.availabilityTimeConstraint(), source.timezone(),
                source.slots(), source.cursor(), offset + source.pageSize() < source.slots().size(),
                source.generatedAt(), source.expiresAt(), offset, source.pageSize());
    }

    private String retainedSlotPageResponse(AvailabilityResult availability, NormalizedUserTurn turn) {
        boolean english = isEnglish(turn);
        boolean hinglish = isHinglish(turn);
        boolean hindi = !english;
        List<String> labels = displayedSlots(availability).stream()
                .map(slot -> spokenTime(slot.startsAt(), hindi, hinglish)).toList();
        String times = String.join(english ? ", " : " और ", labels);
        String date = AivaSpokenDateFormatter.format(availability.date(), turn.responseLanguage(), turn.responseStyle());
        if (english) return "Available slots for " + date + " are: " + times
                + ". Choose one, or say ‘more slots’.";
        if (hinglish) return date + " ke available slots hain: " + times
                + ". Koi slot choose karein, ya ‘aur slots batao’ kahiye.";
        return date + " के उपलब्ध समय हैं: " + times
                + "। इनमें से कोई समय चुनिए, या और समय के लिए ‘और समय बताइए’ कहें।";
    }

    private String slotSelectedForBookingResponse(AvailabilityResult availability, AvailabilitySlot selected,
            NormalizedUserTurn turn) {
        boolean english = isEnglish(turn);
        boolean hinglish = isHinglish(turn);
        boolean hindi = !english;
        String date = AivaSpokenDateFormatter.format(availability.date(), turn.responseLanguage(), turn.responseStyle());
        String time = spokenTime(selected.startsAt(), hindi, hinglish);
        if (english) return "I’ll verify the " + time + " slot for " + date + " before asking you to confirm.";
        if (hinglish) return date + " ke " + time + " slot ko verify karke aapse final confirmation loongi.";
        return date + " के " + time + " वाले समय की उपलब्धता जाँचकर आपसे अंतिम पुष्टि लूँगी।";
    }

    private void logSlotSelection(AvailabilityResult availability, AvailabilitySlot selected,
            AivaV2Models.Selection selection) {
        String source = StringUtils.hasText(selection.exactTime()) ? "TIME"
                : selection.ordinal() != null && selection.ordinal() == -1 ? "LAST" : "ORDINAL";
        log.info("VOICE_SLOT_CONTEXT_ACTION state=AWAIT_SLOT action=SELECT_SLOT selectionSource={} "
                        + "displayedPageOffset={} displayedCandidateCount={} contextPreserved=true",
                source, availability.displayOffset(), displayedSlots(availability).size());
        log.info("VOICE_SLOT_SELECTION spokenNormalized={} canonicalSlotSelected=true canonicalStart={} "
                        + "providerRetained={} dateRetained={} daypartRetained={}",
                selected.startsAt(), selected.startsAt(), availability.doctorId() != null,
                availability.date() != null, StringUtils.hasText(availability.timeWindow()));
    }

    /** Context-bound deterministic recognition; raw STT text is evidence, never provider truth. */
    private VoiceRecognitionResult recognizeContextBoundControls(SessionProjection session, NormalizedUserTurn turn) {
        String raw = turn.rawText() == null ? "" : turn.rawText().trim();
        boolean slotContext = AivaV2VoiceSlotContext.isAwaitSlot(session, Instant.now());
        boolean explicitNextDate = AivaV2VoiceSlotContext.explicitlySearchesNextDate(raw);
        if (slotContext && raw.matches("(?iu).*(?:और|aur)\\s*(?:(?:प्लॉट|plot|स्लॉट|slots?)\\s*)?(?:बताओ|बताइए|दिखाओ|dikhao|batao).*")) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SHOW_MORE,
                    VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
        }
        if (slotContext && !explicitNextDate && isShowMore(turn)) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SHOW_MORE,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
        }
        if (!explicitNextDate && turn.pagination() == NormalizedUserTurn.Pagination.SHOW_MORE) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SHOW_MORE,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.CONFIRM,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.NEGATE,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        if (slotContext && displayedSlotSelectionResolver.resolve(turn,
                session.latestAvailabilityResult()).recognized()) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SELECT_SLOT,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.DISPLAYED_CHOICES);
        }
        if (turn.ordinal() != null && slotContext) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SELECT_SLOT,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
        }
        if (turn.temporal() != null && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.DATE_EVIDENCE,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        if (turn.daypart() != null) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.DAYPART_EVIDENCE,
                    VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        if (turn.doctorEntity() != null || StringUtils.hasText(turn.explicitDoctorReference())) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.PROVIDER_EVIDENCE,
                    VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
        }
        return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.NONE,
                VoiceRecognitionResult.Quality.NO_MATCH, VoiceRecognitionResult.Source.LANGUAGE_ADAPTER);
    }

    /** Pending clarification answers outrank global confirmation/provider parsing. */
    private VoiceRecognitionResult recognizePendingClarification(VoiceClarificationState pending,
                                                                  NormalizedUserTurn turn) {
        if (pending == null) return null;
        if ("PROVIDER_IDENTITY".equals(pending.pendingAction())) {
            List<VoiceProviderIdentityCandidate> candidates = pending.canonicalProviderCandidates();
            if (pending.confirmSingleProviderCandidate() && candidates.size() == 1
                    && turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
                return new VoiceRecognitionResult(VoiceRecognitionResult.Action.PROVIDER_EVIDENCE,
                        VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                        null, List.of(candidates.getFirst().displayName()), false);
            }
            if (pending.confirmSingleProviderCandidate() && turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
                return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.NEGATE,
                        VoiceRecognitionResult.Quality.EXACT, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
            }
            if (turn.confirmation() != NormalizedUserTurn.Confirmation.NONE) {
                return new VoiceRecognitionResult(VoiceRecognitionResult.Action.CLARIFY,
                        VoiceRecognitionResult.Quality.AMBIGUOUS, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                        "yes/no cannot choose among multiple provider candidates", pending.candidateOptions(), true);
            }
            Integer ordinal = turn.ordinal();
            if (ordinal != null && ordinal >= 1 && ordinal <= candidates.size()) {
                return new VoiceRecognitionResult(VoiceRecognitionResult.Action.PROVIDER_EVIDENCE,
                        VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                        null, List.of(candidates.get(ordinal - 1).displayName()), false);
            }
            String evidence = pendingProviderEvidence(turn);
            if (StringUtils.hasText(evidence) && DoctorNameNormalizer.isPlausibleReference(evidence)) {
                List<String> matches = candidates.stream()
                        .filter(candidate -> DoctorNameNormalizer.matches(candidate.displayName(), evidence))
                        .map(VoiceProviderIdentityCandidate::displayName).toList();
                if (matches.size() == 1) return new VoiceRecognitionResult(
                        VoiceRecognitionResult.Action.PROVIDER_EVIDENCE, VoiceRecognitionResult.Quality.STRONG,
                        VoiceRecognitionResult.Source.CURRENT_CONTEXT, null, matches, false);
                if (matches.size() > 1) return new VoiceRecognitionResult(
                        VoiceRecognitionResult.Action.PROVIDER_EVIDENCE, VoiceRecognitionResult.Quality.AMBIGUOUS,
                        VoiceRecognitionResult.Source.CURRENT_CONTEXT, "answer matches multiple stored candidates",
                        matches, true);
            }
            return new VoiceRecognitionResult(VoiceRecognitionResult.Action.CLARIFY,
                    VoiceRecognitionResult.Quality.NO_MATCH, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                    "answer did not match stored provider candidates", pending.candidateOptions(), true);
        }
        if (!"SHOW_MORE".equals(pending.pendingAction())) return null;
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.SHOW_MORE,
                    VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
        }
        if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
            return VoiceRecognitionResult.of(VoiceRecognitionResult.Action.NEGATE,
                    VoiceRecognitionResult.Quality.STRONG, VoiceRecognitionResult.Source.CURRENT_CONTEXT);
        }
        return new VoiceRecognitionResult(VoiceRecognitionResult.Action.CLARIFY,
                VoiceRecognitionResult.Quality.NO_MATCH, VoiceRecognitionResult.Source.CURRENT_CONTEXT,
                "pending SHOW_MORE clarification expects yes or no", pending.candidateOptions(), true);
    }

    private String pendingProviderEvidence(NormalizedUserTurn turn) {
        String evidence = turn.doctorEntity() == null ? turn.explicitDoctorReference()
                : turn.doctorEntity().canonicalQuery();
        if (StringUtils.hasText(evidence)) return evidence;
        return DoctorNameNormalizer.providerEvidence(turn.rawText())
                .replaceFirst("(?iu)\\s+(?:ka|ki|ke)\\s+(?:appointment|booking).*$", "")
                .replaceFirst("(?iu)\\s+(?:appointment|booking)\\b.*$", "")
                .replaceFirst("(?iu)\\s+chahiye\\b.*$", "").trim();
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

    private String slotContinuationClarification(NormalizedUserTurn turn) {
        return isEnglish(turn)
                ? "Would you like to choose one of the displayed times, or see more slots?"
                : isHinglish(turn)
                ? "Dikhaaye gaye time mein se koi slot chunna hai, ya aur slots dekhne hain?"
                : "दिए गए समय में से कोई स्लॉट चुनना चाहते हैं, या और स्लॉट देखना चाहते हैं?";
    }

    private String slotContinuationRetryClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Please say either 'choose a displayed time' or 'show more slots'."
                : isHinglish(turn) ? "In dono mein se ek kahein: dikhaya gaya time chunna hai, ya aur slots dekhne hain."
                : "कृपया इन दो में से एक कहें: दिया गया समय चुनना है, या और स्लॉट देखने हैं।";
    }

    private String slotContinuationFallback(NormalizedUserTurn turn) {
        return isEnglish(turn)
                ? "I have kept your current appointment selection. Say 'more slots', change the date, or choose a displayed time."
                : isHinglish(turn)
                ? "Aapka current appointment selection surakshit hai. 'Aur slots', date badlein, ya dikhaye gaye time mein se chunein."
                : "आपका मौजूदा appointment selection सुरक्षित है। 'और स्लॉट', तारीख बदलें, या दिए गए समय में से चुनें।";
    }

    private String missingDoctorClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Which doctor's slots would you like to check?"
                : isHinglish(turn) ? "Aap kis doctor ke slots dekhna chahenge?"
                : "आप किस डॉक्टर के slots देखना चाहेंगे?";
    }

    private String narrowerMissingDoctorClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Please name the doctor whose availability you want to check."
                : isHinglish(turn) ? "Jis doctor ke slots chahiye, unka naam bata dijiye."
                : "कृपया उस डॉक्टर का नाम बताइए जिनके उपलब्ध समय देखने हैं।";
    }

    private String unknownIntentClarification(NormalizedUserTurn turn) {
        return isEnglish(turn)
                ? "Would you like information about a doctor, available appointment times, or the clinic?"
                : isHinglish(turn)
                ? "Aap doctor, available slots, ya clinic ke baare mein jaanna chahte hain?"
                : "क्या आप डॉक्टर की जानकारी, उपलब्ध slots, या clinic के बारे में जानना चाहते हैं?";
    }

    private String narrowedUnknownIntentClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "I can help find a doctor, check available times, or share clinic information. Which one do you need?"
                : isHinglish(turn) ? "Main doctor dhoondhne, available slots dekhne, ya clinic ki jaankari dene mein madad kar sakti hoon. Aapko kya chahiye?"
                : "मैं डॉक्टर ढूँढ़ने, उपलब्ध समय देखने, या clinic की जानकारी देने में मदद कर सकती हूँ। आपको किसमें मदद चाहिए?";
    }

    private String narrowedDoctorClarification(List<ProviderCandidate> candidates, NormalizedUserTurn turn) {
        if (candidates == null || candidates.isEmpty()) return narrowerMissingDoctorClarification(turn);
        String names = candidates.stream().limit(3).map(ProviderCandidate::displayName)
                .filter(StringUtils::hasText).reduce((left, right) -> left + ", " + right).orElse("");
        return isEnglish(turn) ? "Please choose one doctor: " + names + "."
                : isHinglish(turn) ? "In doctors mein se ek ka naam batayein: " + names + "."
                : "कृपया इन डॉक्टरों में से एक का नाम बताइए: " + names + "।";
    }

    private String humanAssistanceResponse(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "I can try to get a person to help you."
                : isHinglish(turn) ? "Main kisi person se madad dilane ki koshish kar sakti hoon."
                : "मैं किसी व्यक्ति से मदद दिलाने की कोशिश कर सकती हूँ।";
    }

    private String systemFailureClarification(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "I couldn't retrieve that information. Would you like to try again or ask about something else?"
                : isHinglish(turn) ? "Abhi jaankari nahi mil paayi. Kya aap phir se koshish karna chahenge ya kuch aur poochhenge?"
                : "क्षमा कीजिए, अभी जानकारी नहीं मिल पाई। क्या आप दोबारा कोशिश करना चाहेंगे या कुछ और पूछेंगे?";
    }

    private AivaV2SessionStore.TurnExecution clarify(
            AtomicReference<TurnResult> resultRef,
            SessionProjection session,
            AivaExecutionContext context,
            NormalizedUserTurn turn,
            VoiceClarificationKind kind,
            VoiceUnresolvedReason reason,
            List<String> options,
            String firstPrompt,
            String retryPrompt,
            String clarificationIntent,
            String specialty
    ) {
        return clarify(resultRef, session, context, turn, kind, reason, options, firstPrompt,
                retryPrompt, clarificationIntent, specialty, null);
    }

    private AivaV2SessionStore.TurnExecution clarify(
            AtomicReference<TurnResult> resultRef,
            SessionProjection session,
            AivaExecutionContext context,
            NormalizedUserTurn turn,
            VoiceClarificationKind kind,
            VoiceUnresolvedReason reason,
            List<String> options,
            String firstPrompt,
            String retryPrompt,
            String clarificationIntent,
            String specialty,
            String pendingAction
    ) {
        VoiceClarificationState previous = session.voiceClarificationState();
        boolean sameIssue = previous != null && previous.kind() == kind
                && previous.unresolvedReason() == reason;
        int previousAttempts = sameIssue ? previous.attemptCount() : 0;
        boolean escalate = sameIssue && (previous.escalationDecision() || previousAttempts >= 2);
        VoiceClarificationState next;
        String response;
        String intent = clarificationIntent;
        if (escalate) {
            next = new VoiceClarificationState(kind, Math.min(previousAttempts, 2),
                    VoiceUnresolvedReason.REPEATED_CLARIFICATION_FAILURE, options, true);
            response = humanAssistanceResponse(turn);
            intent = "ESCALATION_DECISION";
        } else {
            int attempts = previousAttempts + 1;
            next = new VoiceClarificationState(kind, attempts, reason, options, false, pendingAction,
                    session.activeWorkflow());
            response = attempts == 1 ? firstPrompt : retryPrompt;
        }
        logClarificationTrace(context, next);
        TurnResult turnResult = new TurnResult(response, intent, null,
                true, false, false, specialty, 0,
                next.escalationDecision() ? next.unresolvedReason().name() : reason.name(), false, turn);
        resultRef.set(turnResult);
        return execution(session.withVoiceClarificationState(next), turnResult);
    }

    private void logClarificationTrace(AivaExecutionContext context, VoiceClarificationState state) {
        log.info("VOICE_CLARIFICATION_TRACE providerCallId={} kind={} attemptCount={} unresolvedReason={} escalationDecision={}",
                context.sessionId(), state == null ? "NONE" : state.kind(),
                state == null ? 0 : state.attemptCount(),
                state == null ? "NONE" : state.unresolvedReason(),
                state != null && state.escalationDecision());
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

    private boolean sameProvider(ProviderCandidate left, ProviderCandidate right) {
        if (left.doctorId() != null && right.doctorId() != null) return left.doctorId().equals(right.doctorId());
        return DoctorNameNormalizer.matches(left.displayName(), right.displayName());
    }

    private String providerSwitchClarification(ProviderCandidate active, NormalizedUserTurn turn) {
        String name = active.displayName();
        if (isEnglish(turn)) return "Dr. " + name + " is currently selected. Would you like to continue with this doctor or change doctors?";
        if (isHinglish(turn)) return "Abhi Dr. " + name + " selected hain. Kya aap inke saath continue karna chahte hain ya doctor badalna chahte hain?";
        return "अभी डॉक्टर " + name + " selected हैं. क्या आप इन्हीं के साथ continue करना चाहते हैं या doctor बदलना चाहते हैं?";
    }

    private String providerSwitchWarning(ProviderCandidate active, ProviderCandidate target,
                                         NormalizedUserTurn turn) {
        return providerSwitchWarning(active, target == null ? null : target.displayName(), turn);
    }

    private String providerSwitchWarning(ProviderCandidate active, String targetName,
                                         NormalizedUserTurn turn) {
        String from = active == null ? "current doctor" : active.displayName();
        String to = StringUtils.hasText(targetName) ? targetName : "the other doctor";
        if (isEnglish(turn)) return "Your appointment flow is currently with Dr. " + from
                + ". Switching to Dr. " + to + " will reset the current slot selection. Would you like to switch? Please say yes or no.";
        if (isHinglish(turn)) return "Abhi aapka appointment flow Dr. " + from
                + " ke saath hai. Dr. " + to + " par switch karne se current slot selection reset ho jayegi. Kya aap switch karna chahenge? Haan ya nahi boliye.";
        return "अभी आपका appointment flow डॉक्टर " + from
                + " के साथ चल रहा है. डॉक्टर " + to + " पर switch करने से current slot selection reset हो जाएगी. क्या आप switch करना चाहते हैं? हाँ या नहीं बताइए।";
    }

    private String providerSwitchDeclined(NormalizedUserTurn turn) {
        if (isEnglish(turn)) return "Okay. I have kept your current doctor and appointment selection unchanged.";
        if (isHinglish(turn)) return "Theek hai. Aapka current doctor aur appointment selection waise hi rakha hai.";
        return "ठीक है। आपका मौजूदा डॉक्टर और appointment selection वैसे ही सुरक्षित है।";
    }

    private boolean isSameProviderReaffirmation(ProviderCandidate active, String providerEvidence,
            NormalizedUserTurn turn) {
        String raw = turn == null || turn.rawText() == null ? "" : turn.rawText().toLowerCase(Locale.ROOT);
        if (active == null) return false;
        if (matchesCanonicalProviderReference(active.displayName(), providerEvidence)
                || matchesCanonicalProviderReference(active.displayName(), turn.rawText())) return true;
        if (raw.matches("(?s).*(?:\\b(?:same doctor|that doctor|that provider|that doctor|his|her|their|that one)\\b|"
                + "उनका|उनकी|उनके|उनसे|इसी\\s*(?:doctor|डॉक्टर)|वही\\s*(?:doctor|डॉक्टर)|"
                + "\\b(?:unka|unki|unke|unse)\\b).*")) return true;
        return raw.matches("(?s).*(?:\\bhi\\b|\\bhee\\b|\\bsame\\b|\\bonly\\b|ही|वही|ही चाहिए).*")
                && DoctorNameNormalizer.matches(active.displayName(), providerEvidence == null ? raw : providerEvidence);
    }

    private boolean matchesCanonicalProviderReference(String canonicalName, String evidence) {
        if (!StringUtils.hasText(canonicalName) || !StringUtils.hasText(evidence)) return false;
        String bounded = evidence.trim()
                .replaceFirst("(?iu)^\\s*(?:no|नहीं|नही)\\s*[,;:]?\\s*", "")
                .replaceFirst("(?iu)\\s*(?:का|की|के|का appointment.*|की appointment.*|के साथ.*|"
                        + "appointment.*|अपॉइंटमेंट.*|\u0936\u093e\u092e.*|\u0938\u0941\u092c\u0939.*|"
                        + "evening.*|morning.*|afternoon.*|same doctor|same provider|only|ही चाहिए|ही|वही)\\s*[.!?।]*$", "")
                .trim();
        return DoctorNameNormalizer.matches(canonicalName, bounded);
    }

    private SessionProjection beginProviderSwitch(SessionProjection session, ProviderCandidate active,
            ProviderCandidate target, NormalizedUserTurn turn, AivaExecutionContext context) {
        LocalDate explicitDate = hasDateExpression(turn) ? resolvedDate(turn, context) : null;
        String explicitWindow = turn.daypart() == null ? null : turn.daypart().name().toLowerCase(Locale.ROOT);
        SessionProjection updated = AivaV2VoiceContextTransitions.requestProviderSwitch(session, target,
                context.tenantId(), session.activeWorkflow(), explicitDate, explicitWindow, turn.exactTime());
        PendingProviderSwitch pending = updated.voiceClarificationState() == null ? null
                : updated.voiceClarificationState().pendingProviderSwitch();
        if (pending != null) log.info("AIVA_V2_PROVIDER_SWITCH event=PROVIDER_SWITCH_REQUESTED currentProviderId={} targetProviderId={} sourceContext={} reason={}",
                pending.currentProviderId(), pending.targetProviderId(), pending.originatingContext(), pending.reason());
        return updated;
    }

    /** Declining a switch keeps the old canonical provider but carries forward explicit time context. */
    private SessionProjection retainProviderSwitchContextOnDecline(SessionProjection session,
            PendingProviderSwitch pending, NormalizedUserTurn turn, AivaExecutionContext context) {
        if (session == null || pending == null || session.activeDraft() == null) {
            return session == null ? null : session.withVoiceClarificationState(null);
        }
        AivaV2Models.BookingDraft draft = session.activeDraft();
        LocalDate explicitDate = hasExplicitTemporalOrDaypartEvidence(turn)
                && turn.temporal() != null && turn.temporal().status() == NormalizedUserTurn.TemporalStatus.RESOLVED
                ? turn.temporal().localDate() : null;
        LocalDate nextDate = explicitDate != null ? explicitDate
                : pending.requestedDate() == null ? draft.preferredDate() : pending.requestedDate();
        String explicitWindow = turn != null && turn.daypart() != null
                ? turn.daypart().name().toLowerCase(Locale.ROOT) : null;
        String nextWindow = explicitWindow != null ? explicitWindow : pending.requestedTimeWindow() == null
                ? draft.preferredTimeWindow() : pending.requestedTimeWindow();
        LocalTime nextExactTime = turn != null && turn.exactTime() != null ? turn.exactTime()
                : pending.requestedExactTime() == null ? draft.exactTime() : pending.requestedExactTime();
        boolean dateChanged = !java.util.Objects.equals(draft.preferredDate(), nextDate);
        boolean daypartChanged = !java.util.Objects.equals(draft.preferredTimeWindow(), nextWindow);
        boolean exactTimeChanged = !java.util.Objects.equals(draft.exactTime(), nextExactTime);
        if (!dateChanged && !daypartChanged && !exactTimeChanged) return session.withVoiceClarificationState(null);
        AivaV2Models.BookingDraft retained = new AivaV2Models.BookingDraft(draft.draftId(),
                draft.patientSubjectId(), draft.tenantScope(), draft.selectedProvider(), draft.specialtyFilter(),
                nextDate, nextWindow, nextExactTime, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, draft.revision() + 1, draft.expiresAt(), null);
        return session.withPendingBookingConfirmation(null, retained)
                .withVoiceAvailabilityResult(null).withVoiceClarificationState(null);
    }

    private SessionProjection clearSelectedSlotForContextChange(SessionProjection session) {
        if (session == null) return null;
        AivaV2Models.BookingDraft draft = session.activeDraft();
        if (draft == null) return session.withPendingBookingConfirmation(null, null)
                .withVoiceAvailabilityResult(null);
        AivaV2Models.BookingDraft collecting = new AivaV2Models.BookingDraft(draft.draftId(),
                draft.patientSubjectId(), draft.tenantScope(), draft.selectedProvider(), draft.specialtyFilter(),
                draft.preferredDate(), draft.preferredTimeWindow(), null, null, null, null,
                AivaV2Models.DraftStatus.COLLECTING, draft.revision() + 1, draft.expiresAt(), null);
        return session.withPendingBookingConfirmation(null, collecting).withVoiceAvailabilityResult(null);
    }

    private SessionProjection clearSlotForProviderChange(SessionProjection session, AivaExecutionContext context) {
        if (session == null || session.activeDraft() != null) return clearSelectedSlotForContextChange(session);
        AvailabilityResult retained = session.latestAvailabilityResult();
        if (retained == null || retained.date() == null
                || retained.expiresAt() != null && !retained.expiresAt().isAfter(Instant.now())) {
            return clearSelectedSlotForContextChange(session);
        }
        AvailabilityResult temporalContext = providerAvailabilityTemporalContext(retained.date(),
                retained.timeWindow(), context);
        return session.withPendingBookingConfirmation(null, null).withVoiceAvailabilityResult(temporalContext);
    }

    private AvailabilityResult providerAvailabilityTemporalContext(LocalDate date, String timeWindow,
            AivaExecutionContext context) {
        Instant now = Instant.now();
        String zone = context == null || context.timezone() == null ? "Asia/Kolkata" : context.timezone().getId();
        return new AvailabilityResult(UUID.randomUUID(), null, 0L,
                "provider-change-date-context|" + (context == null ? "voice" : context.tenantId()) + "|" + date,
                null, null, null, date, timeWindow, null, zone, List.of(),
                "VOICE_PROVIDER_AVAILABILITY_DATE", false, now, now.plusSeconds(10 * 60), 0, 3);
    }

    private VoiceClarificationState contextChangeClarification(String action, String resumeContext) {
        return new VoiceClarificationState(VoiceClarificationKind.MISSING_INFO, 0,
                VoiceUnresolvedReason.MISSING_REQUIRED_INFO, List.of(), false, action,
                resumeContext, List.of(), false);
    }

    private String changeProviderPrompt(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Which doctor would you like instead?"
                : isHinglish(turn) ? "Aap kis doctor ko choose karna chahenge?"
                : "आप किस डॉक्टर को चुनना चाहेंगे?";
    }

    private String changeDatePrompt(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "What date would you like to check instead?"
                : isHinglish(turn) ? "Aap kis date ke slots dekhna chahenge?"
                : "आप किस तारीख के समय देखना चाहेंगे?";
    }

    private String changeDaypartPrompt(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Would you prefer morning, afternoon, or evening?"
                : isHinglish(turn) ? "Aap morning, afternoon, ya evening mein se kya prefer karenge?"
                : "आप सुबह, दोपहर या शाम में से कौन-सा समय चाहेंगे?";
    }

    private void logContextAction(String action, ProviderCandidate activeBefore, ProviderCandidate activeAfter,
            LocalDate dateBefore, LocalDate dateAfter, String daypartBefore, String daypartAfter,
            String invalidationReason) {
        log.info("VOICE_CONTEXT_ACTION contextAction={} pendingClarificationType={} activeProviderBefore={} "
                        + "requestedProvider={} activeProviderAfter={} dateBefore={} dateAfter={} "
                        + "daypartBefore={} daypartAfter={} slotInvalidatedReason={}",
                action, action, activeBefore == null ? null : activeBefore.doctorId(), null,
                activeAfter == null ? null : activeAfter.doctorId(), dateBefore, dateAfter,
                daypartBefore, daypartAfter, invalidationReason);
    }

    private Map<String, Object> providerSwitchDiagnostics(SessionProjection session) {
        PendingProviderSwitch pending = session.voiceClarificationState() == null ? null
                : session.voiceClarificationState().pendingProviderSwitch();
        if (pending == null) return Map.of("mutationAllowed", false, "contextPreserved", true);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("mutationAllowed", false);
        diagnostics.put("clarificationType", "PROVIDER_SWITCH");
        diagnostics.put("pendingClarificationType", "PROVIDER_SWITCH_CONFIRMATION");
        diagnostics.put("pendingAction", "PROVIDER_SWITCH_CONFIRMATION");
        diagnostics.put("currentProviderId", pending.currentProviderId());
        diagnostics.put("activeProviderBefore", pending.currentProviderId());
        diagnostics.put("requestedProvider", pending.targetProviderId());
        diagnostics.put("targetProviderId", pending.targetProviderId());
        diagnostics.put("activeProviderAfter", pending.currentProviderId());
        diagnostics.put("dateBefore", pending.requestedDate() == null ? null : pending.requestedDate().toString());
        diagnostics.put("dateAfter", pending.requestedDate() == null ? null : pending.requestedDate().toString());
        diagnostics.put("daypartBefore", pending.requestedTimeWindow());
        diagnostics.put("daypartAfter", pending.requestedTimeWindow());
        diagnostics.put("originatingContext", pending.originatingContext());
        diagnostics.put("contextPreserved", true);
        diagnostics.put("availabilityReloaded", false);
        diagnostics.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(diagnostics);
    }

    private ProviderCandidate revalidateProviderSwitchTarget(AivaExecutionContext context,
            PendingProviderSwitch pending) {
        if (context == null || pending == null || !context.tenantId().equals(pending.tenantId())) return null;
        ProviderSearchResult evidence = transactionalBoundary.resolveVoiceProviderEvidence(
                context.tenantId(), pending.targetDisplayName());
        if (evidence == null || evidence.status() != ResolutionStatus.RESOLVED
                || evidence.resolvedProvider() == null
                || !pending.targetProviderId().equals(evidence.resolvedProvider().doctorId())) return null;
        ProviderCandidate canonical = transactionalBoundary.resolveVoiceBookingProvider(
                context.tenantId(), evidence.resolvedProvider());
        return canonical != null && pending.targetProviderId().equals(canonical.doctorId())
                && pending.tenantId().toString().equals(canonical.tenantId()) ? canonical : null;
    }

    private ConversationDecision providerSwitchCommitDecision(NormalizedUserTurn turn, String canonicalName) {
        AivaV2Models.BookingPatch patch = new AivaV2Models.BookingPatch(
                AivaV2Models.ValuePatch.set(canonicalName), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.ValuePatch.unchanged(), AivaV2Models.ValuePatch.unchanged(),
                AivaV2Models.AvailabilityTimeConstraintPatch.unchanged());
        return new ConversationDecision(AivaV2Models.DECISION_SCHEMA_VERSION,
                AivaV2Models.DialogAct.CHANGE_INFORMATION, AivaV2Models.Operation.UPDATE_BOOKING,
                patch, null, AivaV2Models.ConfirmationPolarity.NONE, AivaV2Models.TopicAction.CONTINUE,
                turn.responseLanguage(), 1d, "CONFIRMED_PROVIDER_SWITCH_CANONICAL_ADOPTION", false, 0L, null, false);
    }

    private VoiceClarificationState providerIdentityState(List<ProviderCandidate> candidates, UUID tenantId,
                                                           String resumeContext) {
        List<VoiceProviderIdentityCandidate> safe = candidates == null ? List.of() : candidates.stream()
                .filter(candidate -> candidate != null && StringUtils.hasText(candidate.doctorId())
                        && StringUtils.hasText(candidate.displayName())
                        && candidate.source() == ProviderSource.CARE_PRIVATE
                        && tenantId != null && tenantId.toString().equals(candidate.tenantId()))
                .limit(3)
                .map(candidate -> new VoiceProviderIdentityCandidate(candidate.doctorId(), tenantId,
                        candidate.displayName())).toList();
        List<String> names = safe.stream().map(VoiceProviderIdentityCandidate::displayName).toList();
        return new VoiceClarificationState(VoiceClarificationKind.PROVIDER_IDENTITY, 1,
                VoiceUnresolvedReason.AMBIGUOUS_DOCTOR, names, false, "PROVIDER_IDENTITY",
                resumeContext, safe, safe.size() == 1);
    }

    /** Resolves only against the canonical tenant-bound candidates already stored in the clarification. */
    private PendingProviderCandidateResolution resolvePendingProviderCandidate(AivaExecutionContext context,
            VoiceClarificationState clarification, NormalizedUserTurn turn) {
        List<VoiceProviderIdentityCandidate> candidates = clarification.canonicalProviderCandidates();
        if (candidates == null || candidates.isEmpty()) return new PendingProviderCandidateResolution(null, true);
        if (clarification.confirmSingleProviderCandidate() && candidates.size() == 1) {
            if (turn.confirmation() == NormalizedUserTurn.Confirmation.POSITIVE) {
                ProviderCandidate canonical = revalidateProviderCandidate(context, candidates.getFirst());
                return new PendingProviderCandidateResolution(canonical, canonical == null);
            }
            if (turn.confirmation() == NormalizedUserTurn.Confirmation.NEGATIVE) {
                return new PendingProviderCandidateResolution(null, false);
            }
        }
        if (turn.confirmation() != NormalizedUserTurn.Confirmation.NONE) return new PendingProviderCandidateResolution(null, false);
        Integer ordinal = turn.ordinal();
        if (ordinal != null && ordinal >= 1 && ordinal <= candidates.size()) {
            ProviderCandidate canonical = revalidateProviderCandidate(context, candidates.get(ordinal - 1));
            return new PendingProviderCandidateResolution(canonical, canonical == null);
        }
        String evidence = pendingProviderEvidence(turn);
        if (!StringUtils.hasText(evidence) || !DoctorNameNormalizer.isPlausibleReference(evidence)) {
            return new PendingProviderCandidateResolution(null, false);
        }
        String boundedEvidence = evidence;
        List<VoiceProviderIdentityCandidate> matches = candidates.stream()
                .filter(candidate -> DoctorNameNormalizer.matches(candidate.displayName(), boundedEvidence)).toList();
        if (matches.size() != 1) return new PendingProviderCandidateResolution(null, false);
        ProviderCandidate canonical = revalidateProviderCandidate(context, matches.getFirst());
        return new PendingProviderCandidateResolution(canonical, canonical == null);
    }

    private ProviderCandidate revalidateProviderCandidate(AivaExecutionContext context,
            VoiceProviderIdentityCandidate selected) {
        if (context == null || selected == null || !context.tenantId().equals(selected.tenantId())) return null;
        ProviderSearchResult result = transactionalBoundary.resolveVoiceProviderEvidence(
                context.tenantId(), selected.displayName());
        if (result == null || result.status() != ResolutionStatus.RESOLVED || result.resolvedProvider() == null
                || !selected.providerId().equals(result.resolvedProvider().doctorId())) return null;
        ProviderCandidate canonical = transactionalBoundary.resolveVoiceBookingProvider(
                context.tenantId(), result.resolvedProvider());
        return canonical != null && selected.providerId().equals(canonical.doctorId())
                && selected.tenantId().toString().equals(canonical.tenantId()) ? canonical : null;
    }

    private String providerIdentityClarification(VoiceClarificationState clarification, NormalizedUserTurn turn) {
        List<String> names = clarification == null ? List.of() : clarification.candidateOptions();
        if (clarification != null && clarification.confirmSingleProviderCandidate() && names.size() == 1) {
            String name = DoctorNameNormalizer.providerEvidence(names.getFirst());
            if (isEnglish(turn)) return "Do you mean Dr. " + name + "? Please say yes or no.";
            if (isHinglish(turn)) return "Kya aap Dr. " + name + " ki baat kar rahe hain? Haan ya nahi boliye.";
            return "क्या आप डॉक्टर " + name + " की बात कर रहे हैं? हाँ या नहीं बताइए।";
        }
        String pair = names.stream().limit(3).map(name -> {
            String displayName = DoctorNameNormalizer.providerEvidence(name);
            if (isEnglish(turn)) return "Dr. " + displayName;
            if (isHinglish(turn)) return displayName;
            return "डॉक्टर " + displayName;
        }).reduce((left, right) -> left + (isEnglish(turn) ? " or " : isHinglish(turn) ? " ya " : " या ") + right)
                .orElse("");
        if (isEnglish(turn)) return StringUtils.hasText(pair)
                ? "Which doctor do you mean — " + pair + "? Please say a name or first/second."
                : "Which doctor do you mean?";
        if (isHinglish(turn)) return StringUtils.hasText(pair)
                ? pair + " — kis doctor ke saath continue karna chahenge? Name ya first/second boliye."
                : "Kis doctor ke saath continue karna chahenge?";
        return StringUtils.hasText(pair) ? "कृपया बताइए, आप " + pair
                + " में से किस डॉक्टर की बात कर रहे हैं? पहला या दूसरा भी कह सकते हैं।"
                : "कृपया डॉक्टर का नाम बताइए।";
    }

    private String providerIdentityDeclined(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "Okay. I have kept your current appointment details unchanged."
                : isHinglish(turn) ? "Theek hai. Aapki current appointment details waise hi rakhi hain."
                : "ठीक है। आपकी मौजूदा appointment details सुरक्षित रखी हैं।";
    }

    private String providerIdentityExpired(NormalizedUserTurn turn) {
        return isEnglish(turn) ? "That doctor list is no longer current. Please tell me the doctor name again."
                : isHinglish(turn) ? "Woh doctor list ab current nahi hai. Doctor ka naam phir se batayein."
                : "डॉक्टरों की पिछली सूची अब उपलब्ध नहीं है। कृपया डॉक्टर का नाम फिर से बताइए।";
    }

    private Map<String, Object> providerIdentityDiagnostics(VoiceClarificationState clarification) {
        return providerIdentityDiagnostics(clarification, VoiceRecognitionResult.Quality.AMBIGUOUS);
    }

    private Map<String, Object> providerIdentityDiagnostics(VoiceClarificationState clarification,
            VoiceRecognitionResult.Quality quality) {
        return Map.of("mutationAllowed", false, "clarificationType", "PROVIDER_IDENTITY",
                "pendingAction", "PROVIDER_IDENTITY", "recognitionQuality", quality.name(),
                "recognitionAction", "PROVIDER_EVIDENCE", "recognitionSource", "CANONICAL_PROVIDER",
                "clarificationRecommended", true,
                "canonicalCandidates", clarification.canonicalProviderCandidates().stream()
                        .map(candidate -> Map.of("providerId", candidate.providerId(),
                                "tenantId", candidate.tenantId().toString(), "displayName", candidate.displayName())).toList(),
                "contextPreserved", true);
    }

    private boolean isSlotFollowUp(NormalizedUserTurn turn) {
        return hasExplicitSlotEvidence(turn);
    }

    private String mutationResponse(DoctorResolution resolution, NormalizedUserTurn turn) {
        String intent = intentName(turn);
        if ("CONSULT_DOCTOR".equals(intent) && isBookingRequest(turn)
                && resolution != null && resolution.doctor() != null) intent = "BOOK_APPOINTMENT";
        if ("CANCEL_APPOINTMENT".equals(intent)) {
            return unsupportedScopeResponse(turn, "CANCEL_NOT_SUPPORTED");
        }
        if ("RESCHEDULE_APPOINTMENT".equals(intent)) {
            return unsupportedScopeResponse(turn, "RESCHEDULE_NOT_SUPPORTED");
        }
        if ("FIND_APPOINTMENTS".equals(intent)) {
            return unsupportedScopeResponse(turn, "PATIENT_DATA_NOT_SUPPORTED");
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
                        + "intent={} selectedTool={} policyResult={} doctorQueryPresent={}",
                context.sessionId(), turn.explicitDoctorReference(), resolution.previousDoctorName(),
                resolution.doctor() == null ? null : resolution.doctor().displayName(), resolution.source(),
                intent, tool, policy, StringUtils.hasText(doctorQuery));
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
        if (isGenericDoctorAvailabilityRequest(turn, speciality)) return "FIND_DOCTOR";
        if (turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST) return "FIND_DOCTOR";
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

    private AvailabilityResult providerAvailabilityDateContext(LocalDate date,
            NormalizedUserTurn.Daypart daypart, AivaExecutionContext context) {
        Instant now = Instant.now();
        return new AvailabilityResult(UUID.randomUUID(), null, 0L,
                "provider-availability-date|" + context.tenantId() + "|" + date,
                null, null, null, date, daypart == null ? null : daypart.name().toLowerCase(Locale.ROOT), null,
                context.timezone() == null ? "Asia/Kolkata" : context.timezone().getId(), List.of(),
                "VOICE_PROVIDER_AVAILABILITY_DATE", false, now, now.plusSeconds(10 * 60), 0, 3);
    }

    private boolean isGenericDoctorAvailabilityRequest(NormalizedUserTurn turn, String speciality) {
        if (turn == null || !hasDateExpression(turn) || !hasAvailabilityLanguage(turn)) return false;
        String value = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean genericQuestion = value.matches("(?s).*(?:\\bwhich\\s+doctor\\b|\\bwhat\\s+doctor\\b|"
                + "\\bwhich\\s+doctors\\b|\\bwhat\\s+doctors\\b|\\bkaunse\\s+doctor\\b|"
                + "\\bkaun\\s+se\\s+doctor\\b|\\bkaun\\s+sa\\s+doctor\\b|कौन\\s+से\\s+डॉक्टर|"
                + "कौन\\s+सा\\s+डॉक्टर|कौन\\s+से\\s+डॉक्टर\\s+उपलब्ध|\\bdoctors?\\s+available\\b).*" );
        // A specialty is a discovery filter, not a canonical provider identity.
        return genericQuestion || StringUtils.hasText(speciality)
                && !StringUtils.hasText(doctorQuery(turn, speciality));
    }

    private boolean hasExplicitSlotEvidence(NormalizedUserTurn turn) {
        if (parsedTimeRange(turn) != null) return true;
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
        String utterance = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        if (utterance.matches("(?s).*(?:\\bchild\\s+doctor\\b|\\bchildren's\\s+doctor\\b|"
                + "बच्चे\\s+के\\s+डॉक्टर|बच्चों\\s+के\\s+डॉक्टर|बच्चों\\s+का\\s+डॉक्टर).*")) {
            return "Pediatrics";
        }
        if (utterance.matches("(?s).*(?:\\bgeneral\\s+medicine\\b|\\bgeneral\\s+physician\\b|\\bgeneral\\s+doctor\\b|\\bphysician\\b|"
                + "जनरल\\s+मेडिसिन|जनरल\\s+फिजिशियन|जनरल\\s+डॉक्टर|सामान्य\\s+चिकित्सक).*")) return "General Medicine";
        if (utterance.matches("(?s).*(?:\\bpediatric(?:s|ian)?\\b|\\bpaediatric(?:s|ian)?\\b|"
                + "\\bchild(?:ren)?'s\\s+doctor\\b|बाल\\s*रोग(?:\\s+विशेषज्ञ)?|पीडियाट्रिक(?:\\s+डॉक्टर)?).*")
                || raw != null && raw.matches("(?iu).*(?:बाल\\s*रोग|पीडियाट्रिक).*")) {
            return "Pediatrics";
        }
        String canonical = specialtyResolver.canonicalize(raw);
        if (StringUtils.hasText(canonical) && !canonical.equalsIgnoreCase(turn.rawText())) return canonical;

        // Preserve these established spoken discovery terms at the V2 Voice
        // boundary without changing the legacy planner's specialty vocabulary.
        if (utterance.matches("(?s).*(?:\\bpediatric(?:s|ian)?\\b|\\bpaediatric(?:s|ian)?\\b|"
                + "\\bchild(?:ren)?'s\\s+doctor\\b|बाल\\s*रोग(?:\\s+विशेषज्ञ)?|पीडियाट्रिक(?:\\s+डॉक्टर)?).*")) {
            return "Pediatrics";
        }
        if (utterance.matches("(?s).*(?:\\bcardiologist\\b|\\bcardiology\\b|कार्डियोलॉजिस्ट|कार्डियोलॉजी).*")) {
            return "Cardiology";
        }
        if (utterance.matches("(?s).*(?:\\bdermatologist\\b|\\bdermatology\\b|\\bskin\\s+doctor\\b|त्वचा रोग विशेषज्ञ).*")) {
            return "Dermatology";
        }
        return null;
    }

    private String resolvedDoctorId(SessionProjection session) {
        if (session.activeVoiceDoctor() != null) return session.activeVoiceDoctor().doctorId();
        return session.latestProviderResult() == null || session.latestProviderResult().resolvedProvider() == null
                ? null : session.latestProviderResult().resolvedProvider().doctorId();
    }

    private boolean isClosing(NormalizedUserTurn turn) {
        if (turn == null || turn.rawText() == null) return false;
        String raw = turn.rawText().toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[,;.!?।]+", " ")
                .replaceAll("\\s+", " ").trim();
        return raw.matches("(?iu)(?:thank you(?: ji)?|thanks(?: a lot| ji)?|okay thank you|ok thank you|"
                + "okay thanks|ok thanks|no thank you|no thanks|that'?s all thank you|that is all thank you|"
                + "i'?m done thanks|i am done thanks|that'?s all|that is all|bye(?: ji)?|goodbye|"
                + "धन्यवाद|शुक्रिया|ठीक है धन्यवाद|बस धन्यवाद|नहीं धन्यवाद|नही धन्यवाद|"
                + "मुझे मदद नहीं चाहिए धन्यवाद|बस इतना ही|ठीक है|अलविदा|"
                + "thik hai thanks|bas thank you|nahi thanks|nahin thanks|"
                + "mujhe aur help nahi chahiye thanks|okay धन्यवाद)")
                || raw.matches("(?iu).*(?:[, ]+)(?:thank you(?: ji)?|thanks(?: ji)?|धन्यवाद|शुक्रिया)$");
    }

    private String closingResponse(NormalizedUserTurn turn) {
        if (isEnglish(turn)) return "Thank you. Take care.";
        if (isHinglish(turn)) return "Thank you. Apna dhyan rakhiye.";
        return "धन्यवाद। स्वस्थ रहिए।";
    }

    private boolean isBlockedMutation(NormalizedUserTurn turn) {
        return isBookingRequest(turn)
                || turn.intent() == NormalizedUserTurn.Intent.CANCELLATION
                || turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE
                || turn.intent() == NormalizedUserTurn.Intent.LOOKUP
                && !isAppointmentAvailabilityRequest(turn);
    }

    private String intentName(NormalizedUserTurn turn) {
        if (turn.intent() == NormalizedUserTurn.Intent.CANCELLATION) return "CANCEL_APPOINTMENT";
        if (turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE) return "RESCHEDULE_APPOINTMENT";
        if (turn.intent() == NormalizedUserTurn.Intent.CONSULTATION_REQUEST
                && !hasExplicitBookingVerb(turn) && turn.doctorEntity() == null
                && !StringUtils.hasText(turn.explicitDoctorReference())) return "CONSULT_DOCTOR";
        // A dated request for future availability is a read-only slot query,
        // even when the language adapter sees the generic "appointment" noun
        // and labels it as booking or lookup. Explicit patient-owned history
        // remains an appointment lookup and is deliberately checked first.
        if (!isPatientOwnedAppointmentLookup(turn) && isDatedAppointmentAvailability(turn)) {
            return "FIND_SLOTS";
        }
        if (hasExplicitBookingVerb(turn)) return "BOOK_APPOINTMENT";
        if (isPatientOwnedAppointmentLookup(turn)) return "FIND_APPOINTMENTS";
        if (isBookingRequest(turn)) return "BOOK_APPOINTMENT";
        return switch (turn.intent()) {
            case BOOKING -> "BOOK_APPOINTMENT";
            case CONSULTATION_REQUEST -> "CONSULT_DOCTOR";
            case CANCELLATION -> "CANCEL_APPOINTMENT";
            case RESCHEDULE -> "RESCHEDULE_APPOINTMENT";
            case LOOKUP -> "FIND_APPOINTMENTS";
            default -> "UNKNOWN";
        };
    }

    private boolean isDatedAppointmentAvailability(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean appointmentMention = text.matches("(?s).*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*");
        return appointmentMention && hasDateExpression(turn) && isAppointmentAvailabilityRequest(turn);
    }

    private boolean isPatientOwnedAppointmentLookup(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean appointmentMention = text.matches("(?s).*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*");
        boolean bookingMention = text.matches("(?s).*(?:\\bbookings?\\b|\\bbooking\\b|\\breservations?\\b|बुकिंग|बुकिंग्स?).*");
        // Hindi/Hinglish callers commonly say "meri booking" in Latin script.
        bookingMention = bookingMention || text.matches("(?s).*\\bbooking\\b.*");
        if (!appointmentMention && !bookingMention) return false;
        boolean ownership = text.matches("(?s).*(?:\\bmy\\b|\\bmeri\\b|\\bmera\\b|\\bmere\\b|मेरी|मेरा|मेरे|अपनी|अपना|अपने).*");
        boolean explicitStatus = text.matches("(?s).*(?:\\bappointment\\s+status\\b|\\bbooking\\s+status\\b|"
                + "\\bstatus\\s+(?:of|for)\\s+(?:my\\s+)?(?:appointment|booking)\\b|"
                + "अपॉइंटमेंट\\s+(?:का\\s+)?स्टेटस|बुकिंग\\s+(?:का\\s+)?स्टेटस).*");
        boolean history = text.matches("(?s).*(?:\\bbooked\\b|\\bscheduled\\b|\\bupcoming\\b|"
                + "\\bappointment\\s+history\\b|\\b(?:old|previous|existing)\\s+appointments?\\b|"
                + "पुरानी\\s+(?:अपॉइंटमेंट|appointment|बुकिंग)|पुराने\\s+अपॉइंटमेंट|"
                + "\\bwhat is my\\b|\\bis my\\b|\\bare my\\b|\\bdo i have\\b|\\bhave i got\\b|"
                + "\\bappointment\\s+status\\b|\\bbooking\\s+status\\b|बुक्ड|बुक हुई|बुक किया).*");
        // A first-person appointment booking request is still a new booking;
        // ownership language alone must not route it to unsupported history.
        return explicitStatus || history || ownership && !hasExplicitBookingVerb(turn);
    }

    private boolean hasExplicitBookingVerb(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\bbook\\b|\\bappointment\\s+book\\b|बुक(?:\\s*(?:कर|करो|करें|कीजिए)|\\s+अपॉइंटमेंट)|अपॉइंटमेंट\\s+बुक).*" );
    }

    private boolean isBookingRequest(NormalizedUserTurn turn) {
        if (turn.intent() == NormalizedUserTurn.Intent.CANCELLATION
                || turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE) return false;
        if (isPatientOwnedAppointmentLookup(turn)) return false;
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        boolean appointment = text.matches(".*(?:\\bappointments?\\b|अपॉइंटमेंट्स?|अपॉइंटमेन्ट्स?).*");
        return text.matches(".*(?:\\bbook(?:ing)?\\b|\\bappointment\\s+book\\b|बुक(?:िंग)?|अपॉइंटमेंट\\s+बुक).*" )
                || appointment && text.matches(".*(?:want|need|chahiye|चाहिए|लेना|लेनी|चाहता|चाहती|with|के\\s+साथ|उनका|उनकी|उनके|same\\s+doctor|उसी\\s+डॉक्टर|दे\\s*दीजिए|कर\\s*दीजिए|बुक\\s*कर).*" );
    }

    private boolean isConsultationClarification(VoiceClarificationState state) {
        return state != null && state.pendingAction() != null
                && state.pendingAction().startsWith("CONSULTATION_");
    }

    private boolean refersToMissingProviderContext(NormalizedUserTurn turn) {
        String text = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(?:\\b(?:same doctor|that doctor|him|her|his|their)\\b|"
                + "\\b(?:unka|unki|unke|unse)\\b|उनका|उनकी|उनके|उनसे).*" );
    }

    private String consultationOption(NormalizedUserTurn turn, List<String> options) {
        if (turn == null || options == null || options.isEmpty()) return null;
        Integer ordinal = turn.ordinal();
        if (ordinal != null && ordinal >= 1 && ordinal <= options.size()) return options.get(ordinal - 1);
        String specialty = specialty(turn);
        String evidence = StringUtils.hasText(specialty) ? specialty : turn.rawText().trim();
        List<String> matches = options.stream().filter(option -> DoctorNameNormalizer.matches(option, evidence)
                || option.equalsIgnoreCase(evidence)
                || turn.specialtyEntity() != null
                && option.equalsIgnoreCase(turn.specialtyEntity().canonicalQuery())).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String consultationSpecialtyPrompt(List<String> options, NormalizedUserTurn turn) {
        String values = options.stream().map(value -> consultationSpecialtyDisplay(value, turn))
                .reduce((left, right) -> left + (isEnglish(turn) ? " or " : isHinglish(turn) ? " ya " : " या ") + right)
                .orElse("");
        if (isEnglish(turn)) return "Which type of doctor would you like to consult"
                + (values.isBlank() ? "?" : "— " + values + "?");
        if (isHinglish(turn)) return "Aap kis specialty ke doctor se consult karna chahte hain"
                + (values.isBlank() ? "?" : "— " + values + "?");
        return "आप किस प्रकार के डॉक्टर से मिलना चाहते हैं" + (values.isBlank() ? "?" : "— " + values + "?");
    }

    private String consultationProviderPrompt(List<String> options, NormalizedUserTurn turn) {
        String names = options.stream().limit(3).reduce((left, right) -> left + ", " + right).orElse("");
        if (isEnglish(turn)) return "Available doctors include " + names + ". Which doctor would you prefer?";
        if (isHinglish(turn)) return "Available doctors hain: " + names + ". Aap kis doctor se consult karna chahenge?";
        return "उपलब्ध डॉक्टर हैं: " + names + "। आप किस डॉक्टर से मिलना चाहेंगे?";
    }

    private String consultationSpecialtyDisplay(String value, NormalizedUserTurn turn) {
        if (isEnglish(turn) || isHinglish(turn)) return value;
        if ("General Medicine".equalsIgnoreCase(value)) return "जनरल मेडिसिन";
        if ("Pediatrics".equalsIgnoreCase(value)) return "बच्चों के डॉक्टर";
        return value;
    }

    private AvailabilityResult consultationContext(LocalDate date, NormalizedUserTurn.Daypart daypart,
            String cursor, String doctorId, AivaExecutionContext context) {
        Instant now = Instant.now();
        return new AvailabilityResult(UUID.randomUUID(), null, 0L,
                "voice-consultation|" + context.tenantId() + "|" + String.valueOf(date),
                doctorId, doctorId, null, date, daypart == null ? null : daypart.name(), null,
                context.timezone() == null ? "Asia/Kolkata" : context.timezone().getId(), List.of(), cursor,
                false, now, now.plusSeconds(10 * 60), 0, 3);
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

    private record PendingProviderCandidateResolution(ProviderCandidate selected, boolean stale) { }

    private AivaV2SessionStore.TurnExecution execution(SessionProjection session, TurnResult result) {
        TurnResult enriched = withSessionDiagnostics(session, result);
        NormalizedUserTurn turn = enriched == null ? null : enriched.normalizedTurn();
        log.info("AIVA_V2_VOICE_FLOW stage=VOICE_RESPONSE tenantId={} providerCallId={} conversationIdHash={} turnId={} turnNumber={} "
                        + "intent={} tool={} activeWorkflow={} activeVoiceDoctorId={} activeVoiceDoctorName={} "
                        + "bookingProviderId={} bookingProviderName={} providerResolved={} effectiveDate={} datePresent={} "
                        + "draftRevision={} draftStatus={} decisionCategory={} executionDecision={}",
                org.slf4j.MDC.get(AivaV2VoiceFlowTrace.TENANT_ID), org.slf4j.MDC.get(AivaV2VoiceFlowTrace.CALL_ID),
                AivaV2VoiceFlowTrace.conversationHash(session.conversationId()), AivaV2VoiceFlowTrace.turnId(),
                AivaV2VoiceFlowTrace.turnNumber(), enriched == null ? null : enriched.intent(),
                enriched == null ? null : enriched.tool(), session.activeWorkflow(),
                session.activeVoiceDoctor() == null ? null : session.activeVoiceDoctor().doctorId(),
                session.activeVoiceDoctor() == null ? null : session.activeVoiceDoctor().displayName(),
                session.activeDraft() == null || session.activeDraft().selectedProvider() == null ? null
                        : session.activeDraft().selectedProvider().doctorId(),
                session.activeDraft() == null || session.activeDraft().selectedProvider() == null ? null
                        : session.activeDraft().selectedProvider().displayName(),
                session.activeDraft() != null && session.activeDraft().selectedProvider() != null,
                session.activeDraft() == null ? null : session.activeDraft().preferredDate(),
                session.activeDraft() != null && session.activeDraft().preferredDate() != null,
                session.activeDraft() == null ? null : session.activeDraft().revision(),
                session.activeDraft() == null ? null : session.activeDraft().status(),
                enriched == null ? null : enriched.toolResultSummary(),
                enriched != null && enriched.mutationBlocked() ? "BLOCKED" : "NOT_EXECUTED");
        MessageResponse response = messageResponse(session, enriched);
        return new AivaV2SessionStore.TurnExecution(session, response);
    }

    private TurnResult withSessionDiagnostics(SessionProjection session, TurnResult result) {
        if (result != null && result.normalizedTurn() != null) {
            Map<String, Object> diagnostics = new LinkedHashMap<>(result.runtimeDiagnostics());
            NormalizedUserTurn turn = result.normalizedTurn();
            diagnostics.putIfAbsent("language", turn.responseLanguage());
            diagnostics.putIfAbsent("responseStyle", turn.responseStyle().name());
            diagnostics.putIfAbsent("explicitDoctorReference", turn.explicitDoctorReference());
            diagnostics.putIfAbsent("resolvedDoctorId", session.activeVoiceDoctor() == null
                    ? null : session.activeVoiceDoctor().doctorId());
            diagnostics.putIfAbsent("resolvedDoctorName", session.activeVoiceDoctor() == null
                    ? null : session.activeVoiceDoctor().displayName());
            diagnostics.putIfAbsent("activeVoiceDoctorAfter", session.activeVoiceDoctor() == null
                    ? null : session.activeVoiceDoctor().displayName());
            diagnostics.putIfAbsent("explicitDate", explicitDate(turn));
            diagnostics.putIfAbsent("parsedTemporalSource", explicitDate(turn) == null ? "NONE" : "CURRENT_TURN");
            String effectiveDate = result.toolResult() != null && result.toolResult().availabilityResult() != null
                    ? String.valueOf(result.toolResult().availabilityResult().date())
                    : session.latestAvailabilityResult() != null && session.latestAvailabilityResult().date() != null
                    ? session.latestAvailabilityResult().date().toString() : explicitDate(turn);
            diagnostics.putIfAbsent("effectiveDate", effectiveDate);
            diagnostics.putIfAbsent("temporalSource", explicitDate(turn) != null ? "CURRENT_TURN"
                    : effectiveDate == null ? "NONE" : "SESSION_CONTEXT");
            diagnostics.putIfAbsent("parsedDaypart", turn.daypart() == null ? null : turn.daypart().name());
            diagnostics.putIfAbsent("parsedExactTime", turn.exactTime() == null ? null : turn.exactTime().toString());
            diagnostics.putIfAbsent("parsedTimeRange", parsedTimeRange(turn));
            diagnostics.putIfAbsent("recognizedSpecialty", turn.specialtyEntity() == null
                    ? null : turn.specialtyEntity().canonicalQuery());
            String semanticText = (turn.rawText() + " " + turn.normalizedText()).toLowerCase(Locale.ROOT);
            diagnostics.putIfAbsent("providerEvidence", turn.doctorEntity() != null
                    || turn.specialtyEntity() != null || semanticText.matches("(?s).*(?:doctor|physician|डॉक्टर|चिकित्सक|available doctor).*"));
            diagnostics.putIfAbsent("slotAvailabilityEvidence", semanticText.matches("(?s).*(?:slot|availability|available|स्लॉट|अवेलेबल|उपलब्ध).*"));
            diagnostics.putIfAbsent("selectedQueryType", result.tool() == null ? null : switch (result.tool()) {
                case "FIND_DOCTOR_SCHEDULE" -> result.normalizedTurn().temporal().localDate() == null
                        ? QueryType.RECURRING_SCHEDULE.name() : QueryType.EFFECTIVE_DATE_SCHEDULE.name();
                case "FIND_SLOTS" -> QueryType.SLOT_AVAILABILITY.name();
                default -> null;
            });
            diagnostics.putIfAbsent("responseSource", responseSource(result));
            diagnostics.put("activeVoiceDoctorAfter", session.activeVoiceDoctor() == null
                    ? null : session.activeVoiceDoctor().displayName());
            return result.withRuntimeDiagnostics(diagnostics);
        }
        return result;
    }

    private String explicitDate(NormalizedUserTurn turn) {
        return turn.temporal() == null || turn.temporal().status() != NormalizedUserTurn.TemporalStatus.RESOLVED
                ? null : turn.temporal().localDate() == null ? null : turn.temporal().localDate().toString();
    }

    private String parsedTimeRange(NormalizedUserTurn turn) {
        String text = turn.rawText().toLowerCase(Locale.ROOT);
        String spokenNumber = "एक|दो|तीन|चार|पाँच|पांच|छह|छः|सात|आठ|नौ|दस|ग्यारह|बारह|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]{1,2}";
        Matcher matcher = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(" + spokenNumber
                + ")\\s*(?:से|to|and|-)\\s*(" + spokenNumber + ")"
                + "(?:\\s*(?:के\\s+बीच|बीच))?(?![\\p{L}\\p{N}])").matcher(text);
        String startToken;
        String endToken;
        if (matcher.find()) {
            startToken = matcher.group(1);
            endToken = matcher.group(2);
        } else {
            matcher = Pattern.compile("(?iu)between\\s+([\\p{L}\\p{N}]+)\\s+and\\s+([\\p{L}\\p{N}]+)").matcher(text);
            if (!matcher.find()) return null;
            startToken = matcher.group(1);
            endToken = matcher.group(2);
        }
        Integer start = spokenHour(startToken);
        Integer end = spokenHour(endToken);
        if (start == null || end == null) return null;
        boolean explicitAfternoonPeriod = turn.daypart() == NormalizedUserTurn.Daypart.AFTERNOON
                || turn.daypart() == NormalizedUserTurn.Daypart.EVENING
                || text.matches("(?s).*(?:रात|शाम|evening|night|afternoon).*" );
        // For an unqualified clinic-slot range in the common late-day band,
        // interpret 3–8 as PM; morning ranges remain available when explicitly named.
        boolean conventionalClinicPmRange = turn.daypart() == null && start >= 3 && end <= 8;
        if (explicitAfternoonPeriod || conventionalClinicPmRange) {
            if (start < 12) start += 12;
            if (end < 12) end += 12;
        }
        if (start >= 24 || end >= 24 || start >= end) return null;
        return String.format(Locale.ROOT, "%02d:00-%02d:00", start, end);
    }

    private AvailabilityTimeConstraint timeConstraint(NormalizedUserTurn turn, String range,
                                                       AvailabilityResult previous) {
        if (StringUtils.hasText(range)) {
            String[] parts = range.split("-", 2);
            return new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.BETWEEN,
                    LocalTime.parse(parts[0]), LocalTime.parse(parts[1]));
        }
        if (turn.exactTime() != null) {
            return new AvailabilityTimeConstraint(AvailabilityTimeConstraintMode.EXACT, turn.exactTime(), null);
        }
        return previous == null ? null : previous.availabilityTimeConstraint();
    }

    private Integer spokenHour(String value) {
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { }
        return switch (value) {
            case "एक" -> 1; case "दो" -> 2; case "तीन" -> 3; case "चार" -> 4;
            case "पाँच", "पांच" -> 5; case "छह", "छः" -> 6; case "सात" -> 7;
            case "आठ" -> 8; case "नौ" -> 9; case "दस" -> 10; case "ग्यारह" -> 11; case "बारह" -> 12;
            case "one" -> 1; case "two" -> 2; case "three" -> 3; case "four" -> 4;
            case "five" -> 5; case "six" -> 6; case "seven" -> 7; case "eight" -> 8;
            case "nine" -> 9; case "ten" -> 10; case "eleven" -> 11; case "twelve" -> 12;
            default -> null;
        };
    }

    private String responseSource(TurnResult result) {
        if (result.mutationBlocked()) return "BLOCKED_TRANSACTION_RESPONSE";
        if (result.toolExecuted()) return "TOOL_RESULT_RENDERER";
        if (result.intent() != null && (result.intent().startsWith("CLARIFY")
                || "UNKNOWN".equals(result.intent()) || "ESCALATION_DECISION".equals(result.intent()))) {
            return "CLARIFICATION_RECOVERY";
        }
        if (result.toolResultSummary() != null && result.toolResultSummary().contains("FAIL")) return "FALLBACK_ERROR";
        return "DETERMINISTIC_VOICE_ADAPTER";
    }

    public record TurnResult(String response, String intent, String tool, boolean aivaUsed,
                             boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                             int toolResultCount, String toolResultSummary, boolean closing,
                             NormalizedUserTurn normalizedTurn, AivaV2VoiceToolExecutor.ToolResult toolResult,
                             Map<String, Object> runtimeDiagnostics) {
        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary, boolean closing,
                          NormalizedUserTurn normalizedTurn) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted, toolInputSpeciality,
                    toolResultCount, toolResultSummary, closing, normalizedTurn, null, Map.of());
        }

        public TurnResult(String response, String intent, String tool, boolean aivaUsed,
                          boolean mutationBlocked, boolean toolExecuted, String toolInputSpeciality,
                          int toolResultCount, String toolResultSummary, boolean closing,
                          NormalizedUserTurn normalizedTurn, AivaV2VoiceToolExecutor.ToolResult toolResult) {
            this(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted, toolInputSpeciality,
                    toolResultCount, toolResultSummary, closing, normalizedTurn, toolResult, Map.of());
        }

        public TurnResult {
            if (runtimeDiagnostics == null || runtimeDiagnostics.isEmpty()) runtimeDiagnostics = Map.of();
            else {
                Map<String, Object> safe = new LinkedHashMap<>(runtimeDiagnostics);
                safe.entrySet().removeIf(entry -> entry.getValue() == null);
                runtimeDiagnostics = Map.copyOf(safe);
            }
        }

        public TurnResult withRuntimeDiagnostics(Map<String, Object> diagnostics) {
            return new TurnResult(response, intent, tool, aivaUsed, mutationBlocked, toolExecuted,
                    toolInputSpeciality, toolResultCount, toolResultSummary, closing, normalizedTurn,
                    toolResult, diagnostics);
        }

        static TurnResult failure(String response) {
            return new TurnResult(response, "UNKNOWN", null, false, false, false,
                    null, 0, null, false, null, null, Map.of());
        }

        public String policyDecision() {
            return mutationBlocked ? "BLOCKED_READ_ONLY" : "ALLOWED_READ_ONLY";
        }
    }
}
