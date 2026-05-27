package com.deepthoughtnet.clinic.appointment.service.model;

import java.math.BigDecimal;

public record AppointmentStatusUpdateCommand(
        AppointmentStatus status,
        String comment,
        String paymentBypassReason,
        String paymentBypassNotes,
        BigDecimal paymentBypassDueAmount
) {
    public AppointmentStatusUpdateCommand(AppointmentStatus status, String comment) {
        this(status, comment, null, null, null);
    }
}
