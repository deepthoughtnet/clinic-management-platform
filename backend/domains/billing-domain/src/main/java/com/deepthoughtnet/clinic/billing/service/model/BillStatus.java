package com.deepthoughtnet.clinic.billing.service.model;

public enum BillStatus {
    DRAFT,
    UNPAID,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELLED,
    CANCELLED_REFUNDED
}
