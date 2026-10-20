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
                "जनरल मेडिसिन",
                "general position",
                "general physican",
                "general's physician"
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
    void resolvesPediatricsVoiceAliasesToCanonicalSpecialty() {
        for (String phrase : List.of("बच्चों का डॉक्टर", "बच्चों के डॉक्टर", "बाल रोग विशेषज्ञ", "बाल रोग",
                "पेडियाट्रिक्स", "पेडियाट्रिशियन", "पेडिट्रिशियन", "पीडियाट्रिशियन",
                "pediatrician", "pediatrics")) {
            assertThat(resolver.resolve(phrase, List.of("Pediatrics", "General Medicine")).canonicalSpecialty())
                    .as("phrase=%s", phrase)
                    .isEqualTo("Pediatrics");
        }
        assertThat(resolver.canonicalize("मुझे बच्चों के डॉक्टर चाहिए")).isEqualTo("Pediatrics");
        assertThat(resolver.canonicalize("क्या बच्चों का डॉक्टर अवेलेबल है?")).isEqualTo("Pediatrics");
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
