package com.deepthoughtnet.clinic.api.help.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(
        name = "help_attachments",
        indexes = {
                @Index(name = "ix_help_attachments_section_order", columnList = "section_id,display_order")
        }
)
public class HelpAttachmentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private HelpSectionEntity section;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    protected HelpAttachmentEntity() {
    }

    public static HelpAttachmentEntity create(HelpSectionEntity section, String type, String url, Integer displayOrder) {
        HelpAttachmentEntity entity = new HelpAttachmentEntity();
        entity.id = UUID.randomUUID();
        entity.section = section;
        entity.type = type;
        entity.url = url;
        entity.displayOrder = displayOrder == null ? 0 : displayOrder;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public HelpSectionEntity getSection() {
        return section;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
