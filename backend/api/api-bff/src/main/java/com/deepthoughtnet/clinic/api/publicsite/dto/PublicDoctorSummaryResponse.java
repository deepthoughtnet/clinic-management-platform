package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicDoctorSummaryResponse(
        String doctorDisplayName,
        String clinicDisplayName,
        String city,
        String speciality,
        BigDecimal consultationFee,
        List<String> languages,
        String nextAvailableSlotSummary
) {
}
