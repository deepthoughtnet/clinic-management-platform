package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryControllerStocksTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void duplicateBatchReturnsFriendlyBadRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID medicineId = UUID.randomUUID();
        InventoryService inventoryService = mock(InventoryService.class);
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        InventoryTransactionViewMapper inventoryTransactionViewMapper = mock(InventoryTransactionViewMapper.class);
        InventoryController controller = new InventoryController(inventoryService, pharmacyOperationsService, inventoryTransactionViewMapper);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        doThrow(new IllegalArgumentException("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number."))
                .when(inventoryService)
                .createStock(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actorId));

        mockMvc.perform(post("/api/inventory/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": "%s",
                                  "quantityOnHand": 10,
                                  "batchNumber": "B001",
                                  "active": true
                                }
                                """.formatted(medicineId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stock batch already exists for this medicine and location. Edit existing batch or use a different batch number."));

        assertThat(RequestContextHolder.requireTenantId()).isEqualTo(tenantId);
    }
}
