package com.deepthoughtnet.clinic.platform.branding;

import java.util.Optional;
import java.util.UUID;

public interface BrandingLogoProvider {
    Optional<BrandingLogoAsset> resolveLogo(UUID tenantId);
}
