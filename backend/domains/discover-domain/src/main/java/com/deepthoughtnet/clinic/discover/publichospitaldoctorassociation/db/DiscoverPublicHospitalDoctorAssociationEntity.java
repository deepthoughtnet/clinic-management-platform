package com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db;

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
        name = "discover_public_hospital_doctor_associations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_discover_public_hospital_doctor_associations_natural_key",
                        columnNames = {"source_system", "source_hospital_reference", "source_doctor_reference"}
                )
        }
)
public class DiscoverPublicHospitalDoctorAssociationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "public_hospital_reference", nullable = false)
    private UUID publicHospitalReference;

    @Column(name = "public_doctor_reference", nullable = false)
    private UUID publicDoctorReference;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_hospital_reference", nullable = false)
    private UUID sourceHospitalReference;

    @Column(name = "source_doctor_reference", nullable = false)
    private UUID sourceDoctorReference;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverPublicHospitalDoctorAssociationEntity() {
    }

    public static DiscoverPublicHospitalDoctorAssociationEntity create(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            boolean active,
            OffsetDateTime observedAt
    ) {
        DiscoverPublicHospitalDoctorAssociationEntity entity = new DiscoverPublicHospitalDoctorAssociationEntity();
        entity.id = UUID.randomUUID();
        entity.publicHospitalReference = publicHospitalReference;
        entity.publicDoctorReference = publicDoctorReference;
        entity.sourceSystem = sourceSystem;
        entity.sourceHospitalReference = sourceHospitalReference;
        entity.sourceDoctorReference = sourceDoctorReference;
        entity.active = active;
        entity.createdAt = observedAt == null ? OffsetDateTime.now() : observedAt;
        entity.updatedAt = entity.createdAt;
        entity.rowVersion = 0L;
        return entity;
    }

    public void activate(
            UUID publicHospitalReference,
            UUID publicDoctorReference,
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference,
            OffsetDateTime observedAt
    ) {
        this.publicHospitalReference = publicHospitalReference;
        this.publicDoctorReference = publicDoctorReference;
        this.sourceSystem = sourceSystem;
        this.sourceHospitalReference = sourceHospitalReference;
        this.sourceDoctorReference = sourceDoctorReference;
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

    public UUID getPublicHospitalReference() {
        return publicHospitalReference;
    }

    public UUID getPublicDoctorReference() {
        return publicDoctorReference;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public UUID getSourceHospitalReference() {
        return sourceHospitalReference;
    }

    public UUID getSourceDoctorReference() {
        return sourceDoctorReference;
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
                value(sourceHospitalReference),
                value(sourceDoctorReference));
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
