package com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation;

import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db.DiscoverPublicDoctorPracticeAssociationEntity;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db.DiscoverPublicDoctorPracticeAssociationRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDoctorPracticeAssociationService {
    public static final String SOURCE_SYSTEM_HEALTHCARE = "HEALTHCARE";

    private final DiscoverPublicDoctorPracticeAssociationRepository associations;
    private final DiscoverPublicProviderProfileRepository profiles;

    public PublicDoctorPracticeAssociationService(
            DiscoverPublicDoctorPracticeAssociationRepository associations,
            DiscoverPublicProviderProfileRepository profiles
    ) {
        this.associations = associations;
        this.profiles = profiles;
    }

    @Transactional
    public DiscoverPublicDoctorPracticeAssociationEntity upsertActiveAssociation(
            UUID publicDoctorReference,
            UUID publicPracticeReference,
            String sourceSystem,
            UUID sourceDoctorReference,
            UUID sourcePracticeReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicDoctorPracticeAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceDoctorReferenceAndSourcePracticeReference(
                        sourceSystem,
                        sourceDoctorReference,
                        sourcePracticeReference
                );
        if (existingAssociation.isEmpty()) {
            return associations.save(DiscoverPublicDoctorPracticeAssociationEntity.create(
                        publicDoctorReference,
                        publicPracticeReference,
                        sourceSystem,
                        sourceDoctorReference,
                        sourcePracticeReference,
                        true,
                        observedAt
            ));
        }
        DiscoverPublicDoctorPracticeAssociationEntity entity = existingAssociation.get();
        if (entity.isActive()
                && publicDoctorReference.equals(entity.getPublicDoctorReference())
                && publicPracticeReference.equals(entity.getPublicPracticeReference())
                && sourceSystem.equalsIgnoreCase(entity.getSourceSystem())) {
            return entity;
        }
        entity.activate(publicDoctorReference, publicPracticeReference, sourceSystem, sourceDoctorReference, sourcePracticeReference, observedAt);
        return associations.save(entity);
    }

    @Transactional
    public Optional<DiscoverPublicDoctorPracticeAssociationEntity> deactivateAssociation(
            UUID publicDoctorReference,
            UUID publicPracticeReference,
            String sourceSystem,
            UUID sourceDoctorReference,
            UUID sourcePracticeReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicDoctorPracticeAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceDoctorReferenceAndSourcePracticeReference(
                        sourceSystem,
                        sourceDoctorReference,
                        sourcePracticeReference
                );
        if (existingAssociation.isEmpty()) {
            return Optional.empty();
        }
        DiscoverPublicDoctorPracticeAssociationEntity entity = existingAssociation.get();
        if (!entity.isActive()) {
            return existingAssociation;
        }
        entity.deactivate(observedAt);
        return Optional.of(associations.save(entity));
    }

    @Transactional
    public int reconcileClinicDoctors(
            UUID publicPracticeReference,
            UUID sourcePracticeReference,
            List<UUID> eligiblePublicDoctorReferences,
            OffsetDateTime observedAt
    ) {
        Set<UUID> eligible = eligiblePublicDoctorReferences == null
                ? Set.of()
                : new LinkedHashSet<>(eligiblePublicDoctorReferences);
        List<DiscoverPublicDoctorPracticeAssociationEntity> existing = associations
                .findBySourceSystemIgnoreCaseAndSourcePracticeReferenceOrderByCreatedAtAsc(SOURCE_SYSTEM_HEALTHCARE, sourcePracticeReference);
        for (UUID publicDoctorReference : eligible) {
            upsertActiveAssociation(
                    publicDoctorReference,
                    publicPracticeReference,
                    SOURCE_SYSTEM_HEALTHCARE,
                    publicDoctorReference,
                    sourcePracticeReference,
                    observedAt
            );
        }
        for (DiscoverPublicDoctorPracticeAssociationEntity association : existing) {
            if (!eligible.contains(association.getPublicDoctorReference()) && association.isActive()) {
                association.deactivate(observedAt);
                associations.save(association);
            }
        }
        return refreshDoctorCount(publicPracticeReference, observedAt);
    }

    @Transactional
    public int deactivateAllForPractice(UUID publicPracticeReference, UUID sourcePracticeReference, OffsetDateTime observedAt) {
        List<DiscoverPublicDoctorPracticeAssociationEntity> existing = associations
                .findBySourceSystemIgnoreCaseAndSourcePracticeReferenceOrderByCreatedAtAsc(SOURCE_SYSTEM_HEALTHCARE, sourcePracticeReference);
        for (DiscoverPublicDoctorPracticeAssociationEntity association : existing) {
            if (association.isActive()) {
                association.deactivate(observedAt);
                associations.save(association);
            }
        }
        return refreshDoctorCount(publicPracticeReference, observedAt);
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicDoctorPracticeAssociationEntity> findActiveAssociationsByPublicDoctorReference(UUID publicDoctorReference) {
        return associations.findByPublicDoctorReferenceAndActiveTrueOrderByCreatedAtAsc(publicDoctorReference);
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicDoctorPracticeAssociationEntity> findActiveAssociationsByPublicPracticeReference(UUID publicPracticeReference) {
        return associations.findByPublicPracticeReferenceAndActiveTrueOrderByCreatedAtAsc(publicPracticeReference);
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicDoctorPracticeAssociationEntity> findAllAssociations() {
        return associations.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public List<UUID> listPublishedDoctorReferencesByPractice(UUID publicPracticeReference) {
        return findActiveAssociationsByPublicPracticeReference(publicPracticeReference).stream()
                .map(DiscoverPublicDoctorPracticeAssociationEntity::getPublicDoctorReference)
                .distinct()
                .filter(this::isPublishedDoctor)
                .toList();
    }

    @Transactional(readOnly = true)
    public int countPublishedDoctorsByPractice(UUID publicPracticeReference) {
        return (int) listPublishedDoctorReferencesByPractice(publicPracticeReference).stream().distinct().count();
    }

    @Transactional(readOnly = true)
    public List<UUID> listPublishedPracticeReferencesByDoctor(UUID publicDoctorReference) {
        return findActiveAssociationsByPublicDoctorReference(publicDoctorReference).stream()
                .map(DiscoverPublicDoctorPracticeAssociationEntity::getPublicPracticeReference)
                .distinct()
                .filter(this::isPublishedPractice)
                .toList();
    }

    private int refreshDoctorCount(UUID publicPracticeReference, OffsetDateTime observedAt) {
        int doctorCount = countPublishedDoctorsByPractice(publicPracticeReference);
        profiles.findByProviderId(publicPracticeReference).ifPresent(clinic -> {
            clinic.updateDoctorCount(doctorCount, observedAt);
            profiles.save(clinic);
        });
        return doctorCount;
    }

    private boolean isPublishedDoctor(UUID publicDoctorReference) {
        return profiles.findByProviderId(publicDoctorReference)
                .map(DiscoverPublicProviderProfileEntity::getPublicationStatus)
                .map(status -> "PUBLISHED".equalsIgnoreCase(status))
                .orElse(false);
    }

    private boolean isPublishedPractice(UUID publicPracticeReference) {
        return profiles.findByProviderId(publicPracticeReference)
                .map(DiscoverPublicProviderProfileEntity::getPublicationStatus)
                .map(status -> "PUBLISHED".equalsIgnoreCase(status))
                .orElse(false);
    }
}
