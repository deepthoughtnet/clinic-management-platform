package com.deepthoughtnet.clinic.api.help.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
        name = "help_pages",
        indexes = {
                @Index(name = "ix_help_pages_module_status", columnList = "module_key,status,is_active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_help_pages_page_key", columnNames = {"page_key"})
        }
)
public class HelpPageEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "module_key", nullable = false, length = 64)
    private String moduleKey;

    @Column(name = "page_key", nullable = false, length = 128)
    private String pageKey;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(length = 64)
    private String icon;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "page")
    @BatchSize(size = 50)
    private List<HelpSectionEntity> sections = new ArrayList<>();

    protected HelpPageEntity() {
    }

    public static HelpPageEntity create(String moduleKey, String pageKey, String title, String icon, String status, boolean active, UUID actorAppUserId) {
        HelpPageEntity entity = new HelpPageEntity();
        entity.id = UUID.randomUUID();
        entity.moduleKey = moduleKey;
        entity.pageKey = pageKey;
        entity.title = title;
        entity.icon = icon;
        entity.status = status;
        entity.active = active;
        entity.createdBy = actorAppUserId;
        entity.updatedBy = actorAppUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String moduleKey, String title, String icon, String status, boolean active, UUID actorAppUserId) {
        this.moduleKey = moduleKey;
        this.title = title;
        this.icon = icon;
        this.status = status;
        this.active = active;
        this.updatedBy = actorAppUserId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setVersion(int version) {
        this.version = version;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public String getPageKey() {
        return pageKey;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
