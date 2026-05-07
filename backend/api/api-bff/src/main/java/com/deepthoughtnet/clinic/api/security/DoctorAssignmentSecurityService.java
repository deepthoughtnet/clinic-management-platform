package com.deepthoughtnet.clinic.api.security;

import com.deepthoughtnet.clinic.appointment.db.AppointmentEntity;
import com.deepthoughtnet.clinic.appointment.db.AppointmentRepository;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DoctorAssignmentSecurityService {
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;

    public DoctorAssignmentSecurityService(AppointmentRepository appointmentRepository, ConsultationRepository consultationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
    }

    public boolean isDoctor() {
        RequestContext context = RequestContextHolder.get();
        return context != null && Roles.DOCTOR.equals(normalizeRole(context.tenantRole()));
    }

    public boolean isReceptionist() {
        RequestContext context = RequestContextHolder.get();
        return context != null && Roles.RECEPTIONIST.equals(normalizeRole(context.tenantRole()));
    }

    public boolean isClinicAdmin() {
        RequestContext context = RequestContextHolder.get();
        return context != null && Roles.CLINIC_ADMIN.equals(normalizeRole(context.tenantRole()));
    }

    public UUID currentDoctorUserId() {
        RequestContext context = RequestContextHolder.require();
        if (!Roles.DOCTOR.equals(normalizeRole(context.tenantRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not a doctor");
        }
        return context.appUserId();
    }

    public UUID effectiveDoctorUserId(UUID requestedDoctorUserId) {
        if (!isDoctor()) {
            return requestedDoctorUserId;
        }
        UUID currentDoctorUserId = currentDoctorUserId();
        if (requestedDoctorUserId != null && !currentDoctorUserId.equals(requestedDoctorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can only access their own assignments");
        }
        return currentDoctorUserId;
    }

    public List<UUID> visiblePatientIds(UUID tenantId) {
        if (!isDoctor()) {
            return List.of();
        }
        UUID doctorUserId = currentDoctorUserId();
        Set<UUID> patientIds = new LinkedHashSet<>();
        appointmentRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .stream()
                .map(AppointmentEntity::getPatientId)
                .forEach(patientIds::add);
        consultationRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .stream()
                .map(ConsultationEntity::getPatientId)
                .forEach(patientIds::add);
        return List.copyOf(patientIds);
    }

    public void requirePatientAccess(UUID tenantId, UUID patientId) {
        if (!isDoctor()) {
            return;
        }
        UUID doctorUserId = currentDoctorUserId();
        boolean appointmentAssigned = appointmentRepository.existsByTenantIdAndPatientIdAndDoctorUserId(tenantId, patientId, doctorUserId);
        boolean consultationAssigned = consultationRepository.existsByTenantIdAndPatientIdAndDoctorUserId(tenantId, patientId, doctorUserId);
        if (!appointmentAssigned && !consultationAssigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient is not assigned to this doctor");
        }
    }

    public void requireAppointmentAccess(UUID tenantId, UUID appointmentId) {
        if (!isDoctor()) {
            return;
        }
        UUID doctorUserId = currentDoctorUserId();
        if (!appointmentRepository.existsByTenantIdAndIdAndDoctorUserId(tenantId, appointmentId, doctorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment is not assigned to this doctor");
        }
    }

    public void requireNonDoctorQueueStatusUpdate() {
        if (isDoctor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors must start and complete consultations through the consultation workflow");
        }
        if (!isClinicAdmin() && !isReceptionist()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic administrators and receptionists can update queue status");
        }
    }

    public void requireConsultationAccess(UUID tenantId, UUID consultationId) {
        if (!isDoctor()) {
            return;
        }
        UUID doctorUserId = currentDoctorUserId();
        if (!consultationRepository.existsByTenantIdAndIdAndDoctorUserId(tenantId, consultationId, doctorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consultation is not assigned to this doctor");
        }
    }

    public void requireConsultationCommandAccess(UUID tenantId, UUID patientId, UUID doctorUserId, UUID appointmentId) {
        if (!isDoctor()) {
            return;
        }
        UUID currentDoctorUserId = currentDoctorUserId();
        if (!currentDoctorUserId.equals(doctorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can only create or update their own consultations");
        }
        if (appointmentId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consultation requires an assigned appointment or queue item");
        }
        boolean assignedAppointment = appointmentRepository.existsByTenantIdAndIdAndPatientIdAndDoctorUserId(tenantId, appointmentId, patientId, currentDoctorUserId);
        if (!assignedAppointment) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment is not assigned to this doctor and patient");
        }
    }

    public void requireConsultationStartAccess(UUID tenantId, UUID appointmentId) {
        UUID doctorUserId = currentDoctorUserId();
        AppointmentEntity appointment = appointmentRepository.findByTenantIdAndId(tenantId, appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!doctorUserId.equals(appointment.getDoctorUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment is not assigned to this doctor");
        }
        boolean existingConsultation = consultationRepository.findByTenantIdAndAppointmentId(tenantId, appointmentId).isPresent();
        if (appointment.getStatus() != AppointmentStatus.WAITING
                && !(appointment.getStatus() == AppointmentStatus.IN_CONSULTATION && existingConsultation)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment must be waiting or already in consultation");
        }
    }

    public void requireDoctorCanCompleteConsultation(UUID tenantId, UUID consultationId) {
        if (!isDoctor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned doctor can complete consultations");
        }
        requireConsultationAccess(tenantId, consultationId);
    }

    public List<ConsultationStatus> activeConsultationStatuses() {
        return List.of(ConsultationStatus.DRAFT);
    }

    private String normalizeRole(String role) {
        return role == null ? null : role.trim().toUpperCase(Locale.ROOT);
    }
}
