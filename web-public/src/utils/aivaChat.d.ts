export type AivaQuickAction = {
  label: string;
  message: string;
  sendDirect: boolean;
};

export declare const AIVA_CHAT_INTRO_MESSAGE: string;
export declare const AIVA_CHAT_PLACEHOLDER: string;
export declare const AIVA_CHAT_HELP_TEXT: string;
export declare const AIVA_CHAT_QUICK_ACTIONS: AivaQuickAction[];
export declare const AIVA_CHAT_EXAMPLES: string[];
export declare const AIVA_CHAT_FRIENDLY_ERROR: string;

export declare function shouldSendAivaMessageOnKeyDown(key: string, shiftKey: boolean): boolean;
export declare function normalizeAivaMessageDraft(value: string): string;
export declare function isBlankAivaMessage(value: string): boolean;
