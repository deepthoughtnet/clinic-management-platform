package com.deepthoughtnet.clinic.platform.providerintegration.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderConnectionSuggestionRejectionRepository extends JpaRepository<ProviderConnectionSuggestionRejectionEntity, UUID> {
    Optional<ProviderConnectionSuggestionRejectionEntity> findBySuggestionKey(String suggestionKey);
}
