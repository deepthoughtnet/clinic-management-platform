package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationRequest;
import com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationCommand;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients/{patientId}/vaccinations")
public class PatientVaccinationController {
    private final VaccinationService vaccinationService;

    public PatientVaccinationController(VaccinationService vaccinationService) {
        this.vaccinationService = vaccinationService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<PatientVaccinationResponse> list(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listByPatient(tenantId, patientId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public PatientVaccinationResponse create(@PathVariable UUID patientId, @RequestBody PatientVaccinationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.recordVaccination(tenantId, patientId, toCommand(request), actorAppUserId));
    }

    private PatientVaccinationCommand toCommand(PatientVaccinationRequest request) {
        return new PatientVaccinationCommand(
                request.vaccineId(),
                request.doseNumber(),
                request.givenDate(),
                request.nextDueDate(),
                request.batchNumber(),
                request.notes(),
                request.administeredByUserId(),
                request.billId(),
                request.addToBill(),
                request.billItemUnitPrice()
        );
    }

    private PatientVaccinationResponse toResponse(PatientVaccinationRecord record) {
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
