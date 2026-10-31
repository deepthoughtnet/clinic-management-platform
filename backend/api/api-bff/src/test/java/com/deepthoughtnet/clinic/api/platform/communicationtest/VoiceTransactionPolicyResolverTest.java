package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceConversationAdapter;
import com.deepthoughtnet.clinic.api.patientportal.auth.PatientPortalOtpService;
import com.deepthoughtnet.clinic.api.patientportal.careai.HealthcareAiReadOnlyToolGateway;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.AuthenticationMode;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.Operation;
import com.deepthoughtnet.clinic.api.platform.communicationtest.VoiceTransactionAuthPolicy.TrustMode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class VoiceTransactionPolicyResolverTest {
    @Test
    void defaultsFailClosedAndRequireAuthenticationForEveryOperation() {
        VoiceTransactionAuthPolicy policy = new VoiceTransactionPolicyResolver(new VoiceTransactionAuthProperties())
                .resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT);

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.authenticationMode()).isEqualTo(AuthenticationMode.OTP_REQUIRED);
        assertThat(policy.trustMode()).isEqualTo(TrustMode.OTP_REQUIRED);
        assertThat(policy.authTtlMinutes()).isEqualTo(15);
        assertThat(policy.authRequired()).isTrue();
        assertThat(policy.bookingAuthRequired()).isTrue();
        assertThat(policy.lookupAuthRequired()).isTrue();
        assertThat(policy.cancellationAuthRequired()).isTrue();
        assertThat(policy.rescheduleAuthRequired()).isTrue();
        assertThat(policy.testBypassEnabled()).isFalse();
        assertThat(policy.transactionFeatureEnabled()).isFalse();
        assertThat(policy.bookingEnabled()).isFalse();
        assertThat(policy.otpRequired()).isTrue();
        assertThat(policy.bookingExecutionAllowed()).isFalse();
        assertThat(policy.challengeAllowed()).isFalse();
        assertThat(policy.guestBookingEnabled()).isFalse();
        assertThat(policy.voiceQuickRegistrationEnabled()).isFalse();
        assertThat(policy.minimalRegistrationEnabled()).isTrue();
        assertThat(policy.guestBookingRequiresMobileConfirmation()).isTrue();
        assertThat(policy.guestBookingCapabilityTtlMinutes()).isEqualTo(15);
        assertThat(policy.decision()).isEqualTo("AUTHENTICATION_DISABLED");
    }

    @Test
    void preverifiedPhoneModeIsExplicitAndRespectsClinicTenantApplicationPrecedence() {
        UUID tenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(TrustMode.DISABLED);
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setTrustMode(TrustMode.OTP_REQUIRED);
        VoiceTransactionAuthProperties.PolicyOverride clinic = new VoiceTransactionAuthProperties.PolicyOverride();
        clinic.setTrustMode(TrustMode.PREVERIFIED_PHONE_ALLOWED);
        clinic.setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));
        properties.setClinicOverrides(Map.of(clinicId.toString(), clinic));

        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);
        var clinicPolicy = resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT);
        var tenantPolicy = resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT);
        var defaultPolicy = resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT);

        assertThat(clinicPolicy.preverifiedPhoneAllowed()).isTrue();
        assertThat(clinicPolicy.challengeAllowed()).isTrue();
        assertThat(tenantPolicy.trustMode()).isEqualTo(TrustMode.OTP_REQUIRED);
        assertThat(tenantPolicy.challengeAllowed()).isTrue();
        assertThat(defaultPolicy.trustMode()).isEqualTo(TrustMode.DISABLED);
        assertThat(defaultPolicy.preverifiedPhoneAllowed()).isFalse();
        assertThat(clinicPolicy.transactionFeatureEnabled()).isFalse();
    }

    @Test
    void legacyOtpAuthenticationModeStillMapsToOtpTrustMode() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        var policy = new VoiceTransactionPolicyResolver(properties).resolve(null, null, Operation.BOOK_APPOINTMENT);
        assertThat(policy.trustMode()).isEqualTo(TrustMode.OTP_REQUIRED);
        assertThat(policy.challengeAllowed()).isTrue();
        assertThat(policy.otpRequired()).isTrue();
    }

    @Test
    void simpleOperatorFlagsEnableOnlyTheSelectedBookingAndIdentityPaths() {
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(false);
        operator.setGuestBookingEnabled(true);
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(
                new VoiceTransactionAuthProperties(), operator);
        VoiceTransactionAuthPolicy booking = resolver.resolve(null, null, Operation.BOOK_APPOINTMENT);

        assertThat(booking.bookingEnabled()).isTrue();
        assertThat(booking.enabled()).isTrue();
        assertThat(booking.otpRequired()).isFalse();
        assertThat(booking.challengeAllowed()).isFalse();
        assertThat(booking.bookingExecutionAllowed()).isTrue();
        assertThat(booking.guestBookingEnabled()).isTrue();
        assertThat(booking.minimalRegistrationEnabled()).isTrue();
        assertThat(resolver.resolve(null, null, Operation.VIEW_APPOINTMENTS).transactionFeatureEnabled()).isFalse();
    }

    @Test
    void clinicAndTenantOtpOverridesBeatTheSimpleApplicationSetting() {
        UUID tenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setOtpRequired(false);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setOtpRequired(true);
        VoiceTransactionAuthProperties.PolicyOverride clinic = new VoiceTransactionAuthProperties.PolicyOverride();
        clinic.setOtpRequired(false);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));
        properties.setClinicOverrides(Map.of(clinicId.toString(), clinic));
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties, operator);

        assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).otpRequired()).isTrue();
        assertThat(resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT).otpRequired()).isFalse();
    }

    @Test
    void legacyScopedOtpModeRemainsAClinicTenantOverrideWhenSimpleApplicationFlagIsSet() {
        UUID tenantId = UUID.randomUUID();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setOtpRequired(false);
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));

        assertThat(new VoiceTransactionPolicyResolver(properties, operator)
                .resolve(tenantId, null, Operation.BOOK_APPOINTMENT).otpRequired()).isTrue();
    }

    @Test
    void clinicFieldsOverrideTenantAndTenantFieldsOverrideApplicationIndependently() {
        UUID tenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        properties.getDefaultPolicy().setAuthTtlMinutes(18);
        properties.getDefaultPolicy().setBookingAuthRequired(false);
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setAuthTtlMinutes(21);
        tenant.setLookupAuthRequired(false);
        VoiceTransactionAuthProperties.PolicyOverride clinic = new VoiceTransactionAuthProperties.PolicyOverride();
        clinic.setAuthTtlMinutes(27);
        clinic.setBookingAuthRequired(true);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));
        properties.setClinicOverrides(Map.of(clinicId.toString(), clinic));

        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);
        VoiceTransactionAuthPolicy clinicPolicy = resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT);
        VoiceTransactionAuthPolicy tenantOnly = resolver.resolve(tenantId, null, Operation.VIEW_APPOINTMENTS);
        VoiceTransactionAuthPolicy isolated = resolver.resolve(UUID.randomUUID(), null, Operation.VIEW_APPOINTMENTS);

        assertThat(clinicPolicy.challengeAllowed()).isTrue();
        assertThat(clinicPolicy.effectiveScope()).isEqualTo("CLINIC_OVERRIDE");
        assertThat(clinicPolicy.authTtlMinutes()).isEqualTo(27);
        assertThat(clinicPolicy.authRequired()).isTrue();
        assertThat(tenantOnly.effectiveScope()).isEqualTo("TENANT_OVERRIDE");
        assertThat(tenantOnly.authTtlMinutes()).isEqualTo(21);
        assertThat(tenantOnly.authRequired()).isFalse();
        assertThat(isolated.effectiveScope()).isEqualTo("APPLICATION_DEFAULT");
        assertThat(isolated.authRequired()).isTrue();
    }

    @Test
    void allOperationsResolveTheirOwnAuthenticationFlag() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        var defaults = properties.getDefaultPolicy();
        defaults.setEnabled(true);
        defaults.setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        defaults.setBookingAuthRequired(false);
        defaults.setLookupAuthRequired(true);
        defaults.setCancellationAuthRequired(false);
        defaults.setRescheduleAuthRequired(true);
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);

        assertThat(resolver.resolve(null, null, Operation.BOOK_APPOINTMENT).authRequired()).isFalse();
        assertThat(resolver.resolve(null, null, Operation.VIEW_APPOINTMENTS).authRequired()).isTrue();
        assertThat(resolver.resolve(null, null, Operation.CANCEL_APPOINTMENT).authRequired()).isFalse();
        assertThat(resolver.resolve(null, null, Operation.RESCHEDULE_APPOINTMENT).authRequired()).isTrue();
        assertThat(resolver.resolve(null, null, Operation.VIEW_APPOINTMENTS).challengeAllowed()).isTrue();
        assertThat(resolver.resolve(null, null, Operation.BOOK_APPOINTMENT).challengeAllowed()).isFalse();
    }

    @Test
    void ttlIsClampedToFiveThroughThirtyMinutes() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setAuthTtlMinutes(1);
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);
        assertThat(resolver.resolve(null, null, Operation.BOOK_APPOINTMENT).authTtlMinutes()).isEqualTo(5);
        properties.getDefaultPolicy().setAuthTtlMinutes(90);
        assertThat(resolver.resolve(null, null, Operation.BOOK_APPOINTMENT).authTtlMinutes()).isEqualTo(30);
    }

    @Test
    void guestBookingPolicyUsesClinicTenantApplicationPrecedenceAndFailClosedDefaults() {
        UUID tenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        var defaults = properties.getDefaultPolicy();
        defaults.setGuestBookingEnabled(true);
        defaults.setMinimalRegistrationEnabled(true);
        defaults.setGuestBookingCapabilityTtlMinutes(12);
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setGuestBookingRequiresMobileConfirmation(false);
        VoiceTransactionAuthProperties.PolicyOverride clinic = new VoiceTransactionAuthProperties.PolicyOverride();
        clinic.setGuestBookingEnabled(false);
        clinic.setGuestBookingCapabilityTtlMinutes(60);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));
        properties.setClinicOverrides(Map.of(clinicId.toString(), clinic));

        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);
        var effective = resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT);
        var tenantOnly = resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT);
        var isolated = resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT);

        assertThat(effective.guestBookingEnabled()).isFalse();
        assertThat(effective.minimalRegistrationEnabled()).isTrue();
        assertThat(effective.guestBookingRequiresMobileConfirmation()).isFalse();
        assertThat(effective.guestBookingCapabilityTtlMinutes()).isEqualTo(30);
        assertThat(effective.effectiveScope()).isEqualTo("CLINIC_OVERRIDE");
        assertThat(tenantOnly.guestBookingEnabled()).isTrue();
        assertThat(tenantOnly.guestBookingRequiresMobileConfirmation()).isFalse();
        assertThat(isolated.guestBookingEnabled()).isTrue();
        assertThat(isolated.minimalRegistrationEnabled()).isTrue();
    }

    @Test
    void quickRegistrationHasItsOwnTenantOverrideAndDoesNotReuseGuestBookingFlag() {
        UUID enabledTenant = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setGuestBookingEnabled(true);
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setVoiceQuickRegistrationEnabled(true);
        properties.setTenantOverrides(Map.of(enabledTenant.toString(), tenant));
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties);

        assertThat(resolver.resolve(enabledTenant, null, Operation.BOOK_APPOINTMENT)
                .voiceQuickRegistrationEnabled()).isTrue();
        assertThat(resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT)
                .voiceQuickRegistrationEnabled()).isFalse();
        assertThat(resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT)
                .guestBookingEnabled()).isTrue();
    }

    @Test
    void bookingExecutionHasIndependentFailClosedClinicTenantApplicationPrecedence() {
        UUID tenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        properties.getDefaultPolicy().setEnabled(true);
        properties.getDefaultPolicy().setTrustMode(TrustMode.OTP_REQUIRED);
        VoiceTransactionAuthProperties.PolicyOverride tenant = new VoiceTransactionAuthProperties.PolicyOverride();
        tenant.setBookingEnabled(true);
        VoiceTransactionAuthProperties.PolicyOverride clinic = new VoiceTransactionAuthProperties.PolicyOverride();
        clinic.setBookingEnabled(false);
        properties.setTenantOverrides(Map.of(tenantId.toString(), tenant));
        properties.setClinicOverrides(Map.of(clinicId.toString(), clinic));

        var resolver = new VoiceTransactionPolicyResolver(properties);
        assertThat(resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT).bookingEnabled()).isFalse();
        assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).bookingEnabled()).isTrue();
        assertThat(resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT).bookingEnabled()).isFalse();
        assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).bookingExecutionAllowed()).isTrue();
        assertThat(resolver.resolve(tenantId, null, Operation.VIEW_APPOINTMENTS).bookingEnabled()).isTrue();
    }

    @Test
    void bookingKillSwitchOverridesOtpPreverifiedGuestAndAllOtherOperations() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        var defaults = properties.getDefaultPolicy();
        defaults.setEnabled(true);
        defaults.setAuthenticationMode(AuthenticationMode.OTP_REQUIRED);
        defaults.setTrustMode(TrustMode.PREVERIFIED_PHONE_ALLOWED);
        defaults.setBookingAuthRequired(true);
        defaults.setGuestBookingEnabled(true);
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(false);
        operator.setOtpRequired(true);
        operator.setGuestBookingEnabled(true);
        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties, operator);

        var booking = resolver.resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT);
        assertThat(booking.bookingEnabled()).isFalse();
        assertThat(booking.bookingExecutionAllowed()).isFalse();
        assertThat(booking.transactionFeatureEnabled()).isFalse();
        assertThat(booking.challengeAllowed()).isTrue();
        for (Operation operation : java.util.List.of(Operation.VIEW_APPOINTMENTS,
                Operation.CANCEL_APPOINTMENT, Operation.RESCHEDULE_APPOINTMENT)) {
            var policy = resolver.resolve(UUID.randomUUID(), null, operation);
            assertThat(policy.transactionFeatureEnabled()).as(operation.name()).isFalse();
            assertThat(policy.bookingExecutionAllowed()).as(operation.name()).isFalse();
        }
    }

    @Test
    void otpCompatibilityFlagDoesNotDisableSupportedNewBooking() {
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceOperatorProperties operator = new VoiceOperatorProperties();
        operator.setBookingEnabled(true);
        operator.setOtpRequired(true);
        VoiceTransactionAuthPolicy policy = new VoiceTransactionPolicyResolver(properties, operator)
                .resolve(UUID.randomUUID(), null, Operation.BOOK_APPOINTMENT);

        assertThat(policy.otpRequired()).isTrue();
        assertThat(policy.bookingEnabled()).isTrue();
        assertThat(policy.bookingExecutionAllowed()).isTrue();
        assertThat(policy.decision()).isEqualTo("RESTRICTED_NEW_BOOKING_ONLY");
    }

    @Test
    void springBindsTenantAndClinicOverrideMaps() {
        UUID tenantId = UUID.randomUUID();
        UUID disabledTenantId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        new ApplicationContextRunner().withUserConfiguration(PolicyConfiguration.class)
                .withPropertyValues(
                        "voice.otp-required=false",
                        "voice.booking-enabled=true",
                        "clinic.aiva.voice.transaction-auth.default-policy.authentication-mode=OTP_REQUIRED",
                        "clinic.aiva.voice.transaction-auth.default-policy.enabled=true",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + tenantId + ".booking-enabled=true",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + disabledTenantId + ".booking-enabled=false",
                        "clinic.aiva.voice.transaction-auth.clinic-overrides." + clinicId + ".booking-enabled=false",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + tenantId + ".auth-ttl-minutes=19",
                        "clinic.aiva.voice.transaction-auth.clinic-overrides." + clinicId + ".auth-ttl-minutes=24")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VoiceTransactionPolicyResolver resolver = context.getBean(VoiceTransactionPolicyResolver.class);
                    assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).authTtlMinutes()).isEqualTo(19);
                    assertThat(resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT).authTtlMinutes()).isEqualTo(24);
                    assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).bookingEnabled()).isTrue();
                    assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).bookingExecutionAllowed()).isTrue();
                    assertThat(resolver.resolve(disabledTenantId, null, Operation.BOOK_APPOINTMENT).bookingEnabled()).isFalse();
                    assertThat(resolver.resolve(disabledTenantId, null, Operation.BOOK_APPOINTMENT).bookingExecutionAllowed()).isFalse();
                    assertThat(resolver.resolve(tenantId, null, Operation.BOOK_APPOINTMENT).otpRequired()).isFalse();
                    assertThat(resolver.resolve(tenantId, clinicId, Operation.BOOK_APPOINTMENT).bookingEnabled()).isFalse();
                });
    }

    @Test
    void jeevanamUatQuickRegistrationOverrideIsTenantScopedAndDoesNotEnableGuestBooking() {
        UUID jeevanamUatTenant = UUID.fromString("4b1e2915-6d35-4a00-b9f8-2f0cc3bccb1f");
        UUID tenantWithoutOverride = UUID.randomUUID();
        new ApplicationContextRunner().withUserConfiguration(PolicyConfiguration.class)
                .withPropertyValues(
                        "clinic.aiva.voice.transaction-auth.default-policy.voice-quick-registration-enabled=false",
                        "clinic.aiva.voice.transaction-auth.default-policy.guest-booking-enabled=false",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + jeevanamUatTenant
                                + ".booking-enabled=true",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + jeevanamUatTenant
                                + ".guest-booking-enabled=false",
                        "clinic.aiva.voice.transaction-auth.tenant-overrides." + jeevanamUatTenant
                                + ".voice-quick-registration-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VoiceTransactionPolicyResolver resolver = context.getBean(VoiceTransactionPolicyResolver.class);
                    var jeevanamPolicy = resolver.resolve(jeevanamUatTenant, null, Operation.BOOK_APPOINTMENT);
                    var defaultPolicy = resolver.resolve(tenantWithoutOverride, null, Operation.BOOK_APPOINTMENT);

                    assertThat(jeevanamPolicy.voiceQuickRegistrationEnabled()).isTrue();
                    assertThat(jeevanamPolicy.guestBookingEnabled()).isFalse();
                    assertThat(defaultPolicy.voiceQuickRegistrationEnabled()).isFalse();
                    assertThat(defaultPolicy.guestBookingEnabled()).isFalse();
                });
    }

    @Test
    void tenantOverrideEnablesOnlyIntendedTenantWhenGlobalBookingIsOff() {
        UUID enabledTenant = UUID.randomUUID();
        UUID disabledTenant = UUID.randomUUID();
        VoiceTransactionAuthProperties properties = new VoiceTransactionAuthProperties();
        VoiceTransactionAuthProperties.PolicyOverride override = new VoiceTransactionAuthProperties.PolicyOverride();
        override.setBookingEnabled(true);
        properties.getTenantOverrides().put(enabledTenant.toString(), override);
        VoiceOperatorProperties global = new VoiceOperatorProperties();
        global.setBookingEnabled(false);
        global.setGuestBookingEnabled(false);

        VoiceTransactionPolicyResolver resolver = new VoiceTransactionPolicyResolver(properties, global);

        assertThat(resolver.resolve(enabledTenant, null, Operation.BOOK_APPOINTMENT).bookingExecutionAllowed()).isTrue();
        assertThat(resolver.resolve(disabledTenant, null, Operation.BOOK_APPOINTMENT).bookingEnabled()).isFalse();
        assertThat(resolver.resolve(disabledTenant, null, Operation.BOOK_APPOINTMENT).bookingExecutionAllowed()).isFalse();
    }

    @Test
    void springWiresVoiceAuthServiceWithTheCentralPolicyResolver() {
        new ApplicationContextRunner().withUserConfiguration(VoiceServiceConfiguration.class)
                .withBean(HealthcareAiReadOnlyToolGateway.class, () -> mock(HealthcareAiReadOnlyToolGateway.class))
                .withBean(AivaV2VoiceConversationAdapter.class, () -> mock(AivaV2VoiceConversationAdapter.class))
                .withBean(VoiceTestProperties.class, VoiceTestProperties::new)
                .withBean(ClinicTimeZoneResolver.class, () -> mock(ClinicTimeZoneResolver.class))
                .withBean(PatientPortalOtpService.class, () -> mock(PatientPortalOtpService.class))
                .withBean(com.deepthoughtnet.clinic.patient.service.PatientService.class,
                        () -> mock(com.deepthoughtnet.clinic.patient.service.PatientService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VoiceTransactionPolicyResolver.class);
                    assertThat(context).hasSingleBean(VoiceReadOnlyAivaService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({VoiceTransactionAuthProperties.class, VoiceOperatorProperties.class})
    @Import(VoiceTransactionPolicyResolver.class)
    static class PolicyConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({VoiceTransactionAuthProperties.class, VoiceOperatorProperties.class})
    @Import({VoiceTransactionPolicyResolver.class, VoiceReadOnlyAivaService.class})
    static class VoiceServiceConfiguration {}
}
