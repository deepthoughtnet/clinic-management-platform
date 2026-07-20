package com.deepthoughtnet.clinic.carepilot.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.lead.analytics.LeadAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadAnalyticsServiceTest {
    private final UUID tenantId = UUID.randomUUID();

    private LeadRepository repository;
    private LeadAnalyticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(LeadRepository.class);
        service = new LeadAnalyticsService(repository);
    }

    @Test
    void summaryReturnsOperationalMetrics() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        OffsetDateTime now = OffsetDateTime.now(zone);
        OffsetDateTime todayStart = now.toLocalDate().atStartOfDay(zone).toOffsetDateTime();
        when(repository.countByTenantId(tenantId)).thenReturn(20L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.NEW)).thenReturn(5L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.QUALIFIED)).thenReturn(4L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.CONVERTED)).thenReturn(6L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.LOST)).thenReturn(2L);
        when(repository.countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(tenantId)).thenReturn(3L);
        when(repository.countByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(2L);

        LeadEntity staleHigh = LeadEntity.create(tenantId, UUID.randomUUID());
        staleHigh.setFirstName("A");
        staleHigh.setPhone("9876543210");
        staleHigh.setSource(LeadSource.WEBSITE);
        staleHigh.setPriority(LeadPriority.HIGH);
        staleHigh.setStatus(LeadStatus.CONTACTED);

        LeadEntity converted = LeadEntity.create(tenantId, UUID.randomUUID());
        converted.setFirstName("B");
        converted.setPhone("9876543211");
        converted.setSource(LeadSource.MANUAL);
        converted.setPriority(LeadPriority.MEDIUM);
        converted.setStatus(LeadStatus.CONVERTED);

        LeadEntity overdue = LeadEntity.create(tenantId, UUID.randomUUID());
        overdue.setFirstName("C");
        overdue.setPhone("9876543212");
        overdue.setSource(LeadSource.MANUAL);
        overdue.setPriority(LeadPriority.MEDIUM);
        overdue.setStatus(LeadStatus.CONTACTED);
        overdue.setNextFollowUpAt(todayStart.minusMinutes(5));

        LeadEntity dueToday = LeadEntity.create(tenantId, UUID.randomUUID());
        dueToday.setFirstName("D");
        dueToday.setPhone("9876543213");
        dueToday.setSource(LeadSource.MANUAL);
        dueToday.setPriority(LeadPriority.MEDIUM);
        dueToday.setStatus(LeadStatus.CONTACTED);
        dueToday.setNextFollowUpAt(todayStart.plusHours(2));

        when(repository.findByTenantIdAndCreatedAtBetween(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(staleHigh, converted, overdue, dueToday));
        when(repository.findByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(overdue, dueToday));

        var row = service.summary(tenantId, zone, null, null);
        assertThat(row.totalLeads()).isEqualTo(20);
        assertThat(row.convertedLeads()).isEqualTo(1);
        assertThat(row.followUpsDue()).isEqualTo(2);
        assertThat(row.followUpsDueToday()).isEqualTo(1);
        assertThat(row.overdueFollowUps()).isEqualTo(1);
        assertThat(row.conversionRate()).isEqualTo(25d);
    }
}
