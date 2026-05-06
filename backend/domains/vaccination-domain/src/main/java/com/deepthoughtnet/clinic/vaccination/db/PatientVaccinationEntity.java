package com.deepthoughtnet.clinic.vaccination.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patient_vaccinations",
        indexes = {
                @Index(name = "ix_patient_vaccinations_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_patient_vaccinations_tenant_due", columnList = "tenant_id,next_due_date")
        }
)
public class PatientVaccinationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "vaccine_id")
    private UUID vaccineId;

    @Column(name = "vaccine_name_snapshot", nullable = false, length = 256)
    private String vaccineNameSnapshot;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "given_date", nullable = false)
    private LocalDate givenDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "batch_number", length = 128)
    private String batchNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "administered_by_user_id")
    private UUID administeredByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected PatientVaccinationEntity() {
    }

    public static PatientVaccinationEntity create(
            UUID tenantId,
            UUID patientId,
            UUID vaccineId,
            String vaccineNameSnapshot,
            Integer doseNumber,
            LocalDate givenDate,
            LocalDate nextDueDate,
            String batchNumber,
            String notes,
            UUID administeredByUserId
    ) {
        PatientVaccinationEntity entity = new PatientVaccinationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.vaccineId = vaccineId;
        entity.vaccineNameSnapshot = vaccineNameSnapshot;
        entity.doseNumber = doseNumber;
        entity.givenDate = givenDate == null ? LocalDate.now() : givenDate;
        entity.nextDueDate = nextDueDate;
        entity.batchNumber = batchNumber;
        entity.notes = notes;
        entity.administeredByUserId = administeredByUserId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getVaccineId() { return vaccineId; }
    public String getVaccineNameSnapshot() { return vaccineNameSnapshot; }
    public Integer getDoseNumber() { return doseNumber; }
    public LocalDate getGivenDate() { return givenDate; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public String getBatchNumber() { return batchNumber; }
    public String getNotes() { return notes; }
    public UUID getAdministeredByUserId() { return administeredByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
