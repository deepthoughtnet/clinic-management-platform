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
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalNotificationResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalLabLatestResultResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalLabOrderResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalLabResultResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalMeResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalProfileUpdateRequest;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionMedicineResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionResponse;
import com.deepthoughtnet.clinic.api.patientportal.dto.PatientPortalPrescriptionTestResponse;
import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.api.appointment.AppointmentTimingRules;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultRecord;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
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
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiAppointmentOption;

@Service
@Transactional(readOnly = true)
public class PatientPortalService {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalService.class);

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final DoctorProfileService doctorProfileService;
    private final PatientService patientService;
    private final AppUserProvisioner appUserProvisioner;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final BillingService billingService;
    private final LabService labService;
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationActionService notificationActionService;

    @Autowired
    public PatientPortalService(
            AppUserRepository appUserRepository,
            TenantRepository tenantRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorProfileService doctorProfileService,
            PatientService patientService,
            AppUserProvisioner appUserProvisioner,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            BillingService billingService,
            LabService labService,
            NotificationHistoryService notificationHistoryService,
            NotificationActionService notificationActionService
    ) {
        this.appUserRepository = appUserRepository;
        this.tenantRepository = tenantRepository;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.doctorProfileService = doctorProfileService;
        this.patientService = patientService;
        this.appUserProvisioner = appUserProvisioner;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
        this.billingService = billingService;
        this.labService = labService;
        this.notificationHistoryService = notificationHistoryService;
        this.notificationActionService = notificationActionService;
    }

    public PatientPortalService(
            AppUserRepository appUserRepository,
            TenantRepository tenantRepository,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService,
            DoctorProfileService doctorProfileService,
            PatientService patientService,
            AppUserProvisioner appUserProvisioner,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            AppointmentService appointmentService,
            PrescriptionService prescriptionService,
            BillingService billingService
    ) {
        this(
                appUserRepository,
                tenantRepository,
                patientRepository,
                clinicProfileService,
                tenantUserManagementService,
                doctorProfileService,
                patientService,
                appUserProvisioner,
                clinicTimeZoneResolver,
                appointmentService,
                prescriptionService,
                billingService,
                null,
                null,
                null
        );
    }

    public PatientPortalDashboardResponse dashboard() {
        PatientAccess access = requireCurrentPatientAccess();
        String clinicName = clinicName(access.tenantId());
        List<PatientAccess> patientAccesses = resolveAccessiblePatientAccesses(access);
        List<PatientPortalAppointmentResponse> appointments = appointmentResponses(patientAccesses);
        List<PatientPortalPrescriptionResponse> prescriptions = prescriptionResponses(patientAccesses);
        List<PatientPortalBillResponse> bills = billResponses(patientAccesses);
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
        String actorRole = RequestContextHolder.require().tenantRole();
        ZoneId tenantZone = resolveTenantZone(access.tenantId());
        String actorEmail = resolveActorEmail(access.tenantId(), requireActorAppUserId());
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
                requireActorAppUserId(),
                actorRole,
                tenantZone,
                actorEmail
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

    private ZoneId resolveTenantZone(UUID tenantId) {
        return clinicTimeZoneResolver.resolve(tenantId);
    }

    private String resolveActorEmail(UUID tenantId, UUID actorAppUserId) {
        if (tenantId == null || actorAppUserId == null) {
            return null;
        }
        return appUserRepository.findByTenantIdAndId(tenantId, actorAppUserId)
                .map(AppUserEntity::getEmail)
                .orElse(null);
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
        List<PatientAccess> patientAccesses = resolveAccessiblePatientAccesses(access);
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.appointments.list patientPortalSessionId={} resolvedPatientId={} patientMobile={} tenantCount={} appointmentCount={}",
                    RequestContextHolder.require().correlationId(),
                    access.patient().getId(),
                    resolveVerifiedMobile(access),
                    patientAccesses.size(),
                    allAppointments(patientAccesses).size()
            );
        }
        return appointmentResponses(patientAccesses);
    }

    public List<PatientPortalCareAiAppointmentOption> debugAppointments() {
        PatientAccess access = requireCurrentPatientAccess();
        List<PatientAccess> patientAccesses = resolveAccessiblePatientAccesses(access);
        List<PatientPortalCareAiAppointmentOption> appointments = allAppointments(patientAccesses).stream()
                .sorted(Comparator
                        .comparing(AppointmentRecord::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AppointmentRecord::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(record -> new PatientPortalCareAiAppointmentOption(
                        record.id(),
                        record.doctorUserId(),
                        record.doctorName(),
                        record.tenantId(),
                        clinicName(record.tenantId()),
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.status() == null ? null : record.status().name(),
                        summarize(record.reason())
                ))
                .toList();
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.appointments.debug patientPortalSessionId={} resolvedPatientId={} patientMobile={} tenantIds={} appointmentCount={} appointmentIds={} appointmentStatuses={} appointmentDates={}",
                    RequestContextHolder.require().correlationId(),
                    access.patient().getId(),
                    resolveVerifiedMobile(access),
                    patientAccesses.stream().map(PatientAccess::tenantId).toList(),
                    appointments.size(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentId).toList(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::status).toList(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentDate).toList()
            );
        }
        return appointments;
    }

    public List<PatientPortalCareAiAppointmentOption> careAiUpcomingAppointments() {
        PatientAccess access = requireCurrentPatientAccess();
        List<PatientAccess> patientAccesses = resolveAccessiblePatientAccesses(access);
        List<PatientPortalCareAiAppointmentOption> appointments = allAppointments(patientAccesses).stream()
                .filter(this::isUpcomingAppointmentRecord)
                .sorted(Comparator
                        .comparing(AppointmentRecord::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AppointmentRecord::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(record -> new PatientPortalCareAiAppointmentOption(
                        record.id(),
                        record.doctorUserId(),
                        record.doctorName(),
                        record.tenantId(),
                        clinicName(record.tenantId()),
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.status() == null ? null : record.status().name(),
                        summarize(record.reason())
                ))
                .toList();
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.careai.appointments.list patientPortalSessionId={} resolvedPatientId={} patientMobile={} searchedTenantIds={} tenantCount={} appointmentCount={} appointmentIds={} appointmentStatuses={} appointmentDates={}",
                    RequestContextHolder.require().correlationId(),
                    access.patient().getId(),
                    resolveVerifiedMobile(access),
                    patientAccesses.stream().map(PatientAccess::tenantId).toList(),
                    patientAccesses.size(),
                    appointments.size(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentId).toList(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::status).toList(),
                    appointments.stream().map(PatientPortalCareAiAppointmentOption::appointmentDate).toList()
            );
        }
        return appointments;
    }

    public UUID currentPatientId() {
        return requireCurrentPatientAccess().patient().getId();
    }

    public String currentPatientMobile() {
        return requireCurrentPatientAccess().patient().getMobile();
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
        return doctorSlots(publicDoctorId, null, null, null, date);
    }

    public List<PatientPortalDoctorSlotResponse> doctorSlots(String publicDoctorId, String clinicSlug, LocalDate date) {
        return doctorSlots(publicDoctorId, clinicSlug, null, null, date);
    }

    public List<PatientPortalDoctorSlotResponse> doctorSlots(String publicDoctorId, String clinicSlug, String tenantId, String clinicId, LocalDate date) {
        PatientAccess access = requireCurrentPatientAccess();
        requireAppointmentDate(date);
        BookingDoctorAccess bookingDoctor = resolveBookingDoctor(access, publicDoctorId, clinicSlug, tenantId, clinicId);
        ZoneId tenantZone = resolveTenantZone(bookingDoctor.tenantId());
        debugBookingResolution("doctorSlots", publicDoctorId, clinicSlug, tenantId, clinicId, bookingDoctor);
        ZonedDateTime clinicNow = ZonedDateTime.now(tenantZone);
        return appointmentService.listSlots(bookingDoctor.tenantId(), bookingDoctor.doctor().user().appUserId(), date, tenantZone).stream()
                .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), tenantZone, clinicNow))
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

        BookingDoctorAccess bookingDoctor = resolveBookingDoctor(access, request.publicDoctorId(), request.clinicSlug(), request.tenantId(), request.clinicId());
        PatientEntity bookingPatient = resolveBookingPatient(access, bookingDoctor.tenantId());
        UUID actorAppUserId = bookingDoctor.tenantId().equals(access.tenantId())
                ? requireActorAppUserId()
                : ensurePatientPortalActor(bookingDoctor.tenantId(), bookingPatient);
        ZoneId tenantZone = resolveTenantZone(bookingDoctor.tenantId());
        debugBookingResolution("bookAppointment", request.publicDoctorId(), request.clinicSlug(), request.tenantId(), request.clinicId(), bookingDoctor);
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.booking.create patientPortalSessionId={} patientId={} patientMobile={} selectedClinicSlug={} selectedTenantId={} resolvedClinicTenantId={} doctorId={} bookingPatientId={}",
                    RequestContextHolder.require().correlationId(),
                    access.patient().getId(),
                    resolveVerifiedMobile(access),
                    request.clinicSlug(),
                    request.tenantId(),
                    bookingDoctor.tenantId(),
                    request.publicDoctorId(),
                    bookingPatient.getId()
            );
        }
        List<DoctorAvailabilitySlotRecord> slots = appointmentService.listSlots(
                bookingDoctor.tenantId(),
                bookingDoctor.doctor().user().appUserId(),
                request.appointmentDate(),
                tenantZone
        );
        ZonedDateTime clinicNow = ZonedDateTime.now(tenantZone);
        boolean slotSelectable = slots.stream()
                .filter(slot -> AppointmentTimingRules.isSlotBookableForPatient(slot.appointmentDate(), slot.slotTime(), tenantZone, clinicNow))
                .anyMatch(slot -> request.appointmentTime().equals(slot.slotTime()) && slot.selectable());
        if (!slotSelectable) {
            throw new IllegalArgumentException("Selected slot is no longer available.");
        }

        var booked = appointmentService.createScheduled(
                bookingDoctor.tenantId(),
                new AppointmentUpsertCommand(
                        bookingPatient.getId(),
                        bookingDoctor.doctor().user().appUserId(),
                        request.appointmentDate(),
                        request.appointmentTime(),
                        normalizeBookingReason(request.reason()),
                        AppointmentType.SCHEDULED,
                        null,
                        AppointmentPriority.NORMAL,
                        false
                ),
                actorAppUserId,
                false,
                tenantZone
        );
        if (notificationActionService != null) {
            notificationActionService.sendAppointmentBooked(bookingDoctor.tenantId(), booked.id(), actorAppUserId);
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "patient.portal.booking.created patientPortalSessionId={} appointmentId={} appointmentPatientId={} appointmentTenantId={} appointmentStatus={}",
                    RequestContextHolder.require().correlationId(),
                    booked.id(),
                    booked.patientId(),
                    bookingDoctor.tenantId(),
                    booked.status()
            );
        }
        return new PatientPortalAppointmentConfirmationResponse(
                booked.appointmentDate(),
                booked.appointmentTime(),
                booked.doctorName(),
                clinicName(bookingDoctor.tenantId()),
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
        PatientEntity bookingPatient = resolveBookingPatient(access, current.tenantId());
        UUID actorAppUserId = current.tenantId().equals(access.tenantId())
                ? requireActorAppUserId()
                : ensurePatientPortalActor(current.tenantId(), bookingPatient);
        ZoneId tenantZone = resolveTenantZone(current.tenantId());
        var updated = appointmentService.reschedule(
                current.tenantId(),
                current.id(),
                new AppointmentRescheduleCommand(
                        current.doctorUserId(),
                        appointmentDate,
                        appointmentTime,
                        normalizeBookingReason(reason == null ? current.reason() : reason)
                ),
                actorAppUserId,
                false,
                tenantZone
        );
        if (notificationActionService != null) {
            notificationActionService.sendAppointmentRescheduled(current.tenantId(), updated.id(), actorAppUserId);
        }
        return new PatientPortalAppointmentConfirmationResponse(
                updated.appointmentDate(),
                updated.appointmentTime(),
                updated.doctorName(),
                clinicName(current.tenantId()),
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
        PatientEntity bookingPatient = resolveBookingPatient(access, current.tenantId());
        UUID actorAppUserId = current.tenantId().equals(access.tenantId())
                ? requireActorAppUserId()
                : ensurePatientPortalActor(current.tenantId(), bookingPatient);
        var updated = appointmentService.updateStatus(
                current.tenantId(),
                current.id(),
                new AppointmentStatusUpdateCommand(
                        AppointmentStatus.CANCELLED,
                        cancelReason,
                        null,
                        null,
                        null
                ),
                actorAppUserId
        );
        if (notificationActionService != null) {
            notificationActionService.sendAppointmentCancelled(current.tenantId(), current.id(), actorAppUserId);
        }
        return new PatientPortalAppointmentConfirmationResponse(
                updated.appointmentDate(),
                updated.appointmentTime(),
                updated.doctorName(),
                clinicName(current.tenantId()),
                appointmentSource(updated),
                updated.status() == null ? null : updated.status().name(),
                summarize(updated.reason()),
                "Appointment cancelled successfully."
        );
    }

    public List<PatientPortalPrescriptionResponse> prescriptions() {
        PatientAccess access = requireCurrentPatientAccess();
        return prescriptionResponses(resolveAccessiblePatientAccesses(access));
    }

    public List<PatientPortalBillResponse> bills() {
        PatientAccess access = requireCurrentPatientAccess();
        return billResponses(resolveAccessiblePatientAccesses(access));
    }

    public List<PatientPortalNotificationResponse> notifications() {
        if (notificationHistoryService == null) {
            return List.of();
        }
        PatientAccess access = requireCurrentPatientAccess();
        return notificationHistoryService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .map(this::toNotificationResponse)
                .toList();
    }

    public PatientPortalNotificationResponse markNotificationRead(UUID notificationId) {
        if (notificationHistoryService == null) {
            throw new IllegalStateException("Notification history service is not available");
        }
        PatientAccess access = requireCurrentPatientAccess();
        NotificationHistoryRecord record = notificationHistoryService.listByPatient(access.tenantId(), access.patient().getId()).stream()
                .filter(notification -> notificationId != null && notificationId.equals(notification.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        return toNotificationResponse(notificationHistoryService.markRead(record.tenantId(), notificationId));
    }

    public List<PatientPortalLabOrderResponse> labOrders() {
        PatientAccess access = requireCurrentPatientAccess();
        return labOrderResponses(resolveAccessiblePatientAccesses(access), false);
    }

    public List<PatientPortalLabOrderResponse> labReports() {
        PatientAccess access = requireCurrentPatientAccess();
        return labOrderResponses(resolveAccessiblePatientAccesses(access), true);
    }

    public List<PatientPortalLabLatestResultResponse> latestLabResults(String query) {
        PatientAccess access = requireCurrentPatientAccess();
        String term = summarize(query);
        List<LabOrderRecord> records = allLabOrders(resolveAccessiblePatientAccesses(access));
        return records.stream()
                .flatMap(order -> order.results().stream().map(result -> new LatestLabMatch(order, result)))
                .filter(match -> term == null || matchesLabResult(match, term))
                .sorted((a, b) -> compareOffsetDescending(a.order().resultEnteredAt(), b.order().resultEnteredAt()))
                .map(match -> new PatientPortalLabLatestResultResponse(
                        match.order().orderNumber(),
                        match.result().testCode(),
                        match.result().testName(),
                        match.result().componentName(),
                        match.result().resultValue(),
                        match.result().unit(),
                        match.result().referenceRange(),
                        match.order().resultEnteredAt(),
                        match.order().doctorReviewedAt(),
                        match.order().doctorComments()
                ))
                .toList();
    }

    public PrescriptionPdf prescriptionPdf(String prescriptionNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        PrescriptionRecord record = findAccessiblePrescription(resolveAccessiblePatientAccesses(access), prescriptionNumber);
        return prescriptionService.generatePdf(record.tenantId(), record.id(), requireActorAppUserId());
    }

    public com.deepthoughtnet.clinic.api.lab.service.model.LabOrderResultPdf labReportPdf(String orderNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        LabOrderRecord record = findAccessibleLabOrder(resolveAccessiblePatientAccesses(access), orderNumber);
        var pdf = labService.renderReportPdf(record.tenantId(), record.id());
        labService.markDelivered(record.tenantId(), record.id(), requireActorAppUserId());
        return pdf;
    }

    public BillPdf billPdf(String billNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        BillRecord record = findAccessibleBill(resolveAccessiblePatientAccesses(access), billNumber);
        return billingService.generateBillPdf(record.tenantId(), record.id(), requireActorAppUserId());
    }

    public ReceiptPdf latestReceiptPdf(String billNumber) {
        PatientAccess access = requireCurrentPatientAccess();
        BillRecord bill = findAccessibleBill(resolveAccessiblePatientAccesses(access), billNumber);
        ReceiptRecord receipt = billingService.listReceipts(bill.tenantId(), bill.id()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found"));
        return billingService.generateReceiptPdf(bill.tenantId(), receipt.id(), requireActorAppUserId());
    }

    private List<PatientPortalLabOrderResponse> labOrderResponses(List<PatientAccess> patientAccesses, boolean reportsOnly) {
        return allLabOrders(patientAccesses).stream()
                .filter(record -> !reportsOnly || isReportStatus(record.status() == null ? null : record.status().name()))
                .map(this::toLabOrderResponse)
                .toList();
    }

    private List<LabOrderRecord> allLabOrders(List<PatientAccess> patientAccesses) {
        return patientAccesses.stream()
                .flatMap(patientAccess -> labService.listOrders(patientAccess.tenantId(), null, patientAccess.patient().getId(), null, null, null).stream())
                .distinct()
                .toList();
    }

    private LabOrderRecord findAccessibleLabOrder(List<PatientAccess> patientAccesses, String orderNumber) {
        if (!StringUtils.hasText(orderNumber)) {
            throw new IllegalArgumentException("orderNumber is required");
        }
        return allLabOrders(patientAccesses).stream()
                .filter(record -> record.orderNumber() != null && record.orderNumber().equalsIgnoreCase(orderNumber.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Lab order not found"));
    }

    private boolean isReportStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return "REPORT_READY".equalsIgnoreCase(status)
                || "REPORT_GENERATED".equalsIgnoreCase(status)
                || "DOCTOR_REVIEWED".equalsIgnoreCase(status)
                || "DELIVERED".equalsIgnoreCase(status);
    }

    private PatientPortalLabOrderResponse toLabOrderResponse(LabOrderRecord record) {
        return new PatientPortalLabOrderResponse(
                record.orderNumber(),
                record.doctorName(),
                record.status() == null ? null : record.status().name(),
                record.orderedAt(),
                record.sampleCollectedAt(),
                record.resultEnteredAt(),
                record.reportGeneratedAt(),
                record.doctorReviewedAt(),
                record.doctorComments(),
                record.results().stream().map(this::toLabResultResponse).toList()
        );
    }

    private PatientPortalLabResultResponse toLabResultResponse(LabOrderResultRecord record) {
        return new PatientPortalLabResultResponse(
                record.testCode(),
                record.testName(),
                record.componentName(),
                record.resultValue(),
                record.unit(),
                record.referenceRange()
        );
    }

    private boolean matchesLabResult(LatestLabMatch match, String term) {
        return contains(match.order().orderNumber(), term)
                || contains(match.result().testCode(), term)
                || contains(match.result().testName(), term)
                || contains(match.result().componentName(), term)
                || contains(match.result().resultValue(), term);
    }

    private int compareOffsetDescending(OffsetDateTime left, OffsetDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private record LatestLabMatch(LabOrderRecord order, LabOrderResultRecord result) {
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

    private PatientPortalNotificationResponse toNotificationResponse(NotificationHistoryRecord record) {
        return new PatientPortalNotificationResponse(
                record.id() == null ? null : record.id().toString(),
                record.eventType(),
                record.subject(),
                record.message(),
                record.status(),
                record.readAt(),
                record.sourceType(),
                record.sourceId() == null ? null : record.sourceId().toString(),
                record.createdAt(),
                actionPath(record)
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

    private List<PatientPortalAppointmentResponse> appointmentResponses(List<PatientAccess> patientAccesses) {
        return allAppointments(patientAccesses).stream()
                .map(record -> new PatientPortalAppointmentResponse(
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.doctorName(),
                        clinicName(record.tenantId()),
                        appointmentSource(record),
                        summarize(record.reason()),
                        record.status() == null ? null : record.status().name()
                ))
                .sorted(Comparator
                        .comparing(PatientPortalAppointmentResponse::appointmentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientPortalAppointmentResponse::appointmentTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<PatientPortalPrescriptionResponse> prescriptionResponses(List<PatientAccess> patientAccesses) {
        return allPrescriptions(patientAccesses).stream()
                .map(record -> withClinicName(toPrescriptionResponse(record), clinicName(record.tenantId())))
                .toList();
    }

    private List<BillRecord> allBills(List<PatientAccess> patientAccesses) {
        return patientAccesses.stream()
                .flatMap(patientAccess -> billingService.listByPatient(patientAccess.tenantId(), patientAccess.patient().getId()).stream())
                .distinct()
                .toList();
    }

    private List<PrescriptionRecord> allPrescriptions(List<PatientAccess> patientAccesses) {
        return patientAccesses.stream()
                .flatMap(patientAccess -> prescriptionService.listByPatient(patientAccess.tenantId(), patientAccess.patient().getId()).stream())
                .distinct()
                .toList();
    }

    private List<PatientPortalBillResponse> billResponses(List<PatientAccess> patientAccesses) {
        return allBills(patientAccesses).stream()
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
        ZoneId tenantZone = resolveTenantZone(requireCurrentPatientAccess().tenantId());
        return appointments.stream()
                .filter(this::isUpcomingStatus)
                .filter(appointment -> AppointmentTimingRules.isUpcoming(appointment.appointmentDate(), appointment.appointmentTime(), tenantZone))
                .findFirst()
                .orElse(null);
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
        ZoneId tenantZone = resolveTenantZone(appointment.tenantId());
        return AppointmentTimingRules.isUpcoming(appointment, tenantZone);
    }

    private AppointmentRecord requireAccessibleUpcomingAppointment(PatientAccess access, UUID appointmentId) {
        if (appointmentId == null) {
            throw new IllegalArgumentException("Appointment is required");
        }
        return allAppointments(resolveAccessiblePatientAccesses(access)).stream()
                .filter(record -> appointmentId.equals(record.id()))
                .filter(record -> {
                    if (record == null || record.status() == AppointmentStatus.CANCELLED || record.status() == AppointmentStatus.NO_SHOW || record.status() == AppointmentStatus.COMPLETED) {
                        return false;
                    }
                    return AppointmentTimingRules.isActionable(record, resolveTenantZone(record.tenantId()));
                })
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Upcoming appointment not found"));
    }

    private PrescriptionRecord findAccessiblePrescription(List<PatientAccess> patientAccesses, String prescriptionNumber) {
        return allPrescriptions(patientAccesses).stream()
                .filter(record -> record.prescriptionNumber() != null && record.prescriptionNumber().equalsIgnoreCase(prescriptionNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
    }

    private BillRecord findAccessibleBill(List<PatientAccess> patientAccesses, String billNumber) {
        return allBills(patientAccesses).stream()
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

    private String actionPath(NotificationHistoryRecord record) {
        if (record == null || record.sourceType() == null) {
            return null;
        }
        return switch (record.sourceType().toUpperCase()) {
            case "APPOINTMENT" -> "/patient/appointments";
            case "PRESCRIPTION" -> "/patient/prescriptions";
            case "RECEIPT", "BILL" -> "/patient/bills";
            case "LAB_ORDER" -> "/patient/lab";
            case "CONSULTATION" -> "/patient/careai";
            default -> null;
        };
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

    private BookingDoctorAccess resolveBookingDoctor(PatientAccess access, String publicDoctorId, String clinicSlug, String tenantId, String clinicId) {
        UUID bookingTenantId = resolveBookingTenantId(access.tenantId(), clinicSlug, tenantId, clinicId);
        return new BookingDoctorAccess(bookingTenantId, requirePublicBookableDoctor(bookingTenantId, publicDoctorId));
    }

    private UUID resolveBookingTenantId(UUID fallbackTenantId, String clinicSlug, String tenantId, String clinicId) {
        boolean hasExplicitTenantReference = StringUtils.hasText(tenantId) || StringUtils.hasText(clinicId) || StringUtils.hasText(clinicSlug);
        java.util.Optional<UUID> resolvedTenant = resolveExplicitTenantReference(tenantId)
                .or(() -> resolveExplicitTenantReference(clinicId))
                .or(() -> resolveTenantByClinicSlug(clinicSlug));
        UUID bookingTenantId = hasExplicitTenantReference
                ? resolvedTenant.orElseThrow(() -> {
                    logBookingEligibility("resolveBookingTenantId", null, clinicSlug, tenantId, clinicId, fallbackTenantId, null, null, null, null, "unable to resolve clinic tenant");
                    return new IllegalArgumentException("Clinic is not available for online booking.");
                })
                : fallbackTenantId;
        verifyPublicBookingClinic(bookingTenantId, clinicSlug, tenantId, clinicId, fallbackTenantId);
        return bookingTenantId;
    }

    private java.util.Optional<UUID> resolveTenantByClinicSlug(String clinicSlug) {
        if (!StringUtils.hasText(clinicSlug)) {
            return java.util.Optional.empty();
        }
        String normalizedSlug = clinicSlug.trim().toLowerCase(java.util.Locale.ROOT);
        return clinicProfileService.findBySlug(normalizedSlug)
                .map(ClinicProfileRecord::tenantId)
                .or(() -> tenantRepository.findByCode(normalizedSlug).map(TenantEntity::getId));
    }

    private java.util.Optional<UUID> resolveExplicitTenantReference(String value) {
        if (!StringUtils.hasText(value)) {
            return java.util.Optional.empty();
        }
        String trimmed = value.trim();
        try {
            UUID tenantId = UUID.fromString(trimmed);
            return tenantRepository.findById(tenantId).map(TenantEntity::getId);
        } catch (IllegalArgumentException ignored) {
            return tenantRepository.findByCode(trimmed.toLowerCase(java.util.Locale.ROOT)).map(TenantEntity::getId);
        }
    }

    private void verifyPublicBookingClinic(UUID tenantId, String clinicSlug, String tenantIdParam, String clinicId, UUID fallbackTenantId) {
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        if (clinic == null) {
            logBookingEligibility("verifyPublicBookingClinic", null, clinicSlug, tenantIdParam, clinicId, fallbackTenantId, tenantId, null, null, null, "clinic profile not found");
            throw new IllegalArgumentException("Clinic is not available for online booking.");
        }
        if (!clinic.active()) {
            logBookingEligibility("verifyPublicBookingClinic", null, clinicSlug, tenantIdParam, clinicId, fallbackTenantId, tenantId, clinic, null, null, "clinic profile inactive");
            throw new IllegalArgumentException("Clinic is inactive.");
        }
        if (!clinic.publicListingEnabled()) {
            logBookingEligibility("verifyPublicBookingClinic", null, clinicSlug, tenantIdParam, clinicId, fallbackTenantId, tenantId, clinic, null, null, "clinic public listing disabled");
            throw new IllegalArgumentException("Clinic public listing is disabled.");
        }
    }

    private DoctorSnapshot requirePublicBookableDoctor(UUID tenantId, String publicDoctorId) {
        UUID doctorUserId = parseDoctorUserId(publicDoctorId);
        DoctorSnapshot doctor = activeDoctorSnapshots(tenantId).stream()
                .filter(snapshot -> doctorUserId.equals(snapshot.user().appUserId()))
                .filter(snapshot -> snapshot.profile().publicListingEnabled())
                .findFirst()
                .orElse(null);
        if (doctor == null) {
            logBookingEligibility("requirePublicBookableDoctor", publicDoctorId, null, null, null, tenantId, tenantId, null, null, null, "doctor not public/bookable for tenant");
            throw new IllegalArgumentException("Doctor not found");
        }
        return doctor;
    }

    private DoctorSnapshot requireActiveDoctor(UUID tenantId, String publicDoctorId) {
        UUID doctorUserId = parseDoctorUserId(publicDoctorId);
        return activeDoctorSnapshots(tenantId).stream()
                .filter(snapshot -> doctorUserId.equals(snapshot.user().appUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
    }

    private void debugBookingResolution(
            String operation,
            String publicDoctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            BookingDoctorAccess bookingDoctor
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(bookingDoctor.tenantId()).orElse(null);
        var tenant = tenantRepository.findById(bookingDoctor.tenantId()).orElse(null);
        log.debug(
                "patient.portal.booking.resolve operation={} incomingDoctorId={} incomingClinicSlug={} incomingTenantId={} incomingClinicId={} resolvedTenantId={} resolvedTenantCode={} resolvedClinicSlug={} clinicActive={} clinicPublicListing={} tenantPublicListing={} doctorPublicListingEnabled={} doctorBelongsToClinic={}",
                operation,
                publicDoctorId,
                clinicSlug,
                tenantId,
                clinicId,
                bookingDoctor.tenantId(),
                tenant == null ? null : tenant.getCode(),
                clinic == null ? null : clinic.slug(),
                clinic != null && clinic.active(),
                clinic != null && clinic.publicListingEnabled(),
                tenant != null && tenant.isPublicListingEnabled(),
                bookingDoctor.doctor().profile().publicListingEnabled(),
                true
        );
    }

    private void logBookingEligibility(
            String operation,
            String publicDoctorId,
            String clinicSlug,
            String tenantIdParam,
            String clinicIdParam,
            UUID requestTenantId,
            UUID resolvedTenantId,
            ClinicProfileRecord clinic,
            TenantEntity tenant,
            DoctorSnapshot doctor,
            String reason
    ) {
        if (!log.isDebugEnabled()) {
            return;
        }
        UUID sessionTenantId = RequestContextHolder.requireTenantId();
        log.debug(
                "patient.portal.booking.eligibility operation={} doctorId={} clinicSlugParam={} clinicIdParam={} tenantIdParam={} requestTenantId={} sessionTenantId={} resolvedTenantId={} resolvedClinicId={} clinicActive={} clinicPublicListing={} doctorPublicListing={} reason={}",
                operation,
                publicDoctorId,
                clinicSlug,
                clinicIdParam,
                tenantIdParam,
                requestTenantId,
                sessionTenantId,
                resolvedTenantId,
                clinic == null ? null : clinic.id(),
                clinic != null && clinic.active(),
                clinic != null && clinic.publicListingEnabled(),
                doctor != null && doctor.profile().publicListingEnabled(),
                reason
        );
        if (tenant != null) {
            log.debug("patient.portal.booking.eligibility.tenant resolvedTenantCode={} tenantPublicListing={} tenantStatus={}",
                    tenant.getCode(),
                    tenant.isPublicListingEnabled(),
                    tenant.getStatus()
            );
        }
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
        if (appointmentDate.isBefore(LocalDate.now(resolveTenantZone(requireCurrentPatientAccess().tenantId())))) {
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

    private boolean contains(String value, String term) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(term)
                && value.toLowerCase().contains(term.toLowerCase());
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

    private PatientEntity resolveBookingPatient(PatientAccess access, UUID bookingTenantId) {
        if (bookingTenantId.equals(access.tenantId())) {
            return access.patient();
        }

        String mobile = resolveVerifiedMobile(access);
        PatientEntity existingPatient = patientRepository.findFirstByTenantIdAndMobileIgnoreCase(bookingTenantId, mobile)
                .orElse(null);
        if (existingPatient != null) {
            if (!existingPatient.isActive()) {
                throw new IllegalArgumentException("A patient record already exists for this mobile number but is inactive. Please contact the clinic.");
            }
            return existingPatient;
        }

        var created = patientService.create(
                bookingTenantId,
                new PatientUpsertCommand(
                        access.patient().getFirstName(),
                        access.patient().getLastName(),
                        access.patient().getGender(),
                        access.patient().getDateOfBirth(),
                        access.patient().getAgeYears(),
                        mobile,
                        access.patient().getEmail(),
                        access.patient().getAddressLine1(),
                        access.patient().getAddressLine2(),
                        access.patient().getCity(),
                        access.patient().getState(),
                        access.patient().getCountry(),
                        access.patient().getPostalCode(),
                        access.patient().getEmergencyContactName(),
                        access.patient().getEmergencyContactMobile(),
                        access.patient().getBloodGroup(),
                        access.patient().getAllergies(),
                        access.patient().getExistingConditions(),
                        access.patient().getLongTermMedications(),
                        access.patient().getSurgicalHistory(),
                        access.patient().getNotes(),
                        true
                ),
                requireActorAppUserId()
        );
        return patientRepository.findByTenantIdAndId(bookingTenantId, created.id())
                .orElseThrow(() -> new IllegalStateException("Patient record was not found for booking"));
    }

    private UUID ensurePatientPortalActor(UUID tenantId, PatientEntity patient) {
        String subject = "patientportal:" + tenantId + ":" + patient.getId();
        String displayName = fullName(patient.getFirstName(), patient.getLastName());
        UUID appUserId = appUserProvisioner.upsertAndReturnId(tenantId, subject, patient.getEmail(), displayName);
        appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .ifPresent(appUser -> {
                    appUser.setPatientId(patient.getId());
                    appUser.updateProfile(patient.getEmail(), displayName);
                });
        return appUserId;
    }

    private String resolveVerifiedMobile(PatientAccess access) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalSessionPrincipal principal) {
            if (StringUtils.hasText(principal.phone())) {
                return principal.phone().trim();
            }
        }
        if (StringUtils.hasText(access.patient().getMobile())) {
            return access.patient().getMobile().trim();
        }
        throw new UnauthorizedException("Verified mobile number is missing");
    }

    private List<PatientAccess> resolveAccessiblePatientAccesses(PatientAccess access) {
        Map<UUID, PatientAccess> patientAccesses = new LinkedHashMap<>();
        patientAccesses.put(access.tenantId(), access);
        String mobile = resolveVerifiedMobile(access);
        patientRepository.findByMobileIgnoreCaseAndActiveTrue(mobile).stream()
                .sorted(Comparator.comparing(PatientEntity::getTenantId))
                .forEach(patient -> patientAccesses.putIfAbsent(patient.getTenantId(), new PatientAccess(patient.getTenantId(), patient)));
        return List.copyOf(patientAccesses.values());
    }

    private List<AppointmentRecord> allAppointments(List<PatientAccess> patientAccesses) {
        return patientAccesses.stream()
                .flatMap(patientAccess -> appointmentService.listByPatient(patientAccess.tenantId(), patientAccess.patient().getId()).stream())
                .distinct()
                .toList();
    }

    private List<NotificationHistoryRecord> allNotifications(List<PatientAccess> patientAccesses) {
        return patientAccesses.stream()
                .flatMap(patientAccess -> notificationHistoryService.listByPatient(patientAccess.tenantId(), patientAccess.patient().getId()).stream())
                .distinct()
                .toList();
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

    private record BookingDoctorAccess(UUID tenantId, DoctorSnapshot doctor) {
    }

    private record DoctorSnapshot(TenantUserRecord user, DoctorProfileRecord profile) {
    }
}
