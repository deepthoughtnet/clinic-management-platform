package com.deepthoughtnet.clinic.api.lab.service.model;

public enum LabOrderStatusRecord {
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
    CANCELLED
}
