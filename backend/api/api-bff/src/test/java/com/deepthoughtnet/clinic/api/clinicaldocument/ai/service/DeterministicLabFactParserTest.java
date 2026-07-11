package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicLabFactParserTest {

    private final DeterministicLabFactParser parser = new DeterministicLabFactParser();

    @Test
    void parsesExpectedLabFactsFromOcrText() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Hemoglobin 14.1 g/dL 13 - 17 Normal
                HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High
                Estimated Average Glucose 194 mg/dL High
                Random Blood Sugar 198 mg/dL 70 - 140 High
                Total Cholesterol 228 mg/dL < 200 High
                LDL Cholesterol 152 mg/dL < 100 High
                HDL Cholesterol 39 mg/dL > 40 Low
                Triglycerides 238 mg/dL < 150 High
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hba1c", "estimated_average_glucose", "blood_sugar", "cholesterol", "ldl", "hdl", "triglycerides", "hemoglobin");
        assertThat(facts).anySatisfy(row -> {
            if ("hba1c".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("8.4");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("blood_sugar".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("198");
            }
        });
    }

    @Test
    void parsesPipeDelimitedHbA1cTableRowAndEstimatedAverageGlucose() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Test | Result
                HbA1c | 7.3 %
                Estimated Average Glucose | 163 mg/dL
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hba1c", "estimated_average_glucose");
        assertThat(facts).anySatisfy(row -> {
            if ("hba1c".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("7.3");
                assertThat(row.get("unit")).isEqualTo("%");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("estimated_average_glucose".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("163");
                assertThat(row.get("unit")).isEqualTo("mg/dL");
            }
        });
    }

    @Test
    void doesNotParseNarrativeRecommendationTextAsLabs() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Review the elevated HbA1c and discuss abnormal lipid profile.
                Recommend lifestyle modifications.
                Consider tighter glucose monitoring.
                Hemoglobin 14.1 g/dL 13 - 17 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hemoglobin")
                .doesNotContain("hba1c", "blood_sugar");
    }

    @Test
    void supportsHbA1cAliasVariants() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Hb A1c | 7.3 %
                Glycosylated Hemoglobin | 7.3 %
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hba1c");
        assertThat(facts).anySatisfy(row -> {
            if ("hba1c".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("7.3");
                assertThat(row.get("unit")).isEqualTo("%");
            }
        });
    }

    @Test
    void parsesKidneyAndInflammatoryAnalytesFromOcrText() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Creatinine 1.08 mg/dL 0.7 - 1.3
                eGFR 84 mL/min/1.73m2
                CRP 12 mg/L
                ALT 34 U/L
                AST 29 U/L
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("creatinine", "egfr", "crp", "alt", "ast");
        assertThat(facts).anySatisfy(row -> {
            if ("creatinine".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("1.08");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("egfr".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("84");
            }
        });
    }
}
