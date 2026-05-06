package com.deepthoughtnet.clinic.api.reports;

import com.deepthoughtnet.clinic.api.dashboard.dto.DashboardSummaryResponse;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
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
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public ReportingFacade(
            AppointmentService appointmentService,
            ConsultationService consultationService,
            BillingService billingService,
            VaccinationService vaccinationService,
            PrescriptionService prescriptionService,
            InventoryService inventoryService,
            PatientRepository patientRepository
    ) {
        this.appointmentService = appointmentService;
        this.consultationService = consultationService;
        this.billingService = billingService;
        this.vaccinationService = vaccinationService;
        this.prescriptionService = prescriptionService;
        this.inventoryService = inventoryService;
        this.patientRepository = patientRepository;
    }

    public DashboardSummaryResponse dashboardSummary(UUID tenantId) {
        List<com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord> todayAppointments = appointmentService.listToday(tenantId);
        List<ConsultationRecord> consultations = consultationService.list(tenantId);
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null));
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
        return new DashboardSummaryResponse(
                todayAppointments.size(),
                todayAppointments.stream().filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.WAITING).count(),
                todayAppointments.stream().filter(appointment -> appointment.status() == com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus.IN_CONSULTATION).count(),
                consultations.stream().filter(record -> record.status() == ConsultationStatus.COMPLETED).count(),
                todayRevenue.setScale(2, RoundingMode.HALF_UP),
                pendingDues,
                followUpsDue,
                vaccinationService.listDue(tenantId).size(),
                lowStockMedicines
        );
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
        List<BillRecord> bills = billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(patientId, null));
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
        for (BillRecord bill : billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null))) {
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
        return billingService.list(tenantId, new com.deepthoughtnet.clinic.billing.service.model.BillingSearchCriteria(null, null)).stream()
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
