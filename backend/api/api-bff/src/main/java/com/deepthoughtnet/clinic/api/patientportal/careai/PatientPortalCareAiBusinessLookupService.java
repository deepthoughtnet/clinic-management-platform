package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

class PatientPortalCareAiBusinessLookupService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiBusinessLookupService.class);

    private final PatientPortalService patientPortalService;
    private final PublicCatalogFacade publicCatalogFacade;

    PatientPortalCareAiBusinessLookupService(
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade
    ) {
        this.patientPortalService = patientPortalService;
        this.publicCatalogFacade = publicCatalogFacade;
    }

    List<PublicDoctorSummaryResponse> findDoctors(String doctorQuery, String specialityQuery, String clinicSlug) {
        trace("CAREAI_TRACE_DOCTOR_LOOKUP_START",
                "query=" + doctorQuery
                        + " speciality=" + specialityQuery
                        + " lookupMode=" + (StringUtils.hasText(clinicSlug) ? "clinic-specific" : "cross-clinic")
                        + " clinicSlug=" + clinicSlug
                        + " clinicId=null tenantId=null");
        if (publicCatalogFacade == null) {
            List<PublicDoctorSummaryResponse> doctors = patientPortalService.doctors().stream()
                    .filter(doctor -> matchesDoctor(doctor, doctorQuery, specialityQuery))
                    .map(this::toPublicDoctorSummary)
                    .toList();
            trace("CAREAI_TRACE_DOCTOR_LOOKUP_END",
                    "service=fallback-patientPortalService.doctors resultCount=" + doctors.size()
                            + " doctorIds=" + doctors.stream().map(PublicDoctorSummaryResponse::publicDoctorId).toList()
                            + " clinicSlugs=" + doctors.stream().map(PublicDoctorSummaryResponse::clinicSlug).toList());
            return doctors;
        }
        PublicPageResponse<PublicDoctorSummaryResponse> page = publicCatalogFacade.listDoctors(
                StringUtils.hasText(doctorQuery) ? doctorQuery : null,
                null,
                null,
                StringUtils.hasText(specialityQuery) ? specialityQuery : null,
                StringUtils.hasText(clinicSlug) ? clinicSlug : null,
                null,
                0,
                24
        );
        trace("CAREAI_TRACE_DOCTOR_LOOKUP_END",
                "service=publicCatalogFacade.listDoctors resultCount=" + page.items().size()
                        + " doctorIds=" + page.items().stream().map(PublicDoctorSummaryResponse::publicDoctorId).toList()
                        + " clinicSlugs=" + page.items().stream().map(PublicDoctorSummaryResponse::clinicSlug).toList());
        return page.items();
    }

    List<PublicClinicSummaryResponse> findClinics(String clinicQuery) {
        if (publicCatalogFacade == null) {
            trace("findClinics", "fallback=empty resultCount=0 clinicQuery=" + clinicQuery);
            return List.of();
        }
        PublicPageResponse<PublicClinicSummaryResponse> page = publicCatalogFacade.listClinics(
                StringUtils.hasText(clinicQuery) ? clinicQuery : null,
                null,
                null,
                null,
                null,
                1,
                24
        );
        trace("findClinics", "service=publicCatalogFacade.listClinics resultCount=" + page.items().size()
                + " clinicQuery=" + clinicQuery);
        return page.items();
    }

    List<PatientPortalDoctorSlotResponse> findSlots(
            String publicDoctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            LocalDate date
    ) {
        if (StringUtils.hasText(clinicId) || StringUtils.hasText(tenantId)) {
            return patientPortalService.doctorSlots(publicDoctorId, clinicSlug, tenantId, clinicId, date);
        }
        if (StringUtils.hasText(clinicSlug)) {
            return patientPortalService.doctorSlots(publicDoctorId, clinicSlug, date);
        }
        return patientPortalService.doctorSlots(publicDoctorId, date);
    }

    List<com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption> upcomingAppointments() {
        trace("CAREAI_TRACE_APPOINTMENTS_START",
                "patientId=" + safePatientId()
                        + " mobile=" + safeMobile()
                        + " conversationTenantId=" + safeConversationTenantId()
                        + " tenantContextTenantId=" + safeTenantContextTenantId());
        List<com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption> appointments = patientPortalService.debugAppointments().stream()
                .filter(this::isActiveUpcomingAppointment)
                .toList();
        trace("CAREAI_TRACE_APPOINTMENTS_END",
                "repositoryPath=patientPortalService.debugAppointments"
                        + " linkedTenantIds=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::tenantId).distinct().toList()
                        + " appointmentIds=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::appointmentId).toList()
                        + " statuses=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::status).toList()
                        + " count=" + appointments.size());
        return appointments;
    }

    private boolean isActiveUpcomingAppointment(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption appointment) {
        if (appointment == null) {
            return false;
        }
        String status = appointment.status() == null ? "" : appointment.status().trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "BOOKED", "CONFIRMED", "SCHEDULED" -> true;
            default -> false;
        };
    }

    private boolean matchesDoctor(PatientPortalDoctorResponse doctor, String doctorQuery, String specialityQuery) {
        if (doctor == null) {
            return false;
        }
        if (StringUtils.hasText(specialityQuery)
                && (doctor.specialization() == null || !doctor.specialization().toLowerCase().contains(specialityQuery.toLowerCase()))) {
            return false;
        }
        if (!StringUtils.hasText(doctorQuery)) {
            return true;
        }
        String name = doctor.doctorName() == null ? "" : doctor.doctorName().toLowerCase();
        String query = doctorQuery.toLowerCase();
        return name.contains(query) || query.contains(name);
    }

    private PublicDoctorSummaryResponse toPublicDoctorSummary(PatientPortalDoctorResponse doctor) {
        return new PublicDoctorSummaryResponse(
                doctor.publicDoctorId(),
                null,
                doctor.doctorName(),
                null,
                doctor.specialization(),
                doctor.yearsOfExperience(),
                List.of(),
                null,
                null,
                null,
                null,
                false,
                null
        );
    }

    private String safePatientId() {
        try {
            return String.valueOf(patientPortalService.currentPatientId());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeMobile() {
        try {
            return patientPortalService.currentPatientMobile();
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeConversationTenantId() {
        try {
            return String.valueOf(com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.requireTenantId());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeTenantContextTenantId() {
        try {
            return com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.get() == null
                    ? null
                    : com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder.get().tenantId().value().toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private void trace(String method, String details) {
        log.info("CAREAI_TRACE method={} phase=lookup {}", method, details);
    }
}
