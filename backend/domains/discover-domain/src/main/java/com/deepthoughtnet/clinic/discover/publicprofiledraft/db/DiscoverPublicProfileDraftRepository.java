package com.deepthoughtnet.clinic.discover.publicprofiledraft.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProfileDraftRepository extends JpaRepository<DiscoverPublicProfileDraftEntity, UUID> {
    Optional<DiscoverPublicProfileDraftEntity> findByPublicProfileReference(String publicProfileReference);
    Optional<DiscoverPublicProfileDraftEntity> findByDraftReference(String draftReference);
    boolean existsByPublicProfileReference(String publicProfileReference);
}
