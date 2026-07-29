package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.math.BigDecimal;

public record PublicProviderLocationResponse(
        String label,
        String address,
        String city,
        String state,
        String country,
        String pinCode,
        String workingHours,
        boolean parkingAvailable,
        boolean accessibilityAvailable,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
