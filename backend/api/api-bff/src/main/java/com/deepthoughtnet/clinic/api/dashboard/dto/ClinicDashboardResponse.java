package com.deepthoughtnet.clinic.api.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicDashboardResponse(
        LocalDate startDate,
        LocalDate endDate,
        UUID tenantId,
        AppointmentSummary appointmentSummary,
        QueueSummary queueSummary,
        ConsultationSummary consultationSummary,
        PrescriptionSummary prescriptionSummary,
        BillingSummary billingSummary,
        FollowUpSummary followUpSummary,
        List<DoctorSummary> doctorSummaries,
        List<QueueItem> currentWaitingList,
        List<PendingBillItem> recentUnpaidBills,
        List<RecentActivityItem> recentActivity
) {
    public record AppointmentSummary(
            long totalToday,
            long scheduled,
            long checkedIn,
            long inConsultation,
            long completed,
            long noShow,
            long cancelled
    ) { }

    public record QueueSummary(
            long waiting,
            long inConsultation,
            long completed,
            long noShow,
            long cancelled,
            long averageWaitTimeMinutes
    ) { }

    public record ConsultationSummary(
            long started,
            long completed,
            long activeNow,
            long consultationsWithPrescriptions
    ) { }

    public record PrescriptionSummary(
            long prescriptionsGenerated,
            long consultationsWithPrescriptions,
            BigDecimal avgPrescriptionsPerConsultation
    ) { }

    public record BillingSummary(
            long billsCreated,
            long paidBills,
            long pendingBills,
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal pendingAmount
    ) { }

    public record DoctorSummary(
            UUID doctorUserId,
            String doctorName,
            long appointmentsToday,
            long checkedIn,
            long completed,
            long noShow,
            long cancelled,
            LocalTime nextAppointmentTime,
            BigDecimal revenue,
            long prescriptionsGenerated,
            long consultationsCompleted,
            BigDecimal avgConsultationLoad
    ) { }

    public record QueueItem(
            UUID appointmentId,
            UUID patientId,
            String patientName,
            String patientNumber,
            UUID doctorUserId,
            String doctorName,
            Integer tokenNumber,
            LocalTime appointmentTime,
            OffsetDateTime waitingSince,
            String status
    ) { }

    public record PendingBillItem(
            UUID billId,
            String billNumber,
            UUID patientId,
            String patientName,
            BigDecimal dueAmount,
            LocalDate billDate,
            String status
    ) { }

    public record FollowUpSummary(
            long dueInRange,
            long overdue,
            long upcomingNext7Days
    ) { }

    public record RecentActivityItem(
            OffsetDateTime timestamp,
            String type,
            String title,
            String description,
            String relatedPatientName,
            String relatedDoctorName
    ) { }
}
