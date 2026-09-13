package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilityResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AvailabilitySlot;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingDraft;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingPatch;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.BookingReceipt;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConfirmationPolarity;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ConversationDecision;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DialogAct;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.DraftStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Operation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCandidate;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderCapabilities;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ProviderSource;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleConfirmation;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleResolution;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.RescheduleStatus;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.Selection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ToolResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.TopicAction;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.ValuePatch;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AivaV2TransactionalKernelTest {
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private AivaV2BookingTools tools;
    private AivaV2TransactionalKernel kernel;

    @BeforeEach
    void setUp() {
        tools = mock(AivaV2BookingTools.class);
        kernel = new AivaV2TransactionalKernel(tools, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void positiveConfirmationCallsOnlyConfirmBooking() {
        SessionProjection session = readySession();
        when(tools.confirmBooking(any(), any())).thenReturn(ToolResult.success(
                new BookingReceipt("APT-1", "BOOKED", null)));

        var result = kernel.handle(session, decision(Operation.CONFIRM_BOOKING,
                ConfirmationPolarity.POSITIVE, BookingPatch.empty(), null), "turn-1");

        assertThat(result.response().responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(result.session().activeDraft().confirmedAppointmentReference()).isEqualTo("APT-1");
        verify(tools).confirmBooking(any(), any());
        verify(tools, never()).resolveBookingProvider(any(), any());
        verify(tools, never()).getBookingAvailability(any());
    }

    @Test
    void duplicatePositiveConfirmationDoesNotExecuteSecondWrite() {
        BookingDraft confirmed = copy(readySession().activeDraft(), DraftStatus.CONFIRMED, "APT-1");
        SessionProjection session = replace(readySession(), confirmed, null, null);

        var result = kernel.handle(session, decision(Operation.CONFIRM_BOOKING,
                ConfirmationPolarity.POSITIVE, BookingPatch.empty(), null), "turn-2");

        assertThat(result.response().responseCategory()).isEqualTo("BOOKING_CONFIRMED");
        verify(tools, never()).confirmBooking(any(), any());
    }

    @Test
    void appointmentLookupDoesNotMutateBookingState() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        SessionProjection session = readySession();
        when(lookupTool.lookup(any(), any(), any(), any())).thenReturn(
                AivaV2Models.AppointmentLookupResult.found(List.of(
                        new AivaV2Models.AppointmentSummary("APT-1", "Doc Akshu Kumar", "Clinic",
                                LocalDate.of(2026, 9, 14), LocalTime.of(17, 30), "CONFIRMED", null))));
        AivaV2TransactionalKernel lookupKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.ASK_QUESTION,
                Operation.LOOKUP_APPOINTMENTS, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1d, "TEST", false, 1,
                new AivaV2Models.AppointmentLookupFilter(null, null, null, true));

        var result = lookupKernel.handle(session, decision,
                AivaV2TemporalResolution.unchanged(null, null), ZoneOffset.UTC, "turn-lookup");

        assertThat(result.response().responseCategory()).isEqualTo("APPOINTMENTS_FOUND");
        assertThat(result.session().activeDraft()).isSameAs(session.activeDraft());
        assertThat(result.session().pendingConfirmation()).isSameAs(session.pendingConfirmation());
        verify(lookupTool).lookup(any(), any(), any(), any());
        verify(tools, never()).getBookingAvailability(any());
        verify(tools, never()).resolveBookingProvider(any(), any());
    }

    @Test
    void appointmentLookupWithDoctorNoMatchReturnsContextualNone() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        SessionProjection session = readySession();
        when(lookupTool.lookup(any(), any(), any(), any()))
                .thenReturn(AivaV2Models.AppointmentLookupResult.found(List.of()));
        AivaV2TransactionalKernel lookupKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.ASK_QUESTION,
                Operation.LOOKUP_APPOINTMENTS, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1d, "TEST", false, 1,
                new AivaV2Models.AppointmentLookupFilter(ValuePatch.set("Dr SomeoneElse"), null, null, null, false));

        var result = lookupKernel.handle(session, decision,
                AivaV2TemporalResolution.unchanged(null, null), ZoneOffset.UTC, "turn-no-match");

        assertThat(result.response().responseCategory()).isEqualTo("APPOINTMENTS_NONE");
        assertThat(result.response().assistantMessage())
                .isEqualTo("You do not have any upcoming appointments with Dr SomeoneElse.");
        assertThat(result.session().activeDraft()).isSameAs(session.activeDraft());
        verify(tools, never()).getBookingAvailability(any());
        verify(tools, never()).resolveBookingProvider(any(), any());
    }

    @Test
    void unsupportedExplicitLookupQualifierClarifiesWithoutCallingLookupTool() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2TransactionalKernel lookupKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.ASK_QUESTION,
                Operation.LOOKUP_APPOINTMENTS, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1d, "TEST", false, 1,
                AivaV2Models.AppointmentLookupFilter.empty(), true);

        var result = lookupKernel.handle(new SessionProjection("c1", UUID.randomUUID(), "tenant", null,
                        null, null, null, null, 1, NOW.plusSeconds(1800)), decision,
                AivaV2TemporalResolution.unchanged(null, null), ZoneOffset.UTC, "turn-qualifier");

        assertThat(result.response().responseCategory()).isEqualTo("LOOKUP_CLARIFICATION");
        assertThat(result.response().assistantMessage())
                .isEqualTo("Are you asking about an appointment with a specific doctor or specialty?");
        verifyNoInteractions(lookupTool);
    }

    @Test
    void cancellationPassesNormalizedDateAlongsideDecisionFilter() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2CancellationTool cancellationTool = mock(AivaV2CancellationTool.class);
        AivaV2TransactionalKernel cancellationKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                cancellationTool, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate date = LocalDate.of(2026, 9, 15);
        var filter = new AivaV2Models.AppointmentLookupFilter(ValuePatch.set("Dr Akshu"),
                ValuePatch.set(date.toString()), ValuePatch.unchanged(), null, false);
        var appointment = new AivaV2Models.AppointmentSummary("00000000-0000-0000-0000-000000000001",
                "Doc Akshu Kumar", "Clinic", date, LocalTime.of(18, 0), "CONFIRMED", null);
        when(lookupTool.lookup(any(), any(), any(), any()))
                .thenReturn(AivaV2Models.AppointmentLookupResult.found(List.of(appointment)));
        when(cancellationTool.prepare(any(), any())).thenReturn(ToolResult.success(new AivaV2Models.CancellationConfirmation(
                "confirm-cancel", UUID.fromString(appointment.appointmentReference()), appointment.appointmentReference(),
                appointment.doctorDisplayName(), appointment.clinicDisplayName(), appointment.date(), appointment.time(),
                NOW.plusSeconds(300), "idem-cancel")));

        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.START_REQUEST,
                Operation.CANCEL_APPOINTMENT, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1, "TEST", false, 1, filter);
        var temporal = new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, date,
                AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, "15 September 2026", date.toString(), false);

        var result = cancellationKernel.handle(new SessionProjection("c1", UUID.randomUUID(), "tenant", null,
                null, null, null, null, 1, NOW.plusSeconds(1800)), decision, temporal, ZoneOffset.UTC, "turn-cancel");

        assertThat(result.response().responseCategory()).isEqualTo("CANCELLATION_CONFIRMATION");
        var filterCaptor = org.mockito.ArgumentCaptor.forClass(AivaV2Models.AppointmentLookupFilter.class);
        var dateCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(lookupTool).lookup(filterCaptor.capture(), dateCaptor.capture(), eq("c1"), eq("turn-cancel"));
        assertThat(filterCaptor.getValue().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(filterCaptor.getValue().dateExpression().value()).isEqualTo("2026-09-15");
        assertThat(dateCaptor.getValue()).isEqualTo(date);
    }

    @Test
    void negativeCancellationConfirmationClearsCapabilityWithoutRelookingUp() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2CancellationTool cancellationTool = mock(AivaV2CancellationTool.class);
        AivaV2TransactionalKernel cancellationKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                cancellationTool, Clock.fixed(NOW, ZoneOffset.UTC));
        AivaV2Models.CancellationConfirmation confirmation = new AivaV2Models.CancellationConfirmation(
                "confirm-cancel", UUID.randomUUID(), "appointment-1", "Doc Akshu Kumar", "Clinic",
                LocalDate.of(2026, 9, 24), LocalTime.of(20, 0), NOW.plusSeconds(300), "cancel-1");
        SessionProjection session = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null,
                null, null, null, confirmation, 1, NOW.plusSeconds(1800));

        var result = cancellationKernel.handle(session, decision(Operation.CANCEL_APPOINTMENT,
                ConfirmationPolarity.NEGATIVE, BookingPatch.empty(), null), "turn-cancel-no");

        assertThat(result.response().responseCategory()).isEqualTo("CANCEL_CONFIRMATION_REJECTED");
        assertThat(result.response().assistantMessage()).isEqualTo("I’ll leave that appointment unchanged.");
        assertThat(result.session().pendingCancellation()).isNull();
        verifyNoInteractions(lookupTool, cancellationTool);
    }

    @Test
    void cancellationAmbiguityStoresResolutionWithoutReplacingBookingDraft() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2CancellationTool cancellationTool = mock(AivaV2CancellationTool.class);
        AivaV2TransactionalKernel cancellationKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                cancellationTool, Clock.fixed(NOW, ZoneOffset.UTC));
        var first = new AivaV2Models.AppointmentSummary("00000000-0000-0000-0000-000000000001",
                "Doc Akshu Kumar", "Clinic", LocalDate.of(2026, 9, 14), LocalTime.of(17, 30), "CONFIRMED", null);
        var second = new AivaV2Models.AppointmentSummary("00000000-0000-0000-0000-000000000002",
                "Doc Akshu Kumar", "Clinic", LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), "CONFIRMED", null);
        when(lookupTool.lookup(any(), any(), any(), any()))
                .thenReturn(AivaV2Models.AppointmentLookupResult.found(List.of(first, second)));
        var draftSession = readySession();
        var filter = new AivaV2Models.AppointmentLookupFilter(ValuePatch.set("Dr Akshu"),
                ValuePatch.unchanged(), ValuePatch.unchanged(), null, false);

        var result = cancellationKernel.handle(draftSession, new ConversationDecision("1.0", DialogAct.START_REQUEST,
                Operation.CANCEL_APPOINTMENT, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1, "TEST", false, 1, filter),
                AivaV2TemporalResolution.unchanged(null, null), ZoneOffset.UTC, "turn-ambiguous");

        assertThat(result.response().responseCategory()).isEqualTo("CANCELLATION_CHOICES");
        assertThat(result.session().activeDraft()).isSameAs(draftSession.activeDraft());
        assertThat(result.session().pendingCancellationResolution()).isNotNull();
        assertThat(result.session().pendingCancellationResolution().criteria().doctorText().value()).isEqualTo("Dr Akshu");
        assertThat(result.session().pendingCancellationResolution().candidates()).hasSize(2);
    }

    @Test
    void rescheduleBindsTheAuthorizedSourceAppointmentAndOwningClinic() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2CancellationTool cancellationTool = mock(AivaV2CancellationTool.class);
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools, lookupTool,
                cancellationTool, rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate sourceDate = LocalDate.of(2026, 9, 14);
        var source = new AivaV2Models.AppointmentSummary("00000000-0000-0000-0000-000000000010",
                "Doc Akshu Kumar", "NewAuto Lab", sourceDate, LocalTime.of(17, 30), "BOOKED", null,
                "doctor-akshu", "clinic-newauto", "tenant-newauto", "newauto-lab");
        when(lookupTool.lookup(any(), any(), any(), any()))
                .thenReturn(AivaV2Models.AppointmentLookupResult.found(List.of(source)));
        when(rescheduleTools.providerFor(source)).thenReturn(new AivaV2Models.ProviderCandidate(
                "reschedule-doctor-akshu", "doctor-akshu", "doctor-akshu", "clinic-newauto", "tenant-newauto",
                "newauto-lab", null, "Doc Akshu Kumar", "General Medicine", "NewAuto Lab", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false)));
        var decision = new ConversationDecision("1.0", DialogAct.START_REQUEST, Operation.RESCHEDULE_APPOINTMENT,
                BookingPatch.empty(), null, ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en", 1, "TEST", false, 1,
                new AivaV2Models.AppointmentLookupFilter(ValuePatch.unchanged(), ValuePatch.set(sourceDate.toString()),
                        ValuePatch.unchanged(), ValuePatch.unchanged(), false));

        var result = rescheduleKernel.handle(new SessionProjection("c1", UUID.randomUUID(), "current-tenant", null,
                null, null, null, null, null, 1, NOW.plusSeconds(1800)), decision,
                new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, sourceDate,
                        AivaV2TemporalResolution.Source.MODEL_CANONICAL, null, sourceDate.toString(), false), ZoneOffset.UTC, "turn-reschedule-source");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SOURCE_RESOLVED");
        assertThat(result.session().pendingReschedule().sourceAppointmentReference()).isEqualTo(source.appointmentReference());
        assertThat(result.session().pendingReschedule().tenantId()).isEqualTo("tenant-newauto");
        assertThat(result.session().tenantId()).isEqualTo("current-tenant");
    }

    @Test
    void rescheduleTargetDateUsesBoundProviderAvailability() {
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools, mock(AivaV2AppointmentLookupTool.class), null,
                rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate target = LocalDate.of(2026, 9, 16);
        RescheduleResolution state = new RescheduleResolution("00000000-0000-0000-0000-000000000010",
                "Doc Akshu Kumar", "doctor-akshu", "doctor-akshu", "clinic-newauto", "tenant-newauto", "newauto-lab",
                LocalDate.of(2026, 9, 14), LocalTime.of(17, 30), null, null, null, null, null, null,
                RescheduleStatus.TARGET_COLLECTING, 1, NOW, NOW.plusSeconds(300));
        SessionProjection session = new SessionProjection("c1", UUID.randomUUID(), "current-tenant",
                null, null, null, null, null, null, null, state, null, 1, NOW.plusSeconds(1800));
        var slots = List.of(new AivaV2Models.AvailabilitySlot("slot-1830", LocalTime.of(18, 30), LocalTime.of(19, 0), "18:30"));
        var availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 2, "fp", "doctor-akshu",
                "doctor-akshu", "clinic-newauto", target, null, "Asia/Kolkata", slots, null, false, NOW, NOW.plusSeconds(300));
        when(rescheduleTools.availability(any())).thenReturn(ToolResult.success(availability));
        var decision = new ConversationDecision("1.0", DialogAct.PROVIDE_INFORMATION, Operation.UPDATE_RESCHEDULE,
                new BookingPatch(null, null, ValuePatch.set(target.toString()), null, null), null,
                ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en", 1, "TEST", false, 1);

        var result = rescheduleKernel.handle(session, decision,
                new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, target,
                        AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, "16 September", target.toString(), false),
                ZoneOffset.UTC, "turn-reschedule-target");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SLOT_CHOICES");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(target);
        assertThat(result.session().pendingReschedule().tenantId()).isEqualTo("tenant-newauto");
        verify(rescheduleTools).availability(any());
    }

    @Test
    void genericAvailabilityRefinementRemainsOwnedByActiveReschedule() {
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools,
                mock(AivaV2AppointmentLookupTool.class), null, rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate target = LocalDate.of(2026, 9, 16);
        RescheduleResolution state = new RescheduleResolution("appointment", "Doc Akshu Kumar", "doctor-akshu",
                "doctor-akshu", "clinic-newauto", "tenant-newauto", "newauto-lab", LocalDate.of(2026, 9, 14),
                LocalTime.of(17, 30), target, null, null, null, null, null, RescheduleStatus.TARGET_COLLECTING,
                1, NOW, NOW.plusSeconds(300));
        SessionProjection session = new SessionProjection("c1", UUID.randomUUID(), "current-tenant",
                null, null, null, null, null, null, null, state, null, 1, NOW.plusSeconds(1800));
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 2, "fp",
                "doctor-akshu", "doctor-akshu", "clinic-newauto", target, "evening", null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("evening-slot", LocalTime.of(18, 30), LocalTime.of(19, 0), "18:30")),
                null, false, NOW, NOW.plusSeconds(300), 0, 3);
        when(rescheduleTools.availability(any())).thenReturn(ToolResult.success(availability));
        BookingPatch patch = new BookingPatch(null, null, null, ValuePatch.set("evening"), null);
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.PROVIDE_INFORMATION,
                Operation.GET_AVAILABILITY, patch, null, ConfirmationPolarity.NONE, TopicAction.CONTINUE,
                "en", 1, "TEST", false, 1);

        var result = rescheduleKernel.handle(session, decision, "turn-reschedule-evening");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SLOT_CHOICES");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(target);
        assertThat(result.session().activeDraft()).isNull();
        verify(rescheduleTools).availability(any());
    }

    @Test
    void boundRescheduleTargetDateChangeInvalidatesOldAvailabilityAndDoesNotRelookupSource() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools, lookupTool, null,
                rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate oldTarget = LocalDate.of(2026, 9, 16);
        LocalDate newTarget = LocalDate.of(2026, 9, 17);
        AvailabilityResult oldAvailability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 2,
                "old", "doctor-akshu", "doctor-akshu", "clinic", oldTarget, null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("old-slot", LocalTime.of(10, 0), LocalTime.of(10, 30), "10:00")),
                null, false, NOW, NOW.plusSeconds(300));
        AvailabilityResult freshAvailability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 3,
                "new", "doctor-akshu", "doctor-akshu", "clinic", newTarget, null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("new-slot", LocalTime.of(18, 0), LocalTime.of(18, 30), "18:00")),
                null, false, NOW, NOW.plusSeconds(300));
        RescheduleResolution state = new RescheduleResolution("appointment", "Doc Akshu Kumar", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 14), LocalTime.of(17, 30),
                oldTarget, null, oldAvailability.requestId(), oldAvailability, "old-slot", LocalTime.of(10, 0),
                RescheduleStatus.AVAILABILITY_READY, 2, NOW, NOW.plusSeconds(300));
        SessionProjection session = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null,
                null, null, null, null, state, null, 2, NOW.plusSeconds(1800));
        when(rescheduleTools.availability(any())).thenReturn(ToolResult.success(freshAvailability));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.CHANGE_INFORMATION,
                Operation.RESCHEDULE_APPOINTMENT,
                new BookingPatch(null, null, ValuePatch.set(newTarget.toString()), null, null), null,
                ConfirmationPolarity.NONE, TopicAction.CONTINUE, "en", 1, "TEST", false, 1);

        var result = rescheduleKernel.handle(session, decision,
                new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, newTarget,
                        AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, "17 September", newTarget.toString(), false),
                ZoneOffset.UTC, "turn-reschedule-date-change");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SLOT_CHOICES");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(newTarget);
        assertThat(result.session().pendingReschedule().latestAvailability()).isEqualTo(freshAvailability);
        assertThat(result.session().pendingReschedule().selectedSlotReference()).isNull();
        verify(rescheduleTools).availability(any());
        verifyNoInteractions(lookupTool);
    }

    @Test
    void boundRescheduleTemporalTargetDateChangeNormalizesBeforeCleanup() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools, lookupTool, null,
                rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate target = LocalDate.of(2026, 9, 17);
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 1,
                "fresh", "doctor-akshu", "doctor-akshu", "clinic", target, null, "Asia/Kolkata",
                List.of(new AvailabilitySlot("fresh-slot", LocalTime.of(18, 0), LocalTime.of(18, 30), "18:00")),
                null, false, NOW, NOW.plusSeconds(300));
        RescheduleResolution state = new RescheduleResolution("appointment", "Doc Akshu Kumar", "doctor-akshu",
                "doctor-akshu", "clinic", "tenant", "clinic", LocalDate.of(2026, 9, 16), LocalTime.of(20, 0),
                null, null, null, null, null, null, RescheduleStatus.TARGET_COLLECTING, 1, NOW, NOW.plusSeconds(300));
        SessionProjection session = new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null,
                null, null, null, null, state, null, 1, NOW.plusSeconds(1800));
        when(rescheduleTools.availability(any())).thenReturn(ToolResult.success(availability));
        ConversationDecision decision = new ConversationDecision("1.0", DialogAct.CHANGE_INFORMATION,
                Operation.RESCHEDULE_APPOINTMENT, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.CONTINUE, "en", 1, "TEST", false, 1);

        var result = rescheduleKernel.handle(session, decision,
                new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, target,
                        AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, "17 September", target.toString(), false),
                ZoneOffset.UTC, "turn-reschedule-temporal-date");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SLOT_CHOICES");
        assertThat(result.session().pendingReschedule().sourceAppointmentReference()).isEqualTo("appointment");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(target);
        verify(rescheduleTools).availability(any());
        verifyNoInteractions(lookupTool);
    }

    @Test
    void negativeRescheduleConfirmationRejectsSlotButPreservesWorkflow() {
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools,
                mock(AivaV2AppointmentLookupTool.class), null, rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        SessionProjection session = rescheduleConfirmationSession();

        var result = rescheduleKernel.handle(session, decision(Operation.CONFIRM_RESCHEDULE,
                ConfirmationPolarity.NEGATIVE, BookingPatch.empty(), null), "turn-reschedule-no");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_CONFIRMATION_REJECTED");
        assertThat(result.session().pendingReschedule()).isNotNull();
        assertThat(result.session().pendingReschedule().sourceAppointmentReference()).isEqualTo("appointment");
        assertThat(result.session().pendingReschedule().providerHandle()).isEqualTo("doctor-akshu");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(result.session().pendingReschedule().selectedSlotReference()).isNull();
        assertThat(result.session().pendingReschedule().selectedStartsAt()).isNull();
        assertThat(result.session().pendingReschedule().status()).isEqualTo(RescheduleStatus.AVAILABILITY_READY);
        assertThat(result.session().pendingRescheduleConfirmation()).isNull();
        verifyNoInteractions(rescheduleTools);
    }

    @Test
    void targetDateAfterNegativeRescheduleConfirmationRefinesBoundSourceWithoutLookup() {
        AivaV2AppointmentLookupTool lookupTool = mock(AivaV2AppointmentLookupTool.class);
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools, lookupTool, null,
                rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        LocalDate newTarget = LocalDate.of(2026, 9, 18);
        AvailabilityResult fresh = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 4, "fresh",
                "doctor-akshu", "doctor-akshu", "clinic", newTarget, null, "Asia/Kolkata",
                List.of(slot("fresh-slot", "18:30")), null, false, NOW, NOW.plusSeconds(300));
        when(rescheduleTools.availability(any())).thenReturn(ToolResult.success(fresh));
        SessionProjection rejected = rescheduleKernel.handle(rescheduleConfirmationSession(), decision(
                Operation.CONFIRM_RESCHEDULE, ConfirmationPolarity.NEGATIVE, BookingPatch.empty(), null),
                "turn-reschedule-no").session();

        var result = rescheduleKernel.handle(rejected, decision(Operation.UPDATE_RESCHEDULE,
                        ConfirmationPolarity.NONE,
                        new BookingPatch(null, null, ValuePatch.set(newTarget.toString()), null, null), null),
                new AivaV2TemporalResolution(AivaV2TemporalResolution.Status.RESOLVED, newTarget,
                        AivaV2TemporalResolution.Source.CURRENT_TURN_EXPLICIT, "18 September", newTarget.toString(), false),
                ZoneOffset.UTC, "turn-reschedule-after-no");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_SLOT_CHOICES");
        assertThat(result.session().pendingReschedule().sourceAppointmentReference()).isEqualTo("appointment");
        assertThat(result.session().pendingReschedule().targetDate()).isEqualTo(newTarget);
        verify(rescheduleTools).availability(any());
        verifyNoInteractions(lookupTool);
    }

    @Test
    void explicitRescheduleAbandonmentClearsRescheduleWorkflow() {
        AivaV2RescheduleTools rescheduleTools = mock(AivaV2RescheduleTools.class);
        AivaV2TransactionalKernel rescheduleKernel = new AivaV2TransactionalKernel(tools,
                mock(AivaV2AppointmentLookupTool.class), null, rescheduleTools, Clock.fixed(NOW, ZoneOffset.UTC));
        ConversationDecision abandon = new ConversationDecision("1.0", DialogAct.ABANDON,
                Operation.ANSWER_CONTEXT, BookingPatch.empty(), null, ConfirmationPolarity.NONE,
                TopicAction.ABANDON, "en", 1, "TEST", false, 1);

        var result = rescheduleKernel.handle(rescheduleConfirmationSession(), abandon, "turn-reschedule-stop");

        assertThat(result.response().responseCategory()).isEqualTo("RESCHEDULE_ABANDONED");
        assertThat(result.session().pendingReschedule()).isNull();
        assertThat(result.session().pendingRescheduleConfirmation()).isNull();
    }

    @Test
    void dateCorrectionRetainsProviderAndInvalidatesOldSlot() {
        SessionProjection session = readySession();
        BookingPatch patch = new BookingPatch(null, null, ValuePatch.set("2026-09-11"), null, null);
        when(tools.getBookingAvailability(any())).thenAnswer(invocation -> {
            BookingDraft draft = invocation.getArgument(0);
            AvailabilityResult availability = availability(draft, "new-slot", LocalTime.of(10, 0));
            when(tools.criteriaFingerprint(draft)).thenReturn(availability.criteriaFingerprint());
            return ToolResult.success(availability);
        });

        var result = kernel.handle(session, decision(Operation.UPDATE_BOOKING,
                ConfirmationPolarity.NEGATIVE, patch, null), "turn-3");

        assertThat(result.session().activeDraft().selectedProvider().displayName()).isEqualTo("Doc Akshu Kumar");
        assertThat(result.session().activeDraft().preferredDate()).isEqualTo(LocalDate.parse("2026-09-11"));
        assertThat(result.session().activeDraft().selectedSlotReference()).isNull();
        assertThat(result.session().pendingConfirmation()).isNull();
        verify(tools, never()).resolveBookingProvider(any(), any());
    }

    @Test
    void successfulEmptyAvailabilityReturnsProviderAndDateSpecificNoAvailability() {
        ProviderCandidate provider = provider();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), UUID.randomUUID(), "tenant", provider, null,
                LocalDate.parse("2026-09-12"), null, null, null, null, DraftStatus.COLLECTING,
                1, NOW.plusSeconds(1800), null);
        SessionProjection session = new SessionProjection("c1", draft.patientSubjectId(), "tenant", draft,
                null, null, null, null, 1, NOW.plusSeconds(1800));
        when(tools.getBookingAvailability(any())).thenReturn(ToolResult.success(
                new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(), "criteria",
                        provider.providerHandle(), provider.doctorId(), provider.clinicId(), draft.preferredDate(),
                        null, "Asia/Kolkata", List.of(), null, false, NOW, NOW.plusSeconds(300))));

        var result = kernel.handle(session, decision(Operation.GET_AVAILABILITY,
                ConfirmationPolarity.NONE, BookingPatch.empty(), null), "turn-no-availability");

        assertThat(result.response().responseCategory()).isEqualTo("NO_AVAILABILITY");
        assertThat(result.response().assistantMessage()).isEqualTo(
                "Doc Akshu Kumar has no available slots on 12 September 2026. Would you like me to check another date?");
        assertThat(result.session().activeDraft().selectedProvider().displayName()).isEqualTo("Doc Akshu Kumar");
        assertThat(result.session().activeDraft().preferredDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(result.session().latestAvailabilityResult().slots()).isEmpty();
        verify(tools).getBookingAvailability(any());
    }

    @Test
    void pastDateIsRejectedBeforeAvailabilityAndExistingDateIsPreserved() {
        SessionProjection session = readySession();

        var result = kernel.handle(session, decision(Operation.UPDATE_BOOKING,
                ConfirmationPolarity.NEGATIVE,
                new BookingPatch(null, null, ValuePatch.set("2026-09-09"), null, null), null), "turn-past-date");

        assertThat(result.response().responseCategory()).isEqualTo("PAST_DATE");
        assertThat(result.response().assistantMessage())
                .isEqualTo("9 September 2026 is in the past. Please choose today or a future date.");
        assertThat(result.session().activeDraft().selectedProvider().displayName()).isEqualTo("Doc Akshu Kumar");
        assertThat(result.session().activeDraft().preferredDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        verify(tools, never()).getBookingAvailability(any());
    }

    @Test
    void yesterdayIsRejectedAndSameDayDateRemainsAllowed() {
        SessionProjection session = readySession();

        var yesterday = kernel.handle(session, decision(Operation.UPDATE_BOOKING,
                ConfirmationPolarity.NONE,
                new BookingPatch(null, null, ValuePatch.set("2026-09-10"), null, null), null), "turn-yesterday");

        assertThat(yesterday.response().responseCategory()).isEqualTo("PAST_DATE");
        verify(tools, never()).getBookingAvailability(any());

        when(tools.getBookingAvailability(any())).thenReturn(ToolResult.success(
                new AvailabilityResult(UUID.randomUUID(), session.activeDraft().draftId(), 5, "criteria",
                        session.activeDraft().selectedProvider().providerHandle(),
                        session.activeDraft().selectedProvider().doctorId(), null, LocalDate.of(2026, 9, 11),
                        null, "Asia/Kolkata", List.of(), null, false, NOW, NOW.plusSeconds(300))));

        var today = kernel.handle(session, decision(Operation.UPDATE_BOOKING,
                ConfirmationPolarity.NONE,
                new BookingPatch(null, null, ValuePatch.set("2026-09-11"), null, null), null), "turn-today");

        assertThat(today.response().responseCategory()).isEqualTo("NO_AVAILABILITY");
        verify(tools).getBookingAvailability(any());
    }

    @Test
    void contextQuestionUsesLatestResultWithoutCallingTool() {
        SessionProjection session = readySession();

        var result = kernel.handle(session, decision(Operation.ANSWER_CONTEXT,
                ConfirmationPolarity.NONE, BookingPatch.empty(), null), "turn-4");

        assertThat(result.response().responseCategory()).isEqualTo("CONTEXT_ANSWER");
        assertThat(result.response().assistantMessage()).contains("2026-09-14");
        verify(tools, never()).getBookingAvailability(any());
        verify(tools, never()).resolveBookingProvider(any(), any());
    }

    @Test
    void staleAvailabilityCannotBeSelected() {
        SessionProjection session = readySession();
        BookingDraft changed = new BookingDraft(session.activeDraft().draftId(), session.patientId(), session.tenantId(),
                session.activeDraft().selectedProvider(), null, LocalDate.parse("2026-09-15"), null, null,
                session.activeDraft().latestAvailabilityRequestId(), null, DraftStatus.COLLECTING,
                session.activeDraft().revision() + 1, session.activeDraft().expiresAt(), null);
        session = replace(session, changed, session.latestAvailabilityResult(), null);
        when(tools.criteriaFingerprint(changed)).thenReturn("changed");

        var result = kernel.handle(session, decision(Operation.SELECT_SLOT, ConfirmationPolarity.NONE,
                BookingPatch.empty(), new Selection(null, 1, null, null)), "turn-5");

        assertThat(result.response().responseCategory()).isEqualTo("STALE");
        verify(tools, never()).prepareBooking(any(), any());
    }

    @Test
    void showMoreAdvancesSingleAvailabilityResultAndLaterPageSlotCanBeSelected() {
        ProviderCandidate provider = provider();
        UUID patient = UUID.randomUUID();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, "tenant", provider, null,
                LocalDate.of(2026, 9, 14), null, null, null, null, DraftStatus.COLLECTING,
                1, NOW.plusSeconds(1800), null);
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(),
                "criteria", provider.providerHandle(), provider.doctorId(), null, draft.preferredDate(), null,
                "Asia/Kolkata", List.of(
                        slot("slot-1", "09:00"), slot("slot-2", "09:30"), slot("slot-3", "10:00"),
                        slot("slot-4", "10:30"), slot("slot-5", "11:00"), slot("slot-6", "11:30")),
                null, true, NOW, NOW.plusSeconds(300));
        draft = new BookingDraft(draft.draftId(), patient, "tenant", provider, null, draft.preferredDate(),
                null, null, availability.requestId(), null, DraftStatus.COLLECTING, 1,
                draft.expiresAt(), null);
        SessionProjection session = new SessionProjection("c1", patient, "tenant", draft, null, null,
                availability, null, 1, NOW.plusSeconds(1800));
        when(tools.criteriaFingerprint(any())).thenReturn("criteria");

        var secondPage = kernel.handle(session, decision(Operation.SHOW_MORE_SLOTS,
                ConfirmationPolarity.NONE, BookingPatch.empty(), null), "turn-more");

        assertThat(secondPage.response().responseCategory()).isEqualTo("SLOT_CHOICES");
        assertThat(secondPage.response().assistantMessage()).contains("4. 10:30", "5. 11:00", "6. 11:30");
        assertThat(secondPage.session().latestAvailabilityResult().displayOffset()).isEqualTo(3);
        verify(tools, never()).getBookingAvailability(any());

        var noMore = kernel.handle(secondPage.session(), decision(Operation.SHOW_MORE_SLOTS,
                ConfirmationPolarity.NONE, BookingPatch.empty(), null), "turn-no-more");
        assertThat(noMore.response().responseCategory()).isEqualTo("NO_MORE_SLOTS");
        assertThat(noMore.response().assistantMessage()).contains("no more available slots");
        verify(tools, never()).getBookingAvailability(any());

        when(tools.selectBookingSlot(any(), any(), any())).thenReturn(ToolResult.success(
                new AvailabilitySlot("slot-5", LocalTime.of(11, 0), LocalTime.of(11, 30), "11:00")));
        when(tools.prepareBooking(any(), any())).thenReturn(ToolResult.success(new BookingConfirmation(
                "confirm-5", draft.draftId(), draft.revision(), provider.providerHandle(), provider.doctorId(), null,
                availability.requestId(), "slot-5", draft.preferredDate(), LocalTime.of(11, 0),
                NOW.plusSeconds(300), "idem-5")));

        var selected = kernel.handle(secondPage.session(), decision(Operation.SELECT_SLOT,
                ConfirmationPolarity.NONE, BookingPatch.empty(), new Selection(null, 5, null, null)), "turn-select-5");

        assertThat(selected.response().responseCategory()).isEqualTo("CONFIRMATION_REQUIRED");
        verify(tools).selectBookingSlot(any(), any(), any());
    }

    private SessionProjection rescheduleConfirmationSession() {
        LocalDate target = LocalDate.of(2026, 9, 22);
        AvailabilityResult availability = new AvailabilityResult(UUID.randomUUID(), UUID.randomUUID(), 3,
                "criteria", "doctor-akshu", "doctor-akshu", "clinic", target, "evening", null,
                "Asia/Kolkata", List.of(slot("slot-1", "18:30"), slot("slot-2", "19:30")), null,
                false, NOW, NOW.plusSeconds(300), 0, 3);
        RescheduleResolution state = new RescheduleResolution("appointment", null, List.of(),
                "Doc Akshu Kumar", "doctor-akshu", "doctor-akshu", "clinic", "tenant", "clinic",
                LocalDate.of(2026, 9, 16), LocalTime.of(20, 0), target, null, availability.requestId(),
                availability, "slot-2", LocalTime.of(19, 30), RescheduleStatus.CONFIRMATION_PENDING, 3,
                NOW, NOW.plusSeconds(300));
        RescheduleConfirmation confirmation = new RescheduleConfirmation("confirm-reschedule", "appointment",
                "doctor-akshu", "doctor-akshu", "clinic", "tenant", availability.requestId(), "slot-2",
                target, LocalTime.of(19, 30), state.revision(), NOW, NOW.plusSeconds(300), "command-1");
        return new SessionProjection("c1", UUID.randomUUID(), "tenant", null, null, null, null, null,
                null, null, state, confirmation, 3, NOW.plusSeconds(1800));
    }

    private SessionProjection readySession() {
        UUID patient = UUID.randomUUID();
        ProviderCandidate provider = provider();
        BookingDraft draft = new BookingDraft(UUID.randomUUID(), patient, "tenant", provider, null,
                LocalDate.parse("2026-09-14"), null, LocalTime.of(16, 30), null, "slot-1",
                DraftStatus.READY_FOR_CONFIRMATION, 4, NOW.plusSeconds(1800), null);
        AvailabilityResult availability = availability(draft, "slot-1", LocalTime.of(16, 30));
        draft = new BookingDraft(draft.draftId(), patient, "tenant", provider, null, draft.preferredDate(), null,
                draft.exactTime(), availability.requestId(), "slot-1", DraftStatus.READY_FOR_CONFIRMATION,
                4, draft.expiresAt(), null);
        BookingConfirmation confirmation = new BookingConfirmation("confirm-1", draft.draftId(), 4,
                provider.providerHandle(), provider.doctorId(), provider.clinicId(), availability.requestId(),
                "slot-1", draft.preferredDate(), LocalTime.of(16, 30), NOW.plusSeconds(300), "idem-1");
        return new SessionProjection("c1", patient, "tenant", draft, null, null, availability,
                confirmation, 1, NOW.plusSeconds(1800));
    }

    private AvailabilityResult availability(BookingDraft draft, String slotRef, LocalTime time) {
        return new AvailabilityResult(UUID.randomUUID(), draft.draftId(), draft.revision(), "criteria",
                draft.selectedProvider().providerHandle(), draft.selectedProvider().doctorId(), null,
                draft.preferredDate(), null, "Asia/Kolkata",
                List.of(new AvailabilitySlot(slotRef, time, time.plusMinutes(30), time.toString())),
                null, false, NOW, NOW.plusSeconds(300));
    }

    private AvailabilitySlot slot(String reference, String time) {
        LocalTime startsAt = LocalTime.parse(time);
        return new AvailabilitySlot(reference, startsAt, startsAt.plusMinutes(30), time);
    }

    private ProviderCandidate provider() {
        return new ProviderCandidate("candidate", "provider", "doctor", null, "tenant", null, null,
                "Doc Akshu Kumar", "General Medicine", "Clinic", ProviderSource.CARE_PRIVATE,
                new ProviderCapabilities(true, true, false, false, false, true, false));
    }

    private ConversationDecision decision(Operation operation, ConfirmationPolarity confirmation,
                                          BookingPatch patch, Selection selection) {
        return new ConversationDecision("1.0", DialogAct.PROVIDE_INFORMATION, operation, patch, selection,
                confirmation, TopicAction.CONTINUE, "en", 1, "TEST", false, 1);
    }

    private BookingDraft copy(BookingDraft draft, DraftStatus status, String reference) {
        return new BookingDraft(draft.draftId(), draft.patientSubjectId(), draft.tenantScope(), draft.selectedProvider(),
                draft.specialtyFilter(), draft.preferredDate(), draft.preferredTimeWindow(), draft.exactTime(),
                draft.latestAvailabilityRequestId(), draft.selectedSlotReference(), status, draft.revision(),
                draft.expiresAt(), reference);
    }

    private SessionProjection replace(SessionProjection session, BookingDraft draft,
                                      AvailabilityResult availability, BookingConfirmation confirmation) {
        return new SessionProjection(session.conversationId(), session.patientId(), session.tenantId(), draft,
                session.suspendedDraft(), session.latestProviderResult(), availability, confirmation,
                session.version(), session.expiresAt());
    }
}
