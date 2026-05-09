package com.deepthoughtnet.clinic.api.patient;

import com.deepthoughtnet.clinic.api.patient.dto.AppointmentSummaryResponse;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationResponse;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.patient.dto.PatientDetailResponse;
import com.deepthoughtnet.clinic.api.patient.dto.PatientRequest;
import com.deepthoughtnet.clinic.api.patient.dto.PatientResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public PatientController(
            PatientService patientService,
            AppointmentService appointmentService,
            ConsultationService consultationService,
            PrescriptionService prescriptionService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.prescriptionService = prescriptionService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<PatientResponse> search(
            @RequestParam(required = false) String patientNumber,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        List<UUID> visiblePatientIds = doctorAssignmentSecurityService.visiblePatientIds(tenantId);
        return patientService.search(tenantId, new PatientSearchCriteria(patientNumber, mobile, name, active))
                .stream()
                .filter(patient -> !doctorAssignmentSecurityService.isDoctor() || visiblePatientIds.contains(patient.id()))
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('patient.create')")
    public PatientResponse create(@Valid @RequestBody PatientRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(patientService.create(tenantId, toCommand(request), actorAppUserId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public PatientDetailResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, id);
        PatientRecord patient = patientService.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        List<AppointmentRecord> appointments = appointmentService.listByPatient(tenantId, id);
        List<ConsultationRecord> consultations = consultationService.listByPatient(tenantId, id);
        return new PatientDetailResponse(
                toResponse(patient),
                appointments.stream()
                        .filter(this::isUpcoming)
                        .map(this::toSummaryResponse)
                        .toList(),
                appointments.stream()
                        .filter(appointment -> !isUpcoming(appointment))
                        .limit(10)
                        .map(this::toSummaryResponse)
                        .toList(),
                consultations.stream()
                        .limit(10)
                        .map(this::toConsultationResponse)
                        .toList()
        );
    }

    @GetMapping("/{patientId}/consultations")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<ConsultationResponse> listConsultations(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
        return consultationService.listByPatient(tenantId, patientId).stream().map(this::toConsultationResponse).toList();
    }

    @GetMapping("/{patientId}/prescriptions")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionResponse> listPrescriptions(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
        return prescriptionService.listByPatient(tenantId, patientId).stream().map(this::toPrescriptionResponse).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('patient.update')")
    public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(patientService.update(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('patient.update')")
    public PatientResponse deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(patientService.deactivate(tenantId, id, actorAppUserId));
    }

    private boolean isUpcoming(AppointmentRecord appointment) {
        LocalDate today = LocalDate.now();
        return appointment.appointmentDate() != null
                && (appointment.appointmentDate().isAfter(today)
                || (appointment.appointmentDate().isEqual(today)
                && appointment.status() != AppointmentStatus.CANCELLED
                && appointment.status() != AppointmentStatus.NO_SHOW
                && appointment.status() != AppointmentStatus.COMPLETED));
    }

    private PatientUpsertCommand toCommand(PatientRequest request) {
        return new PatientUpsertCommand(
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.dateOfBirth(),
                request.ageYears(),
                request.mobile(),
                request.email(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.state(),
                request.country(),
                request.postalCode(),
                request.emergencyContactName(),
                request.emergencyContactMobile(),
                request.bloodGroup(),
                request.allergies(),
                request.existingConditions(),
                request.longTermMedications(),
                request.surgicalHistory(),
                request.notes(),
                request.active()
        );
    }

    private PatientResponse toResponse(PatientRecord record) {
        return new PatientResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientNumber(),
                record.firstName(),
                record.lastName(),
                record.gender(),
                record.dateOfBirth(),
                record.ageYears(),
                record.mobile(),
                record.email(),
                record.addressLine1(),
                record.addressLine2(),
                record.city(),
                record.state(),
                record.country(),
                record.postalCode(),
                record.emergencyContactName(),
                record.emergencyContactMobile(),
                record.bloodGroup(),
                record.allergies(),
                record.existingConditions(),
                record.longTermMedications(),
                record.surgicalHistory(),
                record.notes(),
                record.active(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private AppointmentSummaryResponse toSummaryResponse(AppointmentRecord record) {
        return new AppointmentSummaryResponse(
                record.id() == null ? null : record.id().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
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

    private ConsultationResponse toConsultationResponse(ConsultationRecord record) {
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

    private com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionResponse toPrescriptionResponse(PrescriptionRecord record) {
        return new com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.consultationId() == null ? null : record.consultationId().toString(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.prescriptionNumber(),
                record.versionNumber(),
                record.parentPrescriptionId() == null ? null : record.parentPrescriptionId().toString(),
                record.correctionReason(),
                record.flowType(),
                record.correctedAt(),
                record.supersededByPrescriptionId() == null ? null : record.supersededByPrescriptionId().toString(),
                record.supersededAt(),
                record.diagnosisSnapshot(),
                record.advice(),
                record.followUpDate(),
                record.status(),
                record.finalizedAt(),
                record.finalizedByDoctorUserId() == null ? null : record.finalizedByDoctorUserId().toString(),
                record.printedAt(),
                record.sentAt(),
                record.createdAt(),
                record.updatedAt(),
                record.medicines(),
                record.recommendedTests()
        );
    }
}
