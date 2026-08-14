package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginChallengeResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ChallengeVerifyRequest;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginRequest;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginVerifyRequest;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginVerifyResponse;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.DiscoverContactNormalizer;
import com.deepthoughtnet.clinic.discover.verification.ProviderSessionResult;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationPurpose;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeResult;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService.VerificationVerificationRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider/auth")
public class ProviderAuthController {
    private static final String COOKIE_NAME = ProviderSessionAuthenticationFilter.SESSION_COOKIE;

    private final DiscoverVerificationService verificationService;
    private final ProviderPortalAuthProperties properties;

    public ProviderAuthController(DiscoverVerificationService verificationService, ProviderPortalAuthProperties properties) {
        this.verificationService = verificationService;
        this.properties = properties;
    }

    @PostMapping("/request")
    public LoginChallengeResponse request(@Valid @RequestBody LoginRequest request) {
        ensureVerificationCodeMode();
        return requestChallenge(request);
    }

    @PostMapping("/challenges")
    public LoginChallengeResponse requestChallenge(@Valid @RequestBody LoginRequest request) {
        ensureVerificationCodeMode();
        VerificationChannel channel = inferChannel(request.identifier());
        String normalizedRecipient = normalizeRecipient(request.identifier(), channel);
        VerificationPurpose purpose = channel == VerificationChannel.EMAIL
                ? VerificationPurpose.PROVIDER_LOGIN_EMAIL
                : VerificationPurpose.PROVIDER_LOGIN_PHONE;
        VerificationChallengeResult result = verificationService.requestChallenge(new VerificationChallengeRequest(
                null,
                null,
                purpose,
                channel,
                normalizedRecipient,
                null,
                null,
                "PROVIDER_LOGIN"
        ));
        return new LoginChallengeResponse(
                result.challengeId().toString(),
                result.channel(),
                result.maskedRecipient(),
                result.message(),
                properties.isExposeDevOtp() ? result.developmentCode() : null,
                result.verificationMode(),
                result.expiresAt(),
                result.resendAvailableAt(),
                result.expiresInSeconds(),
                result.resendAfterSeconds(),
                result.providerName(),
                result.deliveryReference()
        );
    }

    @PostMapping("/verify")
    public ResponseEntity<LoginVerifyResponse> verify(
            HttpServletRequest request,
            HttpServletResponse response,
            @Valid @RequestBody LoginVerifyRequest body
    ) {
        ensureVerificationCodeMode();
        VerificationChannel channel = inferChannel(body.identifier());
        String normalizedRecipient = normalizeRecipient(body.identifier(), channel);
        VerificationPurpose purpose = channel == VerificationChannel.EMAIL
                ? VerificationPurpose.PROVIDER_LOGIN_EMAIL
                : VerificationPurpose.PROVIDER_LOGIN_PHONE;
        var result = verificationService.verifyChallenge(new VerificationVerificationRequest(
                null,
                null,
                null,
                purpose,
                channel,
                normalizedRecipient,
                body.code(),
                "PROVIDER_LOGIN"
        ));
        if (result.providerAccountId() == null) {
            return ResponseEntity.status(401).body(new LoginVerifyResponse(false, null, "Unable to verify provider login right now."));
        }
        ProviderSessionResult session = verificationService.replaceSession(result.providerAccountId(), readCookie(request, COOKIE_NAME));
        response.addHeader(HttpHeaders.SET_COOKIE, buildSessionCookie(session.sessionToken(), request.isSecure(), session.expiresAt().toEpochSecond()));
        return ResponseEntity.ok(new LoginVerifyResponse(
                true,
                session.expiresAt(),
                "Provider login successful."
        ));
    }

    @PostMapping("/challenges/{challengeId}/verify")
    public ResponseEntity<LoginVerifyResponse> verifyChallenge(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable UUID challengeId,
            @Valid @RequestBody ChallengeVerifyRequest body
    ) {
        ensureVerificationCodeMode();
        var result = verificationService.verifyChallenge(new VerificationVerificationRequest(
                challengeId,
                null,
                null,
                null,
                null,
                null,
                body.code(),
                "PROVIDER_LOGIN"
        ));
        if (result.providerAccountId() == null) {
            return ResponseEntity.status(401).body(new LoginVerifyResponse(false, null, "Unable to verify provider login right now."));
        }
        ProviderSessionResult session = verificationService.replaceSession(result.providerAccountId(), readCookie(request, COOKIE_NAME));
        response.addHeader(HttpHeaders.SET_COOKIE, buildSessionCookie(session.sessionToken(), request.isSecure(), session.expiresAt().toEpochSecond()));
        return ResponseEntity.ok(new LoginVerifyResponse(true, session.expiresAt(), "Provider login successful."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request, COOKIE_NAME);
        if (token != null) {
            verificationService.revokeSession(token);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, clearSessionCookie(request.isSecure()));
        return ResponseEntity.noContent().build();
    }

    private VerificationChannel inferChannel(String identifier) {
        return identifier != null && identifier.contains("@") ? VerificationChannel.EMAIL : VerificationChannel.SMS;
    }

    private String normalizeRecipient(String value, VerificationChannel channel) {
        return DiscoverContactNormalizer.normalizeRecipient(value, channel);
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

    private String clearSessionCookie(boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
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

    private void ensureVerificationCodeMode() {
        if (properties.getMode() == ProviderPortalAuthProperties.Mode.ACCESS_APPROVAL) {
            throw new ForbiddenException("Verification-code login is not available in controlled access mode.");
        }
    }
}
