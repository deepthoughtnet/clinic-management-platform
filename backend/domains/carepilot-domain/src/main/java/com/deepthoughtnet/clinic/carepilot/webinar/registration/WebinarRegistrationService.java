package com.deepthoughtnet.clinic.carepilot.webinar.registration;

import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationRecord;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
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

    public WebinarRegistrationService(
            WebinarRepository webinarRepository,
            WebinarRegistrationRepository registrationRepository,
            PatientRepository patientRepository,
            LeadRepository leadRepository
    ) {
        this.webinarRepository = webinarRepository;
        this.registrationRepository = registrationRepository;
        this.patientRepository = patientRepository;
        this.leadRepository = leadRepository;
    }

    @Transactional(readOnly = true)
    public Page<WebinarRegistrationRecord> list(UUID tenantId, UUID webinarId, int page, int size) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
        requireWebinar(tenantId, webinarId);
        return registrationRepository.findByTenantIdAndWebinarIdOrderByCreatedAtDesc(
                tenantId,
                webinarId,
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 200)), Sort.unsorted())
        ).map(this::toRecord);
    }

    @Transactional
    public WebinarRegistrationRecord register(UUID tenantId, UUID webinarId, WebinarRegistrationCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
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
        if (StringUtils.hasText(email)) {
            Optional<WebinarRegistrationEntity> existing = registrationRepository.findByTenantIdAndWebinarIdAndAttendeeEmail(tenantId, webinarId, email);
            if (existing.isPresent()) {
                WebinarRegistrationEntity row = existing.get();
                if (row.getRegistrationStatus() != WebinarRegistrationStatus.CANCELLED) {
                    return toRecord(row);
                }
            }
        }

        if (webinar.getCapacity() != null) {
            long active = registrationRepository.countByTenantIdAndWebinarId(tenantId, webinarId)
                    - registrationRepository.countByTenantIdAndWebinarIdAndRegistrationStatus(tenantId, webinarId, WebinarRegistrationStatus.CANCELLED);
            if (active >= webinar.getCapacity()) {
                throw new IllegalArgumentException("webinar capacity reached");
            }
        }

        if (command.patientId() == null && command.leadId() == null) {
            throw new IllegalArgumentException("patientId or leadId is required");
        }
        if (command.patientId() != null && patientRepository.findByTenantIdAndId(tenantId, command.patientId()).isEmpty()) {
            throw new IllegalArgumentException("patientId not found for tenant");
        }
        if (command.leadId() != null && leadRepository.findByTenantIdAndId(tenantId, command.leadId()).isEmpty()) {
            throw new IllegalArgumentException("leadId not found for tenant");
        }

        WebinarRegistrationEntity row = WebinarRegistrationEntity.create(tenantId, webinarId);
        row.setPatientId(command.patientId());
        row.setLeadId(command.leadId());
        row.setAttendeeName(requireText(command.attendeeName(), "attendeeName"));
        row.setAttendeeEmail(email);
        row.setAttendeePhone(normalize(command.attendeePhone()));
        row.setSource(command.source() == null ? inferSource(command.patientId(), command.leadId()) : command.source());
        row.setNotes(normalize(command.notes()));
        return toRecord(registrationRepository.save(row));
    }

    @Transactional
    public WebinarRegistrationRecord markAttendance(UUID tenantId, UUID webinarId, UUID registrationId, WebinarAttendanceCommand command) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(webinarId, "webinarId");
        CarePilotValidators.requireId(registrationId, "registrationId");
        if (command == null || command.registrationStatus() == null) {
            throw new IllegalArgumentException("registrationStatus is required");
        }
        requireWebinar(tenantId, webinarId);
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
        return toRecord(registrationRepository.save(row));
    }

    private WebinarEntity requireWebinar(UUID tenantId, UUID webinarId) {
        return webinarRepository.findByTenantIdAndId(tenantId, webinarId).orElseThrow(() -> new IllegalArgumentException("webinar not found"));
    }

    private WebinarRegistrationRecord toRecord(WebinarRegistrationEntity row) {
        return new WebinarRegistrationRecord(
                row.getId(), row.getTenantId(), row.getWebinarId(), row.getPatientId(), row.getLeadId(), row.getAttendeeName(), row.getAttendeeEmail(), row.getAttendeePhone(),
                row.getRegistrationStatus(), row.isAttended(), row.getAttendedAt(), row.getSource(), row.getNotes(), row.getCreatedAt(), row.getUpdatedAt()
        );
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
}
