package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PublicCatalogFacade {
    private final PlatformTenantManagementService platformTenantManagementService;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final DoctorProfileService doctorProfileService;

    public PublicCatalogFacade(
            PlatformTenantManagementService platformTenantManagementService,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorProfileService doctorProfileService
    ) {
        this.platformTenantManagementService = platformTenantManagementService;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.doctorProfileService = doctorProfileService;
    }

    public List<PublicClinicSummaryResponse> listClinics(String q, String city, String speciality, String tenantCode) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        String normalizedSpeciality = normalize(speciality);

        return loadTenantSnapshots(tenantCode).stream()
                .map(snapshot -> toClinicSummary(snapshot, normalizedSpeciality))
                .filter(Objects::nonNull)
                .filter(clinic -> matchesClinic(clinic, normalizedQuery, normalizedCity, normalizedSpeciality))
                .sorted(Comparator.comparing(PublicClinicSummaryResponse::clinicDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<PublicDoctorSummaryResponse> listDoctors(String q, String city, String speciality, String tenantCode) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        String normalizedSpeciality = normalize(speciality);

        return loadTenantSnapshots(tenantCode).stream()
                .flatMap(snapshot -> snapshot.doctors().stream().map(doctor -> toDoctorSummary(snapshot, doctor)))
                .filter(doctor -> matchesDoctor(doctor, normalizedQuery, normalizedCity, normalizedSpeciality))
                .sorted(Comparator.comparing(PublicDoctorSummaryResponse::doctorDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<String> listSpecialities(String tenantCode) {
        return loadTenantSnapshots(tenantCode).stream()
                .flatMap(snapshot -> snapshot.doctors().stream())
                .flatMap(doctor -> splitSpecialities(doctor.profile().specialization()).stream())
                .distinct()
                .toList();
    }

    public PublicSearchResponse search(String q, String city, String tenantCode) {
        return new PublicSearchResponse(
                listClinics(q, city, null, tenantCode),
                listDoctors(q, city, null, tenantCode),
                listSpecialities(tenantCode)
        );
    }

    private List<TenantSnapshot> loadTenantSnapshots(String tenantCode) {
        String normalizedTenantCode = normalize(tenantCode);
        return platformTenantManagementService.list().stream()
                .filter(this::isActiveTenant)
                .filter(tenant -> matchesTenantCode(tenant, normalizedTenantCode))
                .map(this::toSnapshot)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<TenantSnapshot> toSnapshot(PlatformTenantRecord tenant) {
        Optional<ClinicProfileRecord> clinicProfile = clinicProfileService.findByTenantId(tenant.id())
                .filter(ClinicProfileRecord::active);
        if (clinicProfile.isEmpty()) {
            return Optional.empty();
        }

        List<DoctorSnapshot> doctors = tenantUserManagementService.list(tenant.id()).stream()
                .filter(this::isActiveDoctorUser)
                .map(user -> doctorProfileService.findByDoctorUserId(tenant.id(), user.appUserId())
                        .filter(DoctorProfileRecord::active)
                        .map(profile -> new DoctorSnapshot(user, profile)))
                .flatMap(Optional::stream)
                .filter(snapshot -> StringUtils.hasText(snapshot.profile().specialization()))
                .sorted(Comparator.comparing(snapshot -> snapshot.user().displayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return Optional.of(new TenantSnapshot(tenant, clinicProfile.get(), doctors));
    }

    private PublicClinicSummaryResponse toClinicSummary(TenantSnapshot snapshot, String speciality) {
        List<String> specialities = snapshot.doctors().stream()
                .flatMap(doctor -> splitSpecialities(doctor.profile().specialization()).stream())
                .filter(distinctIgnoreCase())
                .toList();

        if (StringUtils.hasText(speciality) && specialities.stream().noneMatch(item -> containsIgnoreCase(item, speciality))) {
            return null;
        }

        return new PublicClinicSummaryResponse(
                firstNonBlank(snapshot.clinicProfile().displayName(), snapshot.clinicProfile().clinicName(), snapshot.tenant().name()),
                snapshot.clinicProfile().city(),
                buildLocationLabel(snapshot.clinicProfile()),
                specialities
        );
    }

    private PublicDoctorSummaryResponse toDoctorSummary(TenantSnapshot snapshot, DoctorSnapshot doctor) {
        // Phase 1 intentionally avoids exposing fee/language/slot data until a dedicated public listing model exists.
        return new PublicDoctorSummaryResponse(
                doctor.user().displayName(),
                firstNonBlank(snapshot.clinicProfile().displayName(), snapshot.clinicProfile().clinicName(), snapshot.tenant().name()),
                snapshot.clinicProfile().city(),
                normalizeDisplay(doctor.profile().specialization()),
                null,
                List.of(),
                null
        );
    }

    private boolean matchesClinic(PublicClinicSummaryResponse clinic, String q, String city, String speciality) {
        return matchesCommon(q, city, clinic.city(), List.of(
                clinic.clinicDisplayName(),
                clinic.locationLabel(),
                String.join(" ", clinic.specialities())
        )) && (!StringUtils.hasText(speciality)
                || clinic.specialities().stream().anyMatch(item -> containsIgnoreCase(item, speciality)));
    }

    private boolean matchesDoctor(PublicDoctorSummaryResponse doctor, String q, String city, String speciality) {
        return matchesCommon(q, city, doctor.city(), List.of(
                doctor.doctorDisplayName(),
                doctor.clinicDisplayName(),
                doctor.speciality()
        )) && (!StringUtils.hasText(speciality) || containsIgnoreCase(doctor.speciality(), speciality));
    }

    private boolean matchesCommon(String q, String cityFilter, String cityValue, List<String> fields) {
        if (StringUtils.hasText(cityFilter) && !containsIgnoreCase(cityValue, cityFilter)) {
            return false;
        }
        if (!StringUtils.hasText(q)) {
            return true;
        }
        return fields.stream().anyMatch(field -> containsIgnoreCase(field, q));
    }

    private List<String> splitSpecialities(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : raw.split("[,;]")) {
            String value = normalizeDisplay(token);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty() && StringUtils.hasText(raw)) {
            return List.of(normalizeDisplay(raw));
        }
        return values;
    }

    private Predicate<String> distinctIgnoreCase() {
        Map<String, Boolean> seen = new java.util.LinkedHashMap<>();
        return value -> seen.putIfAbsent(value.toLowerCase(Locale.ROOT), Boolean.TRUE) == null;
    }

    private boolean isActiveTenant(PlatformTenantRecord tenant) {
        return tenant != null && "ACTIVE".equalsIgnoreCase(tenant.status());
    }

    private boolean matchesTenantCode(PlatformTenantRecord tenant, String tenantCode) {
        return !StringUtils.hasText(tenantCode) || containsIgnoreCase(tenant.code(), tenantCode);
    }

    private boolean isActiveDoctorUser(TenantUserRecord user) {
        return user != null
                && "DOCTOR".equalsIgnoreCase(user.membershipRole())
                && "ACTIVE".equalsIgnoreCase(user.userStatus())
                && "ACTIVE".equalsIgnoreCase(user.membershipStatus());
    }

    private String buildLocationLabel(ClinicProfileRecord clinicProfile) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addIfText(parts, clinicProfile.city());
        addIfText(parts, clinicProfile.state());
        addIfText(parts, clinicProfile.country());
        return String.join(", ", parts);
    }

    private void addIfText(LinkedHashSet<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private boolean containsIgnoreCase(String source, String term) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(term)
                && source.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeDisplay(String value) {
        return value == null ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        return java.util.Arrays.stream(values).filter(StringUtils::hasText).map(String::trim).findFirst().orElse("");
    }

    private record TenantSnapshot(
            PlatformTenantRecord tenant,
            ClinicProfileRecord clinicProfile,
            List<DoctorSnapshot> doctors
    ) {}

    private record DoctorSnapshot(TenantUserRecord user, DoctorProfileRecord profile) {}
}
