package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceScanConfig.class)
class CarePilotCampaignPostgresMappingTest extends AbstractPostgresDataJpaTest {
    @Autowired
    private CampaignRepository campaignRepository;

    @Test
    void loadsStringBackedEnumsFromExistingPostgresRows() {
        UUID tenantId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-18T00:00:00Z");

        executeSql("""
                insert into carepilot_campaigns (
                    id, tenant_id, campaign_reference, name, campaign_type, status, trigger_type, audience_type,
                    template_id, is_active, notes, created_by, created_at, updated_at, version
                ) values (
                    '%s', '%s', 'CAM-2026-000001', 'Legacy audience campaign', 'CUSTOM', 'DRAFT', 'MANUAL', 'ALL_PATIENTS',
                    null, false, 'legacy row', '%s', '%s', '%s', 0
                )
                """.formatted(campaignId, tenantId, creatorId, createdAt, createdAt));

        CampaignEntity loaded = campaignRepository.findByTenantIdAndId(tenantId, campaignId).orElseThrow();

        assertThat(loaded.getCampaignType()).isEqualTo(CampaignType.CUSTOM);
        assertThat(loaded.getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(loaded.getAudienceType()).isEqualTo(AudienceType.ALL_PATIENTS);
        assertThat(loaded.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(loaded.isActive()).isFalse();
        assertThat(loaded.getCreatedBy()).isEqualTo(creatorId);
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(loaded.getUpdatedAt()).isEqualTo(createdAt);
    }
}
