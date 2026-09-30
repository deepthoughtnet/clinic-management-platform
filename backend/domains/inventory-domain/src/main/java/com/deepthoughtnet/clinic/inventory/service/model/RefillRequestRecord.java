package com.deepthoughtnet.clinic.inventory.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefillRequestRecord(
        UUID id, UUID tenantId, UUID patientId, UUID prescriptionId, String refillCycle,
        String medicineSummary, LocalDate dueDate, String status, String source,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) { }
