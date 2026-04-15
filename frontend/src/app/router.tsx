import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { ProtectedRoute } from '../components/navigation/ProtectedRoute'
import { AppShell } from './layouts/AppShell'
import { AuthLayout } from './layouts/AuthLayout'
import { LoginPage } from '../pages/auth/LoginPage'
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from '../pages/auth/ResetPasswordPage'
import { DashboardPage } from '../pages/user/DashboardPage'
import { MyItsPage } from '../pages/user/MyItsPage'
import { AssistantPage } from '../pages/user/AssistantPage'
import { DocumentationPage } from '../pages/user/DocumentationPage'
import { FilesPage } from '../pages/user/FilesPage'
import { ConversationsPage } from '../pages/user/ConversationsPage'
import { NotificationsPage } from '../pages/user/NotificationsPage'
import { ProfilePage } from '../pages/user/ProfilePage'
import { AdminUsersPage } from '../pages/admin/AdminUsersPage'

const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      {
        path: '/login',
        element: <LoginPage />,
      },
      {
        path: '/forgot-password',
        element: <ForgotPasswordPage />,
      },
      {
        path: '/reset-password',
        element: <ResetPasswordPage />,
      },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/minhas-its', element: <MyItsPage /> },
          { path: '/assistant', element: <AssistantPage /> },
          { path: '/documentation', element: <DocumentationPage /> },
          { path: '/files', element: <FilesPage /> },
          { path: '/conversations', element: <ConversationsPage /> },
          { path: '/notifications', element: <NotificationsPage /> },
          { path: '/profile', element: <ProfilePage /> },
        ],
      },
    ],
  },
  {
    element: <ProtectedRoute roles={['SUPER_ADMIN', 'ADMIN']} />,
    children: [
      {
        element: <AppShell />,
        children: [{ path: '/admin/users', element: <AdminUsersPage /> }],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/login" replace />,
  },
])

export function AppRouter() {
  return <RouterProvider router={router} />
}
