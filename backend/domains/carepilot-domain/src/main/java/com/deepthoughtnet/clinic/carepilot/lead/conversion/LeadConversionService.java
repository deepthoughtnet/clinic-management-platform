package com.deepthoughtnet.clinic.carepilot.lead.conversion;

import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPublisher;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvents;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Converts leads into patients with duplicate-safe linking behavior. */
@Service
public class LeadConversionService {
    private final LeadRepository leadRepository;
    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final LeadActivityService leadActivityService;
    private final LeadService leadService;
    private final ModuleBusinessEventPublisher moduleBusinessEventPublisher;

    public LeadConversionService(
            LeadRepository leadRepository,
            PatientRepository patientRepository,
            PatientService patientService,
            AppointmentService appointmentService,
            LeadActivityService leadActivityService,
            LeadService leadService,
            ModuleBusinessEventPublisher moduleBusinessEventPublisher
    ) {
        this.leadRepository = leadRepository;
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.leadActivityService = leadActivityService;
        this.leadService = leadService;
        this.moduleBusinessEventPublisher = moduleBusinessEventPublisher;
    }

    @Transactional
    public LeadConversionResult convert(UUID tenantId, UUID leadId, UUID actorId, LeadAppointmentBookingCommand appointmentBooking, boolean allowOverbooking) {
        return convert(tenantId, leadId, actorId, appointmentBooking, allowOverbooking, null, true);
    }

    @Transactional
    public LeadConversionResult convert(
            UUID tenantId,
            UUID leadId,
            UUID actorId,
            LeadAppointmentBookingCommand appointmentBooking,
            boolean allowOverbooking,
            UUID viewerAppUserId,
            boolean viewAll
    ) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(leadId, "leadId");
        leadService.assertVisible(tenantId, leadId, viewerAppUserId, viewAll);
        LeadEntity lead = leadRepository.findByTenantIdAndId(tenantId, leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        if (lead.getConvertedPatientId() != null) {
            return new LeadConversionResult(lead.getId(), lead.getConvertedPatientId(), false, lead.getBookedAppointmentId(), null);
        }
        if (lead.getStatus() == LeadStatus.SPAM) {
            throw new IllegalArgumentException("Spam lead cannot be converted");
        }
        OffsetDateTime previousFollowUpAt = lead.getNextFollowUpAt();
        if (appointmentBooking != null) {
            appointmentService.validateScheduledBookingRequest(
                    tenantId,
                    appointmentBooking.doctorUserId(),
                    appointmentBooking.appointmentDate(),
                    appointmentBooking.appointmentTime(),
                    StringUtils.hasText(appointmentBooking.notes()) ? appointmentBooking.notes() : appointmentBooking.reason(),
                    appointmentBooking.priority(),
                    allowOverbooking
            );
        }

        Optional<PatientEntity> existingByPhone = patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, lead.getPhone());
        Optional<PatientEntity> existingByEmail = StringUtils.hasText(lead.getEmail())
                ? patientRepository.findFirstByTenantIdAndEmailIgnoreCaseAndActiveTrue(tenantId, lead.getEmail()) : Optional.empty();

        UUID patientId;
        boolean created;
        if (existingByPhone.isPresent()) {
            patientId = existingByPhone.get().getId();
            created = false;
        } else if (existingByEmail.isPresent()) {
            patientId = existingByEmail.get().getId();
            created = false;
        } else {
            PatientUpsertCommand patientCommand = new PatientUpsertCommand(
                    lead.getFirstName(),
                    lead.getLastName(),
                    lead.getGender() == null ? PatientGender.UNKNOWN : lead.getGender(),
                    lead.getDateOfBirth(),
                    null,
                    lead.getPhone(),
                    lead.getEmail(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    lead.getNotes(),
                    true
            );
            patientId = patientService.create(tenantId, patientCommand, actorId).id();
            created = true;
        }

        UUID appointmentId = null;
        if (appointmentBooking != null) {
            AppointmentRecord record = appointmentService.createScheduled(
                    tenantId,
                    new AppointmentUpsertCommand(
                            patientId,
                            appointmentBooking.doctorUserId(),
                            appointmentBooking.appointmentDate(),
                            appointmentBooking.appointmentTime(),
                            StringUtils.hasText(appointmentBooking.notes()) ? appointmentBooking.notes() : appointmentBooking.reason(),
                            AppointmentType.SCHEDULED,
                            AppointmentStatus.BOOKED,
                            appointmentBooking.priority()
                    ),
                    actorId,
                    allowOverbooking
            );
            appointmentId = record.id();
        }

        lead.setNextFollowUpAt(null);
        lead.setConverted(patientId, actorId);
        leadRepository.save(lead);
        if (previousFollowUpAt != null) {
            leadActivityService.record(
                    tenantId,
                    lead.getId(),
                    LeadActivityType.FOLLOW_UP_COMPLETED,
                    "Follow-up completed",
                    "Follow-up cleared during conversion",
                    null,
                    LeadStatus.CONVERTED,
                    null,
                    null,
                    actorId
            );
        }
        leadActivityService.record(
                tenantId,
                lead.getId(),
                LeadActivityType.CONVERTED_TO_PATIENT,
                "Converted to patient",
                created ? "Created new patient" : "Linked existing patient",
                null,
                LeadStatus.CONVERTED,
                "PATIENT",
                patientId,
                actorId
        );
        if (appointmentId != null) {
            leadService.linkAppointment(tenantId, lead.getId(), appointmentId, actorId);
        }
        moduleBusinessEventPublisher.publish(ModuleBusinessEvents.leadConverted(
                tenantId,
                lead.getId(),
                patientId,
                created,
                appointmentId,
                actorId
        ));

        return new LeadConversionResult(lead.getId(), patientId, created, appointmentId, null);
    }
}
