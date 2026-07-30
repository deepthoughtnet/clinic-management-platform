package com.deepthoughtnet.clinic.discover.reference;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import com.deepthoughtnet.clinic.discover.reference.db.DiscoverReferenceOptionEntity;
import com.deepthoughtnet.clinic.discover.reference.db.DiscoverReferenceOptionRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoverReferenceDataService {
    private final DiscoverReferenceOptionRepository repository;

    public DiscoverReferenceDataService(DiscoverReferenceOptionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listSpecialities() {
        return list(DiscoverReferenceCategory.SPECIALITY);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listServices() {
        return list(DiscoverReferenceCategory.SERVICE);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listFacilities() {
        return list(DiscoverReferenceCategory.FACILITY);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listLanguages() {
        return list(DiscoverReferenceCategory.LANGUAGE);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listCountries() {
        return list(DiscoverReferenceCategory.COUNTRY);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listStates() {
        return list(DiscoverReferenceCategory.STATE);
    }

    @Transactional(readOnly = true)
    public List<DiscoverReferenceOptionRecord> listMedicalCouncils() {
        return list(DiscoverReferenceCategory.MEDICAL_COUNCIL);
    }

    @Transactional(readOnly = true)
    public boolean isAvailableForSubmission(ProviderType providerType) {
        return requiredCategories(providerType).stream()
                .allMatch(category -> !list(category).isEmpty());
    }

    @Transactional(readOnly = true)
    public DiscoverReferenceOptionRecord requireService(ProviderType providerType, ProviderServiceType serviceType) {
        return findService(providerType, serviceType)
                .orElseThrow(() -> new InvalidReferenceValueException("services", "Selected service is not available for this provider type."));
    }

    @Transactional(readOnly = true)
    public Optional<DiscoverReferenceOptionRecord> findService(ProviderType providerType, ProviderServiceType serviceType) {
        if (providerType == null || serviceType == null) {
            return Optional.empty();
        }
        return list(DiscoverReferenceCategory.SERVICE).stream()
                .filter(option -> option.code().equals(serviceType.name()))
                .filter(option -> option.providerTypes().isEmpty() || option.providerTypes().contains(providerType))
                .findFirst();
    }

    private List<DiscoverReferenceCategory> requiredCategories(ProviderType providerType) {
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> List.of(
                    DiscoverReferenceCategory.SPECIALITY,
                    DiscoverReferenceCategory.SERVICE,
                    DiscoverReferenceCategory.FACILITY,
                    DiscoverReferenceCategory.LANGUAGE,
                    DiscoverReferenceCategory.COUNTRY,
                    DiscoverReferenceCategory.STATE,
                    DiscoverReferenceCategory.MEDICAL_COUNCIL
            );
            case CLINIC -> List.of(
                    DiscoverReferenceCategory.SERVICE,
                    DiscoverReferenceCategory.FACILITY,
                    DiscoverReferenceCategory.LANGUAGE,
                    DiscoverReferenceCategory.COUNTRY,
                    DiscoverReferenceCategory.STATE
            );
            case HOSPITAL -> List.of(
                    DiscoverReferenceCategory.SERVICE,
                    DiscoverReferenceCategory.FACILITY,
                    DiscoverReferenceCategory.LANGUAGE,
                    DiscoverReferenceCategory.COUNTRY,
                    DiscoverReferenceCategory.STATE
            );
        };
    }

    private List<DiscoverReferenceOptionRecord> list(DiscoverReferenceCategory category) {
        return repository.findByCategoryAndActiveTrueOrderByDisplayOrderAscDisplayNameAsc(category).stream()
                .map(this::toRecord)
                .toList();
    }

    private DiscoverReferenceOptionRecord toRecord(DiscoverReferenceOptionEntity entity) {
        return new DiscoverReferenceOptionRecord(
                entity.getId(),
                entity.getCategory(),
                entity.getCode(),
                entity.getDisplayName(),
                entity.getProviderTypes(),
                entity.getDisplayOrder(),
                entity.isActive()
        );
    }
}
