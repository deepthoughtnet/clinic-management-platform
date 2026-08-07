package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ReconciliationResult;
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
@RequestMapping("/api/platform/provider-connections")
@PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.view')")
public class ProviderConnectionsController {
    private final ProviderConnectionsService service;

    public ProviderConnectionsController(ProviderConnectionsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ProviderConnectionsOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/public-profiles")
    public List<ProviderConnectionsPublicProfileResponse> publicProfiles(
            @RequestParam(required = false) PublicProfileType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city
    ) {
        return service.publicProfiles(type == null ? PublicProfileType.CLINIC : type, q, city);
    }

    @GetMapping("/public-practices")
    public List<ProviderConnectionsPublicProfileResponse> publicPractices(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city
    ) {
        return service.publicPractices(q, city);
    }

    @GetMapping("/public-profile-lifecycle")
    public List<ProviderConnectionsLifecycleResponse> publicProfileLifecycle(
            @RequestParam(required = false) PublicProfileType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city
    ) {
        return service.publicProfileLifecycle(type == null ? PublicProfileType.CLINIC : type, q, city);
    }

    @GetMapping("/platform-entities")
    public List<ProviderConnectionsPlatformEntityResponse> platformEntities(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q
    ) {
        return service.platformEntities(type, q);
    }

    @GetMapping("/links")
    public List<ProviderConnectionsLinkResponse> links(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        return service.links(type, status, q);
    }

    @GetMapping("/links/{linkId}")
    public ProviderConnectionsLinkDetailResponse linkDetail(@PathVariable UUID linkId) {
        return service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found"));
    }

    @GetMapping("/links/{linkId}/audit")
    public List<ProviderConnectionsAuditResponse> audit(@PathVariable UUID linkId) {
        return service.audit(linkId);
    }

    @GetMapping("/audit/events")
    public List<ProviderConnectionsAuditResponse> auditEvents(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String tenantReference,
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String q
    ) {
        return service.auditEvents(action, tenantReference, providerType, result, q);
    }

    @GetMapping("/suggestions")
    public List<ProviderConnectionsSuggestionResponse> suggestions(@RequestParam(required = false) String q) {
        return service.suggestions(q);
    }

    @GetMapping("/conflicts")
    public List<ProviderConnectionsConflictResponse> conflicts() {
        return service.conflicts();
    }

    @GetMapping("/ownerships")
    public List<ProviderConnectionsOwnershipResponse> ownerships() {
        return service.ownerships();
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reject')")
    public ProviderConnectionsSuggestionResponse rejectSuggestion(
            @PathVariable String suggestionId,
            @RequestBody(required = false) ProviderConnectionsSuggestionDecisionRequest request
    ) {
        return service.rejectSuggestion(suggestionId, request == null ? null : request.reason());
    }

    @PostMapping("/ownerships/{ownershipId}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ProviderConnectionsOwnershipResponse approveOwnership(
            @PathVariable UUID ownershipId,
            @RequestBody(required = false) ProviderConnectionsOwnershipDecisionRequest request
    ) {
        return service.approveOwnership(ownershipId, request == null ? null : request.reason());
    }

    @PostMapping("/ownerships/{ownershipId}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reject')")
    public ProviderConnectionsOwnershipResponse rejectOwnership(
            @PathVariable UUID ownershipId,
            @RequestBody(required = false) ProviderConnectionsOwnershipDecisionRequest request
    ) {
        return service.rejectOwnership(ownershipId, request == null ? null : request.reason());
    }

    @PostMapping("/ownerships/{ownershipId}/dispute")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.identity_override')")
    public ProviderConnectionsOwnershipResponse disputeOwnership(
            @PathVariable UUID ownershipId,
            @RequestBody(required = false) ProviderConnectionsOwnershipDecisionRequest request
    ) {
        return service.disputeOwnership(ownershipId, request == null ? null : request.reason());
    }

    @PostMapping("/ownerships/{ownershipId}/revoke")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.unlink')")
    public ProviderConnectionsOwnershipResponse revokeOwnership(
            @PathVariable UUID ownershipId,
            @RequestBody(required = false) ProviderConnectionsOwnershipDecisionRequest request
    ) {
        return service.revokeOwnership(ownershipId, request == null ? null : request.reason());
    }

    @PostMapping("/links/propose")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.propose')")
    public ProviderConnectionsLinkResponse propose(@RequestBody ProviderConnectionsLinkProposalRequest request) {
        String actorReference = actorReference();
        return request.publicProfileType() == PublicProfileType.DOCTOR
                ? service.proposeDoctorPracticeLink(request, actorReference)
                : service.proposeClinicLink(request, actorReference);
    }

    @PostMapping("/links/{linkId}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ProviderConnectionsLinkResponse approve(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.approveDoctorPracticeLink(linkId, reason, actorReference)
                : service.approveClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/activate")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ProviderConnectionsLinkResponse activate(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.activateDoctorPracticeLink(linkId, reason, actorReference)
                : service.activateClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/unlink")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.unlink')")
    public ProviderConnectionsLinkResponse unlink(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.unlinkDoctorPracticeLink(linkId, reason, actorReference)
                : service.unlinkClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reject')")
    public ProviderConnectionsLinkResponse reject(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.rejectDoctorPracticeLink(linkId, reason, actorReference)
                : service.rejectClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/suspend")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.unlink')")
    public ProviderConnectionsLinkResponse suspend(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.suspendDoctorPracticeLink(linkId, reason, actorReference)
                : service.suspendClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/resume")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ProviderConnectionsLinkResponse resume(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.resumeDoctorPracticeLink(linkId, reason, actorReference)
                : service.resumeClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/links/{linkId}/relink")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.propose')")
    public ProviderConnectionsLinkResponse relink(@PathVariable UUID linkId, @RequestBody(required = false) ProviderConnectionsLinkUpdateRequest request) {
        String reason = request == null ? null : request.reason();
        String actorReference = actorReference();
        ProviderConnectionsLinkResponse link = service.linkDetail(linkId).orElseThrow(() -> new IllegalArgumentException("Link not found")).link();
        return link.publicProfileType() == PublicProfileType.DOCTOR
                ? service.relinkDoctorPracticeLink(linkId, reason, actorReference)
                : service.relinkClinicLink(linkId, reason, actorReference);
    }

    @PostMapping("/reconcile")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reconcile')")
    public ReconciliationResult reconcile(@RequestBody ProviderConnectionsReconcileRequest request) {
        return service.reconcile(request == null ? null : request.linkId());
    }

    private String actorReference() {
        var context = RequestContextHolder.require();
        if (context.appUserId() != null) {
            return context.appUserId().toString();
        }
        if (context.keycloakSub() != null && !context.keycloakSub().isBlank()) {
            return context.keycloakSub();
        }
        if (context.actorEmail() != null && !context.actorEmail().isBlank()) {
            return context.actorEmail();
        }
        throw new IllegalStateException("Authenticated actor reference is unavailable");
    }
}
