package com.deepthoughtnet.clinic.api.patientportal;

import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentBookingRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalAppointmentConfirmationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillLineResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillReceiptSummaryResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalBillResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDashboardResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalDoctorSlotResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalMeResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalProfileUpdateRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionMedicineResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionTestResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRescheduleCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillItemType;
import com.deepthoughtnet.clinic.billing.service.model.BillLineRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.BillPdf;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptPdf;
import com.deepthoughtnet.clinic.billing.service.model.ReceiptRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;

@Service
@Transactional(readOnly = true)
public class PatientPortalService {
    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final DoctorProfileService doctorProfileService;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final BillingService billingService;

    public PatientPortalService(
            AppUserRepository appUserRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorProfileService doctorProfileService,
            PatientService patientService,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            BillingService billingService
    ) {
        this.appUserRepository = appUserRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.doctorProfileService = doctorProfileService;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
        this.billingService = billingService;
    }

    public PatientPortalDashboardResponse dashboard() {
        PatientAccess access = requireCurrentPatientAccess();
        String clinicName = clinicName(access.tenantId());
        List<PatientPortalAppointmentResponse> appointments = appointmentResponses(access, clinicName);
        List<PatientPortalPrescriptionResponse> prescriptions = prescriptionResponses(access, clinicName);
        List<PatientPortalBillResponse> bills = billResponses(access);
        return new PatientPortalDashboardResponse(
                fullName(access.patient().getFirstName(), access.patient().getLastName()),
                access.patient().getPatientNumber(),
                clinicName,
                nextUpcomingAppointment(appointments),
                prescriptions.isEmpty() ? null : prescriptions.getFirst(),
                bills.stream()
                        .map(PatientPortalBillResponse::dueAmount)
                        .filter(amount -> amount != null)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                bills.isEmpty() ? null : bills.getFirst()
        );
    }

    public PatientPortalMeResponse me() {
        PatientAccess access = requireCurrentPatientAccess();
        return toMeResponse(access.tenantId(), access.patient());
    }

