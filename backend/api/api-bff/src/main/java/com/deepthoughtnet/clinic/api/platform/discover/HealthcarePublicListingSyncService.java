package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.DoctorAvailabilityQueryService;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class HealthcarePublicListingSyncService {
    private static final String SOURCE_SYSTEM_CLINIC = "HEALTHCARE_CLINIC";
    private static final String SOURCE_SYSTEM_DOCTOR = "HEALTHCARE_DOCTOR";
    private static final String BOOKING_MODE_ONLINE = "ONLINE_BOOKING";
    private static final String BOOKING_MODE_CALL = "CALL_TO_BOOK";

    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final DoctorAvailabilityQueryService doctorAvailabilityQueryService;
    private final ClinicalDocumentService clinicalDocumentService;
    private final ProviderPublicProfileService publicProfileService;

    public HealthcarePublicListingSyncService(
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorAvailabilityQueryService doctorAvailabilityQueryService,
            ClinicalDocumentService clinicalDocumentService,
            ProviderPublicProfileService publicProfileService
    ) {
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.doctorAvailabilityQueryService = doctorAvailabilityQueryService;
        this.clinicalDocumentService = clinicalDocumentService;
        this.publicProfileService = publicProfileService;
    }

    @Transactional
    public HealthcarePublicListingSyncSummary syncTenant(UUID tenantId, UUID actorAppUserId, String reason) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<HealthcarePublicListingSyncOutcome> outcomes = new ArrayList<>();

        HealthcarePublicListingSyncOutcome clinicOutcome = syncClinic(tenantId, actorAppUserId, reason);
        outcomes.add(clinicOutcome);
        inserted += clinicOutcome.inserted();
        updated += clinicOutcome.updated();
        skipped += clinicOutcome.skipped();
        failed += clinicOutcome.failed();

        for (DoctorProfileRecord doctor : doctorProfileService.findByTenantIdAndActive(tenantId)) {
            HealthcarePublicListingSyncOutcome outcome = syncDoctor(tenantId, doctor, actorAppUserId, reason);
            outcomes.add(outcome);
            inserted += outcome.inserted();
            updated += outcome.updated();
            skipped += outcome.skipped();
            failed += outcome.failed();
        }

        return new HealthcarePublicListingSyncSummary(inserted, updated, skipped, failed, outcomes);
    }

    @Transactional
    public HealthcarePublicListingSyncOutcome syncClinic(UUID tenantId, UUID actorAppUserId, String reason) {
        Optional<ClinicProfileRecord> clinicOpt = clinicProfileService.findByTenantId(tenantId);
        if (clinicOpt.isEmpty()) {
            return HealthcarePublicListingSyncOutcome.skipped(SOURCE_SYSTEM_CLINIC, tenantId, null, "Clinic profile not found");
        }
        ClinicProfileRecord clinic = clinicOpt.get();
        if (!clinic.active() || !clinic.publicListingEnabled() || !StringUtils.hasText(clinic.displayName())
                || !StringUtils.hasText(clinic.addressLine1()) || !StringUtils.hasText(clinic.city()) || !StringUtils.hasText(clinic.state())) {
            publicProfileService.unpublishPublicProfile(clinic.id(), SOURCE_SYSTEM_CLINIC, reason);
            return HealthcarePublicListingSyncOutcome.unpublished(SOURCE_SYSTEM_CLINIC, clinic.id(), clinic.slug(), "Clinic public listing disabled or incomplete");
        }

        String slug = ensureClinicSlug(tenantId, clinic, actorAppUserId);
        boolean onlineBookable = hasAnyBookableDoctor(tenantId);
        String bookingMode = onlineBookable ? BOOKING_MODE_ONLINE : BOOKING_MODE_CALL;
        UUID logoDocumentId = syncClinicLogo(clinic, tenantId);
        List<DoctorProfileRecord> doctors = doctorProfileService.findByTenantIdAndActive(tenantId).stream()
                .filter(DoctorProfileRecord::publicListingEnabled)
                .toList();
        List<String> specialities = doctors.stream()
                .map(this::firstDoctorSpeciality)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<PublicProviderLocationSnapshot> locations = List.of(new PublicProviderLocationSnapshot(
                firstNonBlank(clinic.displayName(), "Primary"),
                clinic.addressLine1(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                clinic.postalCode(),
                null,
                false,
                false,
                null,
                null
        ));

        PublicProviderProfileSnapshot snapshot = PublicProviderProfileModels.healthcareClinicSnapshot(
                clinic.id(),
                SOURCE_SYSTEM_CLINIC,
                clinic.registrationNumber(),
                slug,
                clinic.displayName(),
                clinic.clinicName(),
                firstNonBlank(clinic.displayName(), clinic.addressLine1(), clinic.city()),
                specialities,
                locations,
                logoDocumentId,
                clinic.phone(),
                clinic.email(),
                clinic.city(),
                clinic.addressLine1(),
                clinic.state(),
                clinic.country(),
                bookingMode,
                OffsetDateTime.now(),
                1,
                "/discover/clinics/" + slug,
                doctors.size()
        );

        var publication = publicProfileService.upsertLifecycleProfile(
                snapshot,
                1,
                null,
                null,
                reason,
                OffsetDateTime.now(),
                PublicationStatus.PUBLISHED.name(),
                SOURCE_SYSTEM_CLINIC,
                clinic.id().toString(),
                clinic.updatedAt() == null ? 0L : clinic.updatedAt().toInstant().toEpochMilli(),
                clinic.updatedAt(),
                0L
        );
        return HealthcarePublicListingSyncOutcome.updated(SOURCE_SYSTEM_CLINIC, clinic.id(), slug, bookingMode, "Clinic projected into Discover");
    }

    @Transactional
    public HealthcarePublicListingSyncOutcome syncDoctor(UUID tenantId, DoctorProfileRecord doctor, UUID actorAppUserId, String reason) {
        if (doctor == null || doctor.doctorUserId() == null) {
            return HealthcarePublicListingSyncOutcome.skipped(SOURCE_SYSTEM_DOCTOR, null, null, "Doctor profile not found");
        }
        Optional<TenantUserRecord> doctorUserOpt = tenantUserManagementService.list(tenantId).stream()
                .filter(user -> doctor.doctorUserId().equals(user.appUserId()))
                .filter(user -> user.membershipRole() != null && user.membershipRole().equalsIgnoreCase("DOCTOR"))
                .findFirst();
        if (doctorUserOpt.isEmpty()) {
            publicProfileService.unpublishPublicProfile(doctor.doctorUserId(), SOURCE_SYSTEM_DOCTOR, reason);
            return HealthcarePublicListingSyncOutcome.unpublished(SOURCE_SYSTEM_DOCTOR, doctor.doctorUserId(), doctor.slug(), "Doctor user not available in tenant");
        }
        TenantUserRecord doctorUser = doctorUserOpt.get();
        Optional<ClinicProfileRecord> clinicOpt = clinicProfileService.findByTenantId(tenantId);
        if (clinicOpt.isEmpty() || !clinicOpt.get().active() || !clinicOpt.get().publicListingEnabled()) {
            publicProfileService.unpublishPublicProfile(doctor.doctorUserId(), SOURCE_SYSTEM_DOCTOR, reason);
            return HealthcarePublicListingSyncOutcome.unpublished(SOURCE_SYSTEM_DOCTOR, doctor.doctorUserId(), doctor.slug(), "Parent clinic is not publicly listed");
        }
        if (!doctor.active() || !doctor.publicListingEnabled() || !StringUtils.hasText(doctorUser.displayName())
                || firstDoctorSpeciality(doctor) == null || doctor.qualification() == null || doctor.qualification().isBlank()) {
            publicProfileService.unpublishPublicProfile(doctor.doctorUserId(), SOURCE_SYSTEM_DOCTOR, reason);
            return HealthcarePublicListingSyncOutcome.unpublished(SOURCE_SYSTEM_DOCTOR, doctor.doctorUserId(), doctor.slug(), "Doctor listing is inactive or incomplete");
        }

        ClinicProfileRecord clinic = clinicOpt.get();
        String slug = ensureDoctorSlug(tenantId, doctor, doctorUser, actorAppUserId);
        String bookingMode = hasActiveAvailability(tenantId, doctor.doctorUserId()) ? BOOKING_MODE_ONLINE : BOOKING_MODE_CALL;
        UUID photoDocumentId = syncDoctorPhoto(tenantId, doctor);
        List<String> specialities = doctor.specializations() == null ? List.of() : doctor.specializations().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<PublicProviderLocationSnapshot> locations = List.of(new PublicProviderLocationSnapshot(
                firstNonBlank(clinic.displayName(), clinic.clinicName()),
                clinic.addressLine1(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                clinic.postalCode(),
                null,
                false,
                false,
                null,
                null
        ));

        BigDecimal consultationFee = doctor.consultationFee() != null ? doctor.consultationFee() : doctor.opdFee();
        PublicProviderProfileSnapshot snapshot = PublicProviderProfileModels.healthcareDoctorSnapshot(
                doctor.doctorUserId(),
                SOURCE_SYSTEM_DOCTOR,
                doctor.registrationNumber(),
                slug,
                doctorUser.displayName(),
                doctorUser.displayName(),
                firstNonBlank(doctor.qualification(), doctor.specialization(), doctorUser.displayName()),
                doctor.qualification(),
                doctor.qualification(),
                null,
                doctor.yearsOfExperience(),
                consultationFee,
                null,
                false,
                specialities,
                locations,
                photoDocumentId,
                doctor.mobile(),
                doctorUser.email(),
                clinic.city(),
                clinic.addressLine1(),
                clinic.state(),
                clinic.country(),
                bookingMode,
                OffsetDateTime.now(),
                1,
                "/discover/doctors/" + slug
        );

        publicProfileService.upsertLifecycleProfile(
                snapshot,
                1,
                null,
                null,
                reason,
                OffsetDateTime.now(),
                PublicationStatus.PUBLISHED.name(),
                SOURCE_SYSTEM_DOCTOR,
                doctor.doctorUserId().toString(),
                doctor.updatedAt() == null ? 0L : doctor.updatedAt().toInstant().toEpochMilli(),
                doctor.updatedAt(),
                0L
        );
        return HealthcarePublicListingSyncOutcome.updated(SOURCE_SYSTEM_DOCTOR, doctor.doctorUserId(), slug, bookingMode, "Doctor projected into Discover");
    }

    private UUID syncClinicLogo(ClinicProfileRecord clinic, UUID tenantId) {
        if (clinic.logoDocumentId() == null) {
            return null;
        }
        ClinicalDocumentRecord document = clinicalDocumentService.get(tenantId, clinic.logoDocumentId());
        byte[] bytes = clinicalDocumentService.downloadBytes(tenantId, clinic.logoDocumentId());
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return publicProfileService.upsertPublishedMedia(
                clinic.id(),
                ProviderDocumentType.LOGO,
                document.originalFilename(),
                document.mediaType(),
                bytes
        );
    }

    private UUID syncDoctorPhoto(UUID tenantId, DoctorProfileRecord doctor) {
        if (doctor.doctorUserId() == null) {
            return null;
        }
        try {
            var photo = doctorProfileService.downloadPhoto(tenantId, doctor.doctorUserId());
            if (photo.bytes() == null || photo.bytes().length == 0) {
                return null;
            }
            return publicProfileService.upsertPublishedMedia(
                    doctor.doctorUserId(),
                    ProviderDocumentType.DOCTOR_PHOTO,
                    photo.fileName(),
                    photo.contentType(),
                    photo.bytes()
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean hasAnyBookableDoctor(UUID tenantId) {
        for (DoctorProfileRecord doctor : doctorProfileService.findByTenantIdAndActive(tenantId)) {
            if (!doctor.publicListingEnabled()) {
                continue;
            }
            if (hasActiveAvailability(tenantId, doctor.doctorUserId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveAvailability(UUID tenantId, UUID doctorUserId) {
        return doctorAvailabilityQueryService.hasActiveAvailability(tenantId, doctorUserId);
    }

    private String ensureClinicSlug(UUID tenantId, ClinicProfileRecord clinic, UUID actorAppUserId) {
        String current = normalizeSlug(clinic.slug());
        if (StringUtils.hasText(current) && !publicProfileService.isSlugReserved(current, clinic.id())) {
            return current;
        }
        String base = slugify(firstNonBlank(clinic.displayName(), clinic.clinicName(), clinic.city(), "clinic"));
        String candidate = ensureUniqueSlug(base, clinic.id());
        clinicProfileService.upsert(
                tenantId,
                new com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand(
                        clinic.clinicName(),
                        clinic.displayName(),
                        clinic.phone(),
                        clinic.email(),
                        clinic.addressLine1(),
                        clinic.addressLine2(),
                        clinic.city(),
                        clinic.state(),
                        clinic.country(),
                        clinic.postalCode(),
                        clinic.registrationNumber(),
                        clinic.gstNumber(),
                        clinic.logoDocumentId(),
                        clinic.active(),
                        clinic.publicListingEnabled(),
                        candidate
                ),
                actorAppUserId
        );
        return candidate;
    }

    private String ensureDoctorSlug(UUID tenantId, DoctorProfileRecord doctor, TenantUserRecord doctorUser, UUID actorAppUserId) {
        String current = normalizeSlug(doctor.slug());
        if (StringUtils.hasText(current) && !publicProfileService.isSlugReserved(current, doctor.doctorUserId())) {
            return current;
        }
        String base = slugify(firstNonBlank(doctorUser.displayName(), doctor.specialization(), "doctor"));
        String candidate = ensureUniqueSlug(base, doctor.doctorUserId());
        doctorProfileService.upsert(
                tenantId,
                doctor.doctorUserId(),
                new DoctorProfileUpsertCommand(
                        doctor.mobile(),
                        doctor.specialization(),
                        doctor.specializations(),
                        doctor.qualification(),
                        doctor.registrationNumber(),
                        doctor.consultationRoom(),
                        doctor.consultationFee(),
                        doctor.opdFee(),
                        doctor.followUpFee(),
                        doctor.emergencyFee(),
                        doctor.yearsOfExperience(),
                        doctor.age(),
                        doctor.active(),
                        doctor.publicListingEnabled(),
                        candidate
                )
        );
        return candidate;
    }

    private String ensureUniqueSlug(String requestedSlug, UUID providerId) {
        String base = StringUtils.hasText(requestedSlug) ? requestedSlug : "provider";
        String candidate = base;
        int suffix = 2;
        while (publicProfileService.isSlugReserved(candidate, providerId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String firstDoctorSpeciality(DoctorProfileRecord doctor) {
        if (doctor == null || doctor.specializations() == null || doctor.specializations().isEmpty()) {
            return doctor == null ? null : doctor.specialization();
        }
        return doctor.specializations().stream().filter(StringUtils::hasText).findFirst().orElse(doctor.specialization());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private String normalizeSlug(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String slugify(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record HealthcarePublicListingSyncOutcome(
            String sourceSystem,
            UUID sourceReference,
            String slug,
            String bookingMode,
            int inserted,
            int updated,
            int skipped,
            int failed,
            String message
    ) {
        static HealthcarePublicListingSyncOutcome skipped(String sourceSystem, UUID sourceReference, String slug, String message) {
            return new HealthcarePublicListingSyncOutcome(sourceSystem, sourceReference, slug, null, 0, 0, 1, 0, message);
        }

        static HealthcarePublicListingSyncOutcome unpublished(String sourceSystem, UUID sourceReference, String slug, String message) {
            return new HealthcarePublicListingSyncOutcome(sourceSystem, sourceReference, slug, null, 0, 0, 1, 0, message);
        }

        static HealthcarePublicListingSyncOutcome updated(String sourceSystem, UUID sourceReference, String slug, String bookingMode, String message) {
            return new HealthcarePublicListingSyncOutcome(sourceSystem, sourceReference, slug, bookingMode, 0, 1, 0, 0, message);
        }
    }

    public record HealthcarePublicListingSyncSummary(
            int inserted,
            int updated,
            int skipped,
            int failed,
            List<HealthcarePublicListingSyncOutcome> outcomes
    ) {
    }
}
