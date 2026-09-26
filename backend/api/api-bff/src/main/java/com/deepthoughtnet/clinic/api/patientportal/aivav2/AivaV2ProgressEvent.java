package com.deepthoughtnet.clinic.api.patientportal.aivav2;

/** Ephemeral presentation feedback; it is never persisted as a conversation turn. */
public record AivaV2ProgressEvent(String operation, String acknowledgement) { }
