package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Converts one transcript into the single semantic input consumed by the runtime. */
@Service
public class PatientPortalCareAiTurnInterpreter {
    private static final Pattern ORDINAL = Pattern.compile("(?i)\\b(first|second|third|fourth|last|1st|2nd|3rd|4th|\\d{1,2})\\b");

    private final PatientPortalCareAiEntityExtractor entityExtractor;

    public PatientPortalCareAiTurnInterpreter(PatientPortalCareAiEntityExtractor entityExtractor) {
        this.entityExtractor = Objects.requireNonNull(entityExtractor);
    }

    public PatientPortalCareAiCanonicalTurn interpret(
            String transcript,
            String language,
            PatientPortalCareAiPlanningContext context,
            PatientPortalCareAiPlannerDecision plannerDecision
    ) {
        return interpret(transcript, language, context, plannerDecision, false, plannerDecision != null);
    }

    public PatientPortalCareAiCanonicalTurn interpret(
            String transcript,
            String language,
            PatientPortalCareAiPlanningContext context,
            PatientPortalCareAiPlannerDecision plannerDecision,
            boolean allowPastDate
    ) {
        return interpret(transcript, language, context, plannerDecision, allowPastDate, plannerDecision != null);
    }

    public PatientPortalCareAiCanonicalTurn interpret(
            String transcript,
            String language,
            PatientPortalCareAiPlanningContext context,
            PatientPortalCareAiPlannerDecision plannerDecision,
            boolean allowPastDate,
            boolean plannerInvoked
    ) {
        String text = transcript == null ? "" : transcript.trim();
        PatientPortalCareAiExtractedEntities extracted = entityExtractor.extract(text, language);
        String lower = text.toLowerCase(Locale.ROOT);
        PatientPortalCareAiConfirmationPolarity confirmation = confirmation(text, lower);
        boolean end = isEnd(text, lower);
        boolean abandon = isAbandon(text, lower) && !end;
        PatientPortalCareAiIntent intent = plannerDecision == null ? null : PatientPortalCareAiIntent.normalize(plannerDecision.intent());
        if (intent == null) {
            intent = deterministicIntent(text, lower);
        }
        if (intent == null) {
            intent = PatientPortalCareAiIntent.UNKNOWN;
        }

        String plannerDate = plannerDecision == null ? null : plannerDecision.preferredDate();
        Double extractedDateConfidence = extracted.confidenceByType() == null
                ? null
                : extracted.confidenceByType().get(PatientPortalCareAiEntityType.DATE);
        // High-confidence deterministic dates outrank model output; weaker partial dates remain model-resolvable.
        boolean authoritativeExtractedDate = StringUtils.hasText(extracted.date())
                && extractedDateConfidence != null
                && extractedDateConfidence >= 0.9d;
        String semanticDate = authoritativeExtractedDate
                ? extracted.date()
                : first(plannerDate, extracted.date());
        String dateIssue = extracted.dateIssue();
        if (StringUtils.hasText(semanticDate)) {
            try {
                LocalDate.parse(semanticDate);
            } catch (RuntimeException ignored) {
                dateIssue = "invalid";
                semanticDate = null;
            }
        }
        String semanticTimeWindow = first(plannerDecision == null ? null : plannerDecision.preferredTimeWindow(), extracted.timeWindow());
        if (!StringUtils.hasText(semanticTimeWindow) && lower(text).contains("after lunch")) {
            semanticTimeWindow = isHindi(language) ? "दोपहर" : "afternoon";
        }
        String semanticExactTime = extracted.time();
        if (!StringUtils.hasText(semanticExactTime)) {
            semanticExactTime = parseExactTime(text);
        }
        if (!StringUtils.hasText(semanticTimeWindow) && context != null
                && ("ask-time".equals(context.lastQuestionKey())
                || StringUtils.hasText(context.preferredDate()) && !StringUtils.hasText(context.preferredTimeWindow()))) {
            semanticTimeWindow = enumeratedTime(text, language);
        }
        PatientPortalCareAiCanonicalEntities entities = new PatientPortalCareAiCanonicalEntities(
                plannerDecision == null ? extracted.doctor() : plannerDecision.doctorName(),
                extracted.clinic(),
                plannerDecision == null ? extracted.speciality() : plannerDecision.speciality(),
                extracted.service(),
                extracted.location(),
                semanticDate,
                semanticTimeWindow,
                semanticExactTime,
                extracted.timeSlot(),
                dateIssue
        );
        PatientPortalCareAiSelectionReference selection = selection(text, lower, plannerDecision);
        PatientPortalCareAiAlternativeRequest alternative = alternative(text, lower, plannerDecision);
        PatientPortalCareAiCorrection correction = correction(text, lower, plannerDecision);
        PatientPortalCareAiDialogAct act = plannerDecision != null && plannerDecision.dialogAct() != null
                ? plannerDecision.dialogAct()
                : dialogAct(text, lower, intent, entities, confirmation, selection, alternative, abandon, end);
        boolean fastPath = isFastPath(text, lower, extracted, confirmation, selection, alternative, abandon, end);
        PatientPortalCareAiInterpretationSource source = plannerInvoked && plannerDecision != null
                ? PatientPortalCareAiInterpretationSource.GEMINI
                : fastPath ? PatientPortalCareAiInterpretationSource.FAST_PATH
                : PatientPortalCareAiInterpretationSource.EXTRACTOR_SIGNAL;
        double confidence = plannerInvoked && plannerDecision != null ? 0.9d : fastPath ? 0.98d : extracted.confidence();
        return new PatientPortalCareAiCanonicalTurn(act, intent, entities, confirmation, correction, alternative, selection, abandon, end, source, confidence);
    }

