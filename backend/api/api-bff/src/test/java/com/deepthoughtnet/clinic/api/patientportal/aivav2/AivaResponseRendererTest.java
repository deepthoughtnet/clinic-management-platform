package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaStructuredResponse.*;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.InteractiveAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.MessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AivaResponseRendererTest {
    private final AivaResponseRenderer renderer = new AivaResponseRenderer();

    @Test
    void rendersProviderChoicesFromPayloadEvenWhenActionsAreEmpty() {
        var structured = AivaStructuredResponse.of(ResponseType.PROVIDER_CHOICES,
                new ProviderChoicesPayload(List.of(new ProviderOption("p1", "Doc Akshu Kumar", null, null),
                        new ProviderOption("p2", "Demo Doctor", "Cardiology", null))), List.of());
        MessageResponse source = response("PROVIDER_CHOICES", structured, "legacy text must not be used");
        assertThat(renderer.render(source, "hi", ResponseStyle.STANDARD).assistantMessage())
                .isEqualTo("मुझे ये डॉक्टर मिले: 1. Doc Akshu Kumar; 2. Demo Doctor। आप किसे चुनेंगे?");
        assertThat(renderer.render(source, "hi", ResponseStyle.HINGLISH).assistantMessage())
                .isEqualTo("Mujhe ye doctors mile: 1. Doc Akshu Kumar; 2. Demo Doctor. Aap kise chunenge?");
        assertThat(renderer.render(source, "en", ResponseStyle.STANDARD).assistantMessage())
                .isEqualTo("I found these doctors: 1. Doc Akshu Kumar; 2. Demo Doctor. Which one would you like?");
    }

    @Test
    void rendersBookingAndAvailabilityInAllThreeStylesFromSameFacts() {
        var askDoctor = AivaStructuredResponse.of(ResponseType.NEED_PROVIDER,
                new NeedProviderPayload(null, null), List.of());
        assertThat(render(askDoctor, "en", ResponseStyle.STANDARD)).isEqualTo("Which doctor or specialty would you like?");
        assertThat(render(askDoctor, "hi", ResponseStyle.STANDARD)).isEqualTo("आप किस डॉक्टर या विशेषज्ञ के साथ अपॉइंटमेंट लेना चाहेंगे?");
        assertThat(render(askDoctor, "hi", ResponseStyle.HINGLISH)).isEqualTo("Aap kis doctor ya specialty ke saath appointment lena chahenge?");

        var slots = AivaStructuredResponse.of(ResponseType.AVAILABLE_SLOTS,
                new AvailabilityPayload("Doc Akshu Kumar", LocalDate.of(2026, 9, 24),
                        List.of(new SlotFact("opaque-slot-77", LocalTime.of(20, 0), null, "20:00")), true, null), List.of());
        assertThat(render(slots, "en", ResponseStyle.STANDARD)).isEqualTo("Available slots for 24 September 2026: 1. 20:00. Which one works?");
        assertThat(render(slots, "hi", ResponseStyle.STANDARD)).isEqualTo("24 सितंबर 2026 के लिए उपलब्ध स्लॉट: 1. 20:00। आपके लिए कौन-सा समय ठीक रहेगा?");
        assertThat(render(slots, "hi", ResponseStyle.HINGLISH)).isEqualTo("Available slots for 24 September 2026: 1. 20:00. Aapke liye kaunsa time theek rahega?");
    }

    @Test
    void emptyAvailabilityRendersAnExplicitNoSlotsMessageInAllThreeStyles() {
        var empty = AivaStructuredResponse.of(ResponseType.AVAILABLE_SLOTS,
                new AvailabilityPayload("Doc Akshu Kumar", LocalDate.of(2026, 9, 24), List.of(), false, null), List.of());
        assertThat(render(empty, "en", ResponseStyle.STANDARD))
                .isEqualTo("There are no available slots for 24 September 2026. Would you like to check another date?");
        assertThat(render(empty, "hi", ResponseStyle.STANDARD))
                .isEqualTo("24 सितंबर 2026 के लिए कोई स्लॉट उपलब्ध नहीं है। क्या आप दूसरी तारीख देखना चाहेंगे?");
        assertThat(render(empty, "hi", ResponseStyle.HINGLISH))
                .isEqualTo("24 September 2026 ke liye koi available slots nahi hain. Kya aap doosri date dekhna chahenge?");

        var emptyReschedule = AivaStructuredResponse.of(ResponseType.RESCHEDULE_AVAILABLE_SLOTS,
                new AvailabilityPayload("Doc Akshu Kumar", LocalDate.of(2026, 9, 24), List.of(), false, null), List.of());
        assertThat(render(emptyReschedule, "en", ResponseStyle.STANDARD))
                .contains("no available reschedule slots");
        assertThat(render(emptyReschedule, "hi", ResponseStyle.STANDARD)).contains("रीशेड्यूल");
        assertThat(render(emptyReschedule, "hi", ResponseStyle.HINGLISH)).contains("reschedule");
    }

    @Test
    void lookupUsesTypedAppointmentFactsWithoutMixedEnglishSentenceFragments() {
        var appointment = new AppointmentFact("APT-Ref/X9", "Doc Akshu Kumar", LocalDate.of(2026, 9, 23),
                LocalTime.of(20, 0), null, "CONFIRMED");
        var found = AivaStructuredResponse.of(ResponseType.APPOINTMENTS_FOUND,
                new AppointmentListPayload(List.of(appointment), new AppliedFilters(null, null, null, null, true)), List.of());
        assertThat(render(found, "en", ResponseStyle.STANDARD))
                .isEqualTo("You have an appointment with Doc Akshu Kumar on 23 September 2026 at 20:00.");
        assertThat(render(found, "hi", ResponseStyle.STANDARD))
                .isEqualTo("आपकी Doc Akshu Kumar के साथ 23 सितंबर 2026 को 20:00 बजे अपॉइंटमेंट है।");
        assertThat(render(found, "hi", ResponseStyle.HINGLISH))
                .isEqualTo("Aapki Doc Akshu Kumar ke saath 23 September 2026 ko 20:00 baje appointment hai.");
        assertThat(appointment.appointmentReference()).isEqualTo("APT-Ref/X9");
    }

    @Test
    void cancellationAndRescheduleRenderFromOperationSpecificPayloads() {
        var appointment = new AppointmentFact("ref", "Doc Akshu Kumar", LocalDate.of(2026, 9, 24), LocalTime.of(20, 0), null, null);
        var cancellation = AivaStructuredResponse.of(ResponseType.CANCELLATION_CONFIRMATION,
                new CancellationConfirmationPayload(appointment), List.of());
        assertThat(render(cancellation, "hi", ResponseStyle.STANDARD))
                .isEqualTo("आपकी Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:00 बजे अपॉइंटमेंट है। क्या मैं इसे रद्द कर दूँ?");
        var reschedule = AivaStructuredResponse.of(ResponseType.RESCHEDULE_CONFIRMATION,
                new RescheduleConfirmationPayload("Doc Akshu Kumar", appointment,
                        LocalDate.of(2026, 9, 26), LocalTime.of(18, 30)), List.of());
        assertThat(render(reschedule, "hi", ResponseStyle.STANDARD))
                .isEqualTo("Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:00 की अपॉइंटमेंट को 26 सितंबर 2026 को 18:30 पर शिफ्ट कर दूँ?");
    }

    @Test
    void bookingConfirmationSuccessAndLegacyCompatibility() {
        var confirmation = AivaStructuredResponse.of(ResponseType.BOOKING_CONFIRMATION,
                new BookingConfirmationPayload("Doc Akshu Kumar", LocalDate.of(2026, 9, 24), LocalTime.of(20, 0)), List.of());
        assertThat(render(confirmation, "hi", ResponseStyle.STANDARD))
                .isEqualTo("Doc Akshu Kumar के साथ 24 सितंबर 2026 को 20:00 बजे। क्या मैं इसे बुक कर दूँ?");
        var success = AivaStructuredResponse.of(ResponseType.BOOKING_SUCCESS,
                new BookingSuccessPayload("APT-Ref/X9", null, null, null), List.of());
        assertThat(render(success, "hi", ResponseStyle.STANDARD)).isEqualTo("आपकी अपॉइंटमेंट बुक हो गई है।");
        MessageResponse legacy = response("OLD", null, "legacy copy");
        assertThat(renderer.render(legacy, "hi", ResponseStyle.STANDARD)).isSameAs(legacy);
    }

    @Test
    void everySemanticResponseTypeRendersAllStylesFromPayloadWithoutReadingLegacyText() {
        LocalDate date = LocalDate.of(2026, 9, 24);
        LocalTime time = LocalTime.of(20, 0);
        var fact = new AppointmentFact("APT-REF", "Doc Akshu Kumar", date, time, "Clinic", "CONFIRMED");
        var list = new AppointmentListPayload(List.of(fact), new AppliedFilters("Dr Akshu", date, null, null, false));
        var availability = new AvailabilityPayload("Doc Akshu Kumar", date,
                List.of(new SlotFact("SLOT-REF", time, time.plusMinutes(30), "20:00")), true, null);
        var rescheduleConfirmation = new RescheduleConfirmationPayload("Doc Akshu Kumar", fact,
                date.plusDays(1), time.plusHours(1));
        List<AivaStructuredResponse> responses = List.of(
                AivaStructuredResponse.of(ResponseType.NEED_PROVIDER, new NeedProviderPayload(null, null), List.of()),
                AivaStructuredResponse.of(ResponseType.PROVIDER_CHOICES,
                        new ProviderChoicesPayload(List.of(new ProviderOption("P1", "Doc Akshu Kumar", "General Medicine", "Clinic"))), List.of()),
                AivaStructuredResponse.of(ResponseType.PROVIDER_NOT_FOUND, new ProviderNotFoundPayload("Dr Unknown", null), List.of()),
                AivaStructuredResponse.of(ResponseType.NEED_DATE, new NeedDatePayload("Doc Akshu Kumar"), List.of()),
                AivaStructuredResponse.of(ResponseType.AVAILABLE_SLOTS, availability, List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_AVAILABLE_SLOTS, availability, List.of()),
                AivaStructuredResponse.of(ResponseType.NO_MORE_SLOTS, new NoMoreSlotsPayload("Doc Akshu Kumar", date, null), List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_NO_MORE_SLOTS, new NoMoreSlotsPayload("Doc Akshu Kumar", date, null), List.of()),
                AivaStructuredResponse.of(ResponseType.BOOKING_CONFIRMATION, new BookingConfirmationPayload("Doc Akshu Kumar", date, time), List.of()),
                AivaStructuredResponse.of(ResponseType.BOOKING_SUCCESS, new BookingSuccessPayload("APT-REF", "Doc Akshu Kumar", date, time), List.of()),
                AivaStructuredResponse.of(ResponseType.APPOINTMENTS_NONE, new AppointmentListPayload(List.of(), null), List.of()),
                AivaStructuredResponse.of(ResponseType.APPOINTMENTS_FOUND, list, List.of()),
                AivaStructuredResponse.of(ResponseType.LOOKUP_CLARIFICATION, new ClarificationPayload("FILTER_MISSING", List.of()), List.of()),
                AivaStructuredResponse.of(ResponseType.CANCELLATION_NONE, new CancellationChoicesPayload(List.of()), List.of()),
                AivaStructuredResponse.of(ResponseType.CANCELLATION_CHOICES, new CancellationChoicesPayload(List.of(fact)), List.of()),
                AivaStructuredResponse.of(ResponseType.CANCELLATION_CONFIRMATION, new CancellationConfirmationPayload(fact), List.of()),
                AivaStructuredResponse.of(ResponseType.CANCELLATION_REJECTED, new CancellationConfirmationPayload(fact), List.of()),
                AivaStructuredResponse.of(ResponseType.CANCELLATION_SUCCESS, new CancellationSuccessPayload(fact), List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_SOURCE_NONE, new RescheduleSourceChoicesPayload(List.of()), List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_SOURCE_CHOICES, new RescheduleSourceChoicesPayload(List.of(fact)), List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_NEED_DATE, new RescheduleNeedDatePayload("Doc Akshu Kumar", fact), List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_CONFIRMATION, rescheduleConfirmation, List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_REJECTED, rescheduleConfirmation, List.of()),
                AivaStructuredResponse.of(ResponseType.RESCHEDULE_SUCCESS,
                        new RescheduleSuccessPayload("Doc Akshu Kumar", date, time, date.plusDays(1), time.plusHours(1)), List.of()),
                AivaStructuredResponse.of(ResponseType.STALE_RESULT, new StaleResultPayload("STALE", null), List.of()),
                AivaStructuredResponse.of(ResponseType.FAILURE, new FailurePayload("SAFE_FAILURE"), List.of()),
                AivaStructuredResponse.of(ResponseType.CLARIFICATION, new ClarificationPayload("MISSING_DATE", List.of("date")), List.of()),
                AivaStructuredResponse.of(ResponseType.CALL_TO_BOOK, new CallToBookPayload("Doc Akshu Kumar", "Clinic", null), List.of())
        );

        for (AivaStructuredResponse structured : responses) {
            MessageResponse source = response(structured.type().name(), structured, "LEGACY_POISON_MUST_NOT_RENDER");
            assertThat(structured.type()).isNotEqualTo(ResponseType.LEGACY);
            for (var locale : List.of(
                    new Object[]{"en", ResponseStyle.STANDARD},
                    new Object[]{"hi", ResponseStyle.HINGLISH},
                    new Object[]{"hi", ResponseStyle.STANDARD})) {
                String language = (String) locale[0];
                ResponseStyle style = (ResponseStyle) locale[1];
                String rendered = renderer.render(source, language, style).assistantMessage();
                assertThat(rendered).as(structured.type() + "/" + language + "/" + style)
                        .isNotBlank().doesNotContain("LEGACY_POISON_MUST_NOT_RENDER");
            }
        }
        assertThat(responses).hasSize(ResponseType.values().length - 1);
    }

    private String render(AivaStructuredResponse structured, String language, ResponseStyle style) {
        return renderer.render(response(structured.type().name(), structured, "English should not leak"), language, style).assistantMessage();
    }

    private MessageResponse response(String category, AivaStructuredResponse structured, String text) {
        return new MessageResponse("conversation", "turn", text, category, null, "test", false, List.<InteractiveAction>of(), structured);
    }
}
