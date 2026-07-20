package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignApprovalHistoryRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.CampaignService;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceScanConfig.class)
class CarePilotCampaignControllerPostgresRouteTest extends AbstractPostgresDataJpaTest {
    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private com.deepthoughtnet.clinic.carepilot.template.db.CampaignTemplateRepository campaignTemplateRepository;

    @Autowired
    private CampaignApprovalHistoryRepository campaignApprovalHistoryRepository;

    private MockMvc mockMvc;
    private UUID tenantId;
    private UUID seededTemplateId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        seededTemplateId = UUID.randomUUID();
        seedTemplate(seededTemplateId, "Legacy Template", true, OffsetDateTime.parse("2026-07-18T00:00:30Z"));
        seedCampaign(UUID.randomUUID(), "CAM-2026-000001", "Legacy Draft Campaign", "DRAFT", null, OffsetDateTime.parse("2026-07-18T00:00:00Z"));
        seedCampaign(UUID.randomUUID(), "CAM-2026-000002", "Needs Review Campaign", "PENDING_APPROVAL", null, OffsetDateTime.parse("2026-07-18T00:01:00Z"));

        CampaignService campaignService = new CampaignService(
                campaignRepository,
                campaignTemplateRepository,
                campaignApprovalHistoryRepository,
                org.mockito.Mockito.mock(AppUserRepository.class),
                org.mockito.Mockito.mock(JdbcTemplate.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CarePilotCampaignController(
                        campaignService,
                        org.mockito.Mockito.mock(CarePilotCampaignRuntimeService.class),
                        org.mockito.Mockito.mock(CarePilotCampaignTriggerService.class),
                        org.mockito.Mockito.mock(IdempotencyService.class),
                        new ObjectMapper()
                )
        ).build();

        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "clinic.admin@test",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "corr-campaign-route"
        ));
        assertThat(campaignRepository.count()).isGreaterThanOrEqualTo(2L);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void campaignsListLoadsExistingPostgresRows() throws Exception {
        mockMvc.perform(get("/api/carepilot/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Needs Review Campaign"))
                .andExpect(jsonPath("$[1].name").value("Legacy Draft Campaign"));
    }

    @Test
    void lookupLoadsExistingPostgresRows() throws Exception {
        mockMvc.perform(get("/api/carepilot/campaigns/lookup").param("q", "Legacy").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Legacy Draft Campaign"))
                .andExpect(jsonPath("$[0].campaignType").value("CUSTOM"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void approvalNeededLoadsExistingPostgresRows() throws Exception {
        mockMvc.perform(get("/api/carepilot/campaigns/approval-needed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Needs Review Campaign"))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void editAndResubmitUsesTheUpdatedVersionAndTransitionsInOneCall() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        seedCampaign(campaignId, "CAM-2026-000003", "UAT Manual Appointment Campaign", "CHANGES_REQUESTED", reviewerId, OffsetDateTime.parse("2026-07-18T00:02:00Z"));

        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "manager@test",
                Set.of("ENGAGE_MANAGER"),
                "ENGAGE_MANAGER",
                "corr-edit-resubmit"
        ));

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/edit-and-resubmit", campaignId)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "UAT Manual Appointment Campaign",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "Updated description",
                                  "expectedVersion": 0,
                                  "resolutionNote": null
                                }
                                """.formatted(seededTemplateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.notes").value("Updated description"));
    }

    private void seedTemplate(UUID templateId, String name, boolean active, OffsetDateTime createdAt) {
        executeSql("""
                insert into carepilot_campaign_templates (
                    id, tenant_id, name, channel_type, subject_line, body_template, is_active, created_at, updated_at
                ) values (
                    '%s', '%s', '%s', 'EMAIL', 'Template subject', 'Template body', %s, '%s', '%s'
                )
                """.formatted(templateId, tenantId, name, active, createdAt, createdAt));
    }

    private void seedCampaign(UUID campaignId, String campaignReference, String name, String status, UUID reviewedBy, OffsetDateTime createdAt) {
        UUID createdBy = UUID.randomUUID();
        String approvedVersionSql = "CHANGES_REQUESTED".equals(status) || "PENDING_APPROVAL".equals(status) ? "0" : "null";
        String approvedHashSql = "CHANGES_REQUESTED".equals(status) || "PENDING_APPROVAL".equals(status) ? "'seeded-hash'" : "null";
        String reviewedBySql = reviewedBy == null ? "null" : "'" + reviewedBy + "'";
        String reviewedAtSql = reviewedBy == null ? "null" : "'" + createdAt + "'";
        String reviewCommentSql = reviewedBy == null ? "null" : "'Please update description'";
        executeSql("""
                insert into carepilot_campaigns (
                    id, tenant_id, campaign_reference, name, campaign_type, status, trigger_type, audience_type,
                    template_id, is_active, notes, created_by, created_at, updated_at, version,
                    approved_version, approved_configuration_hash, reviewed_by, reviewed_at, review_comment
                ) values (
                    '%s', '%s', '%s', '%s', 'CUSTOM', '%s', 'MANUAL', 'ALL_PATIENTS',
                    '%s', %s, 'seeded for mapping regression', '%s', '%s', '%s', 0,
                    %s, %s, %s, %s, %s
                )
                """.formatted(campaignId, tenantId, campaignReference, name, status, seededTemplateId, "PENDING_APPROVAL".equals(status), createdBy, createdAt, createdAt, approvedVersionSql, approvedHashSql, reviewedBySql, reviewedAtSql, reviewCommentSql));
    }
}
