package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProviderServiceTypeConverter implements AttributeConverter<ProviderServiceType, String> {
    @Override
    public String convertToDatabaseColumn(ProviderServiceType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ProviderServiceType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProviderServiceType.fromCode(dbData);
    }
}
