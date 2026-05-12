package com.deepthoughtnet.clinic.api.module;

import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModuleRouteRegistry {
    private static final List<RouteModule> ROUTES = List.of(
            new RouteModule("/api/appointments", SaasModuleCode.APPOINTMENTS.name()),
            new RouteModule("/api/consultations", SaasModuleCode.CONSULTATION.name()),
            new RouteModule("/api/prescriptions", SaasModuleCode.PRESCRIPTION.name()),
            new RouteModule("/api/bills", SaasModuleCode.BILLING.name()),
            new RouteModule("/api/receipts", SaasModuleCode.BILLING.name()),
            new RouteModule("/api/vaccines", SaasModuleCode.VACCINATION.name()),
            new RouteModule("/api/vaccinations", SaasModuleCode.VACCINATION.name()),
            new RouteModule("/api/medicines", SaasModuleCode.INVENTORY.name()),
            new RouteModule("/api/inventory", SaasModuleCode.INVENTORY.name()),
            new RouteModule("/api/ai", SaasModuleCode.AI_COPILOT.name()),
            new RouteModule("/api/carepilot", SaasModuleCode.CAREPILOT.name()),
            new RouteModule("/api/dashboard", SaasModuleCode.APPOINTMENTS.name()),
            new RouteModule("/api/patients", SaasModuleCode.APPOINTMENTS.name()),
            new RouteModule("/api/doctors", SaasModuleCode.APPOINTMENTS.name()),
            new RouteModule("/api/notifications", SaasModuleCode.APPOINTMENTS.name()),
            new RouteModule("/api/reports", SaasModuleCode.APPOINTMENTS.name())
    );

    public String moduleForPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return ROUTES.stream()
                .filter(route -> path.equals(route.pathPrefix()) || path.startsWith(route.pathPrefix() + "/"))
                .map(RouteModule::moduleKey)
                .findFirst()
                .orElse(null);
    }

    private record RouteModule(String pathPrefix, String moduleKey) {
    }
}
