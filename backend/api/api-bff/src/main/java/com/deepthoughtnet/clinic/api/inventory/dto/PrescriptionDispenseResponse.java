package com.deepthoughtnet.clinic.api.inventory.dto;

import java.util.List;
import java.util.UUID;

public record PrescriptionDispenseResponse(
        UUID prescriptionId,
        String prescriptionNumber,
        UUID patientId,
        String patientName,
        String billingStatus,
        UUID billedBillId,
        List<PrescriptionDispenseLineResponse> lines
) {
}
