package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vaccinations")
public class VaccinationController {
    private final VaccinationService vaccinationService;

    public VaccinationController(VaccinationService vaccinationService) {
        this.vaccinationService = vaccinationService;
    }

    @GetMapping("/due")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<PatientVaccinationResponse> due() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listDue(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/overdue")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<PatientVaccinationResponse> overdue() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listOverdue(tenantId).stream().map(this::toResponse).toList();
    }

    private PatientVaccinationResponse toResponse(com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord record) {
        return new PatientVaccinationResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.vaccineId() == null ? null : record.vaccineId().toString(),
                record.vaccineName(),
                record.doseNumber(),
                record.givenDate(),
                record.nextDueDate(),
                record.batchNumber(),
                record.notes(),
                record.administeredByUserId() == null ? null : record.administeredByUserId().toString(),
                record.administeredByUserName(),
                record.createdAt()
        );
    }
}
