package com.deepthoughtnet.clinic.discover.verification;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderSessionEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderSessionRepository;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverVerificationChallengeEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverVerificationChallengeRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DiscoverVerificationService {
    private static final String CONTEXT_ONBOARDING = "PROVIDER_ONBOARDING";
    private static final String CONTEXT_LOGIN = "PROVIDER_LOGIN";

    private final ProviderApplicationRepository applications;
    private final ProviderContactVerificationRepository contactVerifications;
    private final DiscoverVerificationChallengeRepository challenges;
    private final DiscoverProviderAccountRepository providerAccounts;
    private final DiscoverProviderSessionRepository providerSessions;
    private final DiscoverVerificationProperties properties;
    private final VerificationDeliveryPort deliveryPort;
    private final ProviderPublicProfileService publicProfileService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DiscoverVerificationService(
            ProviderApplicationRepository applications,
            ProviderContactVerificationRepository contactVerifications,
            DiscoverVerificationChallengeRepository challenges,
            DiscoverProviderAccountRepository providerAccounts,
            DiscoverProviderSessionRepository providerSessions,
            DiscoverVerificationProperties properties,
            VerificationDeliveryPort deliveryPort,
            ProviderPublicProfileService publicProfileService
    ) {
        this.applications = applications;
        this.contactVerifications = contactVerifications;
        this.challenges = challenges;
        this.providerAccounts = providerAccounts;
        this.providerSessions = providerSessions;
        this.properties = properties;
        this.deliveryPort = deliveryPort;
        this.publicProfileService = publicProfileService;
    }

    @Transactional
    public VerificationChallengeResult requestChallenge(VerificationChallengeRequest request) {
        require(request.purpose() != null, "purpose is required");
        require(request.channel() != null, "channel is required");
        requireText(request.normalizedRecipient(), "normalizedRecipient");
        OffsetDateTime now = OffsetDateTime.now();

        Optional<DiscoverVerificationChallengeEntity> latest = latestChallenge(request);
        if (latest.isPresent()) {
            DiscoverVerificationChallengeEntity active = latest.get();
            if (active.getConsumedAt() == null && active.getInvalidatedAt() == null && active.getExpiresAt().isAfter(now)) {
                if (active.getResendAvailableAt().isAfter(now)) {
                    long remaining = Math.max(0L, active.getResendAvailableAt().toEpochSecond() - now.toEpochSecond());
                    return new VerificationChallengeResult(
                            "Please wait before requesting another code.",
                            null,
                            properties.getChallengeTtl().getSeconds(),
                            remaining,
                            active.getDeliveryProvider(),
                            active.getDeliveryReference()
                    );
                }
                invalidateActiveChallenges(request);
            }
        }

        String code = generateCode();
        String codeHash = digest(code);
        DiscoverVerificationChallengeEntity challenge = DiscoverVerificationChallengeEntity.create(
                request.providerApplicationId(),
                request.providerAccountId(),
                request.purpose(),
                request.channel(),
                normalizeRecipient(request.normalizedRecipient(), request.channel()),
                codeHash,
                properties.getMaxAttempts(),
                now.plus(properties.getChallengeTtl()),
                now.plus(properties.getResendCooldown()),
                "pending",
                null,
                request.createdByContext(),
                maskCode(code)
        );
        challenge = challenges.save(challenge);

        VerificationDeliveryResult deliveryResult = deliveryPort.deliver(new VerificationDeliveryRequest(
                request.providerApplicationId(),
                request.providerAccountId(),
                request.purpose(),
                request.channel(),
                challenge.getNormalizedRecipient(),
                code,
                subjectFor(request.purpose(), request.channel()),
                bodyFor(request.purpose(), code),
                java.util.Map.of(
                        "purpose", request.purpose().name(),
                        "channel", request.channel().name(),
                        "recipient", challenge.getNormalizedRecipient()
                )
        ));

        if (!deliveryResult.accepted()) {
            challenge.invalidate();
            challenges.save(challenge);
        } else {
            challenge = updateDeliveryMetadata(challenge, deliveryResult);
        }
        return new VerificationChallengeResult(
                deliveryResult.accepted()
                        ? challengeMessage(request.purpose(), request.channel())
                        : deliveryResult.message(),
                deliveryResult.developmentCode(),
                properties.getChallengeTtl().getSeconds(),
                properties.getResendCooldown().getSeconds(),
                deliveryResult.providerName(),
                deliveryResult.deliveryReference()
        );
    }

    @Transactional
    public VerificationVerificationResult verifyChallenge(VerificationVerificationRequest request) {
        require(request.purpose() != null, "purpose is required");
        require(request.channel() != null, "channel is required");
        requireText(request.normalizedRecipient(), "normalizedRecipient");
        requireText(request.code(), "code");

        DiscoverVerificationChallengeEntity challenge = latestChallenge(request)
                .orElseThrow(() -> new IllegalArgumentException("The verification code is invalid or has expired."));
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getConsumedAt() != null || challenge.getInvalidatedAt() != null || challenge.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("The verification code is invalid or has expired.");
        }
        if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new IllegalArgumentException("Too many unsuccessful attempts. Request a new code.");
        }
        if (!matchesDigest(normalizeCode(request.code()), challenge.getCodeHash())) {
            challenge.incrementAttemptCount();
            challenges.save(challenge);
            if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
                throw new IllegalArgumentException("Too many unsuccessful attempts. Request a new code.");
            }
            throw new IllegalArgumentException("The verification code is invalid or has expired.");
        }

        challenge.markConsumed();
        challenges.save(challenge);

        DiscoverProviderAccountEntity account = null;
        boolean created = false;
        boolean linked = false;
        if (request.purpose() == VerificationPurpose.PROVIDER_LOGIN_EMAIL || request.purpose() == VerificationPurpose.PROVIDER_LOGIN_PHONE) {
            Optional<ProviderAccountResolution> resolution = resolveAccountForLogin(request.normalizedRecipient(), request.channel());
            if (resolution.isEmpty()) {
                throw new IllegalArgumentException("The verification code is invalid or has expired.");
            }
            account = resolution.get().account();
            created = resolution.get().created();
            linked = resolution.get().linked();
        } else if (request.providerApplicationId() != null) {
            ProviderAccountResolution resolution = resolveOrCreateProviderAccountForApplication(request.providerApplicationId(), request.channel(), challenge.getNormalizedRecipient());
            account = resolution.account();
            created = resolution.created();
            linked = resolution.linked();
            markApplicationContactVerified(requireApplication(request.providerApplicationId()), challenge.getChannel(), challenge.getNormalizedRecipient());
        }

        return new VerificationVerificationResult(
                true,
                "Verification successful.",
                account == null ? null : account.getId(),
                created,
                linked,
                challenge.getNormalizedRecipient(),
                request.purpose(),
                request.channel()
        );
    }

    @Transactional
    public ProviderSessionResult createSession(UUID providerAccountId) {
        DiscoverProviderAccountEntity account = requireAccount(providerAccountId);
        String token = generateSessionToken();
        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime expiresAt = issuedAt.plus(properties.getSessionTtl());
        DiscoverProviderSessionEntity session = DiscoverProviderSessionEntity.create(
                account.getId(),
                digest(token),
                issuedAt,
                expiresAt
        );
        providerSessions.save(session);
        return new ProviderSessionResult(account.getId(), token, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<DiscoverProviderSessionEntity> resolveSession(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return Optional.empty();
        }
        String hashed = digest(sessionToken.trim());
        return providerSessions.findBySessionTokenHash(hashed)
                .filter(DiscoverProviderSessionEntity::isActive);
    }

    @Transactional
    public void revokeSession(String sessionToken) {
        resolveSession(sessionToken).ifPresent(session -> {
            session.revoke();
            providerSessions.save(session);
        });
    }

    @Transactional(readOnly = true)
    public Optional<DiscoverProviderAccountEntity> findAccountByRecipient(VerificationChannel channel, String normalizedRecipient) {
        String normalized = normalizeRecipient(normalizedRecipient, channel);
        return channel == VerificationChannel.EMAIL
                ? providerAccounts.findByNormalizedEmail(normalized)
                : providerAccounts.findByNormalizedPhone(normalized);
    }

    @Transactional(readOnly = true)
    public List<ProviderApplicationEntity> findOwnedApplications(UUID providerAccountId) {
        return applications.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId);
    }

    @Transactional(readOnly = true)
    public List<ProviderWorkspaceApplicationRecord> findOwnedApplicationSummaries(UUID providerAccountId) {
        return findOwnedApplications(providerAccountId).stream()
                .map(application -> new ProviderWorkspaceApplicationRecord(
                        application.getId(),
                        application.getReferenceNumber(),
                        application.getProviderType(),
                        application.getStatus(),
                        firstText(application.getDisplayName(), application.getLegalName(), application.getEmail()),
                        application.getCompletionPercent(),
                        application.getCurrentStep(),
                        application.isContactVerified(),
                        application.getUpdatedAt(),
                        application.getSubmittedAt(),
                        publicProfileService.findByProviderId(application.getId()).map(record -> record.publicPath()).orElse(null)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProviderContactVerificationEntity> findContactVerification(UUID providerId) {
        return contactVerifications.findByProviderId(providerId);
    }

    @Transactional
    public ProviderVerificationLinkResult linkVerifiedContactToProviderApplication(UUID providerApplicationId, VerificationChannel channel) {
        ProviderApplicationEntity application = requireApplication(providerApplicationId);
        String normalizedRecipient = channel == VerificationChannel.EMAIL ? normalizeRecipient(application.getEmail(), channel) : normalizeRecipient(application.getPhone(), channel);
        ProviderAccountResolution resolution = resolveOrCreateProviderAccountForApplication(providerApplicationId, channel, normalizedRecipient);
        markApplicationContactVerified(application, channel, normalizedRecipient);
        return new ProviderVerificationLinkResult(
                resolution.account().getId(),
                resolution.created(),
                resolution.linked()
        );
    }

    private ProviderAccountResolution resolveOrCreateProviderAccountForApplication(UUID providerApplicationId, VerificationChannel channel, String recipient) {
        String normalizedRecipient = normalizeRecipient(recipient, channel);
        Optional<DiscoverProviderAccountEntity> existing = channel == VerificationChannel.EMAIL
                ? providerAccounts.findByNormalizedEmail(normalizedRecipient)
                : providerAccounts.findByNormalizedPhone(normalizedRecipient);
        if (existing.isPresent()) {
            DiscoverProviderAccountEntity account = existing.get();
            touchAccountForChannel(account, channel, normalizedRecipient);
            account = providerAccounts.save(account);
            if (providerApplicationId != null) {
                linkApplicationToAccount(providerApplicationId, account);
            }
            return new ProviderAccountResolution(account, false, providerApplicationId != null);
        }

        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(
                channel == VerificationChannel.EMAIL ? normalizedRecipient : null,
                channel == VerificationChannel.SMS ? normalizedRecipient : null
        );
        if (channel == VerificationChannel.EMAIL) {
            account.markEmailVerified();
        } else {
            account.markPhoneVerified();
        }
        account = providerAccounts.save(account);
        if (providerApplicationId != null) {
            linkApplicationToAccount(providerApplicationId, account);
        }
        return new ProviderAccountResolution(account, true, providerApplicationId != null);
    }

    private Optional<ProviderAccountResolution> resolveAccountForLogin(String recipient, VerificationChannel channel) {
        String normalizedRecipient = normalizeRecipient(recipient, channel);
        Optional<DiscoverProviderAccountEntity> existing = channel == VerificationChannel.EMAIL
                ? providerAccounts.findByNormalizedEmail(normalizedRecipient)
                : providerAccounts.findByNormalizedPhone(normalizedRecipient);
        if (existing.isPresent()) {
            DiscoverProviderAccountEntity account = existing.get();
            touchAccountForChannel(account, channel, normalizedRecipient);
            account = providerAccounts.save(account);
            return Optional.of(new ProviderAccountResolution(account, false, false));
        }

        List<ProviderApplicationEntity> applicationsByContact = (channel == VerificationChannel.EMAIL
                ? applications.findByEmailIgnoreCase(normalizedRecipient)
                : applications.findByPhoneIgnoreCase(normalizedRecipient)).stream()
                .filter(ProviderApplicationEntity::isContactVerified)
                .toList();
        if (applicationsByContact.isEmpty()) {
            return Optional.empty();
        }

        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(
                channel == VerificationChannel.EMAIL ? normalizedRecipient : null,
                channel == VerificationChannel.SMS ? normalizedRecipient : null
        );
        if (channel == VerificationChannel.EMAIL) {
            account.markEmailVerified();
        } else {
            account.markPhoneVerified();
        }
        account = providerAccounts.save(account);
        for (ProviderApplicationEntity application : applicationsByContact) {
            application.setProviderAccountId(account.getId());
            applications.save(application);
        }
        return Optional.of(new ProviderAccountResolution(account, true, true));
    }

    private void touchAccountForChannel(DiscoverProviderAccountEntity account, VerificationChannel channel, String normalizedRecipient) {
        if (channel == VerificationChannel.EMAIL) {
            account.setNormalizedEmail(normalizedRecipient);
            account.markEmailVerified();
        } else {
            account.setNormalizedPhone(normalizedRecipient);
            account.markPhoneVerified();
        }
    }

    private void linkApplicationToAccount(UUID providerApplicationId, DiscoverProviderAccountEntity account) {
        ProviderApplicationEntity application = requireApplication(providerApplicationId);
        application.setProviderAccountId(account.getId());
        applications.save(application);
    }

    private void markApplicationContactVerified(ProviderApplicationEntity application, VerificationChannel channel, String normalizedRecipient) {
        ProviderContactVerificationEntity verification = contactVerifications.findByProviderId(application.getId())
                .orElseGet(() -> ProviderContactVerificationEntity.create(
                        application.getId(),
                        normalizeRecipient(application.getEmail(), VerificationChannel.EMAIL),
                        normalizeRecipient(application.getPhone(), VerificationChannel.SMS)
                ));
        if (channel == VerificationChannel.EMAIL) {
            verification.setEmailNormalized(normalizedRecipient);
            verification.markEmailVerified();
        } else {
            verification.setPhoneNormalized(normalizedRecipient);
            verification.markPhoneVerified();
        }
        contactVerifications.save(verification);
        application.setContactVerified(verification.getEmailVerifiedAt() != null || verification.getPhoneVerifiedAt() != null);
        if (application.getStatus() == ProviderLifecycleStatus.DRAFT) {
            applications.save(application);
        }
    }

    private DiscoverProviderAccountEntity requireAccount(UUID providerAccountId) {
        return providerAccounts.findById(providerAccountId)
                .orElseThrow(() -> new IllegalArgumentException("provider account not found"));
    }

    private ProviderApplicationEntity requireApplication(UUID providerApplicationId) {
        return applications.findById(providerApplicationId)
                .orElseThrow(() -> new IllegalArgumentException("provider application not found"));
    }

    private Optional<DiscoverVerificationChallengeEntity> latestChallenge(VerificationChallengeRequest request) {
        if (request.providerApplicationId() != null) {
            return challenges.findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdOrderByCreatedAtDesc(
                    request.purpose(),
                    request.channel(),
                    normalizeRecipient(request.normalizedRecipient(), request.channel()),
                    request.providerApplicationId()
            );
        }
        if (request.providerAccountId() != null) {
            return challenges.findTopByPurposeAndChannelAndNormalizedRecipientAndProviderAccountIdOrderByCreatedAtDesc(
                    request.purpose(),
                    request.channel(),
                    normalizeRecipient(request.normalizedRecipient(), request.channel()),
                    request.providerAccountId()
            );
        }
        return challenges.findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdIsNullAndProviderAccountIdIsNullOrderByCreatedAtDesc(
                request.purpose(),
                request.channel(),
                normalizeRecipient(request.normalizedRecipient(), request.channel())
        );
    }

    private Optional<DiscoverVerificationChallengeEntity> latestChallenge(VerificationVerificationRequest request) {
        VerificationChallengeRequest challengeRequest = new VerificationChallengeRequest(
                request.providerApplicationId(),
                request.providerAccountId(),
                request.purpose(),
                request.channel(),
                request.normalizedRecipient(),
                null,
                null,
                request.createdByContext()
        );
        return latestChallenge(challengeRequest);
    }

    private void invalidateActiveChallenges(VerificationChallengeRequest request) {
        List<DiscoverVerificationChallengeEntity> activeChallenges = request.providerApplicationId() != null
                ? challenges.findByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                request.purpose(),
                request.channel(),
                normalizeRecipient(request.normalizedRecipient(), request.channel()),
                request.providerApplicationId()
        )
                : request.providerAccountId() != null
                ? challenges.findByPurposeAndChannelAndNormalizedRecipientAndProviderAccountIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                request.purpose(),
                request.channel(),
                normalizeRecipient(request.normalizedRecipient(), request.channel()),
                request.providerAccountId()
        )
                : challenges.findByPurposeAndChannelAndNormalizedRecipientAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                request.purpose(),
                request.channel(),
                normalizeRecipient(request.normalizedRecipient(), request.channel())
        );
        for (DiscoverVerificationChallengeEntity active : activeChallenges) {
            active.invalidate();
            challenges.save(active);
        }
    }

    private DiscoverVerificationChallengeEntity updateDeliveryMetadata(DiscoverVerificationChallengeEntity challenge, VerificationDeliveryResult result) {
        challenge.setDeliveryProvider(result.providerName());
        challenge.setDeliveryReference(result.deliveryReference());
        return challenges.save(challenge);
    }

    private String challengeMessage(VerificationPurpose purpose, VerificationChannel channel) {
        return switch (purpose) {
            case PROVIDER_REGISTRATION_EMAIL -> "Verification email sent.";
            case PROVIDER_REGISTRATION_PHONE -> "Verification OTP sent.";
            case PROVIDER_LOGIN_EMAIL -> "Login verification email sent.";
            case PROVIDER_LOGIN_PHONE -> "Login verification OTP sent.";
        };
    }

    private String subjectFor(VerificationPurpose purpose, VerificationChannel channel) {
        return switch (purpose) {
            case PROVIDER_REGISTRATION_EMAIL -> "Verify your Jeevanam provider email";
            case PROVIDER_REGISTRATION_PHONE -> "Verify your Jeevanam provider phone";
            case PROVIDER_LOGIN_EMAIL -> "Your Jeevanam provider login code";
            case PROVIDER_LOGIN_PHONE -> "Your Jeevanam provider login code";
        };
    }

    private String bodyFor(VerificationPurpose purpose, String code) {
        return """
                Your Jeevanam verification code is %s.

                This code expires soon. If you did not request it, you can ignore this message.
                """.formatted(code);
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash value", ex);
        }
    }

    private boolean matchesDigest(String value, String digest) {
        return digest(value).equals(digest);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String maskCode(String code) {
        if (!StringUtils.hasText(code) || code.length() < 2) {
            return null;
        }
        return "**" + code.substring(code.length() - 2);
    }

    private String normalizeRecipient(String value, VerificationChannel channel) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (channel == VerificationChannel.EMAIL) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed.replaceAll("[^0-9+]", "");
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().replaceAll("\\s+", "");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record ProviderAccountResolution(DiscoverProviderAccountEntity account, boolean created, boolean linked) {
    }

    public record VerificationVerificationRequest(
            UUID providerApplicationId,
            UUID providerAccountId,
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            String code,
            String createdByContext
    ) {
    }

    public record ProviderVerificationLinkResult(
            UUID providerAccountId,
            boolean created,
            boolean linked
    ) {
    }
}
