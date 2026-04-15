import { createContext, useCallback, useEffect, useMemo, useReducer } from 'react'
import type { PropsWithChildren } from 'react'

import type { AssistantMessage } from '../types/assistant'
import type { RecentActivity } from '../types/it'
import { CHAT_SESSION_RESET_EVENT, clearChatSessionStorage } from '../utils/chatSession'
import { sanitizeDeep, sanitizeText } from '../utils/textSanitizer'

const MESSAGES_KEY = 'pts.v2.chatMessages'
const ACTIVITIES_KEY = 'pts.v2.recentActivities'
const ACCESSED_KEY = 'pts.v2.accessedItDocs'
const INTERACTIONS_KEY = 'pts.v2.interactionCount'
const MAX_MESSAGES = 200
const MAX_ACTIVITIES = 6

interface ChatState {
  messages: AssistantMessage[]
  recentActivities: RecentActivity[]
  accessedItDocs: string[]
  interactionCount: number
  selectedItId: string
}

type ChatAction =
  | { type: 'ADD_MESSAGE'; payload: AssistantMessage }
  | { type: 'CLEAR_MESSAGES' }
  | { type: 'ADD_ACTIVITY'; payload: RecentActivity }
  | { type: 'TRACK_IT'; payload: string }
  | { type: 'SET_SELECTED_IT'; payload: string }
  | { type: 'RESET_SESSION' }
  | { type: 'INCREMENT_INTERACTION' }

export interface ChatContextValue extends ChatState {
  addMessage: (msg: AssistantMessage) => void
  clearMessages: () => void
  addActivity: (activity: RecentActivity) => void
  trackItAccess: (code: string, description: string) => void
  setSelectedItId: (id: string) => void
  incrementInteraction: () => void
}

function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const raw = window.localStorage.getItem(key)
    if (raw) return sanitizeDeep(JSON.parse(raw) as T)
  } catch {
    // ignore
  }
  return fallback
}

function saveToStorage<T>(key: string, value: T): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(sanitizeDeep(value)))
  } catch {
    // ignore
  }
}

function getInitialState(): ChatState {
  return {
    messages: loadFromStorage<AssistantMessage[]>(MESSAGES_KEY, []),
    recentActivities: loadFromStorage<RecentActivity[]>(ACTIVITIES_KEY, []),
    accessedItDocs: loadFromStorage<string[]>(ACCESSED_KEY, []),
    interactionCount: loadFromStorage<number>(INTERACTIONS_KEY, 0),
    selectedItId: '',
  }
}

function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'ADD_MESSAGE': {
      const messages = [...state.messages, sanitizeDeep(action.payload)].slice(-MAX_MESSAGES)
      saveToStorage(MESSAGES_KEY, messages)
      return { ...state, messages }
    }
    case 'CLEAR_MESSAGES': {
      saveToStorage(MESSAGES_KEY, [])
      return { ...state, messages: [] }
    }
    case 'ADD_ACTIVITY': {
      const recentActivities = [sanitizeDeep(action.payload), ...state.recentActivities].slice(0, MAX_ACTIVITIES)
      saveToStorage(ACTIVITIES_KEY, recentActivities)
      return { ...state, recentActivities }
    }
    case 'TRACK_IT': {
      const code = sanitizeText(action.payload)
      const accessedItDocs = state.accessedItDocs.includes(code) ? state.accessedItDocs : [...state.accessedItDocs, code]
      saveToStorage(ACCESSED_KEY, accessedItDocs)
      return { ...state, accessedItDocs }
    }
    case 'SET_SELECTED_IT': {
      const nextSelectedItId = sanitizeText(action.payload)
      if (nextSelectedItId === state.selectedItId) {
        return state
      }

      saveToStorage(MESSAGES_KEY, [])
      return {
        ...state,
        selectedItId: nextSelectedItId,
        messages: [],
      }
    }
    case 'RESET_SESSION': {
      clearChatSessionStorage()
      return {
        messages: [],
        recentActivities: [],
        accessedItDocs: [],
        interactionCount: 0,
        selectedItId: '',
      }
    }
    case 'INCREMENT_INTERACTION': {
      const interactionCount = state.interactionCount + 1
      saveToStorage(INTERACTIONS_KEY, interactionCount)
      return { ...state, interactionCount }
    }
    default:
      return state
  }
}

export const ChatContext = createContext<ChatContextValue | null>(null)

export function ChatProvider({ children }: PropsWithChildren) {
  const [state, dispatch] = useReducer(chatReducer, undefined, getInitialState)

  useEffect(() => {
    const handleSessionReset = () => {
      dispatch({ type: 'RESET_SESSION' })
    }

    window.addEventListener(CHAT_SESSION_RESET_EVENT, handleSessionReset)
    return () => {
      window.removeEventListener(CHAT_SESSION_RESET_EVENT, handleSessionReset)
    }
  }, [])

  const addMessage = useCallback((msg: AssistantMessage) => {
    dispatch({ type: 'ADD_MESSAGE', payload: sanitizeDeep(msg) })
  }, [])

  const clearMessages = useCallback(() => {
    dispatch({ type: 'CLEAR_MESSAGES' })
  }, [])

  const addActivity = useCallback((activity: RecentActivity) => {
    dispatch({ type: 'ADD_ACTIVITY', payload: sanitizeDeep(activity) })
  }, [])

  const trackItAccess = useCallback((code: string, description: string) => {
    dispatch({ type: 'TRACK_IT', payload: sanitizeText(code) })
    dispatch({
      type: 'ADD_ACTIVITY',
      payload: { icone: 'VIS', descricao: sanitizeText(description), data: 'Hoje' },
    })
  }, [])

  const setSelectedItId = useCallback((id: string) => {
    dispatch({ type: 'SET_SELECTED_IT', payload: id })
  }, [])

  const incrementInteraction = useCallback(() => {
    dispatch({ type: 'INCREMENT_INTERACTION' })
  }, [])

  const value = useMemo<ChatContextValue>(
    () => ({
      ...state,
      addMessage,
      clearMessages,
      addActivity,
      trackItAccess,
      setSelectedItId,
      incrementInteraction,
    }),
    [state, addMessage, clearMessages, addActivity, trackItAccess, setSelectedItId, incrementInteraction],
  )

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

