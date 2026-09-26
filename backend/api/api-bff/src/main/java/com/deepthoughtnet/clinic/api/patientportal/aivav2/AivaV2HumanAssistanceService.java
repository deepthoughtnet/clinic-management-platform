package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaStructuredResponse.HumanAssistancePayload;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.SessionProjection;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Resolves only configured clinic contact capabilities; it never pretends to transfer a call. */
@Service
class AivaV2HumanAssistanceService {
    private final ClinicProfileService clinicProfileService;
    private final AuditEventPublisher auditEventPublisher;

    AivaV2HumanAssistanceService(ClinicProfileService clinicProfileService,
                                 AuditEventPublisher auditEventPublisher) {
        this.clinicProfileService = clinicProfileService;
        this.auditEventPublisher = auditEventPublisher;
    }

    HumanAssistancePayload request(SessionProjection session) {
        UUID tenantId = UUID.fromString(session.tenantId());
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String phone = clinic == null ? null : clinic.phone();
        List<String> channels = phone == null || phone.isBlank() ? List.of() : List.of("CALL_RECEPTION");
        auditEventPublisher.record(new AuditEventCommand(
                tenantId, "PATIENT", session.patientId(), "HUMAN_ASSISTANCE_REQUESTED",
                RequestContextHolder.require().appUserId(), OffsetDateTime.now(),
                "AIVA human assistance requested", "{\"category\":\"APPOINTMENT_ASSISTANCE\",\"channel\":"
                        + (channels.isEmpty() ? "null" : "\"CALL_RECEPTION\"") + "}"));
        return new HumanAssistancePayload(channels, phone, "APPOINTMENT_ASSISTANCE");
    }
}
