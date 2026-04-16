import { createContext, useCallback, useEffect, useMemo, useReducer } from 'react'
import type { PropsWithChildren } from 'react'

import type { AssistantMessage } from '../types/assistant'
import type { RecentActivity } from '../types/it'
import { CHAT_SESSION_RESET_EVENT } from '../utils/chatSession'
import { sanitizeDeep, sanitizeText } from '../utils/textSanitizer'

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

function getInitialState(): ChatState {
  return {
    messages: [],
    recentActivities: [],
    accessedItDocs: [],
    interactionCount: 0,
    selectedItId: '',
  }
}

function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'ADD_MESSAGE':
      return {
        ...state,
        messages: [...state.messages, sanitizeDeep(action.payload)].slice(-MAX_MESSAGES),
      }
    case 'CLEAR_MESSAGES':
      return { ...state, messages: [] }
    case 'ADD_ACTIVITY':
      return {
        ...state,
        recentActivities: [sanitizeDeep(action.payload), ...state.recentActivities].slice(0, MAX_ACTIVITIES),
      }
    case 'TRACK_IT': {
      const code = sanitizeText(action.payload)
      const accessedItDocs = state.accessedItDocs.includes(code) ? state.accessedItDocs : [...state.accessedItDocs, code]
      return { ...state, accessedItDocs }
    }
    case 'SET_SELECTED_IT': {
      const nextSelectedItId = sanitizeText(action.payload)
      if (nextSelectedItId === state.selectedItId) {
        return state
      }

      return {
        ...state,
        selectedItId: nextSelectedItId,
        messages: [],
      }
    }
    case 'RESET_SESSION':
      return getInitialState()
    case 'INCREMENT_INTERACTION':
      return { ...state, interactionCount: state.interactionCount + 1 }
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
