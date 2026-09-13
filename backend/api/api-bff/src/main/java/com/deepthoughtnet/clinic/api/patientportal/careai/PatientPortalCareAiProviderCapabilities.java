package com.deepthoughtnet.clinic.api.patientportal.careai;

record PatientPortalCareAiProviderCapabilities(
        boolean onlineBooking,
        boolean liveAvailability,
        boolean callToBook,
        boolean publicPhoneAvailable,
        boolean publicProfileAvailable,
        boolean careAuthorized,
        boolean publiclyPublished
) {
    static PatientPortalCareAiProviderCapabilities from(String bookingMode,
                                                        boolean careAuthorized,
                                                        boolean publiclyPublished,
                                                        boolean publicPhoneAvailable,
                                                        boolean publicProfileAvailable) {
        boolean callToBook = bookingMode != null && bookingMode.toUpperCase(java.util.Locale.ROOT).contains("CALL_TO_BOOK");
        return new PatientPortalCareAiProviderCapabilities(
                !callToBook,
                !callToBook,
                callToBook,
                publicPhoneAvailable,
                publicProfileAvailable,
                careAuthorized,
                publiclyPublished
        );
    }
}
