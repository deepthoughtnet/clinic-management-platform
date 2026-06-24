package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "docker"})
@RestController
@RequestMapping("/api/patient-portal/debug")
@PreAuthorize("@permissionChecker.hasRole('PATIENT')")
public class PatientPortalDebugController {
    private final PatientPortalService patientPortalService;
    private final PatientPortalCareAiService patientPortalCareAiService;

    public PatientPortalDebugController(
            PatientPortalService patientPortalService,
            PatientPortalCareAiService patientPortalCareAiService
    ) {
        this.patientPortalService = patientPortalService;
        this.patientPortalCareAiService = patientPortalCareAiService;
    }

    @GetMapping("/careai-smoke")
    public Map<String, Object> careAiSmoke() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("patientId", patientPortalService.currentPatientId());
        response.put("patientMobile", patientPortalService.currentPatientMobile());
        response.put("conversationTenantId", RequestContextHolder.requireTenantId());
        response.put("tenantContextTenantId", RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantId().value());
        response.put("doctorLookup", buildDoctorLookup());
        response.put("appointmentLookup", buildAppointmentLookup());
        response.put("patientPortalAppointmentsPage", buildPatientPortalAppointmentsPage());
        response.put("patientPortalAppointments", buildPatientPortalAppointments());
        response.put("activeConversation", patientPortalCareAiService.debugActiveConversation());
        return response;
    }

    private Map<String, Object> buildDoctorLookup() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> doctors = patientPortalCareAiService.debugDoctorLookup("Vikas");
        result.put("query", "Vikas");
        result.put("resultCount", doctors.size());
        result.put("results", doctors);
        return result;
    }

    private Map<String, Object> buildAppointmentLookup() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> appointments = patientPortalCareAiService.debugAppointmentLookup();
        result.put("resultCount", appointments.size());
        result.put("results", appointments);
        return result;
    }

    private Map<String, Object> buildPatientPortalAppointments() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<PatientPortalCareAiAppointmentOption> appointments = patientPortalService.debugAppointments();
        result.put("resultCount", appointments.size());
        result.put("results", appointments.stream().map(appointment -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appointmentId", appointment.appointmentId());
            entry.put("doctorName", appointment.doctorName());
            entry.put("clinicName", appointment.clinicName());
            entry.put("tenantId", appointment.tenantId());
            entry.put("status", appointment.status());
            entry.put("dateTime", appointment.appointmentDate() == null ? null : appointment.appointmentDate().toString() + "T" + appointment.appointmentTime());
            return entry;
        }).toList());
        return result;
    }

    private Map<String, Object> buildPatientPortalAppointmentsPage() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<PatientPortalAppointmentResponse> appointments = patientPortalService.appointments();
        result.put("resultCount", appointments.size());
        result.put("results", appointments.stream().map(appointment -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appointmentDate", appointment.appointmentDate());
            entry.put("appointmentTime", appointment.appointmentTime());
            entry.put("doctorName", appointment.doctorName());
            entry.put("clinicName", appointment.clinicName());
            entry.put("source", appointment.source());
            entry.put("reason", appointment.reason());
            entry.put("status", appointment.status());
            return entry;
        }).toList());
        return result;
    }
}
