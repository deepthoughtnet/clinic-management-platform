package com.deepthoughtnet.clinic.api.platform.commercial.subscription;

import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.CreateSubscriptionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.LifecycleActionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ReplaceSubscriptionRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionDetailResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionHistoryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionStatusCountsResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.subscription.dto.CommercialSubscriptionDtos.ValidationResultResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommercialSubscriptionApiService {
    private final CommercialSubscriptionService delegate;
    private final ObjectMapper objectMapper;

    public CommercialSubscriptionApiService(CommercialSubscriptionService delegate, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.objectMapper = objectMapper;
    }

    public SubscriptionStatusCountsResponse getStatusCounts() {
        return map(delegate.getStatusCounts(), SubscriptionStatusCountsResponse.class);
    }

    public PageResponse<SubscriptionSummaryResponse> listSubscriptions(String search, UUID tenantId, UUID planTemplateId, com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus status, int page, int size) {
        return mapPage(delegate.listSubscriptions(search, tenantId, planTemplateId, status, page, size), SubscriptionSummaryResponse.class);
    }

    public SubscriptionDetailResponse getSubscription(UUID id) {
        return map(delegate.getSubscription(id), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse createSubscription(CreateSubscriptionRequest request) {
        return map(delegate.createAssignment(map(request, CommercialSubscriptionModels.CreateSubscriptionRequest.class)), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse activate(UUID id, LifecycleActionRequest request) {
        return map(delegate.activate(id, map(request, CommercialSubscriptionModels.LifecycleActionRequest.class)), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse pause(UUID id, LifecycleActionRequest request) {
        return map(delegate.pause(id, map(request, CommercialSubscriptionModels.LifecycleActionRequest.class)), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse resume(UUID id, LifecycleActionRequest request) {
        return map(delegate.resume(id, map(request, CommercialSubscriptionModels.LifecycleActionRequest.class)), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse cancel(UUID id, LifecycleActionRequest request) {
        return map(delegate.cancel(id, map(request, CommercialSubscriptionModels.LifecycleActionRequest.class)), SubscriptionDetailResponse.class);
    }

    public SubscriptionDetailResponse replace(UUID id, ReplaceSubscriptionRequest request) {
        return map(delegate.replace(id, map(request, CommercialSubscriptionModels.ReplaceSubscriptionRequest.class)), SubscriptionDetailResponse.class);
    }

    public List<SubscriptionHistoryResponse> history(UUID id) {
        return delegate.loadHistory(id).stream().map(item -> objectMapper.convertValue(item, SubscriptionHistoryResponse.class)).toList();
    }

    public ValidationResultResponse validate(CreateSubscriptionRequest request) {
        return map(delegate.validateAssignment(map(request, CommercialSubscriptionModels.CreateSubscriptionRequest.class)), ValidationResultResponse.class);
    }

    private <S, T> T map(S source, Class<T> targetClass) {
        return objectMapper.convertValue(source, targetClass);
    }

    private <S, T> PageResponse<T> mapPage(com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.PageResponse<S> source, Class<T> targetClass) {
        List<T> items = source.items().stream().map(item -> objectMapper.convertValue(item, targetClass)).toList();
        return new PageResponse<>(items, source.page(), source.size(), source.totalElements(), source.totalPages());
    }
}
