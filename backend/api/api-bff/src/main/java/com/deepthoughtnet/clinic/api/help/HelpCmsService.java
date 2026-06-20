package com.deepthoughtnet.clinic.api.help;

import com.deepthoughtnet.clinic.api.help.HelpModels.HelpAttachmentRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpAttachmentType;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpContentRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpContentStatus;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageLifecycleRequest;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageStatus;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageSummary;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpPageUpsertRequest;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSearchResult;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSectionRecord;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSectionType;
import com.deepthoughtnet.clinic.api.help.HelpModels.HelpSectionUpsertRequest;
import com.deepthoughtnet.clinic.api.help.db.HelpAttachmentEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpAttachmentRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpContentEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpContentRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpPageEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpPageRepository;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionEntity;
import com.deepthoughtnet.clinic.api.help.db.HelpSectionRepository;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HelpCmsService {
    private static final String DEFAULT_LANGUAGE = "en";

    private final HelpPageRepository pageRepository;
    private final HelpSectionRepository sectionRepository;
    private final HelpContentRepository contentRepository;
    private final HelpAttachmentRepository attachmentRepository;

    public HelpCmsService(
            HelpPageRepository pageRepository,
            HelpSectionRepository sectionRepository,
            HelpContentRepository contentRepository,
            HelpAttachmentRepository attachmentRepository
    ) {
        this.pageRepository = pageRepository;
        this.sectionRepository = sectionRepository;
        this.contentRepository = contentRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional(readOnly = true)
    public List<HelpPageSummary> listPages() {
        return pageRepository.findAllByOrderByModuleKeyAscTitleAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public HelpPageRecord getPublicPage(String pageKey, String languageCode) {
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(HelpPageKeyResolver.resolveLookupPageKey(pageKey))
                .filter(entity -> entity.isActive() && "PUBLISHED".equalsIgnoreCase(entity.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        return toPageRecord(page, resolveLanguage(languageCode), false);
    }

    @Transactional(readOnly = true)
    public HelpPageRecord getAdminPage(String pageKey) {
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(HelpPageKeyResolver.resolveLookupPageKey(pageKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        return toPageRecord(page, DEFAULT_LANGUAGE, true);
    }

    @Transactional(readOnly = true)
    public List<HelpSearchResult> search(String query, String languageCode) {
        String term = normalize(query);
        if (term.isEmpty()) {
            return List.of();
        }
        String resolvedLang = resolveLanguage(languageCode);
        List<HelpPageEntity> pages = pageRepository.findByStatusAndActiveTrueOrderByModuleKeyAscTitleAsc("PUBLISHED");
        List<HelpSearchResult> results = new ArrayList<>();
        for (HelpPageEntity page : pages) {
            HelpPageRecord record = toPageRecord(page, resolvedLang, false);
            for (HelpSectionRecord section : record.sections()) {
                String haystack = normalize(
                        page.getTitle() + " " + page.getModuleKey() + " " + section.sectionKey() + " " + section.sectionType() + " " + extractSearchableText(section.contentJson())
                );
                if (haystack.contains(term)) {
                    String searchableText = extractSearchableText(section.contentJson());
                    results.add(new HelpSearchResult(
                            page.getPageKey(),
                            page.getTitle(),
                            page.getModuleKey(),
                            section.sectionKey(),
                            section.sectionType(),
                            snippet(searchableText, term),
                            resolvedLang
                    ));
                }
            }
        }
        return results;
    }

    @Transactional
    public HelpPageRecord createPage(HelpPageUpsertRequest request, UUID actorAppUserId) {
        if (pageRepository.findByPageKeyIgnoreCase(request.pageKey()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Help page already exists");
        }
        HelpPageEntity page = HelpPageEntity.create(
                request.moduleKey(),
                request.pageKey(),
                request.title(),
                request.icon(),
                normalizeStatus(request.status(), HelpPageStatus.DRAFT.name()),
                request.active(),
                actorAppUserId
        );
        page.setVersion(1);
        page = pageRepository.save(page);
        upsertSections(page, request.sections(), 1, actorAppUserId);
        return toPageRecord(page, DEFAULT_LANGUAGE, true);
    }

    @Transactional
    public HelpPageRecord updatePage(HelpPageUpsertRequest request, UUID actorAppUserId) {
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(request.pageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        int nextVersion = Math.max(1, page.getVersion() + 1);
        page.update(
                request.moduleKey(),
                request.title(),
                request.icon(),
                normalizeStatus(request.status(), HelpPageStatus.DRAFT.name()),
                request.active(),
                actorAppUserId
        );
        page.setVersion(nextVersion);
        page = pageRepository.save(page);
        upsertSections(page, request.sections(), nextVersion, actorAppUserId);
        return toPageRecord(page, DEFAULT_LANGUAGE, true);
    }

    @Transactional
    public HelpPageRecord publish(HelpPageLifecycleRequest request, UUID actorAppUserId) {
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(request.pageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        int version = request.version() == null ? page.getVersion() : request.version();
        markVersion(page, version, HelpPageStatus.PUBLISHED.name(), actorAppUserId);
        page.update(page.getModuleKey(), page.getTitle(), page.getIcon(), HelpPageStatus.PUBLISHED.name(), true, actorAppUserId);
        page.setVersion(version);
        return toPageRecord(pageRepository.save(page), DEFAULT_LANGUAGE, true);
    }

    @Transactional
    public HelpPageRecord archive(HelpPageLifecycleRequest request, UUID actorAppUserId) {
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(request.pageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        page.update(page.getModuleKey(), page.getTitle(), page.getIcon(), HelpPageStatus.ARCHIVED.name(), false, actorAppUserId);
        page = pageRepository.save(page);
        markAllContentStatus(page.getId(), HelpContentStatus.ARCHIVED.name(), actorAppUserId);
        return toPageRecord(page, DEFAULT_LANGUAGE, true);
    }

    @Transactional
    public HelpPageRecord rollback(HelpPageLifecycleRequest request, UUID actorAppUserId) {
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rollback version is required");
        }
        HelpPageEntity page = pageRepository.findByPageKeyIgnoreCase(request.pageKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Help page not found"));
        markVersion(page, request.version(), HelpContentStatus.PUBLISHED.name(), actorAppUserId);
        page.update(page.getModuleKey(), page.getTitle(), page.getIcon(), HelpPageStatus.PUBLISHED.name(), true, actorAppUserId);
        page.setVersion(request.version());
        page = pageRepository.save(page);
        return toPageRecord(page, DEFAULT_LANGUAGE, true);
    }

    private void upsertSections(HelpPageEntity page, List<HelpSectionUpsertRequest> sections, int version, UUID actorAppUserId) {
        if (sections == null) {
            return;
        }
        for (HelpSectionUpsertRequest request : sections) {
            HelpSectionEntity section = sectionRepository.findByPage_IdAndSectionKeyIgnoreCase(page.getId(), request.sectionKey())
                    .map(existing -> {
                        existing.update(normalizeSectionType(request.sectionType(), HelpSectionType.DESCRIPTION.name()), request.displayOrder(), request.collapsible(), request.active());
                        return existing;
                    })
                    .orElseGet(() -> HelpSectionEntity.create(page, request.sectionKey(), normalizeSectionType(request.sectionType(), HelpSectionType.DESCRIPTION.name()), request.displayOrder(), request.collapsible(), request.active()));
            section = sectionRepository.save(section);
            HelpContentEntity content = HelpContentEntity.create(
                    section,
                    DEFAULT_LANGUAGE,
                    request.contentJson(),
                    version,
                    HelpContentStatus.DRAFT.name(),
                    actorAppUserId
            );
            contentRepository.save(content);
        }
    }

    private void markVersion(HelpPageEntity page, int version, String status, UUID actorAppUserId) {
        List<HelpContentEntity> contents = contentRepository.findBySection_Page_IdOrderByVersionDescCreatedAtDesc(page.getId());
        for (HelpContentEntity content : contents) {
            if (content.getVersion() == version) {
                content.setStatus(status, actorAppUserId);
            } else if ("PUBLISHED".equalsIgnoreCase(content.getStatus())) {
                content.setStatus(HelpContentStatus.ARCHIVED.name(), actorAppUserId);
            }
        }
        contentRepository.saveAll(contents);
    }

    private void markAllContentStatus(UUID pageId, String status, UUID actorAppUserId) {
        List<HelpContentEntity> contents = contentRepository.findBySection_Page_IdOrderByVersionDescCreatedAtDesc(pageId);
        for (HelpContentEntity content : contents) {
            content.setStatus(status, actorAppUserId);
        }
        contentRepository.saveAll(contents);
    }

    private HelpPageRecord toPageRecord(HelpPageEntity page, String languageCode, boolean includeHistory) {
        List<HelpSectionRecord> sections = sectionRepository.findByPage_IdOrderByDisplayOrderAsc(page.getId()).stream()
                .filter(HelpSectionEntity::isActive)
                .map(section -> toSectionRecord(section, languageCode, includeHistory))
                .toList();
        Set<Integer> versions = new LinkedHashSet<>();
        for (HelpContentEntity content : contentRepository.findBySection_Page_IdOrderByVersionDescCreatedAtDesc(page.getId())) {
            versions.add(content.getVersion());
        }
        return new HelpPageRecord(
                page.getId(),
                page.getModuleKey(),
                page.getPageKey(),
                page.getTitle(),
                page.getIcon(),
                page.getStatus(),
                page.getVersion(),
                page.isActive(),
                page.getCreatedBy(),
                page.getUpdatedBy(),
                page.getCreatedAt(),
                page.getUpdatedAt(),
                new ArrayList<>(versions),
                sections
        );
    }

    private HelpSectionRecord toSectionRecord(HelpSectionEntity section, String languageCode, boolean includeHistory) {
        List<HelpAttachmentRecord> attachments = attachmentRepository.findBySection_IdOrderByDisplayOrderAsc(section.getId()).stream()
                .map(this::toAttachmentRecord)
                .toList();
        List<HelpContentEntity> contents = contentRepository.findBySection_IdOrderByVersionDescCreatedAtDesc(section.getId());
        HelpContentEntity effective = pickEffectiveContent(contents, languageCode);
        List<HelpContentRecord> history = includeHistory
                ? contents.stream().map(this::toContentRecord).toList()
                : List.of();
        return new HelpSectionRecord(
                section.getId(),
                section.getSectionKey(),
                section.getSectionType(),
                section.getDisplayOrder(),
                section.isCollapsible(),
                section.isActive(),
                effective == null ? null : effective.getContentJson(),
                effective == null ? null : effective.getLanguageCode(),
                effective == null ? null : effective.getVersion(),
                effective == null ? null : effective.getStatus(),
                attachments,
                history
        );
    }

    private HelpAttachmentRecord toAttachmentRecord(HelpAttachmentEntity attachment) {
        return new HelpAttachmentRecord(attachment.getId(), attachment.getType(), attachment.getUrl(), attachment.getDisplayOrder());
    }

    private HelpContentRecord toContentRecord(HelpContentEntity content) {
        return new HelpContentRecord(
                content.getId(),
                content.getLanguageCode(),
                content.getContentJson(),
                content.getVersion(),
                content.getStatus(),
                content.getCreatedBy(),
                content.getUpdatedBy(),
                content.getCreatedAt(),
                content.getUpdatedAt()
        );
    }

    private HelpContentEntity pickEffectiveContent(List<HelpContentEntity> contents, String languageCode) {
        if (contents.isEmpty()) {
            return null;
        }
        String resolved = resolveLanguage(languageCode);
        for (HelpContentEntity content : contents) {
            if (resolved.equalsIgnoreCase(content.getLanguageCode()) && "PUBLISHED".equalsIgnoreCase(content.getStatus())) {
                return content;
            }
        }
        if (!DEFAULT_LANGUAGE.equalsIgnoreCase(resolved)) {
            for (HelpContentEntity content : contents) {
                if (DEFAULT_LANGUAGE.equalsIgnoreCase(content.getLanguageCode()) && "PUBLISHED".equalsIgnoreCase(content.getStatus())) {
                    return content;
                }
            }
        }
        return contents.stream().filter(content -> "PUBLISHED".equalsIgnoreCase(content.getStatus())).findFirst().orElse(contents.get(0));
    }

    private HelpPageSummary toSummary(HelpPageEntity page) {
        return new HelpPageSummary(
                page.getId(),
                page.getModuleKey(),
                page.getPageKey(),
                page.getTitle(),
                page.getIcon(),
                page.getStatus(),
                page.getVersion(),
                page.isActive(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }

    private String resolveLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = languageCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "en", "hi", "mr", "ta", "te", "bn" -> normalized;
            default -> DEFAULT_LANGUAGE;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String extractSearchableText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        try {
            String normalized = contentJson
                    .replaceAll("[\\{\\}\\[\\]\\\"']", " ")
                    .replaceAll("[:;,]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return normalized;
        } catch (Exception ex) {
            return contentJson;
        }
    }

    private String snippet(String text, String term) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) return "";
        int index = normalize(normalized).indexOf(term);
        if (index < 0) {
            return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
        }
        int start = Math.max(0, index - 40);
        int end = Math.min(normalized.length(), index + term.length() + 80);
        return (start > 0 ? "..." : "") + normalized.substring(start, end) + (end < normalized.length() ? "..." : "");
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSectionType(String sectionType, String fallback) {
        if (sectionType == null || sectionType.isBlank()) {
            return fallback;
        }
        return sectionType.trim().toUpperCase(Locale.ROOT);
    }
}
