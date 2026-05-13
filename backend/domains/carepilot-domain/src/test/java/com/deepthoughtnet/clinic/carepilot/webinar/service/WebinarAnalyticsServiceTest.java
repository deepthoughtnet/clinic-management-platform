package com.deepthoughtnet.clinic.carepilot.webinar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.analytics.WebinarAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationEntity;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRegistrationRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.db.WebinarRepository;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebinarAnalyticsServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private WebinarRepository webinarRepository;
    private WebinarRegistrationRepository registrationRepository;
    private LeadRepository leadRepository;
    private WebinarAnalyticsService service;

    @BeforeEach
    void setUp() {
        webinarRepository = mock(WebinarRepository.class);
        registrationRepository = mock(WebinarRegistrationRepository.class);
        leadRepository = mock(LeadRepository.class);
        service = new WebinarAnalyticsService(webinarRepository, registrationRepository, leadRepository);
    }

    @Test
    void aggregateSummary() {
        when(webinarRepository.countByTenantId(tenantId)).thenReturn(3L);
        when(webinarRepository.countByTenantIdAndStatus(tenantId, WebinarStatus.COMPLETED)).thenReturn(1L);
        when(webinarRepository.findByTenantIdAndStatusInAndScheduledStartAtBetween(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(registrationRepository.countByTenantId(tenantId)).thenReturn(10L);
        when(registrationRepository.countByTenantIdAndAttendedTrue(tenantId)).thenReturn(6L);
        when(registrationRepository.countByTenantIdAndRegistrationStatus(tenantId, WebinarRegistrationStatus.NO_SHOW)).thenReturn(2L);
        when(leadRepository.countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(tenantId)).thenReturn(1L);
        var r = WebinarRegistrationEntity.create(tenantId, UUID.randomUUID());
        r.setSource(WebinarRegistrationSource.MANUAL);
        when(registrationRepository.findByTenantId(tenantId)).thenReturn(List.of(r));

        var summary = service.summary(tenantId);
        assertThat(summary.totalWebinars()).isEqualTo(3);
        assertThat(summary.attendanceRate()).isEqualTo(60.0);
        assertThat(summary.registrationsBySource()).containsEntry("MANUAL", 1L);
    }
}
