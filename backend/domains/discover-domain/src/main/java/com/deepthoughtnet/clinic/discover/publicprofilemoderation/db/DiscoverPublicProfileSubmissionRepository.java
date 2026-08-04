package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProfileSubmissionRepository extends JpaRepository<DiscoverPublicProfileSubmissionEntity, UUID> {
    Optional<DiscoverPublicProfileSubmissionEntity> findBySubmissionReference(String submissionReference);
    Optional<DiscoverPublicProfileSubmissionEntity> findFirstByPublicProfileReferenceAndCurrentTrueOrderBySubmittedAtDesc(String publicProfileReference);
    List<DiscoverPublicProfileSubmissionEntity> findByPublicProfileReferenceOrderBySubmittedAtDesc(String publicProfileReference);
    boolean existsByPublicProfileReferenceAndCurrentTrueAndModerationStatusIn(String publicProfileReference, List<String> moderationStatuses);
}
