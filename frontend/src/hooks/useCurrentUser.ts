import { useAuth } from './useAuth'

export function useCurrentUser() {
  const { user, isLoading, refreshUser } = useAuth()

  return {
    user,
    isLoading,
    refreshUser,
  }
}