    private PatientPortalCareAiIntent deterministicIntent(String text, String lower) {
        if (lower.contains("reset conversation") || lower.contains("reset chat")
                || lower.contains("start over") || lower.contains("begin again")
                || text.contains("रीसेट") || text.contains("शुरू से")) {
            return PatientPortalCareAiIntent.RESET_CONVERSATION;
        }
        if (lower.contains("reschedule") || lower.contains("change my appointment") || lower.contains("move my appointment")) {
            return PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT;
        }
        if (lower.contains("cancel my appointment") || lower.contains("cancel appointment")
                || (lower.matches(".*\\bcancel\\b.*")
                && (lower.contains("visit") || lower.contains("booking")
                || lower.contains("doctor") || lower.contains("dr ")))) {
            return PatientPortalCareAiIntent.CANCEL_APPOINTMENT;
        }
        if (lower.contains("check my appointment") || lower.contains("check my appointments")
                || lower.contains("show my appointment") || lower.contains("show appointments")
                || lower.contains("my next appointment") || lower.contains("upcoming appointment")
                || lower.contains("appointment status") || lower.contains("do i have an appointment")) {
            return PatientPortalCareAiIntent.CHECK_APPOINTMENT;
        }
        if (lower.contains("find doctor") || lower.contains("find a doctor")
                || lower.contains("show doctors") || lower.contains("available doctor")
                || (lower.contains("find ")
                && (lower.contains("physician") || lower.contains("general medicine")
                || lower.contains("specialist")))) {
            return PatientPortalCareAiIntent.FIND_DOCTOR;
        }
        if (lower.contains("find clinic") || lower.contains("find a clinic")
                || lower.contains("show clinics") || lower.contains("available clinic")) {
            return PatientPortalCareAiIntent.FIND_CLINIC;
        }
        if (lower.contains("book") || lower.contains("appointment") || lower.contains("doctor appointment") || text.contains("अपॉइंटमेंट")) {
            return PatientPortalCareAiIntent.BOOK_APPOINTMENT;
        }
        return null;
    }

