package com.deepthoughtnet.clinic.discover.landingpage.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingPageTemplateRepository extends JpaRepository<LandingPageTemplateEntity, String> {
    List<LandingPageTemplateEntity> findByProviderTypeAndActiveTrueOrderBySortOrderAsc(ProviderType providerType);
    Optional<LandingPageTemplateEntity> findFirstByProviderTypeAndActiveTrueOrderBySortOrderAsc(ProviderType providerType);
    List<LandingPageTemplateEntity> findAllByActiveTrueOrderBySortOrderAsc();
}
