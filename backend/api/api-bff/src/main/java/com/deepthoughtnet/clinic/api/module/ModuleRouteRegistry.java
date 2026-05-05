package com.deepthoughtnet.clinic.api.module;

import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModuleRouteRegistry {
    private static final List<RouteModule> ROUTES = List.of(
            new RouteModule("/api/patients", ModuleKeys.PATIENTS),
            new RouteModule("/api/appointments", ModuleKeys.APPOINTMENTS),
            new RouteModule("/api/consultations", ModuleKeys.CONSULTATIONS),
            new RouteModule("/api/prescriptions", ModuleKeys.PRESCRIPTIONS),
            new RouteModule("/api/billing", ModuleKeys.BILLING),
            new RouteModule("/api/vaccinations", ModuleKeys.VACCINATIONS),
            new RouteModule("/api/inventory", ModuleKeys.INVENTORY),
            new RouteModule("/api/reports", ModuleKeys.REPORTS),
            new RouteModule("/api/ai", ModuleKeys.AI_SUPPORT)
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
