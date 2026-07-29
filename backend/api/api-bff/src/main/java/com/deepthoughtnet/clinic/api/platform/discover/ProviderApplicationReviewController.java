package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentContentRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderReviewDetailRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderReviewSummaryRecord;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/discover/provider-applications")
@PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.view')")
public class ProviderApplicationReviewController {
    private final ProviderApplicationReviewApiService service;

    public ProviderApplicationReviewController(ProviderApplicationReviewApiService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProviderReviewSummaryRecord> list(
            @RequestParam(required = false) List<ProviderLifecycleStatus> status,
            @RequestParam(required = false) ProviderType providerType,
            @RequestParam(required = false) String search
    ) {
        return service.list(status, providerType, search);
    }

    @GetMapping("/{referenceNumber}")
    public ProviderReviewDetailRecord get(@PathVariable String referenceNumber) {
        return service.get(referenceNumber);
    }

    @GetMapping("/{referenceNumber}/documents/{documentId}/content")
    @PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.history.view')")
    public ResponseEntity<byte[]> documentContent(@PathVariable String referenceNumber, @PathVariable UUID documentId) {
        DocumentContentRecord content = service.documentContent(referenceNumber, documentId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType() == null || content.contentType().isBlank() ? "application/octet-stream" : content.contentType()))
                .header("Content-Disposition", "inline; filename=\"" + content.originalFilename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }

    @PostMapping("/{referenceNumber}/start-review")
    @PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.review')")
    public ProviderApplicationRecord startReview(@PathVariable String referenceNumber, @RequestBody(required = false) ReviewActionRequest request) {
        return service.startReview(referenceNumber, request == null ? null : request.reason());
    }

    @PostMapping("/{referenceNumber}/request-changes")
    @PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.request.changes')")
    public ProviderApplicationRecord requestChanges(@PathVariable String referenceNumber, @RequestBody ReviewActionRequest request) {
        return service.requestChanges(referenceNumber, request == null ? null : request.reason(), request == null ? null : request.requestedSections());
    }

    @PostMapping("/{referenceNumber}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.approve')")
    public ProviderApplicationRecord approve(@PathVariable String referenceNumber, @RequestBody(required = false) ReviewActionRequest request) {
        return service.approve(referenceNumber, request == null ? null : request.reason());
    }

    @PostMapping("/{referenceNumber}/publish")
    @PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.publish')")
    public ProviderApplicationRecord publish(@PathVariable String referenceNumber, @RequestBody(required = false) ReviewActionRequest request) {
        return service.publish(referenceNumber, request == null ? null : request.reason());
    }

    public record ReviewActionRequest(
            String reason,
            List<@NotBlank String> requestedSections
    ) {
    }
}
