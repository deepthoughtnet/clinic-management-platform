package com.deepthoughtnet.clinic.carepilot.template.service;

import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import com.deepthoughtnet.clinic.carepilot.template.db.CareTemplateEntity;
import com.deepthoughtnet.clinic.carepilot.template.db.CareTemplateRepository;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateRecord;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CareTemplateUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.template.service.model.TemplatePreviewResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized tenant template service for administration use-cases.
 */
@Service
public class CareTemplateService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\u0000-\\u001F\\u007F]");
    private static final int MAX_NAME_LENGTH = 140;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_SUBJECT_LENGTH = 300;
    private static final int MAX_BODY_LENGTH = 10000;
    private static final int MAX_VARIABLES_JSON_LENGTH = 10000;
    private final CareTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public CareTemplateService(CareTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CareTemplateRecord> list(UUID tenantId, CareTemplateSearchCriteria criteria) {
        CarePilotValidators.requireTenant(tenantId);
        ensureDefaultSystemTemplates(tenantId);
        CareTemplateSearchCriteria safe = criteria == null ? new CareTemplateSearchCriteria(null, null, null, null, null) : criteria;
        String search = safe.search() == null ? null : safe.search().trim().toLowerCase();
        List<CareTemplateEntity> entities;
        if (search == null || search.isBlank()) {
            entities = repository.searchNoText(tenantId, safe.templateType(), safe.channel(), safe.category(), safe.active());
        } else {
            entities = repository.searchWithText(
                    tenantId,
                    safe.templateType(),
                    safe.channel(),
                    safe.category(),
                    safe.active(),
                    "%" + search + "%"
            );
        }
        return entities.stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public CareTemplateRecord get(UUID tenantId, UUID templateId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        CareTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        return toRecord(entity);
    }

    @Transactional
    public CareTemplateRecord create(UUID tenantId, CareTemplateUpsertCommand command, UUID actorAppUserId) {
        CarePilotValidators.requireTenant(tenantId);
        validateRequired(command);
        validateUniqueName(tenantId, command.templateType(), command.name(), null);
        String normalizedName = command.name().trim();
        String normalizedDescription = normalizeNullable(command.description());
        String normalizedSubject = normalizeNullable(command.subject());
        String normalizedBody = command.body().trim();
        String normalizedVariablesJson = normalizeNullable(command.variablesJson());
        CareTemplateEntity entity = CareTemplateEntity.create(
                tenantId,
                normalizedName,
                normalizedDescription,
                command.templateType(),
                command.channel(),
                command.category(),
                normalizedSubject,
                normalizedBody,
                normalizedVariablesJson,
                command.active() == null || command.active(),
                false,
                actorAppUserId
        );
        return toRecord(repository.save(entity));
    }

    @Transactional
    public CareTemplateRecord update(UUID tenantId, UUID templateId, CareTemplateUpsertCommand command, UUID actorAppUserId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        validateRequired(command);
        CareTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        validateUniqueName(tenantId, command.templateType(), command.name(), templateId);
        String normalizedName = command.name().trim();
        String normalizedDescription = normalizeNullable(command.description());
        String normalizedSubject = normalizeNullable(command.subject());
        String normalizedBody = command.body().trim();
        String normalizedVariablesJson = normalizeNullable(command.variablesJson());
        entity.update(
                normalizedName,
                normalizedDescription,
                command.templateType(),
                command.channel(),
                command.category(),
                normalizedSubject,
                normalizedBody,
                normalizedVariablesJson,
                command.active(),
                actorAppUserId
        );
        return toRecord(repository.save(entity));
    }

    @Transactional
    public CareTemplateRecord activate(UUID tenantId, UUID templateId, UUID actorAppUserId) {
        return setActive(tenantId, templateId, true, actorAppUserId);
    }

    @Transactional
    public CareTemplateRecord deactivate(UUID tenantId, UUID templateId, UUID actorAppUserId) {
        return setActive(tenantId, templateId, false, actorAppUserId);
    }

    @Transactional
    public void delete(UUID tenantId, UUID templateId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        CareTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (entity.isSystemTemplate()) {
            throw new IllegalArgumentException("System template cannot be deleted");
        }
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResult preview(UUID tenantId, UUID templateId, Map<String, String> variables) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        CareTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        return new TemplatePreviewResult(
                renderTemplate(entity.getSubject(), variables == null ? Map.of() : variables),
                renderTemplate(entity.getBody(), variables == null ? Map.of() : variables)
        );
    }

    /**
     * Simple safe placeholder substitution using {{variable}} tokens.
     */
    public String renderTemplate(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Map<String, String> safe = new LinkedHashMap<>();
        if (variables != null) {
            safe.putAll(variables);
        }
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = safe.getOrDefault(token, matcher.group(0));
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private void ensureDefaultSystemTemplates(UUID tenantId) {
        if (!repository.findByTenantIdAndSystemTemplateTrue(tenantId).isEmpty()) {
            return;
        }
        List<CareTemplateEntity> defaults = List.of(
                defaultTemplate(tenantId, "Appointment Reminder", TemplateType.REMINDER, TemplateChannel.EMAIL, TemplateCategory.APPOINTMENT_REMINDER, "Appointment reminder for {{patientName}}", "Hello {{patientName}}, this is a reminder for your appointment on {{appointmentDate}} at {{appointmentTime}}."),
                defaultTemplate(tenantId, "Refill Reminder", TemplateType.REMINDER, TemplateChannel.SMS, TemplateCategory.REFILL_REMINDER, null, "Hi {{patientName}}, your medicine refill for {{medicineName}} is due on {{refillDueDate}}."),
                defaultTemplate(tenantId, "Vaccination Reminder", TemplateType.REMINDER, TemplateChannel.WHATSAPP, TemplateCategory.VACCINATION, null, "Dear {{patientName}}, {{vaccineName}} is due on {{vaccinationDueDate}}."),
                defaultTemplate(tenantId, "Webinar Reminder", TemplateType.WEBINAR, TemplateChannel.EMAIL, TemplateCategory.WEBINAR, "Webinar reminder", "Hello {{patientName}}, join our webinar here: {{webinarLink}}"),
                defaultTemplate(tenantId, "Billing Receipt", TemplateType.BILLING, TemplateChannel.EMAIL, TemplateCategory.BILLING, "Payment confirmation for {{billNumber}}", "Hi {{patientName}}, we received {{billAmount}} for bill {{billNumber}}."),
                defaultTemplate(tenantId, "Lead Follow-up", TemplateType.LEAD, TemplateChannel.WHATSAPP, TemplateCategory.LEAD, null, "Hi {{leadName}}, following up regarding your enquiry with {{clinicName}}."),
                defaultTemplate(tenantId, "Wellness Message", TemplateType.CAMPAIGN, TemplateChannel.SMS, TemplateCategory.WELLNESS, null, "Hello {{patientName}}, stay healthy with regular checkups at {{clinicName}}.")
        );
        repository.saveAll(defaults);
    }

    private CareTemplateEntity defaultTemplate(
            UUID tenantId,
            String name,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            String subject,
            String body
    ) {
        return CareTemplateEntity.create(
                tenantId,
                name,
                "System default template",
                templateType,
                channel,
                category,
                subject,
                body,
                null,
                true,
                true,
                null
        );
    }

    private CareTemplateRecord setActive(UUID tenantId, UUID templateId, boolean active, UUID actorAppUserId) {
        CarePilotValidators.requireTenant(tenantId);
        CarePilotValidators.requireId(templateId, "templateId");
        CareTemplateEntity entity = repository.findByTenantIdAndId(tenantId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        entity.setActive(active, actorAppUserId);
        return toRecord(repository.save(entity));
    }

    private void validateRequired(CareTemplateUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Template payload is required");
        }
        requireText(command.name(), "Template name", MAX_NAME_LENGTH);
        requireEnum(command.templateType(), "Template type");
        TemplateChannel channel = requireEnum(command.channel(), "Channel");
        requireEnum(command.category(), "Category");
        String subject = normalizeNullable(command.subject());
        String body = requireMultilineText(command.body(), "Template body", MAX_BODY_LENGTH);
        validateSubject(channel, subject);
        validatePlaceholderSyntax(subject, "Subject");
        validatePlaceholderSyntax(body, "Body");
        validateDescription(command.description());
        validateVariablesJson(command.variablesJson());
    }

    private void validateUniqueName(UUID tenantId, TemplateType templateType, String name, UUID ignoreTemplateId) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        boolean duplicate = ignoreTemplateId == null
                ? repository.existsByTenantIdAndTemplateTypeAndNameIgnoreCase(tenantId, templateType, normalized)
                : repository.existsByTenantIdAndTemplateTypeAndNameIgnoreCaseAndIdNot(tenantId, templateType, normalized, ignoreTemplateId);
        if (duplicate) {
            throw new IllegalArgumentException("A template with this name already exists.");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException(label + " must not contain control characters.");
        }
        return normalized;
    }

    private String requireMultilineText(String value, String label, int maxLength) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private <T> T requireEnum(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private void validateDescription(String description) {
        String normalized = normalizeNullable(description);
        if (normalized == null) {
            return;
        }
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
            throw new IllegalArgumentException("Description must not contain control characters.");
        }
    }

    private void validateSubject(TemplateChannel channel, String subject) {
        if (channel == TemplateChannel.EMAIL && subject == null) {
            throw new IllegalArgumentException("Subject is required for email templates.");
        }
        if (subject != null && subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Subject must be " + MAX_SUBJECT_LENGTH + " characters or fewer.");
        }
        if (subject != null && CONTROL_CHAR_PATTERN.matcher(subject).find()) {
            throw new IllegalArgumentException("Subject must not contain control characters.");
        }
    }

    private void validateVariablesJson(String variablesJson) {
        String normalized = normalizeNullable(variablesJson);
        if (normalized == null) {
            return;
        }
        if (normalized.length() > MAX_VARIABLES_JSON_LENGTH) {
            throw new IllegalArgumentException("Variables JSON must be " + MAX_VARIABLES_JSON_LENGTH + " characters or fewer.");
        }
        try {
            JsonNode node = objectMapper.readTree(normalized);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Variables JSON must be a JSON object.");
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Enter valid JSON.");
        }
    }

    private void validatePlaceholderSyntax(String value, String label) {
        if (value == null) {
            return;
        }
        int index = 0;
        while (index < value.length()) {
            int open = value.indexOf("{{", index);
            int close = value.indexOf("}}", index);
            if (close >= 0 && (open < 0 || close < open)) {
                throw new IllegalArgumentException(label + " contains an invalid placeholder.");
            }
            if (open < 0) {
                return;
            }
            int end = value.indexOf("}}", open + 2);
            if (end < 0) {
                throw new IllegalArgumentException(label + " contains an invalid placeholder.");
            }
            String token = value.substring(open + 2, end).trim();
            if (token.isBlank() || !token.matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException(label + " contains an invalid placeholder.");
            }
            index = end + 2;
        }
    }

    private CareTemplateRecord toRecord(CareTemplateEntity entity) {
        return new CareTemplateRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDescription(),
                entity.getTemplateType(),
                entity.getChannel(),
                entity.getCategory(),
                entity.getSubject(),
                entity.getBody(),
                entity.getVariablesJson(),
                entity.isActive(),
                entity.isSystemTemplate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }
}
