package com.deepthoughtnet.clinic.api.reports;

import com.deepthoughtnet.clinic.api.dashboard.dto.DashboardSummaryResponse;
import com.deepthoughtnet.clinic.api.dashboard.dto.ClinicDashboardResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.billing.service.BillingService;
import com.deepthoughtnet.clinic.billing.service.model.BillRecord;
import com.deepthoughtnet.clinic.billing.service.model.PaymentRecord;
import com.deepthoughtnet.clinic.billing.service.model.PaymentMode;
import com.deepthoughtnet.clinic.billing.service.model.RefundRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.inventory.db.MedicineEntity;
import com.deepthoughtnet.clinic.inventory.db.MedicineRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacyCashierShiftRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleItemRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySalePaymentRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleRepository;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnEntity;
import com.deepthoughtnet.clinic.inventory.db.PharmacySaleReturnRepository;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.notification.service.NotificationCenterService;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.notification.service.NotificationSummary;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReportingFacade {
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final BillingService billingService;
    private final VaccinationService vaccinationService;
    private final PrescriptionService prescriptionService;
    private final InventoryService inventoryService;
    private final MedicineRepository medicineRepository;
    private final PharmacySaleRepository pharmacySaleRepository;
    private final PharmacySaleItemRepository pharmacySaleItemRepository;
    private final PharmacySalePaymentRepository pharmacySalePaymentRepository;
    private final PharmacySaleReturnRepository pharmacySaleReturnRepository;
    private final PharmacyCashierShiftRepository pharmacyCashierShiftRepository;
    private final AppUserRepository appUserRepository;
    private final PatientRepository patientRepository;
    private final NotificationCenterService notificationCenterService;

    public ReportingFacade(
            AppointmentService appointmentService,
            ConsultationService consultationService,
            BillingService billingService,
            VaccinationService vaccinationService,
            PrescriptionService prescriptionService,
            InventoryService inventoryService,
            MedicineRepository medicineRepository,
            PharmacySaleRepository pharmacySaleRepository,
            PharmacySaleItemRepository pharmacySaleItemRepository,
            PharmacySalePaymentRepository pharmacySalePaymentRepository,
            PharmacySaleReturnRepository pharmacySaleReturnRepository,
            PharmacyCashierShiftRepository pharmacyCashierShiftRepository,
            AppUserRepository appUserRepository,
            PatientRepository patientRepository,
            NotificationCenterService notificationCenterService
    ) {
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.billingService = billingService;
        this.vaccinationService = vaccinationService;
        this.prescriptionService = prescriptionService;
        this.inventoryService = inventoryService;
        this.medicineRepository = medicineRepository;
        this.pharmacySaleRepository = pharmacySaleRepository;
        this.pharmacySaleItemRepository = pharmacySaleItemRepository;
        this.pharmacySalePaymentRepository = pharmacySalePaymentRepository;
        this.pharmacySaleReturnRepository = pharmacySaleReturnRepository;
        this.pharmacyCashierShiftRepository = pharmacyCashierShiftRepository;
        this.appUserRepository = appUserRepository;
        this.patientRepository = patientRepository;
        this.notificationCenterService = notificationCenterService;
    }

    public DashboardSummaryResponse dashboardSummary(UUID tenantId) {
        List<com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord> todayAppointments = appointmentService.listToday(tenantId);
        List<ConsultationRecord> consultations = consultationService.list(tenantId);
        return dashboardSummary(tenantId, todayAppointments, consultations);
    }

    public DashboardSummaryResponse dashboardSummary(UUID tenantId, UUID doctorUserId) {
        List<com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord> todayAppointments = appointmentService.search(
                tenantId,
                new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(doctorUserId, null, LocalDate.now(), null, null)
        );
        List<ConsultationRecord> consultations = consultationService.listByDoctor(tenantId, doctorUserId);
        return dashboardSummary(tenantId, todayAppointments, consultations);
    }

    private DashboardSummaryResponse dashboardSummary(
            UUID tenantId,
            List<com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord> todayAppointments,
            List<ConsultationRecord> consultations
    ) {
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null));
        BigDecimal todayRevenue = BigDecimal.ZERO;
        for (BillRecord bill : bills) {
            for (PaymentRecord payment : billingService.listPayments(tenantId, bill.id())) {
                if (payment.createdAt() != null && payment.createdAt().toLocalDate().equals(LocalDate.now())) {
                    todayRevenue = todayRevenue.add(payment.amount());
                }
            }
        }
        BigDecimal pendingDues = bills.stream()
                .map(BillRecord::dueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long followUpsDue = consultations.stream()
                .filter(record -> record.followUpDate() != null && !record.followUpDate().isAfter(LocalDate.now()))
                .count();
        long lowStockMedicines = inventoryService.listLowStock(tenantId).size();
        NotificationSummary notificationSummary = notificationCenterService.summarize(tenantId);
        return new DashboardSummaryResponse(
                todayAppointments.size(),
                todayAppointments.stream().filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.WAITING).count(),
                todayAppointments.stream().filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.IN_CONSULTATION).count(),
                consultations.stream().filter(record -> record.status() == ConsultationStatus.COMPLETED).count(),
                todayRevenue.setScale(2, RoundingMode.HALF_UP),
                pendingDues,
                followUpsDue,
                vaccinationService.listDue(tenantId).size(),
                lowStockMedicines,
                notificationSummary.pendingCount(),
                notificationSummary.failedCount(),
                notificationSummary.sentTodayCount()
        );
    }

    public ClinicDashboardResponse clinicDashboard(UUID tenantId, LocalDate date, UUID doctorUserId) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return clinicDashboard(tenantId, effectiveDate, effectiveDate, doctorUserId);
    }

    public ClinicDashboardResponse clinicDashboard(UUID tenantId, LocalDate startDate, LocalDate endDate, UUID doctorUserId) {
        LocalDate from = startDate == null ? LocalDate.now() : startDate;
        LocalDate to = endDate == null ? from : endDate;
        if (to.isBefore(from)) {
            LocalDate temp = from;
            from = to;
            to = temp;
        }
        final LocalDate effectiveFrom = from;
        final LocalDate effectiveTo = to;
        List<AppointmentRecord> dateAppointments = appointmentService.search(
                tenantId,
                new AppointmentSearchCriteria(doctorUserId, null, null, null, null)
        ).stream().filter(a -> between(a.appointmentDate(), effectiveFrom, effectiveTo)).toList();
        List<ConsultationRecord> consultations = doctorUserId == null
                ? consultationService.list(tenantId)
                : consultationService.listByDoctor(tenantId, doctorUserId);
        List<ConsultationRecord> dateConsultations = consultations.stream()
                .filter(record -> record.createdAt() != null && between(record.createdAt().toLocalDate(), effectiveFrom, effectiveTo))
                .toList();
        List<ConsultationRecord> activeConsultations = consultations.stream()
                .filter(record -> record.status() == ConsultationStatus.DRAFT)
                .toList();
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null));
        if (doctorUserId != null) {
            bills = bills.stream()
                    .filter(bill -> dateAppointments.stream().anyMatch(appointment -> appointment.id() != null && appointment.id().equals(bill.appointmentId())))
                    .toList();
        }
        final List<BillRecord> scopedBills = bills;
        List<BillRecord> dateBills = bills.stream()
                .filter(bill -> between(bill.billDate(), effectiveFrom, effectiveTo))
                .toList();
        List<PaymentRecord> allPayments = new ArrayList<>();
        for (BillRecord bill : bills) {
            allPayments.addAll(billingService.listPayments(tenantId, bill.id()));
        }
        List<PaymentRecord> datePayments = allPayments.stream()
                .filter(payment -> payment.createdAt() != null && between(payment.createdAt().toLocalDate(), effectiveFrom, effectiveTo))
                .toList();

        long scheduled = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.BOOKED).count();
        long checkedIn = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.WAITING).count();
        long inConsultation = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.IN_CONSULTATION).count();
        long completed = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.COMPLETED).count();
        long noShow = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.NO_SHOW).count();
        long cancelled = dateAppointments.stream().filter(a -> a.status() == AppointmentStatus.CANCELLED).count();

        long averageWaitMinutes = 0;
        List<AppointmentRecord> waitingAppointments = dateAppointments.stream()
                .filter(a -> a.status() == AppointmentStatus.WAITING)
                .toList();
        if (!waitingAppointments.isEmpty()) {
            long totalWait = waitingAppointments.stream()
                    .filter(a -> a.updatedAt() != null)
                    .mapToLong(a -> ChronoUnit.MINUTES.between(a.updatedAt(), OffsetDateTime.now(ZoneOffset.UTC)))
                    .sum();
            averageWaitMinutes = totalWait / waitingAppointments.size();
        }

        BigDecimal billedToday = dateBills.stream().map(BillRecord::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidToday = datePayments.stream().map(PaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingToday = dateBills.stream().map(BillRecord::dueAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ClinicDashboardResponse.DoctorSummary> doctorSummaries = dateAppointments.stream()
                .filter(a -> a.doctorUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(AppointmentRecord::doctorUserId))
                .entrySet()
                .stream()
                .map(entry -> {
                    UUID key = entry.getKey();
                    List<AppointmentRecord> records = entry.getValue();
                    String doctorName = records.stream().map(AppointmentRecord::doctorName).filter(name -> name != null && !name.isBlank()).findFirst().orElse(key.toString());
                    LocalTime nextAppointment = records.stream()
                            .filter(record -> record.appointmentTime() != null)
                            .filter(record -> record.appointmentDate() != null && !record.appointmentDate().isBefore(LocalDate.now()))
                            .map(AppointmentRecord::appointmentTime)
                            .sorted()
                            .findFirst()
                            .orElse(null);
                    BigDecimal doctorRevenue = BigDecimal.ZERO;
                    for (BillRecord bill : scopedBills) {
                        for (AppointmentRecord record : records) {
                            if (record.id() != null && record.id().equals(bill.appointmentId())) {
                                doctorRevenue = doctorRevenue.add(bill.paidAmount() == null ? BigDecimal.ZERO : bill.paidAmount());
                                break;
                            }
                        }
                    }
                    return new ClinicDashboardResponse.DoctorSummary(
                            key,
                            doctorName,
                            records.size(),
                            records.stream().filter(r -> r.status() == AppointmentStatus.WAITING).count(),
                            records.stream().filter(r -> r.status() == AppointmentStatus.COMPLETED).count(),
                            records.stream().filter(r -> r.status() == AppointmentStatus.NO_SHOW).count(),
                            records.stream().filter(r -> r.status() == AppointmentStatus.CANCELLED).count(),
                            nextAppointment,
                            doctorRevenue.setScale(2, RoundingMode.HALF_UP),
                            prescriptions(tenantId, effectiveFrom, effectiveTo, key, null).size(),
                            dateConsultations.stream().filter(c -> key.equals(c.doctorUserId()) && c.status() == ConsultationStatus.COMPLETED).count(),
                            BigDecimal.valueOf(records.size()).divide(BigDecimal.valueOf(Math.max(1L, ChronoUnit.DAYS.between(effectiveFrom, effectiveTo.plusDays(1)))), 2, RoundingMode.HALF_UP)
                    );
                })
                .sorted(Comparator.comparing(ClinicDashboardResponse.DoctorSummary::doctorName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<ClinicDashboardResponse.QueueItem> queueItems = dateAppointments.stream()
                .filter(a -> a.status() == AppointmentStatus.WAITING || a.status() == AppointmentStatus.IN_CONSULTATION)
                .sorted(Comparator.comparing(AppointmentRecord::tokenNumber, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AppointmentRecord::appointmentTime, Comparator.nullsLast(LocalTime::compareTo)))
                .map(a -> new ClinicDashboardResponse.QueueItem(
                        a.id(),
                        a.patientId(),
                        a.patientName(),
                        a.patientNumber(),
                        a.doctorUserId(),
                        a.doctorName(),
                        a.tokenNumber(),
                        a.appointmentTime(),
                        a.updatedAt(),
                        a.status().name()
                ))
                .toList();

        List<ClinicDashboardResponse.PendingBillItem> pendingBillItems = dateBills.stream()
                .filter(bill -> bill.dueAmount() != null && bill.dueAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BillRecord::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(bill -> new ClinicDashboardResponse.PendingBillItem(
                        bill.id(),
                        bill.billNumber(),
                        bill.patientId(),
                        bill.patientName(),
                        bill.dueAmount(),
                        bill.billDate(),
                        bill.status() == null ? null : bill.status().name()
                ))
                .toList();

        long followUpDueToday = consultations.stream().filter(c -> between(c.followUpDate(), effectiveFrom, effectiveTo)).count();
        long followUpOverdue = consultations.stream().filter(c -> c.followUpDate() != null && c.followUpDate().isBefore(effectiveFrom)).count();
        long followUpNext7 = consultations.stream().filter(c -> c.followUpDate() != null && c.followUpDate().isAfter(effectiveTo) && !c.followUpDate().isAfter(effectiveTo.plusDays(7))).count();

        List<ClinicDashboardResponse.RecentActivityItem> activity = new ArrayList<>();
        for (AppointmentRecord appointment : dateAppointments) {
            activity.add(new ClinicDashboardResponse.RecentActivityItem(
                    appointment.updatedAt() != null ? appointment.updatedAt() : appointment.createdAt(),
                    "APPOINTMENT",
                    "Appointment " + appointment.status().name(),
                    activityDescriptionForAppointment(appointment),
                    appointment.patientName(),
                    appointment.doctorName()
            ));
        }
        for (ConsultationRecord consultation : dateConsultations) {
            activity.add(new ClinicDashboardResponse.RecentActivityItem(
                    consultation.updatedAt() != null ? consultation.updatedAt() : consultation.createdAt(),
                    "CONSULTATION",
                    consultation.status() == ConsultationStatus.COMPLETED ? "Consultation completed" : "Consultation started",
                    consultation.status() == ConsultationStatus.COMPLETED
                            ? safe(consultation.doctorName()) + " completed consultation for " + safe(consultation.patientName())
                            : safe(consultation.doctorName()) + " started consultation for " + safe(consultation.patientName()),
                    consultation.patientName(),
                    consultation.doctorName()
            ));
        }
        List<Map<String, Object>> prescriptionRows = prescriptions(tenantId, effectiveFrom, effectiveTo, doctorUserId, null);
        for (Map<String, Object> prescription : prescriptionRows) {
            activity.add(new ClinicDashboardResponse.RecentActivityItem(
                    null,
                    "PRESCRIPTION",
                    "Prescription generated",
                    "Prescription generated for " + String.valueOf(prescription.getOrDefault("patientName", "patient")),
                    String.valueOf(prescription.getOrDefault("patientName", "")),
                    String.valueOf(prescription.getOrDefault("doctorName", ""))
            ));
        }
        for (PaymentRecord payment : datePayments) {
            activity.add(new ClinicDashboardResponse.RecentActivityItem(
                    payment.createdAt(),
                    "PAYMENT",
                    "Payment received",
                    "Payment received for amount " + payment.amount(),
                    null,
                    null
            ));
        }
        List<ClinicDashboardResponse.RecentActivityItem> recentActivity = activity.stream()
                .filter(item -> item.timestamp() != null)
                .sorted(Comparator.comparing(ClinicDashboardResponse.RecentActivityItem::timestamp, Comparator.reverseOrder()))
                .limit(20)
                .toList();

        long prescriptionsGeneratedToday = prescriptionRows.size();
        long consultationsWithPrescriptions = dateConsultations.stream()
                .filter(c -> c.id() != null && prescriptionRows.stream().anyMatch(p -> c.id().equals(p.get("consultationId"))))
                .count();
        BigDecimal avgPrescriptionPerConsultation = BigDecimal.valueOf(prescriptionsGeneratedToday)
                .divide(BigDecimal.valueOf(Math.max(1L, dateConsultations.size())), 2, RoundingMode.HALF_UP);

        return new ClinicDashboardResponse(
                effectiveFrom,
                effectiveTo,
                tenantId,
                new ClinicDashboardResponse.AppointmentSummary(dateAppointments.size(), scheduled, checkedIn, inConsultation, completed, noShow, cancelled),
                new ClinicDashboardResponse.QueueSummary(checkedIn, inConsultation, completed, noShow, cancelled, averageWaitMinutes),
                new ClinicDashboardResponse.ConsultationSummary(dateConsultations.size(), dateConsultations.stream().filter(c -> c.status() == ConsultationStatus.COMPLETED).count(), activeConsultations.size(), consultationsWithPrescriptions),
                new ClinicDashboardResponse.PrescriptionSummary(prescriptionsGeneratedToday, consultationsWithPrescriptions, avgPrescriptionPerConsultation),
                new ClinicDashboardResponse.BillingSummary(dateBills.size(), dateBills.stream().filter(b -> b.paidAmount() != null && b.paidAmount().compareTo(BigDecimal.ZERO) > 0).count(), pendingBillItems.size(), billedToday.setScale(2, RoundingMode.HALF_UP), paidToday.setScale(2, RoundingMode.HALF_UP), pendingToday.setScale(2, RoundingMode.HALF_UP)),
                new ClinicDashboardResponse.FollowUpSummary(followUpDueToday, followUpOverdue, followUpNext7),
                doctorSummaries,
                queueItems,
                pendingBillItems,
                recentActivity
        );
    }

    public ClinicDashboardResponse platformDashboard(LocalDate startDate, LocalDate endDate) {
        LocalDate from = startDate == null ? LocalDate.now() : startDate;
        LocalDate to = endDate == null ? from : endDate;
        if (to.isBefore(from)) {
            LocalDate temp = from;
            from = to;
            to = temp;
        }
        return new ClinicDashboardResponse(
                from,
                to,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public ClinicDashboardResponse filterByRole(ClinicDashboardResponse response, Set<String> normalizedRoles) {
        if (response == null) {
            return null;
        }
        Set<String> roles = normalizedRoles == null ? Set.of() : normalizedRoles;
        if (roles.contains(Roles.CLINIC_ADMIN) || roles.contains(Roles.AUDITOR)) {
            return response;
        }
        if (roles.contains(Roles.BILLING_USER)) {
            return new ClinicDashboardResponse(
                    response.startDate(),
                    response.endDate(),
                    response.tenantId(),
                    null,
                    null,
                    null,
                    null,
                    response.billingSummary(),
                    null,
                    List.of(),
                    List.of(),
                    response.recentUnpaidBills(),
                    response.recentActivity().stream().filter(item -> "PAYMENT".equalsIgnoreCase(item.type())).toList()
            );
        }
        if (roles.contains(Roles.DOCTOR)) {
            return new ClinicDashboardResponse(
                    response.startDate(),
                    response.endDate(),
                    response.tenantId(),
                    response.appointmentSummary(),
                    response.queueSummary(),
                    response.consultationSummary(),
                    response.prescriptionSummary(),
                    null,
                    response.followUpSummary(),
                    response.doctorSummaries(),
                    response.currentWaitingList(),
                    List.of(),
                    response.recentActivity().stream()
                            .filter(item -> !"PAYMENT".equalsIgnoreCase(item.type()))
                            .toList()
            );
        }
        if (roles.contains(Roles.RECEPTIONIST)) {
            ClinicDashboardResponse.BillingSummary basicBilling = response.billingSummary() == null
                    ? null
                    : new ClinicDashboardResponse.BillingSummary(
                    response.billingSummary().billsCreated(),
                    response.billingSummary().paidBills(),
                    response.billingSummary().pendingBills(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
            return new ClinicDashboardResponse(
                    response.startDate(),
                    response.endDate(),
                    response.tenantId(),
                    response.appointmentSummary(),
                    response.queueSummary(),
                    response.consultationSummary(),
                    response.prescriptionSummary(),
                    basicBilling,
                    response.followUpSummary(),
                    response.doctorSummaries().stream().map(d -> new ClinicDashboardResponse.DoctorSummary(
                            d.doctorUserId(),
                            d.doctorName(),
                            d.appointmentsToday(),
                            d.checkedIn(),
                            d.completed(),
                            d.noShow(),
                            d.cancelled(),
                            d.nextAppointmentTime(),
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                            d.prescriptionsGenerated(),
                            d.consultationsCompleted(),
                            d.avgConsultationLoad()
                    )).toList(),
                    response.currentWaitingList(),
                    List.of(),
                    response.recentActivity().stream()
                            .filter(item -> !"PAYMENT".equalsIgnoreCase(item.type()))
                            .toList()
            );
        }
        return new ClinicDashboardResponse(
                response.startDate(),
                response.endDate(),
                response.tenantId(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private String activityDescriptionForAppointment(AppointmentRecord appointment) {
        return switch (appointment.status()) {
            case BOOKED -> "Appointment created for " + safe(appointment.patientName());
            case WAITING -> safe(appointment.patientName()) + " checked in";
            case CANCELLED -> "Appointment cancelled for " + safe(appointment.patientName());
            case NO_SHOW -> safe(appointment.patientName()) + " marked no-show";
            case IN_CONSULTATION -> safe(appointment.patientName()) + " moved to consultation";
            case COMPLETED -> "Appointment completed for " + safe(appointment.patientName());
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "patient" : value;
    }

    public List<Map<String, Object>> patientVisits(UUID tenantId, LocalDate from, LocalDate to, UUID doctorUserId, UUID patientId) {
        return appointmentService.search(tenantId, new com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria(doctorUserId, patientId, null, null, null))
                .stream()
                .filter(appointment -> between(appointment.appointmentDate(), from, to))
                .map(appointment -> row(
                        "date", appointment.appointmentDate(),
                        "patientId", appointment.patientId(),
                        "patientName", appointment.patientName(),
                        "doctorUserId", appointment.doctorUserId(),
                        "doctorName", appointment.doctorName(),
                        "status", appointment.status().name(),
                        "appointmentId", appointment.id()
                ))
                .toList();
    }

    public List<Map<String, Object>> doctorConsultations(UUID tenantId, LocalDate from, LocalDate to, UUID doctorUserId, UUID patientId, String status) {
        return consultationService.list(tenantId).stream()
                .filter(record -> between(record.createdAt().toLocalDate(), from, to))
                .filter(record -> doctorUserId == null || doctorUserId.equals(record.doctorUserId()))
                .filter(record -> patientId == null || patientId.equals(record.patientId()))
                .filter(record -> status == null || status.isBlank() || record.status().name().equalsIgnoreCase(status))
                .map(record -> row(
                        "date", record.createdAt().toLocalDate(),
                        "doctorUserId", record.doctorUserId(),
                        "doctorName", record.doctorName(),
                        "consultationId", record.id(),
                        "patientId", record.patientId(),
                        "patientName", record.patientName(),
                        "status", record.status().name()
                ))
                .toList();
    }

    public List<Map<String, Object>> revenue(UUID tenantId, LocalDate from, LocalDate to, UUID doctorUserId, UUID patientId, String paymentMode, String source) {
        ReportSource sourceFilter = parseSource(source);
        PaymentMode paymentModeFilter = parsePaymentMode(paymentMode);

        RevenueTotals clinic = sourceFilter.includesClinic()
                ? clinicRevenueTotals(tenantId, from, to, patientId, paymentModeFilter)
                : RevenueTotals.zero();
        RevenueTotals pharmacy = sourceFilter.includesPharmacy()
                ? pharmacyRevenueTotals(tenantId, from, to, patientId, paymentModeFilter)
                : RevenueTotals.zero();

        List<Map<String, Object>> rows = new ArrayList<>();
        if (sourceFilter.includesClinic()) {
            rows.add(revenueRow("CLINIC", clinic));
        }
        if (sourceFilter.includesPharmacy()) {
            rows.add(revenueRow("PHARMACY", pharmacy));
        }
        if (sourceFilter == ReportSource.ALL) {
            RevenueTotals total = clinic.plus(pharmacy);
            rows.add(row(
                    "source", "TOTAL",
                    "clinicRevenue", money(clinic.revenueBeforeRefunds()),
                    "pharmacyRevenue", money(pharmacy.revenueBeforeRefunds()),
                    "totalGrossRevenue", money(total.grossRevenue()),
                    "discounts", money(total.discounts()),
                    "tax", money(total.tax()),
                    "totalRevenue", money(total.revenueBeforeRefunds()),
                    "refunds", money(total.refunds()),
                    "netRevenue", money(total.netRevenue())
            ));
        }
        return rows;
    }

    public List<Map<String, Object>> dailySales(UUID tenantId, LocalDate from, LocalDate to, String paymentMode, String source) {
        ReportSource sourceFilter = parseSource(source);
        PaymentMode paymentModeFilter = parsePaymentMode(paymentMode);
        Map<String, DailySalesAccumulator> rows = new LinkedHashMap<>();

        if (sourceFilter.includesClinic()) {
            for (BillRecord bill : billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null))) {
                if (!between(bill.billDate(), from, to)) {
                    continue;
                }
                List<PaymentRecord> paymentsInRange = billingService.listPayments(tenantId, bill.id()).stream()
                        .filter(payment -> between(payment.paymentDate(), from, to))
                        .toList();
                BigDecimal totalPaymentAmount = paymentsInRange.stream()
                        .map(PaymentRecord::amount)
                        .map(this::safeMoney)
                        .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                BigDecimal matchingPaymentAmount = paymentsInRange.stream()
                        .filter(payment -> matchesPaymentMode(payment.paymentMode(), paymentModeFilter))
                        .map(PaymentRecord::amount)
                        .map(this::safeMoney)
                        .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                if (paymentModeFilter != null && matchingPaymentAmount.compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) <= 0) {
                    continue;
                }
                BigDecimal allocationRatio = allocationRatio(paymentModeFilter == null ? null : matchingPaymentAmount, totalPaymentAmount);
                DailySalesAccumulator daily = rows.computeIfAbsent(
                        bill.billDate() + "|CLINIC",
                        key -> new DailySalesAccumulator(bill.billDate(), "CLINIC")
                );
                daily.grossSales = daily.grossSales.add(prorateMoney(safeMoney(bill.subtotalAmount()), allocationRatio));
                daily.discount = daily.discount.add(prorateMoney(safeMoney(bill.discountAmount()), allocationRatio));
                daily.tax = daily.tax.add(prorateMoney(safeMoney(bill.taxAmount()), allocationRatio));
                for (PaymentRecord payment : paymentsInRange) {
                    if (!matchesPaymentMode(payment.paymentMode(), paymentModeFilter)) {
                        continue;
                    }
                    daily.addPayment(payment.paymentMode() == null ? "OTHER" : payment.paymentMode().name(), safeMoney(payment.amount()));
                }
                for (RefundRecord refund : billingService.listRefunds(tenantId, bill.id())) {
                    if (refund.refundedAt() == null || !between(refund.refundedAt().toLocalDate(), from, to)) {
                        continue;
                    }
                    if (!matchesPaymentMode(refund.refundMode(), paymentModeFilter)) {
                        continue;
                    }
                    daily.refunds = daily.refunds.add(safeMoney(refund.amount()));
                }
            }
        }

        if (sourceFilter.includesPharmacy()) {
            for (PharmacySaleEntity sale : pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
                if (!between(sale.getSaleDateTime() == null ? null : sale.getSaleDateTime().toLocalDate(), from, to)) {
                    continue;
                }
                List<PharmacySalePaymentEntity> paymentsInRange = pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId()).stream()
                        .filter(payment -> between(payment.getPaymentDate(), from, to))
                        .toList();
                BigDecimal totalPaymentAmount = paymentsInRange.stream()
                        .map(PharmacySalePaymentEntity::getAmount)
                        .map(this::safeMoney)
                        .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                BigDecimal matchingPaymentAmount = paymentsInRange.stream()
                        .filter(payment -> matchesPaymentMode(payment.getPaymentMode(), paymentModeFilter))
                        .map(PharmacySalePaymentEntity::getAmount)
                        .map(this::safeMoney)
                        .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
                if (paymentModeFilter != null && matchingPaymentAmount.compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) <= 0) {
                    continue;
                }
                BigDecimal allocationRatio = allocationRatio(paymentModeFilter == null ? null : matchingPaymentAmount, totalPaymentAmount);
                DailySalesAccumulator daily = rows.computeIfAbsent(
                        sale.getSaleDateTime().toLocalDate() + "|PHARMACY",
                        key -> new DailySalesAccumulator(sale.getSaleDateTime().toLocalDate(), "PHARMACY")
                );
                daily.grossSales = daily.grossSales.add(prorateMoney(safeMoney(sale.getSubtotal()), allocationRatio));
                daily.discount = daily.discount.add(prorateMoney(safeMoney(sale.getDiscount()), allocationRatio));
                daily.tax = daily.tax.add(prorateMoney(safeMoney(sale.getTax()), allocationRatio));
                for (PharmacySalePaymentEntity payment : paymentsInRange) {
                    if (!matchesPaymentMode(payment.getPaymentMode(), paymentModeFilter)) {
                        continue;
                    }
                    daily.addPayment(payment.getPaymentMode(), safeMoney(payment.getAmount()));
                }
            }
            for (PharmacySaleReturnEntity saleReturn : pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
                if (!between(saleReturn.getCreatedAt() == null ? null : saleReturn.getCreatedAt().toLocalDate(), from, to)) {
                    continue;
                }
                if (!matchesPaymentMode(saleReturn.getRefundMode(), paymentModeFilter)) {
                    continue;
                }
                DailySalesAccumulator daily = rows.computeIfAbsent(
                        saleReturn.getCreatedAt().toLocalDate() + "|PHARMACY",
                        key -> new DailySalesAccumulator(saleReturn.getCreatedAt().toLocalDate(), "PHARMACY")
                );
                daily.refunds = daily.refunds.add(safeMoney(saleReturn.getRefundAmount()));
            }
        }

        return rows.values().stream()
                .sorted(Comparator.comparing(DailySalesAccumulator::date).thenComparing(DailySalesAccumulator::source))
                .map(accumulator -> row(
                        "date", accumulator.date(),
                        "source", accumulator.source(),
                        "grossSales", money(accumulator.grossSales),
                        "discount", money(accumulator.discount),
                        "tax", money(accumulator.tax),
                        "refunds", money(accumulator.refunds),
                        "netSales", money(accumulator.netSales()),
                        "cash", money(accumulator.cash),
                        "upi", money(accumulator.upi),
                        "card", money(accumulator.card),
                        "other", money(accumulator.other)
                ))
                .toList();
    }

    public List<Map<String, Object>> medicineSales(UUID tenantId, LocalDate from, LocalDate to, String paymentMode) {
        PaymentMode paymentModeFilter = parsePaymentMode(paymentMode);
        List<PharmacySaleEntity> sales = pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(sale -> between(sale.getSaleDateTime() == null ? null : sale.getSaleDateTime().toLocalDate(), from, to))
                .toList();
        Map<UUID, PharmacySaleEntity> saleById = sales.stream().collect(java.util.stream.Collectors.toMap(PharmacySaleEntity::getId, sale -> sale));
        Map<UUID, MedicineEntity> medicineById = medicineRepository.findByTenantIdOrderByMedicineNameAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(MedicineEntity::getId, medicine -> medicine));
        Map<UUID, String> cashierById = userDisplayMap(tenantId, sales.stream().map(PharmacySaleEntity::getCreatedBy).toList());
        Map<UUID, String> patientById = patientDisplayMap(tenantId, sales.stream().map(PharmacySaleEntity::getPatientId).toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PharmacySaleItemEntity item : pharmacySaleItemRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            PharmacySaleEntity sale = saleById.get(item.getSaleId());
            if (sale == null) {
                continue;
            }
            List<PharmacySalePaymentEntity> payments = pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId());
            String mode = resolveSalePaymentMode(payments);
            if (paymentModeFilter != null && !paymentModeMatches(mode, paymentModeFilter)) {
                continue;
            }
            MedicineEntity medicine = medicineById.get(item.getMedicineId());
            rows.add(row(
                    "saleDate", sale.getSaleDateTime() == null ? null : sale.getSaleDateTime().toLocalDate(),
                    "saleNumber", sale.getSaleNumber(),
                    "medicine", medicine == null ? "Unknown medicine" : medicine.getMedicineName(),
                    "batch", item.getBatchNumber(),
                    "qty", item.getQuantity(),
                    "unitPrice", money(item.getUnitPrice()),
                    "discount", money(item.getDiscount()),
                    "tax", money(item.getTax()),
                    "lineTotal", money(item.getLineTotal()),
                    "paymentMode", mode,
                    "cashier", cashierById.getOrDefault(sale.getCreatedBy(), "Unknown user"),
                    "customerPatient", customerOrPatient(sale, patientById)
            ));
        }
        rows.sort(Comparator.<Map<String, Object>, String>comparing(row -> String.valueOf(row.getOrDefault("saleDate", "")), Comparator.reverseOrder())
                .thenComparing(row -> String.valueOf(row.getOrDefault("saleNumber", "")), Comparator.reverseOrder()));
        return rows;
    }

    public List<Map<String, Object>> paymentModes(UUID tenantId, LocalDate from, LocalDate to, String paymentMode, String source) {
        ReportSource sourceFilter = parseSource(source);
        PaymentMode paymentModeFilter = parsePaymentMode(paymentMode);
        Map<String, PaymentModeBreakdown> totals = new LinkedHashMap<>();

        if (sourceFilter.includesClinic()) {
            for (BillRecord bill : billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null))) {
                for (PaymentRecord payment : billingService.listPayments(tenantId, bill.id())) {
                    if (!between(payment.paymentDate(), from, to)) {
                        continue;
                    }
                    if (!matchesPaymentMode(payment.paymentMode(), paymentModeFilter)) {
                        continue;
                    }
                    String mode = payment.paymentMode() == null ? "OTHER" : payment.paymentMode().name();
                    paymentModeAccumulator(totals, "CLINIC|" + mode).recordPayment(safeMoney(payment.amount()));
                }
                for (RefundRecord refund : billingService.listRefunds(tenantId, bill.id())) {
                    if (refund.refundedAt() == null || !between(refund.refundedAt().toLocalDate(), from, to)) {
                        continue;
                    }
                    if (!matchesPaymentMode(refund.refundMode(), paymentModeFilter)) {
                        continue;
                    }
                    String mode = refund.refundMode() == null ? "OTHER" : refund.refundMode().name();
                    paymentModeAccumulator(totals, "CLINIC|" + mode).recordRefund(safeMoney(refund.amount()));
                }
            }
        }

        if (sourceFilter.includesPharmacy()) {
            for (PharmacySalePaymentEntity payment : pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
                if (!between(payment.getPaymentDate(), from, to)) {
                    continue;
                }
                if (paymentModeFilter != null && !paymentModeFilter.name().equalsIgnoreCase(payment.getPaymentMode())) {
                    continue;
                }
                String mode = normalizePaymentModeLabel(payment.getPaymentMode());
                paymentModeAccumulator(totals, "PHARMACY|" + mode).recordPayment(safeMoney(payment.getAmount()));
            }
            for (PharmacySaleReturnEntity saleReturn : pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
                if (saleReturn.getCreatedAt() == null || !between(saleReturn.getCreatedAt().toLocalDate(), from, to)) {
                    continue;
                }
                if (paymentModeFilter != null && !paymentModeFilter.name().equalsIgnoreCase(saleReturn.getRefundMode())) {
                    continue;
                }
                String mode = normalizePaymentModeLabel(saleReturn.getRefundMode());
                paymentModeAccumulator(totals, "PHARMACY|" + mode).recordRefund(safeMoney(saleReturn.getRefundAmount()));
            }
        }

        return totals.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    PaymentModeBreakdown breakdown = entry.getValue();
                    return row(
                            "source", parts[0],
                            "paymentMode", parts[1],
                            "count", breakdown.count(),
                            "grossAmount", money(breakdown.grossAmount()),
                            "refundAmount", money(breakdown.refundAmount()),
                            "netAmount", money(breakdown.netAmount())
                    );
                })
                .toList();
    }

    public Map<String, Object> cashCounterSummary(UUID tenantId, LocalDate from, LocalDate to, String paymentMode, String source, String search) {
        List<CashCounterLedgerEntry> entries = cashCounterLedgerEntries(tenantId, from, to, paymentMode, source, search);
        BigDecimal clinicBillingCollected = entries.stream()
                .filter(entry -> entry.sourceKind() == CashCounterSourceKind.CLINIC_BILL)
                .map(CashCounterLedgerEntry::grossAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal pharmacyPosCollected = entries.stream()
                .filter(entry -> entry.sourceKind() == CashCounterSourceKind.PHARMACY_POS)
                .map(CashCounterLedgerEntry::grossAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal refundsReturns = entries.stream()
                .map(CashCounterLedgerEntry::refundAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal todayTotalCollected = clinicBillingCollected.add(pharmacyPosCollected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netCash = entries.stream()
                .filter(entry -> "CASH".equals(entry.paymentMode()))
                .map(CashCounterLedgerEntry::netAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal netUpi = entries.stream()
                .filter(entry -> "UPI".equals(entry.paymentMode()))
                .map(CashCounterLedgerEntry::netAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal netCard = entries.stream()
                .filter(entry -> "CARD".equals(entry.paymentMode()))
                .map(CashCounterLedgerEntry::netAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal netOther = entries.stream()
                .filter(entry -> !"CASH".equals(entry.paymentMode()) && !"UPI".equals(entry.paymentMode()) && !"CARD".equals(entry.paymentMode()))
                .map(CashCounterLedgerEntry::netAmount)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        List<PharmacyCashierShiftEntity> shifts = pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId);
        long openCashierShifts = shifts.stream()
                .filter(shift -> "OPEN".equalsIgnoreCase(shift.getStatus()))
                .count();
        long varianceAlerts = shifts.stream()
                .filter(shift -> between(shift.getOpenedAt() == null ? null : shift.getOpenedAt().toLocalDate(), from, to))
                .filter(shift -> safeMoney(shift.getVarianceAmount()).compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) != 0)
                .count();

        return row(
                "todayTotalCollected", money(todayTotalCollected),
                "clinicBillingCollected", money(clinicBillingCollected),
                "pharmacyPosCollected", money(pharmacyPosCollected),
                "refundsReturns", money(refundsReturns),
                "netCash", money(netCash),
                "netUpi", money(netUpi),
                "netCard", money(netCard),
                "netOther", money(netOther),
                "openCashierShifts", openCashierShifts,
                "varianceAlerts", varianceAlerts
        );
    }

    public List<Map<String, Object>> cashCounterLedger(UUID tenantId, LocalDate from, LocalDate to, String paymentMode, String source, String search) {
        return cashCounterLedgerEntries(tenantId, from, to, paymentMode, source, search).stream()
                .map(entry -> row(
                        "dateTime", entry.occurredAt(),
                        "source", entry.sourceLabel(),
                        "businessReference", entry.businessReference(),
                        "receiptNumber", entry.receiptNumber(),
                        "patientCustomer", entry.patientCustomer(),
                        "paymentMode", entry.paymentMode(),
                        "grossAmount", money(entry.grossAmount()),
                        "refundAmount", money(entry.refundAmount()),
                        "netAmount", money(entry.netAmount()),
                        "cashier", entry.cashier(),
                        "shiftReference", entry.shiftReference(),
                        "status", entry.status()
                ))
                .toList();
    }

    public List<Map<String, Object>> cashierShifts(UUID tenantId, LocalDate from, LocalDate to) {
        Map<UUID, String> cashierById = userDisplayMap(
                tenantId,
                pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId).stream().map(PharmacyCashierShiftEntity::getCashierUserId).toList()
        );
        return pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId).stream()
                .filter(shift -> between(shift.getOpenedAt() == null ? null : shift.getOpenedAt().toLocalDate(), from, to))
                .map(shift -> row(
                        "shiftOpened", shift.getOpenedAt(),
                        "shiftClosed", shift.getClosedAt(),
                        "cashier", cashierById.getOrDefault(shift.getCashierUserId(), "Unknown user"),
                        "expectedCash", money(shift.getExpectedCashAmount()),
                        "expectedUpi", money(shift.getExpectedUpiAmount()),
                        "expectedCard", money(shift.getExpectedCardAmount()),
                        "expectedOther", money(shift.getExpectedOtherAmount()),
                        "actualTotal", money(sum(safeMoney(shift.getActualCashAmount()), safeMoney(shift.getActualUpiAmount()), safeMoney(shift.getActualCardAmount()), safeMoney(shift.getActualOtherAmount()))),
                        "variance", money(shift.getVarianceAmount()),
                        "status", shift.getStatus()
                ))
                .toList();
    }

    public List<Map<String, Object>> pendingDues(UUID tenantId) {
        return billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null)).stream()
                .filter(bill -> bill.dueAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(bill -> row(
                        "billId", bill.id(),
                        "billNumber", bill.billNumber(),
                        "patientId", bill.patientId(),
                        "patientName", bill.patientName(),
                        "dueAmount", bill.dueAmount(),
                        "status", bill.status().name()
                ))
                .toList();
    }

    public List<Map<String, Object>> vaccinationsDue(UUID tenantId) {
        return vaccinationService.listDue(tenantId).stream()
                .map(record -> row(
                        "patientId", record.patientId(),
                        "patientName", record.patientName(),
                        "vaccineName", record.vaccineName(),
                        "givenDate", record.givenDate(),
                        "nextDueDate", record.nextDueDate(),
                        "doseNumber", record.doseNumber()
                ))
                .toList();
    }

    public List<Map<String, Object>> followUps(UUID tenantId) {
        return consultationService.list(tenantId).stream()
                .filter(record -> record.followUpDate() != null)
                .map(record -> row(
                        "consultationId", record.id(),
                        "patientId", record.patientId(),
                        "patientName", record.patientName(),
                        "doctorUserId", record.doctorUserId(),
                        "doctorName", record.doctorName(),
                        "followUpDate", record.followUpDate(),
                        "status", record.status().name()
                ))
                .toList();
    }

    public List<Map<String, Object>> lowStock(UUID tenantId) {
        return inventoryService.listLowStock(tenantId).stream()
                .map(record -> row(
                        "stockId", record.stockId(),
                        "medicineId", record.medicineId(),
                        "medicineName", record.medicineName(),
                        "batchNumber", record.batchNumber(),
                        "expiryDate", record.expiryDate(),
                        "quantityOnHand", record.quantityOnHand(),
                        "lowStockThreshold", record.lowStockThreshold()
                ))
                .toList();
    }

    public List<Map<String, Object>> prescriptions(UUID tenantId, LocalDate from, LocalDate to, UUID doctorUserId, UUID patientId) {
        return prescriptionService.list(tenantId).stream()
                .filter(record -> between(record.createdAt().toLocalDate(), from, to))
                .filter(record -> doctorUserId == null || doctorUserId.equals(record.doctorUserId()))
                .filter(record -> patientId == null || patientId.equals(record.patientId()))
                .map(record -> row(
                        "date", record.createdAt().toLocalDate(),
                        "prescriptionId", record.id(),
                        "prescriptionNumber", record.prescriptionNumber(),
                        "patientId", record.patientId(),
                        "patientName", record.patientName(),
                        "doctorUserId", record.doctorUserId(),
                        "doctorName", record.doctorName(),
                        "status", record.status().name()
                ))
                .toList();
    }

    private boolean between(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        if (to != null && value.isAfter(to)) {
            return false;
        }
        return true;
    }

    private Map<String, Object> row(Object... kv) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            row.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return row;
    }

    private RevenueTotals clinicRevenueTotals(UUID tenantId, LocalDate from, LocalDate to, UUID patientId, PaymentMode paymentMode) {
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        for (BillRecord bill : billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(patientId, null, null, null, null, null))) {
            if (!between(bill.billDate(), from, to)) {
                continue;
            }
            List<PaymentRecord> paymentsInRange = billingService.listPayments(tenantId, bill.id()).stream()
                    .filter(payment -> between(payment.paymentDate(), from, to))
                    .toList();
            BigDecimal totalPaymentAmount = paymentsInRange.stream()
                    .map(PaymentRecord::amount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            BigDecimal matchingPaymentAmount = paymentsInRange.stream()
                    .filter(payment -> matchesPaymentMode(payment.paymentMode(), paymentMode))
                    .map(PaymentRecord::amount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            if (paymentMode != null && matchingPaymentAmount.compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) <= 0) {
                continue;
            }
            BigDecimal allocationRatio = allocationRatio(paymentMode == null ? null : matchingPaymentAmount, totalPaymentAmount);
            grossRevenue = grossRevenue.add(prorateMoney(safeMoney(bill.subtotalAmount()), allocationRatio));
            discounts = discounts.add(prorateMoney(safeMoney(bill.discountAmount()), allocationRatio));
            tax = tax.add(prorateMoney(safeMoney(bill.taxAmount()), allocationRatio));
            revenue = revenue.add(paymentMode == null ? totalPaymentAmount : matchingPaymentAmount);
            for (RefundRecord refund : billingService.listRefunds(tenantId, bill.id())) {
                if (refund.refundedAt() == null || !between(refund.refundedAt().toLocalDate(), from, to)) {
                    continue;
                }
                if (!matchesPaymentMode(refund.refundMode(), paymentMode)) {
                    continue;
                }
                refunds = refunds.add(safeMoney(refund.amount()));
            }
        }
        return new RevenueTotals(grossRevenue, discounts, tax, revenue, refunds);
    }

    private RevenueTotals pharmacyRevenueTotals(UUID tenantId, LocalDate from, LocalDate to, UUID patientId, PaymentMode paymentMode) {
        List<PharmacySaleEntity> sales = pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(sale -> between(sale.getSaleDateTime() == null ? null : sale.getSaleDateTime().toLocalDate(), from, to))
                .filter(sale -> patientId == null || patientId.equals(sale.getPatientId()))
                .toList();
        BigDecimal grossRevenue = sales.stream().map(PharmacySaleEntity::getSubtotal).map(this::safeMoney).reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal discounts = sales.stream().map(PharmacySaleEntity::getDiscount).map(this::safeMoney).reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal tax = sales.stream().map(PharmacySaleEntity::getTax).map(this::safeMoney).reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        Collection<UUID> saleIdsOnly = sales.stream().map(PharmacySaleEntity::getId).toList();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        if (paymentMode != null) {
            grossRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            discounts = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        for (PharmacySaleEntity sale : sales) {
            List<PharmacySalePaymentEntity> paymentsInRange = pharmacySalePaymentRepository.findByTenantIdAndSaleIdOrderByCreatedAtAsc(tenantId, sale.getId()).stream()
                    .filter(payment -> between(payment.getPaymentDate(), from, to))
                    .toList();
            BigDecimal totalPaymentAmount = paymentsInRange.stream()
                    .map(PharmacySalePaymentEntity::getAmount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            BigDecimal matchingPaymentAmount = paymentsInRange.stream()
                    .filter(payment -> matchesPaymentMode(payment.getPaymentMode(), paymentMode))
                    .map(PharmacySalePaymentEntity::getAmount)
                    .map(this::safeMoney)
                    .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            if (paymentMode != null && matchingPaymentAmount.compareTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) <= 0) {
                continue;
            }
            BigDecimal allocationRatio = allocationRatio(paymentMode == null ? null : matchingPaymentAmount, totalPaymentAmount);
            if (paymentMode != null) {
                grossRevenue = grossRevenue.add(prorateMoney(safeMoney(sale.getSubtotal()), allocationRatio));
                discounts = discounts.add(prorateMoney(safeMoney(sale.getDiscount()), allocationRatio));
                tax = tax.add(prorateMoney(safeMoney(sale.getTax()), allocationRatio));
            }
            revenue = revenue.add(paymentMode == null ? totalPaymentAmount : matchingPaymentAmount);
        }
        for (PharmacySaleReturnEntity saleReturn : pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (!saleIdsOnly.contains(saleReturn.getSaleId())) {
                continue;
            }
            if (!between(saleReturn.getCreatedAt() == null ? null : saleReturn.getCreatedAt().toLocalDate(), from, to)) {
                continue;
            }
            if (!matchesPaymentMode(saleReturn.getRefundMode(), paymentMode)) {
                continue;
            }
            refunds = refunds.add(safeMoney(saleReturn.getRefundAmount()));
        }
        return new RevenueTotals(grossRevenue, discounts, tax, revenue, refunds);
    }

    private Map<String, Object> revenueRow(String source, RevenueTotals totals) {
        return row(
                "source", source,
                "clinicRevenue", money("CLINIC".equals(source) ? totals.revenueBeforeRefunds() : BigDecimal.ZERO),
                "pharmacyRevenue", money("PHARMACY".equals(source) ? totals.revenueBeforeRefunds() : BigDecimal.ZERO),
                "totalGrossRevenue", money(totals.grossRevenue()),
                "discounts", money(totals.discounts()),
                "tax", money(totals.tax()),
                "totalRevenue", money(totals.revenueBeforeRefunds()),
                "refunds", money(totals.refunds()),
                "netRevenue", money(totals.netRevenue())
        );
    }

    private PaymentModeBreakdown paymentModeAccumulator(Map<String, PaymentModeBreakdown> totals, String key) {
        return totals.computeIfAbsent(key, unused -> new PaymentModeBreakdown());
    }

    private List<CashCounterLedgerEntry> cashCounterLedgerEntries(UUID tenantId, LocalDate from, LocalDate to, String paymentMode, String source, String search) {
        PaymentMode paymentModeFilter = parsePaymentMode(paymentMode);
        CashCounterSourceFilter sourceFilter = parseCashCounterSource(source);

        List<BillRecord> bills = billingService.list(
                tenantId,
                new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null, null)
        );
        List<PharmacySaleEntity> sales = pharmacySaleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<PharmacyCashierShiftEntity> shifts = pharmacyCashierShiftRepository.findByTenantIdOrderByOpenedAtDesc(tenantId);

        Map<UUID, BillRecord> billById = bills.stream().collect(java.util.stream.Collectors.toMap(BillRecord::id, bill -> bill));
        Map<UUID, PharmacySaleEntity> saleById = sales.stream().collect(java.util.stream.Collectors.toMap(PharmacySaleEntity::getId, sale -> sale));
        Map<UUID, PharmacyCashierShiftEntity> shiftById = shifts.stream()
                .collect(java.util.stream.Collectors.toMap(PharmacyCashierShiftEntity::getId, shift -> shift, (left, right) -> left));

        List<UUID> userIds = new ArrayList<>();
        bills.forEach(bill -> {
            for (PaymentRecord payment : billingService.listPayments(tenantId, bill.id())) {
                userIds.add(payment.receivedBy());
            }
            for (RefundRecord refund : billingService.listRefunds(tenantId, bill.id())) {
                userIds.add(refund.refundedBy());
            }
        });
        pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).forEach(payment -> userIds.add(payment.getReceivedBy()));
        pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).forEach(returnRow -> userIds.add(returnRow.getCreatedBy()));

        Map<UUID, String> userById = userDisplayMap(tenantId, userIds);
        Map<UUID, String> patientById = patientDisplayMap(tenantId, sales.stream().map(PharmacySaleEntity::getPatientId).toList());

        List<CashCounterLedgerEntry> rows = new ArrayList<>();

        if (sourceFilter.includesClinicPayments() || sourceFilter.includesRefunds()) {
            for (BillRecord bill : bills) {
                List<PaymentRecord> payments = billingService.listPayments(tenantId, bill.id());
                Map<UUID, PaymentRecord> paymentById = payments.stream()
                        .collect(java.util.stream.Collectors.toMap(PaymentRecord::id, payment -> payment, (left, right) -> left));
                if (sourceFilter.includesClinicPayments()) {
                    for (PaymentRecord payment : payments) {
                        if (!between(payment.paymentDate(), from, to)) {
                            continue;
                        }
                        if (paymentModeFilter != null && payment.paymentMode() != paymentModeFilter) {
                            continue;
                        }
                        rows.add(new CashCounterLedgerEntry(
                                CashCounterSourceKind.CLINIC_BILL,
                                payment.paymentDateTime() == null ? payment.createdAt() : payment.paymentDateTime(),
                                "Clinic Bill",
                                safeString(bill.billNumber()),
                                safeString(payment.receiptNumber()),
                                patientCustomerLabel(bill.patientName(), bill.patientNumber()),
                                normalizePaymentModeLabel(payment.paymentMode() == null ? null : payment.paymentMode().name()),
                                safeMoney(payment.amount()),
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                safeMoney(payment.amount()),
                                userById.getOrDefault(payment.receivedBy(), "Unknown user"),
                                "",
                                bill.status() == null ? "PAID" : bill.status().name(),
                                ledgerSearchHaystack(
                                        bill.billNumber(),
                                        payment.receiptNumber(),
                                        bill.patientName(),
                                        bill.patientNumber(),
                                        payment.referenceNumber()
                                )
                        ));
                    }
                }
                if (sourceFilter.includesRefunds()) {
                    for (RefundRecord refund : billingService.listRefunds(tenantId, bill.id())) {
                        if (!between(refund.refundedAt() == null ? null : refund.refundedAt().toLocalDate(), from, to)) {
                            continue;
                        }
                        if (paymentModeFilter != null && refund.refundMode() != paymentModeFilter) {
                            continue;
                        }
                        PaymentRecord originalPayment = refund.paymentId() == null ? null : paymentById.get(refund.paymentId());
                        rows.add(new CashCounterLedgerEntry(
                                CashCounterSourceKind.REFUND,
                                refund.refundedAt() == null ? refund.createdAt() : refund.refundedAt(),
                                "Refund",
                                safeString(bill.billNumber()),
                                originalPayment == null ? "" : safeString(originalPayment.receiptNumber()),
                                patientCustomerLabel(bill.patientName(), bill.patientNumber()),
                                normalizePaymentModeLabel(refund.refundMode() == null ? null : refund.refundMode().name()),
                                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                                safeMoney(refund.amount()),
                                safeMoney(refund.amount()).negate().setScale(2, RoundingMode.HALF_UP),
                                userById.getOrDefault(refund.refundedBy(), "Unknown user"),
                                "",
                                "REFUNDED",
                                ledgerSearchHaystack(
                                        bill.billNumber(),
                                        originalPayment == null ? null : originalPayment.receiptNumber(),
                                        bill.patientName(),
                                        bill.patientNumber(),
                                        refund.reason()
                                )
                        ));
                    }
                }
            }
        }

        List<PharmacySalePaymentEntity> pharmacyPayments = pharmacySalePaymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<PharmacySaleReturnEntity> pharmacyReturns = pharmacySaleReturnRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (sourceFilter.includesPharmacyPayments()) {
            for (PharmacySalePaymentEntity payment : pharmacyPayments) {
                PharmacySaleEntity sale = saleById.get(payment.getSaleId());
                if (sale == null) {
                    continue;
                }
                if (!between(payment.getPaymentDate(), from, to)) {
                    continue;
                }
                if (paymentModeFilter != null && !paymentModeFilter.name().equalsIgnoreCase(payment.getPaymentMode())) {
                    continue;
                }
                rows.add(new CashCounterLedgerEntry(
                        CashCounterSourceKind.PHARMACY_POS,
                        payment.getPaymentDateTime() == null ? payment.getCreatedAt() : payment.getPaymentDateTime(),
                        "Pharmacy POS",
                        safeString(sale.getSaleNumber()),
                        safeString(payment.getReceiptNumber()),
                        customerOrPatient(sale, patientById),
                        normalizePaymentModeLabel(payment.getPaymentMode()),
                        safeMoney(payment.getAmount()),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        safeMoney(payment.getAmount()),
                        userById.getOrDefault(payment.getReceivedBy(), "Unknown user"),
                        shiftReference(shiftById.get(payment.getCashierShiftId())),
                        safeString(sale.getStatus()).isBlank() ? "PAID" : safeString(sale.getStatus()),
                        ledgerSearchHaystack(
                                sale.getSaleNumber(),
                                payment.getReceiptNumber(),
                                sale.getCustomerName(),
                                sale.getCustomerMobile(),
                                customerOrPatient(sale, patientById),
                                payment.getReferenceNumber()
                        )
                ));
            }
        }
        if (sourceFilter.includesReturns()) {
            for (PharmacySaleReturnEntity saleReturn : pharmacyReturns) {
                PharmacySaleEntity sale = saleById.get(saleReturn.getSaleId());
                if (sale == null) {
                    continue;
                }
                if (!between(saleReturn.getCreatedAt() == null ? null : saleReturn.getCreatedAt().toLocalDate(), from, to)) {
                    continue;
                }
                if (paymentModeFilter != null && saleReturn.getRefundMode() != null && !paymentModeFilter.name().equalsIgnoreCase(saleReturn.getRefundMode())) {
                    continue;
                }
                rows.add(new CashCounterLedgerEntry(
                        CashCounterSourceKind.RETURN,
                        saleReturn.getCreatedAt(),
                        "Return",
                        safeString(saleReturn.getReturnNumber()),
                        safeString(sale.getSaleNumber()),
                        customerOrPatient(sale, patientById),
                        normalizePaymentModeLabel(saleReturn.getRefundMode()),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        safeMoney(saleReturn.getRefundAmount()),
                        safeMoney(saleReturn.getRefundAmount()).negate().setScale(2, RoundingMode.HALF_UP),
                        userById.getOrDefault(saleReturn.getCreatedBy(), "Unknown user"),
                        "",
                        "RETURNED",
                        ledgerSearchHaystack(
                                saleReturn.getReturnNumber(),
                                sale.getSaleNumber(),
                                sale.getCustomerName(),
                                sale.getCustomerMobile(),
                                customerOrPatient(sale, patientById),
                                saleReturn.getReferenceNumber()
                        )
                ));
            }
        }

        String searchTerm = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(entry -> searchTerm.isBlank() || entry.searchText().contains(searchTerm))
                .sorted(Comparator.comparing(CashCounterLedgerEntry::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Map<UUID, String> userDisplayMap(UUID tenantId, Collection<UUID> ids) {
        List<UUID> filtered = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> display = new HashMap<>();
        for (AppUserEntity user : appUserRepository.findByTenantIdAndIdIn(tenantId, filtered)) {
            String value = user.getDisplayName();
            if (value == null || value.isBlank()) {
                value = user.getEmail();
            }
            display.put(user.getId(), value == null || value.isBlank() ? "Unknown user" : value);
        }
        return display;
    }

    private Map<UUID, String> patientDisplayMap(UUID tenantId, Collection<UUID> ids) {
        List<UUID> filtered = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> display = new HashMap<>();
        for (PatientEntity patient : patientRepository.findByTenantIdAndIdIn(tenantId, filtered)) {
            String fullName = ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " " + (patient.getLastName() == null ? "" : patient.getLastName())).trim();
            if (fullName.isBlank()) {
                fullName = patient.getPatientNumber();
            }
            display.put(patient.getId(), fullName == null || fullName.isBlank() ? "Unknown patient" : fullName);
        }
        return display;
    }

    private String customerOrPatient(PharmacySaleEntity sale, Map<UUID, String> patientById) {
        if (sale.getPatientId() != null) {
            return patientById.getOrDefault(sale.getPatientId(), "Unknown patient");
        }
        if (sale.getCustomerName() != null && !sale.getCustomerName().isBlank()) {
            return sale.getCustomerName() + (sale.getCustomerMobile() == null || sale.getCustomerMobile().isBlank() ? "" : " • " + sale.getCustomerMobile());
        }
        if (sale.getCustomerMobile() != null && !sale.getCustomerMobile().isBlank()) {
            return "Walk-in • " + sale.getCustomerMobile();
        }
        return "Walk-in";
    }

    private String resolveSalePaymentMode(List<PharmacySalePaymentEntity> payments) {
        if (payments.isEmpty()) {
            return "UNPAID";
        }
        List<String> modes = payments.stream()
                .map(PharmacySalePaymentEntity::getPaymentMode)
                .filter(mode -> mode != null && !mode.isBlank())
                .map(this::normalizePaymentModeLabel)
                .distinct()
                .toList();
        if (modes.isEmpty()) {
            return "OTHER";
        }
        if (modes.size() == 1) {
            return modes.getFirst();
        }
        return "MULTIPLE";
    }

    private boolean paymentModeMatches(String label, PaymentMode paymentMode) {
        return label != null && label.equalsIgnoreCase(paymentMode.name());
    }

    private boolean matchesPaymentMode(PaymentMode actual, PaymentMode filter) {
        return filter == null || actual == filter;
    }

    private boolean matchesPaymentMode(String actual, PaymentMode filter) {
        return filter == null || (actual != null && filter.name().equalsIgnoreCase(actual));
    }

    private BigDecimal allocationRatio(BigDecimal matchingAmount, BigDecimal totalAmount) {
        if (matchingAmount == null) {
            return BigDecimal.ONE.setScale(8, RoundingMode.HALF_UP);
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0 || matchingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return matchingAmount.divide(totalAmount, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal prorateMoney(BigDecimal amount, BigDecimal ratio) {
        if (ratio == null) {
            return safeMoney(amount);
        }
        return safeMoney(amount).multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizePaymentModeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CASH", "UPI", "CARD" -> normalized;
            default -> "OTHER";
        };
    }

    private PaymentMode parsePaymentMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CashCounterSourceFilter parseCashCounterSource(String value) {
        if (value == null || value.isBlank()) {
            return CashCounterSourceFilter.ALL;
        }
        try {
            return CashCounterSourceFilter.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CashCounterSourceFilter.ALL;
        }
    }

    private String shiftReference(PharmacyCashierShiftEntity shift) {
        if (shift == null || shift.getOpenedAt() == null) {
            return "";
        }
        return "Shift " + shift.getOpenedAt().toLocalDate() + " " + shift.getOpenedAt().toLocalTime().truncatedTo(ChronoUnit.MINUTES);
    }

    private String patientCustomerLabel(String patientName, String patientNumber) {
        if (patientName != null && !patientName.isBlank()) {
            return patientName;
        }
        if (patientNumber != null && !patientNumber.isBlank()) {
            return patientNumber;
        }
        return "Unknown patient";
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String ledgerSearchHaystack(String... values) {
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private ReportSource parseSource(String value) {
        if (value == null || value.isBlank()) {
            return ReportSource.ALL;
        }
        try {
            return ReportSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ReportSource.ALL;
        }
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return safeMoney(value);
    }

    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BigDecimal value : values) {
            total = total.add(safeMoney(value));
        }
        return total;
    }

    private record RevenueTotals(BigDecimal grossRevenue, BigDecimal discounts, BigDecimal tax, BigDecimal revenueBeforeRefunds, BigDecimal refunds) {
        static RevenueTotals zero() {
            BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return new RevenueTotals(zero, zero, zero, zero, zero);
        }

        RevenueTotals plus(RevenueTotals other) {
            return new RevenueTotals(
                    grossRevenue.add(other.grossRevenue),
                    discounts.add(other.discounts),
                    tax.add(other.tax),
                    revenueBeforeRefunds.add(other.revenueBeforeRefunds),
                    refunds.add(other.refunds)
            );
        }

        BigDecimal netRevenue() {
            return revenueBeforeRefunds.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static final class PaymentModeBreakdown {
        private int count;
        private BigDecimal grossAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal refundAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private void recordPayment(BigDecimal amount) {
            count += 1;
            grossAmount = grossAmount.add(amount);
        }

        private void recordRefund(BigDecimal amount) {
            refundAmount = refundAmount.add(amount);
        }

        private int count() { return count; }
        private BigDecimal grossAmount() { return grossAmount; }
        private BigDecimal refundAmount() { return refundAmount; }
        private BigDecimal netAmount() { return grossAmount.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP); }
    }

    private record CashCounterLedgerEntry(
            CashCounterSourceKind sourceKind,
            OffsetDateTime occurredAt,
            String sourceLabel,
            String businessReference,
            String receiptNumber,
            String patientCustomer,
            String paymentMode,
            BigDecimal grossAmount,
            BigDecimal refundAmount,
            BigDecimal netAmount,
            String cashier,
            String shiftReference,
            String status,
            String searchText
    ) {
    }

    private enum CashCounterSourceKind {
        CLINIC_BILL,
        PHARMACY_POS,
        REFUND,
        RETURN
    }

    private enum CashCounterSourceFilter {
        ALL,
        CLINIC,
        PHARMACY,
        REFUND,
        RETURN;

        boolean includesClinicPayments() {
            return this == ALL || this == CLINIC;
        }

        boolean includesPharmacyPayments() {
            return this == ALL || this == PHARMACY;
        }

        boolean includesRefunds() {
            return this == ALL || this == REFUND;
        }

        boolean includesReturns() {
            return this == ALL || this == RETURN;
        }
    }

    private enum ReportSource {
        ALL,
        CLINIC,
        PHARMACY;

        boolean includesClinic() {
            return this == ALL || this == CLINIC;
        }

        boolean includesPharmacy() {
            return this == ALL || this == PHARMACY;
        }
    }

    private static final class DailySalesAccumulator {
        private final LocalDate date;
        private final String source;
        private BigDecimal grossSales = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal refunds = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal cash = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal upi = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal card = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal other = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private DailySalesAccumulator(LocalDate date, String source) {
            this.date = date;
            this.source = source;
        }

        private void addPayment(String mode, BigDecimal amount) {
            switch (mode == null ? "OTHER" : mode.trim().toUpperCase(Locale.ROOT)) {
                case "CASH" -> cash = cash.add(amount);
                case "UPI" -> upi = upi.add(amount);
                case "CARD" -> card = card.add(amount);
                default -> other = other.add(amount);
            }
        }

        private BigDecimal netSales() {
            return grossSales.subtract(discount).add(tax).subtract(refunds).setScale(2, RoundingMode.HALF_UP);
        }

        private LocalDate date() { return date; }
        private String source() { return source; }
    }
}
