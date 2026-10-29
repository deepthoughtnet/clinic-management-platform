package com.deepthoughtnet.clinic.api.patientportal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;

class PatientPortalOtpServiceTest {
    private static final UUID TENANT_A_ID = UUID.randomUUID();
    private static final UUID TENANT_B_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID APP_USER_ID = UUID.randomUUID();

    private TenantRepository tenantRepository;
    private PatientRepository patientRepository;
    private AppUserProvisioner appUserProvisioner;
    private AppUserRepository appUserRepository;
    private PatientPortalOtpChallengeRepository challengeRepository;
    private PatientPortalSessionTokenService sessionTokenService;
    private PatientPortalAuthProperties properties;
    private MessagingProviderRegistry messagingProviderRegistry;
    private MessageProvider smsProvider;
    private PatientPortalOtpService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        patientRepository = mock(PatientRepository.class);
        appUserProvisioner = mock(AppUserProvisioner.class);
        appUserRepository = mock(AppUserRepository.class);
        challengeRepository = mock(PatientPortalOtpChallengeRepository.class);
        properties = new PatientPortalAuthProperties();
        messagingProviderRegistry = mock(MessagingProviderRegistry.class);
        smsProvider = mock(MessageProvider.class);
        properties.setExposeDevOtp(true);
        properties.setSessionSecret("test-patient-portal-secret");
        sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalOtpService(
                tenantRepository,
                patientRepository,
                appUserProvisioner,
                appUserRepository,
                challengeRepository,
                sessionTokenService,
                properties, messagingProviderRegistry
        );
        when(messagingProviderRegistry.resolve(MessageChannel.SMS)).thenReturn(smsProvider);
        when(smsProvider.providerName()).thenReturn("test-sms");
        when(smsProvider.send(any(MessageRequest.class))).thenReturn(new MessageResult(true,
                MessageDeliveryStatus.SENT, "test-sms", "message-id", null, null, OffsetDateTime.now()));
        when(tenantRepository.findByCode("tenant-a")).thenReturn(Optional.of(tenant("tenant-a", TENANT_A_ID)));
        when(tenantRepository.findByCode("tenant-b")).thenReturn(Optional.of(tenant("tenant-b", TENANT_B_ID)));
        when(tenantRepository.findByCode("demo-clinic")).thenReturn(Optional.of(tenant("demo-clinic", TENANT_B_ID)));
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(challengeRepository.findTopByPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(any()))
                .thenAnswer(invocation -> challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc(
                        invocation.getArgument(0)));
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(invocation -> challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(challengeRepository.save(any(PatientPortalOtpChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestOtpExposesDevOtpOnlyWhenEnabled() {
        var response = service.requestOtp("tenant-a", "+91 98765 43210");

        assertThat(response.accepted()).isTrue();
        assertThat(response.devOtp()).matches("\\d{6}");

        properties.setExposeDevOtp(false);
        sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalOtpService(
                tenantRepository,
                patientRepository,
                appUserProvisioner,
                appUserRepository,
                challengeRepository,
                sessionTokenService,
                properties
        );
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.empty());

        var hiddenResponse = service.requestOtp("tenant-a", "9876543210");

        assertThat(hiddenResponse.accepted()).isTrue();
        assertThat(hiddenResponse.devOtp()).isNull();
    }

    @Test
    void priorVoiceOtpEvidenceRequiresCurrentTenantPatientAndRegisteredPhoneBinding() {
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210", OffsetDateTime.now());
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(challengeRepository.existsByTenantIdAndIdentifiedPatientIdAndPhoneNormalizedAndChannelAndVerifiedAtIsNotNull(
                TENANT_A_ID, PATIENT_ID, "919876543210", "VOICE")).thenReturn(true);

        assertThat(service.hasPreviouslyVerifiedVoicePhoneBinding(TENANT_A_ID, PATIENT_ID, "+91 98765 43210"))
                .isTrue();
        assertThat(service.hasPreviouslyVerifiedVoicePhoneBinding(TENANT_B_ID, PATIENT_ID, "+91 98765 43210"))
                .isFalse();
        assertThat(service.hasPreviouslyVerifiedVoicePhoneBinding(TENANT_A_ID, PATIENT_ID, "+91 90000 00000"))
                .isFalse();
        verify(challengeRepository).existsByTenantIdAndIdentifiedPatientIdAndPhoneNormalizedAndChannelAndVerifiedAtIsNotNull(
                TENANT_A_ID, PATIENT_ID, "919876543210", "VOICE");
    }

    @Test
    void accessApprovalModeBlocksOtpLoginAndDevOtpExposure() {
        properties.setMode(PatientPortalAuthProperties.Mode.ACCESS_APPROVAL);
        sessionTokenService = new PatientPortalSessionTokenService(properties, new ObjectMapper());
        service = new PatientPortalOtpService(
                tenantRepository,
                patientRepository,
                appUserProvisioner,
                appUserRepository,
                challengeRepository,
                sessionTokenService,
                properties
        );

        assertThatThrownBy(() -> service.requestOtp("tenant-a", "9876543210"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.verifyOtp("tenant-a", "9876543210", "123456"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requestOtpAllowsPhoneOnlyWithoutContext() {
        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.empty());

        var response = service.requestOtp("9876543210", (PatientPortalOtpContext) null);

        assertThat(response.accepted()).isTrue();
        assertThat(response.devOtp()).matches("\\d{6}");
    }

    @Test
    void verifyOtpRejectsExpiredChallenges() throws Exception {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        setField(challenge, "expiresAt", OffsetDateTime.now().minusSeconds(5));
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("expired");
    }

    @Test
    void verifyOtpStopsAfterMaximumAttempts() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));

        for (int attempt = 0; attempt < 5; attempt++) {
            service.verifyOtp("tenant-a", "9876543210", "999999");
        }

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("attempts exceeded");
        assertThat(challenge.getAttempts()).isEqualTo(5);
    }

    @Test
    void verifyOtpIsTenantIsolated() {
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_B_ID, "9876543210"))
                .thenReturn(Optional.empty());

        var response = service.verifyOtp("tenant-b", "9876543210", "123456");

        assertThat(response.verified()).isFalse();
        assertThat(response.message()).contains("Unable to verify patient access");
        verify(patientRepository, never()).findByTenantIdAndMobileIgnoreCaseAndActiveTrue(eq(TENANT_A_ID), any());
    }

    @Test
    void verifyOtpReturnsSafeMessageWhenNoPatientMatches() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(List.of());

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isFalse();
        assertThat(response.registrationRequired()).isTrue();
        assertThat(response.registrationSessionToken()).isNotBlank();
        assertThat(response.message()).contains("Complete your quick registration");
    }

