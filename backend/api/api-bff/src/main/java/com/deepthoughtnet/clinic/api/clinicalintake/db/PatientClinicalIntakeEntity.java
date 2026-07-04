package com.deepthoughtnet.clinic.api.clinicalintake.db;

import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patient_clinical_intakes",
        indexes = {
                @Index(name = "ix_patient_clinical_intakes_tenant_patient_created", columnList = "tenant_id,patient_id,created_at"),
                @Index(name = "ix_patient_clinical_intakes_tenant_patient_appointment", columnList = "tenant_id,patient_id,appointment_id"),
                @Index(name = "ix_patient_clinical_intakes_tenant_patient_consultation", columnList = "tenant_id,patient_id,consultation_id"),
                @Index(name = "ix_patient_clinical_intakes_tenant_completed", columnList = "tenant_id,complete,created_at")
        }
)
public class PatientClinicalIntakeEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "chief_complaint", columnDefinition = "text")
    private String chiefComplaint;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "bmi")
    private Double bmi;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "temperature")
    private Double temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "temperature_unit", length = 16)
    private TemperatureUnit temperatureUnit;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "random_blood_sugar")
    private Double randomBloodSugar;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "recorded_by_user_id", nullable = false)
    private UUID recordedByUserId;

    @Column(name = "recorded_by_name", nullable = false, length = 255)
    private String recordedByName;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    protected PatientClinicalIntakeEntity() {
    }

    public static PatientClinicalIntakeEntity create(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID appointmentId,
            UUID consultationId,
            String chiefComplaint,
            Double heightCm,
            Double weightKg,
            Double bmi,
            Integer bloodPressureSystolic,
            Integer bloodPressureDiastolic,
            Integer pulseRate,
            Double temperature,
            TemperatureUnit temperatureUnit,
            Integer spo2,
            Integer respiratoryRate,
            Double randomBloodSugar,
            Integer painScore,
            String notes,
            UUID recordedByUserId,
            String recordedByName,
            boolean complete,
            UUID createdBy,
            UUID updatedBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PatientClinicalIntakeEntity entity = new PatientClinicalIntakeEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.appointmentId = appointmentId;
        entity.consultationId = consultationId;
        entity.chiefComplaint = chiefComplaint;
        entity.heightCm = heightCm;
        entity.weightKg = weightKg;
        entity.bmi = bmi;
        entity.bloodPressureSystolic = bloodPressureSystolic;
        entity.bloodPressureDiastolic = bloodPressureDiastolic;
        entity.pulseRate = pulseRate;
        entity.temperature = temperature;
        entity.temperatureUnit = temperatureUnit;
        entity.spo2 = spo2;
        entity.respiratoryRate = respiratoryRate;
        entity.randomBloodSugar = randomBloodSugar;
        entity.painScore = painScore;
        entity.notes = notes;
        entity.recordedByUserId = recordedByUserId;
        entity.recordedByName = recordedByName;
        entity.complete = complete;
        entity.completedAt = complete ? now : null;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.createdBy = createdBy;
        entity.updatedBy = updatedBy;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public Double getBmi() {
        return bmi;
    }

    public Integer getBloodPressureSystolic() {
        return bloodPressureSystolic;
    }

    public Integer getBloodPressureDiastolic() {
        return bloodPressureDiastolic;
    }

    public Integer getPulseRate() {
        return pulseRate;
    }

    public Double getTemperature() {
        return temperature;
    }

    public TemperatureUnit getTemperatureUnit() {
        return temperatureUnit;
    }

    public Integer getSpo2() {
        return spo2;
    }

    public Integer getRespiratoryRate() {
        return respiratoryRate;
    }

    public Double getRandomBloodSugar() {
        return randomBloodSugar;
    }

    public Integer getPainScore() {
        return painScore;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getRecordedByUserId() {
        return recordedByUserId;
    }

    public String getRecordedByName() {
        return recordedByName;
    }

    public boolean isComplete() {
        return complete;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void update(
            UUID appointmentId,
            UUID consultationId,
            String chiefComplaint,
            Double heightCm,
            Double weightKg,
            Double bmi,
            Integer bloodPressureSystolic,
            Integer bloodPressureDiastolic,
            Integer pulseRate,
            Double temperature,
            TemperatureUnit temperatureUnit,
            Integer spo2,
            Integer respiratoryRate,
            Double randomBloodSugar,
            Integer painScore,
            String notes,
            UUID recordedByUserId,
            String recordedByName,
            boolean complete,
            UUID updatedBy
    ) {
        this.appointmentId = appointmentId;
        this.consultationId = consultationId;
        this.chiefComplaint = chiefComplaint;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.bmi = bmi;
        this.bloodPressureSystolic = bloodPressureSystolic;
        this.bloodPressureDiastolic = bloodPressureDiastolic;
        this.pulseRate = pulseRate;
        this.temperature = temperature;
        this.temperatureUnit = temperatureUnit;
        this.spo2 = spo2;
        this.respiratoryRate = respiratoryRate;
        this.randomBloodSugar = randomBloodSugar;
        this.painScore = painScore;
        this.notes = notes;
        this.recordedByUserId = recordedByUserId;
        this.recordedByName = recordedByName;
        this.complete = complete;
        if (complete && this.completedAt == null) {
            this.completedAt = OffsetDateTime.now();
        } else if (!complete) {
            this.completedAt = null;
        }
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = updatedBy;
    }
}
