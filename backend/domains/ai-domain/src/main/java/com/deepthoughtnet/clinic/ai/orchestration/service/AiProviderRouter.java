package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.List;

public interface AiProviderRouter {
    AiProvider resolve(AiTaskType taskType);

    List<AiProvider> resolveCandidates(AiTaskType taskType);

    default List<AiProvider> resolveCandidates(AiProductCode productCode, AiTaskType taskType, String useCaseCode) {
        return resolveCandidates(taskType);
    }
}
