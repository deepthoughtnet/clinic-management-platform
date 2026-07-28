package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicProviderLocationResponse(
        String label,
        String address,
        String city,
        String state,
        String country,
        String pinCode,
        String workingHours,
        boolean parkingAvailable,
        boolean accessibilityAvailable
) {
}
