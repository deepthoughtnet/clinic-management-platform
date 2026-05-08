package com.deepthoughtnet.clinic.consultation.service;

public final class ConsultationVitalsCalculator {
    private ConsultationVitalsCalculator() {
    }

    public static Double calculateBmi(Double weightKg, Double heightCm) {
        if (weightKg == null || heightCm == null || heightCm <= 0.0) {
            return null;
        }
        double heightM = heightCm / 100.0;
        if (heightM <= 0.0) {
            return null;
        }
        return weightKg / (heightM * heightM);
    }

    public static String bmiCategory(Double weightKg, Double heightCm) {
        Double bmi = calculateBmi(weightKg, heightCm);
        if (bmi == null) {
            return null;
        }
        if (bmi < 18.5) {
            return "Underweight";
        }
        if (bmi < 25.0) {
            return "Normal";
        }
        if (bmi < 30.0) {
            return "Overweight";
        }
        return "Obese";
    }
}
