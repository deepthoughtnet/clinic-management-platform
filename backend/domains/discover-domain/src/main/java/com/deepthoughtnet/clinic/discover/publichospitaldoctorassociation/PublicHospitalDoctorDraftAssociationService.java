package com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation;

import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorDraftAssociationEntity;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorDraftAssociationRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicHospitalDoctorDraftAssociationService {
    public static final String SOURCE_SYSTEM_DISCOVER_PROVIDER = "DISCOVER_PROVIDER_PROFILE";

    private final DiscoverPublicHospitalDoctorDraftAssociationRepository associations;
    private final DiscoverPublicProviderProfileRepository profiles;
    private final PublicHospitalDoctorAssociationService publishedAssociations;

    public PublicHospitalDoctorDraftAssociationService(
            DiscoverPublicHospitalDoctorDraftAssociationRepository associations,
            DiscoverPublicProviderProfileRepository profiles,
            PublicHospitalDoctorAssociationService publishedAssociations
    ) {
        this.associations = associations;
        this.profiles = profiles;
        this.publishedAssociations = publishedAssociations;
    }

    @Transactional
    public DiscoverPublicHospitalDoctorDraftAssociationEntity upsertActiveAssociation(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicHospitalDoctorDraftAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
                        sourceSystem,
                        sourceHospitalReference,
                        sourceDoctorReference
                );
        if (existingAssociation.isEmpty()) {
            DiscoverPublicHospitalDoctorDraftAssociationEntity created = associations.save(DiscoverPublicHospitalDoctorDraftAssociationEntity.create(
                    publicHospitalReference,
                    publicDoctorReference,
                    sourceSystem,
                    sourceHospitalReference,
                    sourceDoctorReference,
                    true,
                    observedAt
            ));
            return created;
        }
        DiscoverPublicHospitalDoctorDraftAssociationEntity entity = existingAssociation.get();
        if (entity.isActive()
                && publicHospitalReference.equals(entity.getPublicHospitalReference())
                && publicDoctorReference.equals(entity.getPublicDoctorReference())
                && sourceSystem.equalsIgnoreCase(entity.getSourceSystem())) {
            return entity;
        }
        entity.activate(publicHospitalReference, publicDoctorReference, sourceSystem, sourceHospitalReference, sourceDoctorReference, observedAt);
        return associations.save(entity);
    }

    @Transactional
    public Optional<DiscoverPublicHospitalDoctorDraftAssociationEntity> deactivateAssociation(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicHospitalDoctorDraftAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
                        sourceSystem,
                        sourceHospitalReference,
                        sourceDoctorReference
                );
        if (existingAssociation.isEmpty()) {
            return Optional.empty();
        }
        DiscoverPublicHospitalDoctorDraftAssociationEntity entity = existingAssociation.get();
        if (!entity.isActive()) {
            return existingAssociation;
        }
        entity.deactivate(observedAt);
        return Optional.of(associations.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findAllAssociations() {
        return associations.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findActiveAssociationsByHospitalReference(UUID publicHospitalReference) {
        return associations.findByPublicHospitalReferenceAndActiveTrueOrderByCreatedAtAsc(publicHospitalReference);
    }

    @Transactional
    public List<UUID> listDraftDoctorReferencesByHospital(UUID publicHospitalReference) {
        ensureDraftProjectionFromPublished(publicHospitalReference, OffsetDateTime.now());
        return findActiveAssociationsByHospitalReference(publicHospitalReference).stream()
                .map(DiscoverPublicHospitalDoctorDraftAssociationEntity::getPublicDoctorReference)
                .distinct()
                .filter(this::isPublishedDoctor)
                .toList();
    }

    @Transactional
    public int reconcileDraftHospitalDoctors(
            UUID publicHospitalReference,
            UUID sourceHospitalReference,
            List<UUID> eligiblePublicDoctorReferences,
            OffsetDateTime observedAt
    ) {
        LinkedHashSet<UUID> eligible = eligiblePublicDoctorReferences == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(eligiblePublicDoctorReferences);
        List<DiscoverPublicHospitalDoctorDraftAssociationEntity> existing = associations
                .findByPublicHospitalReferenceOrderByCreatedAtAsc(publicHospitalReference);
        for (UUID publicDoctorReference : eligible) {
            upsertActiveAssociation(
                    publicHospitalReference,
                    publicDoctorReference,
                    SOURCE_SYSTEM_DISCOVER_PROVIDER,
                    sourceHospitalReference,
                    publicDoctorReference,
                    observedAt
            );
        }
        for (DiscoverPublicHospitalDoctorDraftAssociationEntity association : existing) {
            if (!eligible.contains(association.getPublicDoctorReference()) && association.isActive()) {
                association.deactivate(observedAt);
                associations.save(association);
            }
        }
        return findActiveAssociationsByHospitalReference(publicHospitalReference).size();
    }

    @Transactional
    public void ensureDraftProjectionFromPublished(UUID publicHospitalReference, OffsetDateTime observedAt) {
        if (publicHospitalReference == null || associations.existsByPublicHospitalReference(publicHospitalReference)) {
            return;
        }
        List<UUID> publishedDoctorReferences = publishedAssociations.listPublishedDoctorReferencesByHospital(publicHospitalReference);
        if (publishedDoctorReferences.isEmpty()) {
            return;
        }
        reconcileDraftHospitalDoctors(publicHospitalReference, publicHospitalReference, publishedDoctorReferences, observedAt);
    }

    @Transactional(readOnly = true)
    public List<UUID> listEffectiveDoctorReferencesByHospital(UUID publicHospitalReference) {
        if (publicHospitalReference == null) {
            return List.of();
        }
        if (associations.existsByPublicHospitalReference(publicHospitalReference)) {
            return listDraftDoctorReferencesByHospital(publicHospitalReference);
        }
        return publishedAssociations.listPublishedDoctorReferencesByHospital(publicHospitalReference);
    }

    @Transactional(readOnly = true)
    public boolean hasDraftRows(UUID publicHospitalReference) {
        return publicHospitalReference != null && associations.existsByPublicHospitalReference(publicHospitalReference);
    }

    private boolean isPublishedDoctor(UUID publicDoctorReference) {
        return profiles.findByProviderId(publicDoctorReference)
                .map(DiscoverPublicProviderProfileEntity::getPublicationStatus)
                .map(status -> "PUBLISHED".equalsIgnoreCase(status))
                .orElse(false);
    }
}
