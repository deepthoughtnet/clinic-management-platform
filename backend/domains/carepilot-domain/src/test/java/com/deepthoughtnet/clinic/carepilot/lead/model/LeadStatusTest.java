package com.deepthoughtnet.clinic.carepilot.lead.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeadStatusTest {
    @Test
    void activePipelineExcludesTerminalStatuses() {
        assertThat(LeadStatus.NEW.isActivePipeline()).isTrue();
        assertThat(LeadStatus.CONTACTED.isActivePipeline()).isTrue();
        assertThat(LeadStatus.QUALIFIED.isActivePipeline()).isTrue();
        assertThat(LeadStatus.FOLLOW_UP_REQUIRED.isActivePipeline()).isTrue();
        assertThat(LeadStatus.APPOINTMENT_BOOKED.isActivePipeline()).isTrue();
        assertThat(LeadStatus.CONVERTED.isActivePipeline()).isFalse();
        assertThat(LeadStatus.LOST.isActivePipeline()).isFalse();
        assertThat(LeadStatus.SPAM.isActivePipeline()).isFalse();
    }
}
