package com.deepthoughtnet.clinic.carepilot.messaging.model;

/**
 * Provider-neutral channel classification for CarePilot message dispatch.
 *
 * <p>APP_NOTIFICATION is retained for backward compatibility with existing execution rows,
 * while IN_APP is the preferred name for new callers.
 */
public enum ChannelType {
    EMAIL,
    SMS,
    WHATSAPP,
    IN_APP,
    APP_NOTIFICATION
}
