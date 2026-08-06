package com.deepthoughtnet.clinic.api.clinic;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DiscoverPresenceController {
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderPublicProfileDraftService draftService;
    private final ProviderOwnershipService providerOwnershipService;
    private final ProviderLinkingService providerLinkingService;

    public DiscoverPresenceController(
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            ProviderPublicProfileService publicProfileService,
            ProviderPublicProfileDraftService draftService,
            ProviderOwnershipService providerOwnershipService,
            ProviderLinkingService providerLinkingService
    ) {
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.publicProfileService = publicProfileService;
        this.draftService = draftService;
        this.providerOwnershipService = providerOwnershipService;
        this.providerLinkingService = providerLinkingService;
    }

    @GetMapping("/clinic/discover-presence")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.read')")
    public ClinicDiscoverPresenceResponse clinicPresence() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic profile not found"));
        PublicProfileLifecycleRecord lifecycle = publicProfileService.findLifecycleByProviderId(clinic.id()).orElse(null);
        PublicProfileDraftWorkspaceRecord draft = draftService.findDraft(tenantId.toString()).orElse(null);
        OwnershipRecord ownership = providerOwnershipService.findLatestOwnership(tenantId.toString()).orElse(null);
        ClaimIntentRecord claimIntent = providerOwnershipService.findActiveClaimIntent(tenantId.toString()).orElse(null);
        Optional<BookingTargetResolution> link = providerLinkingService.resolveBookingTarget(new PublicProviderReference(tenantId.toString(), null));
        BookingCapability bookingCapability = link.map(com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution::bookingCapability).orElse(BookingCapability.NOT_AVAILABLE);
        PlatformConnectionStatus connectionStatus = link.map(com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution::connectionStatus).orElse(PlatformConnectionStatus.NOT_CONNECTED);
        String maskedProviderMobile = ownership == null ? null : providerOwnershipService.maskedProviderMobile(ownership.providerAccountId()).orElse(null);
        OffsetDateTime publicProfileSynchronizedAt = lifecycle == null ? null : lifecycle.projectedAt();
        return new ClinicDiscoverPresenceResponse(
                tenantId,
                clinic.displayName(),
                clinic.city(),
                clinic.addressLine1(),
                clinic.publicListingEnabled() ? "ENABLED" : "DISABLED",
                ownership == null ? (claimIntent != null ? "CLAIM_PENDING" : "UNCLAIMED") : ownership.status().name(),
                ownership == null ? null : ownership.id(),
                maskedProviderMobile,
                lifecycle == null ? "UNPUBLISHED" : lifecycle.publicationStatus(),
                connectionStatus.name(),
                bookingCapability.name(),
                ownership == null ? null : ownership.updatedAt(),
                lifecycle == null ? null : lifecycle.publishedAt(),
                publicProfileSynchronizedAt,
                claimIntent == null ? null : claimIntent.connectionReference(),
                providerOwnershipService.presenceAllowedActions(ownership, claimIntent),
                publicProfileSynchronizedAt,
                lifecycle == null ? null : lifecycle.publicPath(),
                draft == null ? null : draft.draftReference(),
                draft == null ? null : draft.contentStatus(),
                draft == null ? null : draft.readinessStatus(),
                draft == null ? 0 : draft.completenessPercentage(),
                draft == null ? null : draft.lastSavedAt(),
                draft == null ? List.of() : draft.allowedActions()
        );
    }

    @PostMapping("/clinic/discover-presence/claim-intents")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.update')")
    public ClinicDiscoverPresenceClaimIntentResponse createClinicClaimIntent() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic profile not found"));
        ClaimIntentRecord intent = providerOwnershipService.createClinicClaimIntent(tenantId, actorAppUserId, "Healthcare initiated connection");
        return new ClinicDiscoverPresenceClaimIntentResponse(
                intent.connectionReference(),
                intent.status().name(),
                intent.expiresAt(),
                "/provider/workspace?connectionReference=" + intent.connectionReference(),
                clinic.displayName(),
                clinic.city(),
                clinic.addressLine1()
        );
    }

    @GetMapping("/doctors/{doctorUserId}/discover-presence")
    @PreAuthorize("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')")
    public ClinicDiscoverPresenceResponse doctorPresence(@PathVariable UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord doctor = tenantUserManagementService.list(tenantId).stream()
                .filter(user -> doctorUserId.equals(user.appUserId()))
                .filter(user -> "DOCTOR".equalsIgnoreCase(user.membershipRole()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
        DoctorProfileRecord profile = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId).orElse(null);
        PublicProfileLifecycleRecord lifecycle = profile == null ? null : publicProfileService.findLifecycleByProviderId(doctorUserId).orElse(null);
        PublicProfileDraftWorkspaceRecord draft = draftService.findDraft(doctorUserId.toString()).orElse(null);
        OwnershipRecord ownership = providerOwnershipService.findLatestOwnership(doctorUserId.toString()).orElse(null);
        ClaimIntentRecord claimIntent = providerOwnershipService.findActiveClaimIntent(doctorUserId.toString()).orElse(null);
        Optional<BookingTargetResolution> link = providerLinkingService.resolveBookingTarget(new PublicProviderReference(doctorUserId.toString(), profile == null ? null : profile.slug()));
        BookingCapability bookingCapability = link.map(com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution::bookingCapability).orElse(BookingCapability.NOT_AVAILABLE);
        PlatformConnectionStatus connectionStatus = link.map(com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution::connectionStatus).orElse(PlatformConnectionStatus.NOT_CONNECTED);
        String maskedProviderMobile = ownership == null ? null : providerOwnershipService.maskedProviderMobile(ownership.providerAccountId()).orElse(null);
        OffsetDateTime publicProfileSynchronizedAt = lifecycle == null ? null : lifecycle.projectedAt();
        return new ClinicDiscoverPresenceResponse(
                tenantId,
                doctor.displayName(),
                clinicProfileService.findByTenantId(tenantId).map(ClinicProfileRecord::city).orElse(null),
                profile == null ? null : profile.consultationRoom(),
                profile != null && profile.publicListingEnabled() ? "ENABLED" : "DISABLED",
                ownership == null ? (claimIntent != null ? "CLAIM_PENDING" : "UNCLAIMED") : ownership.status().name(),
                ownership == null ? null : ownership.id(),
                maskedProviderMobile,
                lifecycle == null ? "UNPUBLISHED" : lifecycle.publicationStatus(),
                connectionStatus.name(),
                bookingCapability.name(),
                ownership == null ? null : ownership.updatedAt(),
                lifecycle == null ? null : lifecycle.publishedAt(),
                publicProfileSynchronizedAt,
                claimIntent == null ? null : claimIntent.connectionReference(),
                providerOwnershipService.presenceAllowedActions(ownership, claimIntent),
                publicProfileSynchronizedAt,
                lifecycle == null ? null : lifecycle.publicPath(),
                draft == null ? null : draft.draftReference(),
                draft == null ? null : draft.contentStatus(),
                draft == null ? null : draft.readinessStatus(),
                draft == null ? 0 : draft.completenessPercentage(),
                draft == null ? null : draft.lastSavedAt(),
                draft == null ? List.of() : draft.allowedActions()
        );
    }

    @PostMapping("/doctors/{doctorUserId}/discover-presence/claim-intents")
    @PreAuthorize("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')")
    public ClinicDiscoverPresenceClaimIntentResponse createDoctorClaimIntent(@PathVariable UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        DoctorProfileRecord profile = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
        ClaimIntentRecord intent = providerOwnershipService.createDoctorClaimIntent(tenantId, doctorUserId, actorAppUserId, "Healthcare initiated connection");
        return new ClinicDiscoverPresenceClaimIntentResponse(
                intent.connectionReference(),
                intent.status().name(),
                intent.expiresAt(),
                "/provider/workspace?connectionReference=" + intent.connectionReference(),
                profile.specialization(),
                clinicProfileService.findByTenantId(tenantId).map(ClinicProfileRecord::city).orElse(null),
                profile.consultationRoom()
        );
    }

    public record ClinicDiscoverPresenceResponse(
            UUID tenantReference,
            String displayName,
            String city,
            String area,
            String publicDiscoveryConsent,
            String ownershipStatus,
            UUID ownershipId,
            String maskedProviderMobile,
            String publicProfileStatus,
            String platformConnectionStatus,
            String bookingCapability,
            OffsetDateTime ownershipUpdatedAt,
            OffsetDateTime lastPublishedAt,
            OffsetDateTime publicProfileSynchronizedAt,
            String connectionReference,
            List<String> allowedActions,
            OffsetDateTime lastSynchronizedAt,
            String publicPath,
            String draftReference,
            String draftStatus,
            String draftReadinessStatus,
            int draftCompletenessPercentage,
            OffsetDateTime draftLastSavedAt,
            List<String> draftAllowedActions
    ) {
    }

    public record ClinicDiscoverPresenceClaimIntentResponse(
            String connectionReference,
            String status,
            OffsetDateTime expiresAt,
            String returnTo,
            String displayName,
            String city,
            String area
    ) {
    }

}
