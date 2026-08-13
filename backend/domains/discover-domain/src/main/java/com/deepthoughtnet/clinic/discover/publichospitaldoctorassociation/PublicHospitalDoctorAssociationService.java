package com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation;

import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorAssociationEntity;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorAssociationRepository;
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
public class PublicHospitalDoctorAssociationService {
    public static final String SOURCE_SYSTEM_DISCOVER_PROVIDER = "DISCOVER_PROVIDER_PROFILE";

    private final DiscoverPublicHospitalDoctorAssociationRepository associations;
    private final DiscoverPublicProviderProfileRepository profiles;

    public PublicHospitalDoctorAssociationService(
            DiscoverPublicHospitalDoctorAssociationRepository associations,
            DiscoverPublicProviderProfileRepository profiles
    ) {
        this.associations = associations;
        this.profiles = profiles;
    }

    @Transactional
    public DiscoverPublicHospitalDoctorAssociationEntity upsertActiveAssociation(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicHospitalDoctorAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
                        sourceSystem,
                        sourceHospitalReference,
                        sourceDoctorReference
                );
        if (existingAssociation.isEmpty()) {
            DiscoverPublicHospitalDoctorAssociationEntity created = associations.save(DiscoverPublicHospitalDoctorAssociationEntity.create(
                    publicHospitalReference,
                    publicDoctorReference,
                    sourceSystem,
                    sourceHospitalReference,
                    sourceDoctorReference,
                    true,
                    observedAt
            ));
            refreshDoctorCount(publicHospitalReference, observedAt);
            return created;
        }
        DiscoverPublicHospitalDoctorAssociationEntity entity = existingAssociation.get();
        if (entity.isActive()
                && publicHospitalReference.equals(entity.getPublicHospitalReference())
                && publicDoctorReference.equals(entity.getPublicDoctorReference())
                && sourceSystem.equalsIgnoreCase(entity.getSourceSystem())) {
            return entity;
        }
        entity.activate(publicHospitalReference, publicDoctorReference, sourceSystem, sourceHospitalReference, sourceDoctorReference, observedAt);
        DiscoverPublicHospitalDoctorAssociationEntity saved = associations.save(entity);
        refreshDoctorCount(publicHospitalReference, observedAt);
        return saved;
    }

    @Transactional
    public Optional<DiscoverPublicHospitalDoctorAssociationEntity> deactivateAssociation(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            OffsetDateTime observedAt
    ) {
        Optional<DiscoverPublicHospitalDoctorAssociationEntity> existingAssociation = associations
                .findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
                        sourceSystem,
                        sourceHospitalReference,
                        sourceDoctorReference
                );
        if (existingAssociation.isEmpty()) {
            return Optional.empty();
        }
        DiscoverPublicHospitalDoctorAssociationEntity entity = existingAssociation.get();
        if (!entity.isActive()) {
            return existingAssociation;
        }
        entity.deactivate(observedAt);
        DiscoverPublicHospitalDoctorAssociationEntity saved = associations.save(entity);
        refreshDoctorCount(publicHospitalReference, observedAt);
        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicHospitalDoctorAssociationEntity> findActiveAssociationsByPublicHospitalReference(UUID publicHospitalReference) {
        return associations.findByPublicHospitalReferenceAndActiveTrueOrderByCreatedAtAsc(publicHospitalReference);
    }

    @Transactional(readOnly = true)
    public List<DiscoverPublicHospitalDoctorAssociationEntity> findAllAssociations() {
        return associations.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public List<UUID> listPublishedDoctorReferencesByHospital(UUID publicHospitalReference) {
        return findActiveAssociationsByPublicHospitalReference(publicHospitalReference).stream()
                .map(DiscoverPublicHospitalDoctorAssociationEntity::getPublicDoctorReference)
                .distinct()
                .filter(this::isPublishedDoctor)
                .toList();
    }

    @Transactional(readOnly = true)
    public int countPublishedDoctorsByHospital(UUID publicHospitalReference) {
        return (int) listPublishedDoctorReferencesByHospital(publicHospitalReference).stream().distinct().count();
    }

    @Transactional(readOnly = true)
    public List<UUID> listPublishedHospitalReferencesByDoctor(UUID publicDoctorReference) {
        return associations.findByPublicDoctorReferenceAndActiveTrueOrderByCreatedAtAsc(publicDoctorReference).stream()
                .map(DiscoverPublicHospitalDoctorAssociationEntity::getPublicHospitalReference)
                .distinct()
                .filter(this::isPublishedHospital)
                .toList();
    }

    @Transactional
    public int reconcileHospitalDoctors(
            UUID publicHospitalReference,
            UUID sourceHospitalReference,
            List<UUID> eligiblePublicDoctorReferences,
            OffsetDateTime observedAt
    ) {
        LinkedHashSet<UUID> eligible = eligiblePublicDoctorReferences == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(eligiblePublicDoctorReferences);
        List<DiscoverPublicHospitalDoctorAssociationEntity> existing = associations
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
        for (DiscoverPublicHospitalDoctorAssociationEntity association : existing) {
            if (!eligible.contains(association.getPublicDoctorReference()) && association.isActive()) {
                association.deactivate(observedAt);
                associations.save(association);
            }
        }
        return refreshDoctorCount(publicHospitalReference, observedAt);
    }

    @Transactional
    public int deactivateAllForHospital(UUID publicHospitalReference, UUID sourceHospitalReference, OffsetDateTime observedAt) {
        List<DiscoverPublicHospitalDoctorAssociationEntity> existing = associations.findByPublicHospitalReferenceOrderByCreatedAtAsc(publicHospitalReference);
        for (DiscoverPublicHospitalDoctorAssociationEntity association : existing) {
            if (association.isActive()) {
                association.deactivate(observedAt);
                associations.save(association);
            }
        }
        return refreshDoctorCount(publicHospitalReference, observedAt);
    }

    private int refreshDoctorCount(UUID publicHospitalReference, OffsetDateTime observedAt) {
        int doctorCount = countPublishedDoctorsByHospital(publicHospitalReference);
        profiles.findByProviderId(publicHospitalReference).ifPresent(hospital -> {
            hospital.updateDoctorCount(doctorCount, observedAt);
            profiles.save(hospital);
        });
        return doctorCount;
    }

    private boolean isPublishedDoctor(UUID publicDoctorReference) {
        return profiles.findByProviderId(publicDoctorReference)
                .map(DiscoverPublicProviderProfileEntity::getPublicationStatus)
                .map(status -> "PUBLISHED".equalsIgnoreCase(status))
                .orElse(false);
    }

    private boolean isPublishedHospital(UUID publicHospitalReference) {
        return profiles.findByProviderId(publicHospitalReference)
                .map(DiscoverPublicProviderProfileEntity::getPublicationStatus)
                .map(status -> "PUBLISHED".equalsIgnoreCase(status))
                .orElse(false);
    }
}
