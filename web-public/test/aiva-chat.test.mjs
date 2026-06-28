import test from "node:test";
import assert from "node:assert/strict";

import {
  AIVA_CHAT_EXAMPLES,
  AIVA_CHAT_FRIENDLY_ERROR,
  AIVA_CHAT_HELP_TEXT,
  AIVA_CHAT_INTRO_MESSAGE,
  AIVA_CHAT_PLACEHOLDER,
  AIVA_CHAT_QUICK_ACTIONS,
  isBlankAivaMessage,
  normalizeAivaMessageDraft,
  shouldSendAivaMessageOnKeyDown,
} from "../src/utils/aivaChat.js";

test("aiva chat keyboard handling sends on enter and preserves shift enter newline", () => {
  assert.equal(shouldSendAivaMessageOnKeyDown("Enter", false), true);
  assert.equal(shouldSendAivaMessageOnKeyDown("Enter", true), false);
  assert.equal(shouldSendAivaMessageOnKeyDown("Escape", false), false);
});

test("aiva chat draft helpers reject blank messages and normalize newlines", () => {
  assert.equal(isBlankAivaMessage("   "), true);
  assert.equal(isBlankAivaMessage("\n\t"), true);
  assert.equal(isBlankAivaMessage("Book appointment"), false);
  assert.equal(normalizeAivaMessageDraft("Line 1\r\nLine 2"), "Line 1\nLine 2");
});

test("aiva quick actions send direct messages and examples stay patient friendly", () => {
  assert.ok(AIVA_CHAT_QUICK_ACTIONS.length >= 5);
  assert.ok(AIVA_CHAT_QUICK_ACTIONS.every((action) => action.sendDirect === true && action.message.trim().length > 0));
  assert.deepEqual(AIVA_CHAT_EXAMPLES, [
    "Book appointment with Dr Vikas tomorrow",
    "Cancel my next appointment",
    "Show my lab reports",
    "Check my pending bills",
  ]);
});

test("aiva chat copy stays friendly and non-technical", () => {
  assert.match(AIVA_CHAT_INTRO_MESSAGE, /healthcare assistant/i);
  assert.match(AIVA_CHAT_INTRO_MESSAGE, /Book appointments/i);
  assert.match(AIVA_CHAT_INTRO_MESSAGE, /Check lab reports/i);
  assert.match(AIVA_CHAT_PLACEHOLDER, /Ask AIVA anything/i);
  assert.match(AIVA_CHAT_PLACEHOLDER, /Show my lab reports/);
  assert.match(AIVA_CHAT_HELP_TEXT, /confirmation/i);
  assert.equal(
    AIVA_CHAT_FRIENDLY_ERROR,
    "Sorry, I could not complete that request. Please try again or contact reception.",
  );
});
