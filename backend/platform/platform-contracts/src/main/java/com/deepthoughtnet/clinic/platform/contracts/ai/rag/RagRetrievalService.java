package com.deepthoughtnet.clinic.platform.contracts.ai.rag;

public interface RagRetrievalService {
    RagSearchResult search(RagSearchRequest request);
}
