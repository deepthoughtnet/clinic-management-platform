package com.deepthoughtnet.clinic.discover.publicprofiledraft.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProfileDraftVersionRepository extends JpaRepository<DiscoverPublicProfileDraftVersionEntity, java.util.UUID> {
    List<DiscoverPublicProfileDraftVersionEntity> findByDraftReferenceOrderByVersionNumberDesc(String draftReference);
    Optional<DiscoverPublicProfileDraftVersionEntity> findFirstByDraftReferenceOrderByVersionNumberDesc(String draftReference);
    Optional<DiscoverPublicProfileDraftVersionEntity> findByDraftReferenceAndVersionNumber(String draftReference, int versionNumber);
}
