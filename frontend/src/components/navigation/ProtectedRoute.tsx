import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '../../hooks/useAuth'

interface ProtectedRouteProps {
  roles?: string[]
}

export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, user } = useAuth()

  if (isLoading) {
    return <div className="page-state">Carregando sessão...</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (roles && (!user?.role || !roles.includes(String(user.role).toUpperCase()))) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
