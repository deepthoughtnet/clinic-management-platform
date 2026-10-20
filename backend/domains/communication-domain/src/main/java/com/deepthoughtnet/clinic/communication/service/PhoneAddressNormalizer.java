package com.deepthoughtnet.clinic.communication.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Provider-neutral E.164-style normalization for configured phone addresses. */
public final class PhoneAddressNormalizer {
    private static final Pattern ALLOWED = Pattern.compile("^[+0-9() .\\-]+$");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]{8,15}$");

    private PhoneAddressNormalizer() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DID/phone address is required");
        }
        String input = value.trim();
        if (!ALLOWED.matcher(input).matches()) {
            throw new IllegalArgumentException("DID/phone address contains unsupported characters");
        }
        String compact = input.replaceAll("[() .\\-]", "");
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (compact.startsWith("+")) compact = "+" + compact.substring(1).replace("+", "");
        if (compact.startsWith("+")) {
            if (!DIGITS.matcher(compact.substring(1)).matches()) throw new IllegalArgumentException("Invalid DID/phone address");
            return compact;
        }
        if (!DIGITS.matcher(compact).matches()) throw new IllegalArgumentException("Invalid DID/phone address");
        return "+" + compact;
    }
}
