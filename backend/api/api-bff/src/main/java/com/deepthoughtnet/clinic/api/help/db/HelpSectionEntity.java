package com.deepthoughtnet.clinic.api.help.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "help_sections",
        indexes = {
                @Index(name = "ix_help_sections_page_order", columnList = "page_id,display_order")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_help_sections_page_key", columnNames = {"page_id", "section_key"})
        }
)
public class HelpSectionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private HelpPageEntity page;

    @Column(name = "section_key", nullable = false, length = 128)
    private String sectionKey;

    @Column(name = "section_type", nullable = false, length = 32)
    private String sectionType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_collapsible", nullable = false)
    private boolean collapsible = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected HelpSectionEntity() {
    }

    public static HelpSectionEntity create(HelpPageEntity page, String sectionKey, String sectionType, Integer displayOrder, boolean collapsible, boolean active) {
        HelpSectionEntity entity = new HelpSectionEntity();
        entity.id = UUID.randomUUID();
        entity.page = page;
        entity.sectionKey = sectionKey;
        entity.sectionType = sectionType;
        entity.displayOrder = displayOrder == null ? 0 : displayOrder;
        entity.collapsible = collapsible;
        entity.active = active;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String sectionType, Integer displayOrder, boolean collapsible, boolean active) {
        this.sectionType = sectionType;
        this.displayOrder = displayOrder == null ? 0 : displayOrder;
        this.collapsible = collapsible;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public HelpPageEntity getPage() {
        return page;
    }

    public String getSectionKey() {
        return sectionKey;
    }

    public String getSectionType() {
        return sectionType;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isCollapsible() {
        return collapsible;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
