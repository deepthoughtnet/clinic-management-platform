package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionPrincipal;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionTokenService;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalRegistrationRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalRegistrationResponse;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class PatientPortalRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalRegistrationService.class);
    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final AppUserProvisioner appUserProvisioner;
    private final AppUserRepository appUserRepository;
    private final PatientPortalSessionTokenService sessionTokenService;

    public PatientPortalRegistrationService(
            PatientRepository patientRepository,
            PatientService patientService,
            AppUserProvisioner appUserProvisioner,
            AppUserRepository appUserRepository,
            PatientPortalSessionTokenService sessionTokenService
    ) {
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.appUserProvisioner = appUserProvisioner;
        this.appUserRepository = appUserRepository;
        this.sessionTokenService = sessionTokenService;
    }

    public PatientPortalRegistrationResponse complete(PatientPortalRegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request is required");
        }
        if (request.dateOfBirth() == null && request.ageYears() == null) {
            throw new IllegalArgumentException("Date of birth or age is required");
        }

        PatientPortalSessionPrincipal principal = requireRegistrationPrincipal();
        UUID tenantId = principal.tenantId();
        String verifiedPhone = requirePhone(principal.phone());
        UUID actorAppUserId = requireActorAppUserId();

        PatientEntity patient = resolvePrimaryActivePatientByTenantAndMobile(tenantId, verifiedPhone);
        boolean linkedExistingPatient = patient != null;
        if (patient == null) {
            var created = patientService.create(
                    tenantId,
                    new PatientUpsertCommand(
                            request.firstName(),
                            request.lastName(),
                            request.gender(),
                            request.dateOfBirth(),
                            request.ageYears(),
                            verifiedPhone,
                            request.email(),
                            request.addressLine1(),
                            request.addressLine2(),
                            request.city(),
                            request.state(),
                            request.country(),
                            request.postalCode(),
                            request.emergencyContactName(),
                            request.emergencyContactMobile(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true
                    ),
                    actorAppUserId
            );
            patient = patientRepository.findByTenantIdAndId(tenantId, created.id())
                    .orElseThrow(() -> new IllegalStateException("Registered patient record was not found"));
        }

        String patientDisplayName = fullName(patient.getFirstName(), patient.getLastName());
        UUID patientId = patient.getId();
        String patientEmail = patient.getEmail();
        String patientSubject = patientSubject(tenantId, patientId);
        UUID patientAppUserId = appUserProvisioner.upsertAndReturnId(
                tenantId,
                patientSubject,
                patientEmail,
                patientDisplayName
        );
        appUserRepository.findByTenantIdAndId(tenantId, patientAppUserId)
                .ifPresent(appUser -> {
                    appUser.setPatientId(patientId);
                    appUser.updateProfile(patientEmail, patientDisplayName);
                });
        appUserRepository.findByTenantIdAndId(tenantId, actorAppUserId)
                .ifPresent(appUser -> appUser.setPatientId(patientId));

        String patientSessionToken = sessionTokenService.issuePatientToken(
                patientSubject,
                tenantId,
                patientId,
                verifiedPhone,
                patientDisplayName
        );

        return new PatientPortalRegistrationResponse(
                !linkedExistingPatient,
                linkedExistingPatient,
                linkedExistingPatient
                        ? "An existing patient profile was linked to your verified mobile number."
                        : "Your patient profile is ready. You can continue to the portal and booking now.",
                tenantId.toString(),
                patientDisplayName,
                patientSessionToken
        );
    }

    private PatientEntity resolvePrimaryActivePatientByTenantAndMobile(UUID tenantId, String verifiedPhone) {
        List<PatientEntity> candidates = patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, verifiedPhone);
        if (candidates.isEmpty()) {
            return null;
        }
        PatientEntity primary = candidates.stream()
                .min(Comparator.comparing(PatientEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientEntity::getId))
                .orElse(candidates.get(0));
        if (candidates.size() > 1) {
            log.warn("patient.portal.registration.duplicate_mobile tenantId={} phone={} matches={} primaryPatientId={}", tenantId, verifiedPhone, candidates.size(), primary.getId());
        }
        return primary;
    }

    private PatientPortalSessionPrincipal requireRegistrationPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PatientPortalSessionPrincipal principal)) {
            throw new UnauthorizedException("Patient registration session is missing");
        }
        if (principal.roles() == null || !principal.roles().contains("PATIENT_REGISTRATION")) {
            throw new ForbiddenException("Patient registration is not available for this session");
        }
        if (principal.tenantId() == null) {
            throw new UnauthorizedException("Patient registration tenant context is missing");
        }
        return principal;
    }

    private UUID requireActorAppUserId() {
        UUID appUserId = RequestContextHolder.require().appUserId();
        if (appUserId == null) {
            throw new UnauthorizedException("Patient registration actor is missing");
        }
        return appUserId;
    }

    private String requirePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new UnauthorizedException("Verified mobile number is missing");
        }
        String normalized = phone.trim().replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9]{10}")) {
            throw new UnauthorizedException("Verified mobile number is missing");
        }
        return normalized;
    }

    private String fullName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        return (first + " " + last).trim();
    }

    private String patientSubject(UUID tenantId, UUID patientId) {
        return "patientportal:" + tenantId + ":" + patientId;
    }
}
