package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarePilotLeadCsvServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private LeadService leadService;
    private LeadRepository leadRepository;
    private CampaignRepository campaignRepository;
    private TenantUserManagementService tenantUserManagementService;
    private LeadActivityService leadActivityService;
    private CarePilotLeadCsvService service;

    @BeforeEach
    void setUp() {
        leadService = mock(LeadService.class);
        leadRepository = mock(LeadRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        tenantUserManagementService = mock(TenantUserManagementService.class);
        leadActivityService = mock(LeadActivityService.class);
        service = new CarePilotLeadCsvService(leadService, leadRepository, campaignRepository, tenantUserManagementService, leadActivityService);
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of());
        when(leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByTenantIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void importCsvImportsValidRowsAndReportsInvalidEnumRow() throws Exception {
        when(leadService.create(eq(tenantId), any(), eq(actorId))).thenReturn(lead("Ava", "+15550123", "ava@example.com"));
        String csv = """
                firstName,lastName,phone,email,source,status,priority,campaignName,assignedToEmail,nextFollowUpAt,sourceDetails,tags,notes
                Ava,Smith,+15550123,ava@example.com,WEBINAR,NEW,MEDIUM,,,,registered,tag,note
                Ben,Jones,+15550124,ben@example.com,NOT_A_SOURCE,NEW,MEDIUM,,,,registered,tag,note
                """;

        var result = service.importCsv(tenantId, csv.getBytes(StandardCharsets.UTF_8), actorId);

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rowErrors()).singleElement().extracting("rowNumber").isEqualTo(3);
        verify(leadService).create(eq(tenantId), any(), eq(actorId));
    }

    @Test
    void importCsvSkipsDuplicatesByPhoneOrEmail() throws Exception {
        when(leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, "+15550123")).thenReturn(Optional.of(mock(com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity.class)));
        String csv = """
                firstName,lastName,phone,email,source,status,priority,campaignName,assignedToEmail,nextFollowUpAt,sourceDetails,tags,notes
                Ava,Smith,+15550123,ava@example.com,WEBINAR,NEW,MEDIUM,,,,registered,tag,note
                """;

        var result = service.importCsv(tenantId, csv.getBytes(StandardCharsets.UTF_8), actorId);

        assertThat(result.importedCount()).isZero();
        assertThat(result.skippedDuplicateCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(leadService, never()).create(eq(tenantId), any(), eq(actorId));
    }

    @Test
    void exportCsvIncludesMappedCampaignAndAssignee() throws Exception {
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CampaignEntity campaign = mock(CampaignEntity.class);
        when(campaign.getId()).thenReturn(campaignId);
        when(campaign.getName()).thenReturn("Spring Webinar");
        when(campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(campaign));
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(new TenantUserRecord(
                userId,
                tenantId,
                null,
                "owner@example.com",
                "Owner",
                "ACTIVE",
                "CLINIC_ADMIN",
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "EXISTING"
        )));
        when(leadService.searchAll(eq(tenantId), any(LeadSearchCriteria.class))).thenReturn(List.of(new LeadRecord(
                UUID.randomUUID(),
                tenantId,
                "Ava",
                "Smith",
                "Ava Smith",
                "+15550123",
                "ava@example.com",
                PatientGender.FEMALE,
                null,
                LeadSource.WEBINAR,
                "registered",
                campaignId,
                userId,
                LeadStatus.NEW,
                LeadPriority.MEDIUM,
                "note",
                "tag",
                null,
                null,
                null,
                null,
                null,
                actorId,
                actorId,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));

        String csv = service.exportCsv(tenantId, new LeadSearchCriteria(null, null, null, null, null, false, null, null));

        assertThat(csv).contains("campaignName").contains("assignedToEmail");
        assertThat(csv).contains("Spring Webinar").contains("owner@example.com");
    }

    @Test
    void templateHasExpectedHeader() throws Exception {
        String csv = service.importTemplateCsv();
        assertThat(csv).contains("firstName,lastName,phone,email,source,status,priority,campaignName,assignedToEmail,nextFollowUpAt,sourceDetails,tags,notes");
    }

    private LeadRecord lead(String firstName, String phone, String email) {
        return new LeadRecord(
                UUID.randomUUID(),
                tenantId,
                firstName,
                null,
                firstName,
                phone,
                email,
                PatientGender.UNKNOWN,
                null,
                LeadSource.MANUAL,
                null,
                null,
                null,
                LeadStatus.NEW,
                LeadPriority.MEDIUM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                actorId,
                actorId,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
