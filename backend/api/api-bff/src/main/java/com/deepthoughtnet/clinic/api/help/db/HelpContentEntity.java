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
        name = "help_content",
        indexes = {
                @Index(name = "ix_help_content_section_language_status", columnList = "section_id,language_code,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_help_content_section_language_version", columnNames = {"section_id", "language_code", "version"})
        }
)
public class HelpContentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private HelpSectionEntity section;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected HelpContentEntity() {
    }

    public static HelpContentEntity create(HelpSectionEntity section, String languageCode, String contentJson, int version, String status, UUID actorAppUserId) {
        HelpContentEntity entity = new HelpContentEntity();
        entity.id = UUID.randomUUID();
        entity.section = section;
        entity.languageCode = languageCode;
        entity.contentJson = contentJson;
        entity.version = version;
        entity.status = status;
        entity.createdBy = actorAppUserId;
        entity.updatedBy = actorAppUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void setStatus(String status, UUID actorAppUserId) {
        this.status = status;
        this.updatedBy = actorAppUserId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public HelpSectionEntity getSection() {
        return section;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getContentJson() {
        return contentJson;
    }

    public int getVersion() {
        return version;
    }

    public String getStatus() {
        return status;
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
