package com.deepthoughtnet.clinic.billing.service.model;

public enum BillStatus {
    DRAFT,
    UNPAID,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELLED
}
