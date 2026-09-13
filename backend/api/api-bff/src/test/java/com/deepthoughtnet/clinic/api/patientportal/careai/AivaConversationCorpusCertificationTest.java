package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.careai.CareAiTaskNotificationService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AivaConversationCorpusCertificationTest {
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID APP_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.now(CLINIC_ZONE);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDate DAY_AFTER_TOMORROW = TODAY.plusDays(2);
    private static final LocalDate NEXT_MONDAY = TODAY.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate NEXT_FRIDAY = TODAY.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    private static final LocalDate NEXT_SATURDAY = TODAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    private static final LocalDate NEXT_WEEK = TODAY.plusWeeks(1);

    private enum CorpusTier {
        CERTIFIED,
        EXPLORATORY
    }

    private enum GapClassification {
        SUPPORTED_REGRESSION,
        SUPPORTED_BUT_INCOMPLETE,
        UNSUPPORTED,
        AMBIGUOUS_EXPECTATION,
        FUTURE_ENHANCEMENT
    }

    @Test
    void certifyUtteranceCorpus() {
        CorpusReport report = new CorpusReport("utterance");
        runCases(report, certifiedUtteranceCorpus());
        runCases(report, exploratoryUtteranceCorpus());
        report.printSummary();
        report.assertCertifiedClean();
    }

    @Test
    void certifyConversationCorpus() {
        CorpusReport report = new CorpusReport("conversation");
        runCases(report, certifiedConversationCorpus());
        runCases(report, exploratoryConversationCorpus());
        report.printSummary();
        report.assertCertifiedClean();
    }

    private void runCases(CorpusReport report, List<CorpusCase> cases) {
        for (CorpusCase corpusCase : cases) {
            runCase(report, corpusCase);
        }
    }

    private void runCase(CorpusReport report, CorpusCase corpusCase) {
        try (CorpusHarness harness = new CorpusHarness()) {
            corpusCase.runner().accept(harness);
            report.pass(corpusCase);
        } catch (AssertionError | RuntimeException ex) {
            report.fail(corpusCase, ex);
        }
    }

    private static List<CorpusCase> certifiedUtteranceCorpus() {
        List<CorpusCase> cases = new ArrayList<>();

        cases.add(new CorpusCase(
                "cert-intent-book-appointment-en",
                "booking",
                "BOOK_APPOINTMENT",
                "INTENT",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    PatientPortalCareAiMessageResponse response = harness.message("I want to book an appointment.", "en");
                    assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
                    assertThat(response.assistantMessage()).isNotBlank();
                }
        ));
        cases.add(new CorpusCase(
                "cert-intent-book-appointment-hi",
                "booking",
                "BOOK_APPOINTMENT",
                "INTENT",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "hi",
                harness -> {
                    when(harness.patientPortalService.doctors()).thenReturn(List.of(
                            PatientPortalCareAiTestSupport.doctor("doctor-vikas", "Dr Vikas", "General Medicine")
                    ));
                    when(harness.patientPortalService.doctorSlots("doctor-vikas", TOMORROW)).thenReturn(List.of(
                            harness.slot(TOMORROW, LocalTime.of(10, 0), true)
                    ));
                    PatientPortalCareAiMessageResponse response = harness.message("मुझे डॉक्टर विकास की अपॉइंटमेंट बुक करनी है", "hi");
                    assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
                    assertThat(response.assistantMessage()).doesNotContain("Please tell me the doctor name or speciality you want.");
                }
        ));

        cases.add(new CorpusCase(
                "cert-speciality-general-medicine",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("General Medicine", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-general-physician",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("General Physician", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-gp",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("GP", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-speciality-prefix",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("speciality General Medicine", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-specialty-prefix",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("specialty General Medicine", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-department-prefix",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("department General Medicine", "en").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-hindi-general-physician",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "hi",
                harness -> assertThat(harness.extract("जनरल फिजिशियन", "hi").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-hindi-general-medicine",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "hi",
                harness -> assertThat(harness.extract("जनरल मेडिसिन", "hi").speciality()).isEqualTo("General Medicine")
        ));
        cases.add(new CorpusCase(
                "cert-speciality-hinglish-general-physician",
                "entity-resolution",
                "BOOK_APPOINTMENT",
                "SPECIALITY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> assertThat(harness.extract("mujhe general physician chahiye", "en").speciality()).isEqualTo("General Medicine")
        ));

        return cases;
    }

    private static List<CorpusCase> exploratoryUtteranceCorpus() {
        List<CorpusCase> cases = new ArrayList<>();

        cases.addAll(buildCases("booking-start", "en", "BOOK_APPOINTMENT", "INTENT", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "I want to book an appointment",
                "Book a doctor",
                "Need an appointment",
                "I need a physician",
                "Can I see a doctor",
                "Doctor tomorrow",
                "I need to see someone tomorrow",
                "Please help me book",
                "mujhe doctor appointment chahiye",
                "मुझे डॉक्टर की अपॉइंटमेंट चाहिए"
        ), (harness, phrase, language) -> {
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
            assertThat(response.assistantMessage()).isNotBlank();
        }));

        cases.addAll(buildCases("specialty-resolution", "en", "BOOK_APPOINTMENT", "SPECIALITY", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "General Medicine",
                "General Physician",
                "physician",
                "GP",
                "speciality General Medicine",
                "specialty General Medicine",
                "department General Medicine",
                "general medicine ka doctor",
                "जनरल फिजिशियन",
                "जनरल मेडिसिन"
        ), (harness, phrase, language) -> {
            harness.message("I want to book an appointment.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().speciality()).isEqualTo("General Medicine");
            assertThat(response.assistantMessage()).doesNotContain("Please tell me the doctor name or speciality you want.");
        }));

        cases.addAll(buildCases("doctor-resolution", "en", "BOOK_APPOINTMENT", "DOCTOR", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "Dr Akshu",
                "Doc Akshu Kumar",
                "Akshu",
                "Book Doc Akshu",
                "appointment with Akshu tomorrow",
                "can I meet Dr Akshu tomorrow",
                "Dr Neha",
                "Doc Neha Mehta",
                "Neha",
                "doctor Neha"
        ), (harness, phrase, language) -> {
            harness.message("I want to book an appointment.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().doctorName()).isNotBlank();
            assertThat(response.assistantMessage().toLowerCase(Locale.ROOT)).contains("date");
        }));

        cases.addAll(buildCases("date-resolution", "en", "BOOK_APPOINTMENT", "DATE", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "tomorrow",
                "day after tomorrow",
                "next Monday",
                "next Friday",
                "15 September 2026",
                "this Saturday",
                "कल",
                "परसों",
                "आज",
                "next week"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().preferredDate()).isNotBlank();
            assertThat(response.assistantMessage().toLowerCase(Locale.ROOT)).containsAnyOf("time", "slot");
        }));

        cases.addAll(buildCases("time-resolution", "en", "BOOK_APPOINTMENT", "TIME", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "morning",
                "afternoon",
                "evening",
                "night",
                "10 AM",
                "10:30",
                "after lunch",
                "सुबह",
                "दोपहर",
                "शाम"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu tomorrow.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().preferredTimeWindow() != null || response.state().suggestedSlot() != null)
                    .isTrue();
        }));

        cases.addAll(buildCases("slot-selection", "en", "BOOK_APPOINTMENT", "SLOT", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "first one",
                "second one",
                "second doctor",
                "the last doctor",
                "that doctor",
                "the other doctor",
                "not this one",
                "show me another one",
                "first slot",
                "second slot"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu tomorrow.", "en");
            harness.message("10:30", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().suggestedSlot() != null || response.state().doctorName() != null)
                    .isTrue();
        }));

        cases.addAll(buildCases("confirmation-positive", "en", "BOOK_APPOINTMENT", "CONFIRMATION", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "yes",
                "yeah",
                "yep",
                "confirm",
                "book it",
                "go ahead",
                "okay book it",
                "हाँ",
                "जी हाँ",
                "बुक कर दीजिए"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu tomorrow at 10 AM.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().booked() || response.state().actionCompleted() || response.state().confirmationPending())
                    .isTrue();
        }));

        cases.addAll(buildCases("confirmation-negative", "en", "BOOK_APPOINTMENT", "CONFIRMATION", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "no",
                "don't",
                "cancel",
                "not yet",
                "never mind",
                "stop",
                "cancel this conversation",
                "forget that",
                "नहीं",
                "रद्द"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu tomorrow at 10 AM.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.state().booked()).isFalse();
            assertThat(response.assistantMessage()).isNotBlank();
        }));

        cases.addAll(buildCases("reset-help", "en", "CONTROL", "RESET", CorpusTier.EXPLORATORY, GapClassification.FUTURE_ENHANCEMENT, List.of(
                "start over",
                "reset conversation",
                "clear this chat",
                "what can you do",
                "help me",
                "hello",
                "hi",
                "good morning",
                "switch conversation",
                "बातचीत बदलो"
        ), (harness, phrase, language) -> {
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.assistantMessage()).isNotBlank();
        }));

        cases.addAll(buildCases("correction", "en", "BOOK_APPOINTMENT", "SWITCH", CorpusTier.EXPLORATORY, GapClassification.SUPPORTED_BUT_INCOMPLETE, List.of(
                "Actually another doctor",
                "No, Dr Akshu",
                "Make that Monday",
                "Tomorrow instead",
                "Afternoon instead",
                "Not that slot",
                "Second one instead",
                "Different clinic",
                "Actually I want General Medicine",
                "Sorry, I meant General Physician"
        ), (harness, phrase, language) -> {
            harness.message("Book appointment with Dr Akshu tomorrow.", "en");
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.assistantMessage()).isNotBlank();
        }));

        cases.addAll(buildCases("hindi-hinglish-mixed", "hi", "BOOK_APPOINTMENT", "MIXED", CorpusTier.EXPLORATORY, GapClassification.FUTURE_ENHANCEMENT, List.of(
                "मैं अपॉइंटमेंट बुक करना चाहता हूँ",
                "जनरल फिजिशियन",
                "कल सुबह",
                "दूसरा डॉक्टर",
                "हाँ बुक कर दीजिए",
                "mujhe doctor appointment chahiye",
                "general medicine ka doctor",
                "kal morning",
                "second one",
                "बुक कर दीजिए"
        ), (harness, phrase, language) -> {
            PatientPortalCareAiMessageResponse response = harness.message(phrase, language);
            assertThat(response.assistantMessage()).isNotBlank();
        }));

        return cases;
    }

    private static List<CorpusCase> certifiedConversationCorpus() {
        List<CorpusCase> cases = new ArrayList<>();

        cases.add(new CorpusCase(
                "cert-booking-hindi-date-carry-forward",
                "booking",
                "BOOK_APPOINTMENT",
                "DATE",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "mixed",
                harness -> {
                    when(harness.patientPortalService.doctors()).thenReturn(List.of(
                            PatientPortalCareAiTestSupport.doctor("doctor-vikas", "Dr Vikas", "General Medicine")
                    ));
                    when(harness.patientPortalService.doctorSlots("doctor-vikas", TOMORROW)).thenReturn(List.of(
                            harness.slot(TOMORROW, LocalTime.of(10, 0), true),
                            harness.slot(TOMORROW, LocalTime.of(10, 30), true)
                    ));
                    harness.message("Book appointment with Dr Vikas", "en");
                    PatientPortalCareAiMessageResponse response = harness.message("मैं 27 जून 2026 को आना चाहता हूँ", "hi");
                    assertThat(response.state().preferredDate()).isEqualTo(TOMORROW.toString());
                    assertThat(response.assistantMessage()).contains("कृपया इन उपलब्ध स्लॉट में से एक चुनिए");
                    assertThat(response.state().slotOptions()).containsExactly("10:00", "10:30");
                }
        ));

        cases.add(new CorpusCase(
                "cert-call-to-book-protection",
                "discovery",
                "FIND_DOCTOR",
                "CALL_TO_BOOK",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    PatientPortalCareAiMessageResponse response = harness.message("Book Dr Arjun Mehta", "en");
                    assertThat(response.state().confirmationPending()).isFalse();
                    assertThat(response.assistantMessage()).isNotBlank();
                    verify(harness.patientPortalService, never()).bookAppointment(any());
                }
        ));

        cases.add(new CorpusCase(
                "cert-cancel-confirmation",
                "cancel",
                "CANCEL_APPOINTMENT",
                "CONFIRMATION",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    PatientPortalCareAiMessageResponse first = harness.message("cancel my appointment", "en");
                    assertThat(first.state().currentIntent()).isEqualTo("CANCEL_APPOINTMENT");
                    assertThat(first.state().appointmentOptions()).hasSize(2);
                    PatientPortalCareAiMessageResponse second = harness.message("2", "en");
                    assertThat(second.state().confirmationPending()).isTrue();
                    assertThat(second.assistantMessage()).contains("Should I cancel this appointment?");
                    PatientPortalCareAiMessageResponse third = harness.message("yes", "en");
                    assertThat(third.state().actionCompleted()).isTrue();
                    assertThat(third.state().confirmationPending()).isFalse();
                    verify(harness.patientPortalService, org.mockito.Mockito.times(1)).cancelAppointment(any(), anyString(), nullable(String.class));
                }
        ));

        cases.add(new CorpusCase(
                "cert-workflow-switch-booking-to-cancel",
                "control",
                "CANCEL_APPOINTMENT",
                "SWITCH",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    when(harness.patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                            harness.appointment(UUID.randomUUID(), UUID.randomUUID(), "Dr Neha Mehta", TOMORROW, LocalTime.of(14, 0), "BOOKED")
                    ));
                    PatientPortalCareAiMessageResponse first = harness.message("Book appointment with Dr Neha Mehta", "en");
                    assertThat(first.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
                    PatientPortalCareAiMessageResponse response = harness.message("cancel appointment", "en");
                    assertThat(response.state().currentIntent()).isEqualTo("CANCEL_APPOINTMENT");
                    assertThat(response.assistantMessage()).contains("Should I cancel this appointment?");
                    assertThat(response.assistantMessage()).doesNotContain("cancel this booking flow");
                }
        ));

        cases.add(new CorpusCase(
                "cert-reset-clears-workflow",
                "control",
                "CONTROL",
                "RESET",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    when(harness.patientPortalService.doctors()).thenReturn(List.of(
                            PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
                    ));
                    harness.message("Book appointment with Dr Neha Mehta", "en");
                    PatientPortalCareAiMessageResponse response = harness.message("start over", "en");
                    assertThat(response.state().currentIntent()).isNull();
                    assertThat(response.state().doctorName()).isNull();
                    assertThat(response.state().suggestedSlot()).isNull();
                    assertThat(response.assistantMessage()).isNotBlank();
                }
        ));

        cases.add(new CorpusCase(
                "cert-read-only-appointments",
                "check",
                "CHECK_APPOINTMENT",
                "READ_ONLY",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
                    when(harness.patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                            harness.appointment(UUID.randomUUID(), UUID.randomUUID(), "Dr Neha Mehta", tomorrow, LocalTime.of(14, 0), "BOOKED")
                    ));
                    PatientPortalCareAiMessageResponse response = harness.message("When is my next appointment?", "en");
                    assertThat(response.state().currentIntent()).isEqualTo("CHECK_APPOINTMENT");
                    assertThat(response.assistantMessage()).contains("Dr Neha Mehta");
                    assertThat(response.assistantMessage()).contains("Sunrise Clinic");
                    assertThat(response.assistantMessage()).contains("14:00");
                }
        ));

        cases.add(new CorpusCase(
                "cert-booking-flow-switches-to-check-appointment",
                "control",
                "CHECK_APPOINTMENT",
                "SWITCH",
                CorpusTier.CERTIFIED,
                GapClassification.SUPPORTED_REGRESSION,
                "en",
                harness -> {
                    LocalDate tomorrow = LocalDate.now(CLINIC_ZONE).plusDays(1);
                    when(harness.patientPortalService.doctors()).thenReturn(List.of(
                            PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine")
                    ));
                    when(harness.patientPortalService.careAiUpcomingAppointments()).thenReturn(List.of(
                            harness.appointment(UUID.randomUUID(), UUID.randomUUID(), "Dr Neha Mehta", tomorrow, LocalTime.of(14, 0), "BOOKED")
                    ));
                    harness.message("Book appointment with Dr Neha Mehta", "en");
                    PatientPortalCareAiMessageResponse response = harness.message("check my appointment", "en");
                    assertThat(response.state().currentIntent()).isEqualTo("CHECK_APPOINTMENT");
                    assertThat(response.assistantMessage()).contains("Dr Neha Mehta");
                    assertThat(response.assistantMessage()).doesNotContain("cancel this booking flow");
                }
        ));

        return cases;
    }

    private static List<CorpusCase> exploratoryConversationCorpus() {
        List<CorpusCase> cases = new ArrayList<>();
        cases.addAll(bookingHappyPaths());
        cases.addAll(bookingCorrections());
        cases.addAll(callToBookPaths());
        cases.addAll(cancelPaths());
        cases.addAll(reschedulePaths());
        cases.addAll(readOnlyAndResetPaths());
        return cases;
    }

    private static List<CorpusCase> bookingHappyPaths() {
        List<String> starts = List.of(
                "I want to book an appointment",
                "Book a doctor",
                "Need an appointment",
                "I need a physician",
                "Can I see a doctor",
                "Doctor tomorrow",
                "I need to see someone tomorrow",
                "Please help me book",
                "मुझे डॉक्टर की अपॉइंटमेंट चाहिए",
                "mujhe doctor appointment chahiye"
        );
        List<String> specialties = List.of(
                "General Medicine",
                "General Physician",
                "physician",
                "GP",
                "speciality General Medicine",
                "specialty General Medicine",
                "department General Medicine",
                "जनरल फिजिशियन",
                "जनरल मेडिसिन",
                "general medicine ka doctor"
        );
        List<String> doctors = List.of(
                "Dr Akshu",
                "Doc Akshu Kumar",
                "Akshu",
                "Book Doc Akshu",
                "appointment with Akshu tomorrow",
                "can I meet Dr Akshu tomorrow",
                "Dr Neha",
                "Doc Neha Mehta",
                "Neha",
                "doctor Neha"
        );
        List<String> dates = List.of(
                "tomorrow",
                "day after tomorrow",
                "next Monday",
                "next Friday",
                "15 September 2026",
                "this Saturday",
                "कल",
                "परसों",
                "आज",
                "next week"
        );
        List<String> slots = List.of(
                "first one",
                "second one",
                "the last doctor",
                "that doctor",
                "the other doctor",
                "not this one",
                "show me another one",
                "first slot",
                "second slot",
                "10:30"
        );
        List<String> confirms = List.of(
                "yes",
                "yeah",
                "yep",
                "confirm",
                "book it",
                "go ahead",
                "okay book it",
                "हाँ",
                "जी हाँ",
                "बुक कर दीजिए"
        );

        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            String id = "booking-happy-" + (index + 1);
            String language = index >= 8 ? "hi" : index >= 6 ? "hinglish" : "en";
            cases.add(new CorpusCase(
                    id,
                    "booking",
                    "BOOK_APPOINTMENT",
                    "BOOKING_FLOW",
                    CorpusTier.EXPLORATORY,
                    GapClassification.SUPPORTED_BUT_INCOMPLETE,
                    language,
                    harness -> {
                        PatientPortalCareAiMessageResponse first = harness.message(starts.get(index), language);
                        assertThat(first.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");

                        PatientPortalCareAiMessageResponse second = harness.message(specialties.get(index), language);
                        assertThat(second.state().speciality()).isEqualTo("General Medicine");

                        PatientPortalCareAiMessageResponse third = harness.message(doctors.get(index), language);
                        assertThat(third.state().doctorName()).isNotBlank();

                        PatientPortalCareAiMessageResponse fourth = harness.message(dates.get(index), language);
                        assertThat(fourth.state().preferredDate()).isNotBlank();

                        PatientPortalCareAiMessageResponse fifth = harness.message(slots.get(index), language);
                        assertThat(fifth.state().suggestedSlot() != null || fifth.state().confirmationPending()).isTrue();

                        PatientPortalCareAiMessageResponse sixth = harness.message(confirms.get(index), language);
                        assertThat(sixth.state().booked() || sixth.state().actionCompleted() || "COMPLETED".equals(sixth.state().workflowSubState()))
                                .isTrue();
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> bookingCorrections() {
        List<String> corrections = List.of(
                "Actually another doctor",
                "No, Dr Akshu",
                "Make that Monday",
                "Tomorrow instead",
                "Afternoon instead",
                "Not that slot",
                "Second one instead",
                "Different clinic",
                "Actually I want General Medicine",
                "Sorry, I meant General Physician"
        );
        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            String language = index >= 8 ? "hi" : "en";
            cases.add(new CorpusCase(
                    "booking-correction-" + (index + 1),
                    "booking",
                    "BOOK_APPOINTMENT",
                    "CORRECTION",
                    CorpusTier.EXPLORATORY,
                    GapClassification.SUPPORTED_BUT_INCOMPLETE,
                    language,
                    harness -> {
                        harness.message("Book appointment with Dr Akshu tomorrow.", "en");
                        harness.message("General Medicine", "en");
                        harness.message("Dr Akshu", "en");
                        PatientPortalCareAiMessageResponse response = harness.message(corrections.get(index), language);
                        assertThat(response.assistantMessage()).isNotBlank();
                        assertThat(response.state().currentIntent()).isEqualTo("BOOK_APPOINTMENT");
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> callToBookPaths() {
        List<String> attempts = List.of(
                "Dr Arjun Mehta",
                "Book Dr Arjun Mehta",
                "Can I see Dr Arjun Mehta",
                "Dr Arjun",
                "Arjun Mehta",
                "meet Dr Arjun",
                "doctor arjun mehta",
                "view doctor profile",
                "call clinic",
                "book it anyway"
        );
        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            cases.add(new CorpusCase(
                    "call-to-book-" + (index + 1),
                    "discovery",
                    "FIND_DOCTOR",
                    "CALL_TO_BOOK",
                    CorpusTier.EXPLORATORY,
                    GapClassification.SUPPORTED_BUT_INCOMPLETE,
                    index >= 8 ? "hi" : "en",
                    harness -> {
                        PatientPortalCareAiMessageResponse response = harness.message(attempts.get(index), index >= 8 ? "hi" : "en");
                        assertThat(response.assistantMessage()).isNotBlank();
                        assertThat(response.state().confirmationPending()).isFalse();
                        verify(harness.patientPortalService, never()).bookAppointment(any());
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> cancelPaths() {
        List<String> requests = List.of(
                "cancel my appointment",
                "Cancel tomorrow's appointment",
                "I don't want to go tomorrow",
                "Cancel appointment with Dr Akshu",
                "Please cancel this booking",
                "cancel it",
                "cancel my next appointment",
                "रद्द कर दीजिए",
                "अपॉइंटमेंट रद्द करें",
                "not the appointment"
        );
        List<String> confirms = List.of(
                "yes",
                "confirm",
                "book it",
                "okay",
                "हाँ",
                "जी हाँ",
                "cancel",
                "go ahead",
                "yep",
                "please"
        );
        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            cases.add(new CorpusCase(
                    "cancel-" + (index + 1),
                    "cancel",
                    "CANCEL_APPOINTMENT",
                    "CANCEL",
                    CorpusTier.EXPLORATORY,
                    GapClassification.SUPPORTED_BUT_INCOMPLETE,
                    index >= 7 ? "hi" : "en",
                    harness -> {
                        PatientPortalCareAiMessageResponse first = harness.message(requests.get(index), index >= 7 ? "hi" : "en");
                        assertThat(first.state().currentIntent()).isEqualTo("CANCEL_APPOINTMENT");
                        if (index % 2 == 0) {
                            PatientPortalCareAiMessageResponse second = harness.message(confirms.get(index), index >= 7 ? "hi" : "en");
                            assertThat(second.state().actionCompleted() || second.state().booked() || "COMPLETED".equals(second.state().workflowSubState()))
                                    .isTrue();
                        }
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> reschedulePaths() {
        List<String> requests = List.of(
                "reschedule my appointment",
                "move my appointment",
                "change my appointment",
                "shift it to afternoon",
                "make it Monday",
                "tomorrow instead",
                "different clinic",
                "next Friday morning",
                "कल सुबह",
                "change the time"
        );
        List<String> followUps = List.of(
                "tomorrow morning",
                "next Monday",
                "afternoon",
                "10:30",
                "first slot",
                "second slot",
                "11 AM",
                "परसों",
                "yes",
                "confirm"
        );
        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            cases.add(new CorpusCase(
                    "reschedule-" + (index + 1),
                    "reschedule",
                    "RESCHEDULE_APPOINTMENT",
                    "RESCHEDULE",
                    CorpusTier.EXPLORATORY,
                    GapClassification.SUPPORTED_BUT_INCOMPLETE,
                    index >= 8 ? "hi" : "en",
                    harness -> {
                        PatientPortalCareAiMessageResponse first = harness.message(requests.get(index), index >= 8 ? "hi" : "en");
                        assertThat(first.state().currentIntent()).isEqualTo("RESCHEDULE_APPOINTMENT");
                        PatientPortalCareAiMessageResponse second = harness.message(followUps.get(index), index >= 8 ? "hi" : "en");
                        assertThat(second.assistantMessage()).isNotBlank();
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> readOnlyAndResetPaths() {
        List<String> requests = List.of(
                "Do I have an appointment?",
                "What's my next appointment?",
                "When is my doctor appointment?",
                "show upcoming appointments",
                "What can you do?",
                "Can you help me?",
                "start over",
                "reset conversation",
                "switch conversation",
                "hello"
        );
        List<CorpusCase> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            String intent = index < 4 ? "CHECK_APPOINTMENT" : "CONTROL";
            String entityType = index < 4 ? "READ_ONLY" : "CONTROL";
            cases.add(new CorpusCase(
                    "read-only-reset-" + (index + 1),
                    index < 4 ? "check" : "control",
                    intent,
                    entityType,
                    CorpusTier.EXPLORATORY,
                    index < 4 ? GapClassification.SUPPORTED_BUT_INCOMPLETE : GapClassification.FUTURE_ENHANCEMENT,
                    index >= 8 ? "hi" : "en",
                    harness -> {
                        PatientPortalCareAiMessageResponse response = harness.message(requests.get(index), index >= 8 ? "hi" : "en");
                        assertThat(response.assistantMessage()).isNotBlank();
                        if (index >= 6) {
                            assertThat(response.state().currentIntent()).isNull();
                        }
                    }
            ));
        }
        return cases;
    }

    private static List<CorpusCase> buildCases(String category,
                                               String language,
                                               String intent,
                                               String entityType,
                                               CorpusTier tier,
                                               GapClassification gapClassification,
                                               List<String> basePhrases,
                                               UtteranceAssertion assertion) {
        List<CorpusCase> cases = new ArrayList<>();
        for (String base : basePhrases) {
            for (String variant : variants(base)) {
                cases.add(new CorpusCase(
                        category + "-" + slug(base) + "-" + slug(variant),
                        category,
                        intent,
                        entityType,
                        tier,
                        gapClassification,
                        language,
                        harness -> assertion.assertCase(harness, variant, language)
                ));
            }
        }
        return cases;
    }

    private static List<String> variants(String base) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(base);
        variants.add(base + ".");
        variants.add(base.toLowerCase(Locale.ROOT));
        return new ArrayList<>(variants);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    private interface UtteranceAssertion {
        void assertCase(CorpusHarness harness, String phrase, String language);
    }

    private record CorpusCase(
            String id,
            String workflow,
            String intent,
            String entityType,
            CorpusTier tier,
            GapClassification gapClassification,
            String language,
            Consumer<CorpusHarness> runner
    ) {
    }

    private static final class CorpusReport {
        private final String name;
        private final List<String> certifiedFailures = new ArrayList<>();
        private final List<String> exploratoryFailures = new ArrayList<>();
        private final Map<String, Integer> byLanguage = new LinkedHashMap<>();
        private final Map<String, Integer> byWorkflow = new LinkedHashMap<>();
        private final Map<String, Integer> byIntent = new LinkedHashMap<>();
        private final Map<String, Integer> byEntityType = new LinkedHashMap<>();
        private final Map<GapClassification, Integer> byClassification = new LinkedHashMap<>();
        private int certifiedPassed;
        private int certifiedFailed;
        private int exploratoryPassed;
        private int exploratoryKnownGaps;
        private int passed;

        private CorpusReport(String name) {
            this.name = name;
        }

        void pass(CorpusCase corpusCase) {
            passed++;
            recordMetadata(corpusCase);
            if (corpusCase.tier() == CorpusTier.CERTIFIED) {
                certifiedPassed++;
            } else {
                exploratoryPassed++;
            }
        }

        void fail(CorpusCase corpusCase, Throwable throwable) {
            String message = throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
            recordMetadata(corpusCase);
            byClassification.merge(corpusCase.gapClassification(), 1, Integer::sum);
            String formatted = corpusCase.id() + " [" + corpusCase.workflow() + "/" + corpusCase.language() + "/" + corpusCase.intent() + "/" + corpusCase.entityType() + "]: " + message;
            if (corpusCase.tier() == CorpusTier.CERTIFIED) {
                certifiedFailed++;
                certifiedFailures.add(formatted);
            } else {
                exploratoryKnownGaps++;
                exploratoryFailures.add(corpusCase.gapClassification() + " :: " + formatted);
            }
        }

        void printSummary() {
            System.out.println("Stage E corpus (" + name + "): passed=" + passed + " certifiedPassed=" + certifiedPassed + " certifiedFailed=" + certifiedFailed + " exploratoryPassed=" + exploratoryPassed + " exploratoryKnownGaps=" + exploratoryKnownGaps);
            System.out.println("Stage E corpus (" + name + ") languages=" + byLanguage);
            System.out.println("Stage E corpus (" + name + ") workflows=" + byWorkflow);
            System.out.println("Stage E corpus (" + name + ") intents=" + byIntent);
            System.out.println("Stage E corpus (" + name + ") entityTypes=" + byEntityType);
            System.out.println("Stage E corpus (" + name + ") classifications=" + byClassification);
            if (!certifiedFailures.isEmpty()) {
                System.out.println("Stage E corpus (" + name + ") certified failures=");
                certifiedFailures.forEach(System.out::println);
            }
            if (!exploratoryFailures.isEmpty()) {
                System.out.println("Stage E corpus (" + name + ") exploratory gaps=");
                exploratoryFailures.forEach(System.out::println);
            }
        }

        private void recordMetadata(CorpusCase corpusCase) {
            byLanguage.merge(corpusCase.language(), 1, Integer::sum);
            byWorkflow.merge(corpusCase.workflow(), 1, Integer::sum);
            byIntent.merge(corpusCase.intent(), 1, Integer::sum);
            byEntityType.merge(corpusCase.entityType(), 1, Integer::sum);
        }

        void assertCertifiedClean() {
            if (!certifiedFailures.isEmpty()) {
                throw new AssertionError("Stage E " + name + " certified corpus failed: " + certifiedFailures);
            }
        }
    }

    private static final class CorpusHarness implements AutoCloseable {
        private final PatientPortalService patientPortalService = mock(PatientPortalService.class);
        private final PublicCatalogFacade publicCatalogFacade = mock(PublicCatalogFacade.class);
        private final DiscoverReferenceDataService discoverReferenceDataService = mock(DiscoverReferenceDataService.class);
        private final CareAiConversationPersistenceService conversationPersistenceService = mock(CareAiConversationPersistenceService.class);
        private final CareAiReceptionistTaskService receptionistTaskService = mock(CareAiReceptionistTaskService.class);
        private final CareAiTaskNotificationService taskNotificationService = mock(CareAiTaskNotificationService.class);
        private final ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        private final AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
        private final VoiceTestProperties voiceTestProperties = new VoiceTestProperties();
        private final Map<String, Map<LocalDate, List<PatientPortalDoctorSlotResponse>>> slotsByDoctor = new LinkedHashMap<>();
        private final Map<String, PatientPortalDoctorResponse> privateDoctorById = new LinkedHashMap<>();
        private final Map<String, PublicDoctorSummaryResponse> publicDoctorById = new LinkedHashMap<>();
        private final Map<String, PublicClinicSummaryResponse> publicClinicBySlug = new LinkedHashMap<>();
        private final List<PatientPortalCareAiAppointmentOption> appointments = new ArrayList<>();
        private final PatientPortalCareAiService service;

        private CorpusHarness() {
            RequestContextHolder.set(new RequestContext(new TenantId(TENANT_ID), APP_USER_ID, "subject-1", Set.of("PATIENT"), "PATIENT", "corpus-1"));
            voiceTestProperties.getLlm().setMaxOutputTokens(1024);
            when(clinicTimeZoneResolver.resolve(any())).thenReturn(CLINIC_ZONE);
            when(patientPortalService.currentPatientId()).thenReturn(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            when(patientPortalService.currentPatientMobile()).thenReturn("9999999999");
            setupCatalog();
            setupPrivateDoctors();
            setupSlots();
            setupAppointments();
            setupPatientPortalServiceStubs();
            this.service = new PatientPortalCareAiService(
                    patientPortalService,
                    clinicTimeZoneResolver,
                    new LlmBackedPatientPortalCareAiPlanner(aiOrchestrationService, new ObjectMapper(), voiceTestProperties, true),
                    conversationPersistenceService,
                    receptionistTaskService,
                    taskNotificationService,
                    publicCatalogFacade,
                    discoverReferenceDataService,
                    new PatientPortalAppointmentResolverService()
            );
        }

        PatientPortalCareAiMessageResponse message(String text, String language) {
            return service.message(new PatientPortalCareAiMessageRequest(text, language));
        }

        PatientPortalCareAiMessageResponse voiceMessage(String text, String language) {
            return service.messageFromVoice(new PatientPortalCareAiMessageRequest(text, language));
        }

        PatientPortalCareAiExtractedEntities extract(String text, String language) {
            PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();
            return new PatientPortalCareAiEntityExtractor(registry, new SpecialtyResolver(registry))
                    .extract(text, language);
        }

        PatientPortalCareAiAppointmentOption appointment(UUID appointmentId,
                                                         UUID doctorUserId,
                                                         String doctorName,
                                                         LocalDate date,
                                                         LocalTime time,
                                                         String status) {
            return new PatientPortalCareAiAppointmentOption(
                    appointmentId,
                    doctorUserId,
                    doctorName,
                    TENANT_ID,
                    "Sunrise Clinic",
                    date,
                    time,
                    status,
                    "Review visit"
            );
        }

        @Override
        public void close() {
            RequestContextHolder.clear();
        }

        private void setupCatalog() {
            when(discoverReferenceDataService.listServices()).thenReturn(List.of(
                    new DiscoverReferenceOptionRecord(UUID.fromString("44444444-4444-4444-4444-444444444444"), DiscoverReferenceCategory.SERVICE, "CONSULTATION", "Consultation", List.of(), 1, true),
                    new DiscoverReferenceOptionRecord(UUID.fromString("55555555-5555-5555-5555-555555555555"), DiscoverReferenceCategory.SERVICE, "HEALTH_CHECK", "Health Check", List.of(), 2, true),
                    new DiscoverReferenceOptionRecord(UUID.fromString("66666666-6666-6666-6666-666666666666"), DiscoverReferenceCategory.SERVICE, "TELECONSULTATION", "Teleconsultation", List.of(), 3, true)
            ));
            publicClinicBySlug.put("curapilot-demo-clinic", clinic("curapilot-demo-clinic", "CuraPilot Demo Clinic", "Baner", "Pune"));
            publicClinicBySlug.put("jeevanam-demo-clinic", clinic("jeevanam-demo-clinic", "Jeevanam Demo Clinic", "Baner", "Pune"));
            when(publicCatalogFacade.listClinics(
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(BigDecimal.class),
                    nullable(BigDecimal.class),
                    nullable(Integer.class),
                    eq(0),
                    eq(24)
            )).thenReturn(new PublicPageResponse<>(List.copyOf(publicClinicBySlug.values()), 0, 24, publicClinicBySlug.size(), 1));

            publicDoctorById.put("doctor-arjun", publicDoctor("doctor-arjun", "arjun-mehta", "Dr Arjun Mehta", "General Medicine", "Jeevanam Automation Lab", "curapilot-demo-clinic", "CALL_TO_BOOK", false));
            publicDoctorById.put("doctor-vikas", publicDoctor("doctor-vikas", "vikas-mehta", "Dr Vikas Mehta", "General Medicine", "Sunrise Clinic", "curapilot-demo-clinic", "ONLINE_BOOKING", true));
            when(publicCatalogFacade.listDoctors(
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(BigDecimal.class),
                    nullable(BigDecimal.class),
                    nullable(Integer.class),
                    eq(0),
                    eq(24)
            )).thenReturn(new PublicPageResponse<>(List.copyOf(publicDoctorById.values()), 0, 24, publicDoctorById.size(), 1));
        }

        private void setupPrivateDoctors() {
            privateDoctorById.put("doctor-akshu", PatientPortalCareAiTestSupport.doctor("doctor-akshu", "Dr Akshu Kumar", "General Medicine"));
            privateDoctorById.put("doctor-neha", PatientPortalCareAiTestSupport.doctor("doctor-neha", "Dr Neha Mehta", "General Medicine"));
            privateDoctorById.put("doctor-ashish", PatientPortalCareAiTestSupport.doctor("doctor-ashish", "Dr Ashish Shri", "General Medicine"));
            privateDoctorById.put("doctor-vikas", PatientPortalCareAiTestSupport.doctor("doctor-vikas", "Dr Vikas", "General Medicine"));
            when(patientPortalService.doctors()).thenReturn(List.copyOf(privateDoctorById.values()));
        }

        private void setupSlots() {
            slotsByDoctor.put("doctor-akshu", Map.of(
                    TOMORROW, List.of(slot(TOMORROW, LocalTime.of(9, 0), true), slot(TOMORROW, LocalTime.of(9, 30), true), slot(TOMORROW, LocalTime.of(10, 30), true)),
                    NEXT_MONDAY, List.of(slot(NEXT_MONDAY, LocalTime.of(11, 0), true), slot(NEXT_MONDAY, LocalTime.of(11, 30), true)),
                    NEXT_WEEK, List.of(slot(NEXT_WEEK, LocalTime.of(16, 0), true), slot(NEXT_WEEK, LocalTime.of(16, 30), true))
            ));
            Map<LocalDate, List<PatientPortalDoctorSlotResponse>> nehaSlots = new LinkedHashMap<>();
            nehaSlots.put(TOMORROW, List.of(slot(TOMORROW, LocalTime.of(10, 0), true), slot(TOMORROW, LocalTime.of(10, 30), true), slot(TOMORROW, LocalTime.of(11, 0), true)));
            nehaSlots.merge(NEXT_FRIDAY,
                    List.of(slot(NEXT_FRIDAY, LocalTime.of(14, 0), true), slot(NEXT_FRIDAY, LocalTime.of(15, 0), true)),
                    (current, additional) -> java.util.stream.Stream.concat(current.stream(), additional.stream()).toList());
            slotsByDoctor.put("doctor-neha", Map.copyOf(nehaSlots));
            slotsByDoctor.put("doctor-ashish", Map.of(
                    TOMORROW, List.of(slot(TOMORROW, LocalTime.of(12, 0), true), slot(TOMORROW, LocalTime.of(12, 30), true), slot(TOMORROW, LocalTime.of(13, 0), true)),
                    NEXT_SATURDAY, List.of(slot(NEXT_SATURDAY, LocalTime.of(9, 0), true), slot(NEXT_SATURDAY, LocalTime.of(10, 0), true))
            ));
            slotsByDoctor.put("doctor-vikas", Map.of(
                    TOMORROW, List.of(slot(TOMORROW, LocalTime.of(10, 0), true), slot(TOMORROW, LocalTime.of(10, 30), true)),
                    NEXT_WEEK, List.of(slot(NEXT_WEEK, LocalTime.of(11, 0), true), slot(NEXT_WEEK, LocalTime.of(11, 30), true))
            ));
        }

        private void setupAppointments() {
            appointments.add(new PatientPortalCareAiAppointmentOption(
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    UUID.fromString("88888888-8888-8888-8888-888888888888"),
                    "Dr Akshu Kumar",
                    TENANT_ID,
                    "Jeevanam Automation Lab",
                    TOMORROW,
                    LocalTime.of(10, 30),
                    "BOOKED",
                    "Review visit"
            ));
            appointments.add(new PatientPortalCareAiAppointmentOption(
                    UUID.fromString("99999999-9999-9999-9999-999999999999"),
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    "Dr Neha Mehta",
                    TENANT_ID,
                    "CuraPilot Demo Clinic",
                    NEXT_FRIDAY,
                    LocalTime.of(14, 0),
                    "BOOKED",
                    "Follow-up"
            ));
            when(patientPortalService.careAiUpcomingAppointments()).thenReturn(List.copyOf(appointments));
        }

        private void setupPatientPortalServiceStubs() {
            when(patientPortalService.doctorSlots(anyString(), any(LocalDate.class))).thenAnswer(invocation -> slots(invocation.getArgument(0), invocation.getArgument(1)));
            when(patientPortalService.doctorSlots(anyString(), anyString(), any(LocalDate.class))).thenAnswer(invocation -> slots(invocation.getArgument(0), invocation.getArgument(2)));
            when(patientPortalService.doctorSlots(anyString(), anyString(), anyString(), anyString(), any(LocalDate.class))).thenAnswer(invocation -> slots(invocation.getArgument(0), invocation.getArgument(4)));
            when(patientPortalService.doctorSlots(anyString(), anyString(), anyString(), anyString(), anyString(), any(LocalDate.class))).thenAnswer(invocation -> slots(invocation.getArgument(1), invocation.getArgument(5)));
            when(patientPortalService.bookAppointment(any())).thenAnswer(invocation -> {
                com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest request = invocation.getArgument(0);
                return new PatientPortalAppointmentConfirmationResponse(
                        request.appointmentDate(),
                        request.appointmentTime(),
                        doctorName(request.publicDoctorId()),
                        clinicName(request.clinicSlug()),
                        "patient-portal",
                        "BOOKED",
                        request.reason(),
                        "Appointment booked successfully."
                );
            });
            when(patientPortalService.cancelAppointment(any(), anyString(), nullable(String.class))).thenAnswer(invocation -> new PatientPortalAppointmentConfirmationResponse(
                    TOMORROW,
                    LocalTime.of(10, 30),
                    "Dr Akshu Kumar",
                    "Jeevanam Automation Lab",
                    "patient-portal",
                    "CANCELLED",
                    "cancelled",
                    "Appointment cancelled successfully."
            ));
            when(patientPortalService.rescheduleAppointment(any(), any(LocalDate.class), any(LocalTime.class), anyString())).thenAnswer(invocation -> new PatientPortalAppointmentConfirmationResponse(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    "Dr Neha Mehta",
                    "CuraPilot Demo Clinic",
                    "patient-portal",
                    "RESCHEDULED",
                    invocation.getArgument(3),
                    "Appointment rescheduled successfully."
            ));
        }

        private List<PatientPortalDoctorSlotResponse> slots(String doctorId, LocalDate date) {
            return slotsByDoctor.getOrDefault(doctorId, Map.of()).getOrDefault(date, List.of());
        }

        private String doctorName(String publicDoctorId) {
            if ("doctor-arjun".equals(publicDoctorId)) {
                return "Dr Arjun Mehta";
            }
            if ("doctor-vikas".equals(publicDoctorId)) {
                return "Dr Vikas Mehta";
            }
            PatientPortalDoctorResponse doctor = privateDoctorById.get(publicDoctorId);
            return doctor == null ? "Unknown Doctor" : doctor.doctorName();
        }

        private String clinicName(String clinicSlug) {
            if (clinicSlug == null) {
                return "Jeevanam Automation Lab";
            }
            PublicClinicSummaryResponse clinic = publicClinicBySlug.get(clinicSlug);
            return clinic == null ? "Jeevanam Automation Lab" : clinic.clinicDisplayName();
        }

        private PatientPortalDoctorSlotResponse slot(LocalDate date, LocalTime time, boolean available) {
            return new PatientPortalDoctorSlotResponse(date, time, time.plusMinutes(30), available ? "AVAILABLE" : "UNAVAILABLE", available);
        }

        private PublicClinicSummaryResponse clinic(String slug, String name, String area, String city) {
            return new PublicClinicSummaryResponse(
                    slug,
                    "/api/public/clinics/" + slug,
                    name,
                    null,
                    null,
                    null,
                    "Demo clinic address",
                    area,
                    city,
                    "ONLINE_BOOKING",
                    8,
                    2,
                    1,
                    0,
                    true,
                    List.of("General Medicine"),
                    "Subtitle",
                    "Summary",
                    true,
                    BigDecimal.ZERO
            );
        }

        private PublicDoctorSummaryResponse publicDoctor(String publicDoctorId,
                                                         String slug,
                                                         String displayName,
                                                         String speciality,
                                                         String clinicDisplayName,
                                                         String clinicSlug,
                                                         String bookingMode,
                                                         boolean canBookOnline) {
            return new PublicDoctorSummaryResponse(
                    publicDoctorId,
                    slug,
                    "/api/public/doctors/" + slug,
                    displayName,
                    null,
                    null,
                    speciality,
                    8,
                    BigDecimal.valueOf(700),
                    List.of("English"),
                    "Baner",
                    "Pune",
                    bookingMode,
                    "Subtitle",
                    "Summary",
                    clinicDisplayName,
                    clinicSlug,
                    true,
                    "10:30 AM",
                    BigDecimal.ZERO,
                    null,
                    canBookOnline
            );
        }
    }
}
