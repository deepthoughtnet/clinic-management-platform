package com.deepthoughtnet.clinic.carepilot.lead.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LeadPresentationLabelsTest {

    @Test
    void mapsLeadEnumsToBusinessLabelsWithoutChangingEnumValues() {
        assertThat(LeadPresentationLabels.sourceLabel(LeadSource.AI_RECEPTIONIST)).isEqualTo("AI Receptionist");
        assertThat(LeadPresentationLabels.sourceLabel(LeadSource.WALK_IN)).isEqualTo("Walk-in");
        assertThat(LeadPresentationLabels.statusLabel(LeadStatus.FOLLOW_UP_REQUIRED)).isEqualTo("Follow-up Required");
        assertThat(LeadPresentationLabels.priorityLabel(LeadPriority.MEDIUM)).isEqualTo("Medium");
        assertThat(LeadPresentationLabels.activityLabel(LeadActivityType.FOLLOW_UP_COMPLETED)).isEqualTo("Follow-up Completed");
        assertThat(LeadPresentationLabels.statusTransitionLabel(LeadStatus.CONTACTED, LeadStatus.CONVERTED)).isEqualTo("Contacted -> Converted");
        assertThat(LeadSource.WALK_IN.name()).isEqualTo("WALK_IN");
        assertThat(LeadStatus.CONVERTED.name()).isEqualTo("CONVERTED");
    }

    @Test
    void formatsBusinessFacingTimestampsWithTenantZoneLabel() {
        String formatted = LeadPresentationLabels.formatDateTime(
                OffsetDateTime.parse("2026-07-21T05:30:00Z"),
                ZoneId.of("Asia/Kolkata")
        );

        assertThat(formatted).isEqualTo("21 Jul 2026, 11:00 AM IST (UTC+05:30)");
    }
}
