package com.deepthoughtnet.clinic.consultation.db;

import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "consultations",
        indexes = {
                @Index(name = "ix_consultations_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_consultations_tenant_doctor", columnList = "tenant_id,doctor_user_id"),
                @Index(name = "ix_consultations_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_consultations_tenant_appointment", columnList = "tenant_id,appointment_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_consultations_tenant_appointment", columnNames = {"tenant_id", "appointment_id"})
        }
)
public class ConsultationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "chief_complaints", columnDefinition = "text")
    private String chiefComplaints;

    @Column(columnDefinition = "text")
    private String symptoms;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;

    @Column(columnDefinition = "text")
    private String advice;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConsultationStatus status = ConsultationStatus.DRAFT;

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "temperature_value")
    private Double temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "temperature_unit", length = 16)
    private TemperatureUnit temperatureUnit;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected ConsultationEntity() {
    }

    public static ConsultationEntity create(UUID tenantId, UUID patientId, UUID doctorUserId, UUID appointmentId) {
        ConsultationEntity entity = new ConsultationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.doctorUserId = doctorUserId;
        entity.appointmentId = appointmentId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            String chiefComplaints,
            String symptoms,
            String diagnosis,
            String clinicalNotes,
            String advice,
            LocalDate followUpDate,
            Integer bloodPressureSystolic,
            Integer bloodPressureDiastolic,
            Integer pulseRate,
            Double temperature,
            TemperatureUnit temperatureUnit,
            Double weightKg,
            Double heightCm,
            Integer spo2,
            Integer respiratoryRate
    ) {
        this.chiefComplaints = chiefComplaints;
        this.symptoms = symptoms;
        this.diagnosis = diagnosis;
        this.clinicalNotes = clinicalNotes;
        this.advice = advice;
        this.followUpDate = followUpDate;
        this.bloodPressureSystolic = bloodPressureSystolic;
        this.bloodPressureDiastolic = bloodPressureDiastolic;
        this.pulseRate = pulseRate;
        this.temperature = temperature;
        this.temperatureUnit = temperatureUnit;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
        this.spo2 = spo2;
        this.respiratoryRate = respiratoryRate;
        this.updatedAt = OffsetDateTime.now();
    }

    public void complete() {
        this.status = ConsultationStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
        this.updatedAt = this.completedAt;
    }

    public void cancel() {
        this.status = ConsultationStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getChiefComplaints() { return chiefComplaints; }
    public String getSymptoms() { return symptoms; }
    public String getDiagnosis() { return diagnosis; }
    public String getClinicalNotes() { return clinicalNotes; }
    public String getAdvice() { return advice; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public ConsultationStatus getStatus() { return status; }
    public Integer getBloodPressureSystolic() { return bloodPressureSystolic; }
    public Integer getBloodPressureDiastolic() { return bloodPressureDiastolic; }
    public Integer getPulseRate() { return pulseRate; }
    public Double getTemperature() { return temperature; }
    public TemperatureUnit getTemperatureUnit() { return temperatureUnit; }
    public Double getWeightKg() { return weightKg; }
    public Double getHeightCm() { return heightCm; }
    public Integer getSpo2() { return spo2; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
