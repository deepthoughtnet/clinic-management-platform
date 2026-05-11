package com.deepthoughtnet.clinic.api.reports;

import com.deepthoughtnet.clinic.api.dashboard.dto.ClinicDashboardResponse;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    private final PermissionChecker permissionChecker;

    public ReportsController(
            ReportingFacade reportingFacade,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService,
            PermissionChecker permissionChecker
    ) {
        this.reportingFacade = reportingFacade;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping("/clinic-dashboard")
    @PreAuthorize("@permissionChecker.hasPermission('dashboard.read') or @permissionChecker.hasPermission('report.read')")
    public ClinicDashboardResponse clinicDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID doctorUserId
    ) {
        RequestContext context = RequestContextHolder.get();
        Set<String> roles = resolveRoles(context);
        UUID tenantId = context != null && context.tenantId() != null ? context.tenantId().value() : null;
        if (tenantId == null) {
            if (roles.contains(Roles.PLATFORM_ADMIN)) {
                return reportingFacade.platformDashboard(startDate != null ? startDate : date, endDate != null ? endDate : date);
            }
            throw new IllegalStateException("Tenant context is required for clinic dashboard.");
        }
        UUID effectiveDoctorUserId = doctorAssignmentSecurityService.effectiveDoctorUserId(doctorUserId);
        ClinicDashboardResponse response;
        if (startDate != null || endDate != null) {
            response = reportingFacade.clinicDashboard(tenantId, startDate, endDate, effectiveDoctorUserId);
        } else {
            response = reportingFacade.clinicDashboard(tenantId, date, effectiveDoctorUserId);
        }
        return reportingFacade.filterByRole(response, roles);
    }

    private Set<String> resolveRoles(RequestContext context) {
        Set<String> roles = new LinkedHashSet<>();
        String tenantRole = context == null ? null : normalizeRole(context.tenantRole());
        if (tenantRole != null) {
            // Tenant membership role is authoritative for tenant-scoped dashboard filtering.
            roles.add(tenantRole);
        }
        if (context != null) {
            if (context.tokenRoles() != null) {
                context.tokenRoles().forEach(role -> addRole(roles, role));
            }
        }

        // Fallback only when tenant role is missing; choose one primary dashboard role to avoid ambiguous multi-role filtering.
        if (tenantRole == null) {
            if (permissionChecker.hasRole(Roles.CLINIC_ADMIN)) {
                roles.add(Roles.CLINIC_ADMIN);
            } else if (permissionChecker.hasRole(Roles.AUDITOR)) {
                roles.add(Roles.AUDITOR);
            } else if (permissionChecker.hasRole(Roles.DOCTOR)) {
                roles.add(Roles.DOCTOR);
            } else if (permissionChecker.hasRole(Roles.RECEPTIONIST)) {
                roles.add(Roles.RECEPTIONIST);
            } else if (permissionChecker.hasRole(Roles.BILLING_USER)) {
                roles.add(Roles.BILLING_USER);
            }
        }

        if (permissionChecker.hasRole(Roles.PLATFORM_ADMIN)) {
            roles.add(Roles.PLATFORM_ADMIN);
        }
        return roles;
    }

    private void addRole(Set<String> roles, String role) {
        String normalized = normalizeRole(role);
        if (normalized != null) {
            roles.add(normalized);
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
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
