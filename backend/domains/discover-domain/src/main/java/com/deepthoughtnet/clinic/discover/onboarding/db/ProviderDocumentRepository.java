package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderDocumentRepository extends JpaRepository<ProviderDocumentEntity, UUID> {
    List<ProviderDocumentEntity> findByProviderIdOrderByUploadedAtDesc(UUID providerId);
    java.util.Optional<ProviderDocumentEntity> findFirstByProviderIdAndDocumentTypeOrderByUploadedAtDesc(UUID providerId, ProviderDocumentType documentType);
}
