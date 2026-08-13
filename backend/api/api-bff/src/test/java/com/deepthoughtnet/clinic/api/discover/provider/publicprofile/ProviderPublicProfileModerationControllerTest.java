package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionPrincipal;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftVersionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileSubmissionEligibilityRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ProviderPublicProfileModerationControllerTest {
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
    private static final String PUBLIC_PROFILE_REFERENCE = "2206731d-3f34-426f-b069-2abca255f988";

    @Mock
    private ProviderPublicProfileModerationService moderationService;
    @Mock
    private ProviderPublicProfileDraftService draftService;
    @InjectMocks
    private ProviderPublicProfileModerationController controller;

    @Test
    void moderationUsesDraftTenantConsentStatusWhenEnabled() {
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(draft("ENABLED")));
        PublicProfileSubmissionEligibilityRecord eligibility = new PublicProfileSubmissionEligibilityRecord(
                true,
                List.of(),
                List.of("SUBMIT_FOR_REVIEW"),
                "NOT_SUBMITTED",
                "UNPUBLISHED",
                null,
                null,
                null,
                null,
                3,
                true,
                "/discover/hospitals/jeevanam-multispeciality-hospital"
        );
        when(moderationService.submissionEligibility(eq(PROVIDER_ACCOUNT_ID), eq(PUBLIC_PROFILE_REFERENCE), eq(true))).thenReturn(eligibility);

        ResponseEntity<?> response = controller.moderation(authentication(), PUBLIC_PROFILE_REFERENCE);

        assertThat(response.getBody()).isEqualTo(eligibility);
        verify(moderationService).submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);
    }

    @Test
    void moderationUsesDraftTenantConsentStatusWhenDisabled() {
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(draft("DISABLED")));
        PublicProfileSubmissionEligibilityRecord eligibility = new PublicProfileSubmissionEligibilityRecord(
                false,
                List.of("TENANT_CONSENT_REQUIRED"),
                List.of("VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE"),
                "NOT_SUBMITTED",
                "UNPUBLISHED",
                null,
                null,
                null,
                null,
                3,
                true,
                "/discover/hospitals/jeevanam-multispeciality-hospital"
        );
        when(moderationService.submissionEligibility(eq(PROVIDER_ACCOUNT_ID), eq(PUBLIC_PROFILE_REFERENCE), eq(false))).thenReturn(eligibility);

        ResponseEntity<?> response = controller.moderation(authentication(), PUBLIC_PROFILE_REFERENCE);

        assertThat(response.getBody()).isEqualTo(eligibility);
        verify(moderationService).submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, false);
    }

    private Authentication authentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new ProviderSessionPrincipal(PROVIDER_ACCOUNT_ID, UUID.randomUUID(), Set.of("PROVIDER")));
        return authentication;
    }

    private PublicProfileDraftWorkspaceRecord draft(String tenantConsentStatus) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T04:11:11Z");
        PublicProfileDraftReadinessRecord readiness = new PublicProfileDraftReadinessRecord(
                "READY",
                true,
                100,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                3
        );
        return new PublicProfileDraftWorkspaceRecord(
                UUID.randomUUID(),
                "draft-reference",
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                PROVIDER_ACCOUNT_ID,
                "VERIFIED",
                tenantConsentStatus,
                "PUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                3,
                now,
                now,
                now,
                now,
                "Jeevanam Multispeciality Hospital",
                "jeevanam-multispeciality-hospital",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "9876501502",
                "hospital@example.com",
                null,
                null,
                null,
                1998,
                "HEALTHCARE_CLINIC_PROFILE",
                "HEALTHCARE_CLINIC_PROFILE",
                1L,
                now,
                "/discover/hospitals/jeevanam-multispeciality-hospital",
                List.of("EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS", "SUBMIT_FOR_REVIEW"),
                List.of(),
                readiness,
                List.<PublicProfileDraftVersionRecord>of(),
                Map.<String, com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord>of()
        );
    }
}
