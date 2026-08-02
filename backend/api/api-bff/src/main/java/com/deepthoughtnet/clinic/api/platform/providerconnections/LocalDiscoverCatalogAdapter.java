package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.DiscoverCatalogPort;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocalDiscoverCatalogAdapter implements DiscoverCatalogPort {
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderLinkingService linkingService;

    public LocalDiscoverCatalogAdapter(ProviderPublicProfileService publicProfileService, ProviderLinkingService linkingService) {
        this.publicProfileService = publicProfileService;
        this.linkingService = linkingService;
    }

    @Override
    public Optional<PublicProviderSummary> findPublishedProvider(PublicProviderReference publicReference) {
        if (publicReference == null || !StringUtils.hasText(publicReference.publicProviderId())) {
            return Optional.empty();
        }
        UUID providerId = parseUuid(publicReference.publicProviderId());
        if (providerId == null) {
            return Optional.empty();
        }
        return publicProfileService.findByProviderId(providerId).map(detail -> toPublicProviderSummary(detail, null));
    }

    @Override
    public Optional<PublicProviderSummary> findPublishedPractice(PublicProviderReference publicReference) {
        if (publicReference == null || !StringUtils.hasText(publicReference.publicProviderId()) || !StringUtils.hasText(publicReference.publicPracticeId())) {
            return Optional.empty();
        }
        UUID providerId = parseUuid(publicReference.publicProviderId());
        if (providerId == null) {
            return Optional.empty();
        }
        return publicProfileService.findByProviderId(providerId)
                .filter(detail -> detail.providerType() == ProviderType.INDIVIDUAL_DOCTOR)
                .flatMap(detail -> practiceSummaries(detail).stream()
                        .filter(summary -> publicReference.publicPracticeId().equals(summary.publicReference().publicPracticeId()))
                        .findFirst());
    }

    @Override
    public List<PublicProviderSummary> searchPublishedProviders(String query, String city, PublicProfileType publicProfileType) {
        ProviderType providerType = toProviderType(publicProfileType);
        if (providerType == null) {
            return List.of();
        }
        return publicProfileService.summariesByType(providerType, query, city, null, null, null).stream()
                .sorted(Comparator.comparing(PublicProviderProfileSummaryRecord::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(summary -> publicProfileService.findBySlug(summary.canonicalSlug())
                        .map(detail -> toPublicProviderSummary(detail, null))
                        .orElseGet(() -> summaryFallback(providerType, summary)))
                .toList();
    }

    @Override
    public List<PublicProviderSummary> searchPublishedPractices(String query, String city, PublicProfileType publicProfileType) {
        ProviderType providerType = toProviderType(publicProfileType);
        if (providerType != ProviderType.INDIVIDUAL_DOCTOR) {
            return List.of();
        }
        return publicProfileService.summariesByType(providerType, query, city, null, null, null).stream()
                .map(summary -> publicProfileService.findBySlug(summary.canonicalSlug()).map(this::practiceSummaries).orElse(List.of()))
                .flatMap(List::stream)
                .toList();
    }

    List<PublicProviderSummary> listPublicProviders(String query, String city, PublicProfileType publicProfileType) {
        return searchPublishedProviders(query, city, publicProfileType);
    }

    List<PublicProviderSummary> listPublicPractices(String query, String city, PublicProfileType publicProfileType) {
        return searchPublishedPractices(query, city, publicProfileType);
    }

    private List<PublicProviderSummary> practiceSummaries(PublicProviderProfileDetailRecord detail) {
        List<PublicProviderSummary> summaries = new ArrayList<>();
        for (int index = 0; index < detail.locations().size(); index++) {
            var location = detail.locations().get(index);
            String practiceReference = practiceReference(detail.providerId(), index, location.label(), location.address(), location.city());
            String publicPath = detail.publicPath() + "?practice=" + practiceReference;
            String displayName = firstNonBlank(detail.displayName(), location.label(), location.city());
            summaries.add(new PublicProviderSummary(
                    PublicProfileType.DOCTOR,
                    new PublicProviderReference(detail.providerId().toString(), practiceReference),
                    detail.canonicalSlug(),
                    displayName,
                    location.address(),
                    location.city(),
                    location.state(),
                    location.country(),
                    detail.contactPhone(),
                    detail.consultationFee() == null ? null : detail.consultationFee().toPlainString(),
                    mapBookingCapability(detail.bookingMode()),
                    AvailabilityState.UNKNOWN,
                    PublicationStatus.PUBLISHED,
                    resolveSourceSystem(detail.providerId(), PublicProfileType.DOCTOR, detail.canonicalSlug()),
                    detail.publishedVersionNumber(),
                    detail.publishedAt(),
                    detail.publishedAt()
            ));
        }
        return summaries;
    }

    private PublicProviderSummary summaryFallback(ProviderType providerType, PublicProviderProfileSummaryRecord summary) {
        PublicProfileType publicProfileType = toPublicProfileType(providerType);
        return new PublicProviderSummary(
                publicProfileType,
                new PublicProviderReference(summary.providerId().toString(), null),
                summary.canonicalSlug(),
                summary.displayName(),
                summary.area(),
                summary.city(),
                null,
                null,
                summary.contactPhone(),
                null,
                mapBookingCapability(null),
                AvailabilityState.UNKNOWN,
                PublicationStatus.PUBLISHED,
                resolveSourceSystem(summary.providerId(), publicProfileType, summary.canonicalSlug()),
                0L,
                null,
                null
        );
    }

    private PublicProviderSummary toPublicProviderSummary(PublicProviderProfileDetailRecord detail, String practiceReference) {
        return new PublicProviderSummary(
                toPublicProfileType(detail.providerType()),
                new PublicProviderReference(detail.providerId().toString(), practiceReference),
                detail.canonicalSlug(),
                detail.displayName(),
                detail.area(),
                detail.city(),
                detail.state(),
                detail.country(),
                detail.contactPhone(),
                detail.consultationFee() == null ? null : detail.consultationFee().toPlainString(),
                mapBookingCapability(detail.bookingMode()),
                mapAvailability(detail.bookingMode()),
                PublicationStatus.PUBLISHED,
                resolveSourceSystem(detail.providerId(), toPublicProfileType(detail.providerType()), detail.canonicalSlug()),
                detail.publishedVersionNumber(),
                detail.publishedAt(),
                detail.publishedAt()
        );
    }

    private BookingCapability mapBookingCapability(String bookingMode) {
        if (!StringUtils.hasText(bookingMode)) {
            return BookingCapability.CALL_TO_BOOK;
        }
        try {
            return BookingCapability.valueOf(bookingMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BookingCapability.CALL_TO_BOOK;
        }
    }

    private AvailabilityState mapAvailability(String bookingMode) {
        return BookingCapability.ONLINE_BOOKING.name().equalsIgnoreCase(bookingMode) ? AvailabilityState.AVAILABLE_TODAY : AvailabilityState.UNKNOWN;
    }

    private SourceSystem resolveSourceSystem(UUID providerId, PublicProfileType type, String canonicalSlug) {
        if (providerId != null) {
            if (type == PublicProfileType.DOCTOR) {
                Optional<PublicDoctorPracticePlatformLinkEntity> doctorLink = linkingService.listDoctorPracticeLinks().stream()
                        .filter(link -> providerId.toString().equals(link.getPublicDoctorReference()))
                        .findFirst();
                if (doctorLink.isPresent()) {
                    return doctorLink.get().getSourceSystem();
                }
            } else if (type == PublicProfileType.CLINIC) {
                Optional<PublicClinicPlatformLinkEntity> clinicLink = linkingService.listClinicLinks().stream()
                        .filter(link -> providerId.toString().equals(link.getPublicClinicReference()) || canonicalSlugEquals(link.getPublicClinicReference(), canonicalSlug))
                        .findFirst();
                if (clinicLink.isPresent()) {
                    return clinicLink.get().getSourceSystem();
                }
            }
        }
        return SourceSystem.DISCOVER_PROVIDER;
    }

    private boolean canonicalSlugEquals(String reference, String canonicalSlug) {
        if (!StringUtils.hasText(reference) || !StringUtils.hasText(canonicalSlug)) {
            return false;
        }
        return reference.trim().equalsIgnoreCase(canonicalSlug.trim());
    }

    private ProviderType toProviderType(PublicProfileType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DOCTOR -> ProviderType.INDIVIDUAL_DOCTOR;
            case CLINIC -> ProviderType.CLINIC;
            case HOSPITAL -> ProviderType.HOSPITAL;
        };
    }

    private PublicProfileType toPublicProfileType(ProviderType providerType) {
        if (providerType == null) {
            return PublicProfileType.CLINIC;
        }
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> PublicProfileType.DOCTOR;
            case CLINIC -> PublicProfileType.CLINIC;
            case HOSPITAL -> PublicProfileType.HOSPITAL;
        };
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String practiceReference(UUID providerId, int index, String label, String address, String city) {
        String seed = String.join("|",
                providerId == null ? "" : providerId.toString(),
                String.valueOf(index),
                value(label),
                value(address),
                value(city)
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
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
}
