package com.deepthoughtnet.clinic.api.lab;

import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterEntity;
import com.deepthoughtnet.clinic.api.lab.db.LabTestMasterRepository;
import com.deepthoughtnet.clinic.api.lab.dto.LabCsvImportResponse;
import com.deepthoughtnet.clinic.api.lab.dto.LabCsvImportRowError;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabTestUpsertCommand;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LabCsvService {
    private static final String[] CSV_HEADERS = {
            "testCode",
            "testName",
            "category",
            "sampleType",
            "department",
            "unit",
            "referenceRange",
            "turnaroundTime",
            "price",
            "active",
            "parameters"
    };

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "HEMATOLOGY",
            "BIOCHEMISTRY",
            "MICROBIOLOGY",
            "PATHOLOGY",
            "RADIOLOGY",
            "CARDIOLOGY",
            "OTHER"
    );

    private final LabService labService;
    private final LabTestMasterRepository labTestMasterRepository;

    public LabCsvService(LabService labService, LabTestMasterRepository labTestMasterRepository) {
        this.labService = labService;
        this.labTestMasterRepository = labTestMasterRepository;
    }

    @Transactional
    public LabCsvImportResponse importCsv(UUID tenantId, byte[] csvBytes, UUID actorAppUserId) throws IOException {
        if (csvBytes == null || csvBytes.length == 0) {
            throw new IllegalArgumentException("CSV file is required");
        }

        List<LabCsvImportRowError> rowErrors = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        try (CSVParser parser = CSVParser.parse(
                new String(csvBytes, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build()
        )) {
            for (CSVRecord record : parser) {
                int rowNumber = Math.toIntExact(record.getRecordNumber()) + 1;
                try {
                    ParsedLabTestRow row = parseRow(record, rowNumber);
                    Optional<LabTestMasterEntity> existing = findExisting(tenantId, row.testCode(), row.testName());
                    LabTestUpsertCommand command = new LabTestUpsertCommand(
                            row.testCode(),
                            row.testName(),
                            row.category(),
                            row.department(),
                            row.sampleType(),
                            row.unit(),
                            row.referenceRange(),
                            row.turnaroundTime(),
                            row.price(),
                            row.active(),
                            row.parameters()
                    );
                    if (existing.isPresent()) {
                        labService.updateTest(tenantId, existing.get().getId(), command, actorAppUserId);
                        updatedCount++;
                    } else {
                        labService.createTest(tenantId, command, actorAppUserId);
                        createdCount++;
                    }
                } catch (RuntimeException ex) {
                    rowErrors.add(new LabCsvImportRowError(rowNumber, friendlyMessage(ex)));
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse lab test CSV");
        }

        int totalRows = createdCount + updatedCount + rowErrors.size();
        return new LabCsvImportResponse(totalRows, createdCount, updatedCount, rowErrors.size(), rowErrors);
    }

    @Transactional(readOnly = true)
    public String importTemplateCsv() throws IOException {
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build())) {
            printer.printRecord(
                    "CBC",
                    "Complete Blood Count",
                    "HEMATOLOGY",
                    "Blood",
                    "Hematology",
                    "g/dL",
                    "13.0-17.0",
                    "24 hrs",
                    "250.00",
                    "true",
                    "Hemoglobin|g/dL|13.0-17.0|<7.0|1;WBC|10^3/uL|4.0-11.0|>20.0|2"
            );
            printer.flush();
            return writer.toString();
        }
    }

    private Optional<LabTestMasterEntity> findExisting(UUID tenantId, String testCode, String testName) {
        if (StringUtils.hasText(testCode)) {
            Optional<LabTestMasterEntity> byCode = labTestMasterRepository.findByTenantIdAndTestCodeIgnoreCase(tenantId, testCode.trim());
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        if (StringUtils.hasText(testName)) {
            return labTestMasterRepository.findByTenantIdAndTestNameIgnoreCase(tenantId, testName.trim());
        }
        return Optional.empty();
    }

    private ParsedLabTestRow parseRow(CSVRecord record, int rowNumber) {
        String testCode = normalizeNullable(value(record, "testCode"));
        if (StringUtils.hasText(testCode) && !testCode.matches("^[A-Za-z0-9/_-]{1,30}$")) {
            throw new IllegalArgumentException("testCode contains invalid characters");
        }
        String testName = requireText(value(record, "testName"), "testName is required");
        if (testName.length() > 100) {
            throw new IllegalArgumentException("testName must be 100 characters or fewer");
        }
        if (!hasLetterOrNumber(testName)) {
            throw new IllegalArgumentException("testName must contain letters or numbers");
        }
        String category = requireText(value(record, "category"), "category is required").toUpperCase(Locale.ROOT);
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("category is invalid");
        }
        String sampleType = requireText(value(record, "sampleType"), "sampleType is required");
        if (sampleType.length() > 60) {
            throw new IllegalArgumentException("sampleType must be 60 characters or fewer");
        }
        String department = normalizeNullable(value(record, "department"));
        if (department != null && department.length() > 60) {
            throw new IllegalArgumentException("department must be 60 characters or fewer");
        }
        String unit = normalizeNullable(value(record, "unit"));
        if (unit != null && unit.length() > 30) {
            throw new IllegalArgumentException("unit must be 30 characters or fewer");
        }
        String referenceRange = normalizeNullable(value(record, "referenceRange"));
        if (referenceRange != null && referenceRange.length() > 120) {
            throw new IllegalArgumentException("referenceRange must be 120 characters or fewer");
        }
        String turnaroundTime = normalizeNullable(value(record, "turnaroundTime"));
        if (turnaroundTime != null && turnaroundTime.length() > 30) {
            throw new IllegalArgumentException("turnaroundTime must be 30 characters or fewer");
        }
        String priceText = requireText(value(record, "price"), "price is required");
        BigDecimal price;
        try {
            price = new BigDecimal(priceText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("price must be a valid amount");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0 || price.compareTo(new BigDecimal("999999.00")) > 0) {
            throw new IllegalArgumentException("price must be between 0 and 999999.00");
        }
        boolean active = parseBoolean(value(record, "active"), true);
        List<com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand> parameters = parseParameters(value(record, "parameters"));
        return new ParsedLabTestRow(testCode, testName, category, department, sampleType, unit, referenceRange, turnaroundTime, price, active, parameters);
    }

    private List<com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand> parseParameters(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] entries = raw.split(";");
        List<com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand> parameters = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index].trim();
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            String[] parts = entry.split("\\|", -1);
            if (parts.length == 0) {
                throw new IllegalArgumentException("parameters are invalid");
            }
            String parameterName = requireText(parts[0], "parameter name is required");
            if (parameterName.length() > 60) {
                throw new IllegalArgumentException("parameterName must be 60 characters or fewer");
            }
            if (seen.stream().anyMatch(existing -> existing.equalsIgnoreCase(parameterName))) {
                throw new IllegalArgumentException("parameter names must be unique within a test");
            }
            seen.add(parameterName);
            String unit = parts.length > 1 ? normalizeNullable(parts[1]) : null;
            String normalRange = parts.length > 2 ? normalizeNullable(parts[2]) : null;
            String criticalRange = parts.length > 3 ? normalizeNullable(parts[3]) : null;
            int sortOrder = index + 1;
            if (parts.length > 4 && StringUtils.hasText(parts[4])) {
                try {
                    sortOrder = Integer.parseInt(parts[4].trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("sortOrder must be a whole number");
                }
            }
            parameters.add(new com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand(
                    parameterName,
                    unit,
                    normalRange,
                    criticalRange,
                    sortOrder
            ));
        }
        return parameters;
    }

    private String value(CSVRecord record, String header) {
        return record.isMapped(header) ? record.get(header) : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim())) {
            return Boolean.parseBoolean(value.trim());
        }
        throw new IllegalArgumentException("active must be true or false");
    }

    private boolean hasLetterOrNumber(String value) {
        return value != null && value.matches(".*[A-Za-z0-9].*");
    }

    private String friendlyMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : "Invalid row";
    }

    private record ParsedLabTestRow(
            String testCode,
            String testName,
            String category,
            String department,
            String sampleType,
            String unit,
            String referenceRange,
            String turnaroundTime,
            BigDecimal price,
            boolean active,
            List<com.deepthoughtnet.clinic.api.lab.service.model.LabTestParameterUpsertCommand> parameters
    ) {
    }
}
