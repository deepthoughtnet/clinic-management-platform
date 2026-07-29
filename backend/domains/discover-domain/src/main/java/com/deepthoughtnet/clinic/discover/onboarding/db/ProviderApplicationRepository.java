package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplicationEntity, UUID> {
    Optional<ProviderApplicationEntity> findByTokenHash(String tokenHash);
    Optional<ProviderApplicationEntity> findByReferenceNumber(String referenceNumber);
    List<ProviderApplicationEntity> findByStatusIn(Collection<ProviderLifecycleStatus> statuses);
    List<ProviderApplicationEntity> findByProviderAccountIdOrderByUpdatedAtDesc(UUID providerAccountId);
    List<ProviderApplicationEntity> findByEmailIgnoreCase(String email);
    List<ProviderApplicationEntity> findByPhoneIgnoreCase(String phone);
}
