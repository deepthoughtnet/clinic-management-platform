package com.deepthoughtnet.clinic.communication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PhoneAddressNormalizerTest {
    @Test void equivalentInternationalFormatsNormalizeToSameAddress() {
        assertEquals("+919999000001", PhoneAddressNormalizer.normalize("00919999000001"));
        assertEquals("+919999000001", PhoneAddressNormalizer.normalize("+919999000001"));
        assertEquals("+919999000001", PhoneAddressNormalizer.normalize("+91 9999-000-001"));
        assertEquals("+919999000001", PhoneAddressNormalizer.normalize("919999000001"));
    }

    @Test void nonIndianInternationalNumberRetainsCountryCode() {
        assertEquals("+442079460018", PhoneAddressNormalizer.normalize("00442079460018"));
        assertEquals("+442079460018", PhoneAddressNormalizer.normalize("+44 20 7946 0018"));
    }

    @Test void malformedAddressIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhoneAddressNormalizer.normalize("not-a-number"));
        assertThrows(IllegalArgumentException.class, () -> PhoneAddressNormalizer.normalize("+123"));
    }
}
