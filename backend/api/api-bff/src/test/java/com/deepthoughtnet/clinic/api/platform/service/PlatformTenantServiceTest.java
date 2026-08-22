package com.deepthoughtnet.clinic.api.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.identity.db.TenantPlanEntity;
import com.deepthoughtnet.clinic.identity.db.TenantPlanRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionRepository;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningRequest;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningResult;
import com.deepthoughtnet.clinic.identity.service.provisioning.TenantProvisioningService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PlatformTenantServiceTest {

    @Mock private PlatformTenantManagementService platformTenantManagementService;
    @Mock private TenantProvisioningService tenantProvisioningService;
    @Mock private TenantUserManagementService tenantUserManagementService;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantPlanRepository tenantPlanRepository;
    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private TenantModuleService tenantModuleService;
    @Mock private ClinicProfileService clinicProfileService;
    @Mock private ClinicProfileRepository clinicProfileRepository;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private JdbcTemplate jdbcTemplate;

    private PlatformTenantService service;

    @BeforeEach
    void setUp() {
        service = new PlatformTenantService(
                platformTenantManagementService,
                tenantProvisioningService,
                tenantUserManagementService,
                tenantRepository,
                tenantPlanRepository,
                tenantSubscriptionRepository,
                tenantModuleService,
                clinicProfileService,
                clinicProfileRepository,
                auditEventPublisher,
                new ObjectMapper(),
                jdbcTemplate
        );
    }

    @Test
    void createTenantRejectsMissingRegistrationNumberBeforeProvisioning() {
        assertThatThrownBy(() -> service.createTenant(baseCommand(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Registration number is required.");

        verifyNoInteractions(tenantProvisioningService, clinicProfileService);
    }

    @Test
    void createTenantPassesRegistrationNumberIntoClinicProfileBootstrap() {
        UUID tenantId = UUID.randomUUID();
        String registrationNumber = "JEEVANAM-AUTO-REG-001";

        when(tenantRepository.existsByCode("jeevanam-auto")).thenReturn(false);
        when(clinicProfileRepository.existsByEmailIgnoreCase("automation.lab@jeevanam.test")).thenReturn(false);
        when(tenantPlanRepository.findById("ENTERPRISE")).thenReturn(Optional.of(TenantPlanEntity.create("ENTERPRISE", "Enterprise", Map.of())));
        when(tenantProvisioningService.provisionTenant(any(TenantProvisioningRequest.class))).thenReturn(
                new TenantProvisioningResult(tenantId, "jeevanam-auto", "ENTERPRISE", "automation.lab@jeevanam.test", "kc-user-1", UUID.randomUUID())
        );
        when(platformTenantManagementService.get(tenantId)).thenReturn(
                new PlatformTenantRecord(
                        tenantId,
                        "jeevanam-auto",
                        "Jeevanam Automation Lab",
                        "ENTERPRISE",
                        "ACTIVE",
                        false,
                        new TenantModulesRecord(false, false, false, false, false, false, false, false, false, false),
                        OffsetDateTime.parse("2026-08-21T00:00:00Z"),
                        OffsetDateTime.parse("2026-08-21T00:00:00Z")
                )
        );
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.<TenantUserRecord>of());
        when(tenantModuleService.findForTenant(tenantId)).thenReturn(Map.of());
        when(tenantSubscriptionRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(Optional.empty());
        when(clinicProfileService.upsert(eq(tenantId), any(ClinicProfileUpsertCommand.class), any())).thenReturn(null);

        var detail = service.createTenant(baseCommand(registrationNumber));

        ArgumentCaptor<ClinicProfileUpsertCommand> captor = ArgumentCaptor.forClass(ClinicProfileUpsertCommand.class);
        verify(clinicProfileService).upsert(eq(tenantId), captor.capture(), any());
        assertThat(captor.getValue().registrationNumber()).isEqualTo(registrationNumber);
        assertThat(detail.tenant().id()).isEqualTo(tenantId);
        assertThat(detail.tenant().code()).isEqualTo("jeevanam-auto");
        verify(tenantProvisioningService).provisionTenant(any(TenantProvisioningRequest.class));
        verify(auditEventPublisher).record(any());
    }

    @Test
    void createTenantDoesNotProvisionWhenRegistrationNumberIsMissing() {
        assertThatThrownBy(() -> service.createTenant(baseCommand(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Registration number is required.");

        verify(tenantProvisioningService, never()).provisionTenant(any());
        verify(clinicProfileService, never()).upsert(any(), any(), any());
    }

    private PlatformTenantService.CreateTenantCommand baseCommand(String registrationNumber) {
        return new PlatformTenantService.CreateTenantCommand(
                "Jeevanam Automation Lab",
                "jeevanam-auto",
                "Jeevanam Automation Lab",
                "Pune",
                "Maharashtra",
                "India",
                "411014",
                "9000000101",
                "automation.lab@jeevanam.test",
                "Automation Test Facility",
                "QA Lab",
                registrationNumber,
                "ENTERPRISE",
                Map.of("APPOINTMENTS", true),
                "uat.auto.clinicadmin@jeevanam.test",
                "UAT",
                "Automation",
                "Temp@123456"
        );
    }
}