    private PatientPortalCareAiDialogAct dialogAct(
            String text, String lower, PatientPortalCareAiIntent intent,
            PatientPortalCareAiCanonicalEntities entities,
            PatientPortalCareAiConfirmationPolarity confirmation,
            PatientPortalCareAiSelectionReference selection,
            PatientPortalCareAiAlternativeRequest alternative,
            boolean abandon, boolean end
    ) {
        if (end) return PatientPortalCareAiDialogAct.END_CONVERSATION;
        if (abandon) return PatientPortalCareAiDialogAct.ABANDON_WORKFLOW;
        if (confirmation == PatientPortalCareAiConfirmationPolarity.POSITIVE) return PatientPortalCareAiDialogAct.CONFIRM;
        if (confirmation == PatientPortalCareAiConfirmationPolarity.NEGATIVE) return PatientPortalCareAiDialogAct.REJECT;
        if (selection.present()) return PatientPortalCareAiDialogAct.SELECT_OPTION;
        if (alternative.present()) return PatientPortalCareAiDialogAct.REQUEST_ALTERNATIVE;
        if (lower.contains("actually") || lower.contains("instead") || lower.contains("change") || lower.contains("switch")) {
            return PatientPortalCareAiDialogAct.CHANGE_INFORMATION;
        }
        if (intent != null && (lower.contains("want") || lower.contains("need") || lower.contains("book") || lower.contains("find"))) {
            return PatientPortalCareAiDialogAct.START_REQUEST;
        }
        if (!entities.isEmpty()) return PatientPortalCareAiDialogAct.PROVIDE_INFORMATION;
        if (lower.endsWith("?") || lower.startsWith("what ") || lower.startsWith("can ")) return PatientPortalCareAiDialogAct.ASK_QUESTION;
        return PatientPortalCareAiDialogAct.UNKNOWN;
    }

    private PatientPortalCareAiConfirmationPolarity confirmation(String text, String lower) {
        if (lower.contains("don't") || lower.contains("do not") || lower.contains("not okay") || lower.equals("no")
                || lower.startsWith("no,") || text.contains("नहीं") || text.contains("मत ")) {
            return PatientPortalCareAiConfirmationPolarity.NEGATIVE;
        }
        if (lower.equals("yes") || lower.equals("confirm") || lower.equals("go ahead")
                || lower.contains("book it") || lower.contains("haan kar do") || text.contains("हाँ बुक कर दीजिए")) {
            return PatientPortalCareAiConfirmationPolarity.POSITIVE;
        }
        if (lower.equals("maybe") || lower.equals("not sure") || lower.equals("okay") || lower.equals("ok")) {
            return PatientPortalCareAiConfirmationPolarity.AMBIGUOUS;
        }
        return PatientPortalCareAiConfirmationPolarity.NONE;
    }

    private PatientPortalCareAiSelectionReference selection(String text, String lower, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (plannerDecision != null && plannerDecision.selectionOrdinal() != null) {
            return new PatientPortalCareAiSelectionReference(true, plannerDecision.selectionOrdinal(), plannerDecision.selectionTarget());
        }
        var matcher = ORDINAL.matcher(text);
        if (!matcher.find()) return PatientPortalCareAiSelectionReference.none();
        String value = matcher.group(1).toLowerCase(Locale.ROOT);
        Integer ordinal = switch (value) {
            case "first", "1st" -> 1;
            case "second", "2nd" -> 2;
            case "third", "3rd" -> 3;
            case "fourth", "4th" -> 4;
            default -> value.matches("\\d{1,2}") ? Integer.valueOf(value) : null;
        };
        String target = lower.contains("slot") ? "slot" : lower.contains("doctor") ? "doctor" : null;
        return new PatientPortalCareAiSelectionReference(true, ordinal, target);
    }

    private PatientPortalCareAiAlternativeRequest alternative(String text, String lower, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (plannerDecision != null && StringUtils.hasText(plannerDecision.alternativeTarget())) {
            return new PatientPortalCareAiAlternativeRequest(true, plannerDecision.alternativeTarget());
        }
        if (lower.contains("another date") || lower.contains("different date") || lower.contains("another day") || lower.contains("next available date")) {
            return new PatientPortalCareAiAlternativeRequest(true, "date");
        }
        if (lower.contains("another time") || lower.contains("different time") || lower.contains("another slot")) {
            return new PatientPortalCareAiAlternativeRequest(true, "time");
        }
        if (lower.contains("another doctor") || lower.contains("different doctor") || lower.contains("someone else")) {
            return new PatientPortalCareAiAlternativeRequest(true, "doctor");
        }
        return PatientPortalCareAiAlternativeRequest.none();
    }

