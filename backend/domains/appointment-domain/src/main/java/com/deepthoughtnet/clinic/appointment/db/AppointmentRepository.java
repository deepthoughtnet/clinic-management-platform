package com.deepthoughtnet.clinic.appointment.db;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID>, JpaSpecificationExecutor<AppointmentEntity> {
    Optional<AppointmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AppointmentEntity> findByTenantIdAndAppointmentDateOrderByAppointmentTimeAscCreatedAtAsc(UUID tenantId, LocalDate appointmentDate);

    List<AppointmentEntity> findByTenantIdAndDoctorUserIdAndAppointmentDateOrderByTokenNumberAscAppointmentTimeAscCreatedAtAsc(
            UUID tenantId,
            UUID doctorUserId,
            LocalDate appointmentDate
    );

    List<AppointmentEntity> findByTenantIdAndPatientIdOrderByAppointmentDateDescAppointmentTimeDescCreatedAtDesc(UUID tenantId, UUID patientId);

    @Query("""
            select coalesce(max(a.tokenNumber), 0)
            from AppointmentEntity a
            where a.tenantId = :tenantId
              and a.doctorUserId = :doctorUserId
              and a.appointmentDate = :appointmentDate
            """)
    Integer findMaxTokenNumber(UUID tenantId, UUID doctorUserId, LocalDate appointmentDate);
}
