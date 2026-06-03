package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
