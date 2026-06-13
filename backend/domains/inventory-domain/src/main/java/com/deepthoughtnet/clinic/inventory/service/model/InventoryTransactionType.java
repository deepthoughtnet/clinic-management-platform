package com.deepthoughtnet.clinic.inventory.service.model;

public enum InventoryTransactionType {
    STOCK_IN,
    DISPENSED,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    RETURN,
    CUSTOMER_RETURN_IN,
    CUSTOMER_RETURN_NON_SELLABLE,
    VENDOR_RETURN_OUT,
    WRITE_OFF,
    EXPIRED,
    CANCELLED_DISPENSE,
    OPENING,
    PURCHASE,
    SALE,
    ADJUSTMENT,
    TRANSFER_IN,
    TRANSFER_OUT
}
