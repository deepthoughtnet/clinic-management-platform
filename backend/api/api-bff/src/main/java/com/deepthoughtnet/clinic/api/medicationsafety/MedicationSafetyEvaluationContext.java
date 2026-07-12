package com.deepthoughtnet.clinic.api.medicationsafety;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;

record MedicationSafetyEvaluationContext(
        ConsultationEntity consultation,
        PatientEntity patient,
        PrescriptionEntity prescription,
        ClinicalContextResponse clinicalContext,
        MedicationSafetyEvaluationRequest request,
        String prescriptionHash,
        String patientContextHash,
        String snapshotHash
) {
}
