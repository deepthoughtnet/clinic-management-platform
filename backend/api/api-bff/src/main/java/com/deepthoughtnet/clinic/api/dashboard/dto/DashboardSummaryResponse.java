package com.deepthoughtnet.clinic.api.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        long todayAppointments,
        long waitingPatients,
        long inConsultationCount,
        long completedConsultations,
        BigDecimal todayRevenue,
        BigDecimal pendingDues,
        long followUpsDue,
        long vaccinationsDue,
        long lowStockMedicines,
        long pendingNotifications,
        long failedNotifications,
        long sentNotificationsToday
) {
}
