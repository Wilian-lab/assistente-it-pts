export const CHAT_SESSION_RESET_EVENT = 'pts:chat-session-reset'

export function resetChatSession(): void {
  window.dispatchEvent(new CustomEvent(CHAT_SESSION_RESET_EVENT))
}
