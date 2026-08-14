package com.deepthoughtnet.clinic.api.discover.provider.access;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginVerifyResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionAuthenticationFilter;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderPortalAuthProperties;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessGrantRecord;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestCommand;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestRecord;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestService;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/auth")
public class ProviderAccessAuthController {
    private static final String COOKIE_NAME = ProviderSessionAuthenticationFilter.SESSION_COOKIE;

    private final ProviderPortalAccessRequestService accessRequestService;
    private final ProviderPortalAuthProperties properties;

    public ProviderAccessAuthController(ProviderPortalAccessRequestService accessRequestService, ProviderPortalAuthProperties properties) {
        this.accessRequestService = accessRequestService;
        this.properties = properties;
    }

    @PostMapping("/access-requests")
    public ProviderAccessModels.ProviderAccessRequestResponse submit(@Valid @RequestBody ProviderAccessModels.ProviderAccessRequestSubmitRequest request) {
        ensureAccessApprovalMode();
        ProviderPortalAccessRequestRecord record = accessRequestService.submit(new ProviderPortalAccessRequestCommand(
                request.fullName(),
                request.email(),
                request.mobile(),
                request.providerType(),
                request.providerApplicationReference(),
                request.note()
        ));
        return toResponse(record);
    }

    @PostMapping("/access-login")
    public ResponseEntity<LoginVerifyResponse> accessLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid @RequestBody ProviderAccessModels.ProviderAccessLoginRequest body
    ) {
        ensureAccessApprovalMode();
        ProviderPortalAccessGrantRecord grant = accessRequestService.authenticate(body.identifier(), body.accessCode());
        String existingSession = readCookie(request, COOKIE_NAME);
        var session = accessRequestService.getVerificationService().replaceSession(grant.providerAccountId(), existingSession);
        response.addHeader(HttpHeaders.SET_COOKIE, buildSessionCookie(session.sessionToken(), request.isSecure(), session.expiresAt().toEpochSecond()));
        return ResponseEntity.ok(new LoginVerifyResponse(true, session.expiresAt(), "Provider login successful."));
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

    private String buildSessionCookie(String value, boolean secure, long expiresAtEpochSeconds) {
        Duration maxAge = Duration.between(OffsetDateTime.now(), OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(expiresAtEpochSeconds), java.time.ZoneOffset.UTC));
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build()
                .toString();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (cookie != null && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void ensureAccessApprovalMode() {
        if (properties.getMode() != ProviderPortalAuthProperties.Mode.ACCESS_APPROVAL) {
            throw new ForbiddenException("Controlled provider access is not enabled in the current mode.");
        }
    }
}
