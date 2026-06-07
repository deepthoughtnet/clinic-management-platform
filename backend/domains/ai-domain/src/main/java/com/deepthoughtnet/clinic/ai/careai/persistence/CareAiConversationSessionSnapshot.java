package com.deepthoughtnet.clinic.ai.careai.persistence;

import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiMessageEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiPendingConfirmationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import java.util.List;

public record CareAiConversationSessionSnapshot(
        CareAiConversationEntity conversation,
        CareAiWorkflowEntity workflow,
        CareAiPendingConfirmationEntity pendingConfirmation,
        List<CareAiMessageEntity> recentMessages
) {
}
