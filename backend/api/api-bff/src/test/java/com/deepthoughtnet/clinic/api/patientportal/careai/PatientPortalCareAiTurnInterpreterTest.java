package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiTurnInterpreterTest {
    private final PatientPortalCareAiEntityRegistry registry = new PatientPortalCareAiEntityRegistry();
    private final PatientPortalCareAiTurnInterpreter interpreter = new PatientPortalCareAiTurnInterpreter(
            new PatientPortalCareAiEntityExtractor(registry)
    );

    @Test
    void interpretsNaturalBookingTurnThroughOneCanonicalShape() {
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "Book Dr Akshu next Thursday afternoon.",
                "en",
                context("BOOK_APPOINTMENT"),
                null
        );

        assertEquals(PatientPortalCareAiIntent.BOOK_APPOINTMENT, turn.intent());
        assertEquals("Akshu", turn.entities().doctor());
        assertEquals("afternoon", turn.entities().timeWindow());
        assertEquals(PatientPortalCareAiDialogAct.START_REQUEST, turn.dialogAct());
        assertEquals(PatientPortalCareAiInterpretationSource.EXTRACTOR_SIGNAL, turn.source());
    }

    @Test
    void interpretsCorrectionAndAlternativeWithoutExecutingAnything() {
        PatientPortalCareAiCanonicalTurn correction = interpreter.interpret(
                "Actually, make it morning instead.", "en", context("BOOK_APPOINTMENT"), null);
        PatientPortalCareAiCanonicalTurn alternative = interpreter.interpret(
                "another date", "en", context("BOOK_APPOINTMENT"), null);

        assertEquals(PatientPortalCareAiDialogAct.CHANGE_INFORMATION, correction.dialogAct());
        assertEquals("time", correction.correction().target());
        assertTrue(alternative.alternative().present());
        assertEquals("date", alternative.alternative().target());
        assertEquals(PatientPortalCareAiDialogAct.REQUEST_ALTERNATIVE, alternative.dialogAct());
    }

    @Test
    void negativeConfirmationHasPrecedenceOverPositiveWords() {
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "don't confirm", "en", context("BOOK_APPOINTMENT"), null);

        assertEquals(PatientPortalCareAiConfirmationPolarity.NEGATIVE, turn.confirmation());
        assertEquals(PatientPortalCareAiDialogAct.REJECT, turn.dialogAct());
    }

    @Test
    void endConversationIsDistinctFromWorkflowAbandonment() {
        PatientPortalCareAiCanonicalTurn end = interpreter.interpret("No thanks, bye.", "en", context("BOOK_APPOINTMENT"), null);
        PatientPortalCareAiCanonicalTurn abandon = interpreter.interpret("Never mind", "en", context("BOOK_APPOINTMENT"), null);

        assertTrue(end.endConversation());
        assertFalse(end.abandonWorkflow());
        assertTrue(abandon.abandonWorkflow());
        assertFalse(abandon.endConversation());
    }

    @Test
    void selectionIsCandidateScopedSemanticInput() {
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "second doctor", "en", context("BOOK_APPOINTMENT"), null);

        assertEquals(PatientPortalCareAiDialogAct.SELECT_OPTION, turn.dialogAct());
        assertEquals(2, turn.selection().ordinal());
        assertEquals("doctor", turn.selection().target());
    }

    @Test
    void naturalMultiEntityTurnDoesNotUseFastPath() {
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "Can we do Akshu sometime next Thursday afternoon?", "en", context("BOOK_APPOINTMENT"), null);

        assertEquals(PatientPortalCareAiInterpretationSource.EXTRACTOR_SIGNAL, turn.source());
        assertEquals("afternoon", turn.entities().timeWindow());
    }

    @Test
    void deterministicCanonicalDateOutranksConflictingPlannerDate() {
        PatientPortalCareAiPlannerDecision plannerDecision = new PatientPortalCareAiPlannerDecision(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                "Akshu",
                null,
                "2026-09-10",
                "afternoon",
                PatientPortalCareAiPlannerConfirmationDecision.NONE,
                "natural booking request",
                false,
                null,
                PatientPortalCareAiDialogAct.START_REQUEST,
                null,
                null,
                null,
                null
        );

        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "Can we do Akshu on 10th June 2027 afternoon?",
                "en",
                context("BOOK_APPOINTMENT"),
                plannerDecision,
                false,
                true
        );

        assertEquals(PatientPortalCareAiInterpretationSource.GEMINI, turn.source());
        assertEquals("2027-06-10", turn.entities().date());
    }

    @Test
    void noisyNumericSpeechDateUsesIndiaDayMonthOrder() {
        PatientPortalCareAiCanonicalTurn turn = interpreter.interpret(
                "It's 10, 09, 26.", "en", context("BOOK_APPOINTMENT"), null);

        assertEquals("2026-09-10", turn.entities().date());
    }

    private PatientPortalCareAiPlanningContext context(String intent) {
        return new PatientPortalCareAiPlanningContext(
                "en", "", intent,
                null, null, false, null,
                null, null,
                null, null, null, null, null,
                List.<String>of(), List.<String>of(), List.<String>of(), List.<String>of(), List.<String>of(),
                List.<String>of(), List.<String>of(), null, 0
        );
    }
}
