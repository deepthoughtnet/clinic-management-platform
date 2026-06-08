package com.deepthoughtnet.clinic.ai.careai.task;

import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;

public record CareAiReceptionistTaskUpsertResult(
        CareAiReceptionistTaskEntity task,
        boolean created
) {
}
