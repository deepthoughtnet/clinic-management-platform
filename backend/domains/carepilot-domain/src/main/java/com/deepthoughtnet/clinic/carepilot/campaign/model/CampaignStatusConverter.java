package com.deepthoughtnet.clinic.carepilot.campaign.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CampaignStatusConverter implements AttributeConverter<CampaignStatus, String> {
    @Override
    public String convertToDatabaseColumn(CampaignStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CampaignStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CampaignStatus.fromLegacy(dbData);
    }
}