    @Transactional
    public PatientPortalMeResponse updateProfile(PatientPortalProfileUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Profile request is required");
        }
        PatientAccess access = requireCurrentPatientAccess();
        PatientEntity patient = access.patient();
        var updated = patientService.update(
                access.tenantId(),
                patient.getId(),
                new PatientUpsertCommand(
                        request.firstName(),
                        request.lastName(),
                        request.gender(),
                        request.dateOfBirth() != null ? request.dateOfBirth() : patient.getDateOfBirth(),
                        request.ageYears() != null ? request.ageYears() : patient.getAgeYears(),
                        patient.getMobile(),
                        request.email(),
                        request.addressLine1(),
                        request.addressLine2(),
                        request.city(),
                        request.state(),
                        request.country(),
                        request.postalCode(),
                        request.emergencyContactName(),
                        request.emergencyContactMobile(),
                        patient.getBloodGroup(),
                        patient.getAllergies(),
                        patient.getExistingConditions(),
                        patient.getLongTermMedications(),
                        patient.getSurgicalHistory(),
                        patient.getNotes(),
                        patient.isActive()
                ),
                requireActorAppUserId()
        );
        return new PatientPortalMeResponse(
                updated.patientNumber(),
                fullName(updated.firstName(), updated.lastName()),
                clinicName(access.tenantId()),
                updated.gender() == null ? null : updated.gender().name(),
                updated.dateOfBirth(),
                updated.ageYears(),
                updated.mobile(),
                updated.email(),
                updated.addressLine1(),
                updated.addressLine2(),
                updated.city(),
                updated.state(),
                updated.country(),
                updated.postalCode(),
                updated.emergencyContactName(),
                updated.emergencyContactMobile(),
                updated.bloodGroup(),
                updated.allergies(),
                updated.existingConditions(),
                updated.longTermMedications()
        );
    }

    private PatientPortalMeResponse toMeResponse(UUID tenantId, PatientEntity patient) {
        return new PatientPortalMeResponse(
                patient.getPatientNumber(),
                fullName(patient.getFirstName(), patient.getLastName()),
                clinicName(tenantId),
                patient.getGender() == null ? null : patient.getGender().name(),
                patient.getDateOfBirth(),
                patient.getAgeYears(),
                patient.getMobile(),
                patient.getEmail(),
                patient.getAddressLine1(),
                patient.getAddressLine2(),
                patient.getCity(),
                patient.getState(),
                patient.getCountry(),
                patient.getPostalCode(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactMobile(),
                patient.getBloodGroup(),
                patient.getAllergies(),
                patient.getExistingConditions(),
                patient.getLongTermMedications()
        );
    }

    public List<PatientPortalAppointmentResponse> appointments() {
        PatientAccess access = requireCurrentPatientAccess();
        return appointmentResponses(access, clinicName(access.tenantId()));
    }

    public List<PatientPortalCareAiAppointmentOption> careAiUpcomingAppointments() {
        PatientAccess access = requireCurrentPatientAccess();
        String clinicName = clinicName(access.tenantId());
        return appointmentService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .filter(this::isUpcomingAppointmentRecord)
                .sorted(Comparator
                        .comparing(AppointmentRecord::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AppointmentRecord::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(record -> new PatientPortalCareAiAppointmentOption(
                        record.id(),
                        record.doctorUserId(),
                        record.doctorName(),
                        clinicName,
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.status() == null ? null : record.status().name(),
                        summarize(record.reason())
                ))
                .toList();
    }

    public UUID currentPatientId() {
        return requireCurrentPatientAccess().patient().getId();
    }

    public List<PatientPortalDoctorResponse> doctors() {
        PatientAccess access = requireCurrentPatientAccess();
        return activeDoctorSnapshots(access.tenantId()).stream()
                .map(snapshot -> new PatientPortalDoctorResponse(
                        snapshot.user().appUserId().toString(),
                        snapshot.user().displayName(),
                        summarize(snapshot.profile().specialization()),
                        summarize(snapshot.profile().qualification()),
                        summarize(snapshot.profile().consultationRoom()),
                        snapshot.profile().yearsOfExperience()
                ))
                .toList();
    }

    public List<PatientPortalDoctorSlotResponse> doctorSlots(String publicDoctorId, LocalDate date) {
        PatientAccess access = requireCurrentPatientAccess();
        requireAppointmentDate(date);
        DoctorSnapshot doctor = requireActiveDoctor(access.tenantId(), publicDoctorId);
        return appointmentService.listSlots(access.tenantId(), doctor.user().appUserId(), date).stream()
                .map(this::toDoctorSlotResponse)
                .toList();
    }

    @Transactional
    public PatientPortalAppointmentConfirmationResponse bookAppointment(PatientPortalAppointmentBookingRequest request) {
        PatientAccess access = requireCurrentPatientAccess();
        if (request == null) {
            throw new IllegalArgumentException("Appointment request is required");
        }
        requireAppointmentDate(request.appointmentDate());
        if (request.appointmentTime() == null) {
            throw new IllegalArgumentException("Appointment time is required");
        }

        DoctorSnapshot doctor = requireActiveDoctor(access.tenantId(), request.publicDoctorId());
        List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(access.tenantId(), doctor.user().appUserId(), request.appointmentDate());
        boolean slotSelectable = slots.stream()
                .anyMatch(slot -> request.appointmentTime().equals(slot.slotTime()) && slot.selectable());
        if (!slotSelectable) {
            throw new IllegalArgumentException("Selected slot is no longer available.");
        }

        var booked = appointmentService.createScheduled(
                access.tenantId(),
                new AppointmentUpsertCommand(
                        access.patient().getId(),
                        doctor.user().appUserId(),
                        request.appointmentDate(),
                        request.appointmentTime(),
                        normalizeBookingReason(request.reason()),
                        AppointmentType.SCHEDULED,
                        null,
                        AppointmentPriority.NORMAL,
                        false
                ),
                requireActorAppUserId(),
                false
        );
        return new PatientPortalAppointmentConfirmationResponse(
                booked.appointmentDate(),
                booked.appointmentTime(),
                booked.doctorName(),
                clinicName(access.tenantId()),
                appointmentSource(booked),
                booked.status() == null ? null : booked.status().name(),
                summarize(booked.reason()),
                "Appointment booked successfully."
        );
    }

    @Transactional
    public PatientPortalAppointmentConfirmationResponse rescheduleAppointment(
            UUID appointmentId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String reason
    ) {
        PatientAccess access = requireCurrentPatientAccess();
        requireAppointmentDate(appointmentDate);
        if (appointmentTime == null) {
            throw new IllegalArgumentException("Appointment time is required");
        }
        AppointmentRecord current = requireAccessibleUpcomingAppointment(access, appointmentId);
        var updated = appointmentService.reschedule(
                access.tenantId(),
                current.id(),
                new AppointmentRescheduleCommand(
                        current.doctorUserId(),
                        appointmentDate,
                        appointmentTime,
                        normalizeBookingReason(reason == null ? current.reason() : reason)
                ),
                requireActorAppUserId(),
                false
        );
        return new PatientPortalAppointmentConfirmationResponse(
                updated.appointmentDate(),
                updated.appointmentTime(),
                updated.doctorName(),
                clinicName(access.tenantId()),
                appointmentSource(updated),
                updated.status() == null ? null : updated.status().name(),
                summarize(updated.reason()),
                "Appointment rescheduled successfully."
        );
    }

    @Transactional
    public PatientPortalAppointmentConfirmationResponse cancelAppointment(UUID appointmentId, String reason) {
        PatientAccess access = requireCurrentPatientAccess();
        AppointmentRecord current = requireAccessibleUpcomingAppointment(access, appointmentId);
        String cancelReason = StringUtils.hasText(reason) ? reason.trim() : "Cancelled by patient";
        var updated = appointmentService.updateStatus(
                access.tenantId(),
                current.id(),
                new AppointmentStatusUpdateCommand(
                        AppointmentStatus.CANCELLED,
                        cancelReason,
                        null,
                        null,
                        null
                ),
                requireActorAppUserId()
        );
        return new PatientPortalAppointmentConfirmationResponse(
                updated.appointmentDate(),
                updated.appointmentTime(),
                updated.doctorName(),
                clinicName(access.tenantId()),
                appointmentSource(updated),
                updated.status() == null ? null : updated.status().name(),
                summarize(updated.reason()),
                "Appointment cancelled successfully."
        );
    }

    public List<PatientPortalPrescriptionResponse> prescriptions() {
        PatientAccess access = requireCurrentPatientAccess();
        return prescriptionResponses(access, clinicName(access.tenantId()));
    }

    public List<PatientPortalBillResponse> bills() {
        PatientAccess access = requireCurrentPatientAccess();
        return billResponses(access);
    }

    public PrescriptionPdf prescriptionPdf(String prescriptionNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        PrescriptionRecord record = findAccessiblePrescription(access, prescriptionNumber);
        return prescriptionService.generatePdf(access.tenantId(), record.id(), requireActorAppUserId());
    }

    public BillPdf billPdf(String billNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        BillRecord record = findAccessibleBill(access, billNumber);
        return billingService.generateBillPdf(access.tenantId(), record.id(), requireActorAppUserId());
    }

    public ReceiptPdf latestReceiptPdf(String billNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        BillRecord bill = findAccessibleBill(access, billNumber);
        ReceiptRecord receipt = billingService.listReceipts(access.tenantId(), bill.id()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        return billingService.generateReceiptPdf(access.tenantId(), receipt.id(), requireActorAppUserId());
    }

    private PatientAccess requireCurrentPatientAccess() {
        var context = RequestContextHolder.require();
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID appUserId = context.appUserId();
        if (appUserId == null) {
            throw new UnauthorizedException("Authenticated patient context is missing");
        }

        AppUserEntity appUser = appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated patient account was not found"));
        UUID patientId = appUser.getPatientId();
        if (patientId == null) {
            throw new ForbiddenException("Patient portal access is not configured for this account");
        }

        PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                .filter(PatientEntity::isActive)
                .orElseThrow(() -> new ForbiddenException("Patient portal access is not configured for this tenant"));
        return new PatientAccess(tenantId, patient);
    }

    private PatientPortalPrescriptionResponse toPrescriptionResponse(PrescriptionRecord record) {
        return new PatientPortalPrescriptionResponse(
                record.prescriptionNumber(),
                prescriptionDate(record),
                record.doctorName(),
                null,
                summarize(record.diagnosisSnapshot()),
                summarize(record.advice()),
                record.followUpDate(),
                record.status() == null ? null : record.status().name(),
                record.id() != null,
                record.medicines().stream().map(this::toPrescriptionMedicineResponse).toList(),
                record.recommendedTests().stream().map(this::toPrescriptionTestResponse).toList()
        );
    }

    private PatientPortalPrescriptionMedicineResponse toPrescriptionMedicineResponse(PrescriptionMedicineRecord record) {
        return new PatientPortalPrescriptionMedicineResponse(
                record.medicineName(),
                medicineInstructions(record)
        );
    }

    private PatientPortalPrescriptionTestResponse toPrescriptionTestResponse(PrescriptionTestRecord record) {
        return new PatientPortalPrescriptionTestResponse(
                record.testName(),
                summarize(record.instructions())
        );
    }

    private PatientPortalBillResponse toBillResponse(BillRecord record) {
        return new PatientPortalBillResponse(
                record.billNumber(),
                classifyBillType(record),
                record.billDate(),
                record.status() == null ? null : record.status().name(),
                record.totalAmount(),
                record.paidAmount(),
                record.dueAmount(),
                latestReceipt(record),
                record.lines().stream().map(this::toBillLineResponse).toList()
        );
    }

    private PatientPortalBillLineResponse toBillLineResponse(BillLineRecord record) {
        return new PatientPortalBillLineResponse(
                record.itemName(),
                record.quantity(),
                record.totalPrice(),
                billLineSummary(record)
        );
    }

    private PatientPortalDoctorSlotResponse toDoctorSlotResponse(DoctorAvailabilitySlotRecord slot) {
        return new PatientPortalDoctorSlotResponse(
                slot.appointmentDate(),
                slot.slotTime(),
                slot.slotEndTime(),
                slot.status() == null ? null : slot.status().name(),
                slot.selectable()
        );
    }

    private List<PatientPortalAppointmentResponse> appointmentResponses(PatientAccess access, String clinicName) {
        return appointmentService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(record -> new PatientPortalAppointmentResponse(
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.doctorName(),
                        clinicName,
                        appointmentSource(record),
                        summarize(record.reason()),
                        record.status() == null ? null : record.status().name()
                ))
                .sorted(Comparator
                        .comparing(PatientPortalAppointmentResponse::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientPortalAppointmentResponse::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<PatientPortalPrescriptionResponse> prescriptionResponses(PatientAccess access, String clinicName) {
        return prescriptionService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(record -> withClinicName(toPrescriptionResponse(record), clinicName))
                .toList();
    }

    private List<PatientPortalBillResponse> billResponses(PatientAccess access) {
        return billingService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(this::toBillResponse)
                .toList();
    }

    private PatientPortalPrescriptionResponse withClinicName(
            PatientPortalPrescriptionResponse response,
            String clinicName
    ) {
        return new PatientPortalPrescriptionResponse(
                response.prescriptionNumber(),
                response.prescriptionDate(),
                response.doctorName(),
                clinicName,
                response.diagnosisSummary(),
                response.adviceSummary(),
                response.followUpDate(),
                response.status(),
                response.pdfAvailable(),
                response.medicines(),
                response.recommendedTests()
        );
    }

    private PatientPortalAppointmentResponse nextUpcomingAppointment(List<PatientPortalAppointmentResponse> appointments) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        return appointments.stream()
                .filter(this::isUpcomingStatus)
                .filter(appointment -> appointment.appointmentDate() != null)
                .filter(appointment -> appointment.appointmentDate().isAfter(today)
                        || (appointment.appointmentDate().isEqual(today)
                        && (appointment.appointmentTime() == null || !appointment.appointmentTime().isBefore(now))))
                .findFirst()
                .orElse(appointments.isEmpty() ? null : appointments.getFirst());
    }

    private boolean isUpcomingStatus(PatientPortalAppointmentResponse appointment) {
        String status = appointment.status();
        return status == null
                || (!"CANCELLED".equalsIgnoreCase(status)
                && !"NO_SHOW".equalsIgnoreCase(status)
                && !"COMPLETED".equalsIgnoreCase(status));
    }

    private boolean isUpcomingAppointmentRecord(AppointmentRecord appointment) {
        if (appointment == null || appointment.appointmentDate() == null) {
            return false;
        }
        if (appointment.status() == AppointmentStatus.CANCELLED
                || appointment.status() == AppointmentStatus.NO_SHOW
                || appointment.status() == AppointmentStatus.COMPLETED) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        return appointment.appointmentDate().isAfter(today)
                || (appointment.appointmentDate().isEqual(today)
                && (appointment.appointmentTime() == null || !appointment.appointmentTime().isBefore(now)));
    }

    private AppointmentRecord requireAccessibleUpcomingAppointment(PatientAccess access, UUID appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("Appointment is required");
        }
        return appointmentService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .filter(record -> appointmentId.equals(record.id()))
                .filter(this::isUpcomingAppointmentRecord)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Upcoming appointment not found"));
    }

    private PrescriptionRecord findAccessiblePrescription(PatientAccess access, String prescriptionNumber) {
        return prescriptionService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .filter(record -> record.prescriptionNumber() != null && record.prescriptionNumber().equalsIgnoreCase(prescriptionNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
    }

    private BillRecord findAccessibleBill(PatientAccess access, String billNumber) {
        return billingService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .filter(record -> record.billNumber() != null && record.billNumber().equalsIgnoreCase(billNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
    }

    private PatientPortalBillReceiptSummaryResponse latestReceipt(BillRecord record) {
        if (record.id() == null) {
            return null;
        }
        return billingService.listReceipts(record.tenantId(), record.id()).stream()
                .findFirst()
                .map(receipt -> new PatientPortalBillReceiptSummaryResponse(
                        receipt.receiptNumber(),
                        receipt.receiptDate(),
                        receipt.amount()
                ))
                .orElse(null);
    }

    private String clinicName(UUID tenantId) {
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        if (clinic == null) {
            return "Clinic";
        }
        if (StringUtils.hasText(clinic.displayName())) {
            return clinic.displayName().trim();
        }
        if (StringUtils.hasText(clinic.clinicName())) {
            return clinic.clinicName().trim();
        }
        return "Clinic";
    }

    private String appointmentSource(com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord record) {
        if (record.type() == null) {
            return null;
        }
        return switch (record.type()) {
            case WALK_IN -> "Walk-in";
            case SCHEDULED -> "Scheduled";
            case FOLLOW_UP -> "Follow-up";
            case VACCINATION -> "Vaccination";
        };
    }

    private LocalDate prescriptionDate(PrescriptionRecord record) {
        OffsetDateTime finalizedAt = record.finalizedAt();
        if (finalizedAt != null) {
            return finalizedAt.toLocalDate();
        }
        return record.createdAt() == null ? null : record.createdAt().toLocalDate();
    }

    private String medicineInstructions(PrescriptionMedicineRecord record) {
        String summary = String.join(" · ", List.of(
                nullToBlank(record.dosage()),
                nullToBlank(record.frequency()),
                nullToBlank(record.duration()),
                nullToBlank(record.instructions())
        ).stream().filter(StringUtils::hasText).toList());
        return StringUtils.hasText(summary) ? summary : "Instructions will appear once available.";
    }

    private String billLineSummary(BillLineRecord record) {
        String quantity = record.quantity() == null ? null : "Qty " + record.quantity();
        String amount = record.totalPrice() == null ? null : record.totalPrice().toPlainString();
        String itemType = record.itemType() == null ? null : record.itemType().name().replace('_', ' ');
        String summary = String.join(" · ", List.of(itemType, quantity, amount).stream().filter(StringUtils::hasText).toList());
        return StringUtils.hasText(summary) ? summary : "Line summary";
    }

    private String classifyBillType(BillRecord record) {
        if (record == null || record.lines() == null || record.lines().isEmpty()) {
            return record != null && record.consultationId() != null ? "Consultation" : "Bill";
        }
        boolean hasMedicine = record.lines().stream().anyMatch(line -> line.itemType() == BillItemType.MEDICINE);
        boolean hasConsultation = record.lines().stream().anyMatch(line -> line.itemType() == BillItemType.CONSULTATION);
        if (hasMedicine && record.lines().stream().allMatch(line -> line.itemType() == BillItemType.MEDICINE)) {
            return "Medicine";
        }
        if (hasMedicine) {
            return "Pharmacy";
        }
        if (hasConsultation) {
            return "Consultation";
        }
        return "Bill";
    }

    private List<DoctorSnapshot> activeDoctorSnapshots(UUID tenantId) {
        return tenantUserManagementService.list(tenantId).stream()
                .filter(this::isActiveDoctorUser)
                .map(user -> doctorProfileService.findByDoctorUserId(tenantId, user.appUserId())
                        .filter(DoctorProfileRecord::active)
                        .map(profile -> new DoctorSnapshot(user, profile)))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(snapshot -> snapshot.user().displayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private DoctorSnapshot requireActiveDoctor(UUID tenantId, String publicDoctorId) {
        UUID doctorUserId = parseDoctorUserId(publicDoctorId);
        return activeDoctorSnapshots(tenantId).stream()
                .filter(snapshot -> doctorUserId.equals(snapshot.user().appUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
    }

    private UUID parseDoctorUserId(String publicDoctorId) {
        if (!StringUtils.hasText(publicDoctorId)) {
            throw new IllegalArgumentException("Doctor is required");
        }
        try {
            return UUID.fromString(publicDoctorId.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Doctor not found");
        }
    }

    private void requireAppointmentDate(LocalDate appointmentDate) {
        if (appointmentDate == null) {
            throw new IllegalArgumentException("Appointment date is required");
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Please choose a current or future appointment date.");
        }
    }

    private String normalizeBookingReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("Reason must be 300 characters or fewer.");
        }
        return trimmed;
    }

    private boolean isActiveDoctorUser(TenantUserRecord user) {
        return user != null
                && user.appUserId() != null
                && "DOCTOR".equalsIgnoreCase(user.membershipRole())
                && "ACTIVE".equalsIgnoreCase(user.userStatus())
                && "ACTIVE".equalsIgnoreCase(user.membershipStatus());
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 220 ? trimmed.substring(0, 217) + "..." : trimmed;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private UUID requireActorAppUserId() {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        if (actorAppUserId == null) {
            throw new UnauthorizedException("Authenticated patient account was not found");
        }
        return actorAppUserId;
    }

    private String fullName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private record PatientAccess(UUID tenantId, PatientEntity patient) {
    }

    private record DoctorSnapshot(TenantUserRecord user, DoctorProfileRecord profile) {
    }
}
