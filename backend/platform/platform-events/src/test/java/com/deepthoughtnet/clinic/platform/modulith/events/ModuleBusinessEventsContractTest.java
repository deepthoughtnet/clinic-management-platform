package com.deepthoughtnet.clinic.platform.modulith.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.modulith.events.model.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LeadConvertedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ModuleBusinessEventsContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void appointmentBookedEventIsTypedVersionedAndSerializable() throws Exception {
        MDC.put("correlationId", "corr-appointment");
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        AppointmentBookedEvent event = ModuleBusinessEvents.appointmentBooked(
                tenantId,
                aggregateId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 7, 22),
                LocalTime.of(11, 0),
                "BOOKED",
                "SCHEDULED",
                UUID.randomUUID()
        );

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.payload().appointmentId()).isEqualTo(aggregateId);
        assertThat(event.correlationId()).isEqualTo("corr-appointment");
        assertThat(objectMapper.writeValueAsString(event))
                .contains("\"eventType\":\"APPOINTMENT_BOOKED\"")
                .contains("\"eventVersion\":1")
                .contains("\"tenantId\":\"" + tenantId + "\"");
    }

    @Test
    void labReportPublishedEventUsesCurrentCorrelationContext() {
        MDC.put("X-Correlation-ID", "corr-lab");
        LabReportPublishedEvent event = ModuleBusinessEvents.labReportPublished(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "report.pdf",
                "DELIVERED",
                UUID.randomUUID()
        );

        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.correlationId()).isEqualTo("corr-lab");
        assertThat(event.payload().reportFilename()).isEqualTo("report.pdf");
    }

    @Test
    void leadConvertedEventGeneratesStableDeterministicEventId() {
        UUID tenantId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        LeadConvertedEvent first = ModuleBusinessEvents.leadConverted(tenantId, leadId, UUID.randomUUID(), true, null, UUID.randomUUID());
        LeadConvertedEvent second = ModuleBusinessEvents.leadConverted(tenantId, leadId, UUID.randomUUID(), false, UUID.randomUUID(), UUID.randomUUID());

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.eventVersion()).isEqualTo(1);
        assertThat(first.payload().leadId()).isEqualTo(leadId);
    }
}
