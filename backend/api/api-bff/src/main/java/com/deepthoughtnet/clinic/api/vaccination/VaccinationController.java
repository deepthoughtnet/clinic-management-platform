package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationResponse;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccinationRecommendationItemResponse;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccinationRecommendationResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationRecommendationService;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vaccinations")
public class VaccinationController {
    private final VaccinationService vaccinationService;
    private final VaccinationRecommendationService vaccinationRecommendationService;

    public VaccinationController(VaccinationService vaccinationService, VaccinationRecommendationService vaccinationRecommendationService) {
        this.vaccinationService = vaccinationService;
        this.vaccinationRecommendationService = vaccinationRecommendationService;
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

    @GetMapping("/history")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public List<PatientVaccinationResponse> history(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID vaccineId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String dueStatus
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return vaccinationService.listHistory(tenantId).stream()
                .filter(row -> patientId == null || patientId.toString().equals(row.patientId()))
                .filter(row -> vaccineId == null || vaccineId.toString().equals(row.vaccineId()))
                .filter(row -> fromDate == null || (row.givenDate() != null && !row.givenDate().isBefore(fromDate)))
                .filter(row -> toDate == null || (row.givenDate() != null && !row.givenDate().isAfter(toDate)))
                .filter(row -> matchesDueStatus(row, dueStatus))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/recommendations")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public VaccinationRecommendationResponse recommendations(
            @RequestParam UUID patientId,
            @RequestParam(required = false) String scheduleType
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var summary = vaccinationRecommendationService.recommend(tenantId, patientId, scheduleType);
        return new VaccinationRecommendationResponse(
                summary.patientId(),
                summary.scheduleType(),
                summary.recommendedToday().stream().map(VaccinationRecommendationItemResponse::from).toList(),
                summary.overdue().stream().map(VaccinationRecommendationItemResponse::from).toList(),
                summary.upcoming().stream().map(VaccinationRecommendationItemResponse::from).toList(),
                summary.completed().stream().map(VaccinationRecommendationItemResponse::from).toList(),
                summary.optionalRiskBased().stream().map(VaccinationRecommendationItemResponse::from).toList(),
                summary.notApplicable().stream().map(VaccinationRecommendationItemResponse::from).toList()
        );
    }

    private PatientVaccinationResponse toResponse(com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord record) {
        return new PatientVaccinationResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.patientMobile(),
                record.patientAgeYears(),
                record.patientGender(),
                record.patientAllergies(),
                record.vaccineId() == null ? null : record.vaccineId().toString(),
                record.vaccineName(),
                record.source(),
                record.externalPlace(),
                record.proofDocumentId() == null ? null : record.proofDocumentId().toString(),
                record.verifiedStatus(),
                record.verifiedByUserId() == null ? null : record.verifiedByUserId().toString(),
                record.verifiedByUserName(),
                record.verifiedAt(),
                record.doseNumber(),
                record.givenDate(),
                record.nextDueDate(),
                record.batchNumber(),
                record.notes(),
                record.administeredByUserId() == null ? null : record.administeredByUserId().toString(),
                record.administeredByUserName(),
                record.createdByUserId() == null ? null : record.createdByUserId().toString(),
                record.createdByUserName(),
                record.updatedByUserId() == null ? null : record.updatedByUserId().toString(),
                record.updatedByUserName(),
                record.updatedAt(),
                record.billId() == null ? null : record.billId().toString(),
                record.billNumber(),
                record.billStatus(),
                record.billLineId() == null ? null : record.billLineId().toString(),
                record.inventoryTransactionId() == null ? null : record.inventoryTransactionId().toString(),
                record.inventoryStockBatchId() == null ? null : record.inventoryStockBatchId().toString(),
                record.inventoryBatchNumber(),
                record.inventoryBatchManufacturer(),
                record.inventoryBatchExpiryDate(),
                record.reminderNotificationId() == null ? null : record.reminderNotificationId().toString(),
                record.reminderQueuedAt(),
                record.reminderStatus(),
                record.workflowWarnings(),
                record.recordedByUserId() == null ? null : record.recordedByUserId().toString(),
                record.recordedByUserName(),
                record.createdAt()
        );
    }

    private boolean matchesDueStatus(com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord row, String dueStatus) {
        if (dueStatus == null || dueStatus.isBlank() || "ALL".equalsIgnoreCase(dueStatus)) {
            return true;
        }
        if (row.nextDueDate() == null) {
            return "NOT_DUE".equalsIgnoreCase(dueStatus);
        }
        LocalDate today = LocalDate.now();
        return switch (dueStatus.toUpperCase()) {
            case "OVERDUE" -> row.nextDueDate().isBefore(today);
            case "DUE" -> !row.nextDueDate().isBefore(today);
            case "NOT_DUE" -> false;
            default -> true;
        };
    }
}
