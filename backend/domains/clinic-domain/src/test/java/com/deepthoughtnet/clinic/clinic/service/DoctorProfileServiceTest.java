package com.deepthoughtnet.clinic.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.db.DoctorProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.DoctorProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DoctorProfileServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID doctorUserId = UUID.randomUUID();
    private DoctorProfileRepository repository;
    private DoctorProfileService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DoctorProfileRepository.class);
        service = new DoctorProfileService(repository);
        when(repository.save(any(DoctorProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProfileWhenMissing() {
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.empty());
        var result = service.upsert(tenantId, doctorUserId, new DoctorProfileUpsertCommand("9999", "Dermatology", "MBBS", "REG-1", "Room 2", true));
        assertThat(result.specialization()).isEqualTo("Dermatology");
        assertThat(result.active()).isTrue();
    }

    @Test
    void updatesExistingProfile() {
        DoctorProfileEntity existing = DoctorProfileEntity.create(tenantId, doctorUserId);
        existing.update("1111", "ENT", "MD", "REG-2", "Room 1", true);
        when(repository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)).thenReturn(Optional.of(existing));
        var result = service.upsert(tenantId, doctorUserId, new DoctorProfileUpsertCommand("2222", "Cardiology", "DM", "REG-3", "Room 3", false));
        assertThat(result.mobile()).isEqualTo("2222");
        assertThat(result.specialization()).isEqualTo("Cardiology");
        assertThat(result.active()).isFalse();
    }
}
