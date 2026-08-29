package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.pharmacy.PharmacyOperationsService;
import com.deepthoughtnet.clinic.inventory.service.InventoryService;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionRecord;
import com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionType;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventoryControllerTransfersTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void transferStockIsMappedAndDelegatesToInventoryService() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID medicineId = UUID.randomUUID();
        UUID stockBatchId = UUID.randomUUID();
        UUID fromLocationId = UUID.randomUUID();
        UUID toLocationId = UUID.randomUUID();

        InventoryService inventoryService = mock(InventoryService.class);
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        InventoryTransactionViewMapper inventoryTransactionViewMapper = mock(InventoryTransactionViewMapper.class);
        InventoryController controller = new InventoryController(inventoryService, pharmacyOperationsService, inventoryTransactionViewMapper);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));

        InventoryTransactionRecord saved = new InventoryTransactionRecord(
                UUID.randomUUID(),
                tenantId,
                medicineId,
                stockBatchId,
                fromLocationId,
                toLocationId,
                InventoryTransactionType.TRANSFER_OUT,
                10,
                55,
                45,
                "UAT transfer to secondary pharmacy",
                "TRANSFER",
                null,
                actorId,
                null,
                OffsetDateTime.now()
        );
        when(inventoryService.transferStock(eq(tenantId), any(), eq(actorId))).thenReturn(saved);

        mockMvc.perform(post("/api/inventory/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": "%s",
                                  "stockBatchId": "%s",
                                  "fromLocationId": "%s",
                                  "toLocationId": "%s",
                                  "quantity": 10,
                                  "reason": "UAT transfer to secondary pharmacy"
                                }
                                """.formatted(medicineId, stockBatchId, fromLocationId, toLocationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.reason").value("UAT transfer to secondary pharmacy"));

        ArgumentCaptor<com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand> captor =
                ArgumentCaptor.forClass(com.deepthoughtnet.clinic.inventory.service.model.InventoryTransferCommand.class);
        org.mockito.Mockito.verify(inventoryService).transferStock(eq(tenantId), captor.capture(), eq(actorId));
        assertThat(captor.getValue().medicineId()).isEqualTo(medicineId);
        assertThat(captor.getValue().stockBatchId()).isEqualTo(stockBatchId);
        assertThat(captor.getValue().fromLocationId()).isEqualTo(fromLocationId);
        assertThat(captor.getValue().toLocationId()).isEqualTo(toLocationId);
        assertThat(captor.getValue().quantity()).isEqualTo(10);
        assertThat(captor.getValue().reason()).isEqualTo("UAT transfer to secondary pharmacy");
    }

    @Test
    void transferStockReturnsBadRequestForBusinessValidationErrors() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        InventoryService inventoryService = mock(InventoryService.class);
        PharmacyOperationsService pharmacyOperationsService = mock(PharmacyOperationsService.class);
        InventoryTransactionViewMapper inventoryTransactionViewMapper = mock(InventoryTransactionViewMapper.class);
        InventoryController controller = new InventoryController(inventoryService, pharmacyOperationsService, inventoryTransactionViewMapper);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        when(inventoryService.transferStock(eq(tenantId), any(), eq(actorId))).thenThrow(new IllegalArgumentException("source and destination locations must differ"));

        mockMvc.perform(post("/api/inventory/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicineId": "%s",
                                  "stockBatchId": "%s",
                                  "fromLocationId": "%s",
                                  "toLocationId": "%s",
                                  "quantity": 10,
                                  "reason": "UAT transfer to secondary pharmacy"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("source and destination locations must differ"));
    }
}
