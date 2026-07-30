package com.deepthoughtnet.clinic.discover.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderServiceTypeCatalogContractTest {

    @Test
    void canonicalServiceCodesMatchTheActiveDiscoverCatalog() {
        List<String> acceptedCodes = Arrays.stream(ProviderOnboardingEnums.ProviderServiceType.values())
                .map(Enum::name)
                .toList();

        assertThat(acceptedCodes).containsExactly(
                "CONSULTATION",
                "TELECONSULTATION",
                "HEALTH_CHECKUPS",
                "VACCINATION",
                "MINOR_PROCEDURES",
                "HOME_VISIT",
                "LAB_COLLECTION",
                "CHRONIC_DISEASE_MANAGEMENT",
                "PREVENTIVE_CARE"
        );
    }
}
