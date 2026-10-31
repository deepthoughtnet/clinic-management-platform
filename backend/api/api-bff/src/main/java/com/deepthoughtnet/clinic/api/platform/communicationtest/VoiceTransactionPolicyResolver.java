package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.AuthenticationMode;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.Operation;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Central clinic → tenant → application policy resolver. */
@Component
public class VoiceTransactionPolicyResolver {
    private static final int DEFAULT_TTL_MINUTES = 15;
    private final VoiceTransactionAuthProperties properties;
    private final VoiceOperatorProperties operatorProperties;

    public VoiceTransactionPolicyResolver(VoiceTransactionAuthProperties properties) {
        this(properties, new VoiceOperatorProperties());
    }

    @Autowired
    public VoiceTransactionPolicyResolver(VoiceTransactionAuthProperties properties,
            VoiceOperatorProperties operatorProperties) {
        this.properties = properties == null ? new VoiceTransactionAuthProperties() : properties;
        this.operatorProperties = operatorProperties == null ? new VoiceOperatorProperties() : operatorProperties;
    }

    public VoiceTransactionAuthPolicy resolve(UUID tenantId, UUID clinicId, Operation operation) {
        VoiceTransactionAuthProperties.PolicyOverride app = properties.getDefaultPolicy();
        VoiceTransactionAuthProperties.PolicyOverride tenant = find(properties.getTenantOverrides(), tenantId);
        VoiceTransactionAuthProperties.PolicyOverride clinic = find(properties.getClinicOverrides(), clinicId);
        boolean bookingEnabled = value(clinic == null ? null : clinic.getBookingEnabled(),
                tenant == null ? null : tenant.getBookingEnabled(), operatorProperties.getBookingEnabled(),
                app.getBookingEnabled(), false);
        boolean guestBooking = value(clinic == null ? null : clinic.getGuestBookingEnabled(),
                tenant == null ? null : tenant.getGuestBookingEnabled(), operatorProperties.getGuestBookingEnabled(),
                app.getGuestBookingEnabled(), false);
        boolean quickRegistration = value(clinic == null ? null : clinic.getVoiceQuickRegistrationEnabled(),
                tenant == null ? null : tenant.getVoiceQuickRegistrationEnabled(),
                app.getVoiceQuickRegistrationEnabled(), false);
        boolean otpRequired = resolveOtpRequired(clinic, tenant, app);
        boolean configuredEnabled = value(clinic == null ? null : clinic.getEnabled(),
                tenant == null ? null : tenant.getEnabled(), app.getEnabled(), false);
        // The legacy master switch remains compatible, while the simple operation flags
        // are sufficient to activate their own guarded Voice path.
        boolean enabled = configuredEnabled || bookingEnabled || guestBooking || quickRegistration;
        AuthenticationMode mode = otpRequired ? AuthenticationMode.OTP_REQUIRED : AuthenticationMode.DISABLED;
        VoiceTransactionAuthPolicy.TrustMode configuredTrustMode = value(
                clinic == null ? null : clinic.getTrustMode(), tenant == null ? null : tenant.getTrustMode(),
                app.getTrustMode(), null);
        VoiceTransactionAuthPolicy.TrustMode trustMode = configuredTrustMode != null ? configuredTrustMode
                : otpRequired ? VoiceTransactionAuthPolicy.TrustMode.OTP_REQUIRED
                : VoiceTransactionAuthPolicy.TrustMode.DISABLED;
        int ttl = boundedTtl(value(clinic == null ? null : clinic.getAuthTtlMinutes(),
                tenant == null ? null : tenant.getAuthTtlMinutes(), app.getAuthTtlMinutes(), DEFAULT_TTL_MINUTES));
        boolean booking = value(clinic == null ? null : clinic.getBookingAuthRequired(),
                tenant == null ? null : tenant.getBookingAuthRequired(), app.getBookingAuthRequired(), true);
        boolean lookup = value(clinic == null ? null : clinic.getLookupAuthRequired(),
                tenant == null ? null : tenant.getLookupAuthRequired(), app.getLookupAuthRequired(), true);
        boolean cancellation = value(clinic == null ? null : clinic.getCancellationAuthRequired(),
                tenant == null ? null : tenant.getCancellationAuthRequired(), app.getCancellationAuthRequired(), true);
        boolean reschedule = value(clinic == null ? null : clinic.getRescheduleAuthRequired(),
                tenant == null ? null : tenant.getRescheduleAuthRequired(), app.getRescheduleAuthRequired(), true);
        boolean minimalRegistration = value(clinic == null ? null : clinic.getMinimalRegistrationEnabled(),
                tenant == null ? null : tenant.getMinimalRegistrationEnabled(), app.getMinimalRegistrationEnabled(), true);
        boolean confirmMobile = value(clinic == null ? null : clinic.getGuestBookingRequiresMobileConfirmation(),
                tenant == null ? null : tenant.getGuestBookingRequiresMobileConfirmation(),
                app.getGuestBookingRequiresMobileConfirmation(), true);
        int guestTtl = boundedGuestTtl(value(clinic == null ? null : clinic.getGuestBookingCapabilityTtlMinutes(),
                tenant == null ? null : tenant.getGuestBookingCapabilityTtlMinutes(),
                app.getGuestBookingCapabilityTtlMinutes(), 15));
        boolean required = operation != null && switch (operation) {
            case BOOK_APPOINTMENT -> booking;
            case VIEW_APPOINTMENTS -> lookup;
            case CANCEL_APPOINTMENT -> cancellation;
            case RESCHEDULE_APPOINTMENT -> reschedule;
        };
        String scope = hasAny(clinic) ? "CLINIC_OVERRIDE"
                : hasAny(tenant) ? "TENANT_OVERRIDE" : "APPLICATION_DEFAULT";
        String decision = !enabled ? "AUTHENTICATION_DISABLED"
                : operation == null ? "NO_TRANSACTION_OPERATION"
                : operation == Operation.BOOK_APPOINTMENT && !bookingEnabled ? "BOOKING_FEATURE_DISABLED"
                : operation == Operation.BOOK_APPOINTMENT ? "RESTRICTED_NEW_BOOKING_ONLY"
                : trustMode == VoiceTransactionAuthPolicy.TrustMode.PREVERIFIED_PHONE_ALLOWED && required
                ? "PREVERIFIED_PHONE_EVIDENCE_REQUIRED"
                : !required ? "AUTH_NOT_REQUIRED_TRANSACTION_STILL_DISABLED" : "OTP_AUTHENTICATION_REQUIRED";
        boolean operationExecutionEnabled = operation == Operation.BOOK_APPOINTMENT && bookingEnabled;
        return new VoiceTransactionAuthPolicy(enabled, mode, ttl, booking, lookup, cancellation, reschedule,
                false, scope, operation, required, operationExecutionEnabled, decision, guestBooking, minimalRegistration,
                confirmMobile, guestTtl, trustMode, bookingEnabled, otpRequired, quickRegistration);
    }

