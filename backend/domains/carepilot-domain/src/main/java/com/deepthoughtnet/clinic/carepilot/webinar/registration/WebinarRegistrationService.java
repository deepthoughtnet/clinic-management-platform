package com.deepthoughtnet.clinic.carepilot.webinar.registration;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.intake.LeadIntakeService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Registration and attendance service for webinar operations. */
@Service
public class WebinarRegistrationService {
    private final WebinarRepository webinarRepository;
    private final WebinarRegistrationRepository registrationRepository;
    private final PatientRepository patientRepository;
    private final LeadRepository leadRepository;
    private final LeadIntakeService leadIntakeService;
    private final LeadActivityService leadActivityService;
    private final CampaignRepository campaignRepository;

    public WebinarRegistrationService(
            WebinarRepository webinarRepository,
            WebinarRegistrationRepository registrationRepository,
            PatientRepository patientRepository,
            LeadRepository leadRepository,
            LeadIntakeService leadIntakeService,
            LeadActivityService leadActivityService,
            CampaignRepository campaignRepository
    ) {
        this.webinarRepository = webinarRepository;
        this.registrationRepository = registrationRepository;
        this.patientRepository = patientRepository;
        this.leadRepository = leadRepository;
        this.leadIntakeService = leadIntakeService;
        this.leadActivityService = leadActivityService;
        this.campaignRepository = campaignRepository;
    }

