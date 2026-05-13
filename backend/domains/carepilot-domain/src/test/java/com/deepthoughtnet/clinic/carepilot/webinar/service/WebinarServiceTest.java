package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarUpsertCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebinarServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private WebinarRepository repository;
    private WebinarService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebinarRepository.class);
        service = new WebinarService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWebinar() {
        var row = service.create(tenantId, new WebinarUpsertCommand(
                "Diabetes Awareness", "Session", WebinarType.HEALTH_AWARENESS, WebinarStatus.SCHEDULED,
                "https://example.com/w/1", "Admin", "admin@example.com",
                OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(1), "UTC", 100,
                true, true, true, "care"
        ), actorId);

        assertThat(row.title()).isEqualTo("Diabetes Awareness");
        assertThat(row.status()).isEqualTo(WebinarStatus.SCHEDULED);
    }
}
