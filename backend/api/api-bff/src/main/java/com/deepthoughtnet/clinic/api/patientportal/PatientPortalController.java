package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageRequest;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiMessageResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiResetResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDashboardResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalMeResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient-portal")
@PreAuthorize("@permissionChecker.hasRole('PATIENT')")
public class PatientPortalController {
    private final PatientPortalService patientPortalService;
    private final PatientPortalCareAiService patientPortalCareAiService;

    public PatientPortalController(
            PatientPortalService patientPortalService,
            PatientPortalCareAiService patientPortalCareAiService
    ) {
        this.patientPortalService = patientPortalService;
        this.patientPortalCareAiService = patientPortalCareAiService;
    }

    @GetMapping("/dashboard")
    public PatientPortalDashboardResponse dashboard() {
        return patientPortalService.dashboard();
    }

    @GetMapping("/me")
    public PatientPortalMeResponse me() {
        return patientPortalService.me();
    }

    @GetMapping("/appointments")
    public List<PatientPortalAppointmentResponse> appointments() {
        return patientPortalService.appointments();
    }

    @GetMapping("/doctors")
    public List<PatientPortalDoctorResponse> doctors() {
        return patientPortalService.doctors();
    }

    @GetMapping("/doctors/{publicDoctorId}/slots")
    public List<PatientPortalDoctorSlotResponse> doctorSlots(
            @PathVariable String publicDoctorId,
            @RequestParam LocalDate date
    ) {
        return patientPortalService.doctorSlots(publicDoctorId, date);
    }

    @PostMapping("/appointments")
    public PatientPortalAppointmentConfirmationResponse bookAppointment(
            @RequestBody PatientPortalAppointmentBookingRequest request
    ) {
        return patientPortalService.bookAppointment(request);
    }

    @PostMapping("/careai/message")
    public PatientPortalCareAiMessageResponse careAiMessage(@RequestBody PatientPortalCareAiMessageRequest request) {
        return patientPortalCareAiService.message(request);
    }

    @PostMapping("/careai/reset")
    public PatientPortalCareAiResetResponse careAiReset() {
        return patientPortalCareAiService.reset();
    }

    @GetMapping("/prescriptions")
    public List<PatientPortalPrescriptionResponse> prescriptions() {
        return patientPortalService.prescriptions();
    }

    @GetMapping("/bills")
    public List<PatientPortalBillResponse> bills() {
        return patientPortalService.bills();
    }

    @GetMapping(value = "/prescriptions/{prescriptionNumber}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> prescriptionPdf(@PathVariable String prescriptionNumber) {
        var pdf = patientPortalService.prescriptionPdf(prescriptionNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @GetMapping(value = "/bills/{billNumber}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> billPdf(@PathVariable String billNumber) {
        var pdf = patientPortalService.billPdf(billNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    @GetMapping(value = "/bills/{billNumber}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> latestReceiptPdf(@PathVariable String billNumber) {
        var pdf = patientPortalService.latestReceiptPdf(billNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }
}
