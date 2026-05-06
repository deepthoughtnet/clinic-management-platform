package com.deepthoughtnet.clinic.api.patient.dto;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationResponse;
import java.util.List;

public record PatientDetailResponse(
        PatientResponse patient,
        List<AppointmentSummaryResponse> upcomingAppointments,
        List<AppointmentSummaryResponse> recentAppointments,
        List<ConsultationResponse> previousConsultations
) {
}
