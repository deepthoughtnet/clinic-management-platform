package com.deepthoughtnet.clinic.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.exception.TenantModuleDisabledException;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantModuleEntitlementServiceImplTest {
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void clinicGenerationAccessRequiresEnabledModule() {
        TenantEntity tenant = TenantEntity.create("acme", "Acme", "BASIC");
        TenantModuleEntitlementServiceImpl service = new TenantModuleEntitlementServiceImpl(tenantRepository(tenant));

        assertFalse(service.isModuleEnabled(tenantId, ModuleKeys.CLINIC_GENERATION));
        assertThrows(TenantModuleDisabledException.class,
                () -> service.requireModuleEnabled(tenantId, ModuleKeys.CLINIC_GENERATION));
    }

    @Test
    void reconciliationAccessWorksWhenModuleEnabled() {
        TenantEntity tenant = TenantEntity.create("acme", "Acme", "PRO");
        TenantModuleEntitlementServiceImpl service = new TenantModuleEntitlementServiceImpl(tenantRepository(tenant));

        assertTrue(service.isModuleEnabled(tenantId, ModuleKeys.RECONCILIATION));
        service.requireModuleEnabled(tenantId, ModuleKeys.RECONCILIATION);
        assertTrue(service.isModuleEnabled(tenantId, ModuleKeys.DECISIONING));
    }

    @Test
    void apClinicAutomationCanBeDisabled() {
        TenantEntity tenant = TenantEntity.create("acme", "Acme", "PRO");
        tenant.configureModules(false, true, true, false, false, false, false);
        TenantModuleEntitlementServiceImpl service = new TenantModuleEntitlementServiceImpl(tenantRepository(tenant));

        assertFalse(service.isModuleEnabled(tenantId, ModuleKeys.CLINIC_AUTOMATION));
        assertFalse(service.isModuleEnabled(tenantId, ModuleKeys.AGENT_INTAKE));
        assertFalse(service.isModuleEnabled(tenantId, ModuleKeys.DECISIONING));
        assertTrue(service.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT));
        assertThrows(TenantModuleDisabledException.class,
                () -> service.requireModuleEnabled(tenantId, ModuleKeys.CLINIC_AUTOMATION));
    }

    @Test
    void carePilotUsesDedicatedTenantFlag() {
        TenantEntity tenant = TenantEntity.create("acme", "Acme", "BASIC");
        tenant.configureModules(true, false, false, true, true, true, false, false, true, true);
        TenantModuleEntitlementServiceImpl service = new TenantModuleEntitlementServiceImpl(tenantRepository(tenant));

        assertTrue(service.isModuleEnabled(tenantId, ModuleKeys.CAREPILOT));
        assertTrue(service.isModuleEnabled(tenantId, "care_pilot"));
    }

    private TenantRepository tenantRepository(TenantEntity tenant) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return objectMethod(proxy, method, args);
            }
            if ("findById".equals(method.getName())) {
                return Optional.of(tenant);
            }
            return defaultValue(method.getReturnType());
        };
        return TenantRepository.class.cast(Proxy.newProxyInstance(
                TenantRepository.class.getClassLoader(),
                new Class<?>[]{TenantRepository.class},
                handler
        ));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "TenantRepository proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.isPrimitive()) {
            return 0;
        }
        if (returnType.equals(Optional.class)) {
            return Optional.empty();
        }
        if (returnType.equals(List.class)) {
            return List.of();
        }
        return null;
    }
}
