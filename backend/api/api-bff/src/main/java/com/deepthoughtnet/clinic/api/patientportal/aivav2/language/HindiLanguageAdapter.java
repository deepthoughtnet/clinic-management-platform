package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HindiLanguageAdapter extends EnglishLanguageAdapter {
    private static final Pattern HINDI_SIGNAL = Pattern.compile(
            "(?:mujhe|meri|mere|ke\\s+saath|karni\\s+hai|wali|wala|theek|hai|haan|nahi|nahin|namaste|subah|dopahar|shaam|raat|baje|dikhao|aur|dusri|doosra|doosre|agali|pehla|पहला|हाँ|हां|नहीं|नही|नमस्ते|सुबह|दोपहर|शाम|रात|इवनिंग|मॉर्निंग|आफ्टरनून|बजे|दिखाओ|और|दूसरी|अगली|स्लॉट)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HINGLISH_DOCTOR = Pattern.compile(
            "(?i)((?:doctor|dr\\.?|doc)\\s+[\\p{L}][\\p{L}\\p{N}'-]*(?:\\s+(?!ka\\b|ki\\b|ke\\b|के(?=\\s)|sath\\b|साथ(?=\\s)|with\\b|appointment\\b|consult|sitting|schedule|hours|time|evening|morning|afternoon)[\\p{L}][\\p{L}\\p{N}'-]*){0,3})"
                    + "(?=(?:['’]s\\s+appointment)|\\s+(?:ke\\s+(?:saath|sath|sitting|time)|के\\s+(?:साथ|बैठने)|से|with|appointment|consult|consultation|sitting|schedule|hours|time|in|mein|में|evening|morning|afternoon)(?:\\s|$)|[?.!,।]|$)");
    private static final Pattern DEVANAGARI_DOCTOR = Pattern.compile(
            "((?:डॉ\\.?|डॉक्टर)\\s+(?!(?:के|साथ|से)(?=\\s|$))[\\p{IsDevanagari}\\p{M}]+"
                    + "(?:\\s+(?!(?:के|साथ|से)(?=\\s|$))[\\p{IsDevanagari}\\p{M}]+){0,3})"
                    + "(?=\\s+(?:के\\s+साथ|से|की|का|available|अवेलेबल|उपलब्ध|हैं|है|कब|किस\\s+समय|बैठते|बैठने|consult|consultation|sitting|schedule|hours|time|शाम|सुबह|दोपहर)(?:\\s|$)|[?.!,।]|$)");
    private static final Pattern BARE_LATIN_DOCTOR = Pattern.compile(
            "((?:[A-Z][\\p{L}][\\p{L}\\p{N}'-]*\\s+){1,2}[A-Z][\\p{L}\\p{N}'-]*)"
                    + "(?=(?:['’]s)?\\s+(?:appointment|appointments|available|हैं|है|उपलब्ध|consult|consultation|sitting|schedule|hours|time|का|की|के|ka|ki|ke|kab|कब|today|tomorrow|tonight|kal|parso|parson|आज|कल|परसों|परसो|day\\s+after\\s+tomorrow|evening|morning|afternoon|शाम|सुबह|दोपहर|में|mein)(?:\\s|$)|[?.!,।]|$)");
    private static final Pattern BARE_DEVANAGARI_DOCTOR = Pattern.compile(
            "(?<![\\p{IsDevanagari}\\p{M}])((?!(?:कल|आज|परसों|परसो)\\s)"
                    + "(?:[\\p{IsDevanagari}\\p{M}]+\\s+){1,3}[\\p{IsDevanagari}\\p{M}]+)"
                    + "(?=\\s+(?:available|अवेलेबल|हैं|है|उपलब्ध|की|का|के|कब|किस\\s+समय|बैठते|बैठने|consult|consultation|sitting|schedule|hours|time|शाम|सुबह|दोपहर)(?:\\s|$)|[?.!,।]|$)");
    private static final Pattern HINDI_SPECIALTY = Pattern.compile(
            "(?:विशेषज्ञता|विशेषज्ञ|स्पेशलिटी)\\s+(?:में|की)?\\s*([\\p{IsDevanagari}\\p{M}][\\p{IsDevanagari}\\p{M} ]{1,50})");

    @Override public String languageCode() { return "hi"; }

    boolean hasHindiUtteranceSignal(String rawText) {
        return rawText != null && (rawText.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)
                || HINDI_SIGNAL.matcher(rawText).find());
    }

    @Override
    protected NormalizedUserTurn.Intent intent(String normalized) {
        NormalizedUserTurn.Intent recognized = super.intent(normalized);
        if (recognized != NormalizedUserTurn.Intent.UNKNOWN) return recognized;
        boolean appointment = normalized != null && normalized.matches(".*(?:appointment|appointments|अपॉइंटमेंट्स?).*");
        boolean lookupQuestion = normalized != null && normalized.matches(
                ".*(?:\\bmeri\\b|\\bmere\\b|\\bmera\\b|\\bkya\\b|\\bhain\\b|\\bhai\\b|मेरी|मेरे|मेरा|क्या|हैं|है).*" );
        return appointment && lookupQuestion ? NormalizedUserTurn.Intent.LOOKUP : recognized;
    }

    @Override
    public boolean supports(String requestedLanguage, String rawText) {
        if (rawText != null && (rawText.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)
                || HINDI_SIGNAL.matcher(rawText).find())) return true;
        return requestedLanguage != null && requestedLanguage.toLowerCase(Locale.ROOT).startsWith("hi");
    }

    @Override
    public NormalizedUserTurn normalize(String rawText) { return normalize(rawText, LocalDate.now()); }

    @Override
    public NormalizedUserTurn normalize(String rawText, LocalDate referenceDate) {
        String original = rawText == null ? "" : rawText.trim();
        String normalized = original.toLowerCase(Locale.ROOT)
                .replaceAll("(?<![\\p{L}\\p{N}])(?:subah|सुबह|मॉर्निंग)(?![\\p{L}\\p{N}])", " morning ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:dopahar|दोपहर|आफ्टरनून)(?![\\p{L}\\p{N}])", " afternoon ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:shaam|शाम|इवनिंग|raat|रात)(?![\\p{L}\\p{N}])", " evening ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:aur|और)(?![\\p{L}\\p{N}])", " more ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:dusri|दूसरी|agali|अगली)(?![\\p{L}\\p{N}])", " next ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:pehla|पहला|पहली)(?![\\p{L}\\p{N}])", " first ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:dikhao|दिखाओ)(?![\\p{L}\\p{N}])", " show ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:स्लॉट्स|स्लॉट|slots?)(?![\\p{L}\\p{N}])", " slot ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:haan\\s+ji|ji\\s+haan|haan|हाँ|हां)(?![\\p{L}\\p{N}])", " yes ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:nahi|nahin|na|नहीं|नही)(?![\\p{L}\\p{N}])", " no ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:मेरी|मेरा|मेरे)(?![\\p{L}\\p{N}])", " my ")
                .replaceAll("अपॉइंटमेंट्स", " appointments ").replaceAll("अपॉइंटमेंट", " appointment ")
                .replaceAll("अपॉइंटमेन्ट", " appointment ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:क्या|कौनसी|कौन-सी)(?![\\p{L}\\p{N}])", " what ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:हैं|है)(?![\\p{L}\\p{N}])", " are ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:बुक|बुकिंग)(?![\\p{L}\\p{N}])", " book ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:करनी|करना|करनी है|करना है)(?![\\p{L}\\p{N}])", " ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:रद्द|कैंसल|कैंसिल|cancel)(?![\\p{L}\\p{N}])", " cancel ")
                .replaceAll("फिर\\s+से\\s+(?:तय|शेड्यूल)", " reschedule ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:रीशेड्यूल|शेड्यूल|reschedule)(?![\\p{L}\\p{N}])", " reschedule ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:मेरी|अपनी)(?![\\p{L}\\p{N}])", " my ")
                .replaceAll("\\s+", " ").trim();
        if (Pattern.compile("(?i)^\\s*(?:theek|thik)\\s+hai(?:\\s+kar\\s+do)?[.!?।\\s]*$")
                .matcher(normalized).matches()) {
            normalized = "yes";
        }

        String selectionInput = original
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:raat|रात)\\s+([01]?\\d|2[0-3])", "$1 pm")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:aath|आठ)(?=\\s*(?:baje|बजे))", "8")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:baje|बजे|wali|वाली|wala|वाला)(?![\\p{L}\\p{N}])", " ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:paanch|पाँच|पांच)(?![\\p{L}\\p{N}])", " 5 ")
                .replaceAll("(?i)(?:theek\\s+hai|theek|ठीक\\s+है|ठीक)", " works ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:pehla|पहला|पहली)(?![\\p{L}\\p{N}])", " first ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:dusra|doosra|दूसरा|dusri|doosri|दूसरी)(?![\\p{L}\\p{N}])", " second ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:teesra|teesri|तीसरा|तीसरी)(?![\\p{L}\\p{N}])", " third ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:last|aakhiri|आखिरी)(?![\\p{L}\\p{N}])", " last ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:subah|सुबह|dopahar|दोपहर|shaam|शाम|raat|रात)(?![\\p{L}\\p{N}])", " ");
        String selection = normalizeSelection(selectionInput.toLowerCase(Locale.ROOT)
                .replaceAll("[?.!,;\\u0964\\u0965]+$", "").trim());

        NormalizedUserTurn.EntityReference doctor = doctorEntity(original);
        NormalizedUserTurn.EntityReference specialty = specialtyEntity(original);
        boolean qualifier = doctor != null || Pattern.compile("(?:के\\s+साथ|में|से)\\s+[^?.!,]+").matcher(original).find();
        String responseStyle = original.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)
                ? "STANDARD" : "HINGLISH";
        NormalizedUserTurn turn = canonicalTurn(original, normalized, selection, languageCode(), "hi",
                ResponseStyle.valueOf(responseStyle), doctor, specialty, qualifier, referenceDate);
        if (turn.ordinal() == null && original.matches("(?is).*?(?:second|dusri|doosri|दूसरी|सेकंड).*?(?:appointment|अपॉइंटमेंट).*")) {
            turn = turn.withOrdinal(2);
        } else if (turn.ordinal() == null && original.matches("(?is).*?(?:third|teesri|तीसरी).*?(?:appointment|अपॉइंटमेंट).*")) {
            turn = turn.withOrdinal(3);
        } else if (turn.ordinal() == null && original.matches("(?is).*?(?:first|pehli|pahli|पहली).*?(?:appointment|अपॉइंटमेंट).*")) {
            turn = turn.withOrdinal(1);
        }
        if (turn.exactTime() == null) {
            Matcher contextualHour = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:subah|सुबह|dopahar|दोपहर|shaam|शाम|raat|रात)\\s+([0-9]{1,2})(?![\\p{L}\\p{N}])")
                    .matcher(original);
            if (contextualHour.find()) {
                int hour = Integer.parseInt(contextualHour.group(1));
                if (hour >= 1 && hour <= 12) {
                    String token = contextualHour.group().toLowerCase(Locale.ROOT);
                    boolean evening = token.contains("shaam") || token.contains("शाम")
                            || token.contains("raat") || token.contains("रात");
                    int resolvedHour = evening ? (hour == 12 ? 12 : hour + 12) : hour;
                    turn = turn.withExactTime(LocalTime.of(resolvedHour, 0));
                }
            }
        }
        Matcher nightHour = Pattern.compile("(?:raat|रात)\\s+([0-9]{1,2})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(original);
        if (nightHour.find()) {
            int hour = Integer.parseInt(nightHour.group(1));
            if (hour >= 1 && hour <= 12) turn = turn.withExactTime(LocalTime.of(hour == 12 ? 12 : hour + 12, 0));
        }
        return contextualizeRelativeDate(turn, referenceDate == null ? LocalDate.now() : referenceDate);
    }

    private NormalizedUserTurn contextualizeRelativeDate(NormalizedUserTurn turn, LocalDate referenceDate) {
        var fact = turn.temporal();
        if (fact == null || fact.status() != NormalizedUserTurn.TemporalStatus.RESOLVED
                || fact.relativeKind() == NormalizedUserTurn.RelativeKind.NONE) return turn;
        String raw = fact.rawSpan() == null ? "" : fact.rawSpan().toLowerCase(Locale.ROOT);
        boolean kal = raw.contains("कल") || raw.contains("kal");
        boolean parson = raw.contains("परसों") || raw.contains("parson");
        if (!kal && !parson) return turn;
        boolean future = turn.intent() == NormalizedUserTurn.Intent.BOOKING
                || turn.intent() == NormalizedUserTurn.Intent.RESCHEDULE
                || turn.intent() == NormalizedUserTurn.Intent.CANCELLATION;
        boolean retrospective = turn.intent() == NormalizedUserTurn.Intent.LOOKUP
                && turn.normalizedText().matches(".*(?:था|थी|थे|thi|tha|the|hua|hui|ho|was|were).*" );
        if (future || retrospective) {
            int offset = parson ? (future ? 2 : -2) : (future ? 1 : -1);
            return turn.withTemporal(new NormalizedUserTurn.TemporalFact(fact.rawSpan(),
                    NormalizedUserTurn.TemporalStatus.RESOLVED,
                    referenceDate.plusDays(offset),
                    offset > 0 ? (offset == 1 ? NormalizedUserTurn.RelativeKind.TOMORROW
                            : NormalizedUserTurn.RelativeKind.DAY_AFTER_TOMORROW)
                            : NormalizedUserTurn.RelativeKind.NONE, false));
        }
        return turn.withTemporal(new NormalizedUserTurn.TemporalFact(fact.rawSpan(),
                NormalizedUserTurn.TemporalStatus.AMBIGUOUS, null,
                NormalizedUserTurn.RelativeKind.NONE, false));
    }

    private NormalizedUserTurn.EntityReference doctorEntity(String text) {
        Matcher latin = HINGLISH_DOCTOR.matcher(text);
        if (latin.find()) {
            String name = trimAvailabilityTail(latin.group(1));
            if (isGenericDoctorReference(name)) return null;
            return new NormalizedUserTurn.EntityReference(name, name);
        }
        Matcher devanagari = DEVANAGARI_DOCTOR.matcher(text);
        if (devanagari.find()) {
            String span = devanagari.group(1).trim();
            String name = span.replaceFirst("^(?:डॉक्टर|डॉ\\.?)\\s*", "");
            name = trimAvailabilityTail(name);
            if (name.isBlank()) return null;
            if (isGenericDoctorReference(name)) return null;
            String canonical = DoctorNameNormalizer.canonicalReference(name);
            return new NormalizedUserTurn.EntityReference(span, canonical.isBlank() ? span : "Dr " + canonical);
        }
        Matcher latinName = BARE_LATIN_DOCTOR.matcher(text);
        if (latinName.find()) {
            String name = trimAvailabilityTail(latinName.group(1))
                    .replaceFirst("(?iu)^(?:actually|kal|tomorrow|today|aaj)\\s+", "");
            if (name.isBlank()) return null;
            if (isGenericDoctorReference(name)) return null;
            return new NormalizedUserTurn.EntityReference(name, name);
        }
        // A pronoun-led follow-up has no new provider name. Prevent the bare
        // Devanagari matcher from treating transliterated schedule/question
        // words (e.g. सिटिंग टाइम क्या) as a provider reference.
        if (text.matches("(?is)^\\s*(?:उनका|उनकी|उनके|उनसे|unka|unki|unke)(?:\\s|$).*$")) return null;
        Matcher devanagariName = BARE_DEVANAGARI_DOCTOR.matcher(text);
        if (devanagariName.find()) {
            String name = trimAvailabilityTail(devanagariName.group(1));
            if (name.isBlank()) return null;
            if (isGenericDoctorReference(name)) return null;
            String canonical = DoctorNameNormalizer.canonicalReference(name);
            return new NormalizedUserTurn.EntityReference(name, canonical.isBlank() ? name : "Dr " + canonical);
        }
        return null;
    }

    private boolean isGenericDoctorReference(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("(?:doctor|dr\\.?|doc)(?:\\s+(?:ka|ki|ke))?")) return true;
        java.util.Set<String> nonName = java.util.Set.of("कल", "आज", "परसों", "परसो", "रवि", "रविवार",
                "शाम", "सुबह", "दोपहर", "इवनिंग", "मॉर्निंग", "आफ्टरनून", "evening", "morning",
                "afternoon", "kal", "aaj", "parso", "tomorrow", "today", "kya", "क्या", "है", "हैं",
                "unka", "उनका", "उनकी", "उनके", "meri", "mera", "my", "मेरा", "मेरी", "मेरे",
                "appointment", "अपॉइंटमेंट", "अपॉइंटमेंट्स", "booking", "book", "बुक", "cancel", "कैंसिल",
                "कैंसल", "रद्द", "karna", "karni", "करना", "करनी", "है।");
        String[] tokens = normalized.replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim().split("\\s+");
        if (tokens.length > 0 && java.util.Arrays.stream(tokens).allMatch(nonName::contains)) return true;
        return normalized.matches(".*(?:available|अवेलेबल|उपलब्ध|है|हैं|कौन|कोई|डॉक्टर|which|what|बताइए|बताएं|चाहिए|चाहिये|need|want|\\bkya\\b|\\bkya\\b).*" );
    }

    private String trimAvailabilityTail(String value) {
        return value == null ? "" : value.trim()
                .replaceFirst("(?iu)(?:['’]s)?(?:\\s+(?:available|अवेलेबल|उपलब्ध|है|हैं|consult|consults|consultation|hours|schedule|sitting|time|में|mein|का|की|के|कब|बैठते|बैठने|evening|morning|afternoon))+$", "")
                .trim();
    }

    private NormalizedUserTurn.EntityReference specialtyEntity(String text) {
        Matcher matcher = HINDI_SPECIALTY.matcher(text);
        if (matcher.find()) {
            String span = matcher.group(1).trim();
            String query = DevanagariNameTransliterator.titleCaseWords(DevanagariNameTransliterator.transliterate(span));
            return new NormalizedUserTurn.EntityReference(span, query.isBlank() ? span : query);
        }
        return null;
    }
}
