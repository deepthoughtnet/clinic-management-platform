package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProfileReviewFindingRepository extends JpaRepository<DiscoverPublicProfileReviewFindingEntity, java.util.UUID> {
    List<DiscoverPublicProfileReviewFindingEntity> findBySubmissionReferenceOrderByCreatedAtAsc(String submissionReference);
}
