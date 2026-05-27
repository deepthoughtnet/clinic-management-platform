package com.deepthoughtnet.clinic.api.consultation.service;

import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultationCompletionGuard {
    public static final String PRESCRIPTION_INCOMPLETE_MESSAGE = "Please complete/finalize prescription before completing consultation.";

    private final PrescriptionService prescriptionService;

    public ConsultationCompletionGuard(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    public void ensurePrescriptionReady(UUID tenantId, UUID consultationId) {
        PrescriptionRecord prescription = prescriptionService.findByConsultationId(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException(PRESCRIPTION_INCOMPLETE_MESSAGE));
        if (!isReady(prescription.status())) {
            throw new IllegalArgumentException(PRESCRIPTION_INCOMPLETE_MESSAGE);
        }
    }

    private boolean isReady(PrescriptionStatus status) {
        return status == PrescriptionStatus.FINALIZED
                || status == PrescriptionStatus.PRINTED
                || status == PrescriptionStatus.SENT;
    }
}
