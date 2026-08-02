package com.deepthoughtnet.clinic.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityEntity;
import com.deepthoughtnet.clinic.appointment.db.DoctorAvailabilityRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorAvailabilityQueryServiceTest {
    @Mock
    private DoctorAvailabilityRepository doctorAvailabilityRepository;

    @Test
    void hasActiveAvailabilityReturnsTrueWhenAnActiveRowExists() {
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        DoctorAvailabilityEntity active = availability(true);
        DoctorAvailabilityEntity inactive = availability(false);
        when(doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(tenantId, doctorUserId))
                .thenReturn(List.of(inactive, active));

        DoctorAvailabilityQueryService service = new DoctorAvailabilityQueryService(doctorAvailabilityRepository);

        assertThat(service.hasActiveAvailability(tenantId, doctorUserId)).isTrue();
    }

    @Test
    void hasActiveAvailabilityReturnsFalseWhenNoActiveRowsExist() {
        UUID tenantId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        when(doctorAvailabilityRepository.findByTenantIdAndDoctorUserIdOrderByDayOfWeekAscStartTimeAsc(tenantId, doctorUserId))
                .thenReturn(List.of(availability(false)));

        DoctorAvailabilityQueryService service = new DoctorAvailabilityQueryService(doctorAvailabilityRepository);

        assertThat(service.hasActiveAvailability(tenantId, doctorUserId)).isFalse();
    }

    private DoctorAvailabilityEntity availability(boolean active) {
        DoctorAvailabilityEntity entity = DoctorAvailabilityEntity.create(UUID.randomUUID(), UUID.randomUUID());
        entity.update(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), null, null, 15, 1, active);
        return entity;
    }
}