    @Test
    void verifyOtpFallsBackToDemoClinicRegistrationWhenNoTenantContextIsAvailable() {
        PatientPortalOtpChallengeEntity challenge = challenge(null, "9876543210", "123456");
        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByMobileIgnoreCaseAndActiveTrue("9876543210")).thenReturn(List.of());

        var response = service.verifyOtp("9876543210", "123456", (PatientPortalOtpContext) null);

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isFalse();
        assertThat(response.registrationRequired()).isTrue();
        assertThat(response.tenantId()).isEqualTo(TENANT_B_ID.toString());
        assertThat(response.tenantCode()).isEqualTo("demo-clinic");
        assertThat(response.registrationSessionToken()).isNotBlank();
        assertThat(response.message()).contains("Complete your quick registration");
    }

    @Test
    void verifyOtpIssuesPatientSessionForMatchedPatient() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + PATIENT_ID, null, "Riya Sharma");

        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(List.of(patient));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.tenantId()).isEqualTo(TENANT_A_ID.toString());
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
        assertThat(response.patientSessionToken()).isNotBlank();
        assertThat(response.registrationSessionToken()).isNull();
        assertThat(appUser.getPatientId()).isEqualTo(PATIENT_ID);
    }

    @Test
    void verifyOtpUsesTenantScopedPatientWhenSameMobileExistsInAnotherTenant() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        PatientEntity tenantAValue = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + PATIENT_ID, null, "Riya Sharma");

        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(List.of(tenantAValue));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.patientSessionToken()).isNotBlank();
        assertThat(response.tenantId()).isEqualTo(TENANT_A_ID.toString());
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
    }

    @Test
    void verifyOtpChoosesPrimaryActivePatientWhenDuplicateTenantMobileExists() {
        PatientPortalOtpChallengeEntity challenge = challenge(TENANT_A_ID, "9876543210", "123456");
        PatientEntity older = patientEntity(TENANT_A_ID, UUID.randomUUID(), "9876543210", OffsetDateTime.parse("2025-01-01T00:00:00Z"));
        PatientEntity newer = patientEntity(TENANT_A_ID, UUID.randomUUID(), "9876543210", OffsetDateTime.parse("2025-02-01T00:00:00Z"));
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + older.getId(), null, "Riya Sharma");

        when(challengeRepository.findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(TENANT_A_ID, "9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(TENANT_A_ID, "9876543210"))
                .thenReturn(List.of(newer, older));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + older.getId()),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("tenant-a", "9876543210", "123456");

        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
    }

    @Test
    void verifyOtpResolvesLinkedPatientWhenDuplicateMobileExistsAcrossTenants() {
        PatientPortalOtpChallengeEntity challenge = challenge(null, "9876501234", "123456");
        PatientEntity unlinkedPatient = patientEntity(TENANT_A_ID, UUID.randomUUID(), "9876501234");
        PatientEntity linkedPatient = patientEntity(TENANT_B_ID, PATIENT_ID, "9876501234");
        AppUserEntity linkedAppUser = AppUserEntity.create(TENANT_B_ID, "patientportal:" + TENANT_B_ID + ":" + PATIENT_ID, null, "Rohan Sharma");
        linkedAppUser.setPatientId(PATIENT_ID);
        AppUserEntity patientAppUser = AppUserEntity.create(TENANT_B_ID, "patientportal:" + TENANT_B_ID + ":" + PATIENT_ID, null, "Rohan Sharma");

        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876501234"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByMobileIgnoreCaseAndActiveTrue("9876501234")).thenReturn(List.of(unlinkedPatient, linkedPatient));
        when(appUserRepository.findByPatientId(unlinkedPatient.getId())).thenReturn(List.of());
        when(appUserRepository.findByPatientId(PATIENT_ID)).thenReturn(List.of(linkedAppUser));
        when(tenantRepository.findById(TENANT_B_ID)).thenReturn(Optional.of(tenant("tenant-b", TENANT_B_ID)));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_B_ID),
                eq("patientportal:" + TENANT_B_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_B_ID, APP_USER_ID)).thenReturn(Optional.of(patientAppUser));

        var response = service.verifyOtp("9876501234", "123456", (PatientPortalOtpContext) null);

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.tenantId()).isEqualTo(TENANT_B_ID.toString());
        assertThat(response.tenantCode()).isEqualTo("tenant-b");
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
        assertThat(response.patientSessionToken()).isNotBlank();
        assertThat(response.registrationSessionToken()).isNull();
    }

    @Test
    void verifyOtpResolvesTenantFromUniquePatientWhenContextIsMissing() {
        PatientPortalOtpChallengeEntity challenge = challenge(null, "9876543210", "123456");
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        AppUserEntity appUser = AppUserEntity.create(TENANT_A_ID, "patientportal:" + TENANT_A_ID + ":" + PATIENT_ID, null, "Riya Sharma");

        when(challengeRepository.findTopByPhoneNormalizedOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.of(challenge));
        when(patientRepository.findByMobileIgnoreCaseAndActiveTrue("9876543210")).thenReturn(List.of(patient));
        when(tenantRepository.findById(TENANT_A_ID)).thenReturn(Optional.of(tenant("tenant-a", TENANT_A_ID)));
        when(appUserProvisioner.upsertAndReturnId(
                eq(TENANT_A_ID),
                eq("patientportal:" + TENANT_A_ID + ":" + PATIENT_ID),
                eq(null),
                eq("Riya Sharma")
        )).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.of(appUser));

        var response = service.verifyOtp("9876543210", "123456", (PatientPortalOtpContext) null);

        assertThat(response.verified()).isTrue();
        assertThat(response.patientExists()).isTrue();
        assertThat(response.registrationRequired()).isFalse();
        assertThat(response.tenantId()).isEqualTo(TENANT_A_ID.toString());
        assertThat(response.tenantCode()).isEqualTo("tenant-a");
        assertThat(response.patientDisplayName()).isEqualTo("Riya Sharma");
        assertThat(response.patientSessionToken()).isNotBlank();
    }

    @Test
    void voiceChallengeIsSentThroughMessagingAndExactCallBoundChallengeVerifiesWithoutPortalToken() {
        properties.setMode(PatientPortalAuthProperties.Mode.OTP);
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        var started = service.startVoiceChallenge(TENANT_A_ID, PATIENT_ID, "+91 98765 43210", "call-A", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(started.sent()).isTrue();
        assertThat(started.challengeId()).isNotNull();
        ArgumentCaptor<MessageRequest> request = ArgumentCaptor.forClass(MessageRequest.class);
        verify(smsProvider).send(request.capture());
        assertThat(request.getValue().channel()).isEqualTo(MessageChannel.SMS);
        assertThat(request.getValue().body()).containsPattern("\\d{6}");
        ArgumentCaptor<PatientPortalOtpChallengeEntity> challengeCaptor = ArgumentCaptor.forClass(PatientPortalOtpChallengeEntity.class);
        verify(challengeRepository).save(challengeCaptor.capture());
        PatientPortalOtpChallengeEntity challenge = challengeCaptor.getValue();
        assertThat(challenge.getId()).isEqualTo(started.challengeId());
        assertThat(challenge.getChannel()).isEqualTo("VOICE");
        assertThat(challenge.getProviderCallId()).isEqualTo("call-A");
        assertThat(challenge.getIdentifiedPatientId()).isEqualTo(PATIENT_ID);

        String otp = request.getValue().body().replaceAll(".*?(\\d{6}).*", "$1");
        when(challengeRepository.findById(started.challengeId())).thenReturn(Optional.of(challenge));
        var portalVerification = service.verifyOtp("+91 98765 43210", otp,
                new com.deepthoughtnet.clinic.api.patientportal.auth.dto.PatientPortalOtpContext(
                        null, "tenant-a", null, null, null));
        assertThat(portalVerification.verified()).isFalse();
        assertThat(service.verifyVoiceChallenge(started.challengeId(), TENANT_A_ID, PATIENT_ID,
                "9876543210", "call-B", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), otp).verified()).isFalse();

        when(appUserProvisioner.upsertAndReturnId(eq(TENANT_A_ID), any(), eq(null), any())).thenReturn(APP_USER_ID);
        when(appUserRepository.findByTenantIdAndId(TENANT_A_ID, APP_USER_ID)).thenReturn(Optional.empty());
        var verified = service.verifyVoiceChallenge(started.challengeId(), TENANT_A_ID, PATIENT_ID,
                "+91 98765 43210", "call-A", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), otp);
        assertThat(verified.verified()).isTrue();
        assertThat(verified.authenticatedPatientId()).isEqualTo(PATIENT_ID);
        assertThat(verified.appUserId()).isEqualTo(APP_USER_ID);
        assertThat(verified.expiresAt()).isNotNull();
        verify(challengeRepository, never()).findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(
                eq(TENANT_A_ID), eq("9876543210"));
    }

    @Test
    void voiceChallengeRejectsMockSmsDeliveryProvider() {
        properties.setMode(PatientPortalAuthProperties.Mode.OTP);
        PatientEntity patient = patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(smsProvider.providerName()).thenReturn("mock");

        var started = service.startVoiceChallenge(TENANT_A_ID, PATIENT_ID, "9876543210", "call-A", null);

        assertThat(started.sent()).isFalse();
        assertThat(started.status()).isEqualTo("DELIVERY_UNAVAILABLE");
        verify(smsProvider, never()).send(any(MessageRequest.class));
    }

    @Test
    void voiceVerificationReusesAttemptAndExpiryRulesWithoutGrantingOnFailure() {
        properties.setMode(PatientPortalAuthProperties.Mode.OTP);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(
                patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210")));
        UUID interactionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        PatientPortalOtpChallengeEntity wrongCodeChallenge = PatientPortalOtpChallengeEntity.createVoice(
                TENANT_A_ID, PATIENT_ID, "919876543210", new BCryptPasswordEncoder().encode("123456"),
                OffsetDateTime.now().plusMinutes(5), "call-A", interactionId);
        when(challengeRepository.findById(wrongCodeChallenge.getId())).thenReturn(Optional.of(wrongCodeChallenge));

        var wrong = service.verifyVoiceChallenge(wrongCodeChallenge.getId(), TENANT_A_ID, PATIENT_ID,
                "+91 98765 43210", "call-A", interactionId, "000000");
        assertThat(wrong.verified()).isFalse();
        assertThat(wrong.status()).isEqualTo("FAILED");
        assertThat(wrongCodeChallenge.getAttempts()).isEqualTo(1);
        assertThat(wrong.authenticatedPatientId()).isNull();

        PatientPortalOtpChallengeEntity expiredChallenge = PatientPortalOtpChallengeEntity.createVoice(
                TENANT_A_ID, PATIENT_ID, "919876543210", new BCryptPasswordEncoder().encode("123456"),
                OffsetDateTime.now().minusSeconds(1), "call-A", interactionId);
        when(challengeRepository.findById(expiredChallenge.getId())).thenReturn(Optional.of(expiredChallenge));
        var expired = service.verifyVoiceChallenge(expiredChallenge.getId(), TENANT_A_ID, PATIENT_ID,
                "+91 98765 43210", "call-A", interactionId, "123456");
        assertThat(expired.verified()).isFalse();
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.authenticatedPatientId()).isNull();
    }

    @Test
    void voiceSmsFailureDeletesChallengeAndDoesNotReportSent() {
        properties.setMode(PatientPortalAuthProperties.Mode.OTP);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(
                patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210")));
        when(smsProvider.send(any(MessageRequest.class))).thenReturn(new MessageResult(false,
                MessageDeliveryStatus.FAILED, "test-sms", null, "PROVIDER_ERROR", "delivery failed", null));

        var started = service.startVoiceChallenge(TENANT_A_ID, PATIENT_ID, "9876543210", "call-A", null);

        assertThat(started.sent()).isFalse();
        assertThat(started.status()).isEqualTo("DELIVERY_FAILED");
        ArgumentCaptor<PatientPortalOtpChallengeEntity> saved = ArgumentCaptor.forClass(PatientPortalOtpChallengeEntity.class);
        verify(challengeRepository).save(saved.capture());
        verify(challengeRepository).delete(saved.getValue());
        assertThat(java.util.regex.Pattern.compile("\\b\\d{6}\\b").matcher(started.toString()).find()).isFalse();
    }

    @Test
    void voiceChallengeRejectsTenantAndPatientBindingMismatch() {
        PatientPortalOtpChallengeEntity challenge = PatientPortalOtpChallengeEntity.createVoice(
                TENANT_A_ID, PATIENT_ID, "9876543210", new BCryptPasswordEncoder().encode("123456"),
                OffsetDateTime.now().plusMinutes(5), "call-A", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patientEntity(TENANT_A_ID, PATIENT_ID, "9876543210")));

        assertThat(service.verifyVoiceChallenge(challenge.getId(), TENANT_B_ID, PATIENT_ID, "9876543210",
                "call-A", challenge.getInteractionId(), "123456").verified()).isFalse();
        assertThat(service.verifyVoiceChallenge(challenge.getId(), TENANT_A_ID, UUID.randomUUID(), "9876543210",
                "call-A", challenge.getInteractionId(), "123456").verified()).isFalse();
        assertThat(service.verifyVoiceChallenge(challenge.getId(), TENANT_A_ID, PATIENT_ID, "9876543210",
                "call-A", UUID.randomUUID(), "123456").verified()).isFalse();
    }

    private TenantEntity tenant(String code, UUID id) {
        TenantEntity tenant = TenantEntity.create(code, "Tenant " + code, "TRIAL");
        setField(tenant, "id", id);
        return tenant;
    }

    private PatientPortalOtpChallengeEntity challenge(UUID tenantId, String phone, String otp) {
        return PatientPortalOtpChallengeEntity.create(
                tenantId,
                phone,
                new BCryptPasswordEncoder().encode(otp),
                OffsetDateTime.now().plusMinutes(5)
        );
    }

    private PatientEntity patientEntity(UUID tenantId, UUID patientId, String mobile) {
        return patientEntity(tenantId, patientId, mobile, OffsetDateTime.now());
    }

    private PatientEntity patientEntity(UUID tenantId, UUID patientId, String mobile, OffsetDateTime createdAt) {
        PatientEntity patient = PatientEntity.create(tenantId, "PAT-001");
        patient.update(
                "Riya",
                "Sharma",
                com.deepthoughtnet.clinic.patient.service.model.PatientGender.FEMALE,
                LocalDate.of(1992, 3, 4),
                32,
                mobile,
                "riya@example.com",
                "Line 1",
                null,
                "Pune",
                "MH",
                "India",
                "411001",
                "Emergency",
                "8888888888",
                "O+",
                "Peanuts",
                "Asthma",
                "Inhaler",
                "Appendectomy",
                null,
                true
        );
        setField(patient, "id", patientId);
        setField(patient, "createdAt", createdAt);
        return patient;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
