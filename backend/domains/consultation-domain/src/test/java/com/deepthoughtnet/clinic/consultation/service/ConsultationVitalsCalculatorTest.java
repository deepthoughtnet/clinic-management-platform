package com.deepthoughtnet.clinic.consultation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsultationVitalsCalculatorTest {
    @Test
    void calculatesBmiAndCategory() {
        Double bmi = ConsultationVitalsCalculator.calculateBmi(70.0, 175.0);
        assertThat(bmi).isNotNull();
        assertThat(bmi).isBetween(22.8, 22.9);
        assertThat(ConsultationVitalsCalculator.bmiCategory(70.0, 175.0)).isEqualTo("Normal");
    }

    @Test
    void handlesMissingVitals() {
        assertThat(ConsultationVitalsCalculator.calculateBmi(null, 175.0)).isNull();
        assertThat(ConsultationVitalsCalculator.bmiCategory(null, 175.0)).isNull();
    }
}
