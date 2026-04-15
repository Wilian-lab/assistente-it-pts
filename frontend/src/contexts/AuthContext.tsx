import { useCallback, useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'
import type { LoginPayload, UserProfile } from '../types/auth'

import type { AuthContextValue } from './auth-context'
import { AuthContext } from './auth-context'
import { authService } from '../services/auth/authService'
import { resetChatSession } from '../utils/chatSession'
import {
  AUTH_SESSION_INVALIDATED_EVENT,
  clearAccessToken,
  getAccessToken,
  setAuthSession,
} from '../utils/storage'

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const clearSessionState = useCallback(() => {
    resetChatSession()
    clearAccessToken()
    setUser(null)
    setIsLoading(false)
  }, [])

  const refreshUser = useCallback(async () => {
    const token = getAccessToken()

    if (!token) {
      setUser(null)
      setIsLoading(false)
      return
    }

    try {
      const currentUser = await authService.getCurrentUser()
      setUser(currentUser)
    } catch {
      clearSessionState()
    } finally {
      setIsLoading(false)
    }
  }, [clearSessionState])

  useEffect(() => {
    void refreshUser()
  }, [refreshUser])

  useEffect(() => {
    function handleSessionInvalidated() {
      clearSessionState()
    }

    window.addEventListener(AUTH_SESSION_INVALIDATED_EVENT, handleSessionInvalidated)
    return () => {
      window.removeEventListener(AUTH_SESSION_INVALIDATED_EVENT, handleSessionInvalidated)
    }
  }, [clearSessionState])

  const login = useCallback(async (payload: LoginPayload) => {
    const response = await authService.login(payload)
    resetChatSession()
    setAuthSession(response.accessToken, response.expiresIn)
    setUser(response.user)
    void refreshUser()
  }, [refreshUser])

  const switchSector = useCallback(async (setor: string) => {
    const response = await authService.switchSector({ setor })
    resetChatSession()
    setAuthSession(response.accessToken, response.expiresIn)
    setUser(response.user)
  }, [])

  const logout = useCallback(() => {
    clearSessionState()
  }, [clearSessionState])

  const value = useMemo<AuthContextValue>(() => ({
    user,
    isAuthenticated: Boolean(user && getAccessToken()),
    isLoading,
    login,
    logout,
    refreshUser,
    switchSector,
  }), [isLoading, login, logout, refreshUser, switchSector, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
