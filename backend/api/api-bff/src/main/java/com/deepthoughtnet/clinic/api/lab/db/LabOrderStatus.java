package com.deepthoughtnet.clinic.api.lab.db;

public enum LabOrderStatus {
    ORDERED,
    PAYMENT_PENDING,
    PAID,
    READY_FOR_COLLECTION,
    SAMPLE_COLLECTED,
    PROCESSING,
    RESULT_ENTERED,
    REPORT_READY,
    REPORT_GENERATED,
    DOCTOR_REVIEWED,
    DELIVERED,
    CANCELLED
}
