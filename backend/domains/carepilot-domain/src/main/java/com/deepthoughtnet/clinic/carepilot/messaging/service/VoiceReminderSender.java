package com.deepthoughtnet.clinic.carepilot.messaging.service;

import com.deepthoughtnet.clinic.messaging.spi.MessageResult;

/** Application port for the already-certified voice reminder communication adapter. */
public interface VoiceReminderSender {
    MessageResult send(ReminderCommunicationRequest request);
}
