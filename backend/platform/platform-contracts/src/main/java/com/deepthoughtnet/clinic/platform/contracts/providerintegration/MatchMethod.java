package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

public enum MatchMethod {
    REGISTRATION_EXACT,
    REGISTRATION_AND_CONTACT,
    VERIFIED_CONTACT_EXACT,
    BUSINESS_IDENTITY_MATCH,
    PROVIDER_CONFIRMED,
    TENANT_CONFIRMED,
    PLATFORM_ADMIN_REVIEW,
    MANUAL_REFERENCE
}
