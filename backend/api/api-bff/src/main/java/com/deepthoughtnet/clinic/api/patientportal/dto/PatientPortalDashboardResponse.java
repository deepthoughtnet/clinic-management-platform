package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.math.BigDecimal;

public record PatientPortalDashboardResponse(
        String patientDisplayName,
        String patientNumber,
        String clinicName,
        PatientPortalAppointmentResponse nextAppointment,
        PatientPortalPrescriptionResponse recentPrescription,
        BigDecimal unpaidDueAmount,
        PatientPortalBillResponse latestBill
) {
}
