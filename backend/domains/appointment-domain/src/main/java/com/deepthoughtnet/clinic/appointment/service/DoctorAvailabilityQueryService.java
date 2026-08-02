package com.deepthoughtnet.clinic.appointment.service;

import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorAvailabilityQueryService {
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;

    public DoctorAvailabilityQueryService(DoctorAvailabilityRepository doctorAvailabilityRepository) {
        this.doctorAvailabilityRepository = doctorAvailabilityRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveAvailability(UUID tenantId, UUID doctorUserId) {
        if (tenantId == null || doctorUserId == null) {
            return false;
        }
        return doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(tenantId, doctorUserId)
                .stream()
                .anyMatch(DoctorAvailabilityEntity::isActive);
    }
}
