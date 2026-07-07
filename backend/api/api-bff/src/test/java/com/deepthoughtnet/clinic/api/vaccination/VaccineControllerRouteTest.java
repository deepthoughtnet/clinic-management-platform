package com.deepthoughtnet.clinic.api.vaccination;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportResponse;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportRowResult;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VaccineControllerRouteTest {
    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void importTemplateAndExportUseCsvDownloadHeaders() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        VaccineCsvService csvService = mock(VaccineCsvService.class);
        VaccineAccessChecker accessChecker = mock(VaccineAccessChecker.class);
        when(accessChecker.canManageVaccineMaster()).thenReturn(true);
        when(csvService.importTemplateCsv()).thenReturn("vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,defaultPrice,active\n");
        when(csvService.exportCsv(tenantId)).thenReturn("vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,defaultPrice,active\nHepatitis B,,,,,,IM,Deltoid,2-8 C,123,CLINIC_CUSTOM,Infants,0,30,90,30,60,Annual,false,365,120.50,true\n");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccineController(mock(VaccinationService.class), csvService, accessChecker)).build();

        mockMvc.perform(get("/api/vaccines/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("vaccine-import-template.csv")))
                .andExpect(content().string(containsString("vaccineName,description,manufacturer,brandName,vaccineGroup,doseNumber,route,administrationSite,storageTemperature,ndcBarcode,scheduleType,ageGroup,minAgeDays,recommendedAgeDays,maxAgeDays,gapDays,boosterGapDays,boosterRules,isRecurring,recurrenceDays,defaultPrice,active")));

        mockMvc.perform(get("/api/vaccines/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("vaccine-master-export.csv")))
                .andExpect(content().string(containsString("Hepatitis B")));
    }

    @Test
    void importCsvReturnsImportSummary() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        VaccineCsvService csvService = mock(VaccineCsvService.class);
        VaccineAccessChecker accessChecker = mock(VaccineAccessChecker.class);
        when(accessChecker.canManageVaccineMaster()).thenReturn(true);
        when(csvService.importCsv(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.eq(actorId)))
                .thenReturn(new VaccineCsvImportResponse(1, 1, 0, List.of(new VaccineCsvImportRowResult(2, "Hepatitis B", "CREATED", "Imported successfully"))));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VaccineController(mock(VaccinationService.class), csvService, accessChecker)).build();

        mockMvc.perform(multipart("/api/vaccines/import-csv")
                        .file(new MockMultipartFile("file", "vaccines.csv", "text/csv", "vaccineName,description,ageGroup,gapDays,defaultPrice,active\nHepatitis B,,,30,120.50,true\n".getBytes())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"createdCount\":1")))
                .andExpect(content().string(containsString("\"status\":\"CREATED\"")));
    }
}