    @Transactional(readOnly = true)
    public Page<WebinarRegistrationRecord> list(UUID tenantId, UUID webinarId, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
        WebinarEntity webinar = requireWebinar(tenantId, webinarId);
        return registrationRepository.findByTenantIdAndWebinarIdOrderByCreatedAtDesc(
                tenantId,
                webinarId,
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 200)), Sort.unsorted())
        ).map(row -> toRecord(row, webinar));
    }

    @Transactional
    public WebinarRegistrationRecord register(UUID tenantId, UUID webinarId, WebinarRegistrationCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
        CarePilotValidators.requireId(actorId, "actorId");
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        WebinarEntity webinar = requireWebinar(tenantId, webinarId);
        if (!webinar.isRegistrationEnabled()) {
            throw new IllegalArgumentException("registration is disabled for this webinar");
        }
        if (webinar.getStatus() == WebinarStatus.CANCELLED) {
            throw new IllegalArgumentException("cancelled webinar does not accept registrations");
        }

        String email = normalize(command.attendeeEmail());
        String phone = normalize(command.attendeePhone());
        if (StringUtils.hasText(email)) {
            Optional<WebinarRegistrationEntity> existing = registrationRepository.findByTenantIdAndWebinarIdAndAttendeeEmail(tenantId, webinarId, email);
            if (existing.isPresent() && existing.get().getRegistrationStatus() != WebinarRegistrationStatus.CANCELLED) {
                return toRecord(existing.get(), webinar);
            }
        }
        if (StringUtils.hasText(phone)) {
            Optional<WebinarRegistrationEntity> existing = registrationRepository.findByTenantIdAndWebinarIdAndAttendeePhone(tenantId, webinarId, phone);
            if (existing.isPresent() && existing.get().getRegistrationStatus() != WebinarRegistrationStatus.CANCELLED) {
                return toRecord(existing.get(), webinar);
            }
        }

        if (webinar.getCapacity() != null) {
            long active = registrationRepository.countByTenantIdAndWebinarId(tenantId, webinarId)
                    - registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinarId, WebinarRegistrationStatus.CANCELLED);
            if (active >= webinar.getCapacity()) {
                throw new IllegalArgumentException("webinar capacity reached");
            }
        }

        PatientEntity patient = command.patientId() == null
                ? null
                : patientRepository.findByTenantIdAndId(tenantId, command.patientId())
                        .orElseThrow(() -> new IllegalArgumentException("patientId not found for tenant"));
        if (command.patientId() == null
                && command.leadId() == null
                && !StringUtils.hasText(email)
                && !StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("patientId, leadId, attendeeEmail, or attendeePhone is required");
        }

        LeadRecord linkedLead = resolveLead(tenantId, webinar, command, patient, email, phone, actorId);

        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinarId);
        row.setPatientId(command.patientId());
        row.setLeadId(linkedLead == null ? null : linkedLead.id());
        row.setAttendeeName(requireText(command.attendeeName(), "attendeeName"));
        row.setAttendeeEmail(email);
        row.setAttendeePhone(phone);
        row.setSource(command.source() == null ? inferSource(command.patientId(), row.getLeadId()) : command.source());
        row.setNotes(normalize(command.notes()));
        WebinarRegistrationEntity saved = registrationRepository.save(row);
        if (linkedLead != null) {
            leadActivityService.record(
                    tenantId,
                    linkedLead.id(),
                    LeadActivityType.NOTE_ADDED,
                    "Webinar registered",
                    "Registered for webinar: " + webinar.getTitle(),
                    null,
                    null,
                    "WEBINAR",
                    webinar.getId(),
                    actorId
            );
        }
        return toRecord(saved, webinar, linkedLead);
    }

    @Transactional
    public WebinarRegistrationRecord markAttendance(UUID tenantId, UUID webinarId, UUID registrationId, WebinarAttendanceCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
        CarePilotValidators.requireId(registrationId, "registrationId");
        if (command == null || command.registrationStatus() == null) {
            throw new IllegalArgumentException("registrationStatus is required");
        }
        WebinarEntity webinar = requireWebinar(tenantId, webinarId);
        WebinarRegistrationEntity row = registrationRepository.findById(registrationId)
                .filter(r -> r.getTenantId().equals(tenantId) && r.getWebinarId().equals(webinarId))
                .orElseThrow(() -> new IllegalArgumentException("registration not found"));

        row.setRegistrationStatus(command.registrationStatus());
        row.setNotes(normalize(command.notes()));
        if (command.registrationStatus() == WebinarRegistrationStatus.ATTENDED) {
            row.setAttended(true);
            row.setAttendedAt(OffsetDateTime.now());
        } else if (command.registrationStatus() == WebinarRegistrationStatus.NO_SHOW) {
            row.setAttended(false);
            row.setAttendedAt(null);
        }
        row.touch();
        return toRecord(registrationRepository.save(row), webinar);
    }

    private WebinarEntity requireWebinar(UUID tenantId, UUID webinarId) {
        return webinarRepository.findByTenantIdAndId(tenantId, webinarId).orElseThrow(() -> new IllegalArgumentException("webinar not found"));
    }

    private LeadRecord resolveLead(
            UUID tenantId,
            WebinarEntity webinar,
            WebinarRegistrationCommand command,
            PatientEntity patient,
            String email,
            String phone,
            UUID actorId
    ) {
        LeadEntity explicitLead = command.leadId() == null
                ? null
                : leadRepository.findByTenantIdAndId(tenantId, command.leadId())
                        .orElseThrow(() -> new IllegalArgumentException("leadId not found for tenant"));
        if (explicitLead != null) {
            return ensureCampaign(explicitLead, webinar, actorId);
        }

        LeadEntity existingLead = findExistingLead(tenantId, email, phone, patient);
        if (existingLead != null) {
            return ensureCampaign(existingLead, webinar, actorId);
        }

        String leadPhone = firstNonBlank(phone, patient == null ? null : normalize(patient.getMobile()));
        if (!StringUtils.hasText(leadPhone)) {
            throw new IllegalArgumentException("attendeePhone or patient mobile is required to create a webinar lead");
        }
        String leadEmail = firstNonBlank(email, patient == null ? null : normalize(patient.getEmail()));
        NameParts nameParts = splitName(firstNonBlank(command.attendeeName(), buildPatientName(patient)));
        return leadIntakeService.intake(
                tenantId,
                new LeadUpsertCommand(
                        nameParts.firstName(),
                        nameParts.lastName(),
                        leadPhone,
                        leadEmail,
                        patient == null ? null : patient.getGender(),
                        patient == null ? null : patient.getDateOfBirth(),
                        LeadSource.WEBINAR,
                        buildSourceDetails(webinar, command.source()),
                        webinar.getCampaignId(),
                        null,
                        LeadStatus.NEW,
                        LeadPriority.MEDIUM,
                        null,
                        null,
                        null,
                        null
                ),
                actorId
        );
    }

    private LeadEntity findExistingLead(UUID tenantId, String email, String phone, PatientEntity patient) {
        String candidateEmail = firstNonBlank(email, patient == null ? null : normalize(patient.getEmail()));
        if (StringUtils.hasText(candidateEmail)) {
            Optional<LeadEntity> lead = leadRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, candidateEmail);
            if (lead.isPresent()) {
                return lead.get();
            }
        }

        String candidatePhone = firstNonBlank(phone, patient == null ? null : normalize(patient.getMobile()));
        if (StringUtils.hasText(candidatePhone)) {
            Optional<LeadEntity> lead = leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, candidatePhone);
            if (lead.isPresent()) {
                return lead.get();
            }
        }
        return null;
    }

    private LeadRecord ensureCampaign(LeadEntity lead, WebinarEntity webinar, UUID actorId) {
        if (lead.getCampaignId() == null && webinar.getCampaignId() != null) {
            lead.setCampaignId(webinar.getCampaignId());
            lead.touch(actorId);
            leadRepository.save(lead);
            leadActivityService.record(
                    lead.getTenantId(),
                    lead.getId(),
                    LeadActivityType.CAMPAIGN_LINKED,
                    "Campaign linked",
                    "Linked campaign from webinar registration",
                    null,
                    null,
                    "CAMPAIGN",
                    webinar.getCampaignId(),
                    actorId
            );
        }
        return toLeadRecord(lead);
    }

    private WebinarRegistrationRecord toRecord(WebinarRegistrationEntity row, WebinarEntity webinar) {
        return toRecord(row, webinar, loadLead(row.getTenantId(), row.getLeadId()));
    }

    private WebinarRegistrationRecord toRecord(WebinarRegistrationEntity row, WebinarEntity webinar, LeadRecord lead) {
        UUID campaignId = lead == null || lead.campaignId() == null ? webinar.getCampaignId() : lead.campaignId();
        return new WebinarRegistrationRecord(
                row.getId(),
                row.getTenantId(),
                row.getWebinarId(),
                row.getPatientId(),
                row.getLeadId(),
                lead == null ? null : lead.fullName(),
                campaignId,
                resolveCampaignName(webinar, campaignId),
                row.getAttendeeName(),
                row.getAttendeeEmail(),
                row.getAttendeePhone(),
                row.getRegistrationStatus(),
                row.isAttended(),
                row.getAttendedAt(),
                row.getSource(),
                row.getNotes(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private LeadRecord loadLead(UUID tenantId, UUID leadId) {
        if (leadId == null) {
            return null;
        }
        return leadRepository.findByTenantIdAndId(tenantId, leadId).map(this::toLeadRecord).orElse(null);
    }

    private LeadRecord toLeadRecord(LeadEntity lead) {
        return new LeadRecord(
                lead.getId(),
                lead.getTenantId(),
                lead.getFirstName(),
                lead.getLastName(),
                lead.getFullName(),
                lead.getPhone(),
                lead.getEmail(),
                lead.getGender(),
                lead.getDateOfBirth(),
                lead.getSource(),
                lead.getSourceDetails(),
                lead.getCampaignId(),
                lead.getAssignedToAppUserId(),
                lead.getStatus(),
                lead.getPriority(),
                lead.getNotes(),
                lead.getTags(),
                lead.getConvertedPatientId(),
                lead.getBookedAppointmentId(),
                lead.getLastContactedAt(),
                lead.getNextFollowUpAt(),
                null,
                lead.getCreatedBy(),
                lead.getUpdatedBy(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }

    private String resolveCampaignName(WebinarEntity webinar, UUID campaignId) {
        if (campaignId == null) {
            return null;
        }
        return campaignRepository.findByTenantIdAndId(webinar.getTenantId(), campaignId).map(c -> c.getName()).orElse(null);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String field) {
        CarePilotValidators.requireText(value, field);
        return value.trim();
    }

    private WebinarRegistrationSource inferSource(UUID patientId, UUID leadId) {
        if (patientId != null) {
            return WebinarRegistrationSource.PATIENT;
        }
        if (leadId != null) {
            return WebinarRegistrationSource.LEAD;
        }
        return WebinarRegistrationSource.MANUAL;
    }

    private String buildSourceDetails(WebinarEntity webinar, WebinarRegistrationSource source) {
        String registrationSource = source == null ? WebinarRegistrationSource.MANUAL.name() : source.name();
        return "Webinar title=" + webinar.getTitle()
                + "; webinar type=" + webinar.getWebinarType().name()
                + "; registration source=" + registrationSource;
    }

    private String buildPatientName(PatientEntity patient) {
        if (patient == null) {
            return null;
        }
        String firstName = normalize(patient.getFirstName());
        String lastName = normalize(patient.getLastName());
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private NameParts splitName(String fullName) {
        String safe = StringUtils.hasText(fullName) ? fullName.trim() : "Webinar";
        int separator = safe.indexOf(' ');
        if (separator < 0) {
            return new NameParts(safe, null);
        }
        String firstName = safe.substring(0, separator).trim();
        String lastName = safe.substring(separator + 1).trim();
        return new NameParts(firstName, StringUtils.hasText(lastName) ? lastName : null);
    }

    private record NameParts(String firstName, String lastName) {}
}
