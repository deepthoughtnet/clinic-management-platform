package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceToolExecutor.QueryType;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.NormalizedUserTurn.Daypart;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.language.ResponseStyle;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceAvailabilityData;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceScheduleWindow;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway.VoiceSlot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceReadOnlyAivaServiceScheduleTest {
    private final VoiceReadOnlyAivaService service = new VoiceReadOnlyAivaService(
            mock(HealthcareAiReadOnlyToolGateway.class), mock(AivaV2VoiceConversationAdapter.class));

    @Test
    void scheduleAnswersFromConfiguredHoursEvenWhenNoSlotsAreBookable() {
        VoiceAvailabilityData schedule = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.RECURRING_SCHEDULE, null, Daypart.EVENING,
                List.of(new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0);

        String response = service.renderAvailability(schedule, "hi-IN", ResponseStyle.STANDARD);

        assertThat(response).contains("सोमवार", "5 शाम", "8 शाम");
        assertThat(response).doesNotContain("कोई appointment slot खाली नहीं है");
    }

    @Test
    void noBookableSlotsOnDateDoesNotDenyConfiguredConsultationSchedule() {
        VoiceAvailabilityData effective = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, LocalDate.of(2026, 10, 20), Daypart.EVENING,
                List.of(new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0);

        String response = service.renderAvailability(effective, "hi-IN", ResponseStyle.STANDARD);

        assertThat(response).contains("consultation करते हैं", "कोई appointment slot खाली नहीं है");
    }

    @Test
    void asksOnlyAmongAvailableDaypartsAndLimitsSpokenSlotsToThree() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        VoiceAvailabilityData grouped = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, date, null,
                List.of(new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.NOON,
                        null, null, 30), new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(17, 0),
                        LocalTime.of(20, 0), null, null, 30)),
                List.of(slot(date, 9, 0), slot(date, 9, 30), slot(date, 17, 0)),
                List.of(Daypart.MORNING, Daypart.EVENING), List.of(), false, 0);

        String choicePrompt = service.renderAvailability(grouped, "hi-IN", ResponseStyle.STANDARD);
        VoiceAvailabilityData eveningSlots = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.SLOT_AVAILABILITY, date, Daypart.EVENING, List.of(),
                List.of(slot(date, 17, 0), slot(date, 17, 30), slot(date, 18, 0), slot(date, 18, 30)),
                List.of(Daypart.EVENING), List.of(), false, 0);
        String slotResponse = service.renderAvailability(eveningSlots, "hi-IN", ResponseStyle.STANDARD);

        assertThat(choicePrompt).contains("सुबह और शाम").doesNotContain("दोपहर");
        assertThat(slotResponse).contains("5 शाम", "5:30 शाम", "6 शाम").doesNotContain("6:30 शाम");
    }

    @Test
    void rendersScheduleAndAvailabilityInEnglishAndHinglish() {
        VoiceAvailabilityData schedule = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.RECURRING_SCHEDULE, null, null,
                List.of(new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.NOON,
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0);
        VoiceAvailabilityData options = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, LocalDate.of(2026, 10, 20), null,
                List.of(new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.NOON,
                        null, null, 30), new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(17, 0),
                        LocalTime.of(20, 0), null, null, 30)),
                List.of(slot(LocalDate.of(2026, 10, 20), 9, 0)), List.of(Daypart.MORNING, Daypart.EVENING),
                List.of(), false, 0);

        String english = service.renderAvailability(schedule, "en-IN", ResponseStyle.STANDARD);
        String hinglish = service.renderAvailability(options, "hi-IN", ResponseStyle.HINGLISH);

        assertThat(english).contains("Monday", "9 AM", "12 PM", "consults");
        assertThat(hinglish).contains("Morning aur evening", "slots available hain");
    }

    @Test
    void groupsWeeklyWindowsWithoutFlatteningWeekdaysOrAnnouncingBreaks() {
        VoiceAvailabilityData schedule = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.RECURRING_SCHEDULE, null, null,
                List.of(
                        new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0),
                                LocalTime.of(11, 0), LocalTime.of(11, 30), 30),
                        new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                                null, null, 30),
                        new VoiceScheduleWindow(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0),
                                null, null, 30),
                        new VoiceScheduleWindow(DayOfWeek.FRIDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                                null, null, 30)), List.of(), List.of(), List.of(), false, 0);

        String response = service.renderAvailability(schedule, "hi-IN", ResponseStyle.STANDARD);

        assertThat(response).contains("सोमवार", "बुधवार", "शुक्रवार", "9 सुबह से 1 दोपहर", "5 शाम से 8 शाम")
                .doesNotContain("11");
        assertThat(response).contains("सोमवार 9 सुबह से 1 दोपहर और 5 शाम से 8 शाम");
    }

    @Test
    void dateSpecificScheduleUsesConsultationFactsAndDoesNotDependOnFreeSlots() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        VoiceAvailabilityData effective = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_SCHEDULE, date, Daypart.EVENING,
                List.of(new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0);
        VoiceAvailabilityData leave = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_SCHEDULE, date, Daypart.EVENING, List.of(), List.of(), List.of(),
                List.of(), false, 0, true, false, false);
        VoiceAvailabilityData noSchedule = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_SCHEDULE, date, Daypart.EVENING, List.of(), List.of(), List.of(),
                List.of(), false, 0);

        assertThat(service.renderAvailability(effective, "hi-IN", ResponseStyle.STANDARD))
                .contains("डॉ. Ravi Mehta", "consultation करते हैं", "slots भी देखना चाहेंगे");
        assertThat(service.renderAvailability(leave, "hi-IN", ResponseStyle.STANDARD))
                .contains("आम तौर पर", "उपलब्ध नहीं हैं");
        assertThat(service.renderAvailability(noSchedule, "hi-IN", ResponseStyle.STANDARD))
                .contains("उस दिन शाम में consultation नहीं करते");
    }

    @Test
    void distinguishesFullAndPastSlotsAndKeepsNoMoreResponsesLocalized() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        VoiceAvailabilityData full = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.SLOT_AVAILABILITY, date, Daypart.EVENING,
                List.of(new VoiceScheduleWindow(DayOfWeek.TUESDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0, false, false, true);
        VoiceAvailabilityData past = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.SLOT_AVAILABILITY, LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), Daypart.EVENING,
                List.of(new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0, false, true, false);
        VoiceAvailabilityData exhausted = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.SLOT_AVAILABILITY, date, Daypart.EVENING, List.of(),
                List.of(slot(date, 17, 0)), List.of(Daypart.EVENING), List.of(), false, 3);

        assertThat(service.renderAvailability(full, "hi-IN", ResponseStyle.STANDARD))
                .contains("consult करते हैं", "कोई appointment slot खाली नहीं है");
        assertThat(service.renderAvailability(past, "hi-IN", ResponseStyle.STANDARD))
                .contains("कोई future slot available नहीं है");
        assertThat(service.renderAvailability(exhausted, "en-IN", ResponseStyle.STANDARD))
                .contains("no more slots available in this time window");
    }

    @Test
    void responseStyleKeepsEnglishAndHinglishDistinct() {
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
        VoiceAvailabilityData available = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, date, Daypart.EVENING,
                List.of(new VoiceScheduleWindow(date.getDayOfWeek(), LocalTime.of(17, 0), LocalTime.of(20, 0),
                        null, null, 30)),
                List.of(slot(date, 17, 0), slot(date, 17, 30), slot(date, 18, 0)),
                List.of(Daypart.EVENING), List.of(), false, 0);
        String english = service.renderAvailability(available, "en-IN", ResponseStyle.STANDARD);
        String hinglish = service.renderAvailability(available, "hi-IN", ResponseStyle.HINGLISH);

        assertThat(english).contains("Tomorrow evening", "5:00 PM", "available").doesNotContain("उपलब्ध");
        assertThat(hinglish).contains("Kal evening", "slots available hain");
    }

    @Test
    void englishUtteranceInHindiSessionIsRenderedInEnglish() {
        String utterance = "What are Ravi Mehta's consultation hours?";
        var turn = com.deepthoughtnet.clinic.api.patientportal.aivav2.language.LanguageAdapterRegistry.defaults()
                .resolve("hi-IN", utterance).normalize(utterance);
        VoiceAvailabilityData schedule = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.RECURRING_SCHEDULE, null, null,
                List.of(new VoiceScheduleWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.NOON,
                        null, null, 30)), List.of(), List.of(), List.of(), false, 0);

        String response = service.renderAvailability(schedule, turn.responseLanguage(), turn.responseStyle());

        assertThat(response).contains("Monday", "consults").doesNotContain("सोमवार", "करते हैं");
    }

    @Test
    void dateOnlyNoScheduleWordingDoesNotInventTimeReference() {
        VoiceAvailabilityData noDaypart = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, LocalDate.of(2026, 10, 24), null,
                List.of(), List.of(), List.of(), List.of(), false, 0);
        VoiceAvailabilityData evening = new VoiceAvailabilityData("doctor-ravi", "Ravi Mehta",
                QueryType.EFFECTIVE_DATE_AVAILABILITY, LocalDate.of(2026, 10, 24), Daypart.EVENING,
                List.of(), List.of(), List.of(), List.of(), false, 0);

        String dateOnlyResponse = service.renderAvailability(noDaypart, "hi-IN", ResponseStyle.STANDARD);
        String eveningResponse = service.renderAvailability(evening, "hi-IN", ResponseStyle.STANDARD);

        assertThat(dateOnlyResponse).contains("उस दिन consultation नहीं करते")
                .doesNotContain("इस समय", "उस समय");
        assertThat(eveningResponse).contains("उस दिन शाम में consultation नहीं करते");
    }

    private VoiceSlot slot(LocalDate date, int hour, int minute) {
        LocalTime start = LocalTime.of(hour, minute);
        return new VoiceSlot(date, start, start.plusMinutes(30));
    }
}
