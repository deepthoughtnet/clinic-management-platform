package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void canonicalizesHbA1cWithoutDroppingDigits() {
        assertThat((String) ReflectionTestUtils.invokeMethod(parser, "slug", "HbA1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(parser, "canonicalKeyForLabel", "HbA1c")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(parser, "canonicalKeyForLabel", "HBA1C")).isEqualTo("hba1c");
        assertThat((String) ReflectionTestUtils.invokeMethod(parser, "canonicalKeyForLabel", "Hb A1c")).isEqualTo("hba1c");
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

    @Test
    void parsesFastingGlucoseAliasFromOcrText() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                HbA1c 5.7 % 4.0 - 5.6 High
                Fasting Glucose 102 mg/dL 70 - 99 High
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hba1c", "blood_sugar");
        assertThat(facts).anySatisfy(row -> {
            if ("blood_sugar".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("Fasting Glucose");
                assertThat(row.get("value")).isEqualTo("102");
                assertThat(row.get("flag")).isEqualTo("HIGH");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("hba1c".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("HbA1c");
                assertThat(row.get("value")).isEqualTo("5.7");
                assertThat(row.get("unit")).isEqualTo("%");
                assertThat(row.get("referenceRange")).isEqualTo("4.0 - 5.6");
                assertThat(row.get("flag")).isEqualTo("HIGH");
                assertThat(row.get("evidenceText")).isEqualTo("HbA1c 5.7 % 4.0 - 5.6 High");
            }
        });
    }

    @Test
    void parsesExactHbA1cRowAcrossSupportedAliasVariants() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                HbA1c 5.7 % 4.0 - 5.6 High
                HBA1C 5.7 % 4.0 - 5.6 High
                Hb A1c 5.7 % 4.0 - 5.6 High
                Hemoglobin A1c 5.7 % 4.0 - 5.6 High
                Glycated Hemoglobin 5.7 % 4.0 - 5.6 High
                Glycosylated Hemoglobin 5.7 % 4.0 - 5.6 High
                """, null);

        assertThat(facts).hasSize(1);
        assertThat(facts).first().satisfies(row -> {
            assertThat(row.get("canonicalKey")).isEqualTo("hba1c");
            assertThat(row.get("testName")).isEqualTo("HbA1c");
            assertThat(row.get("value")).isEqualTo("5.7");
            assertThat(row.get("unit")).isEqualTo("%");
            assertThat(row.get("referenceRange")).isEqualTo("4.0 - 5.6");
            assertThat(row.get("flag")).isEqualTo("HIGH");
            assertThat(row.get("evidenceText")).isEqualTo("HbA1c 5.7 % 4.0 - 5.6 High");
        });
    }

    @Test
    void skipsReportTitleAndParsesTheGroundedHbA1cRow() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Lipid Profile & HbA1c Report Date: 18 Mar 2025
                HbA1c 5.7 % 4.0 - 5.6 High
                """, null);

        assertThat(facts).filteredOn(row -> "hba1c".equals(row.get("canonicalKey"))).singleElement()
                .satisfies(row -> {
                    assertThat(row.get("value")).isEqualTo("5.7");
                    assertThat(row.get("evidenceText")).isEqualTo("HbA1c 5.7 % 4.0 - 5.6 High");
                });
    }

    @Test
    void parsesConfirmedCbcRowsFromOcrText() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                RBC 4.82 10^6/uL 4.5 - 5.9 Normal
                WBC 6.8 10^3/uL 4.0 - 11.0 Normal
                Platelets 248 10^3/uL 150 - 450 Normal
                Neutrophils 58 % 40 - 70 Normal
                Lymphocytes 33 % 20 - 40 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hemoglobin", "rbc", "wbc", "platelets", "neutrophils", "lymphocytes");
        assertThat(facts).allSatisfy(row -> assertThat(row.get("flag")).isEqualTo("NORMAL"));
    }

    @Test
    void parsesRbcAndWbcCountAliasesWithExponentUnits() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                RBC Count 4.82 10^6/uL 4.5 - 5.9 Normal
                WBC Count 6.8 10^3/uL 4.0 - 11.0 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("rbc", "wbc");
        assertThat(facts).anySatisfy(row -> {
            if ("rbc".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("4.82");
                assertThat(row.get("unit")).isEqualTo("10^6/uL");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("wbc".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("6.8");
                assertThat(row.get("unit")).isEqualTo("10^3/uL");
            }
        });
    }

    @Test
    void preservesUnknownButValidLabRowsAsUnmappedFacts() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Vitamin XYZ Marker 7.8 ng/mL 5.0 - 10.0 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("unmapped_vitamin_xyz_marker");
        assertThat(facts).anySatisfy(row -> {
            if ("unmapped_vitamin_xyz_marker".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("Vitamin XYZ Marker");
                assertThat(row.get("value")).isEqualTo("7.8");
                assertThat(row.get("unit")).isEqualTo("ng/mL");
            }
        });
    }

    @Test
    void preservesValidAnalyteNamesContainingDigitsAndPunctuation() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                T3 1.2 ng/mL 0.8 - 2.0 Normal
                T4 8.1 ug/dL 5.0 - 12.0 Normal
                Vitamin B12 320 pg/mL 200 - 900 Normal
                25-OH Vitamin D 28 ng/mL 20 - 50 Normal
                CA 19-9 24 U/mL 0 - 37 Normal
                CA-125 18 U/mL 0 - 35 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains(
                        "unmapped_t3",
                        "unmapped_t4",
                        "unmapped_vitamin_b12",
                        "unmapped_25_oh_vitamin_d",
                        "unmapped_ca_19_9",
                        "unmapped_ca_125"
                );
        assertThat(facts).anySatisfy(row -> {
            if ("unmapped_t3".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("T3");
                assertThat(row.get("value")).isEqualTo("1.2");
                assertThat(row.get("unit")).isEqualTo("ng/mL");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("unmapped_25_oh_vitamin_d".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("25-OH Vitamin D");
                assertThat(row.get("value")).isEqualTo("28");
                assertThat(row.get("unit")).isEqualTo("ng/mL");
            }
        });
        assertThat(facts).anySatisfy(row -> {
            if ("unmapped_ca_19_9".equals(row.get("canonicalKey"))) {
                assertThat(row.get("testName")).isEqualTo("CA 19-9");
                assertThat(row.get("value")).isEqualTo("24");
                assertThat(row.get("unit")).isEqualTo("U/mL");
            }
        });
    }

    @Test
    void doesNotBindReportDateNumberToHbA1cValue() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Report Date: 18 Mar 2025
                HbA1c 5.7 % 4.0 - 5.6 High
                """, null);

        assertThat(facts).anySatisfy(row -> {
            if ("hba1c".equals(row.get("canonicalKey"))) {
                assertThat(row.get("value")).isEqualTo("5.7");
                assertThat(row.get("value")).isNotEqualTo("18");
            }
        });
    }

    @Test
    void preservesMixedPanelRowsWithParentheticalAndUnmappedAnalytes() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                ALT (SGPT) 29 U/L 0 - 45 Normal
                Reticulocyte Hemoglobin Equivalent (RET-He) 31.4 pg 28.0 - 35.0 Normal
                Immature Platelet Fraction (IPF) 3.8 % 1.0 - 7.0 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("alt", "unmapped_reticulocyte_hemoglobin_equivalent_ret_he", "unmapped_immature_platelet_fraction_ipf");
        assertThat(facts).allSatisfy(row -> assertThat(row.get("value")).isNotNull());
    }

    @Test
    void doesNotCollapseCompoundAnalytesIntoOrdinaryKnownAnalytes() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                Reticulocyte Hemoglobin Equivalent (RET-He) 31.4 pg 28.0 - 35.0 Normal
                Platelets 248 10^3/uL 150 - 450 Normal
                Immature Platelet Fraction (IPF) 3.8 % 1.0 - 7.0 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .containsExactlyInAnyOrder(
                        "hemoglobin", "unmapped_reticulocyte_hemoglobin_equivalent_ret_he",
                        "platelets", "unmapped_immature_platelet_fraction_ipf");
    }

    @Test
    void doesNotCollapseKnownTokensInsideOtherAnalyteNames() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Glucose-6-Phosphate 4.2 mmol/L 3.0 - 6.0 Normal
                Cholesterol Ester Transfer Protein 2.0 mg/L 1.0 - 3.0 Normal
                Plateletcrit 0.22 % 0.15 - 0.35 Normal
                Reticulocyte Hemoglobin Equivalent (RET-He) 31.4 pg 28.0 - 35.0 Normal
                """, null);

        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("unmapped_glucose_6_phosphate", "unmapped_cholesterol_ester_transfer_protein",
                        "unmapped_plateletcrit", "unmapped_reticulocyte_hemoglobin_equivalent_ret_he")
                .doesNotContain("blood_sugar", "cholesterol", "platelets", "hemoglobin");
    }

    @Test
    void preservesAllRowsInLargeMixedPanel() {
        List<Map<String, Object>> facts = parser.parse(UUID.randomUUID(), """
                Hemoglobin 14.1 g/dL 13.0 - 17.0 Normal
                WBC Count 6.8 10^3/uL 4.0 - 11.0 Normal
                Platelets 248 10^3/uL 150 - 450 Normal
                HbA1c 5.7 % 4.0 - 5.6 High
                Fasting Glucose 102 mg/dL 70 - 99 High
                Creatinine 1.0 mg/dL 0.7 - 1.3 Normal
                ALT (SGPT) 29 U/L 0 - 45 Normal
                TSH 2.1 mIU/L 0.4 - 4.0 Normal
                Apolipoprotein B 92 mg/dL 60 - 120 Normal
                Lipoprotein(a) 18 mg/dL 0 - 30 Normal
                Cystatin C 0.9 mg/L 0.6 - 1.0 Normal
                Homocysteine 11 umol/L 5 - 15 Normal
                Reticulocyte Hemoglobin Equivalent (RET-He) 31.4 pg 28.0 - 35.0 Normal
                Immature Platelet Fraction (IPF) 3.8 % 1.0 - 7.0 Normal
                Soluble Transferrin Receptor 2.4 mg/L 1.8 - 3.5 Normal
                """, null);

        assertThat(facts).hasSize(15);
        assertThat(facts).extracting(row -> row.get("canonicalKey"))
                .contains("hba1c", "alt", "unmapped_reticulocyte_hemoglobin_equivalent_ret_he",
                        "unmapped_immature_platelet_fraction_ipf", "unmapped_tsh");
    }
}
