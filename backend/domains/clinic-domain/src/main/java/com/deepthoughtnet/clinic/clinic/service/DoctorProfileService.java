package com.deepthoughtnet.clinic.clinic.service;

import com.deepthoughtnet.clinic.clinic.db.DoctorProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.DoctorProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DoctorProfileService {
    private final DoctorProfileRepository doctorProfileRepository;

    public DoctorProfileService(DoctorProfileRepository doctorProfileRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @Transactional(readOnly = true)
    public Optional<DoctorProfileRecord> findByDoctorUserId(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId).map(this::toRecord);
    }

    @Transactional
    public DoctorProfileRecord upsert(UUID tenantId, UUID doctorUserId, DoctorProfileUpsertCommand command) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        DoctorProfileEntity entity = doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .orElseGet(() -> DoctorProfileEntity.create(tenantId, doctorUserId));
        entity.update(
                normalizeNullable(command.mobile()),
                normalizeNullable(command.specialization()),
                normalizeNullable(command.qualification()),
                normalizeNullable(command.registrationNumber()),
                normalizeNullable(command.consultationRoom()),
                command.active()
        );
        return toRecord(doctorProfileRepository.save(entity));
    }

    private DoctorProfileRecord toRecord(DoctorProfileEntity entity) {
        return new DoctorProfileRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getDoctorUserId(),
                entity.getMobile(),
                entity.getSpecialization(),
                entity.getQualification(),
                entity.getRegistrationNumber(),
                entity.getConsultationRoom(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireDoctor(UUID doctorUserId) {
        if (doctorUserId == null) {
            throw new IllegalArgumentException("doctorUserId is required");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
