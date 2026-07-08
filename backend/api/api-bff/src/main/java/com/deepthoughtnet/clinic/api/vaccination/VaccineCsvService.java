package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportResponse;
import com.deepthoughtnet.clinic.api.vaccination.dto.VaccineCsvImportRowResult;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineUpsertCommand;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VaccineCsvService {
    private static final String[] REQUIRED_COLUMNS = {
            "vaccineName",
            "description",
            "ageGroup",
            "gapDays",
            "defaultPrice",
            "active"
    };

    private static final String[] CSV_HEADERS = {
            "vaccineName",
            "description",
            "manufacturer",
            "brandName",
            "vaccineGroup",
            "doseNumber",
            "route",
            "administrationSite",
            "storageTemperature",
            "ndcBarcode",
            "scheduleType",
            "ageGroup",
            "minAgeDays",
            "recommendedAgeDays",
            "maxAgeDays",
            "gapDays",
            "boosterGapDays",
            "boosterRules",
            "isRecurring",
            "recurrenceDays",
            "recommendationPolicy",
            "catchUpPolicy",
            "catchUpMaxAgeDays",
            "applicableAgeGroup",
            "clinicalIndications",
            "defaultPrice",
            "active"
    };

    private final VaccinationService vaccinationService;

    public VaccineCsvService(VaccinationService vaccinationService) {
        this.vaccinationService = vaccinationService;
    }

    @Transactional
    public VaccineCsvImportResponse importCsv(UUID tenantId, byte[] csvBytes, UUID actorAppUserId) {
        if (csvBytes == null || csvBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }

        List<VaccineCsvImportRowResult> rows = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenMasterKeys = new HashSet<>();
        int createdCount = 0;
        int failedCount = 0;

        try (CSVParser parser = CSVParser.parse(
                new String(csvBytes, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build()
        )) {
            for (CSVRecord record : parser) {
                int rowNumber = Math.toIntExact(record.getRecordNumber()) + 1;
                String rawName = value(record, "vaccineName");
                try {
                    ParsedRow row = parseRow(record, rowNumber);
                    String normalizedName = row.vaccineName().toLowerCase(Locale.ROOT);
                    if (!seenNames.add(normalizedName)) {
                        throw new IllegalArgumentException("Duplicate vaccine name in CSV");
                    }
                    String masterKey = row.masterKey();
                    if (StringUtils.hasText(masterKey) && !seenMasterKeys.add(masterKey)) {
                        throw new IllegalArgumentException("Duplicate vaccine group/dose/schedule combination in CSV");
                    }
                    vaccinationService.createVaccine(tenantId, row.command(), actorAppUserId);
                    createdCount++;
                    rows.add(new VaccineCsvImportRowResult(rowNumber, row.vaccineName(), "CREATED", "Imported successfully"));
                } catch (RuntimeException ex) {
                    failedCount++;
                    rows.add(new VaccineCsvImportRowResult(rowNumber, StringUtils.hasText(rawName) ? rawName.trim() : "", "FAILED", friendlyMessage(ex)));
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse vaccine CSV");
        }

        return new VaccineCsvImportResponse(createdCount + failedCount, createdCount, failedCount, rows);
    }

    @Transactional(readOnly = true)
    public String importTemplateCsv() {
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            printer.printRecord(
                    "COVID Booster",
                    "Annual booster",
                    "Acme Labs",
                    "Comvax",
                    "ADULT BOOSTER",
                    "1",
                    "IM",
                    "Deltoid",
                    "2-8 C",
                    "123456789",
                    "ADULT",
                    "Adults",
                    "3650",
                    "3650",
                    "3650",
                    "3650",
                    "365",
                    "Annual booster",
                    "false",
                    "365",
                    "ADULT_ROUTINE",
                    "NONE",
                    "",
                    "ADULT",
                    "travel,occupational",
                    "250.00",
                    "true"
            );
            printer.flush();
            return writer.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build vaccine CSV template", ex);
        }
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId) {
        List<VaccineMasterRecord> vaccines = vaccinationService.listVaccines(tenantId);
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            for (VaccineMasterRecord vaccine : vaccines) {
                printer.printRecord(
                        vaccine.vaccineName(),
                        valueOrEmpty(vaccine.description()),
                        valueOrEmpty(vaccine.manufacturer()),
                        valueOrEmpty(vaccine.brandName()),
                        valueOrEmpty(vaccine.vaccineGroup()),
                        valueOrEmpty(vaccine.doseNumber()),
                        valueOrEmpty(vaccine.route()),
                        valueOrEmpty(vaccine.administrationSite()),
                        valueOrEmpty(vaccine.storageTemperature()),
                        valueOrEmpty(vaccine.ndcBarcode()),
                        valueOrEmpty(vaccine.scheduleType()),
                        valueOrEmpty(vaccine.ageGroup()),
                        valueOrEmpty(vaccine.minAgeDays()),
                        valueOrEmpty(vaccine.recommendedAgeDays()),
                        valueOrEmpty(vaccine.maxAgeDays()),
                        valueOrEmpty(firstNonNull(vaccine.gapDays(), vaccine.recommendedGapDays())),
                        valueOrEmpty(vaccine.boosterGapDays()),
                        valueOrEmpty(vaccine.boosterRules()),
                        Boolean.toString(vaccine.recurring()),
                        valueOrEmpty(vaccine.recurrenceDays()),
                        valueOrEmpty(vaccine.recommendationPolicy()),
                        valueOrEmpty(vaccine.catchUpPolicy()),
                        valueOrEmpty(vaccine.catchUpMaxAgeDays()),
                        valueOrEmpty(vaccine.applicableAgeGroup()),
                        valueOrEmpty(vaccine.clinicalIndications()),
                        vaccine.defaultPrice() == null ? "" : vaccine.defaultPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        Boolean.toString(vaccine.active())
                );
            }
            printer.flush();
            return writer.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to export vaccine CSV", ex);
        }
    }

    private ParsedRow parseRow(CSVRecord record, int rowNumber) {
        String vaccineName = requireText(value(record, "vaccineName"), "vaccineName is required");
        if (vaccineName.length() > 100) {
            throw new IllegalArgumentException("vaccineName must be 100 characters or fewer");
        }
        String description = normalizeNullable(value(record, "description"));
        if (description != null && description.length() > 250) {
            throw new IllegalArgumentException("description must be 250 characters or fewer");
        }
        String manufacturer = normalizeNullable(value(record, "manufacturer"));
        if (manufacturer != null && manufacturer.length() > 250) {
            throw new IllegalArgumentException("manufacturer must be 250 characters or fewer");
        }
        String brandName = normalizeNullable(value(record, "brandName"));
        if (brandName != null && brandName.length() > 250) {
            throw new IllegalArgumentException("brandName must be 250 characters or fewer");
        }
        String vaccineGroup = normalizeNullable(value(record, "vaccineGroup"));
        if (vaccineGroup != null && vaccineGroup.length() > 128) {
            throw new IllegalArgumentException("vaccineGroup must be 128 characters or fewer");
        }
        Integer doseNumber = parseInteger(value(record, "doseNumber"), "doseNumber");
        String route = normalizeNullable(value(record, "route"));
        if (route != null) {
            route = normalizeRoute(route);
        }
        String administrationSite = normalizeNullable(value(record, "administrationSite"));
        if (administrationSite != null && administrationSite.length() > 128) {
            throw new IllegalArgumentException("administrationSite must be 128 characters or fewer");
        }
        String storageTemperature = normalizeNullable(value(record, "storageTemperature"));
        if (storageTemperature != null && storageTemperature.length() > 128) {
            throw new IllegalArgumentException("storageTemperature must be 128 characters or fewer");
        }
        String ndcBarcode = normalizeNullable(value(record, "ndcBarcode"));
        if (ndcBarcode != null && ndcBarcode.length() > 128) {
            throw new IllegalArgumentException("ndcBarcode must be 128 characters or fewer");
        }
        String scheduleType = normalizeNullable(value(record, "scheduleType"));
        if (scheduleType != null) {
            scheduleType = normalizeScheduleType(scheduleType);
        }
        String ageGroup = normalizeNullable(value(record, "ageGroup"));
        if (ageGroup != null && ageGroup.length() > 60) {
            throw new IllegalArgumentException("ageGroup must be 60 characters or fewer");
        }
        Integer minAgeDays = parseInteger(value(record, "minAgeDays"), "minAgeDays");
        Integer recommendedAgeDays = parseInteger(value(record, "recommendedAgeDays"), "recommendedAgeDays");
        Integer maxAgeDays = parseInteger(value(record, "maxAgeDays"), "maxAgeDays");
        Integer gapDays = parseInteger(valueAny(record, "gapDays", "recommendedGapDays"), "gapDays");
        Integer boosterGapDays = parseInteger(value(record, "boosterGapDays"), "boosterGapDays");
        String boosterRules = normalizeNullable(value(record, "boosterRules"));
        if (boosterRules != null && boosterRules.length() > 500) {
            throw new IllegalArgumentException("boosterRules must be 500 characters or fewer");
        }
        boolean recurring = parseOptionalBoolean(value(record, "isRecurring"), false);
        Integer recurrenceDays = parseInteger(value(record, "recurrenceDays"), "recurrenceDays");
        String recommendationPolicy = resolveRecommendationPolicy(
                value(record, "recommendationPolicy"),
                scheduleType,
                recurring,
                ageGroup,
                recommendedAgeDays
        );
        String catchUpPolicy = resolveCatchUpPolicy(value(record, "catchUpPolicy"));
        Integer catchUpMaxAgeDays = parseInteger(value(record, "catchUpMaxAgeDays"), "catchUpMaxAgeDays");
        String applicableAgeGroup = resolveApplicableAgeGroup(value(record, "applicableAgeGroup"), ageGroup, recommendedAgeDays, minAgeDays);
        String clinicalIndications = normalizeNullable(value(record, "clinicalIndications"));
        if (clinicalIndications != null && clinicalIndications.length() > 500) {
            throw new IllegalArgumentException("clinicalIndications must be 500 characters or fewer");
        }
        BigDecimal defaultPrice = parseMoney(value(record, "defaultPrice"), "defaultPrice");
        if (defaultPrice != null && defaultPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("defaultPrice must be 0 or greater");
        }
        boolean active = parseRequiredBoolean(value(record, "active"));

        VaccineUpsertCommand command = new VaccineUpsertCommand(
                vaccineName,
                description,
                manufacturer,
                brandName,
                vaccineGroup,
                doseNumber,
                route,
                administrationSite,
                storageTemperature,
                ndcBarcode,
                null,
                null,
                false,
                scheduleType,
                ageGroup,
                minAgeDays,
                recommendedAgeDays,
                maxAgeDays,
                gapDays,
                gapDays,
                boosterGapDays,
                boosterRules,
                recurring,
                recurrenceDays,
                recommendationPolicy,
                catchUpPolicy,
                catchUpMaxAgeDays,
                applicableAgeGroup,
                clinicalIndications,
                defaultPrice,
                active
        );
        return new ParsedRow(vaccineName, command, masterKey(vaccineGroup, doseNumber, scheduleType));
    }

    private String friendlyMessage(RuntimeException ex) {
        String message = ex.getMessage() == null ? "Validation failed" : ex.getMessage();
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("already exists")) {
            return "Duplicate vaccine name";
        }
        if (normalized.contains("duplicate vaccine group")) {
            return "Duplicate vaccine group/dose/schedule combination";
        }
        return message;
    }

    private String value(CSVRecord record, String header) {
        return record.isMapped(header) ? record.get(header) : null;
    }

    private String valueAny(CSVRecord record, String... headers) {
        for (String header : headers) {
            String value = value(record, header);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer parseInteger(String value, String field) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        try {
            Integer parsed = Integer.parseInt(normalized);
            if (parsed < 0) {
                throw new IllegalArgumentException(field + " must be 0 or greater");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
    }

    private BigDecimal parseMoney(String value, String field) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(normalized);
            if (parsed.scale() > 2) {
                throw new IllegalArgumentException(field + " must use at most 2 decimals");
            }
            return parsed.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a valid amount");
        }
    }

    private boolean parseRequiredBoolean(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException("active is required");
        }
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        throw new IllegalArgumentException("active must be true or false");
    }

    private boolean parseOptionalBoolean(String value, boolean defaultValue) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.parseBoolean(normalized);
        }
        throw new IllegalArgumentException("isRecurring must be true or false");
    }

    private String normalizeRoute(String value) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IM", "SC", "ORAL", "NASAL", "ID").contains(upper)) {
            throw new IllegalArgumentException("route must be one of IM, SC, ORAL, NASAL, ID");
        }
        return upper;
    }

    private String normalizeScheduleType(String value) {
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("UIP", "IAP", "CLINIC_CUSTOM", "TRAVEL", "ADULT").contains(upper)) {
            throw new IllegalArgumentException("scheduleType must be one of UIP, IAP, CLINIC_CUSTOM, TRAVEL, ADULT");
        }
        return upper;
    }

    private String resolveRecommendationPolicy(String explicit, String scheduleType, boolean recurring, String ageGroup, Integer recommendedAgeDays) {
        String normalized = normalizeNullable(explicit);
        if (normalized != null) {
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (!Set.of("STANDARD_CHILDHOOD", "CHILDHOOD_CATCHUP", "ADULT_ROUTINE", "ADULT_RISK_BASED", "PREGNANCY", "TRAVEL", "OCCUPATIONAL", "RECURRING", "CLINIC_CUSTOM").contains(upper)) {
                throw new IllegalArgumentException("recommendationPolicy must be one of STANDARD_CHILDHOOD, CHILDHOOD_CATCHUP, ADULT_ROUTINE, ADULT_RISK_BASED, PREGNANCY, TRAVEL, OCCUPATIONAL, RECURRING, CLINIC_CUSTOM");
            }
            return upper;
        }
        if ("ADULT".equalsIgnoreCase(scheduleType)) {
            return "ADULT_ROUTINE";
        }
        if (recurring) {
            return "RECURRING";
        }
        if (looksChildhoodSchedule(ageGroup, recommendedAgeDays)) {
            return "STANDARD_CHILDHOOD";
        }
        return "CLINIC_CUSTOM";
    }

    private String resolveCatchUpPolicy(String explicit) {
        String normalized = normalizeNullable(explicit);
        if (normalized == null) {
            return "NONE";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("NONE", "ALLOWED_UNTIL_AGE", "LIFETIME", "CLINICIAN_DECISION").contains(upper)) {
            throw new IllegalArgumentException("catchUpPolicy must be one of NONE, ALLOWED_UNTIL_AGE, LIFETIME, CLINICIAN_DECISION");
        }
        return upper;
    }

    private String resolveApplicableAgeGroup(String explicit, String ageGroup, Integer recommendedAgeDays, Integer minAgeDays) {
        String normalized = normalizeNullable(explicit);
        if (normalized != null) {
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (!Set.of("NEWBORN", "INFANT", "TODDLER", "CHILD", "ADOLESCENT", "ADULT", "OLDER_ADULT", "ALL").contains(upper)) {
                throw new IllegalArgumentException("applicableAgeGroup must be one of NEWBORN, INFANT, TODDLER, CHILD, ADOLESCENT, ADULT, OLDER_ADULT, ALL");
            }
            return upper;
        }
        if (StringUtils.hasText(ageGroup)) {
            return inferApplicableAgeGroupFromAgeGroup(ageGroup);
        }
        Integer reference = recommendedAgeDays != null ? recommendedAgeDays : minAgeDays;
        if (reference == null) {
            return "ALL";
        }
        if (reference <= 28) return "NEWBORN";
        if (reference <= 365) return "INFANT";
        if (reference <= 3 * 365) return "TODDLER";
        if (reference <= 9 * 365) return "CHILD";
        if (reference <= 17 * 365) return "ADOLESCENT";
        if (reference <= 59 * 365) return "ADULT";
        return "OLDER_ADULT";
    }

    private boolean looksChildhoodSchedule(String ageGroup, Integer recommendedAgeDays) {
        if (recommendedAgeDays != null && recommendedAgeDays <= 17 * 365) {
            return true;
        }
        if (!StringUtils.hasText(ageGroup)) {
            return false;
        }
        String token = ageGroup.toUpperCase(Locale.ROOT);
        return token.contains("NEWBORN")
                || token.contains("INFANT")
                || token.contains("TODDLER")
                || token.contains("CHILD")
                || token.contains("ADOLESCENT")
                || token.contains("WEEK")
                || token.contains("MONTH");
    }

    private String inferApplicableAgeGroupFromAgeGroup(String ageGroup) {
        String token = ageGroup.trim().toUpperCase(Locale.ROOT);
        if (token.contains("NEWBORN") || token.contains("BIRTH")) return "NEWBORN";
        if (token.contains("INFANT") || token.contains("WEEK") || token.contains("MONTH")) return "INFANT";
        if (token.contains("TODDLER") || token.contains("1 YEAR") || token.contains("2 YEAR") || token.contains("3 YEAR")) return "TODDLER";
        if (token.contains("CHILD") || token.contains("4 YEAR") || token.contains("5 YEAR") || token.contains("6 YEAR") || token.contains("7 YEAR") || token.contains("8 YEAR") || token.contains("9 YEAR")) return "CHILD";
        if (token.contains("ADOLESCENT") || token.contains("10 YEAR") || token.contains("11 YEAR") || token.contains("12 YEAR") || token.contains("13 YEAR") || token.contains("14 YEAR") || token.contains("15 YEAR") || token.contains("16 YEAR") || token.contains("17 YEAR")) return "ADOLESCENT";
        if (token.contains("OLDER") || token.contains("SENIOR")) return "OLDER_ADULT";
        if (token.contains("ADULT")) return "ADULT";
        return "ALL";
    }

    private String masterKey(String vaccineGroup, Integer doseNumber, String scheduleType) {
        if (!StringUtils.hasText(vaccineGroup) && doseNumber == null && !StringUtils.hasText(scheduleType)) {
            return "";
        }
        return String.join(
                "|",
                normalizeNullable(vaccineGroup) == null ? "" : normalizeNullable(vaccineGroup).toLowerCase(Locale.ROOT),
                doseNumber == null ? "" : doseNumber.toString(),
                normalizeNullable(scheduleType) == null ? "" : normalizeNullable(scheduleType).toUpperCase(Locale.ROOT)
        );
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String valueOrEmpty(Integer value) {
        return value == null ? "" : value.toString();
    }

    private Integer firstNonNull(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record ParsedRow(String vaccineName, VaccineUpsertCommand command, String masterKey) {
    }
}
