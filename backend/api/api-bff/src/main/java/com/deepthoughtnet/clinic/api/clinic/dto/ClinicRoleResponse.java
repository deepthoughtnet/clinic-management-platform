package com.deepthoughtnet.clinic.api.clinic.dto;

import java.util.List;

public record ClinicRoleResponse(
        String role,
        String displayName,
        List<String> permissions
) {
}
