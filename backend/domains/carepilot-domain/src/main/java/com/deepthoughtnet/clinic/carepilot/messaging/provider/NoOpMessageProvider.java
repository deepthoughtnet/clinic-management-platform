package com.deepthoughtnet.clinic.carepilot.messaging.provider;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Safe fallback provider used when no concrete provider is configured.
 *
 * <p>This provider never calls external systems and always returns a non-sent outcome.
 */
@Component
public class NoOpMessageProvider implements MessageProvider {
    private static final Logger log = LoggerFactory.getLogger(NoOpMessageProvider.class);

    @Override
    public boolean supports(MessageChannel channel) {
        return true;
    }

    @Override
    public MessageResult send(MessageRequest request) {
        log.info(
                "CarePilot message skipped because no concrete provider is configured. tenantId={}, channel={}, recipient={}, executionId={}, correlationId={}",
                request.tenantId(), request.channel(), request.recipient().address(), request.executionId(), request.correlationId()
        );
        return MessageResult.providerUnavailable(providerName(), "No provider configured for channel " + request.channel());
    }

    @Override
    public String providerName() {
        return "carepilot-noop";
    }
}
