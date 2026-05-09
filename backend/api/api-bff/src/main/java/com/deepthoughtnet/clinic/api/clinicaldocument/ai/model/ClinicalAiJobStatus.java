package com.deepthoughtnet.clinic.api.clinicaldocument.ai.model;

public enum ClinicalAiJobStatus {
    QUEUED,
    PROCESSING,
    REVIEW_REQUIRED,
    SUCCEEDED,
    FAILED,
    RETRY_SCHEDULED
}
