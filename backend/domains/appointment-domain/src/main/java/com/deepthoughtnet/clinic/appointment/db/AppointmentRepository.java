package com.deepthoughtnet.clinic.appointment.db;

import java.time.LocalDate;
import java.time.LocalTime;
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

    List<AppointmentEntity> findByTenantIdAndDoctorUserId(UUID tenantId, UUID doctorUserId);

    List<AppointmentEntity> findByTenantIdAndPatientIdOrderByAppointmentDateDescAppointmentTimeDescCreatedAtDesc(UUID tenantId, UUID patientId);

    boolean existsByTenantIdAndIdAndDoctorUserId(UUID tenantId, UUID id, UUID doctorUserId);

    boolean existsByTenantIdAndPatientIdAndDoctorUserId(UUID tenantId, UUID patientId, UUID doctorUserId);

    boolean existsByTenantIdAndIdAndPatientIdAndDoctorUserId(UUID tenantId, UUID id, UUID patientId, UUID doctorUserId);

    boolean existsByTenantIdAndDoctorUserIdAndPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(
            UUID tenantId,
            UUID doctorUserId,
            UUID patientId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            List<com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus> excludedStatuses
    );

    @Query("""
            select coalesce(max(a.tokenNumber), 0)
            from AppointmentEntity a
            where a.tenantId = :tenantId
              and a.doctorUserId = :doctorUserId
              and a.appointmentDate = :appointmentDate
            """)
    Integer findMaxTokenNumber(UUID tenantId, UUID doctorUserId, LocalDate appointmentDate);
}
