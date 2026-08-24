package com.deepthoughtnet.clinic.api.lab;

import java.math.BigDecimal;
import org.springframework.util.StringUtils;

public final class LabValidationSupport {
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999.00");

    private LabValidationSupport() {
    }

    public static String normalizeWholeHours(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("^\\d{1,3}$")) {
            throw new IllegalArgumentException(fieldName + " must be a whole number between 0 and 999");
        }
        int parsed = Integer.parseInt(trimmed);
        if (parsed < 0 || parsed > 999) {
            throw new IllegalArgumentException(fieldName + " must be a whole number between 0 and 999");
        }
        return String.valueOf(parsed);
    }

    public static String normalizeTestCode(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 30) {
            throw new IllegalArgumentException(fieldName + " must be 30 characters or fewer");
        }
        if (!trimmed.matches("^[A-Za-z0-9/_-]+$")) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }
        return trimmed;
    }

    public static BigDecimal normalizeMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must be zero or greater");
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(fieldName + " exceeds the allowed maximum");
        }
        return value.setScale(2);
    }

    public static Integer normalizeDisplayOrder(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be zero or greater");
        }
        return value;
    }
}
