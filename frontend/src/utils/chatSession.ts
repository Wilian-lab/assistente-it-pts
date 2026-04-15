const MESSAGES_KEY = 'pts.v2.chatMessages'
const ACTIVITIES_KEY = 'pts.v2.recentActivities'
const ACCESSED_KEY = 'pts.v2.accessedItDocs'
const INTERACTIONS_KEY = 'pts.v2.interactionCount'

export const CHAT_SESSION_RESET_EVENT = 'pts:chat-session-reset'

const CHAT_STORAGE_KEYS = [MESSAGES_KEY, ACTIVITIES_KEY, ACCESSED_KEY, INTERACTIONS_KEY] as const

export function clearChatSessionStorage(): void {
  for (const key of CHAT_STORAGE_KEYS) {
    window.localStorage.removeItem(key)
  }
}

export function resetChatSession(): void {
  clearChatSessionStorage()
  window.dispatchEvent(new CustomEvent(CHAT_SESSION_RESET_EVENT))
}
