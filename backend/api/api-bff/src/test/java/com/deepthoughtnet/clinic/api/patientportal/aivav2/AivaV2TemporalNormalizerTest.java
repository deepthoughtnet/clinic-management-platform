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
    void resolvesSupportedAbsoluteDateForms() {
        assertThat(normalizer.normalize("14.09.2026", ValuePatch.set("14.09.2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("14/09/2026", ValuePatch.set("14/09/2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("14-09-2026", ValuePatch.set("14-09-2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("Book Dr Akshu on 14.09.2026", ValuePatch.unchanged(), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(normalizer.normalize("12.09.2026", ValuePatch.set("12.09.2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(normalizer.normalize("09.12.2026", ValuePatch.set("09.12.2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 12, 9));
        assertThat(normalizer.normalize("12 September 2026", ValuePatch.set("12 September 2026"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(normalizer.normalize("2026-09-12", ValuePatch.set("2026-09-12"), null).localDate())
                .isEqualTo(LocalDate.of(2026, 9, 12));
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

        assertThat(result.status()).isEqualTo(AivaV2TemporalResolution.Status.RESOLVED);
        assertThat(result.localDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(result.source()).isEqualTo(AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT);
    }
}
