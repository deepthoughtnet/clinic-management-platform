package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpecialtyResolverTest {
    private final PatientPortalCareAiEntityRegistry entityRegistry = new PatientPortalCareAiEntityRegistry();
    private final SpecialtyResolver resolver = new SpecialtyResolver(entityRegistry);

    @Test
    void resolvesGeneralMedicineAliasesToCanonicalSpecialty() {
        List<String> supported = List.of("General Medicine", "Cardiology");
        for (String phrase : List.of(
                "General Medicine",
                "General Physician",
                "general physician",
                "physician",
                "GP",
                "speciality General Medicine",
                "specialty General Medicine",
                "department General Medicine",
                "Find a general physician",
                "general medicine ka doctor",
                "mujhe general physician chahiye",
                "जनरल फिजिशियन",
                "जनरल मेडिसिन"
        )) {
            SpecialtyResolver.SpecialtyResolution resolution = resolver.resolve(phrase, supported);
            assertThat(resolution.resolved())
                    .as("phrase=%s", phrase)
                    .isTrue();
            assertThat(resolution.canonicalSpecialty())
                    .as("phrase=%s", phrase)
                    .isEqualTo("General Medicine");
        }
    }

    @Test
    void leavesUnsupportedSpecialtyUnresolvedWithoutInventingMapping() {
        SpecialtyResolver.SpecialtyResolution resolution = resolver.resolve("quantum medicine", List.of("General Medicine"));

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.status()).isEqualTo(SpecialtyResolver.SpecialtyResolutionStatus.UNRESOLVED);
        assertThat(resolution.candidateText()).isEqualTo("quantum medicine");
    }

    @Test
    void leavesUnknownSpecialtyUnresolvedSoTheWorkflowCanClarifySafely() {
        SpecialtyResolver.SpecialtyResolution resolution = resolver.resolve(
                "quantum medicine",
                List.of("General Medicine", "Cardiology")
        );

        assertThat(resolution.status()).isEqualTo(SpecialtyResolver.SpecialtyResolutionStatus.UNRESOLVED);
        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.candidateText()).isEqualTo("quantum medicine");
    }
}
