package com.deepthoughtnet.clinic.notification.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentBookedNotificationListenerTest {

    @Test
    void delegatesAppointmentBookedEventsToNotificationService() {
        AppointmentBookedNotificationService notificationService = mock(AppointmentBookedNotificationService.class);
        AppointmentBookedNotificationListener listener = new AppointmentBookedNotificationListener(notificationService);
        AppointmentBookedEvent event = AppointmentBookedEvent.booked(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Dr. Test",
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "Asia/Kolkata",
                "BOOKED",
                "SCHEDULED",
                UUID.randomUUID()
        );

        listener.handle(event);

        verify(notificationService).queue(event);
    }
}
