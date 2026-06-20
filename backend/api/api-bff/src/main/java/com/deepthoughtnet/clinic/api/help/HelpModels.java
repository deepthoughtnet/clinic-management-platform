package com.deepthoughtnet.clinic.api.help;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class HelpModels {
    private HelpModels() {
    }

    public enum HelpPageStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    public enum HelpContentStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    public enum HelpSectionType {
        DESCRIPTION,
        WORKFLOW,
        FIELD_TABLE,
        VALIDATION_RULES,
        REPORT_TYPES,
        FILTERS,
        EXPORT_CSV,
        TAB_GUIDE,
        QUICK_ACTIONS,
        DASHBOARD_CARDS,
        PERMISSIONS,
        COMMON_ERRORS,
        COMMON_ISSUES,
        BEST_PRACTICES,
        FAQ,
        RELATED_PAGES,
        VIDEOS,
        IMAGES,
        LINKS,
        AUDIT,
        ROLES,
        TIPS,
        KNOWN_LIMITATIONS
    }

    public enum HelpAttachmentType {
        IMAGE,
        VIDEO,
        PDF,
        LINK
    }

    public record HelpAttachmentRecord(UUID id, String type, String url, Integer displayOrder) {
    }

    public record HelpContentRecord(
            UUID id,
            String languageCode,
            String contentJson,
            Integer version,
            String status,
            UUID createdBy,
            UUID updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record HelpSectionRecord(
            UUID id,
            String sectionKey,
            String sectionType,
            Integer displayOrder,
            boolean collapsible,
            boolean active,
            String contentJson,
            String contentLanguageCode,
            Integer contentVersion,
            String contentStatus,
            List<HelpAttachmentRecord> attachments,
            List<HelpContentRecord> contents
    ) {
    }

    public record HelpPageRecord(
            UUID id,
            String moduleKey,
            String pageKey,
            String title,
            String icon,
            String status,
            Integer version,
            boolean active,
            UUID createdBy,
            UUID updatedBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<Integer> availableVersions,
            List<HelpSectionRecord> sections
    ) {
    }

    public record HelpPageSummary(
            UUID id,
            String moduleKey,
            String pageKey,
            String title,
            String icon,
            String status,
            Integer version,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record HelpSearchResult(
            String pageKey,
            String pageTitle,
            String moduleKey,
            String sectionKey,
            String sectionType,
            String snippet,
            String languageCode
    ) {
    }

    public record HelpAttachmentUpsertRequest(String type, String url, Integer displayOrder) {
    }

    public record HelpContentUpsertRequest(String languageCode, String contentJson) {
    }

    public record HelpSectionUpsertRequest(
            String sectionKey,
            String sectionType,
            Integer displayOrder,
            boolean collapsible,
            boolean active,
            String contentJson
    ) {
    }

    public record HelpPageUpsertRequest(
            String moduleKey,
            String pageKey,
            String title,
            String icon,
            String status,
            boolean active,
            List<HelpSectionUpsertRequest> sections
    ) {
    }

    public record HelpPageLifecycleRequest(String pageKey, Integer version) {
    }
}
