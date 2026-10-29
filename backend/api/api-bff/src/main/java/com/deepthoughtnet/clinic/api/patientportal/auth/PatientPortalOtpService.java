package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpRequestResponse;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpVerifyResponse;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;

@Service
public class PatientPortalOtpService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalOtpService.class);
    private static final String SAFE_VERIFY_FAILURE_MESSAGE = "Unable to verify patient access right now.";
    private static final String DEMO_CLINIC_CODE = "demo-clinic";

    private final TenantRepository tenantRepository;
    private final PatientRepository patientRepository;
    private final AppUserProvisioner appUserProvisioner;
    private final AppUserRepository appUserRepository;
    private final PatientPortalOtpChallengeRepository challengeRepository;
    private final PatientPortalSessionTokenService sessionTokenService;
    private final PatientPortalAuthProperties properties;
    private final MessagingProviderRegistry messagingProviderRegistry;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public PatientPortalOtpService(
            TenantRepository tenantRepository,
            PatientRepository patientRepository,
            AppUserProvisioner appUserProvisioner,
            AppUserRepository appUserRepository,
            PatientPortalOtpChallengeRepository challengeRepository,
            PatientPortalSessionTokenService sessionTokenService,
            PatientPortalAuthProperties properties
    ) {
        this(tenantRepository, patientRepository, appUserProvisioner, appUserRepository,
                challengeRepository, sessionTokenService, properties, null);
    }

    @Autowired
    public PatientPortalOtpService(
            TenantRepository tenantRepository,
            PatientRepository patientRepository,
            AppUserProvisioner appUserProvisioner,
            AppUserRepository appUserRepository,
            PatientPortalOtpChallengeRepository challengeRepository,
            PatientPortalSessionTokenService sessionTokenService,
            PatientPortalAuthProperties properties,
            MessagingProviderRegistry messagingProviderRegistry
    ) {
        this.tenantRepository = tenantRepository;
        this.patientRepository = patientRepository;
        this.appUserProvisioner = appUserProvisioner;
        this.appUserRepository = appUserRepository;
        this.challengeRepository = challengeRepository;
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
        this.messagingProviderRegistry = messagingProviderRegistry;
    }

    /** Voice-only challenge entry point. It shares the portal challenge table and hash policy,
     * but never returns a development code or creates a portal session. */
    @Transactional
    public VoiceChallengeStart startVoiceChallenge(UUID tenantId, UUID identifiedPatientId,
            String mobile, String providerCallId, UUID interactionId) {
        if (tenantId == null || identifiedPatientId == null || !StringUtils.hasText(providerCallId)
                || properties.getMode() != PatientPortalAuthProperties.Mode.OTP
                || messagingProviderRegistry == null) {
            return VoiceChallengeStart.failed("UNAVAILABLE", null, 0);
        }
        String normalizedPhone = normalizePhone(mobile);
        PatientEntity patient = patientRepository.findById(identifiedPatientId).orElse(null);
        if (patient == null || !tenantId.equals(patient.getTenantId()) || !patient.isActive()
                || !sameRegisteredMobile(normalizedPhone, normalizePhone(patient.getMobile()))) {
            return VoiceChallengeStart.failed("INELIGIBLE", null, 0);
        }
        OffsetDateTime now = OffsetDateTime.now();
        var latest = challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(tenantId, normalizedPhone);
        if (latest.isPresent()) {
            long elapsed = Duration.between(latest.get().getCreatedAt(), now).getSeconds();
            long cooldown = properties.getResendCooldown().getSeconds();
            if (elapsed < cooldown) return VoiceChallengeStart.failed("COOLDOWN", latest.get().getId(), cooldown - elapsed);
        }
        String code = generateOtp();
        PatientPortalOtpChallengeEntity challenge = challengeRepository.save(PatientPortalOtpChallengeEntity.createVoice(
                tenantId, identifiedPatientId, normalizedPhone, passwordEncoder.encode(code),
                now.plus(properties.getOtpTtl()), providerCallId, interactionId));
        MessageProvider provider = messagingProviderRegistry.resolve(MessageChannel.SMS);
        if (provider == null || !isVoiceDeliveryProvider(provider.providerName())) {
            challengeRepository.delete(challenge);
            return VoiceChallengeStart.failed("DELIVERY_UNAVAILABLE", null, 0);
        }
        MessageResult delivery;
        try {
            delivery = provider.send(new MessageRequest(tenantId, MessageChannel.SMS,
                    new MessageRecipient(normalizedPhone, null), "Patient verification code",
                    "Your verification code is " + code + ". It expires in " + properties.getOtpTtl().toMinutes()
                            + " minutes. Do not share this code.", null,
                    "VOICE_PATIENT_AUTH", null, null,
                    java.util.Map.of("channel", "VOICE", "callCorrelation", safeCorrelation(providerCallId, interactionId))));
        } catch (RuntimeException failure) {
            challengeRepository.delete(challenge);
            return VoiceChallengeStart.failed("DELIVERY_FAILED", null, 0);
        }
        if (delivery == null || !delivery.success() || (delivery.status() != MessageDeliveryStatus.SENT
                && delivery.status() != MessageDeliveryStatus.QUEUED && delivery.status() != MessageDeliveryStatus.DELIVERED)) {
            challengeRepository.delete(challenge);
            return VoiceChallengeStart.failed("DELIVERY_FAILED", null, 0);
        }
        log.info("patient.portal.voice_auth.challenge_sent tenantId={} challengeId={} provider={}",
                tenantId, challenge.getId(), delivery.providerName());
        return new VoiceChallengeStart(true, "SENT", challenge.getId(), challenge.getExpiresAt(), 0);
    }

    /** Verifies the exact challenge captured in the call-scoped V2 session; no portal token is issued. */
    @Transactional
    public VoiceAuthenticationResult verifyVoiceChallenge(UUID challengeId, UUID tenantId,
            UUID identifiedPatientId, String mobile, String providerCallId, UUID interactionId, String otp) {
        if (challengeId == null || tenantId == null || identifiedPatientId == null
                || !StringUtils.hasText(providerCallId) || properties.getMode() != PatientPortalAuthProperties.Mode.OTP) {
            return VoiceAuthenticationResult.denied("BINDING_MISMATCH", null);
        }
        PatientPortalOtpChallengeEntity challenge = challengeRepository.findById(challengeId).orElse(null);
        String normalizedPhone = normalizePhone(mobile);
        PatientEntity patient = patientRepository.findById(identifiedPatientId).orElse(null);
        if (challenge == null || !tenantId.equals(challenge.getTenantId())
                || !"VOICE".equals(challenge.getChannel())
                || !identifiedPatientId.equals(challenge.getIdentifiedPatientId())
                || !providerCallId.equals(challenge.getProviderCallId())
                || !java.util.Objects.equals(interactionId, challenge.getInteractionId())
                || !normalizedPhone.equals(challenge.getPhoneNormalized())
                || patient == null || !tenantId.equals(patient.getTenantId()) || !patient.isActive()
                || !sameRegisteredMobile(normalizedPhone, normalizePhone(patient.getMobile()))) {
            return VoiceAuthenticationResult.denied("BINDING_MISMATCH", challenge == null ? null : challenge.getExpiresAt());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.isVerified()) return VoiceAuthenticationResult.denied("ALREADY_USED", challenge.getExpiresAt());
        if (challenge.getExpiresAt().isBefore(now)) return VoiceAuthenticationResult.denied("EXPIRED", challenge.getExpiresAt());
        if (challenge.getAttempts() >= properties.getMaxAttempts()) return VoiceAuthenticationResult.denied("LOCKED", challenge.getExpiresAt());
        if (!passwordEncoder.matches(normalizeOtp(otp), challenge.getOtpHash())) {
            challenge.incrementAttempts();
            challengeRepository.save(challenge);
            return VoiceAuthenticationResult.denied(challenge.getAttempts() >= properties.getMaxAttempts()
                    ? "LOCKED" : "FAILED", challenge.getExpiresAt());
        }
        challenge.markVerified();
        challengeRepository.save(challenge);
        String subject = patientSubject(tenantId, identifiedPatientId);
        UUID appUserId = appUserProvisioner.upsertAndReturnId(tenantId, subject, null,
                patient.getFirstName() + " " + patient.getLastName());
        appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .ifPresent(appUser -> appUser.setPatientId(identifiedPatientId));
        return new VoiceAuthenticationResult(true, "VERIFIED", identifiedPatientId, appUserId,
                "OTP", now.toInstant(), now.plus(Duration.ofMinutes(15)).toInstant(), challenge.getExpiresAt());
    }

    /** True only for a previously successful Voice OTP bound to this current patient/mobile/tenant tuple. */
    @Transactional(readOnly = true)
    public boolean hasPreviouslyVerifiedVoicePhoneBinding(UUID tenantId, UUID patientId, String mobile) {
        if (tenantId == null || patientId == null || !StringUtils.hasText(mobile)) return false;
        PatientEntity patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || !tenantId.equals(patient.getTenantId()) || !patient.isActive()) return false;
        String normalizedInbound = normalizePhone(mobile);
        String normalizedStored = normalizePhone(patient.getMobile());
        if (!sameRegisteredMobile(normalizedInbound, normalizedStored)) return false;
        return challengeRepository.existsByTenantIdAndIdentifiedPatientIdAndPhoneNormalizedAndChannelAndVerifiedAtIsNotNull(
                tenantId, patientId, normalizedInbound, "VOICE");
    }

    private String safeCorrelation(String callId, UUID interactionId) {
        return interactionId == null ? callId : interactionId.toString();
    }

    private boolean isVoiceDeliveryProvider(String providerName) {
        if (!StringUtils.hasText(providerName)) return false;
        String normalized = providerName.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("mock") && !normalized.contains("noop")
                && !normalized.contains("not_configured") && !normalized.contains("not-configured")
                && !normalized.startsWith("dev-");
    }

    public record VoiceChallengeStart(boolean sent, String status, UUID challengeId,
                                      OffsetDateTime expiresAt, long retryAfterSeconds) {
        static VoiceChallengeStart failed(String status, UUID challengeId, long retryAfter) {
            return new VoiceChallengeStart(false, status, challengeId, null, retryAfter);
        }
    }

    public record VoiceAuthenticationResult(boolean verified, String status, UUID authenticatedPatientId,
            UUID appUserId, String authenticationMethod, java.time.Instant verifiedAt,
            java.time.Instant expiresAt, OffsetDateTime challengeExpiresAt) {
        static VoiceAuthenticationResult denied(String status, OffsetDateTime expiry) {
            return new VoiceAuthenticationResult(false, status, null, null, null, null, null, expiry);
        }
    }

    @Transactional
    public PatientPortalOtpRequestResponse requestOtp(String tenantCode, String phone) {
        return requestOtp(phone, new PatientPortalOtpContext(null, tenantCode, null, null, null));
    }

    @Transactional
    public PatientPortalOtpRequestResponse requestOtp(String mobile, PatientPortalOtpContext context) {
        ensureOtpModeEnabled();
        TenantEntity tenant = resolveTenant(context);
        UUID tenantId = tenant == null ? null : tenant.getId();
        String normalizedPhone = normalizePhone(mobile);
        OffsetDateTime now = OffsetDateTime.now();

        var latestChallenge = tenantId == null
                ? challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc(normalizedPhone)
                : challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(tenantId, normalizedPhone);
        if (latestChallenge.isPresent()) {
            long secondsSinceLast = Duration.between(latestChallenge.get().getCreatedAt(), now).getSeconds();
            long cooldownSeconds = properties.getResendCooldown().getSeconds();
            if (secondsSinceLast < cooldownSeconds) {
                long remaining = Math.max(0, cooldownSeconds - secondsSinceLast);
                return new PatientPortalOtpRequestResponse(
                        false,
                        "Please wait before requesting another OTP.",
                        properties.getOtpTtl().getSeconds(),
                        remaining,
                        null
                );
            }
        }

        String devOtp = generateOtp();
        String otpHash = passwordEncoder.encode(devOtp);
        OffsetDateTime expiresAt = now.plus(properties.getOtpTtl());

        challengeRepository.save(PatientPortalOtpChallengeEntity.create(
                tenantId,
                normalizedPhone,
                otpHash,
                expiresAt
        ));

        if (properties.isExposeDevOtp()) {
            log.info("patient.portal.otp.generated tenantId={} phone={} devOtp={}", tenantId, normalizedPhone, devOtp);
        } else {
            log.info("patient.portal.otp.generated tenantId={} phone={}", tenantId, normalizedPhone);
        }

        return new PatientPortalOtpRequestResponse(
                true,
                "OTP generated for patient portal login.",
                properties.getOtpTtl().getSeconds(),
                properties.getResendCooldown().getSeconds(),
                properties.getMode() == PatientPortalAuthProperties.Mode.ACCESS_APPROVAL ? null : properties.isExposeDevOtp() ? devOtp : null
        );
    }

    @Transactional
    public PatientPortalOtpVerifyResponse verifyOtp(String tenantCode, String phone, String otp) {
        return verifyOtp(phone, otp, new PatientPortalOtpContext(null, tenantCode, null, null, null));
    }

    @Transactional
    public PatientPortalOtpVerifyResponse verifyOtp(String mobile, String otp, PatientPortalOtpContext context) {
        ensureOtpModeEnabled();
        TenantEntity tenant = resolveTenant(context);
        String normalizedPhone = normalizePhone(mobile);
        String normalizedOtp = normalizeOtp(otp);

        UUID tenantId = tenant == null ? null : tenant.getId();
        var latestChallenge = tenantId == null
                ? challengeRepository.findTopByPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(normalizedPhone)
                : challengeRepository.findTopByTenantIdAndPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(
                        tenantId, normalizedPhone);
        if (latestChallenge.isEmpty()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, SAFE_VERIFY_FAILURE_MESSAGE, null, null, null, null, null);
        }

        PatientPortalOtpChallengeEntity challenge = latestChallenge.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.isVerified()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "A new OTP request is required.", null, null, null, null, null);
        }
        if (challenge.getExpiresAt().isBefore(now)) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "OTP expired. Request a new code.", null, null, null, null, null);
        }
        if (challenge.getAttempts() >= properties.getMaxAttempts()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "OTP attempts exceeded. Request a new code.", null, null, null, null, null);
        }
        if (!passwordEncoder.matches(normalizedOtp, challenge.getOtpHash())) {
            challenge.incrementAttempts();
            challengeRepository.save(challenge);
            if (challenge.getAttempts() >= properties.getMaxAttempts()) {
                return new PatientPortalOtpVerifyResponse(false, false, false, "OTP attempts exceeded. Request a new code.", null, null, null, null, null);
            }
            return new PatientPortalOtpVerifyResponse(false, false, false, "Invalid OTP. Please try again.", null, null, null, null, null);
        }

        challenge.markVerified();
        challengeRepository.save(challenge);

        Optional<PatientEntity> patient = tenantId == null
                ? resolvePatientByMobileForLogin(normalizedPhone)
                : resolvePrimaryActivePatientByTenantAndMobile(tenantId, normalizedPhone);

        if (patient.isEmpty()) {
            if (tenant == null) {
                tenant = resolveDirectRegistrationTenant();
            }
            String registrationToken = null;
            String tenantCode = null;
            String tenantIdValue = null;
            if (tenant != null) {
                String registrationSubject = registrationSubject(tenant.getId(), normalizedPhone);
                registrationToken = sessionTokenService.issueRegistrationToken(
                        registrationSubject,
                        tenant.getId(),
                        normalizedPhone,
                        "New patient"
                );
                tenantIdValue = tenant.getId().toString();
                tenantCode = tenant.getCode();
            }
            return new PatientPortalOtpVerifyResponse(
                    true,
                    false,
                    true,
                    "OTP verified. Complete your quick registration to open the patient portal.",
                    tenantIdValue,
                    tenantCode,
                    null,
                    null,
                    registrationToken
            );
        }

        PatientEntity matchedPatient = patient.get();
        if (tenant == null) {
            tenant = tenantRepository.findById(matchedPatient.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Matched patient tenant was not found"));
            tenantId = tenant.getId();
        }
        String subject = patientSubject(tenant.getId(), matchedPatient.getId());
        UUID appUserId = appUserProvisioner.upsertAndReturnId(
                tenant.getId(),
                subject,
                null,
                matchedPatient.getFirstName() + " " + matchedPatient.getLastName()
        );
        appUserRepository.findByTenantIdAndId(tenant.getId(), appUserId)
                .ifPresent(appUser -> appUser.setPatientId(matchedPatient.getId()));

        String sessionToken = sessionTokenService.issuePatientToken(
                subject,
                tenant.getId(),
                matchedPatient.getId(),
                normalizedPhone,
                matchedPatient.getFirstName() + " " + matchedPatient.getLastName()
        );

        return new PatientPortalOtpVerifyResponse(
                true,
                true,
                false,
                "Patient portal session verified.",
                tenant.getId().toString(),
                tenant.getCode(),
                matchedPatient.getFirstName() + " " + matchedPatient.getLastName(),
                sessionToken,
                null
        );
    }

    private TenantEntity resolveTenant(PatientPortalOtpContext context) {
        if (context == null) {
            return null;
        }
        if (StringUtils.hasText(context.tenantId())) {
            try {
                UUID tenantId = UUID.fromString(context.tenantId().trim());
                TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
                if (tenant != null) {
                    return tenant;
                }
            } catch (IllegalArgumentException ex) {
                log.warn("patient.portal.otp.invalid_tenant_id tenantId={}", context.tenantId());
            }
        }
        if (StringUtils.hasText(context.clinicSlug())) {
            String normalized = context.clinicSlug().trim().toLowerCase(Locale.ROOT);
            return tenantRepository.findByCode(normalized).orElse(null);
        }
        return null;
    }

    private void ensureOtpModeEnabled() {
        if (properties.getMode() == PatientPortalAuthProperties.Mode.ACCESS_APPROVAL) {
            throw new ForbiddenException("OTP login is not available in this Care environment.");
        }
    }

    private TenantEntity resolveDirectRegistrationTenant() {
        return tenantRepository.findByCode(DEMO_CLINIC_CODE).orElse(null);
    }

    private String normalizePhone(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            throw new IllegalArgumentException("Phone is required");
        }
        String normalized = rawPhone.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        return normalized;
    }

    private boolean sameRegisteredMobile(String callerDigits, String storedDigits) {
        if (callerDigits.equals(storedDigits)) return true;
        return callerDigits.length() == 12 && callerDigits.startsWith("91")
                && callerDigits.substring(2).equals(storedDigits)
                || storedDigits.length() == 12 && storedDigits.startsWith("91")
                && storedDigits.substring(2).equals(callerDigits);
    }

    private String normalizeOtp(String rawOtp) {
        if (!StringUtils.hasText(rawOtp)) {
            throw new IllegalArgumentException("OTP is required");
        }
        String normalized = rawOtp.replaceAll("[^0-9]", "");
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("OTP must be 6 digits");
        }
        return normalized;
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String patientSubject(UUID tenantId, UUID patientId) {
        return "patientportal:" + tenantId + ":" + patientId;
    }

    private String registrationSubject(UUID tenantId, String phone) {
        return "patientportal-registration:" + tenantId + ":" + phone;
    }

    private Optional<PatientEntity> resolveUniqueActivePatientByMobile(String normalizedPhone) {
        List<PatientEntity> candidates = patientRepository.findByMobileIgnoreCaseAndActiveTrue(normalizedPhone);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() > 1) {
            log.warn("patient.portal.otp.ambiguous_mobile phone={} matches={}", normalizedPhone, candidates.size());
            return Optional.empty();
        }
        return Optional.of(candidates.get(0));
    }

    private Optional<PatientEntity> resolvePatientByMobileForLogin(String normalizedPhone) {
        List<PatientEntity> candidates = patientRepository.findByMobileIgnoreCaseAndActiveTrue(normalizedPhone);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        List<PatientEntity> linkedCandidates = new ArrayList<>();
        for (PatientEntity candidate : candidates) {
            if (hasActivePortalAccount(candidate.getId())) {
                linkedCandidates.add(candidate);
            }
        }
        if (linkedCandidates.size() == 1) {
            PatientEntity linkedPatient = linkedCandidates.get(0);
            log.info(
                    "patient.portal.otp.linked_mobile_resolved phone={} patientId={} tenantId={}",
                    normalizedPhone,
                    linkedPatient.getId(),
                    linkedPatient.getTenantId()
            );
            return Optional.of(linkedPatient);
        }
        if (linkedCandidates.size() > 1) {
            PatientEntity primary = linkedCandidates.stream()
                    .min(Comparator.comparing(PatientEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(PatientEntity::getId))
                    .orElse(linkedCandidates.get(0));
            log.warn(
                    "patient.portal.otp.linked_mobile_ambiguous phone={} matches={} linkedMatches={} primaryPatientId={}",
                    normalizedPhone,
                    candidates.size(),
                    linkedCandidates.size(),
                    primary.getId()
            );
            return Optional.of(primary);
        }

        log.warn("patient.portal.otp.ambiguous_mobile phone={} matches={}", normalizedPhone, candidates.size());
        return Optional.empty();
    }

    private boolean hasActivePortalAccount(UUID patientId) {
        if (patientId == null) {
            return false;
        }
        return appUserRepository.findByPatientId(patientId).stream()
                .anyMatch(appUser -> appUser != null && "ACTIVE".equalsIgnoreCase(appUser.getStatus()));
    }

    private Optional<PatientEntity> resolvePrimaryActivePatientByTenantAndMobile(UUID tenantId, String normalizedPhone) {
        List<PatientEntity> candidates = patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, normalizedPhone);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        PatientEntity primary = candidates.stream()
                .min(Comparator.comparing(PatientEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientEntity::getId))
                .orElse(candidates.get(0));
        if (candidates.size() > 1) {
            log.warn("patient.portal.otp.duplicate_mobile tenantId={} phone={} matches={} primaryPatientId={}", tenantId, normalizedPhone, candidates.size(), primary.getId());
        }
        return Optional.of(primary);
    }
}
