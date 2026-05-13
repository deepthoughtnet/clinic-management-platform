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
        when(repository.countByTenantId(tenantId)).thenReturn(20L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.NEW)).thenReturn(5L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.QUALIFIED)).thenReturn(4L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.CONVERTED)).thenReturn(6L);
        when(repository.countByTenantIdAndStatus(tenantId, LeadStatus.LOST)).thenReturn(2L);
        when(repository.countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(tenantId)).thenReturn(3L);
        when(repository.countByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(3L);

        LeadEntity staleHigh = LeadEntity.create(tenantId, UUID.randomUUID());
        staleHigh.setFirstName("A");
        staleHigh.setPhone("+15550000");
        staleHigh.setSource(LeadSource.WEBSITE);
        staleHigh.setPriority(LeadPriority.HIGH);
        staleHigh.setStatus(LeadStatus.CONTACTED);
        when(repository.findByTenantIdAndCreatedAtBetween(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(staleHigh));
        when(repository.findByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(staleHigh));

        var row = service.summary(tenantId, null, null);
        assertThat(row.totalLeads()).isEqualTo(20);
        assertThat(row.convertedLeads()).isEqualTo(6);
        assertThat(row.followUpsDue()).isEqualTo(3);
        assertThat(row.conversionRate()).isEqualTo(30d);
    }
}
