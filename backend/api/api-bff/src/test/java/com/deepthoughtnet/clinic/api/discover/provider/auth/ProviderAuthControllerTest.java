package com.deepthoughtnet.clinic.api.discover.provider.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeResult;
import com.deepthoughtnet.clinic.discover.verification.VerificationPurpose;
import com.deepthoughtnet.clinic.discover.verification.VerificationVerificationResult;
import com.deepthoughtnet.clinic.discover.verification.ProviderSessionResult;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProviderAuthControllerTest {
    private ProviderAuthController controller(DiscoverVerificationService verificationService) {
        return new ProviderAuthController(verificationService);
    }

    @Test
    void requestNormalizesEmailAndIndianPhoneIdentifiers() {
        DiscoverVerificationService verificationService = org.mockito.Mockito.mock(DiscoverVerificationService.class);
        when(verificationService.requestChallenge(any(VerificationChallengeRequest.class))).thenReturn(
                new VerificationChallengeResult(
                        java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        VerificationChannel.SMS,
                        "******1201",
                        "Login verification OTP sent.",
                        "483921",
                        "LOCAL",
                        java.time.OffsetDateTime.parse("2026-07-29T00:00:00Z"),
                        java.time.OffsetDateTime.parse("2026-07-29T00:01:00Z"),
                        300,
                        60,
                        "discover-verification-local",
                        "delivery-reference"
                )
        );

        ProviderAuthController phoneController = controller(verificationService);
        ProviderAuthModels.LoginChallengeResponse phoneResponse = phoneController.request(new ProviderAuthModels.LoginRequest(" +91 98765 01201 "));
        ArgumentCaptor<VerificationChallengeRequest> phoneCaptor = ArgumentCaptor.forClass(VerificationChallengeRequest.class);
        verify(verificationService).requestChallenge(phoneCaptor.capture());
        assertThat(phoneCaptor.getValue().channel()).isEqualTo(VerificationChannel.SMS);
        assertThat(phoneCaptor.getValue().purpose()).isEqualTo(VerificationPurpose.PROVIDER_LOGIN_PHONE);
        assertThat(phoneCaptor.getValue().normalizedRecipient()).isEqualTo("9876501201");
        assertThat(phoneResponse.challengeId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(phoneResponse.maskedRecipient()).isEqualTo("******1201");

        DiscoverVerificationService emailService = org.mockito.Mockito.mock(DiscoverVerificationService.class);
        when(emailService.requestChallenge(any(VerificationChallengeRequest.class))).thenReturn(
                new VerificationChallengeResult(
                        java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        VerificationChannel.EMAIL,
                        "d*****@jeevanam.test",
                        "Login verification email sent.",
                        "483921",
                        "LOCAL",
                        java.time.OffsetDateTime.parse("2026-07-29T00:00:00Z"),
                        java.time.OffsetDateTime.parse("2026-07-29T00:01:00Z"),
                        300,
                        60,
                        "discover-verification-local",
                        "delivery-reference"
                )
        );
        ProviderAuthController emailController = controller(emailService);
        ProviderAuthModels.LoginChallengeResponse emailResponse = emailController.request(new ProviderAuthModels.LoginRequest("Discover.Clinic.UAT@Jeevanam.Test"));
        ArgumentCaptor<VerificationChallengeRequest> emailCaptor = ArgumentCaptor.forClass(VerificationChallengeRequest.class);
        verify(emailService).requestChallenge(emailCaptor.capture());
        assertThat(emailCaptor.getValue().channel()).isEqualTo(VerificationChannel.EMAIL);
        assertThat(emailCaptor.getValue().purpose()).isEqualTo(VerificationPurpose.PROVIDER_LOGIN_EMAIL);
        assertThat(emailCaptor.getValue().normalizedRecipient()).isEqualTo("discover.clinic.uat@jeevanam.test");
        assertThat(emailResponse.challengeId()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(emailResponse.maskedRecipient()).isEqualTo("d*****@jeevanam.test");
    }

    @Test
    void verifyChallengeUsesChallengeIdPathAndStringOtp() {
        DiscoverVerificationService verificationService = org.mockito.Mockito.mock(DiscoverVerificationService.class);
        UUID challengeId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID providerAccountId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(verificationService.verifyChallenge(any())).thenReturn(new VerificationVerificationResult(
                true,
                "Verification successful.",
                providerAccountId,
                true,
                true,
                "9876501201",
                VerificationPurpose.PROVIDER_LOGIN_PHONE,
                VerificationChannel.SMS
        ));
        when(verificationService.replaceSession(providerAccountId, null)).thenReturn(new ProviderSessionResult(
                providerAccountId,
                "session-token",
                OffsetDateTime.parse("2026-07-29T01:00:00Z")
        ));

        ProviderAuthController controller = controller(verificationService);
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);
        when(request.isSecure()).thenReturn(false);

        controller.verifyChallenge(request, response, challengeId, new ProviderAuthModels.ChallengeVerifyRequest("039768"));

        ArgumentCaptor<com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService.VerificationVerificationRequest> captor =
                ArgumentCaptor.forClass(com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService.VerificationVerificationRequest.class);
        verify(verificationService).verifyChallenge(captor.capture());
        verify(verificationService).replaceSession(providerAccountId, null);
        assertThat(captor.getValue().challengeId()).isEqualTo(challengeId);
        assertThat(captor.getValue().code()).isEqualTo("039768");
    }
}
