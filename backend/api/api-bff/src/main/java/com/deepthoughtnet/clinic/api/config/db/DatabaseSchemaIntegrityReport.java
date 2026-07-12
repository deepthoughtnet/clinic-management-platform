package com.deepthoughtnet.clinic.api.config.db;

import java.util.List;
import java.util.Map;

public record DatabaseSchemaIntegrityReport(
        boolean healthy,
        boolean matureButEmpty,
        Integer schemaVersion,
        Long tenantCount,
        Long appUserCount,
        Long patientCount,
        List<String> missingTables,
        Map<String, List<String>> missingColumns
) {
}
