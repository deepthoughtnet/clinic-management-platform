package com.deepthoughtnet.clinic.api.platform.careaccess;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalAccessRequestResponse;
import com.deepthoughtnet.clinic.patient.service.PatientPortalAccessRequestService;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/care-access-requests")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class CareAccessRequestController {
    private final PatientPortalAccessRequestService accessRequestService;

    public CareAccessRequestController(PatientPortalAccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @GetMapping
    public List<PatientPortalAccessRequestResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        return accessRequestService.list(status, q).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PatientPortalAccessRequestResponse get(@PathVariable UUID id) {
        return accessRequestService.find(id).map(this::toResponse).orElseThrow(() -> new IllegalArgumentException("Access request not found"));
    }

    @PostMapping("/{id}/approve")
    public PatientPortalAccessRequestResponse approve(@PathVariable UUID id, @RequestBody(required = false) CareAccessRequestDecisionRequest request) {
        return toResponse(accessRequestService.approve(id, actorAppUserId(), reviewedByDisplayName(), request == null ? null : request.reason(), request == null ? null : request.patientId()));
    }

    @PostMapping("/{id}/reject")
    public PatientPortalAccessRequestResponse reject(@PathVariable UUID id, @RequestBody(required = false) CareAccessRequestDecisionRequest request) {
        return toResponse(accessRequestService.reject(id, actorAppUserId(), reviewedByDisplayName(), request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/revoke")
    public PatientPortalAccessRequestResponse revoke(@PathVariable UUID id, @RequestBody(required = false) CareAccessRequestDecisionRequest request) {
        return toResponse(accessRequestService.revoke(id, actorAppUserId(), reviewedByDisplayName(), request == null ? null : request.reason()));
    }

    private UUID actorAppUserId() {
        return RequestContextHolder.require().appUserId();
    }

    private String reviewedByDisplayName() {
        var ctx = RequestContextHolder.require();
        if (ctx.actorDisplayName() != null && !ctx.actorDisplayName().isBlank()) {
            return ctx.actorDisplayName();
        }
        if (ctx.keycloakSub() != null && !ctx.keycloakSub().isBlank()) {
            return ctx.keycloakSub();
        }
        return "Platform Admin";
    }

    private PatientPortalAccessRequestResponse toResponse(PatientPortalAccessRequestRecord record) {
        return new PatientPortalAccessRequestResponse(
                record.id(),
                record.tenantId(),
                record.tenantCode(),
                record.tenantName(),
                record.requestType().name(),
                record.fullName(),
                record.mobile(),
                record.email(),
                record.note(),
                record.status().name(),
                record.rejectionReason(),
                record.linkedPatientId(),
                record.linkedPatientDisplayName(),
                record.reviewedBy(),
                record.reviewedByDisplayName(),
                record.temporaryAccessCode(),
                record.requestedAt(),
                record.reviewedAt(),
                record.approvedAt(),
                record.activatedAt(),
                record.revokedAt(),
                record.accessCodeExpiresAt(),
                record.createdAt(),
                record.updatedAt(),
                record.version()
        );
    }

    public record CareAccessRequestDecisionRequest(String reason, UUID patientId) {}
}
