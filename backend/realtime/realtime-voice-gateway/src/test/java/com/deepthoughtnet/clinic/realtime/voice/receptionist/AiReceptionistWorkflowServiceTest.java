package com.deepthoughtnet.clinic.realtime.voice.receptionist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotStatus;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import com.deepthoughtnet.clinic.realtime.voice.session.VoiceSessionType;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiReceptionistWorkflowServiceTest {
    private ClinicProfileService clinicProfileService;
    private AppointmentService appointmentService;
    private TenantUserManagementService tenantUserManagementService;
    private LeadService leadService;
    private AiReceptionistWorkflowService service;

    @BeforeEach
    void setUp() {
        clinicProfileService = Mockito.mock(ClinicProfileService.class);
        appointmentService = Mockito.mock(AppointmentService.class);
        tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        leadService = Mockito.mock(LeadService.class);
        when(leadService.search(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(leadService.create(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockLeadRecord());
        when(leadService.update(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockLeadRecord());
        service = new AiReceptionistWorkflowService(
                new ObjectMapper(),
                leadService,
                Mockito.mock(LeadActivityService.class),
                clinicProfileService,
                appointmentService,
                tenantUserManagementService,
                Mockito.mock(DoctorProfileService.class)
        );
    }

    @Test
    void greetingTransitionsToIdentifyIntent() {
        VoiceSessionEntity session = newSession("{}");

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "hello", "corr-1");

        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_INTENT_DETECTION");
        assertThat(session.getMetadataJson()).contains("IDENTIFY_INTENT");
    }

    @Test
    void faqIntentUsesDeterministicClinicProfileWhenAvailable() {
        UUID tenantId = UUID.randomUUID();
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", tenantId);
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(new ClinicProfileRecord(
                UUID.randomUUID(), tenantId, "Clinic One", "Clinic One", "+1-222-333-4444", "contact@clinic.test",
                "12 Main St", null, "Metropolis", "CA", "US", "90001", null, null, null, true, null, null
        )));

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "what is your address", "corr-2");

        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_FAQ");
        assertThat(result.deterministicReply()).contains("12 Main St");
    }

    @Test
    void emergencyIntentAlwaysEscalates() {
        VoiceSessionEntity session = newSession("{}");

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I have chest pain emergency", "corr-3");

        assertThat(result.escalate()).isTrue();
        assertThat(result.promptKey()).isEqualTo("AI_RECEPTIONIST_SAFE_ESCALATION");
        assertThat(result.deterministicReply()).contains("can't provide medical advice");
        assertThat(session.getMetadataJson()).contains("HUMAN_ESCALATION");
    }

    @Test
    void repeatedUnknownIntentEscalatesOnThirdAttempt() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\",\"unknownCount\":2}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "blabla random", "corr-4");

        assertThat(result.escalate()).isTrue();
        assertThat(result.escalationReason()).contains("Low confidence");
        assertThat(session.getMetadataJson()).contains("IDENTIFY_INTENT");
    }

    @Test
    void bookingIntentTracksMissingFieldsInMemory() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I want to book appointment", "corr-5");

        assertThat(result.intent()).isEqualTo(ReceptionistIntent.BOOK_APPOINTMENT);
        assertThat(session.getMetadataJson()).contains("missingFields");
        assertThat(session.getMetadataJson()).contains("appointmentRequestCreated");
    }

    @Test
    void billingIntentRoutesToBillingDeskEscalation() {
        VoiceSessionEntity session = newSession("{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\"}}", UUID.randomUUID());

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(), "I need billing invoice help", "corr-6");

        assertThat(result.escalate()).isTrue();
        assertThat(result.escalationCategory()).isEqualTo("BILLING_DESK");
        assertThat(result.deterministicReply()).contains("billing desk");
    }

    @Test
    void confirmedBookingCreatesAppointmentWhenPatientAndSlotAreAvailable() {
        UUID tenantId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        VoiceSessionEntity session = VoiceSessionEntity.create(
                tenantId,
                VoiceSessionType.AI_RECEPTIONIST,
                patientId,
                null,
                "ai-orchestration",
                "mock-stt",
                "mock-tts",
                "{\"receptionist\":{\"state\":\"IDENTIFY_INTENT\",\"slots\":{\"name\":\"John\",\"phone\":\"+15550001\",\"preferredDate\":\"tomorrow\",\"preferredTime\":\"10:00\",\"specialty\":\"cardio\",\"reasonForVisit\":\"checkup\"}}}"
        );

        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                new TenantUserRecord(doctorId, tenantId, "sub", "doctor@test", "Dr Cardio", "ACTIVE", "DOCTOR", "ACTIVE",
                        OffsetDateTime.now(), OffsetDateTime.now(), "READY")
        ));
        when(appointmentService.listSlots(Mockito.eq(tenantId), Mockito.eq(doctorId), Mockito.any(LocalDate.class))).thenReturn(List.of(
                new DoctorAvailabilitySlotRecord(doctorId, "Dr Cardio", LocalDate.now().plusDays(1), LocalTime.of(10, 0),
                        LocalTime.of(10, 15), DoctorAvailabilitySlotStatus.AVAILABLE, 0, 1, true,
                        null, null, null, null, null, null, null)
        ));
        when(appointmentService.createScheduled(Mockito.eq(tenantId), Mockito.any(), Mockito.any(), Mockito.eq(false)))
                .thenReturn(new AppointmentRecord(
                        UUID.randomUUID(), tenantId, patientId, null, null, null, doctorId, "Dr Cardio", null,
                        LocalDate.now().plusDays(1), LocalTime.of(10, 0), null, "checkup",
                        AppointmentType.SCHEDULED, AppointmentPriority.NORMAL, AppointmentStatus.BOOKED,
                        OffsetDateTime.now(), OffsetDateTime.now()
                ));

        ReceptionistWorkflowResult result = service.evaluate(session, UUID.randomUUID(),
                "please confirm and book tomorrow at 10:00 with cardio", "corr-7");

        assertThat(result.intent()).isEqualTo(ReceptionistIntent.BOOK_APPOINTMENT);
        assertThat(session.getMetadataJson()).contains("BOOKING_CREATED");
        assertThat(session.getMetadataJson()).contains("appointmentId");
    }

    private VoiceSessionEntity newSession(String metadata) {
        return newSession(metadata, UUID.randomUUID());
    }

    private VoiceSessionEntity newSession(String metadata, UUID tenantId) {
        return VoiceSessionEntity.create(
                tenantId,
                VoiceSessionType.AI_RECEPTIONIST,
                null,
                null,
                "ai-orchestration",
                "mock-stt",
                "mock-tts",
                metadata
        );
    }

    private LeadRecord mockLeadRecord() {
        OffsetDateTime now = OffsetDateTime.now();
        return new LeadRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "John",
                "Doe",
                "John Doe",
                "+15550001",
                "john@example.test",
                PatientGender.UNKNOWN,
                null,
                LeadSource.AI_RECEPTIONIST,
                "AI_RECEPTIONIST",
                null,
                null,
                LeadStatus.FOLLOW_UP_REQUIRED,
                LeadPriority.MEDIUM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }
}
