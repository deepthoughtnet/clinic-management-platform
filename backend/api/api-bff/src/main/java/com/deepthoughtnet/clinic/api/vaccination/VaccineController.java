package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineRequest;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vaccines")
public class VaccineController {
    private final VaccinationService vaccinationService;
    private final VaccineCsvService vaccineCsvService;
    private final VaccineAccessChecker vaccineAccessChecker;

    public VaccineController(VaccinationService vaccinationService, VaccineCsvService vaccineCsvService, VaccineAccessChecker vaccineAccessChecker) {
        this.vaccinationService = vaccinationService;
        this.vaccineCsvService = vaccineCsvService;
        this.vaccineAccessChecker = vaccineAccessChecker;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<VaccineResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listVaccines(tenantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public VaccineResponse create(@Valid @RequestBody VaccineRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.createVaccine(tenantId, toCommand(request), actorAppUserId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public VaccineResponse update(@PathVariable UUID id, @Valid @RequestBody VaccineRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.updateVaccine(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public VaccineResponse deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(vaccinationService.deactivateVaccine(tenantId, id, actorAppUserId));
    }

    @GetMapping(value = "/import-template", produces = "text/csv")
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public ResponseEntity<byte[]> importTemplate() {
        return csvResponse("vaccine-import-template.csv", vaccineCsvService.importTemplateCsv());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public ResponseEntity<byte[]> export() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return csvResponse("vaccine-master-export.csv", vaccineCsvService.exportCsv(tenantId));
    }

    @PostMapping(value = "/import-csv", consumes = "multipart/form-data")
    @PreAuthorize("@vaccineAccessChecker.canManageVaccineMaster()")
    public com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportResponse importCsv(@RequestParam("file") MultipartFile file) throws java.io.IOException {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return vaccineCsvService.importCsv(tenantId, file.getBytes(), actorAppUserId);
    }

    private VaccineUpsertCommand toCommand(VaccineRequest request) {
        return new VaccineUpsertCommand(
                request.vaccineName(),
                request.description(),
                request.manufacturer(),
                request.brandName(),
                request.vaccineGroup(),
                request.doseNumber(),
                request.route(),
                request.administrationSite(),
                request.storageTemperature(),
                request.ndcBarcode(),
                request.inventoryItemId(),
                request.inventoryItemCode(),
                request.stockTrackingEnabled(),
                request.scheduleType(),
                request.ageGroup(),
                request.minAgeDays(),
                request.recommendedAgeDays(),
                request.maxAgeDays(),
                request.gapDays(),
                request.recommendedGapDays(),
                request.boosterGapDays(),
                request.boosterRules(),
                request.recurring(),
                request.recurrenceDays(),
                request.recommendationPolicy(),
                request.catchUpPolicy(),
                request.catchUpMaxAgeDays(),
                request.applicableAgeGroup(),
                request.clinicalIndications(),
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
                record.manufacturer(),
                record.brandName(),
                record.vaccineGroup(),
                record.doseNumber(),
                record.route(),
                record.administrationSite(),
                record.storageTemperature(),
                record.ndcBarcode(),
                record.inventoryItemId() == null ? null : record.inventoryItemId().toString(),
                record.inventoryItemCode(),
                record.stockTrackingEnabled(),
                record.scheduleType(),
                record.ageGroup(),
                record.minAgeDays(),
                record.recommendedAgeDays(),
                record.maxAgeDays(),
                record.recommendedGapDays(),
                record.gapDays(),
                record.boosterGapDays(),
                record.boosterRules(),
                record.recurring(),
                record.recurrenceDays(),
                record.recommendationPolicy(),
                record.catchUpPolicy(),
                record.catchUpMaxAgeDays(),
                record.applicableAgeGroup(),
                record.clinicalIndications(),
                record.defaultPrice(),
                record.active(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String csv) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
