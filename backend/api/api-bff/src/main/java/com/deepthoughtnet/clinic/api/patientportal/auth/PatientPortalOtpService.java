package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpRequestResponse;
import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpVerifyResponse;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalOtpService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalOtpService.class);
    private static final String SAFE_VERIFY_FAILURE_MESSAGE = "Unable to verify patient access for this clinic.";

    private final TenantRepository tenantRepository;
    private final PatientRepository patientRepository;
    private final AppUserProvisioner appUserProvisioner;
    private final AppUserRepository appUserRepository;
    private final PatientPortalOtpChallengeRepository challengeRepository;
    private final PatientPortalSessionTokenService sessionTokenService;
    private final PatientPortalAuthProperties properties;
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
        this.tenantRepository = tenantRepository;
        this.patientRepository = patientRepository;
        this.appUserProvisioner = appUserProvisioner;
        this.appUserRepository = appUserRepository;
        this.challengeRepository = challengeRepository;
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
    }

    @Transactional
    public PatientPortalOtpRequestResponse requestOtp(String tenantCode, String phone) {
        TenantEntity tenant = resolveTenant(tenantCode);
        String normalizedPhone = normalizePhone(phone);
        OffsetDateTime now = OffsetDateTime.now();

        var latestChallenge = challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(tenant.getId(), normalizedPhone);
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
                tenant.getId(),
                normalizedPhone,
                otpHash,
                expiresAt
        ));

        if (properties.isExposeDevOtp()) {
            log.info("patient.portal.otp.generated tenantId={} phone={} devOtp={}", tenant.getId(), normalizedPhone, devOtp);
        } else {
            log.info("patient.portal.otp.generated tenantId={} phone={}", tenant.getId(), normalizedPhone);
        }

        return new PatientPortalOtpRequestResponse(
                true,
                "OTP generated for patient portal login.",
                properties.getOtpTtl().getSeconds(),
                properties.getResendCooldown().getSeconds(),
                properties.isExposeDevOtp() ? devOtp : null
        );
    }

    @Transactional
    public PatientPortalOtpVerifyResponse verifyOtp(String tenantCode, String phone, String otp) {
        TenantEntity tenant = resolveTenant(tenantCode);
        String normalizedPhone = normalizePhone(phone);
        String normalizedOtp = normalizeOtp(otp);

        var latestChallenge = challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(tenant.getId(), normalizedPhone);
        if (latestChallenge.isEmpty()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, SAFE_VERIFY_FAILURE_MESSAGE, null, null, null, null);
        }

        PatientPortalOtpChallengeEntity challenge = latestChallenge.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.isVerified()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "A new OTP request is required.", null, null, null, null);
        }
        if (challenge.getExpiresAt().isBefore(now)) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "OTP expired. Request a new code.", null, null, null, null);
        }
        if (challenge.getAttempts() >= properties.getMaxAttempts()) {
            return new PatientPortalOtpVerifyResponse(false, false, false, "OTP attempts exceeded. Request a new code.", null, null, null, null);
        }
        if (!passwordEncoder.matches(normalizedOtp, challenge.getOtpHash())) {
            challenge.incrementAttempts();
            challengeRepository.save(challenge);
            if (challenge.getAttempts() >= properties.getMaxAttempts()) {
                return new PatientPortalOtpVerifyResponse(false, false, false, "OTP attempts exceeded. Request a new code.", null, null, null, null);
            }
            return new PatientPortalOtpVerifyResponse(false, false, false, "Invalid OTP. Please try again.", null, null, null, null);
        }

        challenge.markVerified();
        challengeRepository.save(challenge);

        var patient = patientRepository.findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenant.getId(), normalizedPhone);
        if (patient.isEmpty()) {
            String registrationSubject = registrationSubject(tenant.getId(), normalizedPhone);
            String registrationToken = sessionTokenService.issueRegistrationToken(
                    registrationSubject,
                    tenant.getId(),
                    normalizedPhone,
                    "New patient"
            );
            return new PatientPortalOtpVerifyResponse(
                    true,
                    false,
                    true,
                    "OTP verified. Complete your quick registration to open the patient portal.",
                    tenant.getId().toString(),
                    null,
                    null,
                    registrationToken
            );
        }

        PatientEntity matchedPatient = patient.get();
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
                matchedPatient.getFirstName() + " " + matchedPatient.getLastName()
        );

        return new PatientPortalOtpVerifyResponse(
                true,
                true,
                false,
                "Patient portal session verified.",
                tenant.getId().toString(),
                matchedPatient.getFirstName() + " " + matchedPatient.getLastName(),
                sessionToken,
                null
        );
    }

    private TenantEntity resolveTenant(String tenantCode) {
        String normalized = StringUtils.hasText(tenantCode) ? tenantCode.trim().toLowerCase(Locale.ROOT) : null;
        return tenantRepository.findByCode(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tenant code"));
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
}
