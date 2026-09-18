package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HindiLanguageAdapter extends EnglishLanguageAdapter {
    private static final Pattern HINDI_SIGNAL = Pattern.compile(
            "(?:mujhe|meri|mere|ke\\s+saath|karni\\s+hai|wali|wala|theek|hai|haan|nahi|nahin|subah|dopahar|shaam|raat|baje|dikhao|aur|dusri|agali|pehla|पहला|हाँ|हां|नहीं|नही|सुबह|दोपहर|शाम|रात|बजे|दिखाओ|और|दूसरी|अगली|स्लॉट)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HINGLISH_DOCTOR = Pattern.compile(
            "(?i)((?:doctor|dr\\.?|doc)\\s+[\\p{L}][\\p{L}\\p{N}'-]*(?:\\s+(?!ke\\b|के(?=\\s)|sath\\b|साथ(?=\\s)|with\\b|appointment\\b)[\\p{L}][\\p{L}\\p{N}'-]*){0,3})"
                    + "(?=\\s+(?:ke\\s+(?:saath|sath)|के\\s+साथ|से|with|appointment)(?:\\s|$)|[?.!,।]|$)");
    private static final Pattern DEVANAGARI_DOCTOR = Pattern.compile(
            "((?:डॉ\\.?|डॉक्टर)\\s+(?!(?:के|साथ|से)(?=\\s|$))[\\p{IsDevanagari}\\p{M}]+"
                    + "(?:\\s+(?!(?:के|साथ|से)(?=\\s|$))[\\p{IsDevanagari}\\p{M}]+){0,3})"
                    + "(?=\\s+(?:के\\s+साथ|से|की|का)(?:\\s|$)|[?.!,]|$)");
    private static final Pattern HINDI_SPECIALTY = Pattern.compile(
            "(?:विशेषज्ञता|विशेषज्ञ|स्पेशलिटी)\\s+(?:में|की)?\\s*([\\p{IsDevanagari}\\p{M}][\\p{IsDevanagari}\\p{M} ]{1,50})");

    @Override public String languageCode() { return "hi"; }

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
                .replaceAll("(?<![\\p{L}\\p{N}])(?:subah|सुबह)(?![\\p{L}\\p{N}])", " morning ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:dopahar|दोपहर)(?![\\p{L}\\p{N}])", " afternoon ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:shaam|शाम|raat|रात)(?![\\p{L}\\p{N}])", " evening ")
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
                .replaceAll("(?<![\\p{L}\\p{N}])(?:रद्द|कैंसल|cancel)(?![\\p{L}\\p{N}])", " cancel ")
                .replaceAll("फिर\\s+से\\s+(?:तय|शेड्यूल)", " reschedule ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:शेड्यूल|reschedule)(?![\\p{L}\\p{N}])", " reschedule ")
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
                .replaceAll("(?i)(?:theek\\s+hai|theek|ठीक\\s+है|ठीक)", " works ")
                .replaceAll("(?i)(?<![\\p{L}\\p{N}])(?:pehla|पहला|पहली)(?![\\p{L}\\p{N}])", " first ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:dusra|दूसरा|दूसरी)(?![\\p{L}\\p{N}])", " second ")
                .replaceAll("(?<![\\p{L}\\p{N}])(?:subah|सुबह|dopahar|दोपहर|shaam|शाम|raat|रात)(?![\\p{L}\\p{N}])", " ");
        String selection = normalizeSelection(selectionInput.toLowerCase(Locale.ROOT)
                .replaceAll("[?.!,;\\u0964\\u0965]+$", "").trim());

        NormalizedUserTurn.EntityReference doctor = doctorEntity(original);
        NormalizedUserTurn.EntityReference specialty = specialtyEntity(original);
        boolean qualifier = doctor != null || Pattern.compile("(?:के\\s+साथ|में|से)\\s+[^?.!,]+").matcher(original).find();
        String responseStyle = original.codePoints().anyMatch(cp -> cp >= 0x0900 && cp <= 0x097F)
                ? "STANDARD" : "HINGLISH";
        return canonicalTurn(original, normalized, selection, languageCode(), "hi",
                ResponseStyle.valueOf(responseStyle), doctor, specialty, qualifier, referenceDate);
    }

    private NormalizedUserTurn.EntityReference doctorEntity(String text) {
        Matcher latin = HINGLISH_DOCTOR.matcher(text);
        if (latin.find()) return new NormalizedUserTurn.EntityReference(latin.group(1).trim(), latin.group(1).trim());
        Matcher devanagari = DEVANAGARI_DOCTOR.matcher(text);
        if (!devanagari.find()) return null;
        String span = devanagari.group(1).trim();
        String name = span.replaceFirst("^(?:डॉक्टर|डॉ\\.?)\\s*", "");
        String canonical = DevanagariNameTransliterator.titleCaseWords(
                DevanagariNameTransliterator.transliterate(name));
        return new NormalizedUserTurn.EntityReference(span, canonical.isBlank() ? span : "Dr " + canonical);
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
