import { createContext } from 'react'

import type { LoginPayload, UserProfile } from '../types/auth'

export interface AuthContextValue {
  user: UserProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (payload: LoginPayload) => Promise<void>
  logout: () => void
  refreshUser: () => Promise<void>
  switchSector: (setor: string) => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
