package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicMiniResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialityDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicCatalogFacade {
    private static final int MAX_PAGE_SIZE = 24;
    private static final int SLOT_LOOKAHEAD_DAYS = 14;
    private static final int SLOT_SUGGESTION_LIMIT = 5;
    private static final DateTimeFormatter SLOT_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final PlatformTenantManagementService platformTenantManagementService;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final DoctorProfileService doctorProfileService;
    private final AppointmentService appointmentService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    public PublicCatalogFacade(
            PlatformTenantManagementService platformTenantManagementService,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorProfileService doctorProfileService,
            AppointmentService appointmentService,
            ClinicTimeZoneResolver clinicTimeZoneResolver
    ) {
        this.platformTenantManagementService = platformTenantManagementService;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.doctorProfileService = doctorProfileService;
        this.appointmentService = appointmentService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    public PublicPageResponse<PublicClinicSummaryResponse> listClinics(
            String q,
            String city,
            String area,
            String speciality,
            String tenantCode,
            int page,
            int size
    ) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        String normalizedArea = normalize(area);
        String normalizedSpeciality = normalize(speciality);

        List<TenantSnapshot> matches = loadTenantSnapshots(tenantCode).stream()
                .filter(snapshot -> matchesClinic(snapshot, normalizedQuery, normalizedCity, normalizedArea, normalizedSpeciality))
                .sorted(Comparator.comparing(snapshot -> clinicName(snapshot.clinicProfile()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return paginate(
                matches,
                page,
                size,
                this::toClinicSummary
        );
    }

    public PublicClinicDetailResponse clinicDetail(String clinicSlug) {
        TenantSnapshot snapshot = loadTenantSnapshots(null).stream()
                .filter(item -> slugForClinic(item.clinicProfile()).equalsIgnoreCase(cleanSlug(clinicSlug)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic not found"));

        List<PublicDoctorSummaryResponse> doctors = snapshot.doctors().stream()
                .sorted(Comparator.comparing(doctor -> doctor.user().displayName(), String.CASE_INSENSITIVE_ORDER))
                .map(doctor -> toDoctorSummary(snapshot, doctor))
                .toList();

        return new PublicClinicDetailResponse(
                slugForClinic(snapshot.clinicProfile()),
                clinicName(snapshot.clinicProfile()),
                null,
                fullAddress(snapshot.clinicProfile()),
                areaLabel(snapshot.clinicProfile()),
                snapshot.clinicProfile().city(),
                clinicTimings(snapshot),
                doctors,
                clinicSpecialities(snapshot),
                doctors.stream().anyMatch(PublicDoctorSummaryResponse::availableToday)
        );
    }

    public PublicPageResponse<PublicDoctorSummaryResponse> listDoctors(
            String q,
            String city,
            String area,
            String speciality,
            String clinic,
            String tenantCode,
            int page,
            int size
    ) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        String normalizedArea = normalize(area);
        String normalizedSpeciality = normalize(speciality);
        String normalizedClinic = normalize(clinic);

        List<DoctorCandidate> candidates = loadTenantSnapshots(tenantCode).stream()
                .flatMap(snapshot -> snapshot.doctors().stream().map(doctor -> new DoctorCandidate(snapshot, doctor)))
                .filter(candidate -> matchesDoctor(candidate, normalizedQuery, normalizedCity, normalizedArea, normalizedSpeciality, normalizedClinic))
                .sorted(Comparator.comparing(candidate -> candidate.doctor().user().displayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return paginate(
                candidates,
                page,
                size,
                candidate -> toDoctorSummary(candidate.snapshot(), candidate.doctor())
        );
    }

    public PublicDoctorDetailResponse doctorDetail(String doctorSlug) {
        DoctorCandidate candidate = loadTenantSnapshots(null).stream()
                .flatMap(snapshot -> snapshot.doctors().stream().map(doctor -> new DoctorCandidate(snapshot, doctor)))
                .filter(item -> slugForDoctor(item.doctor()).equalsIgnoreCase(cleanSlug(doctorSlug)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        SlotSummary slotSummary = slotSummary(candidate.snapshot().tenant().id(), candidate.doctor().user().appUserId());
        return new PublicDoctorDetailResponse(
                slugForDoctor(candidate.doctor()),
                candidate.doctor().user().displayName(),
                null,
                candidate.doctor().profile().qualification(),
                candidate.doctor().profile().yearsOfExperience(),
                splitSpecialities(candidate.doctor().profile().specialization()),
                List.of(),
                List.of(new PublicClinicMiniResponse(
                        slugForClinic(candidate.snapshot().clinicProfile()),
                        clinicName(candidate.snapshot().clinicProfile()),
                        areaLabel(candidate.snapshot().clinicProfile()),
                        candidate.snapshot().clinicProfile().city()
                )),
                availableDays(candidate.snapshot().tenant().id(), candidate.doctor().user().appUserId()),
                slotSummary.nextSlots(),
                slotSummary.availableToday()
        );
    }

    public List<PublicSpecialitySummaryResponse> listSpecialities(String q, String city, String tenantCode) {
        String normalizedQuery = normalize(q);
        String normalizedCity = normalize(city);
        Map<String, SpecialityAggregate> aggregates = new LinkedHashMap<>();

        for (TenantSnapshot snapshot : loadTenantSnapshots(tenantCode)) {
            for (DoctorSnapshot doctor : snapshot.doctors()) {
                if (StringUtils.hasText(normalizedCity) && !containsIgnoreCase(snapshot.clinicProfile().city(), normalizedCity)) {
                    continue;
                }
                for (String speciality : splitSpecialities(doctor.profile().specialization())) {
                    if (StringUtils.hasText(normalizedQuery) && !containsIgnoreCase(speciality, normalizedQuery)) {
                        continue;
                    }
                    aggregates.computeIfAbsent(speciality.toLowerCase(Locale.ROOT), key -> new SpecialityAggregate(speciality))
                            .add(snapshot.clinicProfile().tenantId(), doctor.user().appUserId());
                }
            }
        }

        return aggregates.values().stream()
                .map(aggregate -> new PublicSpecialitySummaryResponse(
                        aggregate.label(),
                        slugify(aggregate.label()),
                        aggregate.doctorIds().size(),
                        aggregate.clinicIds().size()
                ))
                .sorted(Comparator.comparing(PublicSpecialitySummaryResponse::speciality, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PublicSpecialityDetailResponse specialityDetail(
            String specialitySlug,
            String q,
            String city,
            String area,
            String clinic,
            String tenantCode,
            int page,
            int size
    ) {
        String resolved = resolveSpecialityLabel(specialitySlug, tenantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speciality not found"));

        PublicPageResponse<PublicDoctorSummaryResponse> doctors = listDoctors(
                q,
                city,
                area,
                resolved,
                clinic,
                tenantCode,
                page,
                size
        );

        return new PublicSpecialityDetailResponse(resolved, slugify(resolved), doctors);
    }

    public PublicSearchResponse search(String q, String city, String area, String tenantCode, int page, int size) {
        return new PublicSearchResponse(
                listDoctors(q, city, area, null, null, tenantCode, page, size),
                listClinics(q, city, area, null, tenantCode, page, size),
                listSpecialities(q, city, tenantCode)
        );
    }

    private List<TenantSnapshot> loadTenantSnapshots(String tenantCode) {
        String normalizedTenantCode = normalize(tenantCode);
        return platformTenantManagementService.list().stream()
                .filter(this::isActiveTenant)
                .filter(tenant -> !StringUtils.hasText(normalizedTenantCode) || containsIgnoreCase(tenant.code(), normalizedTenantCode))
                .map(this::toSnapshot)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<TenantSnapshot> toSnapshot(PlatformTenantRecord tenant) {
        Optional<ClinicProfileRecord> clinicProfile = clinicProfileService.findByTenantId(tenant.id())
                .filter(ClinicProfileRecord::active)
                .filter(ClinicProfileRecord::publicListingEnabled);
        if (clinicProfile.isEmpty()) {
            return Optional.empty();
        }

        List<DoctorSnapshot> doctors = tenantUserManagementService.list(tenant.id()).stream()
                .filter(this::isActiveDoctorUser)
                .map(user -> doctorProfileService.findByDoctorUserId(tenant.id(), user.appUserId())
                        .filter(DoctorProfileRecord::active)
                        .filter(DoctorProfileRecord::publicListingEnabled)
                        .filter(profile -> StringUtils.hasText(profile.specialization()))
                        .map(profile -> new DoctorSnapshot(user, profile)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(snapshot -> snapshot.user().displayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (doctors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TenantSnapshot(tenant, clinicProfile.get(), doctors));
    }

    private boolean isActiveTenant(PlatformTenantRecord tenant) {
        return tenant != null
                && "ACTIVE".equalsIgnoreCase(tenant.status());
    }

    private boolean isActiveDoctorUser(TenantUserRecord user) {
        return user != null
                && "DOCTOR".equalsIgnoreCase(user.membershipRole())
                && "ACTIVE".equalsIgnoreCase(user.userStatus())
                && "ACTIVE".equalsIgnoreCase(user.membershipStatus());
    }

    private boolean matchesClinic(TenantSnapshot snapshot, String q, String city, String area, String speciality) {
        List<String> specialities = clinicSpecialities(snapshot);
        if (StringUtils.hasText(speciality) && specialities.stream().noneMatch(item -> containsIgnoreCase(item, speciality))) {
            return false;
        }
        return matchesCommon(
                q,
                city,
                area,
                snapshot.clinicProfile().city(),
                areaLabel(snapshot.clinicProfile()),
                List.of(
                        clinicName(snapshot.clinicProfile()),
                        fullAddress(snapshot.clinicProfile()),
                        String.join(" ", specialities)
                )
        );
    }

    private boolean matchesDoctor(DoctorCandidate candidate, String q, String city, String area, String speciality, String clinic) {
        if (StringUtils.hasText(speciality) && splitSpecialities(candidate.doctor().profile().specialization()).stream().noneMatch(item -> containsIgnoreCase(item, speciality))) {
            return false;
        }
        if (StringUtils.hasText(clinic) && !containsIgnoreCase(clinicName(candidate.snapshot().clinicProfile()), clinic)) {
            return false;
        }
        return matchesCommon(
                q,
                city,
                area,
                candidate.snapshot().clinicProfile().city(),
                areaLabel(candidate.snapshot().clinicProfile()),
                List.of(
                        candidate.doctor().user().displayName(),
                        candidate.doctor().profile().specialization(),
                        clinicName(candidate.snapshot().clinicProfile())
                )
        );
    }

    private boolean matchesCommon(
            String q,
            String cityFilter,
            String areaFilter,
            String cityValue,
            String areaValue,
            List<String> fields
    ) {
        if (StringUtils.hasText(cityFilter) && !containsIgnoreCase(cityValue, cityFilter)) {
            return false;
        }
        if (StringUtils.hasText(areaFilter) && !containsIgnoreCase(areaValue, areaFilter)) {
            return false;
        }
        if (!StringUtils.hasText(q)) {
            return true;
        }
        return fields.stream().anyMatch(field -> containsIgnoreCase(field, q));
    }

    private PublicClinicSummaryResponse toClinicSummary(TenantSnapshot snapshot) {
        List<PublicDoctorSummaryResponse> doctors = snapshot.doctors().stream()
                .map(doctor -> toDoctorSummary(snapshot, doctor))
                .toList();
        return new PublicClinicSummaryResponse(
                slugForClinic(snapshot.clinicProfile()),
                clinicName(snapshot.clinicProfile()),
                null,
                fullAddress(snapshot.clinicProfile()),
                areaLabel(snapshot.clinicProfile()),
                snapshot.clinicProfile().city(),
                doctors.size(),
                doctors.stream().anyMatch(PublicDoctorSummaryResponse::availableToday),
                clinicSpecialities(snapshot)
        );
    }

    private PublicDoctorSummaryResponse toDoctorSummary(TenantSnapshot snapshot, DoctorSnapshot doctor) {
        SlotSummary slotSummary = slotSummary(snapshot.tenant().id(), doctor.user().appUserId());
        return new PublicDoctorSummaryResponse(
                slugForDoctor(doctor),
                doctor.user().displayName(),
                null,
                firstSpeciality(doctor.profile().specialization()),
                doctor.profile().yearsOfExperience(),
                List.of(),
                clinicName(snapshot.clinicProfile()),
                slugForClinic(snapshot.clinicProfile()),
                areaLabel(snapshot.clinicProfile()),
                snapshot.clinicProfile().city(),
                slotSummary.availableToday(),
                slotSummary.firstSummary()
        );
    }

    private List<String> clinicSpecialities(TenantSnapshot snapshot) {
        return snapshot.doctors().stream()
                .flatMap(doctor -> splitSpecialities(doctor.profile().specialization()).stream())
                .filter(distinctIgnoreCase())
                .toList();
    }

    private List<String> clinicTimings(TenantSnapshot snapshot) {
        Map<DayOfWeek, List<DoctorAvailabilityRecord>> byDay = snapshot.doctors().stream()
                .flatMap(doctor -> appointmentService.listDoctorAvailabilities(snapshot.tenant().id(), doctor.user().appUserId()).stream())
                .filter(DoctorAvailabilityRecord::active)
                .collect(Collectors.groupingBy(
                        DoctorAvailabilityRecord::dayOfWeek,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    var earliest = entry.getValue().stream().map(DoctorAvailabilityRecord::startTime).filter(Objects::nonNull).min(Comparator.naturalOrder());
                    var latest = entry.getValue().stream().map(DoctorAvailabilityRecord::endTime).filter(Objects::nonNull).max(Comparator.naturalOrder());
                    String day = entry.getKey().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    if (earliest.isPresent() && latest.isPresent()) {
                        return day + ": " + TIME_FORMAT.format(earliest.get()) + " - " + TIME_FORMAT.format(latest.get());
                    }
                    return day;
                })
                .toList();
    }

    private List<String> availableDays(UUID tenantId, UUID doctorUserId) {
        return appointmentService.listDoctorAvailabilities(tenantId, doctorUserId).stream()
                .filter(DoctorAvailabilityRecord::active)
                .map(DoctorAvailabilityRecord::dayOfWeek)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(day -> day.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList();
    }

    private SlotSummary slotSummary(UUID tenantId, UUID doctorUserId) {
        ZoneId bookingZone = clinicTimeZoneResolver.resolve(tenantId);
        LocalDate today = LocalDate.now(bookingZone);
        List<String> nextSlots = new ArrayList<>();
        boolean availableToday = false;

        for (int offset = 0; offset < SLOT_LOOKAHEAD_DAYS && nextSlots.size() < SLOT_SUGGESTION_LIMIT; offset++) {
            LocalDate date = today.plusDays(offset);
            List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(tenantId, doctorUserId, date, bookingZone).stream()
                    .filter(DoctorAvailabilitySlotRecord::selectable)
                    .sorted(Comparator.comparing(DoctorAvailabilitySlotRecord::slotTime))
                    .toList();
            if (offset == 0 && !slots.isEmpty()) {
                availableToday = true;
            }
            for (DoctorAvailabilitySlotRecord slot : slots) {
                nextSlots.add(formatSlot(slot, today));
                if (nextSlots.size() >= SLOT_SUGGESTION_LIMIT) {
                    break;
                }
            }
        }

        return new SlotSummary(availableToday, nextSlots);
    }

    private String formatSlot(DoctorAvailabilitySlotRecord slot, LocalDate today) {
        String prefix = slot.appointmentDate().equals(today) ? "Today" : SLOT_DATE_FORMAT.format(slot.appointmentDate());
        return prefix + " · " + TIME_FORMAT.format(slot.slotTime());
    }

    private Optional<String> resolveSpecialityLabel(String specialitySlug, String tenantCode) {
        String cleanSlug = cleanSlug(specialitySlug);
        return listSpecialities(null, null, tenantCode).stream()
                .filter(item -> item.specialitySlug().equalsIgnoreCase(cleanSlug))
                .map(PublicSpecialitySummaryResponse::speciality)
                .findFirst();
    }

    private String clinicName(ClinicProfileRecord clinicProfile) {
        return firstNonBlank(clinicProfile.displayName(), clinicProfile.clinicName());
    }

    private String areaLabel(ClinicProfileRecord clinicProfile) {
        return firstNonBlank(clinicProfile.addressLine2(), clinicProfile.addressLine1());
    }

    private String fullAddress(ClinicProfileRecord clinicProfile) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addIfText(parts, clinicProfile.addressLine1());
        addIfText(parts, clinicProfile.addressLine2());
        addIfText(parts, clinicProfile.city());
        addIfText(parts, clinicProfile.state());
        addIfText(parts, clinicProfile.country());
        return String.join(", ", parts);
    }

    private void addIfText(Collection<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private List<String> splitSpecialities(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : raw.split("[,;]")) {
            String normalized = normalizeDisplay(token);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        if (values.isEmpty()) {
            return List.of(normalizeDisplay(raw));
        }
        return values;
    }

    private String firstSpeciality(String raw) {
        return splitSpecialities(raw).stream().findFirst().orElse(null);
    }

    private Predicate<String> distinctIgnoreCase() {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        return value -> seen.putIfAbsent(value.toLowerCase(Locale.ROOT), Boolean.TRUE) == null;
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

    private String slugForClinic(ClinicProfileRecord clinicProfile) {
        if (StringUtils.hasText(clinicProfile.slug())) {
            return cleanSlug(clinicProfile.slug());
        }
        return slugify(clinicName(clinicProfile));
    }

    private String slugForDoctor(DoctorSnapshot doctor) {
        if (StringUtils.hasText(doctor.profile().slug())) {
            return cleanSlug(doctor.profile().slug());
        }
        return slugify(doctor.user().displayName());
    }

    private String cleanSlug(String value) {
        return slugify(value == null ? "" : value);
    }

    private String slugify(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String slug = normalized
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(slug) ? slug : "listing";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private <T, R> PublicPageResponse<R> paginate(List<T> values, int page, int size, Function<T, R> mapper) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * safeSize, values.size());
        int toIndex = Math.min(fromIndex + safeSize, values.size());
        List<R> items = values.subList(fromIndex, toIndex).stream().map(mapper).toList();
        int totalPages = values.isEmpty() ? 0 : (int) Math.ceil((double) values.size() / safeSize);
        return new PublicPageResponse<>(items, safePage, safeSize, values.size(), totalPages);
    }

    private record TenantSnapshot(
            PlatformTenantRecord tenant,
            ClinicProfileRecord clinicProfile,
            List<DoctorSnapshot> doctors
    ) {}

    private record DoctorSnapshot(TenantUserRecord user, DoctorProfileRecord profile) {}

    private record DoctorCandidate(TenantSnapshot snapshot, DoctorSnapshot doctor) {}

    private record SlotSummary(boolean availableToday, List<String> nextSlots) {
        private String firstSummary() {
            return nextSlots.isEmpty() ? null : nextSlots.get(0);
        }
    }

    private static final class SpecialityAggregate {
        private final String label;
        private final LinkedHashSet<UUID> clinicIds = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> doctorIds = new LinkedHashSet<>();

        private SpecialityAggregate(String label) {
            this.label = label;
        }

        private void add(UUID clinicId, UUID doctorId) {
            clinicIds.add(clinicId);
            doctorIds.add(doctorId);
        }

        private String label() {
            return label;
        }

        private LinkedHashSet<UUID> clinicIds() {
            return clinicIds;
        }

        private LinkedHashSet<UUID> doctorIds() {
            return doctorIds;
        }
    }
}
