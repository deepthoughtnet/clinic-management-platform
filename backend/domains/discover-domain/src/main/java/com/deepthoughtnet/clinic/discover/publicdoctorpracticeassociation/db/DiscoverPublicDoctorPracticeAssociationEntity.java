package com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "discover_public_doctor_practice_associations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_discover_public_doctor_practice_associations_natural_key",
                        columnNames = {"source_system", "source_doctor_reference", "source_practice_reference"}
                )
        }
)
public class DiscoverPublicDoctorPracticeAssociationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "public_doctor_reference", nullable = false)
    private UUID publicDoctorReference;

    @Column(name = "public_practice_reference", nullable = false)
    private UUID publicPracticeReference;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_doctor_reference", nullable = false)
    private UUID sourceDoctorReference;

    @Column(name = "source_practice_reference", nullable = false)
    private UUID sourcePracticeReference;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverPublicDoctorPracticeAssociationEntity() {
    }

    public static DiscoverPublicDoctorPracticeAssociationEntity create(
            UUID publicDoctorReference,
            UUID publicPracticeReference,
            String sourceSystem,
            UUID sourceDoctorReference,
            UUID sourcePracticeReference,
            boolean active,
            OffsetDateTime observedAt
    ) {
        DiscoverPublicDoctorPracticeAssociationEntity entity = new DiscoverPublicDoctorPracticeAssociationEntity();
        entity.id = UUID.randomUUID();
        entity.publicDoctorReference = publicDoctorReference;
        entity.publicPracticeReference = publicPracticeReference;
        entity.sourceSystem = sourceSystem;
        entity.sourceDoctorReference = sourceDoctorReference;
        entity.sourcePracticeReference = sourcePracticeReference;
        entity.active = active;
        entity.createdAt = observedAt == null ? OffsetDateTime.now() : observedAt;
        entity.updatedAt = entity.createdAt;
        entity.rowVersion = 0L;
        return entity;
    }

    public void activate(
            UUID publicDoctorReference,
            UUID publicPracticeReference,
            String sourceSystem,
            UUID sourceDoctorReference,
            UUID sourcePracticeReference,
            OffsetDateTime observedAt
    ) {
        this.publicDoctorReference = publicDoctorReference;
        this.publicPracticeReference = publicPracticeReference;
        this.sourceSystem = sourceSystem;
        this.sourceDoctorReference = sourceDoctorReference;
        this.sourcePracticeReference = sourcePracticeReference;
        this.active = true;
        this.updatedAt = observedAt == null ? OffsetDateTime.now() : observedAt;
    }

    public void deactivate(OffsetDateTime observedAt) {
        this.active = false;
        this.updatedAt = observedAt == null ? OffsetDateTime.now() : observedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPublicDoctorReference() {
        return publicDoctorReference;
    }

    public UUID getPublicPracticeReference() {
        return publicPracticeReference;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public UUID getSourceDoctorReference() {
        return sourceDoctorReference;
    }

    public UUID getSourcePracticeReference() {
        return sourcePracticeReference;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public String naturalKey() {
        return String.join("|",
                value(sourceSystem),
                value(sourceDoctorReference),
                value(sourcePracticeReference));
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
