package com.deepthoughtnet.clinic.discover.provideraccess.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderPortalAccessRequestRepository extends JpaRepository<ProviderPortalAccessRequestEntity, UUID> {
    Optional<ProviderPortalAccessRequestEntity> findTopByProviderTypeAndMobileNormalizedOrderByRequestedAtDesc(
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            String mobileNormalized
    );

    Optional<ProviderPortalAccessRequestEntity> findTopByProviderTypeAndEmailNormalizedOrderByRequestedAtDesc(
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            String emailNormalized
    );

    Optional<ProviderPortalAccessRequestEntity> findTopByProviderTypeAndProviderApplicationReferenceIgnoreCaseOrderByRequestedAtDesc(
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            String providerApplicationReference
    );

    List<ProviderPortalAccessRequestEntity> findByProviderTypeAndMobileNormalizedOrderByRequestedAtDesc(
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            String mobileNormalized
    );

    List<ProviderPortalAccessRequestEntity> findByProviderTypeAndEmailNormalizedOrderByRequestedAtDesc(
            com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType providerType,
            String emailNormalized
    );
}
