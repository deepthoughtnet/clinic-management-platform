package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadCsvImportResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadCsvImportRowError;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignEntity;
import com.deepthoughtnet.clinic.carepilot.campaign.db.CampaignRepository;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadEntity;
import com.deepthoughtnet.clinic.carepilot.lead.db.LeadRepository;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadUpsertCommand;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CarePilotLeadCsvService {
    private static final String[] CSV_HEADERS = {
            "firstName", "lastName", "phone", "email", "source", "status", "priority", "campaignName",
            "assignedToEmail", "nextFollowUpAt", "sourceDetails", "tags", "notes"
    };

    private final LeadService leadService;
    private final LeadRepository leadRepository;
    private final CampaignRepository campaignRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final LeadActivityService leadActivityService;

    public CarePilotLeadCsvService(
            LeadService leadService,
            LeadRepository leadRepository,
            CampaignRepository campaignRepository,
            TenantUserManagementService tenantUserManagementService,
            LeadActivityService leadActivityService
    ) {
        this.leadService = leadService;
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.leadActivityService = leadActivityService;
    }

    @Transactional
    public LeadCsvImportResponse importCsv(UUID tenantId, byte[] csvBytes, UUID actorId) throws IOException {
        if (csvBytes == null || csvBytes.length == 0) {
            throw new IllegalArgumentException("CSV file is required");
        }

        List<LeadCsvImportRowError> rowErrors = new ArrayList<>();
        int importedCount = 0;
        int skippedDuplicateCount = 0;
        Map<String, UUID> campaignsByName = campaignsByName(tenantId);
        Map<String, UUID> usersByEmail = usersByEmail(tenantId);
        Set<String> importedPhones = new LinkedHashSet<>();
        Set<String> importedEmails = new LinkedHashSet<>();

        try (CSVParser parser = CSVParser.parse(
                new String(csvBytes, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build()
        )) {
            for (CSVRecord record : parser) {
                int rowNumber = Math.toIntExact(record.getRecordNumber()) + 1;
                try {
                    ParsedLeadImportRow row = parseRow(record, rowNumber, campaignsByName, usersByEmail);
                    if (isDuplicate(tenantId, row.phone(), row.email(), importedPhones, importedEmails)) {
                        skippedDuplicateCount++;
                        continue;
                    }

                    LeadRecord created = leadService.create(tenantId, new LeadUpsertCommand(
                            row.firstName(),
                            row.lastName(),
                            row.phone(),
                            row.email(),
                            null,
                            null,
                            row.source(),
                            row.sourceDetails(),
                            row.campaignId(),
                            row.assignedToAppUserId(),
                            row.status(),
                            row.priority(),
                            row.notes(),
                            row.tags(),
                            null,
                            row.nextFollowUpAt()
                    ), actorId);
                    leadActivityService.record(
                            tenantId,
                            created.id(),
                            LeadActivityType.UPDATED,
                            "Imported from CSV",
                            "Lead created from CSV import",
                            null,
                            null,
                            "IMPORT",
                            null,
                            actorId
                    );
                    if (StringUtils.hasText(row.phone())) {
                        importedPhones.add(normalizePhone(row.phone()));
                    }
                    if (StringUtils.hasText(row.email())) {
                        importedEmails.add(normalizeEmail(row.email()));
                    }
                    importedCount++;
                } catch (IllegalArgumentException ex) {
                    rowErrors.add(new LeadCsvImportRowError(rowNumber, ex.getMessage()));
                }
            }
        }

        return new LeadCsvImportResponse(importedCount, skippedDuplicateCount, rowErrors.size(), rowErrors);
    }

    @Transactional(readOnly = true)
    public String importTemplateCsv() throws IOException {
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            printer.printRecord(
                    "Ava",
                    "Smith",
                    "+15550123",
                    "ava@example.com",
                    "WEBINAR",
                    "NEW",
                    "MEDIUM",
                    "",
                    "",
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withSecond(0).withNano(0),
                    "Registered during webinar",
                    "vip,follow-up",
                    "Interested in preventive care plan"
            );
            printer.flush();
            return writer.toString();
        }
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId, LeadSearchCriteria criteria) throws IOException {
        List<LeadRecord> rows = leadService.searchAll(tenantId, criteria);
        Map<UUID, String> campaignNames = campaignNamesById(tenantId);
        Map<UUID, String> userEmails = userEmailsById(tenantId);
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            for (LeadRecord row : rows) {
                printer.printRecord(
                        safe(row.firstName()),
                        safe(row.lastName()),
                        safe(row.phone()),
                        safe(row.email()),
                        row.source() == null ? "" : row.source().name(),
                        row.status() == null ? "" : row.status().name(),
                        row.priority() == null ? "" : row.priority().name(),
                        safe(campaignNames.get(row.campaignId())),
                        safe(userEmails.get(row.assignedToAppUserId())),
                        row.nextFollowUpAt() == null ? "" : row.nextFollowUpAt().toString(),
                        safe(row.sourceDetails()),
                        safe(row.tags()),
                        safe(row.notes())
                );
            }
            printer.flush();
            return writer.toString();
        }
    }

    private ParsedLeadImportRow parseRow(
            CSVRecord record,
            int rowNumber,
            Map<String, UUID> campaignsByName,
            Map<String, UUID> usersByEmail
    ) {
        String firstNameRaw = value(record, "firstName");
        String phone = normalizeNullable(value(record, "phone"));
        String email = normalizeNullable(value(record, "email"));
        if (!StringUtils.hasText(firstNameRaw) && !StringUtils.hasText(phone) && !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("firstName or phone/email is required");
        }
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("phone is required");
        }
        String normalizedPhone = normalizePhone(phone);
        if (!normalizedPhone.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("phone must be a valid phone number");
        }

        String firstName = StringUtils.hasText(firstNameRaw) ? firstNameRaw.trim() : inferredFirstName(email);
        LeadSource source = parseEnum(value(record, "source"), LeadSource.class, LeadSource.MANUAL, "source", rowNumber);
        LeadStatus status = parseEnum(value(record, "status"), LeadStatus.class, LeadStatus.NEW, "status", rowNumber);
        LeadPriority priority = parseEnum(value(record, "priority"), LeadPriority.class, LeadPriority.MEDIUM, "priority", rowNumber);

        String campaignName = normalizeNullable(value(record, "campaignName"));
        UUID campaignId = null;
        if (StringUtils.hasText(campaignName)) {
            campaignId = campaignsByName.get(campaignName.toLowerCase(Locale.ROOT));
            if (campaignId == null) {
                throw new IllegalArgumentException("campaignName not found for tenant");
            }
        }

        String assignedToEmail = normalizeNullable(value(record, "assignedToEmail"));
        UUID assignedToAppUserId = null;
        if (StringUtils.hasText(assignedToEmail)) {
            assignedToAppUserId = usersByEmail.get(assignedToEmail.toLowerCase(Locale.ROOT));
            if (assignedToAppUserId == null) {
                throw new IllegalArgumentException("assignedToEmail not found for tenant");
            }
        }

        return new ParsedLeadImportRow(
                firstName,
                normalizeNullable(value(record, "lastName")),
                normalizedPhone,
                email == null ? null : email.trim(),
                source,
                status,
                priority,
                campaignId,
                assignedToAppUserId,
                parseNextFollowUpAt(value(record, "nextFollowUpAt"), rowNumber),
                normalizeNullable(value(record, "sourceDetails")),
                normalizeNullable(value(record, "tags")),
                normalizeNullable(value(record, "notes"))
        );
    }

    private boolean isDuplicate(UUID tenantId, String phone, String email, Set<String> importedPhones, Set<String> importedEmails) {
        String normalizedPhone = normalizePhone(phone);
        if (importedPhones.contains(normalizedPhone) || leadRepository.findFirstByTenantIdAndPhoneIgnoreCase(tenantId, normalizedPhone).isPresent()) {
            return true;
        }
        String normalizedEmail = normalizeEmail(email);
        return StringUtils.hasText(normalizedEmail)
                && (importedEmails.contains(normalizedEmail) || leadRepository.findFirstByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail).isPresent());
    }

    private Map<String, UUID> campaignsByName(UUID tenantId) {
        Map<String, UUID> campaigns = new LinkedHashMap<>();
        for (CampaignEntity campaign : campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (StringUtils.hasText(campaign.getName())) {
                campaigns.putIfAbsent(campaign.getName().trim().toLowerCase(Locale.ROOT), campaign.getId());
            }
        }
        return campaigns;
    }

    private Map<UUID, String> campaignNamesById(UUID tenantId) {
        Map<UUID, String> campaigns = new LinkedHashMap<>();
        for (CampaignEntity campaign : campaignRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            campaigns.put(campaign.getId(), campaign.getName());
        }
        return campaigns;
    }

    private Map<String, UUID> usersByEmail(UUID tenantId) {
        Map<String, UUID> users = new LinkedHashMap<>();
        for (TenantUserRecord user : tenantUserManagementService.list(tenantId)) {
            if (StringUtils.hasText(user.email())) {
                users.putIfAbsent(user.email().trim().toLowerCase(Locale.ROOT), user.appUserId());
            }
        }
        return users;
    }

    private Map<UUID, String> userEmailsById(UUID tenantId) {
        Map<UUID, String> users = new LinkedHashMap<>();
        for (TenantUserRecord user : tenantUserManagementService.list(tenantId)) {
            users.put(user.appUserId(), user.email());
        }
        return users;
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E defaultValue, String label, int rowNumber) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid " + label + " '" + raw.trim() + "'");
        }
    }

    private OffsetDateTime parseNextFollowUpAt(String raw, int rowNumber) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("invalid nextFollowUpAt '" + value + "'");
    }

    private String value(CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column) : null;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String inferredFirstName(String email) {
        if (StringUtils.hasText(email) && email.contains("@")) {
            String candidate = email.substring(0, email.indexOf('@')).trim();
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "Imported";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ParsedLeadImportRow(
            String firstName,
            String lastName,
            String phone,
            String email,
            LeadSource source,
            LeadStatus status,
            LeadPriority priority,
            UUID campaignId,
            UUID assignedToAppUserId,
            OffsetDateTime nextFollowUpAt,
            String sourceDetails,
            String tags,
            String notes
    ) {}
}
