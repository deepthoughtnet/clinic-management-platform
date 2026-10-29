package com.deepthoughtnet.clinic.api.platform.communicationtest;

import java.util.UUID;

/** Call-scoped identity/trust assessment; identification alone is never trust. */
public record VoiceIdentityTrust(IdentityState identityState, TrustSource trustSource,
                                 boolean phoneBindingVerified, UUID tenantId, UUID patientId,
                                 String normalizedPhone, String callBinding) {
    public VoiceIdentityTrust {
        identityState = identityState == null ? IdentityState.GUEST : identityState;
        trustSource = trustSource == null ? TrustSource.NONE : trustSource;
        if (identityState != IdentityState.REGISTERED_PHONE_VERIFIED
                && identityState != IdentityState.OTP_VERIFIED) phoneBindingVerified = false;
        if (identityState == IdentityState.REGISTERED_PHONE_VERIFIED && trustSource != TrustSource.PREVERIFIED_PHONE)
            throw new IllegalArgumentException("Preverified identity requires its matching trust source");
        if (identityState == IdentityState.OTP_VERIFIED && trustSource != TrustSource.VOICE_OTP)
            throw new IllegalArgumentException("OTP-verified identity requires its matching trust source");
        if (identityState == IdentityState.REGISTERED_PHONE_VERIFIED
                && (!phoneBindingVerified || tenantId == null || patientId == null
                    || normalizedPhone == null || normalizedPhone.isBlank() || callBinding == null || callBinding.isBlank()))
            throw new IllegalArgumentException("Preverified phone trust must be bound to this tenant, patient, phone, and call");
    }

    public static VoiceIdentityTrust guest() {
        return new VoiceIdentityTrust(IdentityState.GUEST, TrustSource.NONE, false, null, null, null, null);
    }

    public static VoiceIdentityTrust registeredUnverified() {
        return new VoiceIdentityTrust(IdentityState.REGISTERED_UNVERIFIED, TrustSource.NONE, false, null, null, null, null);
    }

    public static VoiceIdentityTrust registeredPhoneVerified(UUID tenantId, UUID patientId, String normalizedPhone,
            String callBinding) {
        return new VoiceIdentityTrust(IdentityState.REGISTERED_PHONE_VERIFIED, TrustSource.PREVERIFIED_PHONE,
                true, tenantId, patientId, normalizedPhone, callBinding);
    }

    public static VoiceIdentityTrust otpVerified() {
        return new VoiceIdentityTrust(IdentityState.OTP_VERIFIED, TrustSource.VOICE_OTP, false,
                null, null, null, null);
    }

    public boolean matchesCall(UUID tenant, UUID patient, String phone, String call) {
        return identityState == IdentityState.REGISTERED_PHONE_VERIFIED && phoneBindingVerified
                && java.util.Objects.equals(tenantId, tenant) && java.util.Objects.equals(patientId, patient)
                && java.util.Objects.equals(normalizedPhone, phone) && java.util.Objects.equals(callBinding, call);
    }

    public enum IdentityState { GUEST, REGISTERED_UNVERIFIED, REGISTERED_PHONE_VERIFIED, OTP_VERIFIED }
    public enum TrustSource { NONE, PREVERIFIED_PHONE, VOICE_OTP }
}
