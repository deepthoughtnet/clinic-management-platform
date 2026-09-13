package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;

final class PatientPortalCareAiTestSupport {
    private PatientPortalCareAiTestSupport() {
    }

    static void setPatientContext(UUID tenantId, UUID appUserId) {
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), appUserId, "subject-1", Set.of("PATIENT"), "PATIENT", "corr-1"));
    }

    static PatientPortalDoctorResponse doctor(String publicDoctorId, String doctorName, String specialization) {
        return new PatientPortalDoctorResponse(publicDoctorId, doctorName, specialization, "MBBS", "Room 1", 8);
    }
}
