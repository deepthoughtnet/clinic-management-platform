package com.deepthoughtnet.clinic.discover.verification;

public record VerificationDeliveryResult(
        boolean accepted,
        String providerName,
        String deliveryReference,
        String developmentCode,
        String message
) {
    public static VerificationDeliveryResult accepted(String providerName, String deliveryReference, String developmentCode, String message) {
        return new VerificationDeliveryResult(true, providerName, deliveryReference, developmentCode, message);
    }

    public static VerificationDeliveryResult unavailable(String providerName, String message) {
        return new VerificationDeliveryResult(false, providerName, null, null, message);
    }
}
