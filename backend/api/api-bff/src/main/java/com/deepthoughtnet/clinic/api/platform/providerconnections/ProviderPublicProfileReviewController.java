package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/provider-connections/public-profile-reviews")
@PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.view')")
public class ProviderPublicProfileReviewController {
    private final ProviderPublicProfileModerationService service;

    public ProviderPublicProfileReviewController(ProviderPublicProfileModerationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<?>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listQueue());
    }

    @GetMapping("/{submissionReference}")
    public ResponseEntity<?> detail(@PathVariable String submissionReference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findSubmissionByReference(submissionReference).orElseThrow(() -> new IllegalArgumentException("Submission not found")));
    }

    @PostMapping("/{submissionReference}/start")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> start(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.startReview(submissionReference, null, request == null ? null : request.expectedRevision(), request == null ? null : request.reason()));
    }

    @PostMapping("/{submissionReference}/request-changes")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> requestChanges(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.requestChanges(submissionReference, null, request == null ? null : request.expectedRevision(), request == null ? null : request.reason(), request == null ? List.of() : request.findings()));
    }

    @PostMapping("/{submissionReference}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> approve(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.approve(submissionReference, null, request == null ? null : request.expectedRevision(), request == null ? null : request.reason()));
    }

    @PostMapping("/{submissionReference}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reject')")
    public ResponseEntity<?> reject(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reject(submissionReference, null, request == null ? null : request.expectedRevision(), request == null ? null : request.reason()));
    }

    @PostMapping("/{submissionReference}/publish")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> publish(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publish(submissionReference, null, request == null ? null : request.reason()));
    }

    @PostMapping("/{publicProfileReference}/unpublish")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.unlink')")
    public ResponseEntity<?> unpublish(@PathVariable String publicProfileReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.unpublish(publicProfileReference, null, request == null ? null : request.reason()));
    }

    public record ReviewCommandRequest(Long expectedRevision, String reason, List<Map<String, Object>> findings) {
    }
}
