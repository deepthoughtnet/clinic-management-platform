package com.deepthoughtnet.clinic.discover.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderSessionEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderSessionRepository;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverVerificationChallengeEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverVerificationChallengeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoverVerificationServiceTest {
    @Mock
    private ProviderApplicationRepository applications;

    @Mock
    private com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationRepository contactVerifications;

    @Mock
    private ProviderLocationRepository locations;

    @Mock
    private ProviderServiceRepository services;

    @Mock
    private ProviderDocumentRepository documents;

    @Mock
    private DiscoverVerificationChallengeRepository challenges;

    @Mock
    private DiscoverProviderAccountRepository providerAccounts;

    @Mock
    private DiscoverProviderSessionRepository providerSessions;

    @Mock
    private VerificationDeliveryPort deliveryPort;

    @Mock
    private ProviderPublicProfileService publicProfileService;

    private DiscoverVerificationService service(DiscoverVerificationProperties properties) {
        return new DiscoverVerificationService(
                applications,
                contactVerifications,
                locations,
                services,
                documents,
                challenges,
                providerAccounts,
                providerSessions,
                properties,
                deliveryPort,
                publicProfileService
        );
    }

    @Test
    void localChallengeResponsesExposeVerificationMode() {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        properties.setExposeDevelopmentCode(true);
        DiscoverVerificationService service = service(properties);

        when(challenges.findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdIsNullAndProviderAccountIdIsNullOrderByCreatedAtDesc(
                any(VerificationPurpose.class),
                any(VerificationChannel.class),
                any(String.class)
        ))
                .thenReturn(Optional.empty());
        when(challenges.save(any(DiscoverVerificationChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryPort.deliver(any())).thenReturn(VerificationDeliveryResult.accepted("discover-verification-local", "delivery-1", "483921", "Development verification code generated."));

        VerificationChallengeResult result = service.requestChallenge(new VerificationChallengeRequest(
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_EMAIL,
                VerificationChannel.EMAIL,
                "discover.clinic.uat@jeevanam.test",
                "Subject",
                "Body",
                "PROVIDER_LOGIN"
        ));

        assertThat(result.challengeId()).isNotNull();
        assertThat(result.channel()).isEqualTo(VerificationChannel.EMAIL);
        assertThat(result.maskedRecipient()).isEqualTo("d********@jeevanam.test");
        assertThat(result.developmentCode()).isEqualTo("483921");
        assertThat(result.verificationMode()).isEqualTo("LOCAL");
        assertThat(result.message()).isEqualTo("Login verification email sent.");
    }

    @Test
    void loginVerificationUsesChallengeIdRatherThanLatestChallenge() throws Exception {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        DiscoverVerificationService service = service(properties);

        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876501201");
        account.markPhoneVerified();
        ProviderApplicationEntity linkedApplication = ProviderApplicationEntity.create(
                UUID.randomUUID(),
                "JCL-2026-00000001",
                ProviderType.CLINIC,
                "token",
                "discover.clinic.uat@jeevanam.test",
                "9876501201",
                "password",
                true,
                true
        );
        linkedApplication.setProviderAccountId(account.getId());
        linkedApplication.setContactVerified(true);

        DiscoverVerificationChallengeEntity olderChallenge = DiscoverVerificationChallengeEntity.create(
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_PHONE,
                VerificationChannel.SMS,
                "9876501201",
                digest("039768"),
                5,
                OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().plusMinutes(1),
                "discover-verification-local",
                "delivery-old",
                "PROVIDER_LOGIN",
                "039768"
        );
        DiscoverVerificationChallengeEntity latestChallenge = DiscoverVerificationChallengeEntity.create(
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_PHONE,
                VerificationChannel.SMS,
                "9876501201",
                digest("111222"),
                5,
                OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().plusMinutes(1),
                "discover-verification-local",
                "delivery-latest",
                "PROVIDER_LOGIN",
                "111222"
        );

        when(challenges.findById(olderChallenge.getId())).thenReturn(Optional.of(olderChallenge));
        when(challenges.save(any(DiscoverVerificationChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerAccounts.findByNormalizedPhone("9876501201")).thenReturn(Optional.of(account));

        var result = service.verifyChallenge(new DiscoverVerificationService.VerificationVerificationRequest(
                olderChallenge.getId(),
                null,
                null,
                null,
                null,
                null,
                "039768",
                "PROVIDER_LOGIN"
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.providerAccountId()).isEqualTo(account.getId());
        verify(challenges).findById(olderChallenge.getId());
    }

    @Test
    void loginVerificationCreatesProviderAccountForNewVerifiedPhoneContact() throws Exception {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        DiscoverVerificationService service = service(properties);

        DiscoverVerificationChallengeEntity challenge = DiscoverVerificationChallengeEntity.create(
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_PHONE,
                VerificationChannel.SMS,
                "9876501401",
                digest("606801"),
                5,
                OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().plusMinutes(1),
                "discover-verification-local",
                "delivery-1",
                "PROVIDER_LOGIN",
                "606801"
        );

        when(challenges.findById(challenge.getId())).thenReturn(Optional.of(challenge));
        when(challenges.save(any(DiscoverVerificationChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerAccounts.findByNormalizedPhone("9876501401")).thenReturn(Optional.empty());
        when(applications.findByPhoneIgnoreCase("9876501401")).thenReturn(List.of());
        when(providerAccounts.save(any(DiscoverProviderAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.verifyChallenge(new DiscoverVerificationService.VerificationVerificationRequest(
                challenge.getId(),
                null,
                null,
                null,
                null,
                null,
                "606801",
                "PROVIDER_LOGIN"
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.accountCreated()).isTrue();
        assertThat(result.accountLinked()).isFalse();
        assertThat(result.providerAccountId()).isNotNull();
        verify(providerAccounts).save(any(DiscoverProviderAccountEntity.class));
    }

    @Test
    void loginVerificationReusesLinkedProviderAccountAcrossContacts() throws Exception {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        DiscoverVerificationService service = service(properties);

        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "+919876501201");
        account.markPhoneVerified();
        UUID providerAccountId = account.getId();
        ProviderApplicationEntity linkedApplication = ProviderApplicationEntity.create(
                UUID.randomUUID(),
                "JCL-2026-00000001",
                ProviderType.CLINIC,
                "token",
                "discover.clinic.uat@jeevanam.test",
                "+919876501201",
                "password",
                true,
                true
        );
        linkedApplication.setProviderAccountId(providerAccountId);
        linkedApplication.setContactVerified(true);

        DiscoverVerificationChallengeEntity challenge = DiscoverVerificationChallengeEntity.create(
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_EMAIL,
                VerificationChannel.EMAIL,
                "discover.clinic.uat@jeevanam.test",
                digest("483921"),
                5,
                OffsetDateTime.now().plusMinutes(5),
                OffsetDateTime.now().plusMinutes(1),
                "discover-verification-local",
                "delivery-1",
                "PROVIDER_LOGIN",
                "483921"
        );

        when(challenges.findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdIsNullAndProviderAccountIdIsNullOrderByCreatedAtDesc(
                VerificationPurpose.PROVIDER_LOGIN_EMAIL,
                VerificationChannel.EMAIL,
                "discover.clinic.uat@jeevanam.test"
        )).thenReturn(Optional.of(challenge));
        when(challenges.save(any(DiscoverVerificationChallengeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(applications.findByEmailIgnoreCase("discover.clinic.uat@jeevanam.test")).thenReturn(List.of(linkedApplication));
        com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationEntity verification =
                com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationEntity.create(
                        linkedApplication.getId(),
                        "discover.clinic.uat@jeevanam.test",
                        "+919876501201"
                );
        verification.markEmailVerified();
        when(contactVerifications.findByProviderId(linkedApplication.getId())).thenReturn(Optional.of(verification));
        when(providerAccounts.findById(providerAccountId)).thenReturn(Optional.of(account));
        when(providerAccounts.findByNormalizedEmail("discover.clinic.uat@jeevanam.test")).thenReturn(Optional.empty());
        when(providerAccounts.save(any(DiscoverProviderAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.verifyChallenge(new DiscoverVerificationService.VerificationVerificationRequest(
                null,
                null,
                null,
                VerificationPurpose.PROVIDER_LOGIN_EMAIL,
                VerificationChannel.EMAIL,
                "discover.clinic.uat@jeevanam.test",
                "483921",
                "PROVIDER_LOGIN"
        ));

        assertThat(result.verified()).isTrue();
        assertThat(result.providerAccountId()).isEqualTo(providerAccountId);
        verify(providerAccounts).save(any(DiscoverProviderAccountEntity.class));
        verify(applications, never()).save(any(ProviderApplicationEntity.class));
    }

    @Test
    void replaceSessionRevokesExistingAndPriorAccountSessions() {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        DiscoverVerificationService service = service(properties);

        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create("discover.clinic.uat@jeevanam.test", "9876501201");
        DiscoverProviderSessionEntity currentSession = DiscoverProviderSessionEntity.create(
                account.getId(),
                digestUnchecked("current-session"),
                OffsetDateTime.now().minusMinutes(2),
                OffsetDateTime.now().plusHours(1)
        );
        DiscoverProviderSessionEntity staleActiveSession = DiscoverProviderSessionEntity.create(
                account.getId(),
                digestUnchecked("older-session"),
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now().plusHours(1)
        );

        when(providerAccounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(providerSessions.findBySessionTokenHash(digestUnchecked("current-session"))).thenReturn(Optional.of(currentSession));
        when(providerSessions.findByProviderAccountIdOrderByCreatedAtDesc(account.getId())).thenReturn(List.of(currentSession, staleActiveSession));
        when(providerSessions.save(any(DiscoverProviderSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderSessionResult result = service.replaceSession(account.getId(), "current-session");

        assertThat(result.providerAccountId()).isEqualTo(account.getId());
        assertThat(result.sessionToken()).isNotBlank();
        assertThat(currentSession.getRevokedAt()).isNotNull();
        assertThat(staleActiveSession.getRevokedAt()).isNotNull();
        verify(providerSessions).findBySessionTokenHash(digestUnchecked("current-session"));
    }

    private static String digest(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digestUnchecked(String value) {
        try {
            return digest(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
