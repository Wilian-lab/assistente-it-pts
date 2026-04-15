import { useContext } from 'react'

import { ChatContext } from '../contexts/ChatContext'
import type { ChatContextValue } from '../contexts/ChatContext'

export function useChat(): ChatContextValue {
  const ctx = useContext(ChatContext)
  if (!ctx) {
    throw new Error('useChat must be used within a ChatProvider')
  }
  return ctx
}
