package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderFactsSnapshot;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.HealthcareProviderFactsPort;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalHealthcareProviderFactsAdapter implements HealthcareProviderFactsPort {
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final PlatformTenantManagementService tenantManagementService;
    private final TenantUserManagementService tenantUserManagementService;

    public LocalHealthcareProviderFactsAdapter(
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            PlatformTenantManagementService tenantManagementService,
            TenantUserManagementService tenantUserManagementService
    ) {
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantManagementService = tenantManagementService;
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @Override
    public Optional<ProviderFactsSnapshot> getClinicPublicFacts(ProviderSourceReference sourceReference) {
        Optional<ClinicProfileRecord> clinic = findClinic(sourceReference);
        return clinic.map(this::toClinicFacts);
    }

    @Override
    public Optional<ProviderFactsSnapshot> getDoctorPublicFacts(ProviderSourceReference sourceReference) {
        Optional<DoctorContext> context = findDoctor(sourceReference);
        return context.map(this::toDoctorFacts);
    }

    @Override
    public Optional<ProviderFactsSnapshot> getHospitalPublicFacts(ProviderSourceReference sourceReference) {
        return Optional.empty();
    }

    List<HealthcareProviderFactsRow> listClinicRows() {
        return clinicProfileService.findAll().stream()
                .sorted(Comparator.comparing(ClinicProfileRecord::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toClinicRow)
                .toList();
    }

    List<HealthcareProviderFactsRow> listDoctorRows() {
        List<HealthcareProviderFactsRow> rows = new ArrayList<>();
        for (ClinicProfileRecord clinic : clinicProfileService.findAll()) {
            PlatformTenantRecord tenant = tenantManagementService.get(clinic.tenantId());
            List<TenantUserRecord> users = tenantUserManagementService.list(clinic.tenantId());
            List<DoctorProfileRecord> doctorProfiles = doctorProfileService.findAll().stream()
                    .filter(profile -> clinic.tenantId().equals(profile.tenantId()))
                    .toList();
            for (DoctorProfileRecord profile : doctorProfiles) {
                TenantUserRecord user = users.stream()
                        .filter(item -> profile.doctorUserId().equals(item.appUserId()))
                        .findFirst()
                        .orElse(null);
                rows.add(toDoctorRow(clinic, tenant, profile, user));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(HealthcareProviderFactsRow::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ProviderFactsSnapshot toClinicFacts(ClinicProfileRecord clinic) {
        return new ProviderFactsSnapshot(
                PublicProfileType.CLINIC,
                new ProviderSourceReference(SourceSystem.HEALTHCARE_CLINIC, clinic.tenantId().toString(), sourceRevision(clinic.updatedAt()), clinic.updatedAt()),
                new PublicProviderReference(clinic.tenantId().toString(), null),
                clinic.slug(),
                clinic.displayName(),
                null,
                null,
                null,
                clinic.city(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                clinic.phone(),
                null,
                List.of("IN_PERSON"),
                clinic.active() && clinic.publicListingEnabled() ? BookingCapability.CALL_TO_BOOK : BookingCapability.NOT_AVAILABLE,
                AvailabilityState.UNKNOWN,
                clinic.active() && clinic.publicListingEnabled() ? PublicationStatus.PUBLISHED : PublicationStatus.UNPUBLISHED,
                SourceSystem.HEALTHCARE_CLINIC,
                clinic.updatedAt()
        );
    }

    private ProviderFactsSnapshot toDoctorFacts(DoctorContext context) {
        ClinicProfileRecord clinic = context.clinic();
        DoctorProfileRecord doctor = context.doctor();
        TenantUserRecord user = context.user();
        String displayName = firstNonBlank(user == null ? null : user.displayName(), doctor.specialization(), doctor.slug());
        BigDecimal fee = doctor.consultationFee() != null ? doctor.consultationFee() : doctor.opdFee();
        return new ProviderFactsSnapshot(
                PublicProfileType.DOCTOR,
                new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, doctor.doctorUserId().toString(), sourceRevision(doctor.updatedAt()), doctor.updatedAt()),
                new PublicProviderReference(doctor.doctorUserId().toString(), doctor.slug()),
                doctor.slug(),
                displayName,
                firstNonBlank(doctor.specialization(), doctor.specializations().isEmpty() ? null : doctor.specializations().getFirst()),
                doctor.qualification(),
                doctor.yearsOfExperience(),
                clinic.city(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                firstNonBlank(user == null ? null : user.mobile(), doctor.mobile()),
                fee == null ? null : fee.toPlainString(),
                List.of("IN_PERSON"),
                doctor.active() && doctor.publicListingEnabled() ? BookingCapability.CALL_TO_BOOK : BookingCapability.NOT_AVAILABLE,
                AvailabilityState.UNKNOWN,
                doctor.active() && doctor.publicListingEnabled() ? PublicationStatus.PUBLISHED : PublicationStatus.UNPUBLISHED,
                SourceSystem.HEALTHCARE_DOCTOR,
                doctor.updatedAt()
        );
    }

    private HealthcareProviderFactsRow toClinicRow(ClinicProfileRecord clinic) {
        PlatformTenantRecord tenant = tenantManagementService.get(clinic.tenantId());
        return new HealthcareProviderFactsRow(
                "CLINIC",
                clinic.tenantId(),
                tenant.code(),
                tenant.name(),
                clinic.displayName(),
                clinic.city(),
                clinic.state(),
                clinic.phone(),
                clinic.email(),
                null,
                null,
                clinic.registrationNumber(),
                null,
                clinic.active(),
                clinic.publicListingEnabled(),
                clinic.slug(),
                null,
                null,
                null,
                null,
                clinic.updatedAt(),
                sourceRevision(clinic.updatedAt())
        );
    }

    private HealthcareProviderFactsRow toDoctorRow(ClinicProfileRecord clinic, PlatformTenantRecord tenant, DoctorProfileRecord doctor, TenantUserRecord user) {
        return new HealthcareProviderFactsRow(
                "DOCTOR",
                clinic.tenantId(),
                tenant.code(),
                tenant.name(),
                user == null ? doctor.slug() : firstNonBlank(user.displayName(), doctor.slug()),
                clinic.city(),
                clinic.state(),
                firstNonBlank(user == null ? null : user.mobile(), doctor.mobile()),
                user == null ? null : user.email(),
                firstNonBlank(doctor.specialization(), doctor.specializations().isEmpty() ? null : doctor.specializations().getFirst()),
                doctor.qualification(),
                doctor.registrationNumber(),
                doctor.yearsOfExperience(),
                doctor.active(),
                doctor.publicListingEnabled(),
                doctor.slug(),
                doctor.doctorUserId(),
                doctor.id(),
                doctor.doctorUserId() == null ? null : doctor.doctorUserId().toString(),
                doctor.id() == null ? null : doctor.id().toString(),
                doctor.updatedAt(),
                sourceRevision(doctor.updatedAt())
        );
    }

    private Optional<ClinicProfileRecord> findClinic(ProviderSourceReference sourceReference) {
        if (sourceReference == null || !StringUtils.hasText(sourceReference.sourceEntityReference())) {
            return Optional.empty();
        }
        UUID tenantId = parseUuid(sourceReference.sourceEntityReference());
        if (tenantId != null) {
            return clinicProfileService.findByTenantId(tenantId);
        }
        return clinicProfileService.findAll().stream()
                .filter(clinic -> sourceReference.sourceEntityReference().equalsIgnoreCase(clinic.slug()))
                .findFirst();
    }

    private Optional<DoctorContext> findDoctor(ProviderSourceReference sourceReference) {
        if (sourceReference == null || !StringUtils.hasText(sourceReference.sourceEntityReference())) {
            return Optional.empty();
        }
        String[] parts = sourceReference.sourceEntityReference().split("\\|");
        UUID tenantId = parts.length > 0 ? parseUuid(parts[0]) : null;
        UUID doctorUserId = parts.length > 1 ? parseUuid(parts[1]) : parseUuid(parts[0]);
        if (tenantId == null || doctorUserId == null) {
            return Optional.empty();
        }
        Optional<ClinicProfileRecord> clinic = clinicProfileService.findByTenantId(tenantId);
        Optional<DoctorProfileRecord> doctor = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId);
        Optional<TenantUserRecord> user = tenantUserManagementService.list(tenantId).stream()
                .filter(record -> doctorUserId.equals(record.appUserId()))
                .findFirst();
        if (clinic.isEmpty() || doctor.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DoctorContext(clinic.get(), doctor.get(), user.orElse(null)));
    }

    private long sourceRevision(OffsetDateTime updatedAt) {
        return updatedAt == null ? 0L : updatedAt.toInstant().toEpochMilli();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
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

    private record DoctorContext(ClinicProfileRecord clinic, DoctorProfileRecord doctor, TenantUserRecord user) {
    }
}
