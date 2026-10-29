package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2TemporalNormalizerTest {
    private AivaV2TemporalNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void resolvesRelativeDatesFromCurrentTurn() {
        assertThat(normalizer.normalize("tomorrow", ValuePatch.set("tomorrow"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(normalizer.normalize("day after tomorrow", ValuePatch.set("day after tomorrow"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    void relativeDateUsesExplicitTenantZoneAcrossUtcDateBoundary() {
        AivaV2TemporalNormalizer tenantZoneNormalizer = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-09-11T20:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("Asia/Kolkata"));

        var tomorrow = tenantZoneNormalizer.normalize("tomorrow", ValuePatch.unchanged(), null);

        assertThat(tomorrow.localDate()).isEqualTo(LocalDate.of(2026, 9, 13));
    }

    @Test
    void tomorrowUsesBookingZoneBeforeAndAfterKolkataMidnightRegardlessOfJvmZone() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        AivaV2TemporalNormalizer beforeLocalMidnight = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-10-25T18:29:00Z"), ZoneOffset.UTC), kolkata);
        AivaV2TemporalNormalizer afterLocalMidnight = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-10-25T18:31:00Z"), ZoneOffset.UTC), kolkata);
        AivaV2TemporalNormalizer utc = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-10-25T18:31:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);

        assertThat(beforeLocalMidnight.normalize("कल", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(afterLocalMidnight.normalize("कल", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(utc.normalize("tomorrow", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void resolvesSupportedAbsoluteDateForms() {
        assertThat(normalizer.normalize("14.09.2026", ValuePatch.set("14.09.2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("14/09/2026", ValuePatch.set("14/09/2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("14-09-2026", ValuePatch.set("14-09-2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("Book Dr Akshu on 14.09.2026", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("12.09.2026", ValuePatch.set("12.09.2026"), null).status())
                .isEqualTo(AivaV2TemporalResolution.Status.AMBIGUOUS);
        assertThat(normalizer.normalize("09.12.2026", ValuePatch.set("09.12.2026"), null).status())
                .isEqualTo(AivaV2TemporalResolution.Status.AMBIGUOUS);
        assertThat(normalizer.normalize("12 September 2026", ValuePatch.set("12 September 2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(normalizer.normalize("September 9", ValuePatch.set("September 9"), null).localDate())
                .isEqualTo(LocalDate.of(2027, 9, 9));
        assertThat(normalizer.normalize("2026-09-12", ValuePatch.set("2026-09-12"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    void resolvesSpokenHindiWeekdayToNextOccurrenceAndOverridesSessionDate() {
        AivaV2TemporalNormalizer octoberNormalizer = new AivaV2TemporalNormalizer(
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC), ZoneId.of("Asia/Kolkata"));

        var monday = octoberNormalizer.normalize("मंडे का स्लॉट बताओ", ValuePatch.unchanged(),
                LocalDate.of(2026, 10, 25));

        assertThat(monday.status()).isEqualTo(AivaV2TemporalResolution.Status.RESOLVED);
        assertThat(monday.source()).isEqualTo(AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT);
        assertThat(monday.localDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(octoberNormalizer.normalize("ट्यूसडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(octoberNormalizer.normalize("वेडनेसडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(octoberNormalizer.normalize("थर्सडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(octoberNormalizer.normalize("फ्राइडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(octoberNormalizer.normalize("सैटरडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(octoberNormalizer.normalize("संडे", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void appliesSafeNumericAmbiguityPolicy() {
        var ambiguous = normalizer.normalize("09/10", ValuePatch.set("09/10"), null);
        assertThat(ambiguous.status()).isEqualTo(AivaV2TemporalResolution.Status.AMBIGUOUS);
        assertThat(ambiguous.candidateDates()).containsExactly(
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 9, 10));
        assertThat(normalizer.normalize("13/10", ValuePatch.set("13/10"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(normalizer.normalize("09/10/26", ValuePatch.set("09/10/26"), null).status())
                .isEqualTo(AivaV2TemporalResolution.Status.AMBIGUOUS);
    }

    @Test
    void resolvesMonthAndDayWithoutYearToTheNextOccurrence() {
        var result = normalizer.normalize("15 September", ValuePatch.unchanged(), null);

        assertThat(result.status()).isEqualTo(AivaV2TemporalResolution.Status.RESOLVED);
        assertThat(result.source()).isEqualTo(AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT);
        assertThat(result.localDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void currentTurnWinsWhenModelDateContradictsIt() {
        var result = normalizer.normalize("Book with Akshu tomorrow", ValuePatch.set("2026-09-14"),
                LocalDate.of(2026, 9, 14));

        assertThat(result.localDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(result.source()).isEqualTo(AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT);
        assertThat(result.modelVsDeterministicMismatch()).isTrue();
    }

    @Test
    void absentCurrentDatePreservesPreviousDate() {
        var result = normalizer.normalize("What slots are available?", ValuePatch.unchanged(),
                LocalDate.of(2026, 9, 14));

        assertThat(result.status()).isEqualTo(AivaV2TemporalResolution.Status.UNCHANGED);
        assertThat(result.localDate()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void impossibleNumericDatesAreInvalid() {
        var result = normalizer.normalize("31.02.2026", ValuePatch.set("31.02.2026"), null);

        assertThat(result.status()).isEqualTo(AivaV2TemporalResolution.Status.INVALID);
        assertThat(result.localDate()).isNull();
        assertThat(normalizer.normalize("14.13.2026", ValuePatch.set("14.13.2026"), null).status())
                .isEqualTo(AivaV2TemporalResolution.Status.INVALID);
        assertThat(normalizer.normalize("", ValuePatch.set("31.02.2026"), null).status())
                .isEqualTo(AivaV2TemporalResolution.Status.INVALID);
    }

    @Test
    void dateChangeIsRecognizedAsSet() {
        var result = normalizer.normalize("appointment on 12.09.2026", ValuePatch.unchanged(),
                LocalDate.of(2026, 9, 14));

        assertThat(result.status()).isEqualTo(AivaV2TemporalResolution.Status.AMBIGUOUS);
        assertThat(result.localDate()).isNull();
        assertThat(result.source()).isEqualTo(AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT);
    }
}
