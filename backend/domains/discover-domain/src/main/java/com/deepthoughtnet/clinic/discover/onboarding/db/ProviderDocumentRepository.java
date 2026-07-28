package com.deepthoughtnet.clinic.discover.onboarding.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderDocumentRepository extends JpaRepository<ProviderDocumentEntity, UUID> {
    List<ProviderDocumentEntity> findByProviderIdOrderByUploadedAtDesc(UUID providerId);
}
