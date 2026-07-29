package com.deepthoughtnet.clinic.discover.verification;

public interface VerificationDeliveryPort {
    VerificationDeliveryResult deliver(VerificationDeliveryRequest request);
}
