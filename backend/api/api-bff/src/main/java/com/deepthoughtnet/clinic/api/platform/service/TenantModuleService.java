package com.deepthoughtnet.clinic.api.platform.service;

import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantModuleService {

    public static final Set<String> SUPPORTED_MODULES = Set.of(
            SaasModuleCode.APPOINTMENTS.name(),
            SaasModuleCode.CONSULTATION.name(),
            SaasModuleCode.PRESCRIPTION.name(),
            SaasModuleCode.BILLING.name(),
            SaasModuleCode.VACCINATION.name(),
            SaasModuleCode.INVENTORY.name(),
            SaasModuleCode.AI_COPILOT.name(),
            SaasModuleCode.CAREPILOT.name()
    );

    private final JdbcTemplate jdbcTemplate;

    public TenantModuleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Boolean> findForTenant(UUID tenantId) {
        Map<String, Boolean> modules = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select module_code, enabled from tenant_modules where tenant_id = ?",
                (rs, rowNum) -> {
                    modules.put(rs.getString("module_code"), rs.getBoolean("enabled"));
                    return 1;
                },
                tenantId
        );
        return modules;
    }

    public void upsertForTenant(UUID tenantId, Map<String, Boolean> modules) {
        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<String, Boolean> entry : modules.entrySet()) {
            String moduleCode = normalizeModule(entry.getKey());
            boolean enabled = Boolean.TRUE.equals(entry.getValue());
            jdbcTemplate.update(
                    """
                    insert into tenant_modules (id, tenant_id, module_code, enabled, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    on conflict (tenant_id, module_code)
                    do update set enabled = excluded.enabled, updated_at = excluded.updated_at
                    """,
                    UUID.randomUUID(),
                    tenantId,
                    moduleCode,
                    enabled,
                    Timestamp.from(now.toInstant()),
                    Timestamp.from(now.toInstant())
            );
        }
    }

    public String normalizeModule(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            throw new IllegalArgumentException("module code is required");
        }
        String normalized = SaasModuleCode.normalize(moduleCode);
        if (!SUPPORTED_MODULES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported module code: " + moduleCode);
        }
        return normalized;
    }
}
