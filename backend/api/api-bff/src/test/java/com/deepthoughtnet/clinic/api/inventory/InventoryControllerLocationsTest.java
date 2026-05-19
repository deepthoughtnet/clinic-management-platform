package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryControllerLocationsTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void listLocationsReturnsTenantScopedLocations() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        InventoryService inventoryService = mock(InventoryService.class);
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        InventoryController controller = new InventoryController(inventoryService, pharmacyOperationsService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        when(inventoryService.listLocations(tenantId)).thenReturn(List.of(
                new InventoryLocationRecord(UUID.randomUUID(), tenantId, "Main Pharmacy", "MAIN_PHARMACY", "PHARMACY", true, true, now, now)
        ));

        mockMvc.perform(get("/api/inventory/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locationName").value("Main Pharmacy"))
                .andExpect(jsonPath("$[0].defaultLocation").value(true));

        assertThat(RequestContextHolder.requireTenantId()).isEqualTo(tenantId);
    }
}
