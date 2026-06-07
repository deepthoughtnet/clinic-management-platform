package com.deepthoughtnet.clinic.ai.careai.persistence;

public enum CareAiWorkflowState {
    STARTED,
    COLLECTING_INFO,
    WAITING_CONFIRMATION,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    ESCALATED,
    EXPIRED
}
