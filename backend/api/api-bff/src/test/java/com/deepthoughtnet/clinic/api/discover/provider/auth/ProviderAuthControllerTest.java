package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ChallengeVerifyRequest;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginRequest;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ProviderAuthControllerTest {
    @Mock
    private DiscoverVerificationService verificationService;

    @Test
    void accessApprovalModeRejectsVerificationCodeEndpoints() {
        ProviderPortalAuthProperties properties = new ProviderPortalAuthProperties();
        properties.setMode(ProviderPortalAuthProperties.Mode.ACCESS_APPROVAL);
        ProviderAuthController controller = new ProviderAuthController(verificationService, properties);

        assertThrows(ForbiddenException.class, () -> controller.request(new LoginRequest("provider@example.com")));
        assertThrows(ForbiddenException.class, () -> controller.requestChallenge(new LoginRequest("provider@example.com")));
        assertThrows(ForbiddenException.class, () -> controller.verify(null, null, new com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginVerifyRequest("provider@example.com", "123456")));
        assertThrows(ForbiddenException.class, () -> controller.verifyChallenge(null, null, java.util.UUID.randomUUID(), new ChallengeVerifyRequest("123456")));
    }
}
