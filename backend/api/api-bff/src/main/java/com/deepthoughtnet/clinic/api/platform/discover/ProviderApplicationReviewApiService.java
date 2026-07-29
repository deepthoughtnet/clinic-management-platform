package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentContentRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderReviewDetailRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderReviewSummaryRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProviderApplicationReviewApiService {
    private final ProviderOnboardingService onboardingService;

    public ProviderApplicationReviewApiService(ProviderOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    public List<ProviderReviewSummaryRecord> list(
            List<ProviderLifecycleStatus> statuses,
            ProviderType providerType,
            String search
    ) {
        return onboardingService.listReviewApplications(statuses, providerType, search);
    }

    public ProviderReviewDetailRecord get(String referenceNumber) {
        return onboardingService.getApplicationForReview(referenceNumber);
    }

    public ProviderApplicationRecord startReview(String referenceNumber, String reason) {
        return onboardingService.startReview(resolveId(referenceNumber), reason);
    }

    public ProviderApplicationRecord requestChanges(String referenceNumber, String reason, List<String> requestedSections) {
        return onboardingService.requestChanges(resolveId(referenceNumber), reason, requestedSections);
    }

    public ProviderApplicationRecord approve(String referenceNumber, String reason) {
        return onboardingService.approve(resolveId(referenceNumber), reason);
    }

    public ProviderApplicationRecord publish(String referenceNumber, String reason) {
        return onboardingService.publish(resolveId(referenceNumber), reason);
    }

    public DocumentContentRecord documentContent(String referenceNumber, UUID documentId) {
        return onboardingService.reviewDocumentContent(resolveId(referenceNumber), documentId);
    }

    private UUID resolveId(String referenceNumber) {
        return get(referenceNumber).application().id();
    }
}
