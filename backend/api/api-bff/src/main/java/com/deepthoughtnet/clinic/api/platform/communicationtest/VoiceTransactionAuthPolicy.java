package com.deepthoughtnet.clinic.api.platform.communicationtest;

/** Effective, safe-to-diagnose Voice authentication policy for one operation. */
public record VoiceTransactionAuthPolicy(
        boolean enabled,
        AuthenticationMode authenticationMode,
        int authTtlMinutes,
        boolean bookingAuthRequired,
        boolean lookupAuthRequired,
        boolean cancellationAuthRequired,
        boolean rescheduleAuthRequired,
        boolean testBypassEnabled,
        String effectiveScope,
        Operation requestedOperation,
        boolean authRequired,
        boolean transactionFeatureEnabled,
        String decision,
        boolean guestBookingEnabled,
        boolean minimalRegistrationEnabled,
        boolean guestBookingRequiresMobileConfirmation,
        int guestBookingCapabilityTtlMinutes,
        TrustMode trustMode,
        boolean bookingEnabled,
        boolean otpRequired
) {
    public enum AuthenticationMode { OTP_REQUIRED, DISABLED }
    public enum TrustMode { PREVERIFIED_PHONE_ALLOWED, OTP_REQUIRED, DISABLED }

    public enum Operation {
        BOOK_APPOINTMENT, VIEW_APPOINTMENTS, CANCEL_APPOINTMENT, RESCHEDULE_APPOINTMENT;

        public static Operation fromIntent(String intent) {
            if (intent == null) return null;
            return switch (intent.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "BOOK_APPOINTMENT" -> BOOK_APPOINTMENT;
                case "FIND_APPOINTMENTS", "CHECK_APPOINTMENT" -> VIEW_APPOINTMENTS;
                case "CANCEL_APPOINTMENT" -> CANCEL_APPOINTMENT;
                case "RESCHEDULE_APPOINTMENT" -> RESCHEDULE_APPOINTMENT;
                default -> null;
            };
        }
    }

    public boolean challengeAllowed() {
        return enabled && otpRequired && authRequired;
    }

    public boolean preverifiedPhoneAllowed() {
        return enabled && trustMode == TrustMode.PREVERIFIED_PHONE_ALLOWED;
    }

    /** New-booking feature gate; caller scope is separately enforced by BookingExecutionContext. */
    public boolean bookingExecutionAllowed() {
        return enabled && bookingEnabled;
    }
}
