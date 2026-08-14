package com.deepthoughtnet.clinic.api.platform.provideraccess;

import com.deepthoughtnet.clinic.api.discover.provider.access.ProviderAccessModels;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestRecord;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestService;
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
@RequestMapping("/api/platform/provider-access-requests")
@PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
public class ProviderAccessRequestController {
    private final ProviderPortalAccessRequestService accessRequestService;

    public ProviderAccessRequestController(ProviderPortalAccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @GetMapping
    public List<ProviderAccessModels.ProviderAccessRequestResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        return accessRequestService.list(status, q).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ProviderAccessModels.ProviderAccessRequestResponse get(@PathVariable UUID id) {
        return accessRequestService.find(id).map(this::toResponse).orElseThrow(() -> new IllegalArgumentException("Access request not found"));
    }

    @PostMapping("/{id}/approve")
    public ProviderAccessModels.ProviderAccessRequestResponse approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ProviderAccessModels.ProviderAccessRequestDecisionRequest request
    ) {
        return toResponse(accessRequestService.approve(
                id,
                actorAppUserId(),
                reviewedByDisplayName(),
                request == null ? null : request.reason(),
                request == null ? null : request.providerApplicationReference()
        ));
    }

    @PostMapping("/{id}/reject")
    public ProviderAccessModels.ProviderAccessRequestResponse reject(
            @PathVariable UUID id,
            @RequestBody(required = false) ProviderAccessModels.ProviderAccessRequestDecisionRequest request
    ) {
        return toResponse(accessRequestService.reject(id, actorAppUserId(), reviewedByDisplayName(), request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/revoke")
    public ProviderAccessModels.ProviderAccessRequestResponse revoke(
            @PathVariable UUID id,
            @RequestBody(required = false) ProviderAccessModels.ProviderAccessRequestDecisionRequest request
    ) {
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

    private ProviderAccessModels.ProviderAccessRequestResponse toResponse(ProviderPortalAccessRequestRecord record) {
        return new ProviderAccessModels.ProviderAccessRequestResponse(
                record.id(),
                record.providerType(),
                record.fullName(),
                record.email(),
                record.mobile(),
                record.providerApplicationReference(),
                record.note(),
                record.status().name(),
                record.rejectionReason(),
                record.linkedProviderAccountId(),
                record.linkedProviderAccountDisplayName(),
                record.linkedProviderApplicationReference(),
                record.reviewedBy(),
                record.reviewedByDisplayName(),
                record.temporaryAccessCode(),
                record.requestedAt(),
                record.reviewedAt(),
                record.approvedAt(),
                record.revokedAt(),
                record.accessCodeIssuedAt(),
                record.accessCodeExpiresAt(),
                record.createdAt(),
                record.updatedAt(),
                record.version()
        );
    }
}
