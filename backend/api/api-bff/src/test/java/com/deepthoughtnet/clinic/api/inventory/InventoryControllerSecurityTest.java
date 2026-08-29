package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class InventoryControllerSecurityTest {

    @Test
    void inventoryLocationEndpointsStayBoundToInventoryManagementAndReadAccess() throws Exception {
        Method listLocations = InventoryController.class.getMethod("listLocations");
        Method createLocation = InventoryController.class.getMethod("createLocation", com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand.class);
        Method updateLocation = InventoryController.class.getMethod("updateLocation", UUID.class, com.deepthoughtnet.clinic.inventory.service.model.InventoryLocationUpsertCommand.class);
        Method listPhysicalCountSessions = InventoryController.class.getMethod("listPhysicalCountSessions");
        Method getPhysicalCountSession = InventoryController.class.getMethod("getPhysicalCountSession", UUID.class);
        Method savePhysicalCountSession = InventoryController.class.getMethod("savePhysicalCountSession", UUID.class, com.deepthoughtnet.clinic.inventory.service.model.PhysicalCountSessionSaveCommand.class);
        Method transferStock = InventoryController.class.getMethod("transferStock", InventoryTransferRequest.class);
        Method createTransaction = InventoryController.class.getMethod("createTransaction", com.deepthoughtnet.clinic.inventory.service.model.InventoryTransactionCommand.class);

        assertThat(listLocations.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')");
        assertThat(createLocation.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
        assertThat(updateLocation.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
        assertThat(listPhysicalCountSessions.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')");
        assertThat(getPhysicalCountSession.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('report.read')");
        assertThat(savePhysicalCountSession.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasAnyRole('PHARMACIST', 'PHARMA', 'PHARMACY', 'PHARMACY_INVENTORY_MANAGER', 'CLINIC_ADMIN')");
        assertThat(transferStock.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
        assertThat(createTransaction.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
    }
}
