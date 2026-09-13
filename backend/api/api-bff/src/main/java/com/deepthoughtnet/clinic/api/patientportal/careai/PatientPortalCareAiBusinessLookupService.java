package com.deepthoughtnet.clinic.api.patientportal.careai;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.publicsite.PublicCatalogFacade;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

class PatientPortalCareAiBusinessLookupService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiBusinessLookupService.class);

    private final PatientPortalService patientPortalService;
    private final PublicCatalogFacade publicCatalogFacade;
    private final DiscoverReferenceDataService discoverReferenceDataService;

    PatientPortalCareAiBusinessLookupService(
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade
    ) {
        this(patientPortalService, publicCatalogFacade, null);
    }

    PatientPortalCareAiBusinessLookupService(
            PatientPortalService patientPortalService,
            PublicCatalogFacade publicCatalogFacade,
            DiscoverReferenceDataService discoverReferenceDataService
    ) {
        this.patientPortalService = patientPortalService;
        this.publicCatalogFacade = publicCatalogFacade;
        this.discoverReferenceDataService = discoverReferenceDataService;
    }

    List<PublicDoctorSummaryResponse> findDoctors(String doctorQuery, String specialityQuery, String clinicSlug) {
        trace("CAREAI_TRACE_DOCTOR_LOOKUP_START",
                "query=" + doctorQuery
                        + " speciality=" + specialityQuery
                        + " lookupMode=" + (StringUtils.hasText(clinicSlug) ? "clinic-specific" : "cross-clinic")
                        + " clinicSlug=" + clinicSlug
                        + " clinicId=null tenantId=null");
        LinkedHashMap<String, PublicDoctorSummaryResponse> merged = new LinkedHashMap<>();
        List<PatientPortalDoctorResponse> patientDoctors = patientPortalService.doctors();
        if (patientDoctors == null) {
            patientDoctors = List.of();
        }
        List<PublicDoctorSummaryResponse> privateDoctors = patientDoctors.stream()
                .filter(doctor -> matchesDoctor(doctor, doctorQuery, specialityQuery))
                .map(this::toPublicDoctorSummary)
                .toList();
        privateDoctors.forEach(doctor -> merged.putIfAbsent(doctorLookupKey(doctor), doctor));
        if (publicCatalogFacade != null) {
            PublicPageResponse<PublicDoctorSummaryResponse> page = publicCatalogFacade.listDoctors(
                    StringUtils.hasText(doctorQuery) ? doctorQuery : null,
                    null,
                    null,
                    StringUtils.hasText(specialityQuery) ? specialityQuery : null,
                    StringUtils.hasText(clinicSlug) ? clinicSlug : null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    24
            );
            if (page != null && page.items() != null) {
                page.items().forEach(doctor -> merged.putIfAbsent(doctorLookupKey(doctor), doctor));
            }
        }
        List<PublicDoctorSummaryResponse> doctors = List.copyOf(merged.values());
        trace("CAREAI_TRACE_DOCTOR_LOOKUP_END",
                "service=merged-patientPortalService.doctors+publicCatalogFacade.listDoctors resultCount=" + doctors.size()
                        + " doctorIds=" + doctors.stream().map(PublicDoctorSummaryResponse::publicDoctorId).toList()
                        + " clinicSlugs=" + doctors.stream().map(PublicDoctorSummaryResponse::clinicSlug).toList());
        return doctors;
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
                null,
                null,
                null,
                1,
                24
        );
        if (page == null || page.items() == null) {
            trace("findClinics", "service=publicCatalogFacade.listClinics resultCount=0 clinicQuery=" + clinicQuery);
            return List.of();
        }
        trace("findClinics", "service=publicCatalogFacade.listClinics resultCount=" + page.items().size()
                + " clinicQuery=" + clinicQuery);
        return page.items();
    }

    List<DiscoverReferenceOptionRecord> findServices(String serviceQuery, String locationQuery) {
        if (discoverReferenceDataService == null) {
            trace("findServices", "fallback=empty resultCount=0 serviceQuery=" + serviceQuery + " locationQuery=" + locationQuery);
            return List.of();
        }
        List<DiscoverReferenceOptionRecord> services = discoverReferenceDataService.listServices();
        if (StringUtils.hasText(serviceQuery) || StringUtils.hasText(locationQuery)) {
            String normalizedServiceQuery = normalizeQuery(serviceQuery);
            String normalizedLocationQuery = normalizeQuery(locationQuery);
            services = services.stream()
                    .filter(option -> matchesReferenceOption(option, normalizedServiceQuery, normalizedLocationQuery))
                    .toList();
        }
        trace("findServices", "service=discoverReferenceDataService.listServices resultCount=" + services.size()
                + " serviceQuery=" + serviceQuery
                + " locationQuery=" + locationQuery);
        return services;
    }

    List<PatientPortalDoctorSlotResponse> findSlots(
            String bookingReference,
            String publicDoctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            LocalDate date
    ) {
        if (StringUtils.hasText(clinicId) || StringUtils.hasText(tenantId)) {
            return patientPortalService.doctorSlots(bookingReference, publicDoctorId, clinicSlug, tenantId, clinicId, date);
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
        List<com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption> appointments =
                patientPortalService.careAiUpcomingAppointments();
        trace("CAREAI_TRACE_APPOINTMENTS_END",
                "repositoryPath=patientPortalService.careAiUpcomingAppointments"
                        + " linkedTenantIds=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::tenantId).distinct().toList()
                        + " appointmentIds=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::appointmentId).toList()
                        + " statuses=" + appointments.stream().map(com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption::status).toList()
                        + " count=" + appointments.size());
        return appointments;
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

    private boolean matchesReferenceOption(DiscoverReferenceOptionRecord option, String serviceQuery, String locationQuery) {
        if (option == null) {
            return false;
        }
        if (StringUtils.hasText(serviceQuery) && !containsNormalized(option.displayName(), serviceQuery) && !containsNormalized(option.code(), serviceQuery)) {
            return false;
        }
        if (StringUtils.hasText(locationQuery) && !containsNormalized(option.displayName(), locationQuery) && !containsNormalized(option.code(), locationQuery)) {
            return false;
        }
        return true;
    }

    private boolean containsNormalized(String value, String query) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(query)) {
            return false;
        }
        String normalizedValue = normalizeQuery(value);
        String normalizedQuery = normalizeQuery(query);
        return normalizedValue.contains(normalizedQuery) || normalizedQuery.contains(normalizedValue);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private PublicDoctorSummaryResponse toPublicDoctorSummary(PatientPortalDoctorResponse doctor) {
        return new PublicDoctorSummaryResponse(
                doctor.publicDoctorId(),
                null,
                null,
                doctor.doctorName(),
                null,
                null,
                doctor.specialization(),
                doctor.yearsOfExperience(),
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                "ONLINE_BOOKING",
                true
        );
    }

    private String doctorLookupKey(PublicDoctorSummaryResponse doctor) {
        if (doctor == null) {
            return "";
        }
        if (StringUtils.hasText(doctor.publicDoctorId())) {
            return doctor.publicDoctorId();
        }
        return normalizeQuery(String.join("|",
                nullToBlank(doctor.doctorDisplayName()),
                nullToBlank(doctor.speciality()),
                nullToBlank(doctor.clinicDisplayName())));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
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
