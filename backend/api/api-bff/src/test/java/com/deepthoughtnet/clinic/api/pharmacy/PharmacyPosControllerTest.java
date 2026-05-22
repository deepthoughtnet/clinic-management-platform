package com.deepthoughtnet.clinic.api.pharmacy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PharmacyPosControllerTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void pharmacistIsAllowedToListSales() {
        UUID tenantId = UUID.randomUUID();
        PharmacyPosService service = mock(PharmacyPosService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyPosController controller = new PharmacyPosController(service, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("PHARMACIST"), "PHARMACIST", "cid"));
        when(permissionChecker.hasPermission("inventory.manage")).thenReturn(true);
        when(service.listSales(tenantId)).thenReturn(List.of());

        controller.listSales();

        verify(service).listSales(tenantId);
    }

    @Test
    void receptionistIsDeniedEvenIfBillingPermissionExists() {
        UUID tenantId = UUID.randomUUID();
        PharmacyPosService service = mock(PharmacyPosService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyPosController controller = new PharmacyPosController(service, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "cid"));
        when(permissionChecker.hasPermission("inventory.manage")).thenReturn(false);
        when(permissionChecker.hasPermission("billing.create")).thenReturn(true);

        assertThatThrownBy(controller::listSales)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("pharmacy counter roles");
    }

    @Test
    void doctorIsDenied() {
        UUID tenantId = UUID.randomUUID();
        PharmacyPosService service = mock(PharmacyPosService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyPosController controller = new PharmacyPosController(service, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        when(permissionChecker.hasPermission("inventory.manage")).thenReturn(false);
        when(permissionChecker.hasPermission("billing.create")).thenReturn(false);

        assertThatThrownBy(controller::listSales)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("pharmacy counter roles");
    }

    @Test
    void pharmacistCanUploadPrescription() throws Exception {
        UUID tenantId = UUID.randomUUID();
        PharmacyPosService service = mock(PharmacyPosService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyPosController controller = new PharmacyPosController(service, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("PHARMACIST"), "PHARMACIST", "cid"));
        when(permissionChecker.hasPermission("inventory.manage")).thenReturn(true);
        when(service.uploadPrescription(eq(tenantId), any(), any())).thenReturn(
                new PharmacyPosPrescriptionUploadResponse(UUID.randomUUID(), "rx.pdf", "application/pdf", 10, java.time.OffsetDateTime.now())
        );

        controller.uploadPrescription(new MockMultipartFile("file", "rx.pdf", "application/pdf", "data".getBytes()));

        verify(service).uploadPrescription(eq(tenantId), any(), any());
    }

    @Test
    void receptionistCannotUploadPrescription() {
        UUID tenantId = UUID.randomUUID();
        PharmacyPosService service = mock(PharmacyPosService.class);
        PermissionChecker permissionChecker = mock(PermissionChecker.class);
        PharmacyPosController controller = new PharmacyPosController(service, permissionChecker);
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "cid"));
        when(permissionChecker.hasPermission("inventory.manage")).thenReturn(false);
        when(permissionChecker.hasPermission("billing.create")).thenReturn(true);

        assertThatThrownBy(() -> controller.uploadPrescription(new MockMultipartFile("file", "rx.pdf", "application/pdf", "data".getBytes())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("pharmacy counter roles");
    }
}
