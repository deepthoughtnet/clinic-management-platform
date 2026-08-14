package com.deepthoughtnet.clinic.api.discover.provider.access;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderPortalAuthProperties;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessGrantRecord;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestCommand;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestRecord;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestService;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestStatus;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestType;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.ProviderSessionResult;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderAccessAuthControllerTest {
    @Mock
    private ProviderPortalAccessRequestService accessRequestService;

    @Mock
    private DiscoverVerificationService verificationService;

    @Test
    void accessApprovalModeAllowsAccessRequestSubmissionAndLogin() {
        ProviderPortalAuthProperties properties = new ProviderPortalAuthProperties();
        properties.setMode(ProviderPortalAuthProperties.Mode.ACCESS_APPROVAL);
        ProviderAccessAuthController controller = new ProviderAccessAuthController(accessRequestService, properties);

        ProviderPortalAccessRequestRecord requestRecord = new ProviderPortalAccessRequestRecord(
                UUID.randomUUID(),
                ProviderPortalAccessRequestType.PROVIDER,
                ProviderType.HOSPITAL,
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "9876501111",
                null,
                null,
                ProviderPortalAccessRequestStatus.REQUESTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                0L
        );
        when(accessRequestService.submit(org.mockito.ArgumentMatchers.any(ProviderPortalAccessRequestCommand.class))).thenReturn(requestRecord);

        ProviderPortalAccessGrantRecord grant = new ProviderPortalAccessGrantRecord(UUID.randomUUID(), "provider@example.com", "JEV-2026-001");
        when(accessRequestService.authenticate("provider@example.com", "12345678")).thenReturn(grant);
        when(accessRequestService.getVerificationService()).thenReturn(verificationService);
        when(verificationService.replaceSession(grant.providerAccountId(), null)).thenReturn(new ProviderSessionResult(grant.providerAccountId(), "session-token", OffsetDateTime.now().plusHours(12)));

        ProviderAccessModels.ProviderAccessRequestResponse submitted = controller.submit(new ProviderAccessModels.ProviderAccessRequestSubmitRequest(
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "9876501111",
                ProviderType.HOSPITAL,
                "JEV-2026-001",
                "Need access"
        ));
        assertEquals(ProviderType.HOSPITAL, submitted.providerType());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.LoginVerifyResponse> loginResponse = controller.accessLogin(
                request,
                response,
                new ProviderAccessModels.ProviderAccessLoginRequest("provider@example.com", "12345678")
        );

        assertNotNull(loginResponse.getBody());
        assertEquals(true, loginResponse.getBody().verified());
        assertNotNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void nonAccessApprovalModesRejectControlledAccessEndpoints() {
        ProviderPortalAuthProperties properties = new ProviderPortalAuthProperties();
        properties.setMode(ProviderPortalAuthProperties.Mode.DEV_OTP);
        ProviderAccessAuthController controller = new ProviderAccessAuthController(accessRequestService, properties);

        assertThrows(ForbiddenException.class, () -> controller.submit(new ProviderAccessModels.ProviderAccessRequestSubmitRequest(
                "Jeevanam Multispeciality Hospital",
                "provider@example.com",
                "9876501111",
                ProviderType.HOSPITAL,
                null,
                null
        )));

        assertThrows(ForbiddenException.class, () -> controller.accessLogin(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new ProviderAccessModels.ProviderAccessLoginRequest("provider@example.com", "12345678")
        ));
    }
}
