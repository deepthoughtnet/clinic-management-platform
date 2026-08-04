package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionPrincipal;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/public-profiles")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderPublicProfileModerationController {
    private final ProviderPublicProfileModerationService service;
    private final ClinicProfileService clinicProfileService;

    public ProviderPublicProfileModerationController(ProviderPublicProfileModerationService service, ClinicProfileService clinicProfileService) {
        this.service = service;
        this.clinicProfileService = clinicProfileService;
    }

    @GetMapping("/{publicProfileReference}/moderation")
    public ResponseEntity<?> moderation(Authentication authentication, @PathVariable String publicProfileReference) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        boolean consentEnabled = consentEnabled(publicProfileReference);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.submissionEligibility(principal.providerAccountId(), publicProfileReference, consentEnabled));
    }

    @PostMapping("/{publicProfileReference}/submissions")
    public ResponseEntity<?> submit(Authentication authentication, @PathVariable String publicProfileReference) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        boolean consentEnabled = consentEnabled(publicProfileReference);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.submitForReview(principal.providerAccountId(), publicProfileReference, consentEnabled));
    }

    @PostMapping("/{publicProfileReference}/submissions/{submissionReference}/withdraw")
    public ResponseEntity<?> withdraw(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @PathVariable String submissionReference,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.withdraw(submissionReference, principal.providerAccountId(), request == null ? null : string(request.get("reason"))));
    }

    @GetMapping("/{publicProfileReference}/feedback")
    public ResponseEntity<List<?>> feedback(Authentication authentication, @PathVariable String publicProfileReference) {
        requirePrincipal(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findSubmission(publicProfileReference).map(record -> record.findings()).orElse(List.of()));
    }

    private boolean consentEnabled(String publicProfileReference) {
        UUID tenantId = parseUuid(publicProfileReference);
        if (tenantId == null) {
            return false;
        }
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        return clinic != null && clinic.publicListingEnabled();
    }

    private UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ProviderSessionPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ProviderSessionPrincipal principal)) {
            throw new IllegalStateException("provider session is required");
        }
        return principal;
    }
}
