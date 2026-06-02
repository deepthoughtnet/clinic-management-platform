package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalMeResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal")
@PreAuthorize("@permissionChecker.hasRole('PATIENT')")
public class PatientPortalController {
    private final PatientPortalService patientPortalService;

    public PatientPortalController(PatientPortalService patientPortalService) {
        this.patientPortalService = patientPortalService;
    }

    @GetMapping("/me")
    public PatientPortalMeResponse me() {
        return patientPortalService.me();
    }

    @GetMapping("/appointments")
    public List<PatientPortalAppointmentResponse> appointments() {
        return patientPortalService.appointments();
    }

    @GetMapping("/prescriptions")
    public List<PatientPortalPrescriptionResponse> prescriptions() {
        return patientPortalService.prescriptions();
    }

    @GetMapping("/bills")
    public List<PatientPortalBillResponse> bills() {
        return patientPortalService.bills();
    }
}
