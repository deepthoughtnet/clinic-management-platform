package com.deepthoughtnet.clinic.ai.careai.task;

import java.util.UUID;

public record CareAiReceptionistTaskFilter(
        CareAiReceptionistTaskStatus status,
        CareAiReceptionistTaskType type,
        CareAiReceptionistTaskPriority priority,
        UUID assignedUserId,
        UUID patientId,
        Boolean overdueOnly,
        Boolean dueSoonOnly
) {
}
