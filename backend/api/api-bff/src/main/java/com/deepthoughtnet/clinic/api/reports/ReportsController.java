package com.deepthoughtnet.clinic.api.reports;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {
    private final ReportingFacade reportingFacade;

    public ReportsController(ReportingFacade reportingFacade) {
        this.reportingFacade = reportingFacade;
    }

    @GetMapping("/patient-visits")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> patientVisits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) UUID patientId
    ) {
        return reportingFacade.patientVisits(RequestContextHolder.requireTenantId(), from, to, doctorUserId, patientId);
    }

    @GetMapping("/doctor-consultations")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> doctorConsultations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String status
    ) {
        return reportingFacade.doctorConsultations(RequestContextHolder.requireTenantId(), from, to, doctorUserId, patientId, status);
    }

    @GetMapping("/revenue")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) UUID patientId
    ) {
        return reportingFacade.revenue(RequestContextHolder.requireTenantId(), from, to, doctorUserId, patientId);
    }

    @GetMapping("/payment-modes")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> paymentModes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportingFacade.paymentModes(RequestContextHolder.requireTenantId(), from, to);
    }

    @GetMapping("/pending-dues")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> pendingDues() {
        return reportingFacade.pendingDues(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/vaccinations-due")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> vaccinationsDue() {
        return reportingFacade.vaccinationsDue(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/follow-ups")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> followUps() {
        return reportingFacade.followUps(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> lowStock() {
        return reportingFacade.lowStock(RequestContextHolder.requireTenantId());
    }

    @GetMapping("/prescriptions")
    @PreAuthorize("@permissionChecker.hasPermission('report.read')")
    public List<Map<String, Object>> prescriptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) UUID patientId
    ) {
        return reportingFacade.prescriptions(RequestContextHolder.requireTenantId(), from, to, doctorUserId, patientId);
    }
}
