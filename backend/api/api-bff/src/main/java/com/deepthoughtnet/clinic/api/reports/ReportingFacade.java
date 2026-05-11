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
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.LowStockRecord;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final PatientRepository patientRepository;
    private final NotificationCenterService notificationCenterService;

    public ReportingFacade(
            AppointmentService appointmentService,
            ConsultationService consultationService,
            BillingService billingService,
            VaccinationService vaccinationService,
            PrescriptionService prescriptionService,
            InventoryService inventoryService,
            PatientRepository patientRepository,
            NotificationCenterService notificationCenterService
    ) {
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.billingService = billingService;
        this.vaccinationService = vaccinationService;
        this.prescriptionService = prescriptionService;
        this.inventoryService = inventoryService;
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
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null));
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
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null));
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

    public List<Map<String, Object>> revenue(UUID tenantId, LocalDate from, LocalDate to, UUID doctorUserId, UUID patientId) {
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(patientId, null, null, null, null));
        return bills.stream()
                .filter(bill -> between(bill.billDate(), from, to))
                .map(bill -> row(
                        "date", bill.billDate(),
                        "billId", bill.id(),
                        "billNumber", bill.billNumber(),
                        "patientId", bill.patientId(),
                        "patientName", bill.patientName(),
                        "totalAmount", bill.totalAmount(),
                        "paidAmount", bill.paidAmount(),
                        "dueAmount", bill.dueAmount()
                ))
                .toList();
    }

    public List<Map<String, Object>> paymentModes(UUID tenantId, LocalDate from, LocalDate to) {
        Map<PaymentMode, BigDecimal> totals = new LinkedHashMap<>();
        for (BillRecord bill : billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null))) {
            for (PaymentRecord payment : billingService.listPayments(tenantId, bill.id())) {
                if (payment.createdAt() != null && between(payment.createdAt().toLocalDate(), from, to)) {
                    totals.merge(payment.paymentMode(), payment.amount(), BigDecimal::add);
                }
            }
        }
        return totals.entrySet().stream()
                .map(entry -> row("paymentMode", entry.getKey(), "amount", entry.getValue()))
                .toList();
    }

    public List<Map<String, Object>> pendingDues(UUID tenantId) {
        return billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null, null, null, null)).stream()
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
}