    private PatientPortalCareAiCorrection correction(String text, String lower, PatientPortalCareAiPlannerDecision plannerDecision) {
        if (plannerDecision != null && StringUtils.hasText(plannerDecision.correctionTarget())) {
            return new PatientPortalCareAiCorrection(true, plannerDecision.correctionTarget());
        }
        if (lower.contains("actually") || lower.contains("instead") || lower.contains("change") || lower.contains("switch")) {
            String target = lower.contains("doctor") || lower.contains("akshu") ? "doctor"
                    : lower.contains("date") || lower.contains("day") || lower.contains("thursday") || lower.contains("monday") ? "date"
                    : lower.contains("morning") || lower.contains("afternoon") || lower.contains("time") ? "time" : null;
            return new PatientPortalCareAiCorrection(true, target);
        }
        return PatientPortalCareAiCorrection.none();
    }

    private boolean isFastPath(String text, String lower, PatientPortalCareAiExtractedEntities extracted,
                               PatientPortalCareAiConfirmationPolarity confirmation,
                               PatientPortalCareAiSelectionReference selection,
                               PatientPortalCareAiAlternativeRequest alternative,
                               boolean abandon, boolean end) {
        if (end || abandon || confirmation != PatientPortalCareAiConfirmationPolarity.NONE) {
            return true;
        }
        String normalized = lower.replaceAll("[!?.,]", " ").replaceAll("\\s+", " ").trim();
        if (selection.present() && normalized.split(" ").length <= 3) {
            return true;
        }
        if (alternative.present() && normalized.split(" ").length <= 3) {
            return true;
        }
        if (normalized.matches("(?:today|tomorrow|day after tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next \\w+|this \\w+|\\d{1,2}(?:st|nd|rd|th)? [a-z]+(?: \\d{4})?)")) {
            return true;
        }
        if (normalized.matches("(?:morning|afternoon|evening|night|before lunch|after lunch|\\d{1,2}(?::\\d{2})? ?(?:am|pm))")) {
            return true;
        }
        return StringUtils.hasText(extracted.speciality())
                && normalized.split(" ").length <= 3
                && !normalized.matches(".*\\b(?:book|check|find|show|available|doctor|appointment|please|can|could|want|need)\\b.*");
    }

    private boolean isAbandon(String text, String lower) {
        return lower.contains("never mind") || lower.contains("no thanks") || lower.contains("that's all")
                || lower.equals("stop") || lower.contains("cancel this") || text.contains("नहीं धन्यवाद");
    }

    private boolean isEnd(String text, String lower) {
        return lower.equals("bye") || lower.equals("goodbye") || lower.contains("bye") || lower.contains("goodbye")
                || lower.contains("i'm done") || text.contains("अलविदा");
    }

    private String first(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String enumeratedTime(String text, String language) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1", "first", "1st" -> isHindi(language) ? "सुबह" : "morning";
            case "2", "second", "2nd" -> isHindi(language) ? "दोपहर" : "afternoon";
            case "3", "third", "3rd", "option three" -> isHindi(language) ? "शाम" : "evening";
            case "4", "fourth", "4th" -> isHindi(language) ? "रात" : "night";
            default -> null;
        };
    }

    private boolean isHindi(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("hi");
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String parseExactTime(String text) {
        if (text == null) return null;
        var matcher = Pattern.compile("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b").matcher(text);
        if (!matcher.find()) return null;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String meridiem = matcher.group(3).toLowerCase(Locale.ROOT).replace(".", "");
        if ("pm".equals(meridiem) && hour < 12) hour += 12;
        if ("am".equals(meridiem) && hour == 12) hour = 0;
        return hour >= 0 && hour <= 23 && minute <= 59 ? "%02d:%02d".formatted(hour, minute) : null;
    }
}
