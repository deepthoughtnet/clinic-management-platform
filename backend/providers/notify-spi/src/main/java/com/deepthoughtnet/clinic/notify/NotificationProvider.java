package com.deepthoughtnet.clinic.notify;

public interface NotificationProvider {
    void send(NotificationMessage message);
}