    private VoiceTransactionAuthProperties.PolicyOverride find(
            java.util.Map<String, VoiceTransactionAuthProperties.PolicyOverride> overrides, UUID id) {
        if (id == null || overrides == null) return null;
        VoiceTransactionAuthProperties.PolicyOverride direct = overrides.get(id.toString());
        if (direct != null) return direct;
        return overrides.entrySet().stream().filter(entry -> entry.getKey() != null
                && entry.getKey().equalsIgnoreCase(id.toString())).map(java.util.Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static int boundedTtl(Integer value) {
        return Math.max(5, Math.min(30, value == null ? DEFAULT_TTL_MINUTES : value));
    }

    private static int boundedGuestTtl(Integer value) {
        return Math.max(5, Math.min(30, value == null ? DEFAULT_TTL_MINUTES : value));
    }

    private static boolean hasAny(VoiceTransactionAuthProperties.PolicyOverride value) {
        return value != null && (value.getEnabled() != null || value.getAuthenticationMode() != null
                || value.getAuthTtlMinutes() != null || value.getBookingAuthRequired() != null
                || value.getTrustMode() != null
                || value.getLookupAuthRequired() != null || value.getCancellationAuthRequired() != null
                || value.getRescheduleAuthRequired() != null || value.getGuestBookingEnabled() != null
                || value.getMinimalRegistrationEnabled() != null
                || value.getGuestBookingRequiresMobileConfirmation() != null
                || value.getGuestBookingCapabilityTtlMinutes() != null || value.getBookingEnabled() != null
                || value.getOtpRequired() != null || value.getVoiceQuickRegistrationEnabled() != null);
    }

    private boolean resolveOtpRequired(VoiceTransactionAuthProperties.PolicyOverride clinic,
            VoiceTransactionAuthProperties.PolicyOverride tenant, VoiceTransactionAuthProperties.PolicyOverride app) {
        if (clinic != null && clinic.getOtpRequired() != null) return clinic.getOtpRequired();
        Boolean clinicLegacy = legacyOtpValue(clinic);
        if (clinicLegacy != null) return clinicLegacy;
        if (tenant != null && tenant.getOtpRequired() != null) return tenant.getOtpRequired();
        Boolean tenantLegacy = legacyOtpValue(tenant);
        if (tenantLegacy != null) return tenantLegacy;
        if (operatorProperties.getOtpRequired() != null) return operatorProperties.getOtpRequired();
        if (app.getOtpRequired() != null) return app.getOtpRequired();
        Boolean applicationLegacy = legacyOtpValue(app);
        return applicationLegacy == null || applicationLegacy;
    }

    private static Boolean legacyOtpValue(VoiceTransactionAuthProperties.PolicyOverride policy) {
        if (policy == null || (policy.getAuthenticationMode() == null && policy.getTrustMode() == null)) return null;
        return policy.getAuthenticationMode() == AuthenticationMode.OTP_REQUIRED
                || policy.getTrustMode() == VoiceTransactionAuthPolicy.TrustMode.OTP_REQUIRED;
    }

    private static <T> T value(T clinic, T tenant, T app, T fallback) {
        return clinic != null ? clinic : tenant != null ? tenant : app != null ? app : fallback;
    }

    private static <T> T value(T clinic, T tenant, T operator, T app, T fallback) {
        return clinic != null ? clinic : tenant != null ? tenant : operator != null ? operator
                : app != null ? app : fallback;
    }
}
