package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicProfilePublicationRepository extends JpaRepository<DiscoverPublicProfilePublicationEntity, UUID> {
    Optional<DiscoverPublicProfilePublicationEntity> findByPublicationReference(String publicationReference);
    Optional<DiscoverPublicProfilePublicationEntity> findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(String publicProfileReference);
    Optional<DiscoverPublicProfilePublicationEntity> findFirstBySlugIgnoreCaseAndCurrentTrueAndPublicationStatusOrderByPublishedAtDesc(String slug, String publicationStatus);
    List<DiscoverPublicProfilePublicationEntity> findByCurrentTrueAndPublicationStatusOrderByPublishedAtAsc(String publicationStatus);
    List<DiscoverPublicProfilePublicationEntity> findByPublicProfileReferenceOrderByPublishedAtDesc(String publicProfileReference);
}
