package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceResponseIntent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceResponseInterpreterTest {
    private final VoiceResponseInterpreter interpreter = new VoiceResponseInterpreter();

    @Test void recognizesEnglishAndHindiYes() {
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("yes"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("Yes."));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("haan"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("ha"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("हाँ"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("हाँ।"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("हां"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("जी"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("जी हाँ"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("ji haan"));
        assertEquals(VoiceResponseIntent.YES, interpreter.interpret("I will come"));
    }

    @Test void recognizesEnglishAndHindiNo() {
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("no"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("No."));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("nahi"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("Nahi."));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("नहीं"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("नहीं।"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("नही"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("ना"));
        assertEquals(VoiceResponseIntent.NO, interpreter.interpret("I can't come"));
    }

    @Test void remainsConservative() {
        assertEquals(VoiceResponseIntent.UNCLEAR, interpreter.interpret("maybe"));
        assertEquals(VoiceResponseIntent.NO_RESPONSE, interpreter.interpret("  "));
        assertEquals(VoiceResponseIntent.UNCLEAR, interpreter.interpret("I will think about it"));
    }
}
