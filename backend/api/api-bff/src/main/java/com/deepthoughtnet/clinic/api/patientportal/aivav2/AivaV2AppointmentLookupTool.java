package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import com.deepthoughtnet.clinic.api.patientportal.PatientPortalService;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentLookupFilter;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentLookupResult;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2Models.AppointmentSummary;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAuthorizedClinicResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AivaV2AppointmentLookupTool {
    private static final Logger log = LoggerFactory.getLogger(AivaV2AppointmentLookupTool.class);
    private final PatientPortalService patientPortalService;

    AivaV2AppointmentLookupTool(PatientPortalService patientPortalService) {
        this.patientPortalService = patientPortalService;
    }

    AppointmentLookupResult lookup(AppointmentLookupFilter filter, LocalDate date) {
        return lookup(filter, date, null, null);
    }

    AppointmentLookupResult lookup(AppointmentLookupFilter filter, LocalDate date,
                                   String conversationId, String turnId) {
        try {
            List<PatientPortalCareAiAppointmentOption> merged = patientPortalService
                    .careAiUpcomingAppointmentsAcrossAuthorizedClinics();
            Map<UUID, PatientPortalCareAiAppointmentOption> deduplicated = new LinkedHashMap<>();
            merged.forEach(appointment -> {
                if (appointment != null && appointment.appointmentId() != null) {
                    deduplicated.putIfAbsent(appointment.appointmentId(), appointment);
                }
            });
            List<PatientPortalCareAiAppointmentOption> authorized = List.copyOf(deduplicated.values());
            String doctor = value(filter.doctorText());
            String status = value(filter.status());
            String clinic = value(filter.clinicText());
            Set<UUID> clinicTenantIds = resolveClinicTenantIds(clinic);
            List<PatientPortalCareAiAppointmentOption> afterDoctor = authorized.stream()
                    .filter(appointment -> !StringUtils.hasText(doctor) || containsName(appointment.doctorName(), doctor))
                    .toList();
            List<PatientPortalCareAiAppointmentOption> afterDate = afterDoctor.stream()
                    .filter(appointment -> date == null || date.equals(appointment.appointmentDate()))
                    .toList();
            List<PatientPortalCareAiAppointmentOption> afterStatus = afterDate.stream()
                    .filter(appointment -> !isStatusFilter(status)
                            || status.equalsIgnoreCase(appointment.status()))
                    .toList();
            List<PatientPortalCareAiAppointmentOption> afterClinic = afterStatus.stream()
                    .filter(appointment -> !StringUtils.hasText(clinic) || clinicTenantIds.contains(appointment.tenantId()))
                    .toList();
            List<AppointmentSummary> matches = afterClinic.stream()
                    .map(this::summary)
                    .limit(filter.nextOnly() ? 1 : Long.MAX_VALUE)
                    .toList();
            log.info("AIVA_V2_LOOKUP_TRACE conversationId={} turnId={} authorizedAppointmentCount={} "
                            + "authorizedClinicCount={} queriedClinicCount={} clinicResultCounts={} mergedAppointmentCount={} "
                            + "deduplicatedAppointmentCount={} doctorFilterPresent={} dateFilterPresent={} "
                            + "statusFilterPresent={} clinicFilterPresent={} nextOnly={} "
                            + "afterDoctorFilterCount={} afterDateFilterCount={} afterStatusFilterCount={} "
                            + "finalAppointmentCount={} resultStatus={} currentCareClinicPresent={} "
                            + "authorizedCareClinicCount={} activeTenantContextPresent={} networkLookupMode={} executionClinicSource={}",
                    conversationId, turnId, authorized.size(), distinctClinics(merged).size(),
                    distinctClinics(merged).size(), clinicCounts(merged).values().stream().toList(), merged.size(),
                    authorized.size(), StringUtils.hasText(doctor), date != null, isStatusFilter(status),
                    StringUtils.hasText(clinic), filter.nextOnly(), afterDoctor.size(), afterDate.size(),
                    afterStatus.size(), matches.size(), matches.isEmpty()
                            ? AivaV2Models.AppointmentLookupStatus.NONE : AivaV2Models.AppointmentLookupStatus.FOUND,
                    RequestContextHolder.get() != null && RequestContextHolder.get().tenantId() != null,
                    distinctClinics(merged).size(),
                    RequestContextHolder.get() != null && RequestContextHolder.get().tenantId() != null,
                    "AUTHORIZED_CARE_NETWORK", "APPOINTMENT_RECORD");
            return AppointmentLookupResult.found(matches);
        } catch (RuntimeException ex) {
            log.info("AIVA_V2_LOOKUP_TRACE conversationId={} turnId={} resultStatus=FAILED", conversationId, turnId);
            return AppointmentLookupResult.failed("Appointment information could not be retrieved.");
        }
    }

    private AppointmentSummary summary(PatientPortalCareAiAppointmentOption appointment) {
        return new AppointmentSummary(
                appointment.appointmentId() == null ? null : appointment.appointmentId().toString(),
                appointment.doctorName(), appointment.clinicName(), appointment.appointmentDate(),
                appointment.appointmentTime(), appointment.status(), appointment.reason(),
                appointment.doctorUserId() == null ? null : appointment.doctorUserId().toString(),
                appointment.clinicId(), appointment.tenantId() == null ? null : appointment.tenantId().toString(),
                appointment.clinicSlug());
    }

    private boolean containsName(String name, String query) {
        return normalize(name).contains(normalize(query));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim()
                .replaceFirst("^(doctor|dr|doc)\\s+", "");
    }

    private String value(AivaV2Models.ValuePatch patch) {
        return patch != null && patch.mode() == AivaV2Models.PatchMode.SET ? patch.value() : null;
    }

    private boolean isStatusFilter(String status) {
        return StringUtils.hasText(status) && !"NONE".equalsIgnoreCase(status);
    }

    private Set<UUID> resolveClinicTenantIds(String clinicQuery) {
        if (!StringUtils.hasText(clinicQuery)) return Set.of();
        List<PatientPortalAuthorizedClinicResponse> clinics = patientPortalService.clinics();
        return clinics.stream()
                .filter(clinic -> clinic != null && clinic.active() && clinic.tenantId() != null
                        && (containsName(clinic.clinicName(), clinicQuery)
                        || containsName(clinic.patientDisplayName(), clinicQuery)
                        || containsName(clinic.tenantCode(), clinicQuery)))
                .map(PatientPortalAuthorizedClinicResponse::tenantId)
                .collect(Collectors.toSet());
    }

    private Map<UUID, Integer> clinicCounts(List<PatientPortalCareAiAppointmentOption> appointments) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        appointments.stream()
                .filter(appointment -> appointment != null && appointment.tenantId() != null)
                .forEach(appointment -> counts.merge(appointment.tenantId(), 1, Integer::sum));
        return counts;
    }

    private java.util.Set<UUID> distinctClinics(List<PatientPortalCareAiAppointmentOption> appointments) {
        return appointments.stream()
                .filter(appointment -> appointment != null && appointment.tenantId() != null)
                .map(PatientPortalCareAiAppointmentOption::tenantId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
