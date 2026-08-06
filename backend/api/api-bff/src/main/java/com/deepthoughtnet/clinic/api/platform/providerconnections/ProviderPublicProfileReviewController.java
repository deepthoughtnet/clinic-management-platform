package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findSubmissionByReference(submissionReference).orElseThrow(() -> new ProviderOwnershipConflictException("public_profile_submission_not_found", "Submission not found.")));
    }

    @GetMapping("/{submissionReference}/media/{mediaReference}/content")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.view')")
    public ResponseEntity<byte[]> mediaContent(@PathVariable String submissionReference, @PathVariable String mediaReference) {
        var content = service.submissionMediaContent(submissionReference, mediaReference);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.originalFilename() + "\"")
                .body(content.bytes());
    }

    @PostMapping("/{submissionReference}/start")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> start(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.startReview(
                submissionReference,
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().keycloakSub(),
                RequestContextHolder.require().actorDisplayName(),
                RequestContextHolder.require().actorEmail(),
                request == null ? null : request.expectedRevision(),
                request == null ? null : request.reason()
        ));
    }

    @PostMapping("/{submissionReference}/request-changes")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> requestChanges(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.requestChanges(
                submissionReference,
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().keycloakSub(),
                RequestContextHolder.require().actorDisplayName(),
                RequestContextHolder.require().actorEmail(),
                request == null ? null : request.expectedRevision(),
                request == null ? null : request.reason(),
                request == null ? List.of() : request.findings()
        ));
    }

    @PostMapping("/{submissionReference}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> approve(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.approve(
                submissionReference,
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().keycloakSub(),
                RequestContextHolder.require().actorDisplayName(),
                RequestContextHolder.require().actorEmail(),
                request == null ? null : request.expectedRevision(),
                request == null ? null : request.reason()
        ));
    }

    @PostMapping("/{submissionReference}/reject")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.reject')")
    public ResponseEntity<?> reject(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reject(
                submissionReference,
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().keycloakSub(),
                RequestContextHolder.require().actorDisplayName(),
                RequestContextHolder.require().actorEmail(),
                request == null ? null : request.expectedRevision(),
                request == null ? null : request.reason()
        ));
    }

    @PostMapping("/{submissionReference}/findings")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> addFinding(@PathVariable String submissionReference, @RequestBody ReviewFindingRequest request) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("section", request.section());
        finding.put("field", request.field());
        finding.put("category", request.category());
        finding.put("severity", request.severity());
        finding.put("required", request.blocking() || request.providerActionRequired());
        finding.put("providerFacingMessage", request.providerFacingMessage());
        finding.put("internalNote", request.internalNote());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.addFinding(
                submissionReference,
                RequestContextHolder.require().appUserId(),
                RequestContextHolder.require().keycloakSub(),
                RequestContextHolder.require().actorDisplayName(),
                RequestContextHolder.require().actorEmail(),
                request.expectedRevision(),
                finding
        ));
    }

    @PostMapping("/{submissionReference}/publish")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.approve')")
    public ResponseEntity<?> publish(@PathVariable String submissionReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publish(submissionReference, RequestContextHolder.require().appUserId(), request == null ? null : request.reason()));
    }

    @PostMapping("/{publicProfileReference}/unpublish")
    @PreAuthorize("@permissionChecker.hasPermission('platform.provider_connection.unlink')")
    public ResponseEntity<?> unpublish(@PathVariable String publicProfileReference, @RequestBody(required = false) ReviewCommandRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.unpublish(publicProfileReference, RequestContextHolder.require().appUserId(), request == null ? null : request.reason()));
    }

    public record ReviewCommandRequest(Long expectedRevision, String reason, List<Map<String, Object>> findings) {
    }

    public record ReviewFindingRequest(
            Long expectedRevision,
            String section,
            String category,
            String severity,
            String field,
            String providerFacingMessage,
            String internalNote,
            boolean blocking,
            boolean providerActionRequired
    ) {
    }
}
