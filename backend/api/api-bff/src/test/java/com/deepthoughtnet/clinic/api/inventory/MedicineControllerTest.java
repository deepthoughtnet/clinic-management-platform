package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.pharmacy.MedicineImportResult;
import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MedicineControllerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void importTemplateIsAvailableOnPharmacyAliasPath() throws Exception {
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        when(pharmacyOperationsService.medicineImportTemplateCsv()).thenReturn(String.join("\n",
                "medicineName,genericName,brandName,category,type,strength,unit,defaultDosage,defaultFrequency,defaultDurationDays,defaultTiming,instructions,manufacturer,barcode,qrCode,externalCode,defaultPrice,taxPercent,active",
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,PARA-650,PARA-650,25.00,5,true"
        ));
        MedicineController controller = new MedicineController(mock(InventoryService.class), mock(PermissionChecker.class), pharmacyOperationsService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/pharmacy/medicines/import-template"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("medicineName")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("defaultPrice")));
    }

    @Test
    void importCsvUsesTenantContextOnPharmacyAliasPath() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        InventoryService inventoryService = mock(InventoryService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        MedicineController controller = new MedicineController(inventoryService, permissionChecker, pharmacyOperationsService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("PHARMACIST"), "PHARMACIST", "cid"));

        String csv = String.join("\n",
                "medicineName,genericName,brandName,category,type,strength,unit,defaultDosage,defaultFrequency,defaultDurationDays,defaultTiming,instructions,manufacturer,barcode,qrCode,externalCode,defaultPrice,taxPercent,active",
                "Paracetamol 650,Paracetamol,Dolo,Analgesic,Tablet,650,mg,1 tablet,Twice daily,5,AFTER_FOOD,Take after meals,Micro Labs,PARA-650,PARA-650,PARA-650,25.00,5,true"
        );
        when(pharmacyOperationsService.importMedicines(tenantId, csv.getBytes(), actorId)).thenReturn(
                new MedicineImportResult(
                        1,
                        1,
                        0,
                        0,
                        0,
                        List.of(),
                        ""
                )
        );

        mockMvc.perform(multipart("/api/pharmacy/medicines/import-csv").file(new MockMultipartFile("file", "medicine.csv", "text/csv", csv.getBytes())))
                .andExpect(status().isOk());

        assertThat(RequestContextHolder.requireTenantId()).isEqualTo(tenantId);
    }
}
