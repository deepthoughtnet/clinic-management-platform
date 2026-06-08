package com.deepthoughtnet.clinic.ai.careai.task;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import java.util.List;
import java.util.Map;

public record CareAiReceptionistTaskResumeContext(
        CareAiReceptionistTaskEntity task,
        CareAiConversationEntity conversation,
        CareAiWorkflowEntity workflow,
        List<CareAiMessageEntity> messages,
        Map<String, Object> workflowContext,
        String recommendedNextPrompt
) {
}
