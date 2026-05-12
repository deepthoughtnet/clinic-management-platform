package com.deepthoughtnet.clinic.messaging.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageRequestTest {

    @Test
    void validatesRequiredFields() {
        assertThatThrownBy(() -> new MessageRequest(null, MessageChannel.EMAIL, new MessageRecipient("a@b.com", null), null, "body", null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new MessageRequest(UUID.randomUUID(), null, new MessageRecipient("a@b.com", null), null, "body", null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel");
        assertThatThrownBy(() -> new MessageRequest(UUID.randomUUID(), MessageChannel.EMAIL, null, null, "body", null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
        assertThatThrownBy(() -> new MessageRequest(UUID.randomUUID(), MessageChannel.EMAIL, new MessageRecipient("a@b.com", null), null, " ", null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }

    @Test
    void normalizesMetadataToImmutableMap() {
        MessageRequest request = new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.EMAIL,
                new MessageRecipient("a@b.com", null),
                "s",
                "b",
                null,
                null,
                null,
                null,
                null
        );

        assertThat(request.metadata()).isEmpty();
        assertThatThrownBy(() -> request.metadata().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
