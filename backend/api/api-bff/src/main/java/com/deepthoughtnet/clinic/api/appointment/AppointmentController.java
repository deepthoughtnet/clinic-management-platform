package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentPriorityRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentResponse;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentStatusRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.QueueReorderRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.WalkInAppointmentRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationResponse;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.WalkInAppointmentCommand;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public AppointmentController(
            AppointmentService appointmentService,
            ConsultationService consultationService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')")
    public List<AppointmentResponse> search(
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) LocalDate appointmentDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID effectiveDoctorUserId = doctorAssignmentSecurityService.effectiveDoctorUserId(doctorUserId);
        List<AppointmentRecord> appointments = appointmentService.search(tenantId, new AppointmentSearchCriteria(
                effectiveDoctorUserId,
                patientId,
                appointmentDate,
                parseStatus(status),
                parseType(type)
        ));
        return toResponsesWithConsultations(tenantId, appointments, effectiveDoctorUserId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public AppointmentResponse create(@Valid @RequestBody AppointmentRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        boolean allowOverbooking = doctorAssignmentSecurityService.isClinicAdmin();
        return toResponse(appointmentService.createScheduled(tenantId, new AppointmentUpsertCommand(
                request.patientId(),
                request.doctorUserId(),
                request.appointmentDate(),
                request.appointmentTime(),
                request.reason(),
                request.type(),
                request.status(),
                request.priority()
        ), actorAppUserId, allowOverbooking));
    }

    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public AppointmentResponse createWalkIn(@Valid @RequestBody WalkInAppointmentRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        boolean allowOverbooking = doctorAssignmentSecurityService.isClinicAdmin();
        return toResponse(appointmentService.createWalkIn(tenantId, new WalkInAppointmentCommand(
                request.patientId(),
                request.doctorUserId(),
                request.appointmentDate(),
                request.reason(),
                request.priority()
        ), actorAppUserId, allowOverbooking));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')")
    public AppointmentResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireAppointmentAccess(tenantId, id);
        return toResponse(appointmentService.findById(tenantId, id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public AppointmentResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody AppointmentStatusRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireAppointmentAccess(tenantId, id);
        doctorAssignmentSecurityService.requireNonDoctorQueueStatusUpdate();
        requireReceptionQueueTarget(request.status());
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.updateStatus(tenantId, id, new AppointmentStatusUpdateCommand(request.status(), request.comment()), actorAppUserId));
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public AppointmentResponse updatePriority(@PathVariable UUID id, @Valid @RequestBody AppointmentPriorityRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireNonDoctorQueueStatusUpdate();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.updatePriority(tenantId, id, request.priority(), actorAppUserId));
    }

    @PostMapping("/{appointmentId}/start-consultation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('consultation.create') and @permissionChecker.hasPermission('appointment.manage') and @permissionChecker.hasRole('DOCTOR')")
    public ConsultationResponse startConsultation(@PathVariable UUID appointmentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationStartAccess(tenantId, appointmentId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toConsultationResponse(consultationService.startFromAppointment(tenantId, appointmentId, actorAppUserId));
    }

    @GetMapping("/today")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')")
    public List<AppointmentResponse> today() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID effectiveDoctorUserId = doctorAssignmentSecurityService.effectiveDoctorUserId(null);
        if (effectiveDoctorUserId != null) {
            List<AppointmentRecord> appointments = appointmentService.search(tenantId, new AppointmentSearchCriteria(
                    effectiveDoctorUserId,
                    null,
                    LocalDate.now(),
                    null,
                    null
            ));
            return toResponsesWithConsultations(tenantId, appointments, effectiveDoctorUserId);
        }
        return toResponsesWithConsultations(tenantId, appointmentService.listToday(tenantId), null);
    }

    @PostMapping("/queue/reorder")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public List<AppointmentResponse> reorderQueue(@Valid @RequestBody QueueReorderRequest request, @RequestParam UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireQueueReorderAccess(doctorUserId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return appointmentService.reorderQueueToday(tenantId, doctorUserId, request.orderedAppointmentIds(), actorAppUserId)
                .stream()
                .map(record -> toResponse(record, null))
                .toList();
    }

    private List<AppointmentResponse> toResponsesWithConsultations(UUID tenantId, List<AppointmentRecord> appointments, UUID doctorUserId) {
        Map<UUID, ConsultationRecord> consultationByAppointmentId = consultationsByAppointmentId(tenantId, doctorUserId);
        return appointments.stream()
                .map(record -> toResponse(record, consultationByAppointmentId.get(record.id())))
                .toList();
    }

    private Map<UUID, ConsultationRecord> consultationsByAppointmentId(UUID tenantId, UUID doctorUserId) {
        List<ConsultationRecord> consultations = doctorUserId == null
                ? consultationService.list(tenantId)
                : consultationService.listByDoctor(tenantId, doctorUserId);
        return consultations.stream()
                .filter(record -> record.appointmentId() != null)
                .collect(Collectors.toMap(
                        ConsultationRecord::appointmentId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private AppointmentResponse toResponse(AppointmentRecord record, ConsultationRecord consultation) {
        return new AppointmentResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.patientMobile(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                consultation == null || consultation.id() == null ? null : consultation.id().toString(),
                record.appointmentDate(),
                record.appointmentTime(),
                record.tokenNumber(),
                record.reason(),
                record.type(),
                record.priority(),
                record.status(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private AppointmentResponse toResponse(AppointmentRecord record) {
        return toResponse(record, null);
    }

    private ConsultationResponse toConsultationResponse(com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord record) {
        return new ConsultationResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.chiefComplaints(),
                record.symptoms(),
                record.diagnosis(),
                record.clinicalNotes(),
                record.advice(),
                record.followUpDate(),
                record.status(),
                record.bloodPressureSystolic(),
                record.bloodPressureDiastolic(),
                record.pulseRate(),
                record.temperature(),
                record.temperatureUnit(),
                record.weightKg(),
                record.heightCm(),
                record.spo2(),
                record.respiratoryRate(),
                com.deepthoughtnet.clinic.consultation.service.ConsultationVitalsCalculator.calculateBmi(record.weightKg(), record.heightCm()),
                com.deepthoughtnet.clinic.consultation.service.ConsultationVitalsCalculator.bmiCategory(record.weightKg(), record.heightCm()),
                record.completedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private void requireReceptionQueueTarget(AppointmentStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (status != AppointmentStatus.WAITING && status != AppointmentStatus.CANCELLED && status != AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException("Queue desk can only check in, cancel, or mark no-show");
        }
    }

    private AppointmentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AppointmentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid appointment status");
        }
    }

    private com.deepthoughtnet.clinic.appointment.service.model.AppointmentType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return com.deepthoughtnet.clinic.appointment.service.model.AppointmentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid appointment type");
        }
    }
}
