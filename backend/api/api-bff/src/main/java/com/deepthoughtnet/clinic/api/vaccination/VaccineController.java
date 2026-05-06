package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineRequest;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vaccines")
public class VaccineController {
    private final VaccinationService vaccinationService;

    public VaccineController(VaccinationService vaccinationService) {
        this.vaccinationService = vaccinationService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<VaccineResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listVaccines(tenantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public VaccineResponse create(@RequestBody VaccineRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.createVaccine(tenantId, toCommand(request), actorAppUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public VaccineResponse update(@PathVariable UUID id, @RequestBody VaccineRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.updateVaccine(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public VaccineResponse deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.deactivateVaccine(tenantId, id, actorAppUserId));
    }

    private VaccineUpsertCommand toCommand(VaccineRequest request) {
        return new VaccineUpsertCommand(
                request.vaccineName(),
                request.description(),
                request.ageGroup(),
                request.recommendedGapDays(),
                request.defaultPrice(),
                request.active()
        );
    }

    private VaccineResponse toResponse(VaccineMasterRecord record) {
        return new VaccineResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.vaccineName(),
                record.description(),
                record.ageGroup(),
                record.recommendedGapDays(),
                record.defaultPrice(),
                record.active(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
