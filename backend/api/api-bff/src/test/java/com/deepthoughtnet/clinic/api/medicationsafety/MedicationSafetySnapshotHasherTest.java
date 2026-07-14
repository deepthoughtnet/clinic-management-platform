package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicationSafetySnapshotHasherTest {

    private final MedicationSafetySnapshotHasher hasher = new MedicationSafetySnapshotHasher();

    @Test
    void prescriptionHashIgnoresTransientPrescriptionRowIdentifiers() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String medicineId = UUID.randomUUID().toString();

        MedicationSafetyEvaluationRequest first = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-1", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "1 tab", "1-0-0")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );
        MedicationSafetyEvaluationRequest second = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-2", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "1 tab", "1-0-0")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );

        assertThat(hasher.prescriptionHash(first, 7)).isEqualTo(hasher.prescriptionHash(second, 7));
    }

    @Test
    void prescriptionHashChangesForSafetyRelevantMedicationChanges() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String medicineId = UUID.randomUUID().toString();

        MedicationSafetyEvaluationRequest base = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-1", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "1 tab", "1-0-0")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );
        MedicationSafetyEvaluationRequest doseChanged = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-1", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "2 tab", "1-0-0")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );

        assertThat(hasher.prescriptionHash(base, 7)).isNotEqualTo(hasher.prescriptionHash(doseChanged, 7));
    }

    @Test
    void prescriptionHashChangesForFrequencyChanges() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String medicineId = UUID.randomUUID().toString();

        MedicationSafetyEvaluationRequest base = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-1", medicineId, "Cetirizine 10 mg", "Cetirizine", "10 mg", "1 tab", "0-1-0")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );
        MedicationSafetyEvaluationRequest frequencyChanged = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "DRAFT",
                List.of(medication("row-1", medicineId, "Cetirizine 10 mg", "Cetirizine", "10 mg", "1 tab", "0-0-1")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );

        assertThat(hasher.prescriptionHash(base, 7)).isNotEqualTo(hasher.prescriptionHash(frequencyChanged, 7));
    }

    @Test
    void prescriptionHashIgnoresLifecycleOnlyMedicationItemStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String medicineId = UUID.randomUUID().toString();

        MedicationSafetyEvaluationRequest previewed = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "PREVIEWED",
                List.of(medication("row-1", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "1 tab", "1-0-0", "PREVIEWED")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );
        MedicationSafetyEvaluationRequest finalized = new MedicationSafetyEvaluationRequest(
                tenantId,
                patientId,
                consultationId,
                prescriptionId,
                "FINALIZED",
                List.of(medication("row-1", medicineId, "Paracetamol 500 mg", "Paracetamol", "500 mg", "1 tab", "1-0-0", "FINALIZED")),
                List.of(),
                null,
                List.of(),
                null,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );

        assertThat(hasher.prescriptionHash(previewed, 7)).isEqualTo(hasher.prescriptionHash(finalized, 7));
    }

    private MedicationSafetyMedicationItem medication(
            String prescriptionItemId,
            String medicineId,
            String medicineName,
            String ingredient,
            String strength,
            String dose,
            String frequency
    ) {
        return medication(prescriptionItemId, medicineId, medicineName, ingredient, strength, dose, frequency, "DRAFT");
    }

    private MedicationSafetyMedicationItem medication(
            String prescriptionItemId,
            String medicineId,
            String medicineName,
            String ingredient,
            String strength,
            String dose,
            String frequency,
            String status
    ) {
        return new MedicationSafetyMedicationItem(
                prescriptionItemId,
                medicineId,
                medicineName,
                medicineName.toLowerCase(),
                List.of(ingredient),
                "ANALGESIC",
                strength,
                "mg",
                dose,
                "tab",
                frequency,
                "5 days",
                "AFTER_FOOD",
                "Pain",
                false,
                "PRESCRIPTION_DRAFT",
                status,
                "UNAVAILABLE",
                BigDecimal.ONE,
                "source-document-1",
                "Prescription draft",
                LocalDate.of(2026, 7, 13)
        );
    }
}
