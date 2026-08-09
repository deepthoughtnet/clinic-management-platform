package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicPracticeMiniResponse(
        String practiceType,
        String practiceSlug,
        String practiceDisplayName,
        String publicPath,
        String area,
        String city,
        String bookingReference
) {
    public PublicPracticeMiniResponse(
            String practiceType,
            String practiceSlug,
            String practiceDisplayName,
            String publicPath,
            String area,
            String city
    ) {
        this(practiceType, practiceSlug, practiceDisplayName, publicPath, area, city, null);
    }
}
