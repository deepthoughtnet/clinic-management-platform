package com.deepthoughtnet.clinic.appointment.service.model;

public enum AppointmentPriority {
    URGENT(0),
    MANUAL_PRIORITY(1),
    FOLLOW_UP(2),
    CHILD(3),
    ELDERLY(4),
    NORMAL(5);

    private final int rank;

    AppointmentPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public static AppointmentPriority fromNullable(AppointmentPriority priority) {
        return priority == null ? NORMAL : priority;
    }
}
