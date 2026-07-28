package com.deepthoughtnet.clinic.discover.landingpage.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "discover_landing_page_templates")
public class LandingPageTemplateEntity {
    @Id
    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(length = 1000)
    private String description;

    @Column(name = "supported_sections_json", nullable = false, columnDefinition = "text")
    private String supportedSectionsJson;

    @Column(name = "default_sections_json", nullable = false, columnDefinition = "text")
    private String defaultSectionsJson;

    @Column(name = "default_theme_json", nullable = false, columnDefinition = "text")
    private String defaultThemeJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected LandingPageTemplateEntity() {
    }

    public static LandingPageTemplateEntity create(
            String templateKey,
            String templateName,
            ProviderType providerType,
            int templateVersion,
            String description,
            String supportedSectionsJson,
            String defaultSectionsJson,
            String defaultThemeJson,
            int sortOrder,
            boolean active
    ) {
        LandingPageTemplateEntity entity = new LandingPageTemplateEntity();
        entity.templateKey = templateKey;
        entity.templateName = templateName;
        entity.providerType = providerType;
        entity.templateVersion = templateVersion;
        entity.description = description;
        entity.supportedSectionsJson = supportedSectionsJson;
        entity.defaultSectionsJson = defaultSectionsJson;
        entity.defaultThemeJson = defaultThemeJson;
        entity.sortOrder = sortOrder;
        entity.active = active;
        entity.rowVersion = 0L;
        return entity;
    }

    public String getTemplateKey() { return templateKey; }
    public String getTemplateName() { return templateName; }
    public ProviderType getProviderType() { return providerType; }
    public int getTemplateVersion() { return templateVersion; }
    public String getDescription() { return description; }
    public String getSupportedSectionsJson() { return supportedSectionsJson; }
    public String getDefaultSectionsJson() { return defaultSectionsJson; }
    public String getDefaultThemeJson() { return defaultThemeJson; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public long getRowVersion() { return rowVersion; }
}
