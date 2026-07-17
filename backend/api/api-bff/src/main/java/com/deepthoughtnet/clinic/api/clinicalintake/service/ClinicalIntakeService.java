package com.deepthoughtnet.clinic.api.clinicalintake.service;

import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeEntity;
import com.deepthoughtnet.clinic.api.clinicalintake.db.PatientClinicalIntakeRepository;
import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeRequest;
import com.deepthoughtnet.clinic.api.clinicalintake.dto.ClinicalIntakeResponse;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationVitalsCalculator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalIntakeService {
    private final PatientRepository patientRepository;
    private final PatientClinicalIntakeRepository intakeRepository;
    private final AppUserRepository appUserRepository;

    public ClinicalIntakeService(PatientRepository patientRepository,
                                 PatientClinicalIntakeRepository intakeRepository,
                                 AppUserRepository appUserRepository) {
        this.patientRepository = patientRepository;
        this.intakeRepository = intakeRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ClinicalIntakeResponse> latest(UUID tenantId, UUID patientId, UUID appointmentId, UUID consultationId) {
        PatientClinicalIntakeEntity intake = null;
        if (appointmentId != null) {
            intake = intakeRepository.findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, patientId, appointmentId).orElse(null);
        }
        if (intake == null && consultationId != null) {
            intake = intakeRepository.findFirstByTenantIdAndPatientIdAndConsultationIdOrderByCreatedAtDesc(tenantId, patientId, consultationId).orElse(null);
        }
        if (intake == null && appointmentId == null && consultationId == null) {
            intake = intakeRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId).stream().findFirst().orElse(null);
        }
        return Optional.ofNullable(intake).map(this::toResponse);
    }

    @Transactional
    public ClinicalIntakeResponse save(UUID tenantId, UUID patientId, ClinicalIntakeRequest request, UUID actorAppUserId) {
        if (tenantId == null || patientId == null || actorAppUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing clinical intake context");
        }
        patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        String recordedByName = resolveRecordedByName(tenantId, actorAppUserId);
        Double bmi = ConsultationVitalsCalculator.calculateBmi(request.weightKg(), request.heightCm());
        PatientClinicalIntakeEntity intake = request.appointmentId() == null
                ? intakeRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId).stream().findFirst().orElse(null)
                : intakeRepository.findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(tenantId, patientId, request.appointmentId()).orElse(null);
        if (intake == null) {
            intake = PatientClinicalIntakeEntity.create(
                    UUID.randomUUID(),
                    tenantId,
                    patientId,
                    request.appointmentId(),
                    request.consultationId(),
                    normalizeText(request.chiefComplaint()),
                    request.heightCm(),
                    request.weightKg(),
                    bmi,
                    request.bloodPressureSystolic(),
                    request.bloodPressureDiastolic(),
                    request.pulseRate(),
                    request.temperature(),
                    request.temperatureUnit(),
                    request.spo2(),
                    request.respiratoryRate(),
                    request.randomBloodSugar(),
                    request.painScore(),
                    normalizeText(request.notes()),
                    actorAppUserId,
                    recordedByName,
                    request.complete(),
                    actorAppUserId,
                    actorAppUserId
            );
        } else {
            intake.update(
                    request.appointmentId(),
                    request.consultationId(),
                    normalizeText(request.chiefComplaint()),
                    request.heightCm(),
                    request.weightKg(),
                    bmi,
                    request.bloodPressureSystolic(),
                    request.bloodPressureDiastolic(),
                    request.pulseRate(),
                    request.temperature(),
                    request.temperatureUnit(),
                    request.spo2(),
                    request.respiratoryRate(),
                    request.randomBloodSugar(),
                    request.painScore(),
                    normalizeText(request.notes()),
                    actorAppUserId,
                    recordedByName,
                    request.complete(),
                    actorAppUserId
            );
        }
        PatientClinicalIntakeEntity saved = intakeRepository.save(intake);
        return toResponse(saved);
    }

    private ClinicalIntakeResponse toResponse(PatientClinicalIntakeEntity entity) {
        Double bmi = entity.getBmi();
        return new ClinicalIntakeResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                entity.getAppointmentId(),
                entity.getConsultationId(),
                entity.isComplete() ? "INTAKE_COMPLETE" : "PENDING_INTAKE",
                entity.getChiefComplaint(),
                entity.getHeightCm(),
                entity.getWeightKg(),
                bmi,
                ConsultationVitalsCalculator.bmiCategory(entity.getWeightKg(), entity.getHeightCm()),
                entity.getBloodPressureSystolic(),
                entity.getBloodPressureDiastolic(),
                entity.getPulseRate(),
                entity.getTemperature(),
                entity.getTemperatureUnit(),
                entity.getSpo2(),
                entity.getRespiratoryRate(),
                entity.getRandomBloodSugar(),
                entity.getPainScore(),
                entity.getNotes(),
                entity.getRecordedByUserId(),
                entity.getRecordedByName(),
                entity.isComplete(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String resolveRecordedByName(UUID tenantId, UUID actorAppUserId) {
        return appUserRepository.findByTenantIdAndId(tenantId, actorAppUserId)
                .map(user -> user.getDisplayName() != null && !user.getDisplayName().isBlank() ? user.getDisplayName() : user.getEmail())
                .orElse("Unknown User");
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim().isEmpty() ? null : value.trim().replaceAll("\\s+", " ");
    }
}
