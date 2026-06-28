export const AIVA_CHAT_INTRO_MESSAGE =
  "Hello!\n\nI'm AIVA, your healthcare assistant.\n\nI can help you:\n• Book appointments\n• Reschedule visits\n• Cancel appointments\n• View bills\n• Check lab reports\n• Explain prescriptions\n• Answer clinic questions\n\nI always ask for confirmation before making changes.";

export const AIVA_CHAT_PLACEHOLDER =
  "Ask AIVA anything...\n\nExamples:\n• Book appointment with Dr Vikas tomorrow\n• Cancel my next appointment\n• Show my lab reports";

export const AIVA_CHAT_HELP_TEXT =
  "AIVA will ask for confirmation before booking, rescheduling, or cancelling.";

export const AIVA_CHAT_QUICK_ACTIONS = [
  { label: "Book appointment", message: "Book an appointment for me.", sendDirect: true },
  { label: "Reschedule appointment", message: "Reschedule my appointment.", sendDirect: true },
  { label: "Cancel appointment", message: "Cancel my appointment.", sendDirect: true },
  { label: "Check my appointment", message: "Check my appointment.", sendDirect: true },
  { label: "Show bills", message: "Show my bills.", sendDirect: true },
  { label: "Show lab reports", message: "Show my lab reports.", sendDirect: true },
  { label: "Talk to receptionist", message: "I need help from the receptionist.", sendDirect: true },
];

export const AIVA_CHAT_EXAMPLES = [
  "Book appointment with Dr Vikas tomorrow",
  "Cancel my next appointment",
  "Show my lab reports",
  "Check my pending bills",
];

export const AIVA_CHAT_FRIENDLY_ERROR =
  "Sorry, I could not complete that request. Please try again or contact reception.";

export function shouldSendAivaMessageOnKeyDown(key, shiftKey) {
  return key === "Enter" && !shiftKey;
}

export function normalizeAivaMessageDraft(value) {
  return value.replace(/\r\n/g, "\n");
}

export function isBlankAivaMessage(value) {
  return !value.trim();
}
