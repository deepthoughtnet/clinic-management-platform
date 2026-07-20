package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.analytics.LeadAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CarePilotLeadControllerRouteTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void importTemplateUsesStaticRouteInsteadOfUuidDetailRoute() throws Exception {
        CarePilotLeadCsvService leadCsvService = mock(CarePilotLeadCsvService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        when(clinicTimeZoneResolver.resolve(any())).thenReturn(ZoneId.of("UTC"));
        when(leadCsvService.importTemplateCsv()).thenReturn("firstName,lastName\nAva,Smith\n");
        when(permissionChecker.hasPermission(any())).thenReturn(true);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(leadCsvService, clinicTimeZoneResolver, permissionChecker)).build();

        mockMvc.perform(get("/api/carepilot/leads/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("carepilot-leads-import-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("firstName,lastName")));
    }

    @Test
    void exportUsesStaticRouteInsteadOfUuidDetailRoute() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CarePilotLeadCsvService leadCsvService = mock(CarePilotLeadCsvService.class);
        ClinicTimeZoneResolver clinicTimeZoneResolver = mock(ClinicTimeZoneResolver.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(leadCsvService.exportCsv(eq(tenantId), any(ZoneId.class), any(LeadSearchCriteria.class), any(UUID.class), eq(true))).thenReturn("firstName,lastName\nAva,Smith\n");
        when(permissionChecker.hasPermission(any())).thenReturn(true);
        when(permissionChecker.hasAnyPermission(any())).thenReturn(true);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller(leadCsvService, clinicTimeZoneResolver, permissionChecker)).build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        mockMvc.perform(get("/api/carepilot/leads/export").queryParam("search", "Ava"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("carepilot-leads-export.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("firstName,lastName")));
    }

    private CarePilotLeadController controller(CarePilotLeadCsvService leadCsvService, ClinicTimeZoneResolver clinicTimeZoneResolver, PermissionChecker permissionChecker) {
        return new CarePilotLeadController(
                mock(LeadService.class),
                mock(LeadConversionService.class),
                mock(LeadAnalyticsService.class),
                mock(LeadActivityService.class),
                leadCsvService,
                clinicTimeZoneResolver,
                permissionChecker
        );
    }
}
