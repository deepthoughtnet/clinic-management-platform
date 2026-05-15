package com.deepthoughtnet.clinic.realtime.voice.receptionist;

/**
 * Controlled v1 state model for AI receptionist workflow.
 */
public enum ReceptionistWorkflowState {
    GREETING,
    IDENTIFY_INTENT,
    FAQ_RESPONSE,
    COLLECT_LEAD_DETAILS,
    APPOINTMENT_BOOKING_INTENT,
    COLLECT_APPOINTMENT_DETAILS,
    AVAILABILITY_LOOKUP,
    BOOKING_CONFIRMATION_REQUIRED,
    HUMAN_ESCALATION,
    SESSION_SUMMARY,
    COMPLETED
}
