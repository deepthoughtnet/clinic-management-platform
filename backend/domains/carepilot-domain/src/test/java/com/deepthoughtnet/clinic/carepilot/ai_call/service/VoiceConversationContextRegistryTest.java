package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceInteractionType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceConversationContextRegistryTest {
    @Test void contextsAreIsolatedByProviderCallIdAndRemoved() {
        VoiceConversationContextRegistry registry = new VoiceConversationContextRegistry();
        var a = new VoiceConversationContextRegistry.VoiceConversationContext("call-a", UUID.randomUUID(), UUID.randomUUID(), null, VoiceInteractionType.GENERIC_CONFIRMATION, "en-IN", "test", 1);
        var b = new VoiceConversationContextRegistry.VoiceConversationContext("call-b", UUID.randomUUID(), UUID.randomUUID(), null, VoiceInteractionType.GENERIC_CONFIRMATION, "hi-IN", "test", 1);
        assertTrue(registry.register(a));
        assertTrue(registry.register(b));
        assertSame(a, registry.find("call-a").orElseThrow());
        assertSame(b, registry.find("call-b").orElseThrow());
        assertFalse(registry.register(a));
        assertSame(a, registry.remove("call-a").orElseThrow());
        assertTrue(registry.find("call-a").isEmpty());
        assertEquals(1, registry.size());
    }
}
