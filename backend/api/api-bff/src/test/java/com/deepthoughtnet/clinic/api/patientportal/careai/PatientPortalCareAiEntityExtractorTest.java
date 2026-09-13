package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientPortalCareAiEntityExtractorTest {
    private final PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();
    private final PatientPortalCareAiEntityExtractor extractor = new PatientPortalCareAiEntityExtractor(registry);

    @Test
    void extractsBareSpecialtyVariantsWithoutPrefix() {
        assertThat(extractor.extract("General Medicine", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("General Physician", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("physician", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("GP", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("speciality General Medicine", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("specialty General Medicine", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("department General Medicine", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("general medicine ka doctor", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("mujhe general physician chahiye", "en").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("जनरल फिजिशियन", "hi").speciality()).isEqualTo("General Medicine");
        assertThat(extractor.extract("जनरल मेडिसिन", "hi").speciality()).isEqualTo("General Medicine");
    }

    @Test
    void extractsServiceAndLocationCandidates() {
        assertThat(extractor.extract("consultation", "en").service()).isEqualTo("consultation");
        assertThat(extractor.extract("health check", "en").service()).isEqualTo("health check");
        assertThat(extractor.extract("Pune", "en").location()).isEqualTo("Pune");
        assertThat(extractor.extract("Baner", "en").location()).isEqualTo("Baner");
    }

    @Test
    void doesNotPromoteDateOrFillerWordsToProviderEntities() {
        for (String phrase : new String[]{"Tomorrow", "Uh, tomorrow?", "tomorrow morning", "कल सुबह"}) {
            PatientPortalCareAiExtractedEntities extracted = extractor.extract(phrase, "en");
            assertThat(extracted.doctor()).as(phrase).isNull();
            assertThat(extracted.clinic()).as(phrase).isNull();
            assertThat(extracted.location()).as(phrase).isNull();
        }
        assertThat(extractor.extract("Show me more", "en").location()).isNull();
        PatientPortalCareAiExtractedEntities booking = extractor.extract("I want to book an appointment", "en");
        assertThat(booking.clinic()).isNull();
    }

    @Test
    void confirmationExtractionHonorsNegation() {
        for (String phrase : new String[]{"don't confirm", "not okay", "no, don't book it", "maybe", "मत कीजिए"}) {
            assertThat(extractor.extract(phrase, "en").confirmation()).as(phrase).isFalse();
        }
        assertThat(extractor.extract("haan book kar do", "en").confirmation()).isTrue();
        assertThat(extractor.extract("हाँ बुक कर दीजिए", "hi").confirmation()).isTrue();
    }
}
